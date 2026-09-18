# Execution Plan — now to demo day

> **This is the runbook.** [PLAN.md](PLAN.md) says what we are building and why; this says who does what, in what order, and what they must wait for.
>
> Contract: [KNOWLEDGE_PACK.md](KNOWLEDGE_PACK.md) · Briefs: [ASSIGNMENTS.md](ASSIGNMENTS.md) · Status: [CHECKLIST.md](CHECKLIST.md) · Shipping: [RELEASE_READINESS.md](RELEASE_READINESS.md) · Log: [CURRENT_PROGRESS.md](CURRENT_PROGRESS.md)

## How to use this with a coding agent

Point your agent at this file and your own section. The sections are written to be executable: each task names the files it touches, the module it lives in, the thing it must wait for, and the command that proves it is done.

Three rules for your agent, and they matter more than anything else here:

1. **Only edit files inside your own module.** If you need something from another module, that is an interface change to discuss, not a file to reach into. A PR that edits someone else's module gets rejected on sight, however good it is.
2. **Never tick a checklist box on the strength of the code looking right.** Run the command. This project already has one episode of a module being marked ✅ COMPLETE when it had never been compiled.
3. **Respect the dependency gates.** Each phase below names what blocks it and gives a command to check. If the gate is shut, do the unblocked work in your section instead of guessing at the interface — a guessed interface costs two people a day when it turns out wrong.

Dates are relative to the build deadline, **T**. Samuel pins the real dates at the top of the first standup; everything below is written in T-minus so it stays correct whenever we start.

---

## Where we are — verified, not assumed

Everything in this table was checked against the merged `dev` on 2026-09-18, not read off a commit message.

| Module | Owner | State |
| :--- | :--- | :--- |
| `:core` | Jacob | ✅ Schema, contracts, `StableIdEngine`, `CanonicalJson`, `PackCompactor`. 78 tests. |
| `:capture` | Samuel | ✅ Screenshots, multi-window, scroll, `isScrollable`. |
| `:explore` | Samuel | ✅ Frontier, policy, safety envelope, budgets, recovery. 33 tests. |
| `:understand` | Daniel | ✅ Cloud / local / heuristic backends, caching, JSON repair. 38 tests. |
| `:store` | Jacob | ⚠️ **Persistence is a stub.** See below. `diff()`, `CanonicalJson` and `PackArchive` are real. |
| `:design` | Jiya | ⬜ Empty. |
| `:app` | Neethu | ⬜ Still the retired 3-act demo Activity. |

**149 JVM tests green, 25 Python tests green, debug APK 22.3 MB, CI green on `dev`.**

### The three things that are not true yet

Worth stating plainly, because two of them are load-bearing for the headline judging criteria.

**1. Nothing is wired end to end.** `Explorer` produces a `ScanOutcome`. `HybridUnderstander` produces a `ScreenProfile`. `KnowledgeStore` accepts a `KnowledgePack`. Nothing turns the first into the third. There is no `ScanService`, no orchestrator, and no code path that has ever produced a real `KnowledgePack`. **This is the critical path and it is Samuel's.**

**2. `KnowledgeStore` does not persist anything.** `save()` is an empty body with a comment describing what it would do; `load()` and `latest()` return `null`. So today a scan cannot be reopened, two scans cannot be diffed, and the viewer has nothing to load. The demo's stated fallback — "open a previously saved pack" — does not exist yet. **Jacob's first task, above everything else.**

**3. `StableIdEngine.signature()` folds in duplicate resource-ids.** It sorts them but does not de-duplicate, so a list screen showing five rows and the same screen showing six rows produce different fingerprints and therefore different screen ids. That is the exact failure the contract warns about: *"Anything that changes between two runs on the same screen must not feed the hash."* It will pass a hand-built unit test and fail the first real rescan. **Jacob, before anything else in `:store`.**

### The critical path

```
Samuel: ScanOrchestrator + ScanService
            │
            ├──> first real scan on the Redmi
            │         │
            │         ├──> a real fixture pack  ──> Jiya's tokens are real, Neethu's viewer has real data
            │         └──> a second scan        ──> Jacob's diff and the stability test become provable
            │
            └──> journey replay  ──> the differentiator
```

Everyone can start now against the golden fixture. But the *quality* of four people's work is gated on Samuel producing a real scan, so that is the thing to protect from distraction.

---

## Phase map

