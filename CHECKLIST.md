# Checklist

> A box gets ticked **only** when its verification command passes on this machine. No box is ticked on the strength of "the code looks right" — that is exactly how Phases 5–6 came to be marked complete for a module that had never been compiled.
>
> Context: [PLAN.md](PLAN.md) · **Runbook: [EXECUTION_PLAN.md](EXECUTION_PLAN.md)** · Running log: [CURRENT_PROGRESS.md](CURRENT_PROGRESS.md)

**Phase 9 (shipped)** is below as history. The **pivot workstreams** — what everyone is actually building now — are at the end.

Contract: [KNOWLEDGE_PACK.md](KNOWLEDGE_PACK.md) · Briefs: [ASSIGNMENTS.md](ASSIGNMENTS.md) · Shipping: [RELEASE_READINESS.md](RELEASE_READINESS.md)

Run `source android/env.sh` before any Gradle or ADB command below.

# Phase 9 — Buildable APK & On-Device Engine ✅

## Step 0 — Python correctness fixes

- [x] Essential-state verification wired into the warm replay loop — `make test-all` (22/22)
- [x] Essential-state verification wired into the cold path — `make test-all`
- [x] `BiometricGuard` markers reachable (case bug) — `make test-all`
- [x] PTG records real post-action state instead of self-loops — `make test-all`
- [x] Documented locator weights reconciled with `Matcher.WEIGHTS` — `grep res_id dev_plan.md`
- [x] No Python regressions — `make test-all` → 25 tests, OK

## Step 1 — Toolchain

- [x] JDK 17 installed — `/opt/homebrew/opt/openjdk@17/bin/java -version` → 17.x
- [x] Android SDK 34 + build-tools 34.0.0 + platform-tools — `ls $ANDROID_HOME` → build-tools, platforms, platform-tools, licenses
- [x] SDK licences accepted — `sdkmanager --licenses` exits 0
- [x] `adb` on PATH — `adb version`
- [x] Gradle wrapper committed and pinned to 8.9 — `cat android/gradle/wrapper/gradle-wrapper.properties`
- [x] `android/env.sh` reproduces the environment in one `source`
- [x] `android/local.properties` written and gitignored

## Step 2 — Module builds

- [x] Standard Gradle layout (`android/app/src/main/…`)
- [x] `res/values/strings.xml` with `accessibility_service_description`
- [x] `res/values/colors.xml` matching the overlay's palette
- [x] `res/values/themes.xml` — `Theme.Anima`
- [x] Vector adaptive launcher icons (`ic_launcher`, `ic_launcher_round`), no binaries
- [x] `proguard-rules.pro` exists and keeps framework entry points
- [x] Manifest: `package=` removed, `POST_NOTIFICATIONS` added, FGS `<property>` added, `<activity>` declared
- [x] Resources + manifest link — `./gradlew :app:processDebugResources` BUILD SUCCESSFUL
- [x] **`./gradlew :app:assembleDebug` produces an APK** — app-debug.apk, 5.7 MB ✅

## Step 3 — Kotlin engine

- [x] `Models.kt`, `UIFormer.kt`, `Matcher.kt`, `TextRatio.kt`, `ParameterExtractor.kt` — no `android.*` imports, JVM-testable
- [x] `SkillStore.kt` — schema identical to Python, snake_case JSON keys
- [x] `ReplayEngine.kt` — biometric guard first, popup intercept, drift heal, IME dismissal after text
- [x] `AccessibilityDevice` + hermetic `DemoDevice`
- [x] `TaskerReceiver` reports the engine's real result instead of `val success = true`
- [x] Zero network calls anywhere in the app, and no INTERNET permission — `grep -rE "HttpURLConnection|okhttp|java.net.URL" android/app/src/main` returns nothing

## Step 4 — Judge-facing app

- [x] Permission gate: live status + deep links for Accessibility and Draw-Over-Apps, re-checked in `onResume`
- [x] `POST_NOTIFICATIONS` runtime request on API 33+
- [x] Goal input + Run, disabled with an explanation until the service is on
- [x] 3-act demo button, streaming each act into the UI as it lands
- [x] Scoreboard: mode, latency, LLM calls, cost vs. the ~180s / ~$0.90 stateless baseline
- [x] Engine calls run off the main thread

