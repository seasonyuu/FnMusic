# Features and settings

This document expands on the [README](../README.md) without turning the project front page into a technical reference.

## Connection and sign-in

FnMusic supports FN Connect discovery and relay activation, username/password sign-in, and a custom HTTPS NAS address. Plain-HTTP LAN addresses require explicit confirmation. Login fields, including the password, are remembered locally with Android Keystore encryption. Signing out clears the active session while retaining the form and does not sign in automatically on restart. Older installs restore the address and account but require the password once.

On cold start, the saved token is checked with the protected `user/me` endpoint. If it has expired, the app attempts automatic re-login before opening the library.

## Browsing and playlists

After authentication, the Home screen opens immediately. Cached sections stay visible while independent catalog requests refresh; uncached sections show loading placeholders and a local retry action if a request fails. Home shortcuts return to their source collections. Each navigation tab keeps its own browsing stack, with local sorting and scroll positions.

The library groups all tracks, artists, albums, playlists, favorites, and recently played music. Search has its own entry point. You can create and edit playlists, use bundled or custom-photo covers, add or remove tracks individually or in bulk, clean up missing tracks, and delete playlists. Favorite changes and recent playback are synchronized with the server.

Phones use bottom navigation, medium screens use a navigation rail, and large screens use a persistent sidebar.

## Playback and audio output

Media3 provides background playback, notification and lock-screen controls, previous/next controls, position restore, and LRC lyrics. HTTP range streaming uses a 512 MiB LRU media cache. Local output and an experimental AirPlay path are available from the player. AirPlay playback has been verified with a macOS receiver; other receiver types remain unverified. See [local audio output](local-audio-output.md) and [AirPlay](airplay.md) for routing details and device limitations.

## Profile and administration

The Profile tab shows the current Music user, role, and server. It offers password changes, light/dark/system appearance, independent Liquid Glass controls, Wi-Fi/mobile streaming quality, and temporary playback cache settings. Changing a password clears the old session and remembered password while retaining the server and username. Original streaming is the default; standard quality uses server-side Opus 128 kbps transcoding. Cache capacity and track limits take effect without restarting and do not create permanent downloads.

After administrator status is confirmed, additional controls allow music-folder management, scans and task status, user and folder permissions, new-user access, and server renaming. Music folders are selected from authorized NAS directories with breadcrumbs, storage grouping, and duplicate checks. Library management includes per-folder and full scans, task cancellation and server-approved retries, and search-index rebuilding. Metadata and automatic-lyric defaults follow the web client; local-only metadata disables lyric downloads.

## Lyrics

FnMusic automatically searches enabled online providers (NetEase Cloud Music, QQ Music, and Kugou by default), then [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db), then the fnOS lyrics endpoint. It prefers online word-timed lyrics, AMLL word-timed lyrics, online line-timed lyrics, and finally fnOS lyrics. Equally suitable online results follow the saved provider order (NetEase, QQ, Kugou by default). Matching checks title, artists, recording version, duration when available, and album. Phonetic, background-vocal, and duet layouts are not currently supported.

**Settings → Lyrics → Online lyric search** has separate aggregated-search and AMLL switches, provider selection and drag-to-reorder priority, AMLL mirror selection (Bikonoo by default, GitHub, Dimeta, or GDBA), index updates, and combined lyric-cache management. Only the selected AMLL mirror is queried; there is no automatic mirror fallback. The screen shows content-hash version, JSONL size, last check, and update state. “Latest” means latest on the selected mirror, which may lag upstream.

The player's **Choose lyrics** menu offers automatic selection, fixed fnOS lyrics, manual search, and a per-track timing offset. Manual search has on-demand provider tabs, result lists, details, and previews. Tabs follow the saved online-provider order, with AMLL last; providers disabled for automatic search remain available manually. Saved track bindings continue to work even when that source's automatic search is disabled. A failed binding falls back to fnOS without deleting the selection. Accurate word timing and translations are supported.

Lyrics are cached separately from audio and search indexes. Clearing the combined lyric cache removes online and AMLL lyric content plus online search results, while retaining the AMLL index, track choices, offsets, audio cache, and currently displayed lyrics. Track choices are isolated by endpoint, account, and track GUID. Online search results are cached for 24 hours, empty results for 30 minutes, and request failures are not stored as empty results. Existing AMLL choices and indexes remain compatible.

Provider adapters and YRC/QRC/KRC decoders are implemented in Kotlin; the app does not execute downloaded JavaScript plugins. Normal tests use local fixtures. Opt-in network smoke checks are available with `FNMUSIC_LYRICS_SMOKE=1 ./gradlew :data:testDebugUnitTest --tests '*OnlineLyricsSmokeTest' --rerun-tasks`; provider endpoint availability is not guaranteed.

AMLL distinguishes contributor-authored CC0 material from externally sourced material governed by its original terms. FnMusic shows contributors in the lyric selector and links to the upstream repository from AMLL settings; it does not bundle the database.

## About and open-source notices

**Profile → About** shows the installed version, source repository, and a manual stable-release update check. The result shows a short plain-text preview of release notes and opens the matching GitHub Release in a browser for the full notes and APK; the app does not download or install APKs. Development builds compare their base version and explicitly label stable releases as a reference. Update requests use a separate unauthenticated client and never carry NAS credentials.

**About → Open-source licenses** provides the FnMusic code license and an offline dependency list with full license texts and native/source attributions. AboutLibraries generates variant-specific metadata at build time; maintenance details are in [`config/aboutlibraries/README.md`](../config/aboutlibraries/README.md). The project's [license scope](../LICENSE-SCOPE.md) distinguishes code from third-party assets.
