# `:understand` — screen, element and journey semantics

**Owner: Daniel** · branch `feat/understanding`

Implements `ScreenUnderstander` from `:core`. Three backends behind one interface, composed with fallback in `HybridUnderstander`:

- `CloudVlm` — Gemini 2.5 Flash REST, temperature 0. Needs a key; skipped when absent.
- `LocalLlm` — OpenAI-style client for a local model server (`http://127.0.0.1:8080`, loopback so it works with wifi genuinely off). This **is** the download-optional on-device backend (see decision below).
- `HeuristicUnderstander` — no model, no network. The floor that always answers.

### Decision D1 — on-device Gemma, download-optional (2026-09-18)

The plan asked for Gemma via MediaPipe/LiteRT and said: if the APK size cost is
unacceptable, make the download optional — but decide deliberately and write it down.

**Embedded: rejected.** A 2B-class TFLite Gemma is ~1.4 GB (int8) at minimum; the
debug APK is 22 MB. Embedding weights would blow the APK budget ~100× and make
every build and CI job drag gigabytes. Not a version mismatch, a structural cost.

**Download-optional: accepted.** `LocalLlm` already implements the on-device
story: weights live off-APK, served by the repo's existing LiteRT planner
(`LocalLiteRTPlanner`, loopback), and if the server is absent `completeText`
returns null and the hybrid chain degrades to heuristic — the scan never dies.
"Wifi genuinely off" is satisfied by construction (loopback needs no network) as
long as the model file is provisioned on the device at setup time — exactly what
the T-1 rehearsal's "both devices set up identically" does.

**Revisit trigger:** if the demo device cannot run the loopback planner, an
in-process MediaPipe tasks-genai runtime becomes required. That is a cut-list
item (#3) and a build-hosted decision for the app module, not a `:understand`
contract change; this document is where the switch gets recorded.

`UnderstandCache` is a durable file-backed cache keyed by screen/journey/tone id — rescanning the same structural id reuses the first run's exact bytes, which is what makes the pack stable.

Non-negotiable: strict JSON out with validation and repair, and caching keyed by screen id. A malformed model response must never end a scan, and temperature 0 alone does not make the pack stable — the cache does. Verified by hermetic JVM tests (42), including an end-to-end slice that runs `LocalLlm` against a real loopback HTTP endpoint — including one that dies mid-handshake, which must still degrade to the heuristic floor.

## Your next tasks

Full detail in [`EXECUTION_PLAN.md`](../../EXECUTION_PLAN.md) §Daniel. Status as of 2026-09-18:

1. **D1 — the on-device backend.** Decision made and recorded above (embedded rejected, download-optional accepted). Verification still needs the Redmi.
2. **D2 — journeys over real graph paths** once **G4** opens. "Screen 4 to Screen 9" is worse than no journey. ⏳ waiting on G4.
3. **D3 — prove the cache on a real rescan** once **G5** opens: zero model calls the second time, byte-identical text. ⏳ waiting on G5.
4. **D4 — the hard case in the brief.** Done and unit-tested: a screen with no labels and no roles still yields `FORM`, the correct purpose, and `TEXT`-typed fields; also exercised on the degraded path in the e2e slice.
5. **D5 — fallback rehearsal.** Module part done (throwing backend and dead-wire degradation degrade to heuristic, tested); the "network actually off" rehearsal is hardware, at T-4/T-1.
