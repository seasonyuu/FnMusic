#!/usr/bin/env python3
"""Validate the FN Connect locator-to-fnOS Music flow.

The script accepts a public FN Connect entry URL such as
https://fnos.net/{fnId}/music/, discovers a relay, activates relay mode, and
then reuses the Music API verifier for authenticated read-only checks.

Persistent output is deliberately redacted: it contains neither the FN ID,
discovered addresses, credentials, cookies, tokens, nor library data.
"""

from __future__ import annotations

import argparse
import hashlib
import http.cookiejar
import json
import random
import ssl
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit, urlunsplit
from urllib.request import HTTPCookieProcessor, HTTPSHandler, Request, build_opener

from verify_api import AUTHX_PREFIX, Config, Verifier, env_value, load_dotenv


FN_CONNECT_API_KEY = "zIGtkc3dqZnJpd29qZXJqa2w7c"
FN_CONNECT_ENDPOINT = "/api/v1/fn/con"
DEFAULT_ENTRY_URL = "https://fnos.net/doubleyu/music/"
DEFAULT_OUTPUT = Path("fn-connect-verification-report.json")
RELAY_SUFFIXES = (".fnos.net", ".5ddd.com")


@dataclass
class FlowStep:
    name: str
    outcome: str
    status: int | None = None
    note: str = ""


@dataclass(frozen=True)
class EntryTarget:
    entry_url: str
    fn_id: str
    app_path: str

    @property
    def redacted_url(self) -> str:
        return f"https://fnos.net/{{fnId}}{self.app_path}"


def md5(value: str) -> str:
    return hashlib.md5(value.encode("utf-8"), usedforsecurity=False).hexdigest()


def parse_entry_url(value: str) -> EntryTarget:
    parts = urlsplit(value.strip())
    if parts.scheme != "https" or (parts.hostname or "").lower() not in {
        "fnos.net",
        "www.fnos.net",
    }:
        raise ValueError("entry URL must use https://fnos.net/{fnId}/...")
    segments = [segment for segment in parts.path.split("/") if segment]
    if not segments:
        raise ValueError("entry URL does not contain an FN ID")
    fn_id = segments[0]
    if len(fn_id) < 6:
        raise ValueError("FN ID must contain at least six characters")
    route = "/" + "/".join(segments[1:]) if len(segments) > 1 else "/"
    if route != "/":
        route += "/"
    entry_url = urlunsplit((parts.scheme, parts.netloc, parts.path, parts.query, ""))
    return EntryTarget(entry_url=entry_url, fn_id=fn_id, app_path=route)


def make_opener(cookies: http.cookiejar.CookieJar, verify_tls: bool):
    context = ssl.create_default_context()
    if not verify_tls:
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
    return build_opener(HTTPCookieProcessor(cookies), HTTPSHandler(context=context))


def read_response(opener, request: Request, timeout: float) -> tuple[int, dict[str, str], bytes]:
    try:
        response = opener.open(request, timeout=timeout)
        return (
            response.status,
            {key.lower(): value for key, value in response.headers.items()},
            response.read(2 * 1024 * 1024),
        )
    except HTTPError as exc:
        return (
            exc.code,
            {key.lower(): value for key, value in exc.headers.items()},
            exc.read(64 * 1024),
        )


def fetch_entry(target: EntryTarget, opener, timeout: float) -> int:
    status, headers, body = read_response(
        opener,
        Request(
            target.entry_url,
            headers={
                "Accept": "text/html,application/xhtml+xml",
                "User-Agent": "fn-connect-flow-verifier/1.0",
            },
            method="GET",
        ),
        timeout,
    )
    content_type = headers.get("content-type", "")
    if status != 200 or "text/html" not in content_type or b"FN Connect" not in body:
        raise RuntimeError("entry did not return the FN Connect bootstrap page")
    return status


