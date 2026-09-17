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
