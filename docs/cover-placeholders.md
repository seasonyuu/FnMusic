# Cover placeholders

`CoverImage` selects artwork by the shorter viewport dimension in dp, independently
of network resolution and screen density. Loading, error and missing-cover states
use the same artwork. All three variants use neutral charcoal, gray and silver
so they can appear alongside different page accent colors.

| Viewport | Resource in `core/designsystem/src/main/res/drawable-nodpi/` | Pixels | Detail |
| --- | --- | --- | --- |
| Up to 64 dp | `cover_placeholder_small.png` | 256 × 256 | Smooth disc, large label |
| Above 64 through 180 dp | `cover_placeholder_medium.png` | 640 × 640 | Sparse concentric grooves |
| Above 180 dp | `cover_placeholder.png` | 1254 × 1254 | Fine vinyl texture |

All three assets were recolored using the built-in ImageGen tool, using each
previous variant as its edit target. Small and medium exports were resized with
macOS `sips`. The album detail background also uses the updated large resource.

## Final edit prompts

Each variant uses this shared prompt followed by its variant-specific sentence:

Edit the provided album placeholder. Change ONLY the color palette to strictly achromatic neutral grayscale: charcoal black vinyl, neutral medium silver-gray paper center label, neutral dark gray background and neutral white-gray highlights. Absolutely no purple, burgundy, rose, blue, warm brown, or other hue anywhere. Preserve the exact composition, disc and label sizes, lighting geometry, margins and existing level of detail. This must work alongside any app accent color. Full bleed square, no text, no added objects, no border.

- Small variant: preserve smooth matte disc without grooves and large simple central label.
- Medium variant: preserve sparse broad concentric grooves and restrained satin reflections.
- Large variant: preserve realistic fine vinyl grooves, detailed material texture and soft dimensional highlights for expanded player artwork.
