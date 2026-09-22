# Accompanist lyrics

The submodules use NeriPlayer's forks and the exact gitlinks from NeriPlayer
commit `ac7bdea460b84a25e9b6feb1c66b2592360202f1`:

| Module | Repository | Pinned commit |
| --- | --- | --- |
| lyrics-core | https://github.com/cwuom/accompanist-lyrics-core | `825661a10101b6b17cdcf8f39e0d5a12ff8d21fd` |
| lyrics-ui | https://github.com/cwuom/accompanist-lyrics-ui | `d844e105bfb49104e1eabd03f700f26f492140e2` |

Initialize a checkout with `git submodule update --init --recursive`.
Do not use `git submodule update --remote`: revisions are deliberately pinned,
not automatically advanced to a moving branch.

`adapters/` contains FnMusic-owned Android Gradle projects. They compile the
unchanged submodule sources with FnMusic's SDK, Kotlin and Compose versions,
without importing NeriPlayer's application-specific convention plugins.
The UI adapter removes only the obsolete `ExperimentalAnimatableApi` import
and opt-in in a generated build copy, because FnMusic uses a newer Compose
version where `DeferredTargetAnimation` is stable. Submodule sources remain unchanged.
The upstream tests are included in these projects. Both submodules retain their
upstream Apache-2.0 licenses.

The first integration uses `KaraokeLineText` for timed lyrics. FnMusic still owns
translation display, manual browsing, automatic following, controls and seek
handling. Untimed lyrics retain the existing text renderer. The complete
`KaraokeLyricsView` (including interlude dots and list springs) is available but
not yet used by the screen. No NeriPlayer application source is copied.

The adapter preserves resolved accurate/estimated timings and Unicode segment
boundaries. Zero-duration segments receive a 1 ms rendering duration to avoid
upstream division by zero. This does not change the stored lyric data.

# Liquid menu reference

The menu morph equations are adapted from
[sdegenaar/liquid_glass_widgets](https://github.com/sdegenaar/liquid_glass_widgets/tree/097dea6993e474d5d04db33a4f6f2ea31dd40ac1),
pinned at `097dea6993e474d5d04db33a4f6f2ea31dd40ac1`.
The original MIT notice is in [licenses/liquid-glass-widgets.txt](licenses/liquid-glass-widgets.txt).
FnMusic implements its own Compose host, Android AGSL renderer and interaction handling;
it does not embed Flutter or import the upstream renderer.
The independent numeric fixture generator is `scripts/generate_liquid_menu_samples.py`.

# AirPlay sender

`airplay2-sender/` contains a pinned source snapshot of
[airplay2-sender-cpp](https://github.com/akustikrausch/airplay2-sender-cpp), revision
`fc913509039412790aa2427558d35178aa7842ea`. See its `SOURCE.md`, `LICENSE`,
`NOTICE`, and third-party notices. The FnMusic-owned CMake/JNI adapter is in
`core/airplay`. The player integrates this module through a Media3 AudioSink
and publishes receiver metadata and authenticated playback controls. Compatibility
is currently verified against the local Mac receiver; see `docs/airplay.md`.
