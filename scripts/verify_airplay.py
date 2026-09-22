#!/usr/bin/env python3
"""Explicit, bounded AirPlay verification. Offline by default; never infer audible output."""
from __future__ import annotations

import argparse
import getpass
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import queue
import re
import signal
import subprocess
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "airplay"
PACKAGE = "com.seasonyuu.fnmusic.core.airplay.test"
REVISION = "fc913509039412790aa2427558d35178aa7842ea"


class Blocked(RuntimeError):
    pass


def command(args, *, timeout=600, **kwargs):
    return subprocess.run([str(a) for a in args], cwd=ROOT, timeout=timeout,
                          text=True, capture_output=True, **kwargs)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(path)


def bounded_dns(args):
    try:
        return command(["dns-sd", *args], timeout=4).stdout
    except subprocess.TimeoutExpired as e:
        return e.stdout.decode() if isinstance(e.stdout, bytes) else (e.stdout or "")


def parse_service(text):
    endpoint = re.search(r"can be reached at (\S+):(\d+)", text)
    device = re.search(r"\bdeviceid=([0-9a-fA-F:]{17})\b", text)
    if not endpoint or not device:
        raise Blocked("The selected Mac is not advertising a usable AirPlay service")
    return endpoint.group(1), int(endpoint.group(2)), device.group(1).replace(":", "").upper()


def local_receiver():
    if sys.platform != "darwin":
        raise Blocked("Live verification currently requires the local macOS receiver")
    name = command(["scutil", "--get", "ComputerName"]).stdout.strip()
    local_name = command(["scutil", "--get", "LocalHostName"]).stdout.strip()
    if not name or not local_name:
        raise Blocked("Cannot establish the identity of this Mac")
    host, port, identity = parse_service(bounded_dns(["-L", name, "_airplay._tcp", "local."]))
    if host.rstrip(".").casefold() != (local_name + ".local").casefold():
        raise Blocked("AirPlay hostname does not match this Mac")
    addresses = bounded_dns(["-G", "v4", host])
    local_ips = set(re.findall(r"\binet (\d+\.\d+\.\d+\.\d+)", command(["ifconfig"]).stdout))
    candidates = re.findall(r"\b\d+\.\d+\.\d+\.\d+\b", addresses)
    for candidate in candidates:
        ip = ipaddress.ip_address(candidate)
        if candidate in local_ips and not ip.is_loopback and ip.is_private:
            return candidate, port, identity
    raise Blocked("No local LAN IPv4 address matches this Mac's advertised receiver")


def verify_vendor():
    vendor = ROOT / "third_party/airplay2-sender"
    manifest = json.loads((vendor / "UPSTREAM_SHA256.json").read_text())
    for name, expected in manifest.items():
        actual = hashlib.sha256((vendor / name).read_bytes()).hexdigest()
        if actual != expected:
            raise RuntimeError("Vendored source differs from its pinned manifest: " + name)


def build_host():
    verify_vendor()
    native = OUT / "host"
    for name, args in [
        ("configure", ["cmake", "-S", ROOT / "core/airplay/src/main/cpp", "-B", native,
                       "-DFNMUSIC_AIRPLAY_SANITIZE=ON"]),
        ("build", ["cmake", "--build", native, "--target", "fnmusic_airplay_probe",
                   "raop_core_tests", "raop_loop_tests", "fnmusic_probe_tests", "fnmusic_flush_tests", "fnmusic_now_playing_tests", "-j", "8"]),
    ]:
        r = command(args)
        (OUT / f"host-{name}.log").write_text(r.stdout + r.stderr)
        if r.returncode:
            raise RuntimeError(f"Host {name} failed; inspect build/airplay/host-{name}.log")
    return native / "fnmusic_airplay_probe"


def read_lines(proc, events):
    for line in proc.stdout:
        events.put(line.rstrip())
    events.put(None)


def get_pin_async(pin_answers):
    # Never echo a PIN, include it in process arguments, or store it in a report.
    value = getpass.getpass("AirPlay PIN on this Mac (4 digits): ").strip()
    pin_answers.put(value)


