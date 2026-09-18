# `:design` — brand and design-token extraction

**Owner: Jiya** · branch `feat/design-extract`

Implements `DesignExtractor` from `:core`. Input: screenshots plus pruned node lists. Output: the `design_system` section of the pack — colors, typography, spacing, shape, components, light/dark, tone of voice.

Fully testable from `fixtures/packs/` with no device. The **rebuild test** — recreating 2–3 screens from the pack alone and showing them beside the originals — lives here and is an explicit judging criterion.