| Phase | When | What | Gate to leave it |
| :--- | :--- | :--- | :--- |
| **P1** | now → T-4 | Build. Five parallel workstreams. | Every module's own tests green on its branch. |
| **P2** | T-3 | **Feature freeze.** Everything merges to `dev`. Integration only after this. | `dev` green, end-to-end scan works on the primary device. |
| **P3** | T-2 | **Regression pass** against the merged build, by every owner. | Every owner has run their checklist against `dev`, not their branch. |
| **P4** | T-1 | **Two timed rehearsals**, one with wifi off. Release build. Saved packs loaded. | Two clean runs, backup device set up identically. |
| **P5** | T | Demo. No code changes. | — |

---

## Dependency gates — check before you start, not after

Each gate is a real command. Run it; do not ask.

| # | Gate | Blocks | Check |
| :--- | :--- | :--- | :--- |
| **G1** | Golden fixture is *usable* | Jiya, Neethu | `unzip -l fixtures/packs/golden.animapack` must list `pack.json` **and** `screens/*.webp`, and `pack.json` must have a non-empty `screens[]` — ⚠️ **shut, see below** |
| **G2** | `:core` contracts frozen | everyone | `git log --oneline -1 -- android/core/src/main/kotlin/io/agents/anima/core/Contracts.kt` — ✅ **open** |
| **G3** | `KnowledgeStore` really persists | Neethu's viewer, Jacob's diff, saved-pack fallback | `grep -A2 "override fun save" android/store/src/main/kotlin/io/agents/anima/store/KnowledgeStore.kt` — must not be an empty body |
| **G4** | End-to-end scan produces a pack | realistic work for Jiya and Neethu, all hardware claims | `ls fixtures/packs/real-*.animapack` |
| **G5** | Two real scans of one app exist | stability test, diff demo | `ls fixtures/packs/real-*-scan{1,2}.animapack` |
| **G6** | `ScanController` API published | Neethu's scan-control screen | `ls android/explore/src/main/kotlin/io/agents/anima/explore/ScanController.kt` |

### G1 is shut, and I said otherwise — correcting it

`fixtures/packs/golden.animapack` exists, which is what I originally checked, but the content is a placeholder and not usable:

- its single entry is named **`dummy_pack.json`**, while `PackArchive.importPack()` looks for `pack.json` — so the fixture does not load through our own importer;
- `screens[]` and `journeys[]` are empty, and there is no `design_system` or `graph`;
- **there are no screenshots in it at all**, and a screenshot is the entire input to `:design`.

Checking that a file exists is not checking that it is worth anything. The gate above is rewritten to assert content.

**Jacob:** replace it with a fixture that round-trips through `PackArchive`, or delete it and let Samuel's first real scan (G4) be the first fixture. A placeholder that four people are told to build against is worse than an empty directory, because it is silently wrong rather than visibly missing.

**Jiya and Neethu:** you are not blocked in practice — see Jiya's section for how to make your own input in ten minutes. But do not build test expectations against `golden.animapack` as it stands.

**If your gate is shut:** do the work in your section marked *"unblocked now"*. Do not invent the other side of an interface — raise it in the channel and keep moving on something else. Every hour spent building against a guessed interface is an hour spent twice.

---

# P1 — Build (now → T-4)

## Samuel — `feat/explorer` — the end-to-end scan

You are the critical path. Everything else in P1 is parallel; this is not.

### S1. `ScanOrchestrator` — **do this first, before anything else**

New file, `:explore`. This is the thing that does not exist.

```kotlin
class ScanOrchestrator(
    private val explorer: Explorer,
    private val understander: ScreenUnderstander,
    private val identifier: ScreenIdentifier,
    private val repository: PackRepository,
)
fun scan(targetPackage: String, budget: ScanBudget, listener: ScanListener): KnowledgePack
```

It must:

- Run `Explorer.scan()` to get a `ScanOutcome`.
- For each screen, call `understander.describe(observation, screenId, context)` and fold the `ScreenProfile` into a `Screen` — `name`, `purpose`, `kind`, and per-element `semantic` and `input` keyed by `PrunedNode.id`.
- Assign element ids through `identifier.elementId(...)`, never by position.
- Build `ScreenGraph` from `ScanOutcome.edges`, and `Element.leadsTo` from the same edges.
- Fill `ScanMetadata` — id, timestamps, device, `coverage` (screens, elements, frontier remaining, `stopReason.wire`), and `understander` (backend, model, cache hits from `HybridUnderstander`).
- **Sort every array by id before returning.** Discovery order must never reach the pack.
- Hand the result through `PackCompactor.compact()` and then to `repository.save()`.

**Done when:** a unit test drives the orchestrator over the `FakeApp` with a stub understander and asserts a complete `KnowledgePack` comes out, and that **two runs produce identical `toCanonicalJson()` output**. That test is the headline acceptance criterion and it can pass on a laptop.