def monitor(args, *, seconds, pin_writer, interactive, on_cancel=None):
    """Keep a deadline even while waiting for PIN input or a stuck child."""
    events, pins = queue.Queue(), queue.Queue()
    proc = subprocess.Popen([str(a) for a in args], cwd=ROOT, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, bufsize=1)
    threading.Thread(target=read_lines, args=(proc, events), daemon=True).start()
    deadline = time.monotonic() + seconds + 200
    result = None
    pin_requested = False
    try:
        while time.monotonic() < deadline:
            try:
                line = events.get(timeout=0.25)
            except queue.Empty:
                line = ""
            if line is None:
                break
            if line.startswith("RESULT "):
                result = json.loads(line[7:])
            elif line.startswith("EVENT ") or "airplay_event=" in line:
                event = line.split("EVENT ", 1)[-1] if line.startswith("EVENT ") else line.split("airplay_event=", 1)[1]
                # Fixed categories only; raw native logs/endpoints are not printed.
                if re.fullmatch(r"[a-z_]+", event):
                    print("AirPlay:", event, flush=True)
                    if event == "pin_required" and not pin_requested:
                        pin_requested = True
                        if interactive:
                            threading.Thread(target=get_pin_async, args=(pins,), daemon=True).start()
                        else:
                            print("Awaiting PIN: enter it on the Android test screen; for the Mac CLI, see docs/airplay.md.", flush=True)
            try:
                pin = pins.get_nowait()
            except queue.Empty:
                pin = None
            if pin is not None:
                if not re.fullmatch(r"\d{4}", pin):
                    raise Blocked("PIN must contain four digits")
                pin_writer(pin)
        else:
            raise Blocked("Probe exceeded its bounded deadline")
        code = proc.wait(timeout=10)
        return code, result
    finally:
        if proc.poll() is None:
            if on_cancel:
                try:
                    on_cancel()
                except Exception:
                    pass  # Still stop the child if the device disconnected.
            proc.send_signal(signal.SIGINT)
            try:
                proc.wait(timeout=8)
            except subprocess.TimeoutExpired:
                proc.terminate()
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()


def write_private(path, value):
    temporary = path.with_name(path.name + ".tmp")
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        os.fchmod(f.fileno(), 0o600)
        f.write(value)
    temporary.replace(path)


def mac_probe(args):
    binary = build_host()
    host, port, identity = local_receiver()
    state = OUT / "private"
    state.mkdir(mode=0o700, exist_ok=True)
    state.chmod(0o700)
    pin = state / "mac-pin"
    pin.unlink(missing_ok=True)
    # Hash receiver identity in the filename; plaintext credentials remain private/ignored.
    creds = state / (hashlib.sha256(identity.encode()).hexdigest()[:16] + ".json")
    try:
        code, result = monitor([binary, host, port, args.seconds, identity, pin, creds] +
                               (["controls"] if args.controls else []),
                               seconds=args.seconds, pin_writer=lambda p: write_private(pin, p),
                               interactive=args.interactive)
    finally:
        pin.unlink(missing_ok=True)
    if result is None:
        raise RuntimeError(f"Native probe did not produce a report (exit {code})")
    if code != 0 and result["status"] == "passed":
        result.update(status="failed", reason="probe_process_failed")
    return {"transport": result, "discovery": "passed", "environment": "mac_host"}


def select_android(serial):
    output = command(["adb", "devices"]).stdout
    devices = [l.split()[0] for l in output.splitlines() if re.search(r"\sdevice$", l)]
    if serial:
        if serial not in devices:
            raise Blocked("Requested Android device is not connected/authorized")
        return serial
    if len(devices) != 1:
        raise Blocked("Connect one Android device or pass --serial explicitly")
    return devices[0]


