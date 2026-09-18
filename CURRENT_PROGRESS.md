# Current Progress

> Running log for Phase 9. Newest entry first. Update this at every commit.
> Context: [PLAN.md](PLAN.md) · Task status: [CHECKLIST.md](CHECKLIST.md)

## Where to pick up

**Phase 9 is shipped.** `dev-sam` is pushed, CI green, APK verified on a physical Redmi Note 11 driving real Settings screens.

**Phase 10 is planned and documented, not started** — see [PLAN.md](PLAN.md) and [dev_plan.md §14](dev_plan.md). Two workstreams:

1. **Multi-step cold planning.** The cold path compiles exactly one step today (`anima.py:1271`, `AnimaRuntime.kt:179`), so no flow can ever be learned. Replace it with a bounded plan→act→observe loop. Start here — it is useful with no model at all, and it defines the interface changes Workstream B depends on.
2. **A real planner behind the seam.** `AnimaEngine.kt:15` hardcodes the heuristic. Every planner rule from Phase 9 is fitted to one MIUI device; a model reading the pruned Agent-DOM is what makes this work on any make and model.

**Order of work:** loop → multi-screen demo fixture → destructive-action guard → `PlannerFactory` + flavors → on-device model → cross-OEM validation.

**Do not skip the cross-OEM bar.** Phase 10 is not complete when it works on the Redmi; it is complete when the same goal runs on three unrelated skins with no per-OEM code. Without that bar this phase just produces a second set of device-specific rules.

**Grab a build without a toolchain:** `gh run download --branch dev-sam --name anima-debug-apk`.

**Demo tip:** run "turn on bluetooth" then "toggle wifi" back to back — the sequence that exposed the wrong-screen bug, and the strongest live proof the agent knows which screen it is on.

## Environment (reproduce with `source android/env.sh`)

| Thing | Value |
| :--- | :--- |
| JDK | `/opt/homebrew/opt/openjdk@17` — **must be 17**, AGP 8.5 rejects newer. macOS ships 21/24; this is the #1 build failure here. |
| Android SDK | `/opt/homebrew/share/android-commandlinetools` (Homebrew cask default, *not* `~/Library/Android/sdk`) |
| SDK packages | `platform-tools`, `platforms;android-34`, `build-tools;34.0.0`, licences accepted |
| Gradle | 8.9 via the committed wrapper (`./gradlew`). Homebrew's system Gradle is 9.7 and **is not compatible with AGP 8.x** — it was used once to generate the wrapper and should not be used to build. |
| AGP / Kotlin | 8.5.2 / 1.9.24, compileSdk+targetSdk 34, minSdk 26, JVM target 17 |
| `adb` | `/opt/homebrew/bin/adb` |
| `local.properties` | written, gitignored — regenerate with `echo "sdk.dir=$ANDROID_HOME" > android/local.properties` |

One-time setup on a fresh machine is documented at the top of `android/env.sh`.

## Log

### 2026-09-18 — Phase 10 planned and documented (no code)

Wrote the next phase into the repo: `dev_plan.md` §14 (full spec, decisions, risks), `PLAN.md`, Phase 10 sections in `CHECKLIST.md`, and this entry. No implementation landed — deliberately.

Two audits drove it. The cold path compiles **exactly one step** in both runtimes, so multi-step flows are unreachable by construction and every multi-step skill in the repo is a test fixture. And the APK contains **no model at all**: `AnimaEngine.kt:15` hardcodes the heuristic planner, whose every rule was hand-fitted to one MIUI phone during Phase 9 — punctuation-stripping for "Wi-Fi", the actionable-ancestor redirect, the toggle-row rule that exists only because MIUI renders a switch as a `CheckBox`. That generalises to the phones we tested, not to any make and model.

**Three false claims corrected in the same pass** — the same failure mode as "Phases 5–6 ✅ COMPLETE" for a module that had never compiled:

- `README.md` said the Kotlin tests validate "on-device LiteRT execution". No such test exists; nothing LiteRT exists in the APK.
- `dev_plan.md` §4 said the APK "houses the LiteRT-LM model runtime". It does not.
- Phase 6 was marked ✅ COMPLETED for on-device LiteRT-LM inference. The real artifact is the **Python** `LocalLiteRTPlanner`, an HTTP client for a model server you run yourself — not an embedded runtime, and with **no Kotlin equivalent**. Now marked ⚠️ PARTIAL with the honest scope.

Phase 10 is exactly the work that would make those claims true, so they are now written as intent rather than achievement.

### 2026-09-18 — Two bugs the hermetic suite could never have caught

**"Run a goal" did nothing useful** (`7fbd6ec`). The agent acts on the foreground screen, and pressing RUN leaves *Anima* in the foreground: the taps landed on Anima's own goal input field, whose text is — of course — the best keyword match for the goal the user just typed. Fixed by excluding Anima's own windows from capture, and by finally implementing the Layer 1 deterministic fast-path `dev_plan` §2 has described since the first draft: a goal naming a system setting launches that settings screen first. `turn on bluetooth` / `turn off bluetooth` now work from inside the app.

**A skill fired on the wrong app's screen** (`ee26fc1`) — worse, because it *succeeded*. The `toggle wifi` skill replayed while the Bluetooth page was open and turned Bluetooth off, reporting `success=true llmCalls=0`. MIUI's toggle row has no id, text or content-desc, so the locator held only class + position, and active-weight renormalization rescaled that to a perfect 1.0 against any same-shaped row anywhere. Fixed with label inheritance, a contradiction veto, and a specificity tie-break; see `dev_plan` §13.7.

Verified on the device, the exact sequence that used to misfire: with the Bluetooth page open and bt=1, `toggle wifi` routed to Wi-Fi settings, flipped wifi 1 → 0, and **left Bluetooth alone**. On the wrong screen the planner now declines rather than tapping something plausible.

Python 25/25, Kotlin 76/76.

### 2026-09-18 — `dev-sam` pushed, CI green end to end

Branch is on `origin`. All four CI jobs pass: Python on 3.10/3.11/3.12, and the Android job building the APK on a clean Ubuntu runner (2m27s) with the artifact uploaded at 4.7 MB.

The Android job failed on its very first run, which is exactly why it was worth adding: `android-actions/setup-android@v3` defaults to installing `tools platform-tools`, and the obsolete `tools` package no longer exists in the SDK repository, so sdkmanager exits 1 with "Failed to find package 'tools'". Fixed by naming the packages the build actually needs (`345bb64`), matching `android/env.sh`.

Two harmless annotations remain: Node 20 deprecation on several actions, and `setup-java@v4` deprecation. Neither breaks the build; worth bumping when someone touches the workflow next.

### 2026-09-18 — Step 6: it works on a real phone

Device: Redmi Note 11 (`2201117TI`, MIUI, Android 13 / API 33).

**3-act demo, on-screen:** Act 1 cold 43ms / 1 call / $0.0024 · **Act 2 warm 2ms / 0 LLM calls / $0.00** · Act 3 self-healing chaos 54ms. Total 99ms against a ~180s, ~$0.90 stateless baseline.

**Real Settings UI, over the intent API:** `toggle wifi` tapped (540,498) and `wifi_on` went 1 → 0; two further runs replayed at `llmCalls=0` in 264ms and 687ms, each physically flipping Wi-Fi. The first run also proved self-healing on a real app: the stale skill drifted, was re-grounded and repaired in the on-device SQLite.

Getting there turned up **four planner bugs no mock could have caught** (`19fbb5b`), because real Android is less tidy than the fixtures:
1. `"wifi" in "wi-fi"` is False — punctuation in labels broke every match, and the fixtures hid it because their resource-ids contain the bare word.
2. The label is an inert TextView inside the clickable row, so tapping the label did nothing.
3. Every network row's content-desc says "…,Wi-Fi signal full.", so a *mention* outranked the actual switch and the agent opened a share-password dialog instead.
4. Both the screen title and the toggle row are labelled "Wi-Fi" — and MIUI renders the switch as a `CheckBox` with no text, no desc and no clickable flag, which the Kotlin capture filter dropped outright.