## Step 5 — Tests & CI

- [x] `./gradlew :app:testDebugUnitTest` green — **76 tests, 0 failures, 0 errors**
- [x] `TextRatio` matches `difflib.SequenceMatcher` on values confirmed against real Python output
- [x] Matcher: exact-id 1.0, fuzzy desc, spatial falloff, sub-threshold null, first-node-wins ties
- [x] Cross-runtime: a `skills.json` written by Python imports into the Kotlin store and scores identically
- [x] CI `android` job builds the APK and uploads it as an artifact — **verified green on a clean runner** (2m27s, artifact 4.7 MB); first run failed on the obsolete `tools` SDK package, fixed in `345bb64`
- [x] `make apk` / `make android-test` shortcuts

## Step 6 — On-device (Redmi Note 11, MIUI, Android 13 / API 33)

- [x] `adb devices` lists the phone — `cdf9a5bc`
- [x] APK installs — needed MIUI's **Install via USB** + **USB debugging (Security settings)**; without them `adb install` fails `INSTALL_FAILED_USER_RESTRICTED`
- [x] App opens with no crash, permission gate renders, notifications prompt fires
- [x] Accessibility service connects — `AnimaAgent: Anima Accessibility Service connected successfully`
- [x] **3-act demo runs on-screen**: Act 1 cold 43ms/1 call/$0.0024 · **Act 2 warm 2ms / 0 LLM calls / $0.00** · Act 3 self-healed 54ms · total 99ms vs ~180s baseline
- [x] **Drives the real Settings UI** — `toggle wifi` tapped (540,498) and `wifi_on` went 1 → 0
- [x] **Warm replay on hardware reports `llmCalls=0`** — 264ms and 687ms across two consecutive runs, each physically flipping Wi-Fi
- [x] Self-healing verified on a real app — the stale skill drifted, re-grounded and repaired itself in the on-device SQLite
- [x] **Goal runner works from inside the app** — "turn on bluetooth" bt 0 → 1, "turn off bluetooth" bt 1 → 0, routed via IntentRouter
- [x] **Agent never drives its own UI** — Anima's own windows excluded from capture
- [x] **No cross-screen misfires** — with the Bluetooth page open, `toggle wifi` routes to Wi-Fi, flips wifi and leaves Bluetooth alone
- [ ] Edge-glow overlay + "Take Control" kill-switch exercised by hand ← only remaining device item

## Step 7 — Ship

- [x] `dev-sam` pushed to `origin`
- [x] CI green on all four jobs — `gh run view 35262697144`
- [x] APK downloadable without a toolchain — `gh run download --branch dev-sam --name anima-debug-apk`
- [ ] PR opened into `main` ← team's call on timing

## Step 8 — Docs

- [x] `PLAN.md`, `CHECKLIST.md`, `CURRENT_PROGRESS.md` created
- [x] `dev_plan.md`: Phase 9 row + honest correction of the Phase 5–6 status
- [x] `android/README.md`: real build/run/permission instructions
- [x] `README.md`: APK quickstart
- [x] `CONTRIBUTING.md`: pointer to these three docs


---

---

> **Phase 10 (multi-step planning) is superseded.** The project pivoted to autonomous app exploration — see [PLAN.md](PLAN.md). The multi-step cold loop survives inside the exploration engine below; the rest of that plan is retired.

---

# Blockers — found by inspecting merged code, not by reading commit messages

These three are load-bearing. Two of them are correctness bugs in code that is already on `dev` and already passing its own tests. Full context and the order to fix them in: [EXECUTION_PLAN.md](EXECUTION_PLAN.md).

- [ ] **Nothing is wired end to end** — `Explorer` makes a `ScanOutcome`, `HybridUnderstander` makes a `ScreenProfile`, `KnowledgeStore` takes a `KnowledgePack`, and nothing turns the first into the third. No code path has ever produced a real pack. **Samuel, critical path.**
- [ ] **`KnowledgeStore` does not persist** — `save()` is an empty body, `load()` and `latest()` return `null`. No pack can be reopened, no two scans diffed, and the demo's saved-pack fallback does not exist. **Jacob, first.**
- [ ] **`StableIdEngine.signature()` does not de-duplicate resource-ids** — a list screen with five rows and the same screen with six hash differently, so one screen gets two ids across scans. Passes a hand-built unit test, fails the first real rescan. **Jacob, before anything else in `:store`.**