### S2. `ScanService` — a foreground service

New file, `:capture` or `:app`-facing but owned by you. A four-minute scan on a background thread will be killed by the system; it needs a foreground service with a notification, and the notification needs a **Stop** action wired to the same abort flag as the overlay kill switch.

- `startForeground` with the existing `specialUse` type.
- Progress in the notification: screens found, elapsed.
- Abort from notification, from `FloatingOverlayService`, and on service destruction.

### S3. `ScanController` — the API Neethu builds against (**publishes G6**)

A thin, lifecycle-safe surface: `start(pkg, budget)`, `pause()`, `stop()`, and an observable stream of `ScanListener` events. **Publish the signature early, even before the body works** — Neethu is blocked on the shape, not the behaviour. Tell her the moment it lands.

### S4. Hardware bring-up (**opens G4 and G5**)

Needs the Redmi Note 11 attached. In order:

1. `make apk-install`, grant accessibility and overlay, disable battery optimisation.
2. Scan **Settings** first — it is the app we already have hardware evidence on.
3. Scan a real third-party app. Watch for: leaving the package, tapping something on the deny-list, stalling on a dialog, screenshots being refused under rate limiting.
4. Export two scans of the same app to `fixtures/packs/real-<app>-scan1.animapack` and `-scan2.animapack`, commit them, **and tell Jiya, Neethu and Jacob in the channel.** Four people's work gets more realistic the moment those land.
5. `diff` the two. It must be empty. When it is not, the difference tells you exactly which module broke stability — that is why the crawler's output carries nothing an LLM authored.

### S5. Journey replay — the differentiator

Only after S4. Walk a path through the graph, replay it with the existing matcher, and set `Journey.replayable` from the result rather than asserting it. A pack that can prove itself is the answer to "your knowledge breaks when the app updates"; without this we ship a prettier JSON file.

### Also yours, when the above is done

The drawer-opening edge swipe, and wiring `ScanOutcome` coverage into `scan.coverage`.

---

## Jacob — `feat/knowledge-store` — make the spine real

**Unblocked now. Both of your first two tasks are correctness bugs in merged code, so they come before any new feature.**

### J1. De-duplicate the structural fingerprint — **first**

`StableIdEngine.signature()` sorts resource-ids but does not `.distinct()` them. A list of five rows and the same list with six rows hash differently, so the same screen gets two ids across scans and the byte-identical test fails on the first real app. Also consider whether repeated class names should collapse for the same reason.

**Done when:** a test builds two observations of one screen differing only in how many identical list rows they contain, and asserts the same `screenId`. That test is worth more than the rest of your module.

### J2. Implement persistence — **second, and it unblocks two people (G3)**

`save()` is an empty body; `load()` and `latest()` return `null`. Until this works, no pack can be reopened, no two scans can be diffed, and the demo's saved-pack fallback does not exist.

- Schema: app → scans → screens → elements → journeys, as briefed.
- Screenshots to files, not blobs in the database.
- **Done when:** a test saves a pack, loads it back, and asserts `toCanonicalJson(saved) == toCanonicalJson(loaded)`. A round trip that loses a field is worse than no persistence, because it fails silently in front of a judge.

### J3. Size budget against a real pack

`PackSizeTest` currently asserts against a synthetic pack, which mostly measures your test data. Re-point it at a real one the moment **G4** opens, and keep the build failing when it is exceeded.

### J4. Diffing, proved on real data

`diff()` is implemented. When **G5** opens, prove it: scan1 vs scan2 of the same version must diff empty, and a diff against an older app version must name the changed screens. This is the most convincing thing we can put on screen after the rebuild.

### J5. Export/import round trip

`PackArchive` exists. Prove `.animapack` survives a full round trip including screenshots, and that `PackLegacyReader` opens a `pack_version` older than current.

---

## Daniel — `feat/understanding` — generality and the offline story

**Unblocked now.**

### D1. On-device backend

The one checklist item you left open, and it is the privacy claim in the pitch. Gemma via MediaPipe/LiteRT. If the APK size cost is unacceptable, say so and we make the download optional — but the decision has to be made deliberately and written down, not left implicit.

**This has to work with wifi genuinely off**, because that is how it gets rehearsed at T-1.

### D2. Journeys over real paths

`describeJourney` exists. When **G4** opens, run it over real graph paths from a real scan and check the names are ones a human would recognise. A journey called "Screen 4 to Screen 9" is worse than no journey.

### D3. Prove the cache on a real rescan

Your `UnderstandCache` is what actually makes the text stable — temperature 0 never was. When **G5** opens, assert that the second scan makes **zero** model calls for screens it has already described, and that the text is byte-identical.

