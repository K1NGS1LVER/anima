# `:understand` — screen, element and journey semantics

**Owner: Daniel** · branch `feat/understanding`

Implements `ScreenUnderstander` from `:core`. Three backends behind one interface, composed with fallback:

- `CloudVlm` — Gemini. Fast, high quality, the default while building.
- `OnDeviceLlm` — Gemma via MediaPipe/LiteRT. The privacy story and the offline path.
- `HeuristicUnderstander` — no model, no network. The floor that always answers.

Non-negotiable: strict JSON out with validation and repair, and **caching keyed by screen id**. A malformed model response must never end a scan, and temperature 0 alone does not make the pack stable — the cache does.
