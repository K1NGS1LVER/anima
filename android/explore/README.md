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
| `ScanOrchestrator.kt` | S1: turns one `ScanOutcome` into a `KnowledgePack` — drives `ScreenIdentifier` and `ScreenUnderstander` across every screen, assigns element ids, resolves the graph, sorts every array by id. Returns screenshots separately (`ScanResult.screenshots`) since `PackRepository.save()` has no parameter for image bytes yet — a real gap in the `:core` contract, documented in the class doc rather than worked around. |
| `ScanController.kt` | S3: the interface Neethu's scan-control screen builds against, plus `FakeScanController` — a scripted, deterministic implementation for building and testing with no device. |

Two invariants: the crawler **never taps a destructive control**, and the same app scanned twice **visits screens in the same order** — without the second, the byte-identical acceptance test cannot pass no matter how good the hashing is.

**S2 → S1 wired.** `ScanService.handleStartAction` builds a `ScanOrchestrator` around its `Explorer`, and once `orchestrator.scan()` returns, saves the pack via `ScanEngineProvider.repository` and updates the notification to say so (or says clearly if saving failed, which is a different problem from the crawl itself failing). The screenshot-persistence gap above still applies: a scan with screenshots logs a warning naming the count, since there is nowhere in the contract to put them yet.

## Your next tasks

Full detail in [`EXECUTION_PLAN.md`](../../EXECUTION_PLAN.md) §Samuel. S1–S3 are done; what's left needs the Redmi.

1. ~~**S1 — `ScanOrchestrator`.**~~ Done.
2. ~~**S3 — publish the `ScanController` signature.**~~ Done — gate **G6** is open. `ScanController.kt` + `FakeScanController` exist; tell Neethu.
3. **S4 — hardware bring-up** on the Redmi. Opens **G4** and **G5**, which make four people's work realistic. **Blocked — no device attached this session.**
4. **S5 — journey replay.** The differentiator: a pack that can prove itself. Blocked on S4.

Then: the drawer-opening edge swipe, and `ScanOutcome` coverage into `scan.coverage`.
