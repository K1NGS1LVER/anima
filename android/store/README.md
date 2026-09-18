# `:store` — persistence, assembly, diffing, export

**Owner: Jacob** · branch `feat/knowledge-store`

Implements `ScreenIdentifier` and `PackRepository` from `:core`.

- Stable-ID engine: SHA-256 over a canonical structural fingerprint. **Do not port `compute_screen_signature` from `anima.py:869`** — Python's `hash()` is salted per process.
- Canonical JSON writer: fixed key order, fixed float precision, `Locale.US`. Never map iteration order — `SkillJson.kt` documents why that bites specifically on Android.
- SQLite: app → scans → screens → elements → journeys. New schema; the `skills` table is the wrong shape.
- Diffing across scans, compaction to the size budget, and `.animapack` export/import.

Spec: [`KNOWLEDGE_PACK.md`](../../KNOWLEDGE_PACK.md).

## Your next tasks

See [`../core/README.md`](../core/README.md) — `:core` and `:store` are one workstream. The blocking item here is **J2: `save()` is an empty body and `load()`/`latest()` return `null`**, so nothing persists. That shuts gate **G3**, which blocks Neethu's viewer, cross-scan diffing, and the saved-pack fallback that `RELEASE_READINESS.md` names as the plan when a live scan stalls.
