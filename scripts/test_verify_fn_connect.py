import hashlib
import http.cookiejar
import json
import re
import tempfile
import unittest
from pathlib import Path

from verify_fn_connect import (
    AUTHX_PREFIX,
    FN_CONNECT_API_KEY,
    EntryTarget,
    activate_relay,
    discover_addresses,
    md5,
    parse_entry_url,
    select_relay,
    write_report,
)


class FakeResponse:
    def __init__(self, status, headers, body):
        self.status = status
        self.headers = headers
        self._body = body

    def read(self, _limit=None):
        return self._body


class RecordingOpener:
    def __init__(self, response, on_open=None):
        self.response = response
        self.on_open = on_open
        self.request = None

    def open(self, request, timeout):
        self.request = request
        if self.on_open:
            self.on_open(request, timeout)
        return self.response


def relay_cookie(domain="relay.fnos.net"):
    return http.cookiejar.Cookie(
        version=0,
        name="mode",
        value="relay",
        port=None,
        port_specified=False,
        domain=domain,
        domain_specified=True,
        domain_initial_dot=False,
        path="/",
        path_specified=True,
        secure=True,
        expires=None,
        discard=True,
        comment=None,
        comment_url=None,
        rest={"HttpOnly": None},
        rfc2109=False,
    )


class ParseEntryUrlTests(unittest.TestCase):
    def test_parses_fn_id_and_music_route(self):
        target = parse_entry_url("https://fnos.net/abcdef/music/")
        self.assertEqual(target.fn_id, "abcdef")
        self.assertEqual(target.app_path, "/music/")
        self.assertEqual(target.redacted_url, "https://fnos.net/{fnId}/music/")

    def test_rejects_invalid_entries(self):
        cases = (
            "http://fnos.net/abcdef/music/",
            "https://example.com/abcdef/music/",
            "https://fnos.net/",
            "https://fnos.net/short/music/",
        )
        for value in cases:
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse_entry_url(value)


class LocatorTests(unittest.TestCase):
    def test_locator_signatures_match_current_web_algorithm(self):
        target = EntryTarget(
            entry_url="https://fnos.net/abcdef/music/",
            fn_id="abcdef",
            app_path="/music/",
        )
        payload = {"code": 0, "msg": "", "data": {"fn": ["relay.fnos.net:443"]}}
        opener = RecordingOpener(
            FakeResponse(200, {"Content-Type": "application/json"}, json.dumps(payload).encode())
        )
        status, data = discover_addresses(target, opener, 3)
        self.assertEqual(status, 200)
        self.assertEqual(data["fn"], ["relay.fnos.net:443"])

        headers = {key.lower(): value for key, value in opener.request.header_items()}
        match = re.fullmatch(r"nonce=(\d{6})&timestamp=(\d+)&sign=([0-9a-f]{32})", headers["authx"])
        self.assertIsNotNone(match)
        nonce, timestamp, signature = match.groups()
        body_text = '{"fnId":"abcdef"}'
        signing_text = "_".join(
            (
                AUTHX_PREFIX,
                "/api/v1/fn/con",
                nonce,
                timestamp,
                md5(body_text),
                FN_CONNECT_API_KEY,
            )
        )
        self.assertEqual(signature, md5(signing_text))
        expected_fn_sign = hashlib.sha256(
            f"trim_connect`abcdef`{timestamp}`anna".encode()
        ).hexdigest()
        self.assertEqual(headers["fn-sign"], expected_fn_sign)

    def test_relay_selection_allows_only_https_fn_domains(self):
        self.assertEqual(select_relay({"fn": ["relay.fnos.net:443"]}), ("relay.fnos.net", 443))
        for value in ("relay.example.com:443", "relay.fnos.net:8443", "not a host"):
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                select_relay({"fn": [value]})


class RelayAndPrivacyTests(unittest.TestCase):
    def test_relay_activation_requires_cookie_and_music_html(self):
        cookies = http.cookiejar.CookieJar()

        def set_cookie(_request, _timeout):
            cookies.set_cookie(relay_cookie())

        body = "<!doctype html><title>飞牛音乐</title>".encode()
        opener = RecordingOpener(
            FakeResponse(200, {"Content-Type": "text/html; charset=utf-8"}, body),
            on_open=set_cookie,
        )
        target = EntryTarget("https://fnos.net/abcdef/music/", "abcdef", "/music/")
        status, base_url = activate_relay(target, "relay.fnos.net", 443, cookies, opener, 3)
        self.assertEqual(status, 200)
        self.assertEqual(base_url, "https://relay.fnos.net/music")
        self.assertEqual(opener.request.get_header("Origin"), "https://fnos.net")

    def test_report_does_not_persist_identifiers_or_addresses(self):
        target = EntryTarget("https://fnos.net/secretid/music/", "secretid", "/music/")
        locator_data = {
            "fn": ["secretid.fnos.net:443"],
            "publicIpv4": ["192.0.2.10"],
            "ipv4": ["192.168.1.2"],
        }
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.json"
            write_report(output, target, locator_data, [], None)
            text = output.read_text(encoding="utf-8")
        self.assertNotIn("secretid", text)
        self.assertNotIn("192.0.2.10", text)
        self.assertNotIn("192.168.1.2", text)
        self.assertIn("{fnId}", text)
        self.assertIn("{relayHost}", text)


if __name__ == "__main__":
    unittest.main()
