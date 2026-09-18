# `:core` — the contract

**Owner: Jacob** · branch `feat/knowledge-store`

Pure Kotlin/JVM. **No `android.*` import may enter this module** — that constraint is what lets the whole engine be unit-tested on a laptop with no emulator and no device, and it is enforced by a test (`NoAndroidImportsTest`).

| Package | What |
| :--- | :--- |
| `io.agents.anima.core` | Pack schema v1 (`Pack.kt`) and the cross-module interfaces (`Contracts.kt`). Frozen on Day 0. |
| `io.agents.anima.engine` | UIFormer pruning, weighted matcher, parameter extraction, skill model and replay runtime. |

Changing `Pack.kt` or `Contracts.kt` needs a PR approved by **Samuel and Jacob** — four people build against them.

Spec: [`KNOWLEDGE_PACK.md`](../../KNOWLEDGE_PACK.md).
