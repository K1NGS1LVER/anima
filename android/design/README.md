# `:design` — brand and design-token extraction

**Owner: Jiya** · branch `feat/design-extract`

Implements `DesignExtractor` from `:core`. Input: screenshots plus pruned node lists. Output: the `design_system` section of the pack — colors, typography, spacing, shape, components, light/dark, tone of voice.

Fully testable from `fixtures/packs/` with no device. The **rebuild test** — recreating 2–3 screens from the pack alone and showing them beside the originals — lives here and is an explicit judging criterion.

## Status

Y0–Y6 are in. Verify with:

```bash
cd android && ./gradlew :design:testDebugUnitTest    # 35 tests, no device
```

**The rebuild test (Y5) writes `design/build/reports/rebuild/index.html`** — three
captured screens beside three redrawn from the extracted tokens alone. Open it; that
page is the judging artefact.

### What is done

| | |
|---|---|
| **Y0** fixtures | Three real captures — Settings, Clock, Contacts — in `src/test/resources/fixtures/`, each a dump plus its screenshot. |
| **Y1** colours | Roles read through the node tree, not off a histogram. |
| **Y2** typography | Sizes measured and calibrated; family and weight reported null. |
| **Y3** spacing, shape | Spacing from bounds, radii from pixel curvature. |
| **Y4** components | Recurring patterns with `seen_on` counts. |
| **Y5** rebuild | Side-by-side page, drawn from tokens only. |
| **Y6** determinism | No RNG; asserted stable across repeat runs and reversed crawl order. |

### What is open, and why

- **Y7 light/dark** — waiting on **G4**. Every fixture is light, and the extractor emits
  only the modes it saw rather than inventing a dark palette from light screens.
- **Typography family and weight** — not in a `uiautomator` dump at all; they live on the
  `View`. These stay null until a VLM pass fills them, because a font name this module
  cannot see would be a fabrication in a pack whose whole value is being trustworthy.
- **Theme-attribute cross-check for colours** — same reason: no theme data in a dump.
- **`tone_of_voice`** — left to `ScreenUnderstander.toneOfVoice` on purpose.

### Three things worth knowing before you touch this

1. **Node bounds are touch targets, not drawn shapes.** Settings' list rows report the full
   1080px width while the card they draw is inset 42px. Anything measuring geometry from
   bounds alone will be wrong in a way that still looks plausible.
2. **A container repeats its children's text as its own label.** Measuring Settings' 72dp
   search bar instead of the 27dp label inside it invents a 53sp display face the app does
   not have.
3. **Never sample an element's colour at its centre pixel** — on a labelled control that
   reads the label. Use `Sampling.modalFill`, which masks children out.

### Capturing more fixtures

```bash
export MSYS_NO_PATHCONV=1    # Git Bash rewrites /sdcard paths without this
adb shell uiautomator dump /sdcard/d.xml && adb pull /sdcard/d.xml
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

`uiautomator` dumps whatever window is on top, so check the `package=` in the XML is the
app you meant — an ANR dialog or a permission prompt silently becomes your fixture
otherwise. `Observations` documents the rest.

Note: the `design_system` extracted here is the visual language of the app being *scanned*.
Anima's own look is `android/app/DESIGN.md`, owned by Neethu — different thing, same word.
