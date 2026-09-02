#!/usr/bin/env python3
"""Validate the observed fnOS Music Web API without third-party packages.

The default run is read-only apart from login. Mutating favorite/playlist probes
require both --include-write and --confirm-destructive. Results are written to a
JSON report with identifiers and credentials redacted.
"""

from __future__ import annotations

import argparse
import hashlib
import http.cookiejar
import json
import os
import random
import socket
import ssl
import sys
import time
import uuid
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qsl, quote, unquote, urlencode, urljoin, urlsplit, urlunsplit
from urllib.request import HTTPCookieProcessor, HTTPSHandler, Request, build_opener


DEFAULT_BASE_URL = "https://doubleyu.fnos.net/music"
MAX_BODY_BYTES = 2 * 1024 * 1024
AUTHX_PREFIX = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
AUTHX_API_KEY = "6D5602D4-A342-4799-A0F0-BB795E7167D0"
ID_QUERY_KEYS = {
    "guid",
    "trackguid",
    "albumguid",
    "artistguid",
    "playlistguid",
    "guids",
    "coverid",
    "deviceid",
}


@dataclass
class Config:
    base_url: str
    username: str | None
    password: str | None
    cookie: str | None
    timeout: float
    verify_tls: bool
    search_query: str
    output: Path
    include_write: bool
    confirm_destructive: bool
    login_username_field: str
    login_password_field: str
    write_scope: str


@dataclass
class ApiResult:
    name: str
    method: str
    endpoint: str
    outcome: str
    status: int | None = None
    elapsed_ms: int | None = None
    content_type: str | None = None
    note: str = ""
    response_shape: str | None = None
    response_headers: dict[str, str] = field(default_factory=dict)


@dataclass
class HttpResponse:
    status: int
    url: str
    headers: dict[str, str]
    body: bytes
    elapsed_ms: int

    @property
    def content_type(self) -> str:
        return self.headers.get("content-type", "").split(";", 1)[0].strip().lower()

    def json(self) -> Any:
        return json.loads(self.body.decode("utf-8"))


