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

**Integration seam left open (S2 → S1):** `ScanService.onScanFinished` logs the `ScanOutcome` summary and finishes the notification, but does not yet turn the outcome into a `KnowledgePack` or save it. That wiring depends on `ScanOrchestrator` (S1), which lands separately. The TODO comment at that call site explains the reasoning; landing S1 means wiring `ScanEngineProvider.understander` + `.repository` through there.

## Your next tasks

Full detail in [`EXECUTION_PLAN.md`](../../EXECUTION_PLAN.md) §Samuel. This module is the critical path for the whole team.

1. **S1 — `ScanOrchestrator`.** Nothing currently turns a `ScanOutcome` into a `KnowledgePack`. No real pack has ever been produced. **First, ahead of everything.**
2. ~~**S3 — publish the `ScanController` signature.**~~ Done — gate **G6** is open. `ScanController.kt` + `FakeScanController` exist; tell Neethu.
3. **S4 — hardware bring-up** on the Redmi. Opens **G4** and **G5**, which make four people's work realistic.
4. **S5 — journey replay.** The differentiator: a pack that can prove itself.

Then: the drawer-opening edge swipe, and `ScanOutcome` coverage into `scan.coverage`.
