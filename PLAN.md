# Anima — Phase 9 Plan: Make the APK Real

> **Working branch:** `dev-sam` (branched from `a7fe46e`)
> **Companion docs:** [CHECKLIST.md](CHECKLIST.md) (what's done, with proof) · [CURRENT_PROGRESS.md](CURRENT_PROGRESS.md) (running log + environment state) · [dev_plan.md](dev_plan.md) (architectural SSOT)

## Why this phase exists

The Python runtime is genuinely strong: `anima.py` is ~1400 LOC of pure stdlib with a hermetic suite that runs in ~2s, covering UIFormer pruning, SQLite skill compilation, weighted locators, self-healing drift recovery, and a staged 3-act demo. That half is hackathon-ready.

The Android half was not. `dev_plan.md` marked Phases 5–6 "COMPLETED", but the module had never been compiled once — CI never touched it, and no Gradle wrapper had ever existed in the repo. Concretely:

- **It could not build.** AAPT2 fails on three unresolved resources (`@string/accessibility_service_description`, `@mipmap/ic_launcher`, `@mipmap/ic_launcher_round`); `res/` contained only the accessibility config. The release build referenced a `proguard-rules.pro` that did not exist.
- **It would have crashed on a device even if it built.** `FloatingOverlayService` declares `foregroundServiceType="specialUse"` with no `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` child — on targetSdk 34 that throws `MissingForegroundServiceTypeException` the moment `startForeground()` runs.
- **It was unusable.** No Activity at all, so no launcher icon and no way to grant the accessibility or draw-over-apps permissions, both of which require a user journey through system Settings.
- **There was no agent inside the app.** `TaskerReceiver` captured accessibility nodes and then hardcoded `val success = true`. Every "0 LLM calls on-device" claim lived only in a Python process on a laptop.

The failure case we cannot afford is a judge asking *"can I hold the phone and watch it work?"*. Phase 9 closes exactly that gap.

## What "done" looks like

1. `./gradlew :app:assembleDebug` produces an installable APK, from a committed wrapper, on any machine that ran `source android/env.sh`.
2. The phone replays compiled skills on-device with **zero network calls**, reporting real `llm_calls` — not a hardcoded `true`.
3. A judge can install the APK, be walked through both permissions by the app itself, and tap one button to see Cold → Warm → Chaos with live numbers.
4. CI builds the Android module and runs its unit tests on every push, so "it compiles" stops being a claim and starts being a check.

## Decisions

| Decision | Rationale |
| :--- | :--- |
| **Work on `dev-sam`, no history rewrite** | `d7036ea` (the imperfect demo/HITL commit split) is already on `origin/main` and teammates may have pulled it. Rewriting published history for a cosmetic gain is not worth it; the split stays and is recorded here instead. |
| **No Jetpack Compose** | XML layouts + Material keep the dependency graph and build time small. That matters when the build has to be reproducible on a hackathon table. |
| **Standard Gradle layout** (`android/app/src/main/…`) | The old flat `src/` + `sourceSets` override worked but fought Android Studio and confused contributors. |
| **Kotlin engine mirrors the Python contract byte-for-byte** | Same SQLite schema, same locator weights, same snake_case JSON keys — so a skill compiled on the laptop imports into the phone unchanged. This is the cross-runtime story, and a test enforces it. |
| **Vector-only launcher icons** | The repo keeps zero binary files; `mipmap-anydpi-v26` covers everything at minSdk 26. |
| **JDK 17 pinned via `android/env.sh`** | AGP 8.5 requires exactly 17. A newer JDK on `PATH` (macOS ships 21/24) is the single most common build failure on this repo. |

## Steps

**Step 0 — Correctness fixes in the Python runtime.** Three real bugs found while auditing the code against its own documentation. These land first because the Kotlin port mirrors the *corrected* behaviour. ✅ Done — see `c16c8bd`.

**Step 1 — Toolchain.** JDK 17, Android SDK 34, build-tools, platform-tools, and a committed Gradle 8.9 wrapper. Reproducible via `source android/env.sh`.

**Step 2 — Make the module build.** Restructure to standard layout; fix every AAPT2 blocker, the FGS property, the missing `POST_NOTIFICATIONS`, and the AGP 8 `package=` attribute.

**Step 3 — Port the agent engine to Kotlin** (`io.agents.anima.engine`): models, UIFormer pruning, the weighted matcher (including a `difflib.SequenceMatcher`-equivalent ratio, which the JVM has no stdlib twin for), the SQLite skill store with template slot matching, the parameter extractor, and the replay loop with drift healing. `TaskerReceiver` stops faking success.

**Step 4 — The screen judges touch.** `MainActivity`: permission gate with deep links and live status, a goal runner, and the 3-act demo with a large-type scoreboard. The demo runs against a bundled hermetic fixture, so it works with no target app, no network, and no permissions — deliberately, so a failed permission grant on stage cannot kill the pitch.

**Step 5 — Tests & CI.** Pure-JVM unit tests (no device, no Robolectric) for the matcher, ratio function, parameter extractor and skill JSON round-trip; an Android job in CI that builds the APK and uploads it as an artifact.

**Step 6 — On-device validation** on a real phone over ADB.

**Step 7 — Documentation**, including these tracking docs.

## Out of scope for Phase 9

- Host↔phone bridge as a first-class CLI mode (`anima.py --android`). The JSON skill format is deliberately kept compatible so this stays cheap to add later.
- Further Python runtime polish beyond the Step 0 correctness fixes.
- iOS — deferred indefinitely, see `dev_plan.md` §11.1.
- Release signing and Play Store packaging. Debug builds only.
