# `:explore` — the autonomous crawler

**Owner: Samuel** · branch `feat/explorer`

The exploration policy: what to do next, when to stop, and what never to touch. This is the module that makes a scan autonomous rather than scripted.

| File | What |
| :--- | :--- |
| `Frontier.kt` | Deterministic ordered queue of unexplored actions. |
| `SafetyEnvelope.kt` | Deny-list for destructive controls, package boundary, recovery. |
| `ExplorationPolicy.kt` | Action selection and novelty scoring. |
| `Explorer.kt` | The capture → identify → enqueue → act → observe loop. |
| `ScanBudget.kt` | Step, depth and wall-clock ceilings. |

Two invariants: the crawler **never taps a destructive control**, and the same app scanned twice **visits screens in the same order** — without the second, the byte-identical acceptance test cannot pass no matter how good the hashing is.

## Your next tasks

Full detail in [`EXECUTION_PLAN.md`](../../EXECUTION_PLAN.md) §Samuel. This module is the critical path for the whole team.

1. **S1 — `ScanOrchestrator`.** Nothing currently turns a `ScanOutcome` into a `KnowledgePack`. No real pack has ever been produced. **First, ahead of everything.**
2. **S3 — publish the `ScanController` signature** (gate **G6**). Neethu is blocked on the shape, not the behaviour, so publish it early and tell her.
3. **S4 — hardware bring-up** on the Redmi. Opens **G4** and **G5**, which make four people's work realistic.
4. **S5 — journey replay.** The differentiator: a pack that can prove itself.

Then: the drawer-opening edge swipe, and `ScanOutcome` coverage into `scan.coverage`.
