# `:design` — brand and design-token extraction

**Owner: Jiya** · branch `feat/design-extract`

Implements `DesignExtractor` from `:core`. Input: screenshots plus pruned node lists. Output: the `design_system` section of the pack — colors, typography, spacing, shape, components, light/dark, tone of voice.

Fully testable from `fixtures/packs/` with no device. The **rebuild test** — recreating 2–3 screens from the pack alone and showing them beside the originals — lives here and is an explicit judging criterion.

## Your next tasks

Full detail in [`EXECUTION_PLAN.md`](../../EXECUTION_PLAN.md) §Jiya. Nothing is built yet, so start at Y0.

0. **Y0 — make your own fixtures, about ten minutes.** `fixtures/packs/golden.animapack` is a placeholder with no screenshots in it (issue #5), and screenshots are this module's entire input. Do not wait for anyone:

   ```bash
   adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
   adb shell uiautomator dump && adb pull /sdcard/window_dump.xml
   ```

   `UIFormer.prune(xml, 1080 to 2400)` gives the node list; PNG bytes plus those nodes build a `ScreenObservation`, which is what `DesignExtractor.extract()` takes. Commit three or four under `src/test/resources/`.

1. **Y1–Y4** — colors, typography, spacing and shape, components.
2. **Y5 — the rebuild test.** Your headline and an explicit judging criterion. Start it earlier than feels comfortable: it is the item most likely to be cut for time and the one we least want to cut.
3. **Y6 — determinism.** k-means with a random seed would break the byte-identical requirement from inside this module. Seed it or use a deterministic algorithm.
4. **Y7 — light/dark**, once **G4** opens. Design for single-mode being normal; `setUiMode()` needs `WRITE_SECURE_SETTINGS`, granted over adb on the demo device only.

Note: the `design_system` you extract is the visual language of the app being *scanned*. Anima's own look is `android/app/DESIGN.md`, owned by Neethu — different thing, same word.