# Day 0 — joint, blocking ✅ (except fixtures)

- [x] Gradle split into `:core :capture :explore :understand :design :store :app` — `./gradlew projects` lists all seven
- [x] `./gradlew build` green after the split
- [x] **Pack schema v1 frozen** and committed — `core/src/main/kotlin/io/agents/anima/core/Pack.kt`, matching [KNOWLEDGE_PACK.md](KNOWLEDGE_PACK.md)
- [x] Stable-ID spec agreed (SHA-256 structural hash, dynamic content excluded) — [KNOWLEDGE_PACK.md](KNOWLEDGE_PACK.md); implementation is Jacob's
- [ ] Golden fixture packs in `fixtures/packs/` — `golden.animapack` exists but is a **placeholder**: its entry is `dummy_pack.json` rather than `pack.json` so it does not load through `PackArchive.importPack()`, `screens[]` is empty, and it contains no screenshots. **Jacob: replace or delete.**
- [x] Module interfaces agreed (signatures only) — `core/src/main/kotlin/io/agents/anima/core/Contracts.kt`
- [x] minSdk decision recorded (26 -> 30 for `takeScreenshot()`) — `android/build.gradle.kts`, one place, not seven
- [x] `dev` branch created and five feature branches pushed off it — `git branch -a`
- [x] CI runs per-module tests — `.github/workflows/ci.yml` runs `./gradlew test`, not `:app:testDebugUnitTest`, which would now say nothing about the engine
- [x] `:core` kept free of `android.*` — enforced by `NoAndroidImportsTest`, not by convention

> `main` protection is a repo setting, not a commit. **Samuel: set it in GitHub before anyone opens a PR.**

> Branches were created off `dev` rather than left to each person, because branching off `main` by mistake means missing the module split entirely and hitting a merge conflict across every build file.

# Samuel — Exploration engine · `feat/explorer`

## Capture gaps ✅

- [x] Screenshot capture via `AccessibilityService.takeScreenshot()` — `AnimaAccessibilityService.takeScreenshotSync()`; WebP downscale to the 60 KB / 720 px budget in `capture/Screenshotter.kt`
- [x] `isScrollable` stored on `AccessibilityNode` and `PrunedNode` (read since the first version, never kept)
- [x] Scrollable containers survive `UIFormer` pruning — without this a list screen looks finished at the fold
- [x] `scroll()` in the device contract — `ExplorationDevice.scroll()`, swiping the middle 60% to miss the back gesture and the notification shade
- [x] Multi-window enumeration via `getWindows()` — `captureAllWindowNodes()`, ordered by layer then window id so two scans agree
- [x] Screenshot and node capture in one `observe()` call, so design extraction and the tree describe the same screen

## Exploration loop

- [x] Frontier queue over unexplored elements — `explore/Frontier.kt`
- [x] Action vocabulary: tap / scroll / back / fill / relaunch — `Explorer.perform()`
- [ ] Drawer-opening gesture (edge swipe) — not yet; drawers are reached today only when a hamburger control is exposed as a node
- [x] Mode toggle — `setUiMode()`, honest about needing `WRITE_SECURE_SETTINGS`; returns false rather than faking a light/dark diff
- [x] Novelty scoring prefers navigation over toggles and text — `explore/ExplorationPolicy.kt`
- [x] Stop conditions: frontier exhausted, step budget, screen budget, wall-clock, novelty decay — each with its own `StopReason`
- [x] Coverage stats emitted — `ScanOutcome` carries screens, elements, steps, frontier remaining and stop reason
- [ ] Coverage written into `scan.coverage` of the pack — needs Jacob's assembler
- [x] **Ordered, reproducible traversal** — `ExplorerTest.twoScansOfTheSameAppWalkItIdentically`

## Safety envelope

