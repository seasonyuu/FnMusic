# Liquid menu

Song sorting, album sorting, refresh, and playlist actions use `LiquidMenu` in
`core:designsystem`, through the shared `AppBarMenu` adapter. `LiquidMenuHost` wraps `MusicShell` outside
background capture, drawing the menu in the same window above navigation chrome.
The menu samples `LocalAppBarBackdrop`, which excludes the trigger and the menu.

## Trigger contract

`LiquidMenuTransition.Detached` is the default. The trigger remains visible, including
text-only and custom-shape controls. The menu opens below it with an 8dp gap, or above
when that side has more room. A limited viewport scrolls. Only the menu moves 8dp and
fades over 160ms; its text is not scaled from the trigger size.

`Attached` opts into liquid fusion on supported devices. Register the actual visible
surface with `LiquidMenuAnchorScope.surfaceModifier()`, and pass `foregroundModifier`
to the foreground-only content. The trigger's outer bounds remain the interaction
anchor. Supported contours are `Capsule` (including circles) and
`RoundedRectangle(radius)`. Unknown shapes should use `Detached`; an incomplete attached
registration also uses the detached path.

```kotlin
LiquidMenu(
    expanded = expanded,
    onDismissRequest = { expanded = false },
    onExpandedChange = { expanded = it },
    backdrop = backdrop,
    items = items,
    onSelect = onSelect,
    transition = LiquidMenuTransition.Attached,
    trigger = { toggle ->
        LiquidButton(
            onClick = toggle,
            backdrop = backdrop,
            modifier = Modifier.height(48.dp).then(surfaceModifier()),
            foregroundModifier = foregroundModifier,
        ) { Text("Sort") }
    },
)
```

Foreground content is composed once and recorded in a managed `GraphicsLayer`, then
replayed by the Host without semantic or input nodes. While hidden, the original control
continues recording into a discarded layer so changing labels remain current. Only its
foreground is replayed. Position and size error relative to the initial surface controls
foreground visibility: smooth fade from 35% error to full opacity at 5%. The closing
spring can finish its undershoot with visible content before the original control takes
over. Moving/removing the anchor or resizing the window cancels the session.

`Transient` is for explicitly registered temporary surfaces on bare-icon triggers.
It opens with the same fused shape as `Attached`, but closes by shrinking both blobs
and their separation to zero at the anchor center. The recorded icon returns at its
original size and position. A fade only at progress below 0.04 suppresses subpixel
highlights; negative spring overshoot stays empty. A short continuous closing blend
allows reversal without replacing the trigger layout or its interaction bounds.

## Rendering and interaction

- Android 13/API 33+: two rounded shapes with a smooth SDF union, backdrop blur,
  refraction, edge lighting, and finger glow. Text is outside the shader.
- API 31–32: blurred rounded menu. API 26–30: theme-colored solid menu.
- Disabled glass or shader compilation failure: solid menu. Disabled system
  animations: immediate open/close. No SDK minimum or production dependency upgrade.
- The upstream spring uses stiffness 120 and damping ratio `16 / (2 * sqrt(120))`.
  Closing starts with velocity at most -2.5. Unclamped progress retains overshoot.
- Menus prefer 200dp width, 32dp corners and a 12dp safe-area margin. Measured text
  sets row heights, at least 48dp. A constrained viewport scrolls instead of selecting
  while dragging. Non-scrolling menus select on release and cancel after an edge stretch.
- Held drags share `LiquidButton`'s directional stretch and resisted translation.
  Movement is measured in window coordinates to avoid feedback from the menu's own
  deformation. Release/cancel springs back with damping ratio 0.5 and stiffness 300;
  scrollable menus retain scroll priority.
- During closing, a fresh tap in the original anchor reverses the existing session.
  A held tap retains the session until release, even if the spring finishes first;
  cancellation or dragging out dismisses normally. Selection callbacks are not replayed.
- Stable item IDs preserve business identity. A selection dismisses once, then invokes
  the current callback. Disposal removes the overlay immediately. Escape and system
  back dismiss; arrows/Tab and Enter select. Focus returns to the trigger.

The pinned source and MIT attribution are documented in `third_party/README.md`.
The implementation reproduces the core geometry and interactions, not pixel-identical
Flutter optical rendering. Frame reports from emulators do not establish a real-device FPS guarantee.

## Repeatable verification

With the API 26, 31, 33 and 36 emulators connected:

```sh
python3 scripts/verify_liquid_menu.py --regressions --performance
```

The script runs `git diff --check`, both relevant Gradle unit test modules, builds
the instrumentation APKs, and executes the menu rendering, interaction, compatibility,
reduced-motion, anchor handoff, and all four business menu suites on each requested API. It installs only test APKs,
uses local fixture data, and does not require a NAS or credentials. The AVDs prepared
for this work are named `FnMenu_API_26`, `FnMenu_API_31`, `FnMenu_API_33` and the existing
API 36 device. Start them with the Android emulator/AVD manager before verification.

A focused run uses `--apis 36`; `--skip-build` reuses already built test APKs.
Use `--output build/reports/liquid-menu-adaptation` to keep this adaptation run
separate from previous menu investigations.
Missing devices, missing artifacts, unexpected skips and failed tests yield a nonzero
exit status. The four shader-specific tests (including two foreground handoff tests) are
explicitly inapplicable below API 33;
all behavior and fallback tests still run there.

Results live in `build/reports/liquid-menu/summary.json`, with per-API test logs,
PNG keyframes, screenshot diffs, a song-sort screenshot, and optional frame timing JSON.
Performance is reporting-only: 3 warm-up cycles and 20 measured cycles for the original
Material dropdown and the liquid menu. Reports include P50/P95, display frame budget,
and the fraction of recorded frames exceeding that budget.

For the wider existing glass/app-bar regression suite, add `--regressions`.
That option preserves failures; it does not whitelist existing bugs. The fallback
button test checks the intentional 75% surface opacity introduced in `8daac5a`,
composited over red and blue backgrounds in both themes, and retains the dark-theme
edge-highlight check. The static tab test samples the final parent composite at the
selected tab's center, checking that an opaque neutral probe is tinted to the exact
configured accent in both themes. It locates the interactive tab instead of the label
content that is duplicated by the color-mask layer.

Standalone rendering regression results are saved in
`build/reports/liquid-glass-rendering/`, separately from historical menu reports.

## Reference data and screenshot policy

Numeric fixtures are generated independently with double-precision equations from the
pinned upstream source. To inspect regenerated values without changing files:

```sh
python3 scripts/generate_liquid_menu_samples.py
```

`LiquidMenuGeometryTest` compares normalized fields within `1e-4`. The GPU suite
checks the drawn contour against an independent CPU distance oracle outside a 2px
edge band, checks backdrop changes, and rejects rectangular leaks. Eight keyframes
cover detachment, travel, settlement and closing undershoot.

Screenshot baselines are checked in under the design system's Android test assets,
separately for API 33 and API 36, using a 360×420px fixture at density/font scale 1.
They were recorded only after the independent geometry checks passed and visually
inspected. Regression comparisons allow at most 0.5% pixels with channel differences
over 8/255, and always emit a difference image. Normal verification **never updates**
these assets. A deliberate visual change requires rerunning the independent checks,
reviewing the generated PNGs and diffs, and explicitly replacing the relevant assets.
