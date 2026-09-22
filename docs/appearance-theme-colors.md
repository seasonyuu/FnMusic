# Appearance settings and official accent colors

Verified on 2026-09-14 against the official Music Web application served by the
configured NAS. The connection address was read from `.env`; no credentials or
connection details are included here. Static theme assets were publicly readable,
so password authentication was unnecessary.

The installed Android Feiniu app (`com.trim.app`, version 1.35.2) exposed only
light/dark appearance. The Web application provides the accent catalog below.

| Stable Web key | Android label | Source color |
| --- | --- | --- |
| red | 红色 | `#F62C55` |
| pink | 粉色 | `#F05672` |
| purple | 紫色 | `#C934E1` |
| blue | 蓝色 | `#1B73FB` |
| green | 绿色 | `#6BAB45` |

Order and default (`red`) come from
`/music/static/assets/bdf49c3c3882102fc017ffb661108c63-DZvWdWoT.js`.
Colors come from the `--ds-accent-*` declarations in
`/music/static/assets/337cc548d34c6d80b758ad814ddd793a-BCHGTeiZ.css`.
These are explicit deployed source values, not screenshot samples. Chinese labels
are literal translations of the Web keys, not claimed official marketing names.

Android retains its existing light/dark/system preference independently of accent.
Missing or unrecognized accent IDs use red. Theme choices are local preferences
and do not modify NAS settings. The appearance menu contains two inline illustrated light/dark options, an inline two-line theme color item,
and Liquid Glass. Color swatches have no ripple and do not navigate; old direct Liquid Glass navigation restores with an
appearance parent inserted. Previously saved display mode and theme color subpages restore to appearance. Existing system-mode preferences remain active until an explicit light/dark selection is made.

Filled accent buttons use a light foreground in dark mode (`onPrimary`,
`onSecondary`, and `onTertiary`). Light mode retains the accent contrast calculation;
text-only buttons continue using the readable surface foreground.

Source SHA-256 checksums:

- Catalog JS: `a9948819a1e0868c3cb0c3d05a94255dfe608eb6dd8837cb72d327e091c7e5a4`
- Tokens CSS: `c75bc277ad51f13f0d6e2a25a0fc38e2d8baa973bc367d01d0d89cc257183eef`