- [x] Destructive-control deny-list enforced — `explore/SafetyEnvelope.kt`; candidates are never queued, so no later bug in the loop can reach one
- [x] Forward-motion labels deliberately allowed (Continue, Next, Submit, Sign in, Verify) — the crawler has to get through login and OTP gates
- [x] Never leaves the target package; detects and returns — `SafetyEnvelope.locate()` + `Explorer.recover()`
- [x] Recovery ladder: Back -> Home -> relaunch, ordered by what each costs the scan
- [x] Hard budgets on steps, depth, screens and wall-clock — `explore/ScanBudget.kt`
- [x] Biometric guard records the screen as a boundary and does not drive it — `ExplorerTest.anAuthenticationScreenIsRecordedButNotDriven`
- [ ] Kill switch verified live during a crawl on hardware — wired (`isAborted`) and unit-tested; **needs the phone**

## Done when — all four need hardware

- [ ] Scans a real app with no human input
- [ ] >=30 screens discovered on the fintech target
- [ ] Zero destructive controls tapped across 10 consecutive scans
- [ ] Two consecutive scans visit screens in the same order **on a device** (proved against a fake app; the device adds timing, animation and OEM behaviour)

> No box above is ticked on hardware evidence. `adb devices` was empty for this
> session, so everything marked done is verified by JVM tests and a green build,
> and everything that genuinely needs the Redmi is still open. That distinction
> is the whole point of this file.

# Daniel — Understanding · `feat/understanding`

- [x] `ScreenUnderstander` interface defined in `:core` and agreed
- [x] `HeuristicUnderstander` — no model, no network, always available
- [x] `CloudVlm` backend (Gemini)
- [ ] `OnDeviceLlm` backend (Gemma via MediaPipe/LiteRT) ← deferred by decision; `LocalLlm` (local endpoint client) shipped instead
- [x] Fallback composition — model first, heuristic on any failure
- [x] Strict JSON validation + repair; malformed output never breaks a scan — test with a deliberately broken response
- [x] **Results cached by structural hash**; rescan reuses prior text verbatim
- [x] Temperature 0 across backends
- [x] Screen `name`, `purpose`, `kind` produced
- [x] Per-element `semantic` produced
- [x] **Form-field typing with no roles and no labels** (email / phone / OTP / amount / date / password)
- [x] Journey naming and summarization from graph paths
- [x] `tone_of_voice` classification
- [x] Prompt built from the pruned Agent-DOM, not the raw tree — token budget asserted

## Done when

- [x] An unlabelled, role-less screen still yields a correct purpose and correct field types
- [x] Two runs over the same screens produce identical text
- [x] Scan completes with the model backend forced to fail

# Jacob — Knowledge store · `feat/knowledge-store`

- [ ] **Golden fixture packs shipped** (Day 0 — unblocks two people)
- [ ] Stable screen ID: SHA-256 over canonical structural fingerprint
- [ ] Stable element ID; survives changed row text
- [ ] **`compute_screen_signature` from `anima.py:869` NOT ported** (salted `hash()`, prune-order indices, 10-node window, 4-digit truncation)
- [ ] Canonical JSON writer: fixed key order, fixed float precision, `Locale.US`
- [ ] SQLite schema: app -> scans -> screens -> elements -> journeys
- [ ] Scan diffing: added / removed / changed screens
- [ ] Compaction: string interning, component dedupe, WebP screenshots, empty fields omitted
- [ ] **Size budget enforced by a failing test** — `pack.json` <= 512 KB at 40 screens
- [ ] `.animapack` export/import round-trips
- [ ] Previous-version reader retained on any `pack_version` bump

## Done when

- [ ] **Two consecutive scans produce byte-identical `screens[]`, `journeys[]`, `design_system`** <- the headline test
- [ ] Same app on two devices produces the same screen IDs
- [ ] Diff correctly reports a screen added between two app versions
- [ ] 40-screen pack fits the budget

# Jiya — Design & brand extraction · `feat/design-extract`

Verified with `cd android && ./gradlew :design:testDebugUnitTest` (35 tests, green).
Fixtures are three real captures — Settings, Clock, Contacts — in
`android/design/src/test/resources/fixtures/`.

- [ ] Color palette quantized from screenshots, cross-checked against theme attributes
      — quantizing and classification are done; the **theme-attribute cross-check is not
      possible from a `uiautomator` dump**, which carries no theme data. Needs either the
      accessibility service to read them or a VLM pass.
- [x] Colors classified (primary / on-primary / surface / background / error), not a raw histogram
      — read through the node tree: the window colour comes from the frame's edge ring, so a
      screen of cards does not report its card colour as the background.
