# AirPlay integration and verification

## Current milestone

The prototype gate passed on the local Mac receiver and an Android 15 Pixel 3:
strict authentication, discovery, audio transport, and explicit human listening
were verified. The retained full-player evidence includes a five-minute transport run.
Confirmed FLUSH was separately tested against the Mac and a decrypting fake
receiver; the user confirmed the six-second pause and clean recovery.

The Debug app now includes an experimental production playback path. ExoPlayer,
its queue and MediaSession remain authoritative. `:core:airplay` owns discovery,
Keystore credentials and a dedicated JNI/network worker. A forwarding AudioSink
converts decoded 16-bit mono/stereo PCM to stereo 44.1 kHz, applies bounded
backpressure and reports an estimated position using consumed frames plus the
sender's 1.5-second protocol latency. Remote passthrough/offload is disabled.
Other decoded channel layouts are currently rejected on the remote path.

The player has an AirPlay device sheet and pairing input. The mini-player shows
the selected target; the volume slider controls the receiver independently of
phone volume. Only same-package, same-UID MediaSession commands can select an
output. Successful connection triggers a stop/prepare at the existing queue
position. Failed connection keeps the previous output. Pause re-prepares from
the estimated audible position; seek/flush clears local, resampling and remote
buffers and waits for the receiver's FLUSH acknowledgement. Rejection or a
missing acknowledgement fails the connection. The connection sheet reminds the
user to inspect receiver-side approval dialogs and offers cancellation without
changing the current output. Selecting the active output again reuses it. Unexpected loss pauses playback
without selecting local output. Pairing is saved, but remote sessions are not
automatically restored or reconnected.

**Local Mac generated-audio acceptance passed.** A physical Pixel 3 completed
300 seconds of full-player streaming and queue/control checks; the user confirmed
audible playback, pause/resume, seek, track change, remote volume, and returning
output to the phone. Additional checks passed on the physical phone: real NAS song playback, media
notification actions and screen-off media keys. The user confirmed normal NAS
audio and visible notification/lock-screen controls. A dedicated API 37 emulator
passed the actual permission dialog (deny, then allow) and revocation/restart
checks. This acceptance is scoped to the local Mac and tested Android devices.

 Initial player runs showed advancing
playback and nonzero submitted PCM, but the user reported no audible output.
These are not passes; the ignored reports retain the failure. A subsequent
controlled run was audibly confirmed after removing the additional −24 dB
receiver attenuation from the low-amplitude fixture. Production volume still
defaults to 20%; test tones explicitly use 100% with peak PCM 1200/32768. Prototype success
must not be substituted for player-level audible output and control verification.
No HomePod, Apple TV or multi-room compatibility is claimed.

## Build and offline checks

Prerequisites: JDK 21, Android SDK 37, NDK 28.2.13676358, SDK CMake 3.22.1,
a host CMake and C++20 compiler, Python 3, and adb. The experimental Android
sender currently builds arm64-v8a and x86_64; other native ABIs are not verified.

```sh
python3 scripts/verify_airplay.py
./gradlew :core:airplay:connectedDebugAndroidTest
```

The first command builds the sanitizer-enabled native probe, executes the
upstream fake-receiver and socket tests, runs the verification-script tests,
and builds the Android test APK plus local unit tests. Native dependencies are
fetched at fixed commits; first build needs network access. The second command
runs JNI input validation and Keystore encryption/device-binding tests on the
connected Android device. The live test is skipped unless explicitly enabled.

Outputs: `build/airplay/*-report.json`, archived per-run reports under
`build/airplay/runs/`, build/test logs in the same ignored
directory, and Gradle's module test reports. The instrumented prototype APK is
`core/airplay/build/outputs/apk/androidTest/debug/airplay-debug-androidTest.apk`.
It is a test APK, not the regular FnMusic application or a launcher activity.

## Real Mac receiver

Enable AirPlay Receiver in macOS settings and allow the intended sender. The
runner does **not** modify these settings. The Mac may display a receiver-side
approval dialog; accept it on the Mac promptly, in addition to entering a PIN
when requested. FnMusic allows up to 120 seconds per receiver handshake response after TCP
connects (the TCP connect deadline remains 10 seconds, and the total host
connection wait remains 180 seconds). Expiry gives a specific prompt to check
receiver-side approval; this does not assert that every slow receiver has an
approval dialog. The current Mac's receiver log showed its own approval delegate
expiring after about 15 seconds, cancelling the prompt and rejecting SETUP with
HTTP 400. The sender's longer wait cannot extend that receiver-side UI deadline;
confirm promptly. FnMusic distinguishes SETUP refusal, receiver closure and its
own timer expiry instead of labeling all of them as a timeout.
Check the Mac approval dialog before
attributing this result to a protocol or network fault. It resolves this machine's ComputerName
and LocalHostName, verifies that the advertised service belongs to this Mac,
and chooses a matching LAN IPv4 address from its local interfaces. It will not
connect to the first receiver on a shared network, or use the loopback address.

