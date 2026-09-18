# `:understand` — screen, element and journey semantics

**Owner: Daniel** · branch `feat/understanding`

Implements `ScreenUnderstander` from `:core`. Three backends behind one interface, composed with fallback in `HybridUnderstander`:

- `CloudVlm` — Gemini 2.5 Flash REST, temperature 0. Needs a key; skipped when absent.
- `LocalLlm` — OpenAI-style client for a local model server (`http://127.0.0.1:8080`). The embedded Gemma/LiteRT runtime is deliberately not in this module — the APK stays dependency-free.
- `HeuristicUnderstander` — no model, no network. The floor that always answers.

`UnderstandCache` is a durable file-backed cache keyed by screen/journey/tone id — rescanning the same structural id reuses the first run's exact bytes, which is what makes the pack stable.

Non-negotiable: strict JSON out with validation and repair, and caching keyed by screen id. A malformed model response must never end a scan, and temperature 0 alone does not make the pack stable — the cache does. Verified by hermetic JVM tests, including an end-to-end slice that runs `LocalLlm` against a real loopback HTTP endpoint.