def android_probe(args, discovery_only=False):
    serial = select_android(args.serial)
    host, port, identity = local_receiver()
    adb = ["adb", "-s", serial]
    verify_vendor()
    build = command(["./gradlew", ":core:airplay:assembleDebugAndroidTest"])
    (OUT / "android-live-build.log").write_text(build.stdout + build.stderr)
    if build.returncode:
        raise RuntimeError("Android probe build failed; inspect build/airplay/android-live-build.log")
    apk = ROOT / "core/airplay/build/outputs/apk/androidTest/debug/airplay-debug-androidTest.apk"
    if not apk.exists():
        raise Blocked("Android build did not produce the probe APK")
    installed_apk_sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()
    installed = command([*adb, "install", "-r", "-t", apk], timeout=120)
    if installed.returncode:
        # Recheck authorization after installation: USB can disconnect while
        # building or transferring the APK. Keep raw adb output out of reports.
        select_android(serial)
        raise Blocked("Android probe APK installation failed")
    sdk = int(command([*adb, "shell", "getprop", "ro.build.version.sdk"]).stdout.strip())
    if sdk >= 37:
        permission = command([*adb, "shell", "pm", "grant", PACKAGE, "android.permission.ACCESS_LOCAL_NETWORK"])
        if permission.returncode:
            raise Blocked("Cannot grant local network permission to the test APK")
    if discovery_only:
        command([*adb, "shell", "run-as", PACKAGE, "rm", "-f", "files/airplay-discovery-result.json"])
        command([*adb, "shell", "am", "instrument", "-w", "-r",
                 "-e", "class", "com.seasonyuu.fnmusic.core.airplay.AirPlayProbeTest#discoversExplicitlySelectedMac",
                 "-e", "airplay_discovery", "true", "-e", "airplay_device_id", identity,
                 PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"], timeout=45)
        raw = command([*adb, "exec-out", "run-as", PACKAGE, "cat", "files/airplay-discovery-result.json"])
        try:
            result = json.loads(raw.stdout)
        except ValueError as exc:
            raise Blocked("Discovery instrumentation produced no report") from exc
        result["environment"] = "android_emulator" if serial.startswith("emulator-") else "android_device"
        result["sdk"] = sdk
        result["apk_sha256"] = installed_apk_sha256
        return result
    def private_input(filename, value):
        # filename is a fixed internal literal, never user-controlled shell syntax.
        r = command([*adb, "shell", "run-as", PACKAGE, "sh", "-c",
                     f"'cat > files/{filename}.tmp && mv files/{filename}.tmp files/{filename}'"], input=value)
        if r.returncode:
            raise Blocked("Cannot send private probe input to the Android test APK")
    # Avoid accepting a previous report if instrumentation aborts before the test starts.
    command([*adb, "shell", "run-as", PACKAGE, "rm", "-f", "files/airplay-probe-result.json"])
    code, _ = monitor([
        *adb, "shell", "am", "instrument", "-w", "-r",
        "-e", "class", "com.seasonyuu.fnmusic.core.airplay.AirPlayProbeTest#streamsToExplicitlySelectedMac",
        "-e", "airplay_live", "true", "-e", "airplay_host", host,
        "-e", "airplay_port", str(port), "-e", "airplay_device_id", identity,
        "-e", "airplay_seconds", str(args.seconds), PACKAGE + "/androidx.test.runner.AndroidJUnitRunner",
    ], seconds=args.seconds + 12, pin_writer=lambda p: private_input("airplay-probe-pin", p),
       interactive=args.interactive,
       on_cancel=lambda: private_input("airplay-probe-cancel", "cancel"))
    r = command([*adb, "exec-out", "run-as", PACKAGE, "cat", "files/airplay-probe-result.json"])
    if r.returncode:
        raise Blocked("Android instrumentation produced no report; inspect its test status")
    try:
        result = json.loads(r.stdout)
    except (ValueError, TypeError) as exc:
        raise Blocked("Android instrumentation produced no valid report") from exc
    result.setdefault("automatic_status", result["status"])
    result["manual_controls"] = {item: "pending_manual_confirmation" for item in
        ("pause_resume", "seek_no_old_audio", "track_change", "remote_volume", "local_restore")}
    result["environment"] = "android_emulator" if serial.startswith("emulator-") else "android_device"
    result["apk_sha256"] = installed_apk_sha256
    if code != 0:
        result["transport"].update(status="failed", reason="instrumentation_process_failed")
    return result


def offline():
    build_host()
    checks = {}
    for name, cmd in [
        ("native_protocol_and_sockets", ["ctest", "--test-dir", OUT / "host", "--output-on-failure"]),
        ("verification_script", [sys.executable, "-m", "unittest", "discover", "-s", "scripts", "-p", "test_verify_airplay.py"]),
        ("android_build_and_unit", ["./gradlew", ":core:airplay:assembleDebug", ":core:airplay:assembleDebugAndroidTest", ":core:airplay:testDebugUnitTest",
                                    ":core:player:testDebugUnitTest", ":app:assembleDebug", ":app:assembleDebugAndroidTest"]),
    ]:
        r = command(cmd)
        (OUT / f"{name}.log").write_text(r.stdout + r.stderr)
        checks[name] = "passed" if r.returncode == 0 else "failed"
    return {"checks": checks, "status": "passed" if all(v == "passed" for v in checks.values()) else "failed"}


def permission_probe(args):
    serial = select_android(args.serial)
    if not serial.startswith("emulator-"):
        raise Blocked("Permission verification requires a dedicated emulator; it revokes the test app permission")
    adb = ["adb", "-s", serial]
    if int(command([*adb, "shell", "getprop", "ro.build.version.sdk"]).stdout.strip()) < 37:
        raise Blocked("Permission verification requires Android API 37 or later")
    build = command(["./gradlew", ":app:assembleDebug", ":app:assembleDebugAndroidTest"])
    (OUT / "permission-build.log").write_text(build.stdout + build.stderr)
    if build.returncode:
        raise Blocked("Permission test build failed")
    package = "com.seasonyuu.fnmusic.debug"
    permission = "android.permission.ACCESS_LOCAL_NETWORK"
    for apk in [ROOT / "app/build/outputs/apk/debug/app-debug.apk", ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
        if command([*adb, "install", "-r", "-t", apk]).returncode:
            raise Blocked("Permission test APK installation failed")
    checks = {}
    for name in ("deniedThenGrantedThroughActualSystemDialog", "revokedPermissionNeverStartsDiscoveryAfterRestart"):
        command([*adb, "shell", "pm", "revoke", package, permission])
        command([*adb, "shell", "pm", "clear-permission-flags", package, permission, "user-set", "user-fixed"])
        result = command([*adb, "shell", "am", "instrument", "-w", "-r", "-e", "class",
                          "com.seasonyuu.fnmusic.AirPlayPermissionTest#" + name, "-e", "airplay_permissions", "true",
                          package + ".test/androidx.test.runner.AndroidJUnitRunner"], timeout=120)
        (OUT / ("permission-" + name + ".log")).write_text(result.stdout + result.stderr)
        checks[name] = "passed" if re.search(r"OK \(1 test\)", result.stdout) else "failed"
        if checks[name] != "passed":
            break
    return {"status": "passed" if len(checks) == 2 and all(v == "passed" for v in checks.values()) else "failed",
            "checks": checks, "environment": "android_api37_emulator", "manual_listening": "not_applicable"}


def player_probe(args):
    serial = select_android(args.serial)
    _, _, identity = local_receiver()
    verify_vendor()
    build = command(["./gradlew", ":app:assembleDebug", ":app:assembleDebugAndroidTest"])
    (OUT / "player-build.log").write_text(build.stdout + build.stderr)
    if build.returncode:
        raise Blocked("Player verification APK build failed")
    adb = ["adb", "-s", serial]
    package = "com.seasonyuu.fnmusic.debug"
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    installed_apk_sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()
    for path in [apk, ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
        if command([*adb, "install", "-r", "-t", path], timeout=120).returncode:
            raise Blocked("Player verification APK installation failed")
    if int(command([*adb, "shell", "getprop", "ro.build.version.sdk"]).stdout.strip()) >= 37:
        if command([*adb, "shell", "pm", "grant", package, "android.permission.ACCESS_LOCAL_NETWORK"]).returncode:
            raise Blocked("Player verification requires local network permission")
    command([*adb, "shell", "run-as", package, "rm", "-f", "files/airplay-player-result.json"])
    monitor([*adb, "shell", "am", "instrument", "-w", "-r", "-e", "class",
             "com.seasonyuu.fnmusic.AirPlayPlaybackTest#realServicePreservesQueueAndControlsRemoteOutput",
             "-e", "airplay_player", "true", "-e", "airplay_device_id", identity,
             "-e", "airplay_seconds", str(args.seconds),
             "-e", "airplay_system_controls", str(args.system_controls).lower(),
             "-e", "airplay_nas", str(args.nas).lower(),
             "-e", "airplay_manual_ui", str(args.manual_ui).lower(),
             "-e", "airplay_receiver_ui", str(args.receiver_ui).lower(),
             package + ".test/androidx.test.runner.AndroidJUnitRunner"],
            seconds=args.seconds + 120, pin_writer=lambda _: None, interactive=False,
            on_cancel=lambda: command([*adb, "shell", "am", "force-stop", package]))
    raw = command([*adb, "exec-out", "run-as", package, "cat", "files/airplay-player-result.json"])
    try:
        result = json.loads(raw.stdout)
    except ValueError as exc:
        raise Blocked("Player verification produced no report") from exc
    result.setdefault("automatic_status", result["status"])
    result["manual_controls"] = {item: "pending_manual_confirmation" for item in
        ("pause_resume", "seek_no_old_audio", "track_change", "remote_volume", "local_restore")}
    if args.receiver_ui:
        result["receiver_ui"] = {key: "pending_manual_confirmation" for key in
            ("unicode_metadata", "artwork_after_next_previous", "playing_paused_indicator", "mac_pause_resume", "mac_next_previous")}
    result["environment"] = "android_emulator" if serial.startswith("emulator-") else "android_device"
    result["apk_sha256"] = installed_apk_sha256
    result["full_player_integration"] = "implemented_pending_acceptance"
    result["manual_listening"] = "passed" if args.heard == "yes" else "failed" if args.heard == "no" else "pending_manual_confirmation"
    if result["status"] == "passed" and result["manual_listening"] != "passed":
        result["status"] = result["manual_listening"]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["offline", "mac", "android", "android-discovery", "player", "permissions"], default="offline")
    parser.add_argument("--seconds", type=int, default=90, help="90-second prototype; 300-second soak")
    parser.add_argument("--serial")
    parser.add_argument("--system-controls", action="store_true", help="Player: exercise actual notification actions and screen-off media keys")
    parser.add_argument("--receiver-ui", action="store_true", help="Player: keep output active to verify the Mac Now Playing panel and receiver controls")
    parser.add_argument("--manual-ui", action="store_true", help="Player: keep output active for up to 180 seconds for notification/lock-screen interaction")
    parser.add_argument("--nas", action="store_true", help="Player: use two songs from the existing on-device NAS login")
    parser.add_argument("--controls", action="store_true", help="Mac prototype: confirmed FLUSH and six-second pause")
    parser.add_argument("--interactive", action="store_true", help="Read PIN without echoing it")
    parser.add_argument("--heard", choices=["yes", "no"], help="Explicit human observation of this run only")
    args = parser.parse_args()
    if args.receiver_ui:
        args.manual_ui = True
    if (args.system_controls or args.nas or args.manual_ui) and args.mode != "player":
        parser.error("--system-controls and --nas require --mode player")
    if args.controls and args.mode != "mac":
        parser.error("--controls currently requires --mode mac")
    if not 90 <= args.seconds <= 600:
        parser.error("--seconds must be between 90 and 600")
    if args.mode in ("offline", "android-discovery", "permissions") and args.heard:
        parser.error("Offline tests cannot establish audible output")
    OUT.mkdir(parents=True, exist_ok=True)
    sources = ROOT
    digest = hashlib.sha256()
    source_files = []
    for directory in ("core/airplay/src", "core/player/src", "core/model/src", "feature/music/src",
                      "app/src", "scripts"):
        source_files.extend(p for p in (ROOT / directory).rglob("*")
                            if p.is_file() and p.suffix in (".kt", ".cpp", ".h", ".xml", ".py", ".patch", ".txt"))
    for source in sorted(source_files):
        digest.update(str(source.relative_to(sources)).encode())
        digest.update(source.read_bytes())
    report = {"adapter_sha256": digest.hexdigest(), "schema": 1, "mode": args.mode, "upstream_revision": REVISION,
              "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
              "manual_listening": ("not_applicable" if args.mode in ("offline", "android-discovery", "permissions") else
                                   "passed" if args.heard == "yes" else "failed" if args.heard == "no" else "pending_manual_confirmation"),
              "full_player_integration": "implemented_pending_acceptance"}
    try:
        report.update(permission_probe(args) if args.mode == "permissions" else offline() if args.mode == "offline" else mac_probe(args) if args.mode == "mac" else player_probe(args) if args.mode == "player" else android_probe(args, discovery_only=args.mode == "android-discovery"))
        if "transport" in report:
            report["status"] = report["transport"]["status"]
            if report["status"] == "passed" and report["manual_listening"] != "passed":
                report["status"] = report["manual_listening"]
            if report["status"] == "passed" and report.get("discovery") != "passed":
                report["status"] = "blocked"
    except (Blocked, subprocess.TimeoutExpired, FileNotFoundError) as e:
        report.update(status="blocked", reason=str(e) if isinstance(e, Blocked) else type(e).__name__)
        print(str(e), file=sys.stderr)
    except KeyboardInterrupt:
        report.update(status="blocked", reason="cancelled")
    except Exception as e:
        report.update(status="failed", reason=type(e).__name__)
        print(str(e), file=sys.stderr)
    report["feature_acceptance"] = "incomplete"
    report_name = args.mode + ("-receiver-ui" if args.receiver_ui else "-manual-ui" if args.manual_ui else "-nas" if args.nas else "-system" if args.system_controls else "")
    path = OUT / f"{report_name}-report.json"
    write_json(path, report)
    archive = OUT / "runs" / (args.mode + "-" + str(time.time_ns()) + ".json")
    write_json(archive, report)
    print(f"Report: {path}\nStatus: {report['status']}")
    return 0 if report["status"] == "passed" else 1 if report["status"] == "failed" else 3


if __name__ == "__main__":
    sys.exit(main())