- [ ] Typography roles extracted (family, size, weight), VLM fallback where absent
      — **size** is measured and calibrated (Settings reads 16sp/14sp, Material 3's own
      values). **Family and weight are reported null**: neither is in an accessibility dump,
      and inventing them would be a fabrication in a pack whose value is being trustworthy.
      The VLM fallback is the open half of this box.
- [x] Base spacing unit and radius scale inferred from bounds
      — spacing from bounds; radius from pixel curvature, since radius is a drawable
      property and not in the tree at all.
- [x] Component catalog with `seen_on` counts (button, card, input, list row)
- [ ] Light/dark token diff from both passes — blocked on **G4**. Every fixture was captured
      in light mode, and the extractor emits only the modes it actually saw rather than
      inventing a dark palette from light screens.
- [ ] `tone_of_voice` inputs collected from app copy — deliberately left to
      `ScreenUnderstander.toneOfVoice`; two answers to one question in one pack is worse
      than one.
- [ ] Emits a schema-valid `design_system` section — the `DesignSystem` object is built and
      populated; serialization and schema validation live in `:store`.
- [x] **Deterministic**: the same screenshot produces identical tokens on repeat runs
      — no RNG anywhere, integer arithmetic, explicit tie-breaks. Also asserted against
      reversed crawl order.
- [x] Unit tests run from a static image + node list, no device
      — including PNG decoding: `BitmapFactory` returns null under
      `unitTests.isReturnDefaultValues`, so the decoder is pure Kotlin over `java.util.zip`.

## Rebuild test (judging criterion)

- [x] Renders a screen from the pack alone, no screenshot used
      — enforced by the type: `RebuildScreen` has no screenshot field, so the renderer
      cannot reach the original pixels even by accident.
- [x] Side-by-side comparison view with the original
      — `build/reports/rebuild/index.html`, self-contained, opens in a browser.
- [x] **2-3 screens rebuilt and recognizable** — three. Settings is the strongest: card
      grouping, row rhythm and the title/summary hierarchy all land.

# Neethu — Product app & viewer · `feat/app-ui`

- [ ] Onboarding explains plainly why an accessibility service is required
- [ ] Permission gate with deep links and live status, re-checked on resume
- [ ] App picker: installed apps, icons, search, recents
- [ ] Scan control: start / pause / stop, always-visible abort
- [ ] Live progress: screens found, coverage, elapsed
- [ ] **Viewer — app map**: zoomable, pannable screen graph
- [ ] **Viewer — screen profile beside its screenshot** (name, purpose, elements, form fields)
- [ ] Viewer — journeys browser
- [ ] Viewer — design-system page rendering Jiya's section
- [ ] Export / share `.animapack`
- [ ] Entry point to Jiya's rebuild comparison

## Production polish

- [ ] Adaptive icon and store listing assets
- [ ] Privacy policy screen
- [ ] Empty / loading / error states on every screen
- [ ] Dark mode
- [ ] **Our own UI is accessible** — content descriptions, 48dp targets, font scaling
- [ ] `./gradlew :app:assembleRelease` produces an installable unsigned APK

## Done when

- [ ] Someone who has never seen the project installs, grants, scans and browses without being told how

# Integration — Samuel

- [ ] All modules merged to `dev`, CI green
- [ ] End-to-end on a real device: pick app -> scan -> pack -> viewer
- [ ] Journey replay verifies a recorded journey on a real device
- [ ] Login/OTP gate traversed autonomously with test credentials
- [ ] Retired: `DemoDevice`, `DemoScript`, 3-act demo, `run_benchmark`, `IntentRouter`, `TaskerReceiver`, skill-replay CLI flags
- [ ] README reflects the product, not the old task-agent framing

# Display & release readiness

Full detail and the T-3/T-2/T-1 schedule: [RELEASE_READINESS.md](RELEASE_READINESS.md).

## Performance budgets (measured on the demo device)

- [ ] Scan of a ~30-screen app completes in <= 4 minutes
- [ ] First screen appears in the viewer within 15 s (results stream, not batch)
- [ ] App cold start <= 2 s
- [ ] Viewer map pan and list scroll hold 60 fps
- [ ] No OOM with 40 screens and screenshots held
- [ ] APK <= 30 MB excluding any downloaded model