def load_dotenv(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        key, sep, value = line.partition("=")
        if not sep:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def env_value(dotenv: dict[str, str], key: str, default: str | None = None) -> str | None:
    return os.environ.get(key, dotenv.get(key, default))


def normalize_base_url(value: str) -> str:
    url = value.strip().rstrip("/")
    if not url.endswith("/music"):
        url += "/music"
    return url


def redact_url(url: str) -> str:
    parts = urlsplit(url)
    query = []
    for key, value in parse_qsl(parts.query, keep_blank_values=True):
        query.append((key, "{redacted}" if key.lower() in ID_QUERY_KEYS else value))
    # Hostnames can themselves contain account-like identifiers. Reports only
    # need the path/query shape, so persist a placeholder instead of the host.
    return urlunsplit((parts.scheme, "{nasHost}", parts.path, urlencode(query), ""))


def response_shape(value: Any) -> str:
    if isinstance(value, dict):
        keys = ", ".join(sorted(map(str, value.keys()))[:20])
        return f"object keys=[{keys}]"
    if isinstance(value, list):
        element = type(value[0]).__name__ if value else "empty"
        return f"array length={len(value)} element={element}"
    return type(value).__name__


def validate_roam_contract(value: Any) -> tuple[bool, str]:
    """Validate the nested roam item shape without retaining library data."""
    if not isinstance(value, dict):
        return False, "roam data must be an object"
    playable_items = 0
    for key in ("current", "next"):
        item = value.get(key)
        if item is None:
            continue
        if not isinstance(item, dict):
            return False, f"roam {key} must be an object or null"
        track = item.get("track")
        if not isinstance(track, dict):
            return False, f"roam {key}.track must be an object"
        if not isinstance(track.get("guid"), str) or not track["guid"]:
            return False, f"roam {key}.track.guid must be a non-empty string"
        roam_id = item.get("roamId")
        if roam_id is not None and not isinstance(roam_id, str):
            return False, f"roam {key}.roamId must be a string or null"
        playable_items += 1
    if playable_items == 0:
        return False, "roam response did not contain a playable current or next track"
    return True, "current/next items use {roamId, track}; track contains guid"


def iter_dicts(value: Any) -> Iterable[dict[str, Any]]:
    if isinstance(value, dict):
        yield value
        for item in value.values():
            yield from iter_dicts(item)
    elif isinstance(value, list):
        for item in value:
            yield from iter_dicts(item)


def first_identifier(value: Any, aliases: tuple[str, ...]) -> str | None:
    normalized_aliases = {alias.lower() for alias in aliases}
    for obj in iter_dicts(value):
        for key, item in obj.items():
            if key.lower() in normalized_aliases and isinstance(item, (str, int)) and str(item):
                return str(item)
    return None


def list_item_identifiers(value: Any, aliases: tuple[str, ...]) -> set[str]:
    items = value.get("list") if isinstance(value, dict) else None
    if not isinstance(items, list):
        return set()
    return {
        identifier
        for item in items
        if (identifier := first_identifier(item, aliases)) is not None
    }


def find_named_identifier(value: Any, expected_name: str) -> str | None:
    for obj in iter_dicts(value):
        name = next((obj[k] for k in obj if k.lower() in {"name", "title"}), None)
        if name == expected_name:
            identifier = first_identifier(obj, ("guid", "id", "playlistGUID"))
            if identifier:
                return identifier
    return None


def first_cover_id(value: Any) -> str | None:
    return first_identifier(value, ("coverId", "cover_id", "coverID"))


class ApiClient:
    def __init__(self, config: Config):
        self.config = config
        self.cookies = http.cookiejar.CookieJar()
        context = ssl.create_default_context()
        if not config.verify_tls:
            context.check_hostname = False
            context.verify_mode = ssl.CERT_NONE
        self.opener = build_opener(HTTPCookieProcessor(self.cookies), HTTPSHandler(context=context))

    @staticmethod
    def _md5(value: str) -> str:
        return hashlib.md5(value.encode("utf-8"), usedforsecurity=False).hexdigest()

    def _authx(self, method: str, url: str, json_text: str | None) -> str:
        parts = urlsplit(url)
        if method.upper() == "GET":
            # The Web client parses the URL, discards null-like values, sorts
            # parameter names, URL-encodes them, then hashes the decoded string.
            query_values: dict[str, str] = {}
            for key, value in parse_qsl(parts.query, keep_blank_values=True):
                if value not in {"undefined", "null"}:
                    query_values[key] = value
            canonical = urlencode(sorted(query_values.items())).replace("+", "%20")
            data_hash = self._md5(unquote(canonical))
        else:
            data_hash = self._md5(json_text or "")
        nonce = str(random.SystemRandom().randint(100000, 999999))
        timestamp = str(time.time_ns() // 1_000_000)
        signing_text = "_".join(
            (AUTHX_PREFIX, parts.path, nonce, timestamp, data_hash, AUTHX_API_KEY)
        )
        return f"nonce={nonce}&timestamp={timestamp}&sign={self._md5(signing_text)}"

    def set_music_token(self, token: str) -> None:
        """Mirror the Web client cookie without persisting the token."""
        parts = urlsplit(self.config.base_url)
        if not parts.hostname:
            raise ValueError("base URL has no hostname")
        self.cookies.set_cookie(
            http.cookiejar.Cookie(
                version=0,
                name="music-token",
                value=quote(token, safe=""),
                port=None,
                port_specified=False,
                domain=parts.hostname,
                domain_specified=True,
                domain_initial_dot=False,
                path="/",
                path_specified=True,
                secure=parts.scheme == "https",
                expires=None,
                discard=True,
                comment=None,
                comment_url=None,
                rest={"SameSite": "Strict"},
                rfc2109=False,
            )
        )

    def api_url(self, endpoint: str) -> str:
        return urljoin(self.config.base_url.rstrip("/") + "/", endpoint.lstrip("/"))

    def request(
        self,
        method: str,
        endpoint: str,
        *,
        query: dict[str, Any] | None = None,
        json_body: Any | None = None,
        headers: dict[str, str] | None = None,
        max_body: int = MAX_BODY_BYTES,
    ) -> HttpResponse:
        url = self.api_url(endpoint)
        if query:
            url += ("&" if "?" in url else "?") + urlencode(query)
        body = None
        json_text = None
        request_headers = {
            "Accept": "application/json, text/plain, */*",
            "User-Agent": "fn-music-api-verifier/1.0",
            "Referer": self.config.base_url.rstrip("/") + "/",
        }
        if self.config.cookie:
            request_headers["Cookie"] = self.config.cookie
        if json_body is not None:
            json_text = json.dumps(json_body, ensure_ascii=False, separators=(",", ":"))
            body = json_text.encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        request_headers["authx"] = self._authx(method, url, json_text)
        if headers:
            request_headers.update(headers)
        request = Request(url, data=body, headers=request_headers, method=method)
        started = time.perf_counter()
        try:
            response = self.opener.open(request, timeout=self.config.timeout)
            response_body = response.read(max_body + 1)
            if len(response_body) > max_body:
                response_body = response_body[:max_body]
            return HttpResponse(
                status=response.status,
                url=response.geturl(),
                headers={key.lower(): value for key, value in response.headers.items()},
                body=response_body,
                elapsed_ms=round((time.perf_counter() - started) * 1000),
            )
        except HTTPError as exc:
            response_body = exc.read(min(max_body, 64 * 1024))
            return HttpResponse(
                status=exc.code,
                url=exc.geturl(),
                headers={key.lower(): value for key, value in exc.headers.items()},
                body=response_body,
                elapsed_ms=round((time.perf_counter() - started) * 1000),
            )


class Verifier:
    def __init__(self, config: Config):
        self.config = config
        self.client = ApiClient(config)
        self.results: list[ApiResult] = []
        self.samples: dict[str, Any] = {}

    def add_skip(self, name: str, method: str, endpoint: str, note: str) -> None:
        self.results.append(ApiResult(name, method, endpoint, "SKIP", note=note))
        print(f"SKIP {name}: {note}")

    def probe(
        self,
        name: str,
        method: str,
        endpoint: str,
        *,
        query: dict[str, Any] | None = None,
        json_body: Any | None = None,
        headers: dict[str, str] | None = None,
        expected: set[int] | None = None,
        sample_key: str | None = None,
        max_body: int = MAX_BODY_BYTES,
    ) -> HttpResponse | None:
        expected = expected or {200}
        display_url = self.client.api_url(endpoint)
        if query:
            display_url += "?" + urlencode(query)
        try:
            response = self.client.request(
                method,
                endpoint,
                query=query,
                json_body=json_body,
                headers=headers,
                max_body=max_body,
            )
        except (URLError, TimeoutError, OSError) as exc:
            note = f"{type(exc).__name__}: {exc}"
            self.results.append(ApiResult(name, method, redact_url(display_url), "FAIL", note=note))
            print(f"FAIL {name}: {note}")
            return None

        shape = None
        parsed: Any = None
        sample: Any = None
        business_error = False
        business_note = ""
        if response.body and "json" in response.content_type:
            try:
                parsed = response.json()
                sample = parsed
                if isinstance(parsed, dict) and "code" in parsed and "data" in parsed:
                    code = parsed.get("code")
                    sample = parsed.get("data")
                    shape = f"envelope code={code!r}; data {response_shape(sample)}"
                    if str(code) != "0":
                        business_error = True
                        message = parsed.get("msg")
                        business_note = f"business code {code}"
                        if isinstance(message, str) and message:
                            business_note += f": {message}"
                else:
                    shape = response_shape(parsed)
            except (UnicodeDecodeError, json.JSONDecodeError):
                shape = "invalid JSON"
        elif response.body:
            shape = f"{response.content_type or 'unknown'} bytes={len(response.body)}"

        connect_html = (
            "text/html" in response.content_type
            and (b"FN Connect" in response.body or b"fnOS" in response.body)
        )
        outcome = (
            "PASS"
            if response.status in expected and not connect_html and not business_error
            else "FAIL"
        )
        note = ""
        if connect_html:
            note = "FN Connect relay page returned instead of Music API; use a directly reachable NAS/LAN base URL"
        elif response.status not in expected:
            note = f"expected HTTP {sorted(expected)}, got {response.status}"
        elif business_error:
            note = business_note

        selected_headers = {
            key: response.headers[key]
            for key in ("allow", "accept-ranges", "content-range", "content-length", "etag", "last-modified")
            if key in response.headers
        }
        self.results.append(
            ApiResult(
                name=name,
                method=method,
                endpoint=redact_url(display_url),
                outcome=outcome,
                status=response.status,
                elapsed_ms=response.elapsed_ms,
                content_type=response.content_type or None,
                note=note,
                response_shape=shape,
                response_headers=selected_headers,
            )
        )
        print(f"{outcome:4} {response.status:3} {response.elapsed_ms:5} ms  {name}")
        if parsed is not None and not business_error and sample_key:
            self.samples[sample_key] = sample
        return response

    def login(self) -> bool:
        if self.config.cookie:
            print("INFO using API_COOKIE; password login probe skipped")
            self.add_skip("password login", "POST", "/api/v1/user/password-login", "API_COOKIE supplied")
            return True
        if not self.config.username or not self.config.password:
            self.add_skip("password login", "POST", "/api/v1/user/password-login", "USERNAME/PASSWORD missing")
            return False
        response = self.probe(
            "password login",
            "POST",
            "/api/v1/user/password-login",
            json_body={
                self.config.login_username_field: self.config.username,
                self.config.login_password_field: hashlib.sha256(
                    self.config.password.encode("utf-8")
                ).hexdigest(),
                "deviceId": uuid.uuid4().hex,
            },
            expected={200, 201, 204},
            sample_key="login",
        )
        if not response or not self.results or self.results[-1].outcome != "PASS":
            return False
        token = first_identifier(self.samples.get("login"), ("userToken",))
        if not token:
            result = self.results[-1]
            result.outcome = "FAIL"
            result.note = "login response did not contain userToken"
            print("FAIL password login: successful response did not contain userToken")
            return False
        self.client.set_music_token(token)
        return True

    def run_read_probes(self) -> None:
        fixed = [
            ("initialization state", "/api/v1/initialization/state", None, "initialization"),
            ("system config", "/api/v1/sys/config", None, "config"),
            ("current user", "/api/v1/user/me", None, "me"),
            ("shared libraries", "/api/v1/shared-library/list", None, "shared_libraries"),
            ("tasks", "/api/v1/task/list", None, "tasks"),
            ("playlists", "/api/v1/playlist/list", None, "playlists"),
            ("tracks", "/api/v1/track/list", {"page": 1, "size": 2}, "tracks"),
            ("albums", "/api/v1/album/list", {"page": 1, "size": 2}, "albums"),
            ("artists", "/api/v1/artist/list", {"page": 1, "size": 2}, "artists"),
            ("genres", "/api/v1/genre/list", {"page": 1, "size": 2}, "genres"),
            ("favorite tracks", "/api/v1/favorite-track/list", {"page": 1, "size": 2}, "favorites"),
            ("play history", "/api/v1/play-history/list", {"page": 1, "size": 2}, "history"),
        ]
        for name, endpoint, query, key in fixed:
            self.probe(name, "GET", endpoint, query=query, sample_key=key)

        track_id = first_identifier(self.samples.get("tracks"), ("guid", "trackGUID", "id"))
        album_id = first_identifier(self.samples.get("albums"), ("guid", "albumGUID", "id"))
        artist_id = first_identifier(self.samples.get("artists"), ("guid", "artistGUID", "id"))
        playlist_id = first_identifier(self.samples.get("playlists"), ("guid", "playlistGUID", "id"))

        if track_id:
            self.probe("track metadata", "GET", "/api/v1/track/metadata", query={"guid": track_id}, sample_key="track_metadata")
            self.probe("track lyrics", "GET", "/api/v1/lyric/list", query={"trackGUID": track_id}, sample_key="lyrics")
            self.probe(
                "track stream range",
                "GET",
                "/api/v1/track/stream",
                query={"guid": track_id},
                headers={"Range": "bytes=0-0"},
                expected={200, 206},
                max_body=1024,
            )
        else:
            for name, endpoint in (
                ("track metadata", "/api/v1/track/metadata"),
                ("track lyrics", "/api/v1/lyric/list"),
                ("track stream range", "/api/v1/track/stream"),
            ):
                self.add_skip(name, "GET", endpoint, "no track GUID available")

        if album_id:
            self.probe("album detail", "GET", "/api/v1/album/detail", query={"guid": album_id}, sample_key="album_detail")
            self.probe("album tracks", "GET", "/api/v1/track/album-detail/list", query={"albumGUID": album_id, "page": 1, "size": 2})
        else:
            self.add_skip("album detail", "GET", "/api/v1/album/detail", "no album GUID available")
            self.add_skip("album tracks", "GET", "/api/v1/track/album-detail/list", "no album GUID available")

        if artist_id:
            self.probe("artist detail", "GET", "/api/v1/artist/detail", query={"guid": artist_id}, sample_key="artist_detail")
            self.probe("artist albums", "GET", "/api/v1/album/artist-detail/list", query={"artistGUID": artist_id, "page": 1, "size": 2})
            self.probe("artist tracks", "GET", "/api/v1/track/artist-detail/list", query={"artistGUID": artist_id, "page": 1, "size": 2})
        else:
            self.add_skip("artist detail", "GET", "/api/v1/artist/detail", "no artist GUID available")
            self.add_skip("artist albums", "GET", "/api/v1/album/artist-detail/list", "no artist GUID available")
            self.add_skip("artist tracks", "GET", "/api/v1/track/artist-detail/list", "no artist GUID available")

        if playlist_id:
            self.probe("playlist batch detail", "GET", "/api/v1/playlist/batch-detail", query={"guids": playlist_id})
            self.probe("playlist detail", "GET", "/api/v1/playlist/detail", query={"guid": playlist_id}, sample_key="playlist_detail")
            self.probe("playlist tracks", "GET", "/api/v1/track/playlist-detail/list", query={"playlistGUID": playlist_id, "page": 1, "size": 2})
        else:
            self.add_skip("playlist batch detail", "GET", "/api/v1/playlist/batch-detail", "no playlist GUID available")
            self.add_skip("playlist detail", "GET", "/api/v1/playlist/detail", "no playlist GUID available")
            self.add_skip("playlist tracks", "GET", "/api/v1/track/playlist-detail/list", "no playlist GUID available")

        cover_id = first_cover_id(self.samples.get("track_metadata")) or first_cover_id(self.samples.get("albums"))
        if cover_id:
            self.probe("cover", "GET", "/api/v1/static/cover", query={"coverId": cover_id, "size": 120}, expected={200, 304}, max_body=1024)
        else:
            self.add_skip("cover", "GET", "/api/v1/static/cover", "no coverId available")

        query = self.config.search_query
        self.probe("search suggestions", "GET", "/api/v1/search/suggest", query={"q": query})
        for resource, size in (("track", 2), ("album", 2), ("artist", 2), ("playlist", 2)):
            self.probe(f"search {resource}", "GET", f"/api/v1/search/{resource}", query={"q": query, "page": 1, "size": size})

        self.probe("random roam", "GET", "/api/v1/track/roam-start", query={"deviceId": uuid.uuid4().hex}, sample_key="roam")
        if self.results[-1].outcome == "PASS":
            valid, note = validate_roam_contract(self.samples.get("roam"))
            self.results[-1].response_shape = (
                "envelope code=0; data current/next items={roamId, track}; track={guid, ...}"
            )
            if not valid:
                self.results[-1].outcome = "FAIL"
                self.results[-1].note = note
                print(f"FAIL random roam contract: {note}")

    def run_safe_write_discovery(self) -> None:
        for name, endpoint in (
            ("favorite create discovery", "/api/v1/favorite-track/create"),
            ("favorite delete discovery", "/api/v1/favorite-track/delete"),
            ("playlist create discovery", "/api/v1/playlist/create"),
            ("playlist edit discovery", "/api/v1/playlist/edit"),
            ("playlist add track discovery", "/api/v1/playlist/add-track"),
            ("playlist remove track discovery", "/api/v1/playlist/remove-track"),
            ("playlist purge track discovery", "/api/v1/playlist/purge-track"),
            ("playlist delete discovery", "/api/v1/playlist/delete"),
            ("playlist cover discovery", "/api/v1/static/cover/playlist"),
            ("event report discovery", "/api/v1/event/report"),
        ):
            self.add_skip(
                name,
                "POST",
                endpoint,
                "mutating endpoint not called during the default read-only run",
            )

    def run_write_lifecycle(self) -> None:
        track_ids = list_item_identifiers(self.samples.get("tracks"), ("guid", "trackGUID", "id"))
        favorite_ids = list_item_identifiers(self.samples.get("favorites"), ("guid", "trackGUID", "id"))
        track_id = next(iter(track_ids - favorite_ids), None) or next(iter(track_ids), None)
        if not track_id:
            self.add_skip("write lifecycle", "POST", "/api/v1/playlist/create", "no track GUID available")
            return

        # Favorite, track-play, and playlist payloads are confirmed by the
        # current Web bundle. All writes in this probe are reversible.
        initially_favorite = track_id in favorite_ids
        if initially_favorite:
            self.probe("favorite delete", "POST", "/api/v1/favorite-track/delete", json_body={"trackGUID": track_id}, expected={200, 204})
            self.probe("favorite restore", "POST", "/api/v1/favorite-track/create", json_body={"trackGUID": track_id}, expected={200, 201, 204})
        else:
            self.probe("favorite create", "POST", "/api/v1/favorite-track/create", json_body={"trackGUID": track_id}, expected={200, 201, 204})
            self.probe("favorite delete", "POST", "/api/v1/favorite-track/delete", json_body={"trackGUID": track_id}, expected={200, 204})
        self.probe(
            "track play event",
            "POST",
            "/api/v1/event/report",
            json_body={
                "events": [
                    {
                        "eventType": "track_play",
                        "occurredAt": time.time_ns() // 1_000_000,
                        "payload": {"trackGUID": track_id},
                    }
                ]
            },
            expected={200, 201, 204},
        )

        if self.config.write_scope != "all":
            self.add_skip(
                "playlist write lifecycle",
                "POST",
                "/api/v1/playlist/create",
                "write scope is favorites-events; use --write-scope all for the playlist lifecycle",
            )
            return

        timestamp = datetime.now(timezone.utc).strftime("%m%dT%H%M%SZ")
        temp_name = f"API verify {timestamp}"
        create = self.probe(
            "playlist create",
            "POST",
            "/api/v1/playlist/create",
            json_body={"name": temp_name, "coverId": "playlist_default_1"},
            expected={200, 201},
            sample_key="created_playlist",
        )
        if not create or create.status not in {200, 201}:
            self.add_skip("playlist lifecycle remainder", "POST", "/api/v1/playlist/edit", "playlist creation failed")
            return

        playlist_id = first_identifier(self.samples.get("created_playlist"), ("guid", "playlistGUID", "id"))
        if not playlist_id:
            listing = self.probe("playlist list after create", "GET", "/api/v1/playlist/list", sample_key="playlists_after_create")
            if listing:
                playlist_id = find_named_identifier(self.samples.get("playlists_after_create"), temp_name)
        if not playlist_id:
            self.add_skip("playlist lifecycle remainder", "POST", "/api/v1/playlist/edit", "created playlist GUID not found; manual cleanup may be required")
            return

        try:
            edited_name = f"API edited {timestamp}"
            self.probe(
                "playlist edit",
                "POST",
                "/api/v1/playlist/edit",
                json_body={"guid": playlist_id, "name": edited_name, "coverId": "playlist_default_2"},
                expected={200, 204},
            )
            # The playlist identifier is named `guid` for write requests. The
            # playlist track listing is the separate API that uses playlistGUID.
            membership = {"guid": playlist_id, "trackGUIDs": [track_id]}
            self.probe("playlist add track", "POST", "/api/v1/playlist/add-track", json_body=membership, expected={200, 204})
            self.probe("playlist remove track", "POST", "/api/v1/playlist/remove-track", json_body=membership, expected={200, 204})
            self.probe(
                "playlist purge track count",
                "GET",
                "/api/v1/playlist/purge-track-count",
                query={"guid": playlist_id},
            )
            self.probe(
                "playlist purge invalid tracks",
                "POST",
                "/api/v1/playlist/purge-track",
                json_body={"guid": playlist_id},
                expected={200, 204},
            )
        finally:
            self.probe("playlist delete", "POST", "/api/v1/playlist/delete", json_body={"guid": playlist_id}, expected={200, 204})

    def write_report(self) -> None:
        counts = {outcome: sum(r.outcome == outcome for r in self.results) for outcome in ("PASS", "FAIL", "SKIP")}
        report = {
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "base_url": redact_url(self.config.base_url),
            "summary": counts,
            "results": [asdict(result) for result in self.results],
            # Keep real library metadata out of the persistent report. Samples
            # remain in memory only long enough to discover dependent IDs.
            "response_sample_shapes": {
                key: response_shape(value) for key, value in self.samples.items()
            },
        }
        self.config.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"\nReport: {self.config.output}")
        print(f"Summary: PASS={counts['PASS']} FAIL={counts['FAIL']} SKIP={counts['SKIP']}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Validate observed fnOS Music API endpoints")
    parser.add_argument("--env-file", type=Path, default=Path(".env"), help="dotenv file (default: .env)")
    parser.add_argument("--base-url", help="Music app base URL; defaults to API_BASE_URL or the observed FN Connect URL")
    parser.add_argument("--output", type=Path, default=Path("api-verification-report.json"), help="JSON report path")
    parser.add_argument("--timeout", type=float, default=15.0, help="per-request timeout in seconds")
    parser.add_argument("--search-query", default="音乐", help="non-sensitive search query")
    parser.add_argument("--insecure", action="store_true", help="disable TLS certificate verification")
    parser.add_argument("--include-write", action="store_true", help="run reversible favorite and temporary-playlist mutations")
    parser.add_argument("--confirm-destructive", action="store_true", help="confirm temporary playlist deletion; required with --include-write")
    parser.add_argument(
        "--write-scope",
        choices=("favorites-events", "all"),
        default="favorites-events",
        help="mutations to verify; all also runs the reversible temporary-playlist lifecycle",
    )
    parser.add_argument("--login-username-field", default="username", help="password-login username JSON field")
    parser.add_argument("--login-password-field", default="password", help="password-login password JSON field")
    return parser


def dns_preflight(base_url: str) -> str | None:
    host = urlsplit(base_url).hostname
    if not host:
        return "base URL has no hostname"
    try:
        socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        return (
            f"hostname {host!r} is not resolvable by the operating system ({exc}); "
            "FN Connect browser relay hostnames may require the Web client. Set "
            "API_BASE_URL or --base-url to a directly reachable NAS/LAN address."
        )
    return None


def main() -> int:
    args = build_parser().parse_args()
    if args.include_write and not args.confirm_destructive:
        print("ERROR --include-write requires --confirm-destructive", file=sys.stderr)
        return 2

    dotenv = load_dotenv(args.env_file)
    config = Config(
        base_url=normalize_base_url(args.base_url or env_value(dotenv, "API_BASE_URL", DEFAULT_BASE_URL) or DEFAULT_BASE_URL),
        username=env_value(dotenv, "USERNAME"),
        password=env_value(dotenv, "PASSWORD"),
        cookie=env_value(dotenv, "API_COOKIE"),
        timeout=args.timeout,
        verify_tls=not args.insecure,
        search_query=args.search_query,
        output=args.output,
        include_write=args.include_write,
        confirm_destructive=args.confirm_destructive,
        login_username_field=args.login_username_field,
        login_password_field=args.login_password_field,
        write_scope=args.write_scope,
    )

    verifier = Verifier(config)
    preflight_error = dns_preflight(config.base_url)
    if preflight_error:
        verifier.results.append(
            ApiResult(
                "DNS preflight",
                "DNS",
                redact_url(config.base_url),
                "FAIL",
                note=preflight_error,
            )
        )
        print(f"FAIL DNS preflight: {preflight_error}")
        authenticated = False
    else:
        authenticated = verifier.login()
    if authenticated:
        verifier.run_read_probes()
        verifier.run_safe_write_discovery()
        if config.include_write:
            verifier.run_write_lifecycle()
        else:
            verifier.add_skip(
                "mutating lifecycle",
                "POST",
                "/api/v1/playlist/create",
                "use --include-write --confirm-destructive to run reversible write probes",
            )
    else:
        verifier.add_skip("authenticated endpoints", "GET", "/api/v1/*", "authentication unavailable or failed")

    verifier.write_report()
    return 1 if any(result.outcome == "FAIL" for result in verifier.results) else 0


if __name__ == "__main__":
    raise SystemExit(main())
