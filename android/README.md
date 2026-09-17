# Anima Android Daemon

The phone-resident execution harness for Anima: an `AccessibilityService` that reads the live UI tree, an on-device skill engine that replays compiled skills with **zero network calls**, a Gemini-style ambient overlay, and an Intent API for Tasker/MacroDroid.

This module is a standard single-module Gradle project. Everything below has been run on macOS (Apple Silicon); Linux differs only in the Homebrew paths.

---

## 1. One-time toolchain setup

```bash
brew install openjdk@17 gradle
brew install --cask android-commandlinetools android-platform-tools
yes | sdkmanager --licenses
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > android/local.properties
```

> **JDK 17 is not optional.** AGP 8.5 rejects newer JDKs, and macOS ships 21/24 by default — a stray newer JDK on `PATH` is the single most common build failure here. `android/env.sh` pins it for you.

> Homebrew's system `gradle` is 9.7 and **is not compatible with AGP 8.x**. It is used exactly once, to generate the wrapper. Always build through `./gradlew`, which is pinned to Gradle 8.9 and committed.

## 2. Build

```bash
source android/env.sh          # pins JAVA_HOME + ANDROID_HOME, adds adb to PATH
cd android
./gradlew :app:assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
```

Or from the repo root: `make apk`, `make android-test`, `make apk-install`.

CI builds this module on every push and uploads the APK as a build artifact, so you can grab a build without a local toolchain.

## 3. Install and grant permissions

```bash
adb devices                    # enable Developer Options + USB debugging on the phone first
cd android && ./gradlew :app:installDebug
```

Open **Anima** on the phone. The app's own permission gate walks you through both grants and shows live status:

- **Accessibility Service** — the control plane. Reads `AccessibilityNodeInfo` trees and dispatches gestures.
- **Display over other apps** — the ambient edge-glow and the bottom island HUD with the kill-switch.

To grant them from a laptop instead (useful for a scripted demo):

```bash
# NOTE: debug builds use the applicationId io.agents.anima.debug
adb shell settings put secure enabled_accessibility_services \
    io.agents.anima.debug/io.agents.anima.AnimaAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell appops set io.agents.anima.debug SYSTEM_ALERT_WINDOW allow
```

## 4. Run a task

From the app: type a goal and hit **RUN**, or hit **RUN 3-ACT DEMO** for the Cold → Warm → Chaos story. The demo runs against a bundled hermetic fixture, so it needs no target app, no network and no permissions — deliberately, so a failed grant on stage can't kill the pitch.

Over ADB, via the Tasker/MacroDroid Intent API:

```bash
adb shell am broadcast -a io.agents.anima.RUN_TASK \
  --es goal "Set alarm for 7:30 AM" \
  --es params '{"time":"07:30"}'

adb logcat -s AnimaAgent AnimaTasker
```

Anima broadcasts `io.agents.anima.TASK_COMPLETED` with `success` (Boolean), `latency_ms` (Long) and `llm_calls` (Int). On a replayed skill, `llm_calls` is `0` — those are the engine's real numbers, not a fixed value.

**Tasker / MacroDroid:** Action → Send Intent, action string `io.agents.anima.RUN_TASK`, extra `goal:Order my usual coffee`, optional extra `params:{"size":"grande"}`, target Broadcast Receiver.

---

## Architecture

```
app/src/main/kotlin/io/agents/anima/
├── AnimaAccessibilityService.kt   UI tree capture, dispatchGesture(), emergency + biometric halt
├── FloatingOverlayService.kt      Edge-glow perimeter + bottom island HUD + "Take Control"
├── TaskerReceiver.kt              io.agents.anima.RUN_TASK intent API
├── MainActivity.kt                Permission gate, goal runner, 3-act demo scoreboard
└── engine/                        On-device agent engine (Kotlin port of anima.py)
```

The `engine` package mirrors the Python runtime deliberately: same SQLite schema, same locator weights, same snake_case JSON keys. A skill compiled on a laptop imports into the phone unchanged, and a unit test enforces that compatibility.

The model-free files (`Models`, `Matcher`, `TextRatio`, `UIFormer`, `ParameterExtractor`) carry no `android.*` imports, so they are tested on the plain JVM with no device and no Robolectric.

### Safety boundaries

- **Biometric / lock-screen prompts** halt automation before any autonomous tap, in both runtimes independently — the APK runs without the Python bridge and vice versa (see `dev_plan.md` §11.6).
- **"Take Control"** on the HUD, and any hardware key, trigger `emergencyHalt()` and return input sovereignty immediately.
- **No network.** The on-device engine makes no HTTP calls of any kind.
