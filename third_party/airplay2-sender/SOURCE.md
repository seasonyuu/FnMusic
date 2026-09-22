# Pinned AirPlay sender

Upstream: https://github.com/akustikrausch/airplay2-sender-cpp
Revision: `fc913509039412790aa2427558d35178aa7842ea`

Vendored directories: `src`, `test`, `example`, `third_party`, `licenses`.
`CMakeLists.txt`, `LICENSE`, and `NOTICE` are retained. Upstream files are
unchanged. The FnMusic adapter lives in `core/airplay/src/main/cpp`.

Mbed TLS is pinned by upstream CMake to
`2ca6c285a0dd3f33982dd57299012dacab1ff206` (3.6.0). The adapter builds only
mbedcrypto, not the unused TLS/X.509 libraries. ed25519 is vendored at
`b1f19fab4aebe607805620d25a5e42566ce46a0e`.
See `LICENSE`, `NOTICE`, and `licenses/THIRD-PARTY-NOTICES.txt`.

This is an experimental interoperability dependency. Passing its fake-receiver
tests does not establish macOS receiver compatibility or audible playback.
FnMusic always enables strict receiver authentication.

FnMusic applies `core/airplay/src/main/cpp/patches/0001-confirmed-flush.patch`
to an ignored build-directory copy. The patch adds a serialized, acknowledged
FLUSH API, clears PCM/resampling/retransmission state, and treats FLUSH rejection
as fatal. The original vendored files remain byte-for-byte pinned and hash
checked. `fnmusic_flush_tests` reuses the pinned fake receiver to decrypt the
control request and PCM, and verifies rejection closes the session.

`0002-receiver-approval-wait.patch` adds a bounded receiver-response timeout
configuration. FnMusic uses 120 seconds after TCP connects, leaving the initial
TCP connect watchdog at 10 seconds and the host total connection limit at 180
seconds. This permits receiver-side manual approval without weakening strict
authentication; it does not assert that every delayed response is an approval
dialog. The fake-clock tests cover delayed success, eventual expiry, cancellation
and the unchanged connection watchdog.

`0003-now-playing.patch` adds progress and MediaRemote now-playing publication,
CFDate plist encoding, and dispatch of an allowlist of authenticated reverse
commands. The implementation uses the existing encrypted RTSP/event transport;
it does not open an unauthenticated control listener. Wire-format references:
- https://github.com/music-assistant/airplay-cli/blob/main/src/ap2_mrp.c
- https://github.com/owntone/owntone-server/blob/master/src/outputs/airplay_events.c
These are protocol references; their implementation code is not vendored or
linked. `fnmusic_now_playing_tests` checks decoded outbound metadata/artwork,
playing/paused state, seek, cover removal, and encrypted reverse commands,
including rejection of tampered events and commands after closure.

`0004-unicode-plist.patch` adds UTF-8/UTF-16BE conversion for non-ASCII plist
strings. The pinned encoder's ASCII-only shortcut cannot represent Chinese song
names. Native round-trip tests are supplemented by Python's independent plistlib
decoder to catch encoding errors shared by the sender and fake receiver.
