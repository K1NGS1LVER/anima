# Phase 9 Checklist

> A box gets ticked **only** when its verification command passes on this machine. No box is ticked on the strength of "the code looks right" — that is exactly how Phases 5–6 came to be marked complete for a module that had never been compiled.
>
> Context: [PLAN.md](PLAN.md) · Running log: [CURRENT_PROGRESS.md](CURRENT_PROGRESS.md)

Run `source android/env.sh` before any Gradle or ADB command below.

## Step 0 — Python correctness fixes

- [x] Essential-state verification wired into the warm replay loop — `make test-all` (22/22)
- [x] Essential-state verification wired into the cold path — `make test-all`
- [x] `BiometricGuard` markers reachable (case bug) — `make test-all`
- [x] PTG records real post-action state instead of self-loops — `make test-all`
- [x] Documented locator weights reconciled with `Matcher.WEIGHTS` — `grep res_id dev_plan.md`
- [x] No Python regressions — `make test-all` → 22 tests, OK

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
- [ ] `res/values/strings.xml` with `accessibility_service_description`
- [ ] `res/values/colors.xml` matching the overlay's palette
- [ ] `res/values/themes.xml` — `Theme.Anima`
- [ ] Vector adaptive launcher icons (`ic_launcher`, `ic_launcher_round`), no binaries
- [x] `proguard-rules.pro` exists and keeps framework entry points
- [ ] Manifest: `package=` removed, `POST_NOTIFICATIONS` added, FGS `<property>` added, `<activity>` declared
- [ ] **`./gradlew :app:assembleDebug` produces an APK** ← the gate for this step

## Step 3 — Kotlin engine

- [ ] `Models.kt`, `UIFormer.kt`, `Matcher.kt`, `TextRatio.kt`, `ParameterExtractor.kt` — no `android.*` imports, JVM-testable
- [ ] `SkillStore.kt` — schema identical to Python, snake_case JSON keys
- [ ] `ReplayEngine.kt` — biometric guard first, popup intercept, drift heal, IME dismissal after text
- [ ] `AccessibilityDevice` + hermetic `DemoDevice`
- [ ] `TaskerReceiver` reports the engine's real result instead of `val success = true`
- [ ] Zero network calls anywhere in the Kotlin engine — `grep -rE "HttpURLConnection|okhttp|java.net.URL" android/app/src/main` returns nothing

## Step 4 — Judge-facing app

- [ ] Permission gate: live status + deep links for Accessibility and Draw-Over-Apps, re-checked in `onResume`
- [ ] `POST_NOTIFICATIONS` runtime request on API 33+
- [ ] Goal input + Run, disabled with an explanation until the service is on
- [ ] 3-act demo button, streaming each act into the UI as it lands
- [ ] Scoreboard: mode, latency, LLM calls, cost vs. the ~180s / ~$0.90 stateless baseline
- [ ] Engine calls run off the main thread

## Step 5 — Tests & CI

- [ ] `./gradlew :app:testDebugUnitTest` green
- [ ] `TextRatio` matches `difflib.SequenceMatcher` on values confirmed against real Python output
- [ ] Matcher: exact-id 1.0, fuzzy desc, spatial falloff, sub-threshold null, first-node-wins ties
- [ ] Cross-runtime: a `skills.json` written by Python imports into the Kotlin store and scores identically
- [ ] CI `android` job builds the APK and uploads it as an artifact
- [ ] `make apk` / `make android-test` shortcuts

## Step 6 — On-device (needs the phone plugged in)

- [ ] `adb devices` lists the phone
- [ ] `./gradlew :app:installDebug` succeeds
- [ ] App opens, both permission rows flip to ENABLED after the guided grants
- [ ] 3-act demo runs on-screen, Act 2 reports 0 LLM calls
- [ ] `adb shell am broadcast -a io.agents.anima.RUN_TASK --es goal "toggle wifi"` drives the real Settings UI
- [ ] Edge-glow overlay appears and "Take Control" halts execution
- [ ] Warm run reports `llm_calls=0` in the completion broadcast

## Step 7 — Docs

- [x] `PLAN.md`, `CHECKLIST.md`, `CURRENT_PROGRESS.md` created
- [ ] `dev_plan.md`: Phase 9 row + honest correction of the Phase 5–6 status
- [ ] `android/README.md`: real build/run/permission instructions
- [ ] `README.md`: APK quickstart
- [ ] `CONTRIBUTING.md`: pointer to these three docs