## Devices

- [ ] Primary demo device set up: permissions, battery optimisation off, timeout raised, DND on
- [ ] **Backup device set up identically and rehearsed on**
- [ ] Third device (different OEM) proves output generality
- [ ] Target apps installed and logged in on every device

## Failure paths — rehearse, don't assume

- [ ] Scan runs with **wifi actually off** (on-device fallback), not simulated
- [ ] Saved packs for 2-3 apps present on both devices and openable in the viewer
- [ ] Every stall path shows a visible state; nothing hangs silently
- [ ] Safety envelope holds on an app the team has never scanned before
- [ ] Crash handler writes a readable log; **in-app log export works**

## Schedule

- [ ] **T-3** feature freeze; full merge to `dev`; CI green; end-to-end scan on primary device
- [ ] **T-2** regression pass by every module owner against the merged build, not their branch
- [ ] **T-1** full rehearsal twice — once online, once offline, timed
- [ ] **T-1** `main` tagged, release APK built and installed from the release build
- [ ] **T** no code changes

## Release build

- [ ] `versionCode` / `versionName` set deliberately
- [ ] `./gradlew :app:assembleRelease` produces an installable APK
- [ ] **Release build installed and exercised** — R8 strips what debug keeps
- [ ] R8 rules verified for every reflective entry point (services, receivers, JSON models)
- [ ] Icon, app name, launch screen final
- [ ] Store assets: screenshots, descriptions, feature graphic
- [ ] Privacy policy screen
- [ ] Every declared permission is used and explained in-app
- [ ] No screen-content logging in release
- [ ] Accessibility sweep on our own UI (TalkBack, 48dp, 200% font)
- [ ] First run tested on a **freshly wiped install**

## Presentation

- [ ] Narrator and driver assigned; three on standby for domain questions
- [ ] Flow rehearsed end to end within the time limit
- [ ] Rebuild comparison and journey replay reached before time runs out
- [ ] `scan twice && diff` demonstrated as the stability proof
- [ ] Answers ready for Play Store policy, third-party ToS, and known gaps

---

# P2–P5 — freeze, regression, rehearsal, demo

Expanded, with owners and exit gates, in [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Tick here as each phase closes.

## P2 — T-3, feature freeze

- [ ] All five branches rebased on `dev` and pushed
- [ ] Samuel merged all five in dependency order
- [ ] `./gradlew test` green on the **merged** result
- [ ] `./gradlew :app:assembleDebug` green on the merged result
- [ ] **End-to-end scan on the primary device, from the merged build** — the exit gate
- [ ] Integration report read by everyone; breakages owned same day

## P3 — T-2, regression pass (against `dev`, not your branch)

- [ ] Samuel — 10 consecutive scans, zero destructive taps, kill switch live, two scans diff empty
- [ ] Jacob — save/load/canonical round trip, size budget on a real pack, diff across versions, `.animapack` imports on a second device
- [ ] Daniel — wifi genuinely off end to end, malformed output degrades, rescan makes zero model calls, no-label screen still typed
- [ ] Jiya — tokens match by eye, repeat runs identical, two rebuilt screens recognisable
- [ ] Neethu — fresh install on a wiped device, every empty/loading/error state, dark mode, TalkBack sweep
- [ ] Neethu — **release build installed and exercised** (R8 strips what debug keeps)
- [ ] Neethu — backup device set up identically and verified end to end

## P4 — T-1, rehearsal

- [ ] Rehearsal 1, with network, timed
- [ ] Rehearsal 2, **wifi actually off**, timed
- [ ] Both devices configured identically (developer options, permissions, battery optimisation off, timeout raised, DND on, target apps installed and logged in)
- [ ] Saved packs for 2–3 apps on both devices — Jacob
- [ ] `main` tagged, release APK built and installed from the release build — Neethu
- [ ] Speaking roles assigned
- [ ] Presentation flow walked end to end against the clock, reaching rebuild + replay + diff
- [ ] Answers written down: Play policy, per-app ToS, what the pack does not cover

## P5 — T, demo day

- [ ] No code changes
- [ ] Both phones charged, rehearsed build, saved packs present
- [ ] Log export ready to show if something breaks