```sh
python3 scripts/verify_airplay.py --mode mac --interactive --seconds 90
```

When prompted, read the screen PIN and enter it in the terminal. Input is not
echoed or recorded in logs. The sender generates alternating 440 Hz / 660 Hz /
silent one-second segments at low amplitude (peak 1800/32768) and explicitly
requests 100% receiver volume. Earlier probes set 20% before start(), which the
upstream reset to 100%; the fixture now makes that actual level explicit.
The duration begins **after** streaming starts, not before pairing. The same
samples and network core run through JNI on Android.

For a non-interactive agent run, omit `--interactive`. On `pin_required`, write
only the four digits into a temporary file with mode 0600, then atomically rename
it to `build/airplay/private/mac-pin` (or use `write_private` in the verifier). The
probe consumes and deletes it. Pairing has a bounded wait; rerun if the Mac
has dismissed its PIN. Saved host probe pairing files are mode 0600 in the
ignored `build/airplay/private` directory (0700), not repository configuration.
Android pairings are encrypted using Keystore and scoped to receiver identity.

```sh
# Discovery only: no pairing dialog and no sound.
python3 scripts/verify_airplay.py --mode android-discovery
python3 scripts/verify_airplay.py --mode android --interactive --seconds 90
# When more than one Android device is connected:
python3 scripts/verify_airplay.py --mode android --serial DEVICE_SERIAL --interactive
```

The Android runner incrementally rebuilds and installs the test APK, enables the local-network permission
on API 37+ **for the test package only**, scans for the selected Mac, and passes
its verified endpoint to the JNI probe. Discovery has its own result: supplying
an explicit endpoint does not count as successful Android discovery. If the
emulator's NAT blocks mDNS or return UDP, use a real Android phone on the same
LAN; no relay or fake success fallback is installed.

For non-interactive Android PIN input, send four digits on stdin to:

```sh
adb -s DEVICE_SERIAL shell run-as com.seasonyuu.fnmusic.core.airplay.test \
  sh -c '"cat > files/airplay-probe-pin.tmp && mv files/airplay-probe-pin.tmp files/airplay-probe-pin"'
```

Do not put a PIN in command arguments/history. The interactive runner handles
this input automatically. The prototype sends events but never raw protocol
payloads or credentials to instrumentation output.

After the 90-second gates pass, run `--seconds 300` for the five-minute soak.
Confirm listening explicitly; `--heard yes` is an operator attestation for that
run, never something an unattended runner should add. `--heard no` records an
audible failure. Without either, listening remains pending even when transport
checks pass. The status cannot be promoted to a full feature pass by this flag.

## Evidence and limitations

Reports distinguish `passed`, `failed`, `blocked`, and
`pending_manual_confirmation`. Exit codes are 0 for passed checks, 1 for a
failure, and 3 for a blocked or unconfirmed run. Individual unattended runs retain `feature_acceptance: incomplete`; only the
separate attested `checkpoint-report.json` combines the independent gates into
the scoped acceptance result.

A transport pass requires strict pairing, the requested continuous duration,
audio-packet submissions at least 90% of the expected rate, timing replies, and
an opened event channel without closure/decryption errors. The report preserves
the event reply count: macOS may send no events during a healthy stream. Actual
event request/reply processing is covered by the independent fake receiver tests. UDP submission counts are **not** delivery receipts.
Nonzero generated PCM and a connected receiver do not prove audible output.
The native fake receiver validates encrypted packets independently, but it also
does not substitute for actual Mac listening.

Prototype listening checklist: recognizable alternating tones and silence;
no distortion; sustained output beyond 90 seconds; output stops on teardown.
Production acceptance will additionally require pause/resume, seek, next/previous,
volume, local output restoration, queue preservation, disconnect handling,
notification controls, and a NAS-backed song. Generated-tone playback and the listed audible controls have passed on the
local Mac. NAS playback, notification actions, screen-off controls, visible
lock-screen controls, and API 37 permission handling have now also been tested.
Automatic observations and human confirmations remain separate report fields.

See `third_party/airplay2-sender/SOURCE.md` for exact provenance and notices.

### On-device pairing UI

Android live verification opens a test-only activity on the selected phone.
When the receiver requests a PIN, enter the current four-digit code on the phone
and tap **提交配对码**. Leading zeroes are preserved. The input is masked, excluded
from saved view state and autofill, consumed once in memory, and never logged.
**停止测试** or closing the activity with Back cancels the probe; sockets are released by
the native worker. The screen shows connection and streaming progress. This
activity is included only in the instrumentation APK, not the production app.


## Full player verification (explicit live test)

```sh
python3 scripts/verify_airplay.py --mode player --serial DEVICE_SERIAL --seconds 90
# After the short run and listening succeed:
python3 scripts/verify_airplay.py --mode player --serial DEVICE_SERIAL --seconds 300
```

