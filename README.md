<img src="docs/assets/fnmusic-icon.svg" alt="FnMusic app icon" width="96" height="96">

# FnMusic

An unofficial third-party Android client for the music service of [fnOS](https://www.fnos.net/) (飞牛OS), built against the observed behavior of the current fnOS Music web client. It does not use or declare any official fnOS public API.

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/MinSdk-26%20%28Android%208.0%29-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/Media3-ExoPlayer%201.7-FF6F00)

> **Disclaimer**: This project is an unofficial client for personal sideloading. It is not affiliated with or endorsed by fnOS. All trademarks belong to their respective owners.

<!-- Screenshots: to be added. Suggested: phone home / now playing / playlist. -->

Current version `0.1.0`, early development stage — APIs and build setup may change without notice.

## Features

**Connection & sign-in**

- FN Connect discovery, relay activation, and username/password sign-in.
- Login fields (including the password) are remembered locally with Android Keystore encryption; signing out clears the active session while preserving the form, without signing in again on restart. Legacy installs restore address and account and require the password once.
- Custom HTTPS NAS address; plain-HTTP LAN addresses require explicit user confirmation.
- On cold start, the persisted token is validated against the protected `user/me` endpoint and refreshed by automatic re-login when expired, so the app never boots into an empty library.

**Music library**

- After authentication, enter the home screen immediately. Cached sections remain visible while independent catalog requests refresh in parallel; uncached sections show skeletons with local retry on failure.

- Home, music library, and profile navigation with a separate search entry. The music library groups all tracks, artists, albums, playlists, favorites, and recently played.
- Home collection shortcuts return to their source page. Each navigation tab preserves its own browsing stack, with page-local list sorting and scroll positions.

**Playback**

- Media3 background playback with notification and lock-screen controls.
- HTTP range streaming with a 512 MiB LRU media cache.
- LRC lyrics, playback position restore, previous and next track.

**Favorites & playlists**

- Favorite writes and `track_play` recently-played reporting.
- Playlist creation, rename, default covers, deletion, adding tracks, single/bulk track removal, and cleanup of dead tracks.

**Adaptive layout**

- Bottom navigation bar on phones, `NavigationRail` on medium screens, and a persistent sidebar on large screens.

## Installation

No release channel yet — build from source:

1. Prepare the environment:
   - Android SDK 37;
   - JDK 21 (Gradle provisions the build toolchain from [`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties); the compile target is Java 17).
2. Clone and build the debug APK:

   ```bash
   git clone --recurse-submodules <repository-url>
   cd fn-music
   ./gradlew :app:assembleDebug
   ```

For an existing checkout, run `git submodule update --init --recursive` before building.
Pinned lyrics dependencies and build adapters are documented in [third_party/README.md](third_party/README.md).

3. Install the output at `app/build/outputs/apk/debug/app-debug.apk`.

If your user-level Gradle config has a proxy that is not currently running, disable it for a single invocation:

```bash
./gradlew :app:assembleDebug -Dhttp.proxyHost= -Dhttps.proxyHost=
```

## Architecture & Tech Stack

Single-Activity + Jetpack Compose, layered into modules:

| Module | Responsibility |
|---|---|
| `:app` | App entry, navigation shell, and dependency wiring (`AppGraph` / `AppModule`) |
| `:core:model` | Domain models |
| `:core:network` | fnOS Music API client: FN Connect discovery, relay activation, `authx` signing, session maintenance |
| `:core:player` | Media3 playback service, background playback, and media session |
| `:core:designsystem` | Theming, liquid-glass style components, and dynamic bottom bar behavior |
| `:data` | Repository layer: Room / DataStore, LRC parsing, optimistic favorites |
| `:feature:session` | Connection and sign-in UI |
| `:feature:music` | Music UI |

Key dependencies: Kotlin 2.4, Compose BOM (Material 3 + adaptive), Media3 1.7 (ExoPlayer / media3-session / OkHttp DataSource), Hilt, Room, DataStore, Paging 3, OkHttp + Retrofit, kotlinx.serialization, Coil 3, and [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) (liquid glass effects).

## API Documentation & Verification

[`api.md`](api.md) (written in Chinese) documents the observed fnOS Music web protocol: FN Connect discovery, the relay handshake, the `authx` signing algorithm, and the music endpoints. It contains no account credentials, cookies, or tokens.

[`scripts/`](scripts/) holds the verification tooling:

| Script | Purpose |
|---|---|
| `verify_fn_connect.py` | Full FN Connect flow verification (entry → discovery → relay → sign-in → read-only probes) |
| `verify_api.py` | Read/write probes against the music service; emits a redacted JSON report |
| `test_verify_fn_connect.py` | Offline unit tests for the verifiers |

Credentials for real-NAS write verification are read only from the untracked `.env` (template in [`.env.example`](.env.example)); verification reports (`*-verification-report.json`) are never committed.

## Tests

```bash
./gradlew testDebugUnitTest
python3 scripts/test_verify_fn_connect.py -v
```

## Security & Privacy

- Remembered login fields, login digest, tokens, and connection config are encrypted with Keystore-wrapped AES-GCM keys.
- The password digest remains valid for replay login; logs must never record digests, cookies, tokens, request bodies, or media identifiers.
- HTTPS always validates against system CAs; the app offers no option to skip certificate verification.
- Plain HTTP is accepted only for loopback, RFC 1918, link-local, or IPv6 ULA addresses; the network interceptor re-checks redirect targets.
- Playlist write request bodies were verified through a full temporary-playlist lifecycle; the verifier cleans up in `finally`.

## Assets & Trademarks

The [README app icon](docs/assets/fnmusic-icon.svg) is a vector adaptation of the original logo, matching the Android adaptive icon with a fixed rounded-square mask for documentation.

The logo, Montserrat font, home decoration images, and some navigation SVGs come from the user's current fnOS Music web build and are used for personal sideloading only. Sources, build hashes, and file digests are listed in [`docs/web-assets/manifest.json`](docs/web-assets/manifest.json). Library covers are always loaded through the NAS API and never enter the repository.

To distribute the app publicly, re-verify the licensing of fnOS branding, logo, fonts, icons, and decoration assets first.

## License

No open-source license has been chosen yet. The code is provided for personal learning and use only.


### Profile and playback preferences

The Profile tab shows the current Music user, role, and server. It provides password changes, light/dark/system appearance, independent Liquid Glass controls, Wi-Fi/mobile streaming quality, and automatic playback cache settings. Password changes clear the old session and remembered password while retaining the server and username.

Administrators can manage music folders, submit scans and view their status, create/edit users and folder permissions, configure new-user access, and rename the Music server. These controls are hidden until the administrator role is confirmed. Original streaming remains the upgrade default; standard quality uses server-side Opus 128 kbps transcoding. Temporary cache capacity and track limits take effect without restarting and do not create permanent downloads.

### Lyrics sources

FnMusic automatically searches enabled online providers (NetEase Cloud Music, QQ Music, and Kugou; all enabled by default), then tries [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db), and ultimately falls back to the fnOS lyrics endpoint. Quality order is online word-timed lyrics → AMLL word-timed lyrics → online line-timed lyrics → fnOS. Equally suitable online results use the fixed NetEase, QQ, Kugou order. Matching checks title, artists, recording version, duration when available, and album disambiguation. Search requests send song titles and artist names to the selected providers; NAS credentials are never sent.

**Settings → 歌词 → 在线歌词搜索** combines independent **聚合歌词搜索** and AMLL switches, online source selection, Bikonoo (default)/GitHub/Dimeta/GDBA mirror selection, index updates, and combined lyric cache management. Only the selected AMLL mirror is queried; there is no automatic mirror fallback. Switching mirrors checks that mirror's index. Content-hash version, JSONL size, last check, and update state are shown. “Latest” refers to the selected mirror, which may lag upstream.

The player menu's **选择歌词** opens a bottom sheet for automatic mode, fixed fnOS lyrics, manual search, and a per-track timing offset. Manual search opens a separate sheet with on-demand provider tabs, per-tab results and scroll positions, candidate details, and preview. Both sheets share the player artwork colors; external lyric timing controls expand only when needed. Saved bindings continue to work even if that source's automatic search is disabled. A failed binding falls back to fnOS without erasing the selection. Accurate word timing and translations are supported; phonetic, background-vocal and duet layouts are not included.

Lyrics are cached separately from audio and search indexes. The combined cache display totals online and AMLL lyric caches; clearing it removes both along with online search results while preserving the AMLL index, track choices, offsets, audio cache, and currently displayed lyrics. Track choices are isolated by connection endpoint, account, and track GUID. Existing AMLL choices and indexes remain compatible. Online search results are cached for 24 hours, empty results for 30 minutes, and request failures are not cached as empty results.

Online provider adapters and YRC/QRC/KRC decoders are implemented in Kotlin; no downloaded JavaScript plugins run in the app. Network smoke checks are opt-in (`FNMUSIC_LYRICS_SMOKE=1 ./gradlew :data:testDebugUnitTest --tests '*OnlineLyricsSmokeTest' --rerun-tasks`); normal unit tests use local fixtures. Platform endpoint availability is not guaranteed.

AMLL's README distinguishes contributor-authored CC0 material from externally sourced material governed by its original terms. FnMusic displays contributors in the lyric selector and links to the upstream repository from AMLL settings; it does not bundle the database.