def discover_addresses(target: EntryTarget, opener, timeout: float) -> tuple[int, dict[str, Any]]:
    body_text = json.dumps({"fnId": target.fn_id}, ensure_ascii=False, separators=(",", ":"))
    timestamp = str(time.time_ns() // 1_000_000)
    nonce = str(random.SystemRandom().randint(100000, 999999))
    signing_text = "_".join(
        (
            AUTHX_PREFIX,
            FN_CONNECT_ENDPOINT,
            nonce,
            timestamp,
            md5(body_text),
            FN_CONNECT_API_KEY,
        )
    )
    fn_sign_text = f"trim_connect`{target.fn_id}`{timestamp}`anna"
    request = Request(
        "https://fnos.net" + FN_CONNECT_ENDPOINT,
        data=body_text.encode("utf-8"),
        headers={
            "Accept": "application/json, text/plain, */*",
            "Content-Type": "application/json",
            "authx": f"nonce={nonce}&timestamp={timestamp}&sign={md5(signing_text)}",
            "fn-sign": hashlib.sha256(fn_sign_text.encode("utf-8")).hexdigest(),
            "User-Agent": "fn-connect-flow-verifier/1.0",
        },
        method="POST",
    )
    status, headers, body = read_response(opener, request, timeout)
    if status != 200 or "json" not in headers.get("content-type", ""):
        raise RuntimeError("FN Connect locator did not return JSON success")
    try:
        envelope = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RuntimeError("FN Connect locator returned invalid JSON") from exc
    if not isinstance(envelope, dict) or str(envelope.get("code")) != "0":
        raise RuntimeError("FN Connect locator returned a non-zero business code")
    data = envelope.get("data")
    if not isinstance(data, dict):
        raise RuntimeError("FN Connect locator response has no data object")
    return status, data


def select_relay(data: dict[str, Any]) -> tuple[str, int]:
    candidates = data.get("fn")
    if not isinstance(candidates, list):
        raise RuntimeError("FN Connect locator returned no relay list")
    for candidate in candidates:
        if not isinstance(candidate, str):
            continue
        parts = urlsplit("//" + candidate)
        hostname = (parts.hostname or "").lower()
        try:
            port = parts.port or 443
        except ValueError:
            continue
        if hostname.endswith(RELAY_SUFFIXES) and port == 443:
            return hostname, port
    raise RuntimeError("FN Connect locator returned no permitted HTTPS relay")


def activate_relay(
    target: EntryTarget,
    relay_host: str,
    relay_port: int,
    cookies: http.cookiejar.CookieJar,
    opener,
    timeout: float,
) -> tuple[int, str]:
    authority = relay_host if relay_port == 443 else f"{relay_host}:{relay_port}"
    app_url = f"https://{authority}{target.app_path}"
    status, headers, body = read_response(
        opener,
        Request(
            app_url,
            headers={
                "Accept": "text/html,application/xhtml+xml",
                "Origin": "https://fnos.net",
                "Referer": target.entry_url,
                "Sec-Fetch-Mode": "navigate",
                "Sec-Fetch-Site": "cross-site",
                "User-Agent": (
                    "Mozilla/5.0 AppleWebKit/537.36 "
                    "Chrome/140.0.0.0 Safari/537.36"
                ),
            },
            method="GET",
        ),
        timeout,
    )
    relay_cookie = any(cookie.name == "mode" and cookie.value == "relay" for cookie in cookies)
    content_type = headers.get("content-type", "")
    music_html = b"<title>\xe9\xa3\x9e\xe7\x89\x9b\xe9\x9f\xb3\xe4\xb9\x90</title>" in body
    if status != 200 or "text/html" not in content_type or not relay_cookie or not music_html:
        raise RuntimeError("relay activation did not return Music HTML with mode=relay")
    return status, app_url.rstrip("/")


def add_step(steps: list[FlowStep], name: str, outcome: str, status: int | None = None, note: str = "") -> None:
    steps.append(FlowStep(name=name, outcome=outcome, status=status, note=note))
    suffix = f" HTTP {status}" if status is not None else ""
    print(f"{outcome:4} {name}{suffix}{': ' + note if note else ''}")


def write_report(
    output: Path,
    target: EntryTarget,
    locator_data: dict[str, Any] | None,
    steps: list[FlowStep],
    verifier: Verifier | None,
) -> None:
    counts = {
        outcome: sum(step.outcome == outcome for step in steps)
        + (sum(result.outcome == outcome for result in verifier.results) if verifier else 0)
        for outcome in ("PASS", "FAIL", "SKIP")
    }
    address_counts = {}
    if locator_data:
        for key in ("fn", "ddns", "publicIpv4", "publicIpv6", "ipv4", "ipv6"):
            value = locator_data.get(key)
            address_counts[key] = len(value) if isinstance(value, list) else 0
    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "entry_url": target.redacted_url,
        "selected_relay": "https://{relayHost}:443",
        "summary": counts,
        "locator_address_counts": address_counts,
        "flow_steps": [asdict(step) for step in steps],
        "music_api_results": [asdict(result) for result in verifier.results] if verifier else [],
    }
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nReport: {output}")
    print(f"Summary: PASS={counts['PASS']} FAIL={counts['FAIL']} SKIP={counts['SKIP']}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Validate FN Connect discovery and Music relay access")
    parser.add_argument("--entry-url", default=DEFAULT_ENTRY_URL, help="FN Connect URL: https://fnos.net/{fnId}/music/")
    parser.add_argument("--env-file", type=Path, default=Path(".env"), help="dotenv file containing USERNAME/PASSWORD")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="redacted JSON report path")
    parser.add_argument("--timeout", type=float, default=15.0, help="per-request timeout in seconds")
    parser.add_argument("--insecure", action="store_true", help="disable TLS verification")
    parser.add_argument("--full", action="store_true", help="run all read-only Music API probes after relay login")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        target = parse_entry_url(args.entry_url)
    except ValueError as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 2

    steps: list[FlowStep] = []
    locator_data: dict[str, Any] | None = None
    verifier: Verifier | None = None
    cookies = http.cookiejar.CookieJar()
    opener = make_opener(cookies, not args.insecure)
    try:
        status = fetch_entry(target, opener, args.timeout)
        add_step(steps, "FN Connect entry page", "PASS", status)

        status, locator_data = discover_addresses(target, opener, args.timeout)
        add_step(steps, "FN ID locator", "PASS", status)

        relay_host, relay_port = select_relay(locator_data)
        add_step(steps, "HTTPS relay selected", "PASS")

        status, music_base_url = activate_relay(
            target, relay_host, relay_port, cookies, opener, args.timeout
        )
        add_step(steps, "relay mode activation", "PASS", status, "mode=relay cookie present")

        dotenv = load_dotenv(args.env_file)
        username = env_value(dotenv, "USERNAME")
        password = env_value(dotenv, "PASSWORD")
        if not username or not password:
            raise RuntimeError("USERNAME/PASSWORD missing")
        verifier = Verifier(
            Config(
                base_url=music_base_url,
                username=username,
                password=password,
                cookie=None,
                timeout=args.timeout,
                verify_tls=not args.insecure,
                search_query="音乐",
                output=args.output,
                include_write=False,
                confirm_destructive=False,
                login_username_field="username",
                login_password_field="password",
                write_scope="favorites-events",
            )
        )
        for cookie in cookies:
            verifier.client.cookies.set_cookie(cookie)
        if not verifier.login():
            raise RuntimeError("Music password login failed through relay")
        if args.full:
            verifier.run_read_probes()
        else:
            verifier.probe("initialization through relay", "GET", "/api/v1/initialization/state")
            verifier.probe("current user through relay", "GET", "/api/v1/user/me")
            verifier.probe(
                "tracks through relay",
                "GET",
                "/api/v1/track/list",
                query={"page": 1, "size": 1},
            )
    except (RuntimeError, URLError, TimeoutError, OSError) as exc:
        note = str(exc) if isinstance(exc, RuntimeError) else f"{type(exc).__name__}: network request failed"
        add_step(steps, "flow completion", "FAIL", note=note)

    write_report(args.output, target, locator_data, steps, verifier)
    failed = any(step.outcome == "FAIL" for step in steps)
    failed = failed or bool(verifier and any(result.outcome == "FAIL" for result in verifier.results))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
