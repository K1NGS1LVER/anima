# `:core` — the contract

**Owner: Jacob** · branch `feat/knowledge-store`

Pure Kotlin/JVM. **No `android.*` import may enter this module** — that constraint is what lets the whole engine be unit-tested on a laptop with no emulator and no device, and it is enforced by a test (`NoAndroidImportsTest`).

| Package | What |
| :--- | :--- |
| `io.agents.anima.core` | Pack schema v1 (`Pack.kt`) and the cross-module interfaces (`Contracts.kt`). Frozen on Day 0. |
| `io.agents.anima.engine` | UIFormer pruning, weighted matcher, parameter extraction, skill model and replay runtime. |

Changing `Pack.kt` or `Contracts.kt` needs a PR approved by **Samuel and Jacob** — four people build against them.

Spec: [`KNOWLEDGE_PACK.md`](../../KNOWLEDGE_PACK.md).

## Your next tasks

Full detail, in order, in [`EXECUTION_PLAN.md`](../../EXECUTION_PLAN.md) §Jacob.

1. **J1 — de-duplicate the structural fingerprint.** `StableIdEngine.signature()` sorts resource-ids but does not `.distinct()` them, so the same list screen with five rows and with six rows hashes differently and one screen gets two ids across scans. Passes a hand-built unit test, fails the first real rescan. **Before anything else.**
2. **J2 — implement persistence** (`:store`). Opens gate **G3**, which blocks Neethu's viewer and the demo's saved-pack fallback.
3. **J3 — size budget against a real pack** once **G4** opens.
4. **J4 — prove diffing on real data** once **G5** opens.
5. **J5 — `.animapack` round trip**, including `PackLegacyReader`.

Also yours right now: **issue #5** — `fixtures/packs/golden.animapack` is a placeholder that does not load through `PackArchive.importPack()`. Replace it or delete it; two people are told to build against it.
