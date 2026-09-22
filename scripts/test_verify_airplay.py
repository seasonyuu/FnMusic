import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import verify_airplay as airplay


class ReceiverSelectionTests(unittest.TestCase):
    def test_reads_identity_and_port_from_selected_service(self):
        self.assertEqual(("My-Mac.local.", 7000, "02464E4D5553"), airplay.parse_service(
            "My Mac._airplay._tcp.local. can be reached at My-Mac.local.:7000\n"
            "deviceid=02:46:4E:4D:55:53 features=0x1"))

    def test_missing_identity_cannot_select_receiver(self):
        with self.assertRaises(airplay.Blocked):
            airplay.parse_service("can be reached at Other-Mac.local.:7000")

    def test_off_host_service_is_rejected(self):
        from subprocess import CompletedProcess
        def cmd(args, **kwargs):
            return CompletedProcess(args, 0, "My Mac\n" if args[-1] == "ComputerName" else "My-Mac\n", "")
        with patch.object(airplay.sys, "platform", "darwin"), patch.object(airplay, "command", side_effect=cmd), patch.object(
            airplay, "bounded_dns", return_value="can be reached at Other-Mac.local.:7000 deviceid=02:46:4E:4D:55:53"
        ):
            with self.assertRaises(airplay.Blocked):
                airplay.local_receiver()

    def test_multiple_android_devices_require_selection(self):
        from subprocess import CompletedProcess
        result = CompletedProcess([], 0, "List of devices attached\nemulator-5554\tdevice\nphone\tdevice\n", "")
        with patch.object(airplay, "command", return_value=result):
            with self.assertRaises(airplay.Blocked):
                airplay.select_android(None)
            self.assertEqual("phone", airplay.select_android("phone"))


class ReportTests(unittest.TestCase):
    def run_report(self, probe, heard=None):
        with tempfile.TemporaryDirectory() as tmp:
            args = ["verify_airplay.py", "--mode", "mac"]
            if heard:
                args += ["--heard", heard]
            with patch.object(airplay, "OUT", Path(tmp)), patch.object(airplay.sys, "argv", args), patch.object(
                airplay, "mac_probe", return_value=probe
            ):
                code = airplay.main()
                return code, json.loads((Path(tmp) / "mac-report.json").read_text())

    def test_transport_pass_does_not_imply_audio_or_complete_feature(self):
        code, report = self.run_report({"transport": {"status": "passed"}, "discovery": "passed"})
        self.assertEqual(3, code)
        self.assertEqual("pending_manual_confirmation", report["status"])
        self.assertEqual("incomplete", report["feature_acceptance"])

    def test_hearing_cannot_override_transport_failure(self):
        code, report = self.run_report({"transport": {"status": "failed"}, "discovery": "passed"}, "yes")
        self.assertEqual(1, code)
        self.assertEqual("failed", report["status"])

    def test_missing_discovery_is_not_a_complete_android_pass(self):
        code, report = self.run_report({"transport": {"status": "passed"}, "discovery": "blocked"}, "yes")
        self.assertEqual(3, code)
        self.assertEqual("blocked", report["status"])

    def test_private_input_permissions(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "pin"
            airplay.write_private(p, "1234")
            self.assertEqual(0o600, p.stat().st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()


class PlistInteroperabilityTests(unittest.TestCase):
    def test_unicode_and_date_with_independent_apple_plist_decoder(self):
        import plistlib
        import subprocess
        import datetime
        binary = airplay.OUT / "host" / "fnmusic_now_playing_tests"
        if not binary.exists():
            self.skipTest("Run verify_airplay.py --mode offline to build native fixture")
        data = subprocess.check_output([binary, "--unicode-fixture"])
        decoded = plistlib.loads(data)
        self.assertEqual(decoded["title"], "歌曲 🎵 Café")
        self.assertEqual(decoded["timestamp"], datetime.datetime(2001, 1, 1, 0, 1, 40, 500000))