### D4. The hard case in the brief

A screen with **no labels and no roles** must still get a correct purpose and correctly typed form fields. Find or build such a screen and prove it. This is called out explicitly in the challenge, so expect to be asked.

### D5. Fallback rehearsal

Cloud off, model absent, malformed JSON, timeout — each must degrade to the next backend without ending the scan. Exercise it with the network actually off, not simulated.

---

## Jiya — `feat/design-extract` — **nothing shipped yet; start with Y0**

Everything below unit-tests from a static screenshot and a static node list, with no device and no crawler.

### Y0. Make your own input first — ten minutes, and it unblocks the rest

The shared fixture is a placeholder with no screenshots in it (see G1 above), and your module's entire input is pixels plus bounds. So make your own rather than wait:

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png    # any app, any phone
adb shell uiautomator dump && adb pull /sdcard/window_dump.xml    # the matching tree
```

Then in a test: `UIFormer.prune(xml, 1080 to 2400)` gives you the `List<PrunedNode>`, and the PNG bytes plus those nodes build a `ScreenObservation` — the exact type `DesignExtractor.extract()` takes. Commit three or four of these under `android/design/src/test/resources/` as your own fixtures.

Do this even after a real pack exists. A fixture you control, from an app whose brand you can see with your own eyes, is what lets you assert "primary is this green" rather than "primary is whatever it was last time".

### Y1. Colors

Quantize the screenshot for the real palette; cross-check against theme attributes where readable. Classify primary / on-primary / surface / background / error — a histogram dump is not a design system.

### Y2. Typography

Family, size, weight from the node tree where exposed; VLM fallback where not. Group into display / title / body / caption.

### Y3. Spacing and shape

Infer the base grid unit (4 or 8 dp) and corner radii from element bounds.

### Y4. Components

Detect recurring patterns — primary button, card, input, list row — into a catalog with a `seen_on` count.

### Y5. **The rebuild test — your headline**

Recreate 2–3 screens from the pack alone and show them beside the originals. It is an explicit judging criterion, it validates your own output, and it is the most visually convincing thing we have. Coordinate only the entry point with Neethu; the rendering is yours.

**Start it earlier than feels comfortable.** It is the item most likely to be cut for time, and it is the one we least want to cut.

### Y6. Determinism

The same screenshot must produce identical tokens on repeat runs. Quantization with any randomness in it — k-means with a random seed, for instance — breaks the byte-identical requirement from inside your module. Seed it or use a deterministic algorithm.

### Y7. Light/dark

Wait for **G4** for real dual-mode data. Note that `setUiMode()` needs `WRITE_SECURE_SETTINGS`, granted over adb on the demo device only — so design for the single-mode case being normal and the diff being a bonus.

---

## Neethu — `feat/app-ui` — **unblocked now, G1 is open**

You own the only thing judges touch. Build every screen against the golden fixture; the crawler is not a dependency for any of it.

### N1. Retire the 3-act demo

`MainActivity` is still the old scoreboard, and `TaskerReceiver` belongs to the retired task-execution product. Replace, don't extend.

### N2. Onboarding and permission gate

Explain plainly *why* an accessibility service is needed — this is the screen that decides whether a judge trusts the app. Deep-link to the settings screens, show live status, re-check on resume.

### N3. App picker

Installed apps, icon, label, search, recents.

### N4. Scan control — **needs G6 for the real API**

Start / pause / stop, live progress (screens found, current action, elapsed), always-visible abort. Build against a fake `ScanController` that emits scripted `ScanListener` events, and swap in Samuel's the moment G6 opens. **Do not wait for it to start this screen.**

The user must never feel the phone has been taken away from them. A four-minute scan with no feedback reads as a crash.

### N5. The viewer — the centrepiece

App map (zoomable, pannable graph), screen list with thumbnails, **screen profile beside its screenshot**, journeys browser, design-system page rendering Jiya's section.

### N6. Export and share the `.animapack`

`PackArchive.exportPack` exists; wire it to a share sheet.

### N7. Production polish

Adaptive icon, store assets, privacy policy screen, empty / loading / error states on every screen, dark mode, and **accessibility of our own UI** — content descriptions, 48dp targets, font scaling at 200%. An app built on the accessibility API that is itself inaccessible would be indefensible, and it is the first thing an accessibility-literate judge will check.

---

# P2 — T-3: feature freeze and integration

**Nobody writes a new feature after this point.** Integration and bug fixes only.

| Order | Who | What |
| :--- | :--- | :--- |
| 1 | Everyone | Final push to your branch. Your module's own tests green. |
| 2 | Everyone | `git fetch origin && git rebase origin/dev`, resolve your own conflicts, push. |
| 3 | Samuel | Merge all five branches to `dev` in dependency order: `:core`/`:store` → `:understand` → `:design` → `:explore` → `:app`. |
| 4 | Samuel | `./gradlew test` and `./gradlew :app:assembleDebug` green on the merged result, not on any branch. |
| 5 | Samuel | **End-to-end scan on the primary device**, from the merged build. |
| 6 | Everyone | Read the integration report. If your module broke something, it is yours to fix, today. |

**Merge protocol.** Every PR keeps CI green. A `:core` change needs Samuel **and** Jacob. Conflicts in `CHECKLIST.md` and `CURRENT_PROGRESS.md` are expected and are resolved by keeping both sides — never by deleting someone else's entry.

**Exit gate:** the merged build scans a real app end to end and produces a pack that opens in the viewer. If that is not true at the end of T-3, we are in trouble and we cut from the list at the bottom of this file rather than push the freeze.

---

# P3 — T-2: regression pass

**Every owner runs their own checklist against the merged `dev` build, not against their branch.** This is the single most commonly skipped step and the one that catches integration bugs while there is still time.

| Who | Regression |
| :--- | :--- |
| Samuel | Ten consecutive scans. Zero destructive controls tapped. Kill switch during a live crawl. Two scans of one app → empty diff. |
| Jacob | Save → load → canonical JSON round trip on a real pack. Size budget on a real pack. Diff across two app versions. `.animapack` import on a second device. |
| Daniel | Wifi off, end to end. Malformed model output. Rescan makes zero model calls. A no-label screen still gets typed fields. |
| Jiya | Tokens match the target app by eye. Same screenshot → identical tokens. Two rebuilt screens recognisable beside the originals. |
| Neethu | Fresh install on a wiped device. Every empty / loading / error state. Dark mode on every screen. TalkBack sweep. **Release build installed and exercised** — R8 strips what debug keeps, and a release-only crash is the classic last-day disaster. |

Also at T-2: **Neethu sets up the backup device identically** and verifies it end to end. Not a nice-to-have — a dead primary phone with no rehearsed backup ends the demo.

---

# P4 — T-1: rehearsal

The highest-value hour in the whole schedule. Teams that skip it find their bugs in front of judges.

1. **Two full rehearsals, timed.** One with network, one with **wifi actually off** — not simulated, not airplane-mode-but-wifi-still-on. Off.
2. Both devices set up identically: developer options, accessibility granted, overlay granted, battery optimisation disabled, screen timeout raised, Do Not Disturb on, target apps installed and logged in.
3. **Jacob loads saved packs for 2–3 apps onto both devices.** This is the fallback if a live scan stalls, and it is the product's own import path, not a demo mode.
4. **Neethu tags `main` and builds the release APK**, then installs *that* build and exercises it.
5. Assign speaking roles. Five people; not everyone should talk. One narrator, one driver, three on standby for questions in their own area.
6. Walk the presentation flow end to end, in order, against the clock:

   > problem in one sentence → judge picks an app → scan it, narrating what the agent is deciding → open the pack → app map → one screen profile beside its screenshot → the design system → **the rebuild** → **journey replay** → **scan twice, diff is empty**

   The last three are what separate us from a prettier JSON file. If the rehearsal runs long, cut from the middle, never from the end.

7. Write down the answers to the questions we know are coming: Play Store policy for an accessibility-API app, per-app ToS for scanning third-party apps, and what the pack does *not* yet cover. Having the answer ready reads as competence; being caught reads as spin.

---

# P5 — T: demo day

- **No code changes.** If it is not in by T-1, it is not in.
- Both phones charged, on the rehearsed build, with saved packs present.
- Run the flow from P4. Let a judge pick the app if they will — the safety envelope exists exactly so we can say yes to that.
- If something breaks, open the log export and show why. That has more credibility than pretending it did not happen.

---

## What gets cut if we are behind

Decided now, in the cold, rather than at 2am at T-1. In order of what goes first:

1. Light/dark token diffing (needs `WRITE_SECURE_SETTINGS`; degrade to single-mode, already designed for).
2. The drawer-opening edge swipe.
3. On-device LLM as a *download* rather than embedded — keep the fallback chain, drop the bundled weights.
4. Journeys browser in the viewer (keep journeys in the pack and in the export).
5. Play Store listing assets — the APK matters, the screenshots for a listing do not.

**Never cut:** the safety envelope, the stability test, the rebuild test, journey replay, or the rehearsal. Those are the judging criteria and the thing that stops us doing harm.
