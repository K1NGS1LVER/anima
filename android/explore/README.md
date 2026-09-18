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
| `ScanOutcome.kt` | What one scan produced, plus `ScanListener` for live progress. |
| `ScanService.kt` | S2: the foreground service that runs one scan to completion, with a persistent notification (screens found, elapsed) and a Stop action wired to `AnimaAccessibilityService.shouldHalt`. Started with `ScanService.start(context, targetPackage, budget)`, stopped with `ScanService.stop(context)`. |
| `ScanEngineProvider.kt` | Manual service locator: holds `ScreenIdentifier` (defaults to `StableIdEngine`), and the `ScreenUnderstander`/`PackRepository` the app's composition root must set before `ScanService` can run a scan (it fails fast and stops itself otherwise, since `:explore` cannot see `:understand`/`:store`'s concrete implementations). |
| `ScanNotificationText.kt` | Pure (no `android.*`) formatting for the service's notification text — unit-tested on the plain JVM in `ScanNotificationTextTest.kt`. |

Two invariants: the crawler **never taps a destructive control**, and the same app scanned twice **visits screens in the same order** — without the second, the byte-identical acceptance test cannot pass no matter how good the hashing is.

**Integration seam left open (S2 → S1):** `ScanService.onScanFinished` logs the `ScanOutcome` summary and finishes the notification, but does not yet turn the outcome into a `KnowledgePack` or save it. That wiring depends on `ScanOrchestrator` (S1), which lands in a separate branch/worktree and did not exist yet when S2 was built. The TODO comment at that call site explains the reasoning; whoever lands S1 next should wire `ScanEngineProvider.understander` + `.repository` through there.
