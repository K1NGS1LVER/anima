# Current Progress

> Running log for Phase 9. Newest entry first. Update this at every commit.
> Context: [PLAN.md](PLAN.md) · Task status: [CHECKLIST.md](CHECKLIST.md)

## Where to pick up

**Next action:** Step 6, on-device validation. The APK builds and installs cleanly; what's left is running it on a real phone. The phone is connected over USB but `adb` reports it `unauthorized` — accept the "Allow USB debugging?" dialog on the phone, then `make apk-install`.

**Steps 0–5 are done and verified.** `./gradlew :app:assembleDebug` produces a 5.7 MB APK, 65 Kotlin tests and 22 Python tests pass.

**Nothing is pushed yet.** `dev-sam` is local-only.

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

### 2026-09-18 — Steps 2–5 complete: the APK builds and the engine is real

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
- **CI does not build the Android module yet** (Step 5). Until it does, treat any "the Kotlin compiles" claim as unverified.
- **Debug builds get applicationId `io.agents.anima.debug`** while class names stay `io.agents.anima.*`. Anything matching on component names (accessibility-service enablement checks, `adb shell` commands) must use the runtime `packageName`, never a hardcoded string.