This installs the Debug app and instrumentation APK, opens its debug-only
`AirPlayVerificationActivity`, generates two low-amplitude WAV tracks, verifies
the locally identified Mac against phone NSD discovery, and connects using the
actual service commands. Enter a requested PIN on the phone. No NAS account is
needed. The automated sequence checks continuous playback, nonzero PCM and
submitted-frame evidence, pause/resume, seek, next track, remote volume, queue
preservation and return to local output. Tests clean up the queue and output;
the script force-stops only the test Debug app on cancellation. Manual listening
and individual audible controls remain pending unless explicitly confirmed.
The test activity is not present in release builds.

Production APK: `app/build/outputs/apk/debug/app-debug.apk`.
Player reports: `build/airplay/player-report.json`, with immutable original runs
under `build/airplay/runs`. The final `checkpoint-report.json` links separate
prototype, generated-audio, NAS, manual UI, permission and offline evidence.

### NAS, system controls and permissions

```sh
# Requires an existing NAS login in the Debug app; credentials never leave it.
python3 scripts/verify_airplay.py --mode player --serial DEVICE_SERIAL --nas --system-controls
# Leave a 180-second window for the operator to use actual notification/lock-screen UI.
python3 scripts/verify_airplay.py --mode player --serial DEVICE_SERIAL --nas --manual-ui
# Dedicated API 37 emulator only: requests, grants and revokes this Debug app's permission.
python3 scripts/verify_airplay.py --mode permissions --serial EMULATOR_SERIAL
```

The NAS fixture uses the first two available songs and 20% receiver volume.
It checks the existing service and queue, actual notification PendingIntents,
and system media keys with the screen off. Media keys are distinct from visible
lock-screen UI; the latter requires the manual window. The runner temporarily
stops and replaces the Debug app's queue, and clears it on completion. It does
not modify NAS files or account credentials. The first two NAS songs should be
longer than the requested soak and 60-second seek target.

The manual window can end early by creating the empty marker
`files/airplay-manual-ui-done` with `adb shell run-as com.seasonyuu.fnmusic.debug`.
It records observed pause/resume/track transitions but never infers audible or
visible UI success. Stop other Android UI automation helpers before running these
tests: Android permits only one UiAutomation service per device.

Permission tests are opt-in and run only on a dedicated emulator. The runner
revokes the Debug app's `ACCESS_LOCAL_NETWORK` permission and clears its denial
flags before testing the actual system dialog; it revokes again before the
restart test. It leaves the permission denied and makes no Mac configuration
changes. API 37 emulator permission results do not substitute for physical-phone
mDNS discovery; that was verified separately on the Pixel 3.

Reports remain independent: `player-nas-report.json`,
`player-manual-ui-report.json`, and `permissions-report.json`. The original handshake-timeout result and UI test-harness failures remain in
run history. The user identified the Mac receiver's pending manual approval as
the explanation for the timeout-like behavior; the recorded timer expiry alone
does not establish an intermittent protocol fault. A retry passing does not
erase those observations. No automatic reconnection is implemented.

### Receiver Now Playing and controls

The service publishes title, artist, album, artwork, estimated position, duration,
and actual playing state to the selected receiver. Artwork uses the same
NAS-authenticated bitmap loader as Android notifications, is resized to at most
512 pixels and encoded as JPEG (maximum 512 KiB). URLs, NAS cookies and credentials
are never sent as metadata. Missing artwork does not stop playback; switching
tracks clears the previous artwork and cancels the old publication job.

The sender retains legacy DMAP support, sends RTP progress, and uses AirPlay 2
`updateMRNowPlayingInfo`, `updateMRSupportedCommands`, `updateMRPlaybackState`,
and `updateMRNowPlayingClient`. Only the MediaRemote path publishes artwork during
AirPlay 2 playback, avoiding competing delayed legacy metadata. Stable song and
artwork identifiers invalidate receiver caches on changes. Unicode plist strings
use UTF-16BE; an independent Python plist decoder verifies Chinese and emoji. Progress is estimated, with periodic corrections;
the receiver can extrapolate from the CFDate timestamp and playback rate.

The authenticated AirPlay 2 event channel accepts play, pause, toggle, next and
previous commands, marshals them onto the service main thread and applies them
only to the current ready connection. Candidate, replaced and closed sessions
cannot control playback. This does not advertise receiver-side seeking, shuffle,
repeat or a general DACP server. Those capabilities remain outside this addition.

Run `python3 scripts/verify_airplay.py --mode offline` for protocol and snapshot
checks; the native suite decrypts actual sender messages. For Mac verification,
use `--mode player --nas --receiver-ui --serial <phone>` and operate the **Mac**
Now Playing panel. Confirm title/artist/artwork, play/pause indication, and
Mac-initiated pause/resume/next/previous. The existing Android notification
checklist is not a substitute for this receiver-side checklist. HTTP 200 alone
also does not prove visible rendering or audible control behavior.
