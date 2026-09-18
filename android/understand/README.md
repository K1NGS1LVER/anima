# `:understand` — screen, element and journey semantics

**Owner: Daniel** · branch `feat/understanding`

Implements `ScreenUnderstander` from `:core`. Three backends behind one interface, composed with fallback in `HybridUnderstander`:

- `CloudVlm` — Gemini 2.5 Flash REST, temperature 0. Needs a key; skipped when absent.
- `LocalLlm` — OpenAI-style client for a local model server (`http://127.0.0.1:8080`). The embedded Gemma/LiteRT runtime is deliberately not in this module — the APK stays dependency-free.
- `HeuristicUnderstander` — no model, no network. The floor that always answers.

`UnderstandCache` is a durable file-backed cache keyed by screen/journey/tone id — rescanning the same structural id reuses the first run's exact bytes, which is what makes the pack stable.

Non-negotiable: strict JSON out with validation and repair, and caching keyed by screen id. A malformed model response must never end a scan, and temperature 0 alone does not make the pack stable — the cache does. Verified by hermetic JVM tests, including an end-to-end slice that runs `LocalLlm` against a real loopback HTTP endpoint.

## Your next tasks

Full detail in [`EXECUTION_PLAN.md`](../../EXECUTION_PLAN.md) §Daniel.

1. **D1 — the on-device backend.** The one item left open, and it is the privacy claim in the pitch. It must work with wifi genuinely off, because that is how it gets rehearsed at T-1.
2. **D2 — journeys over real graph paths** once **G4** opens. "Screen 4 to Screen 9" is worse than no journey.
3. **D3 — prove the cache on a real rescan** once **G5** opens: zero model calls the second time, byte-identical text.
4. **D4 — the hard case in the brief:** a screen with no labels and no roles still gets a correct purpose and correctly typed fields. Expect to be asked about this one.
5. **D5 — fallback rehearsal** with the network actually off, not simulated.
