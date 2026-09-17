# Anima Android Native Daemon & Accessibility Harness

This module implements the **Phone-Resident Execution Harness** for the Anima autonomous mobile GUI agent, delivering the architecture outlined in [dev_plan.md](../dev_plan.md).

## Architectural Components

1. **`AnimaAccessibilityService.kt`:**
   - Registers as an Android `AccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`).
   - Extracts filtered semantic UI trees directly from `rootInActiveWindow` using UIFormer pruning logic.
   - Synthesizes and dispatches native Android gestures (`dispatchGesture()`) with sub-millisecond latency.
   - Intercepts system popups and auto-dismisses transient permissions.
   - Implements an atomic emergency kill switch (`emergencyHalt()`) for immediate human takeover.

2. **`FloatingOverlayService.kt` (Gemini-Style Ambient Screen Overlay):**
   - **Luminous Edge-Glow Perimeter:** Fullscreen overlay window with `FLAG_NOT_TOUCHABLE` casting an animated, pulsating cyan/indigo/violet gradient border around the phone bezels, visually signaling that Anima has assumed execution control.
   - **Bottom Floating Island Capsule:** Frosted dark capsule anchored at the screen bottom displaying live agent status (`Replaying Skill (0 LLM)`), performance scoreboard, and an instant **"Take Control"** emergency kill-switch button.

3. **`TaskerReceiver.kt`:**
   - Exported broadcast receiver implementing the **PokeClaw Intent API** (`io.agents.anima.RUN_TASK`).
   - Enables no-code automation apps (**Tasker**, **MacroDroid**) and local scripts (**Termux**, **ADB**) to trigger complex multi-step mobile skills.

---

## Triggering Automation via ADB / Tasker

### 1. Triggering a Goal via ADB Broadcast
```bash
adb shell am broadcast \
  -a io.agents.anima.RUN_TASK \
  --es goal "Set alarm for 7:30 AM" \
  --es params '{"time":"07:30"}'
```

### 2. Tasker & MacroDroid Integration
- **Action:** Send Intent
- **Action String:** `io.agents.anima.RUN_TASK`
- **Extra 1:** `goal:Order my usual coffee`
- **Extra 2 (Optional):** `params:{"size":"grande"}`
- **Target:** Broadcast Receiver

### 3. Listening for Completion
Anima broadcasts `io.agents.anima.TASK_COMPLETED` with extras:
- `success`: `Boolean`
- `latency_ms`: `Long`
- `llm_calls`: `Int`

---

## Building the Daemon APK

```bash
# Build the standalone debug APK:
gradle assembleDebug

# Or install directly to connected Android device via ADB:
gradle installDebug
```
