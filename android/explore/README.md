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
