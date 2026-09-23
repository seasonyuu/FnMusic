# Development and verification

This guide keeps the build, architecture, protocol, and asset details out of the reader-facing READMEs. For behavior and settings, see [Features](features.md).

## Build from source

Requirements:

- Android SDK 37.
- JDK 21. Gradle provisions its daemon toolchain from [`gradle/gradle-daemon-jvm.properties`](../gradle/gradle-daemon-jvm.properties); the Android compile target is Java 17.
- Git submodules, including the pinned lyrics dependencies.

```sh
git clone --recurse-submodules https://github.com/seasonyuu/FnMusic.git
cd FnMusic
./gradlew :app:assembleDebug
```

For an existing checkout, run `git submodule update --init --recursive` before building. Install `app/build/outputs/apk/debug/app-debug.apk` with an in-place update if the app is already installed. Do not clear an existing app's data to fix an installation error. See [third-party dependencies](../third_party/README.md) for pinned sources and adapters.

If a user-level Gradle proxy is unavailable, disable it for this invocation:

```sh
./gradlew :app:assembleDebug -Dhttp.proxyHost= -Dhttps.proxyHost=
```

## Modules

FnMusic uses a single Android activity with Jetpack Compose. The main boundaries are:

| Module | Responsibility |
| --- | --- |
| `:app` | Entry point, navigation shell, and dependency wiring (`AppGraph` / `AppModule`) |
| `:core:model` | Domain models |
| `:core:network` | fnOS Music API client, FN Connect discovery, relay activation, `authx` signing, and session maintenance |
| `:core:player` | Media3 playback service and media session |
| `:core:designsystem` | Themes, liquid-glass components, and responsive navigation |
| `:data` | Repositories, Room / DataStore, LRC parsing, and optimistic favorites |
| `:feature:session` | Connection and sign-in UI |
| `:feature:music` | Library, player, settings, and administration UI |

The project uses Kotlin, Compose Material 3 and adaptive layouts, Media3 / ExoPlayer, Hilt, Room, DataStore, Paging 3, OkHttp / Retrofit, kotlinx.serialization, Coil 3, and [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass). Versions are pinned in the Gradle catalogs rather than this document.

## Protocol and verification

[`api.md`](../api.md) records the observed fnOS Music web protocol: FN Connect discovery, relay handshake, `authx` signing, and music endpoints. It contains no account credentials, cookies, or tokens. These behaviors are not an official public fnOS API.

| Tool | Purpose |
| --- | --- |
| [`verify_fn_connect.py`](../scripts/verify_fn_connect.py) | Full FN Connect flow: entry, discovery, relay, sign-in, and read-only probes |
| [`verify_api.py`](../scripts/verify_api.py) | Music-service read/write probes with a redacted JSON report |
| [`test_verify_fn_connect.py`](../scripts/test_verify_fn_connect.py) | Offline verifier unit tests |

Real-NAS write verification reads credentials only from an untracked `.env` based on [`.env.example`](../.env.example). Verification reports (`*-verification-report.json`) must not be committed. Read/write probes and playback against a live NAS are separate from local fixture tests.

Choose tests by changed behavior using [the testing guide](testing.md). The local starting points are:

```sh
./gradlew testDebugUnitTest
python3 -m unittest discover -s scripts -p 'test_verify_*.py' -v
```

Device tests require a dedicated test emulator because runner cleanup may remove apps or data. See [the preservation rules](../AGENTS.md#emulator-app-and-data-preservation) before running them.

## Security and privacy

Remembered login fields, login digest, tokens, and connection configuration are encrypted using Keystore-wrapped AES-GCM keys. The password digest can still be used for replay login, so logs must never include digests, cookies, tokens, request bodies, or media identifiers.

HTTPS uses system certificate authorities and has no certificate-bypass option. Plain HTTP is accepted only for loopback, RFC 1918, link-local, or IPv6 ULA addresses; redirect targets are checked again by the network interceptor.

Online lyric searches send song titles and artist names to the selected provider. NAS credentials are not sent to lyric providers. See [Features](features.md#lyrics) for source selection and caching behavior.

## Assets and trademarks

The [README icon](assets/fnmusic-icon.svg) is a vector adaptation of the original logo, with a fixed rounded-square mask to match the Android adaptive icon. The logo, Montserrat font, home decoration images, and some navigation SVGs come from the user's fnOS Music web build and are used for personal sideloading only. Sources, build hashes, and file digests are in [`web-assets/manifest.json`](web-assets/manifest.json).

The [README screenshots](assets/screenshots/) were captured from a development emulator. They contain music-library artwork returned by a NAS; the APK does not bundle those covers. Artwork and trademarks remain the property of their respective owners. Before distributing the app publicly, re-check the licensing of fnOS branding, logo, fonts, icons, and decoration assets.

The project has not selected an open-source license and is provided for personal learning and use only. It is not affiliated with or endorsed by fnOS.