Plus a documentation bug worth remembering (`0da93b7`): the RUN_TASK broadcast in the README **could never have worked**. Android 8+ never delivers an implicit broadcast to a manifest receiver, so it printed `Broadcast completed: result=0` and did nothing. `-n <pkg>/<receiver>` is mandatory.

### 2026-09-17 — Steps 2–5 complete: the APK builds and the engine is real

`./gradlew :app:assembleDebug` → **app-debug.apk, 5.7 MB**. `:app:testDebugUnitTest` → **65 tests, 0 failures**. Python still 22/22.

- **Resources + manifest** (`5bc0883`): the three AAPT2 blockers fixed, plus two bugs that only bite at runtime — the missing `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` (guaranteed `MissingForegroundServiceTypeException` on first overlay use) and the undeclared `POST_NOTIFICATIONS`.
- **Kotlin engine** (`a28a1e2`): `io.agents.anima.engine` — SQLite skill store, weighted matcher, a from-scratch `difflib.SequenceMatcher` port, replay loop with drift healing and the biometric guard before every autonomous tap. `TaskerReceiver` now broadcasts the engine's real numbers instead of `val success = true`.
- **Cross-runtime test** (`ca44399`): `skills_from_python.json` is a genuine `SkillDB.export_json()` output; the Kotlin test imports it and asserts the locator scores match anima.py to ten decimals. Divergence between the runtimes now fails a test.
- **MainActivity** (`942ef78`): permission gate with deep links and live status, goal runner, 3-act demo with a streamed scoreboard.

**No network code and no INTERNET permission anywhere in the app** — worth saying out loud in the pitch, because it means the APK *cannot* phone home, enforced by the manifest rather than by promise.

### 2026-09-17 — Step 1 + Step 2 scaffolding

- Toolchain installed from scratch (nothing Android existed on this machine).
- `android/` restructured from the flat `src/` + `sourceSets` override into the standard single-module layout: `android/app/src/main/{kotlin,res}`. The three existing Kotlin services moved unchanged via `git mv`.
- Root/`:app` Gradle files split properly, `viewBinding` enabled, JUnit added for JVM tests, `proguard-rules.pro` written (it was referenced by the release build but had never existed).
- Gradle 8.9 wrapper generated and committed — the repo has never had one, which is why nobody had built this module.
- `.gitignore` extended for Android/Gradle outputs.

### 2026-09-17 — Step 0: correctness fixes (`c16c8bd`)

Audited the runtime against its own documentation and found three real bugs:

1. **`EssentialStateVerifier.verify_progress()` was dead code** — defined, documented in `dev_plan.md` §2 as "Layer 4", and called from nowhere. Both execution paths ran open-loop and reported success without ever checking that an action changed anything on screen. Now wired into both paths and counted into `ExecutionResult.steps_verified`, advisory-only so a false negative can never abort a live demo.
2. **Two `BiometricGuard` markers were unreachable** — mixed-case literals compared against an already-lower-cased dump. Only the blanket `"biometric"` substring was doing any work. A test now asserts every marker stays reachable.
3. **Documented locator weights never matched the code** — `dev_plan.md` §3 claimed `res_id 0.40 / bounds 0.05`; `Matcher.WEIGHTS` is `0.35 / 0.10`. Corrected the doc to the code.

Also fixed in passing: the Page Transition Graph was recording the same node snapshot as both source and target of every edge, so the "transition graph" was drawing self-loops rather than real screen transitions.

Suite: **22/22** (was 20/20).

## Known issues / notes for whoever picks this up

- **Commit `d7036ea` on `main` has an imperfect split** — it is labelled `feat(demo)` but also carries the HITL/IPGuard and D3 dashboard changes, because `anima.py` was staged as a unit. It is already on `origin/main`, so it is **not** being rewritten; recording it here instead. Don't be confused by the label when bisecting.
- **CI now builds the Android module** and uploads the APK as an artifact, but that job has never run — `dev-sam` is unpushed. Treat the workflow itself as unverified until the first push goes green.
- **Debug builds get applicationId `io.agents.anima.debug`** while class names stay `io.agents.anima.*`. Anything matching on component names (accessibility-service enablement checks, `adb shell` commands) must use the runtime `packageName`, never a hardcoded string.
