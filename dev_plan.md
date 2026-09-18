# Anima: Master Engineering Plan & Technical Architecture Document

> **Document Classification:** Internal Technical Blueprint & Developer Roadmap  
> **Target Platform:** Android (Native APK / Embedded SDK) + Host-Tethered Python Runtime  
> **Audience:** Core Engineering Contributors, AI Researchers, and Hackathon Collaborators  
> **Status:** ⚠️ **Project pivoted — see §15.** Anima is now an autonomous app cartographer, not a task-execution agent. Phases 1–9 shipped and several components carry over; Phase 10 (multi-step planning) is superseded. Team plan: `PLAN.md`, `ASSIGNMENTS.md`, `KNOWLEDGE_PACK.md`.

---

## 1. Executive Summary & Core Premise

**Anima** is an **on-device, hybrid-engine autonomous mobile GUI agent runtime and skill engine**. Rather than acting as a passive conversational chatbot, Anima operates mobile applications on behalf of the user, converting high-level natural language instructions (e.g., *"Order my usual morning coffee on Starbucks"*, *"Set an alarm for 7:30 AM"*, *"Turn off Bluetooth and Wi-Fi"*) into concrete, multi-step actions (taps, text inputs, swipes, and system calls) directly on Android smartphones.

### The Problem with Existing Mobile AI Agents
Current open-source and commercial frameworks (baseline AppAgent, AutoDroid, Mobile-Agent-v3) suffer from four systemic architectural failures:
1. **Stateless Amnesia & High Costs:** Every single user request is re-derived from scratch using expensive, high-latency Cloud Vision-Language Models (VLMs), incurring **$0.90+ and ~180 seconds** per task even for daily repetitive tasks.
2. **UI Representation Token Bloat:** Passing raw, uncompressed 1.5MB Android accessibility XML trees consumes **80%–99% of total LLM prompt context**, exhausting context windows and driving up token bills. Conversely, purely vision-based parsers hallucinate coordinates on high-density displays.
3. **Fragility to UI Drift & Popups:** Rigid view-ID or DOM matching crashes when dynamic feeds update, ads pop up, or layout elements shift.
4. **Critical Zero-Trust Security Flaws:** Major frameworks execute model outputs directly via `subprocess.run(shell=True)`, exposing host devices to **Remote Code Execution (RCE)** and **Invisible Screen Text Attacks** (where attackers hide 2% opacity text in apps that VLMs read and execute, but human eyes cannot see).

### Anima's Winning Formula
Anima solves these bottlenecks by pairing **UIFormer-style structural pruning** (cutting XML tokens by >60–80%) with **SkillDroid-style skill compilation** (compiling first-time trajectories into parameterized SQLite templates with weighted locators). On repeat tasks, Anima executes with **0 LLM calls in <0.01 seconds**, achieving **100% cost reduction** and instant responsiveness while maintaining complete local data privacy.

---

## 2. End-to-End Architectural Flow

```
[ User Request (Voice / Text Floating HUD) ]
                     │
                     ▼
         [ Layer 0: Security & PII Guard ]
   - Redacts sensitive text & credit card tokens
   - Filters out low-opacity screen text injections
   - Enforces parameterized non-shell dispatch (shell=False)
                     │
                     ▼
       [ Layer 1: Intent Analysis Router ]
   - Fast-path check: Deterministic System APIs (am start, input keyevent)
   - Cache lookup: SQLite Skill Library via regex + fuzzy matching
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
 [ Full Match Found ]     [ No Match / New Flow ]
        │                         │
        ▼                         ▼
[ Layer 2: Skill Replay ]  [ Layer 3: Hybrid Neuro-Symbolic Agent ]
- Speculative Replay via   1. Capture XML & apply UIFormer DSL Pruning
  Weighted Locators        2. If unlabelled/canvas: Visual Fallback
- 0 LLM Calls              3. Reason via Local LiteRT / Gemini 2.5 Flash
- ~0.001s completion       4. Execute tap/swipe via Sanitized ADB/API
        │                         │
        └────────────┬────────────┘
                     ▼
  [ Layer 4: Verification, Self-Healing & Compilation ]
  - Verify functional progress via A3 Essential-State Milestones
  - Replay drifted? --> Trigger Planner re-grounding & repair skill
  - Cold run succeeded? --> Extract parameter slots & compile to SQLite
```

### Execution Lifecycles
1. **Layer 0 (Security & Sanitization Guard):** Filters screen captures against adversarial low-opacity text injections, redacts PII (passwords, OTPs, 16-digit payment card numbers), and guarantees that no shell string interpolation (`shell=True`) can ever execute on the host or phone.
2. **Layer 1 (Hierarchical Intent Router):** Determines whether the instruction can be fulfilled instantly via deterministic Android system commands (e.g. toggling airplane mode, launching package intents). If not, it checks the local SQLite Skill Library for a matching compiled skill template.
3. **Layer 2 (Speculative Skill Replay):** On a skill match, executes stored UI actions using multi-attribute weighted locators (`resource_id`, `content_desc`, `text`, `class_name`, `bounds`). Completes routine tasks in **sub-millisecond speed with 0 LLM calls**.
4. **Layer 3 (Hybrid Neuro-Symbolic Perception & Planning):** On a cold-start task:
   - Captures UI hierarchy and applies UIFormer DSL pruning (Filter, Merge, Pass-through) to generate a lean JSON `AgentDOM`.
   - If the app is an unlabelled custom canvas (e.g. Unity game or Flutter view), activates screenshot-based visual grounding.
   - Dispatches the compact DOM to Gemini 2.5 Flash (via REST) or an on-device local model.
5. **Layer 4 (Milestone Verification, Self-Healing & Compilation):** Verifies task completion using A3 Essential-State milestones (semantic progress rather than pixel layout). If successful, extracts typed parameter slots (e.g., `{contact_name}`, `{alarm_time}`) and compiles the trajectory into a reusable SQLite skill template. If layout drift occurs during replay, the self-healing loop re-grounds the element and updates the stored locator.

---

## 3. Harvesting the SOTA: Solution Deconstruction

| Harvested Framework | Feature Harvested | Architectural Benefit & Metric Impact |
| :--- | :--- | :--- |
| **SkillDroid** | **Skill Compilation & 5-Attribute Weighted Locators** (`res_id` @ 0.35, `desc` @ 0.25, `text` @ 0.20, `class` @ 0.10, spatial @ 0.10) | Replaces stateless LLM re-derivation with persistent SQLite skill templates. Cuts task execution time from 180s to <0.01s with **0 LLM calls** on repeat runs. |
| **UIFormer** | **DSL UI Tree Compression Plugin** (Filter, Merge, Pass-through) | Bottom-up merging and filtering of non-semantic container nodes (`FrameLayout`, `LinearLayout`, `ViewGroup`), cutting token consumption by **>60% to 80%**. |
| **ClawMobile** | **Deterministic-First Scheduling & Verify-and-Recover Loop** | Prioritizes system intents and ADB keyevents before invoking probabilistic vision models; automatically intercepts and dismisses system popups. |
| **PokeClaw** | **Phone-Resident Execution Harness & Intent API** | Eliminates mandatory PC tethering; exposes an exported Android Activity interface (`io.agents.anima.RUN_TASK`) for external automation (Tasker, MacroDroid). |
| **OmniParser** | **Visual Grounding Fallback (Set-of-Marks)** | Secondary perception layer for unlabelled custom canvases, Flutter widgets, or games where accessibility trees are blank. |
| **A3 Arena** | **Essential-State Procedural Milestone Verification** | Evaluates high-level functional milestones (e.g., "cart count updated", "toggle checked") rather than fragile DOM or pixel comparisons. |
| **CSA Security Advisory** | **Hardened Command Sanitization & Anti-Injection** | Eliminates Remote Code Execution (RCE) by enforcing array-based execution (`shell=False`) and screening inputs for adversarial injection strings. |

---

## 4. Product Delivery Format: Dual-Format Architecture

Anima is structured as a **Dual-Format Product Package**:

```
                              [ Anima Core Runtime ]
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 ▼                                             ▼
  [ Package 1: Standalone APK ]                 [ Package 2: Embedded SDK (.aar) ]
  • Phone-resident daemon                       • Third-party in-app automation
  • System AccessibilityService                 • Direct app-state binding
  • WindowManager Floating Overlay              • Zero-IPC in-process latency
  • Local SQLite Skill Storage                  • Maven / Gradle distribution
                 │
                 ▼
  [ External Automation API ]
  • Exported Intent: io.agents.anima.RUN_TASK
  • Native Tasker & MacroDroid compatibility
```

1. **Primary Deliverable — Standalone Android Application (APK):**
   - Autonomous background service running directly on consumer Android devices.
   - Houses the local SQLite skill library, Accessibility Service, and WindowManager Floating HUD overlay.
   - *Planned (Phase 10, §14):* an on-device model runtime behind the `Planner` seam. The APK ships today with the offline heuristic planner only — there is no model in it.
2. **Secondary Deliverable — Embedded Client SDK (`.aar` / Maven Library):**
   - Embedded by third-party app developers to grant their apps native AI agent capabilities with in-process performance.
3. **External Automation Integration API:**
   - Exposes an exported Activity/Broadcast entrypoint (`io.agents.anima.RUN_TASK`), enabling no-code automation platforms (Tasker, MacroDroid) to trigger complex agentic workflows.

---

## 5. Android System Mechanics & Permissions

### A. Permission Lifecycle & Onboarding
* **Accessibility Service (`android.permission.BIND_ACCESSIBILITY_SERVICE`):** The primary control plane used to read UI node trees (`AccessibilityNodeInfo`), track layout change events (`TYPE_WINDOW_STATE_CHANGED`), and dispatch native gestures (`dispatchGesture()`).
* **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`):** Grants permission to render a floating action hub (`TYPE_APPLICATION_OVERLAY`) over third-party applications.
* **Foreground Service (`FOREGROUND_SERVICE`):** Prevents Android’s Low Memory Killer (OOM) from terminating the agent process during background multi-app workflows.
* **Notification Listener Service (`NotificationListenerService`):** Intercepts incoming SMS and push notifications to extract one-time passwords (OTPs) and verification codes.
* **MediaProjection API:** Requests user consent for on-demand screencasting when visual grounding fallback is activated.

### B. Hardware & Resource Optimization
* **Quantized On-Device Models (Phase 10 target, not yet implemented):** 4-bit / 8-bit quantized models (Gemma via LiteRT / MediaPipe) requiring ~1.5–2.5GB RAM.
* **Hardware Acceleration:** Delegates local inference to the smartphone NPU/GPU via Android NNAPI, falling back to CPU on legacy chipsets.
* **Thermal & Battery Protection:** Mechanical skill replay turns off model inference completely, consuming negligible battery during repetitive automation.

### C. Google Gemini-Inspired Screen Overlay & "Agent-in-Control" UX
To eliminate user disorientation and the "Ghost in the Machine" panic when an autonomous agent operates a device, Anima adopts the Google Gemini / Circle-to-Search dual-layer overlay architecture:
1. **Perimeter Ambient Edge-Glow (`EdgeGlowView`):**
   - Renders a full-screen window with `FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_NO_LIMITS`.
   - Casts an animated, pulsating multi-color gradient border (Cyan $\to$ Indigo $\to$ Violet) hugging the phone's physical display bezels.
   - Crucially, `FLAG_NOT_TOUCHABLE` guarantees that the visual aura passes all synthetic gestures (`dispatchGesture()`) directly to underlying apps without intercepting touches.
2. **Bottom Floating Island Capsule (`BottomIslandCapsule`):**
   - Anchored at `Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL` (80dp above home navigation gesture bar) using `FLAG_NOT_TOUCH_MODAL`.
   - Styled as a frosted slate pill (`#E60F172A` Slate-900 at 90% opacity, 64px rounded radius, cyan border stroke).
   - Features real-time state display (`⚡️ Replaying Skill` vs. `Reasoning Cold Flow`), live performance scoreboard (`0 LLM Calls • ~0.001s • 100% Free`), and an immediate red **"Take Control"** emergency kill-switch button.
3. **Emergency Kill-Switch & Human-in-the-Loop (HITL) Interruption:**
   - Tapping "Take Control" or pressing any physical hardware key immediately halts execution via `AnimaAccessibilityService.emergencyHalt()`, extinguishes the ambient glow, and yields complete input sovereignty back to the user.

---

## 6. The Critical Do's and Don'ts (Anti-Patterns vs. Best Practices)

| Anti-Pattern (DO NOT DO) | Real-World Failure Consequence | Anima Engineering Standard (DO THIS) |
| :--- | :--- | :--- |
| **DO NOT rely solely on screenshot VLM calls for every step.** | Incurs 180s latency per step, costs $0.90/task, and fails on coordinate precision due to display scaling. | **DO prioritize pruned XML accessibility trees**, using visual models strictly as a secondary fallback. |
| **DO NOT pass raw 1.5MB XML trees to the LLM.** | Exhausts context windows, introduces high token costs, and induces model hallucination. | **DO apply UIFormer DSL pruning** to strip non-semantic containers, reducing payload by >60–80%. |
| **DO NOT pass model outputs directly to system shells (`shell=True`).** | Opens severe Remote Code Execution (RCE) vulnerabilities via invisible screen text injections. | **DO enforce strict parameter array sanitization** with `shell=False` across all system calls. |
| **DO NOT keep execution stateless.** | Re-derives the same UI flow every day, yielding inconsistent convergence rates. | **DO compile trajectories into SQLite skills** and replay with 0 LLM calls. |
| **DO NOT attempt automated handling of sensitive screens.** | Risk of leaking financial credentials, passwords, or biometrics. | **DO trigger Human-In-The-Loop (HITL)** manual override on password and payment screens. |
| **DO NOT use hardcoded absolute pixel coordinates.** | Replays break completely across different device resolutions and screen aspect ratios. | **DO store normalized relative coordinates (0–1000)** and evaluate multi-attribute locators. |

---

## 7. Critical Hidden Blind Spots (What Competitors Miss)

These are complex, real-world engineering realities that are not explicitly documented in baseline research papers:

### A. Dynamic WebViews & Canvas Shadow DOMs
* **The Reality:** Apps built with Flutter, React Native, or Unity often compile into a single custom `SurfaceView` or `Canvas`, leaving the Android accessibility tree blank.
* **Anima's Solution:** When `len(pruned_nodes) == 0`, Anima automatically switches to **Visual Grounding Fallback**, capturing a raw frame buffer via `screencap` and passing it to Gemini 2.5 Flash with normalized bounding box targets.

### B. Virtual Keyboard & IME Screen Occlusion
* **The Reality:** When an agent focuses an input field (`EditText`), the soft keyboard slides up, displacing screen bounds by 40%–50% and hiding target action buttons (e.g., "Submit" or "Done").
* **Anima's Solution:** Anima tracks window insets; if target elements are occluded, it dispatches `device.key(66)` (Enter) or `device.key(4)` (Back) to dismiss the keyboard before evaluating next-step locators.

### C. Screen Resolution & Density Normalization
* **The Reality:** A locator recorded on a 1440×3120 (516 PPI) display will miss targets if replayed on a 1080×2400 phone.
* **Anima's Solution:** Element bounding boxes are stored as relative floats normalized from `[0.0, 1.0]`, dynamically projected onto `device.get_screen_dimensions()`.

### D. Session Expiry & Biometric Interruption
* **The Reality:** Routine tasks (banking, shopping) frequently trigger `BiometricPrompt` or session timeouts.
* **Anima's Solution:** The Floating HUD detects biometric prompt packages (`com.android.systemui:id/biometric_prompt`) and immediately halts automation, ringing a gentle haptic chime to request user authentication before resuming.

---

## 8. Market Positioning & Hackathon Pitch Strategy

### The Corner to Own
Position Anima **not** as another generic conversational chat wrapper, but as an **On-Device, Hybrid-Engine Mobile Agent Runtime & Skill Engine**.

### The 3 Core Pillars to Pitch
1. **Unmatched Execution Speed & Efficiency:** Highlight the **10,000× speedup** and **100% token savings** on repeat tasks via compiled skills compared to baseline stateless agents.
2. **Ecological Validity:** Demonstrate that Anima operates on dynamic, real-world Google Play Store apps (Settings, Clock, Starbucks, Chrome), gracefully handling layout drift and popups.
3. **Zero-Trust Security Boundary:** Position Anima as the first mobile agent architecture immune to prompt injection attacks and host RCE vulnerabilities.

### The Live Dual-Mode Demonstration Script
* **Step 1 (Cold Run):** Give the agent a new goal (e.g., *"Toggle Wi-Fi"*). Show the UIFormer pruner compressing the XML tree by >60%, the planner selecting the action, executing the tap, and compiling the trajectory into SQLite.
* **Step 2 (Warm Replay):** Run the exact same command immediately. Show that the skill executes in **~0.001 seconds with ZERO LLM calls and $0.00 API cost**.
* **Step 3 (Self-Healing Chaos Test):** Insert a layout shift or unexpected popup. Show that Anima intercepts the popup or detects locator drift, re-grounds the action, and repairs the skill database in real time.

---

## 9. Modular Implementation Roadmap & Current Status

### Progress Audit Table

| Phase | Milestone Name | Status | Key Deliverables & Capabilities |
| :--- | :--- | :--- | :--- |
| **Phase 1** | **Core Foundation & Pruning** | ✅ **COMPLETED** | Parameterized `Device` & `MockDevice`, `UIFormer` pruning DSL (>60% token cut), `SkillDB` SQLite storage, `SpeculativeReplayEngine` (0 LLM calls). |
| **Phase 2** | **Autonomous Prototype & Safety** | ✅ **COMPLETED** | Dual-mode cold planning (Gemini 2.5 Flash REST + Heuristic), auto-compilation, self-healing drift repair, visual fallback on empty XML, `PopupInterceptor`, `EssentialStateVerifier`, CI matrix, `pyproject.toml`, MIT license. |
| **Phase 3** | **Resolution Invariance & Dynamic Slots** | ✅ **COMPLETED** | Relative coordinate normalization `[0.0, 1.0]` (1080p -> 1440p cross-device replay), `ParameterExtractor` (`{time}`, `{email}`, `{number}`), IME keyboard auto-dismissal, active weight normalization. |
| **Phase 4** | **Page Transition Graph (PTG) & Visual Dashboard** | ✅ **COMPLETED** | Live interactive visualization mapping screen transitions, extracting design tokens, and streaming a real-time token savings scoreboard for hackathon demos (`--ptg`). |
| **Phase 5** | **Native Android APK Daemon & Service Harness** | ✅ **COMPLETED** | Native Kotlin `AccessibilityService` listener, Gemini-style ambient edge-glow border + frosted bottom island HUD with emergency kill-switch (`SYSTEM_ALERT_WINDOW`), `ForegroundService`, and Tasker/MacroDroid Intent API (`io.agents.anima.RUN_TASK`). |
| **Phase 6** | **Local Model Client (Python) & Android Packaging** | ⚠️ **PARTIAL — see §14.1** | Python `LocalLiteRTPlanner`: a stdlib HTTP **client** that POSTs to a model server you run yourself (`--local`, `--local-url`). It is not an embedded runtime, and it has **no Kotlin equivalent** — the APK has no model. Gradle build setup shipped and was completed properly in Phase 9. |
| **Phase 7** | **End-to-End Verification & Multi-Screen Flow Validation** | ✅ **COMPLETED** | 5 realistic E2E journey scenarios (`test_e2e.py`), stateful multi-screen device emulation, form input slot substitution, mid-flight popup auto-recovery, and CLI subprocess verification. |
| **Phase 8** | **Polish, Demo & Hackathon Readiness** | ✅ **COMPLETED** | Staged 3-act `make demo` (Cold→Warm→Chaos) with live token-savings scoreboard, Biometric & Session-Expiry HITL (Python `BiometricGuard` + Kotlin haptic `biometricHalt()`), D3.js v7 force-directed PTG dashboard, skill import/export CLI. Suite at 20/20 tests. |
| **Phase 9** | **Buildable APK & On-Device Engine** | ✅ **VERIFIED ON HARDWARE** | Gradle wrapper + standard module layout, every AAPT2 blocker fixed, launcher Activity with permission gate, Kotlin port of the skill engine (store/matcher/replay) so the phone replays with 0 LLM calls, JVM unit tests, and an Android CI job that builds the APK. Tracked in `PLAN.md` / `CHECKLIST.md` / `CURRENT_PROGRESS.md`. |
| **Phase 10** | **Multi-Step Learning & Device-Agnostic Planning** | 📋 **PLANNED — see §14** | A bounded plan→act→observe cold loop so whole flows can be learned (today exactly one step is compiled), and a real model behind the `Planner` seam so grounding generalises across OEM skins instead of relying on rules hand-fitted to one phone. |

---

### Detailed Stage Breakdown

#### ✅ What Has Been Completed
1. **Hermetic Test Suite (17/17 Total Passing Tests):**
   - **Unit Tests (`test_anima.py`):** UIFormer pruner, security rejection, cold-to-warm loop, self-healing drift, visual fallback, popup interception, milestone progress, skill export/import, cross-resolution replay, dynamic parameter slots, Page Transition Graph, and the local-model HTTP client (against a stub server — no model is exercised).
   - **5 End-to-End Tests (`test_e2e.py` in 0.35s):**
     - `test_e2e_multi_screen_journey`: Stateful multi-screen application navigation (Launcher -> Settings -> Network -> Wi-Fi Toggle) with 0-LLM speculative replay.
     - `test_e2e_popup_interception_and_recovery`: Mid-flight system permission prompt interception, auto-dismissal, and underlying task completion.
     - `test_e2e_cli_dual_mode_subprocess`: Subprocess execution of `anima` CLI verifying Cold Compilation -> Warm Replay -> PTG HTML dashboard creation.
     - `test_e2e_security_injection_protection`: Adversarial shell metacharacter rejection across all system calls (`shell=False`).
     - `test_e2e_parameter_substitution_form`: Dynamic parameter slot extraction and novel value substitution across form flows.
2. **Production Repository Ergonomics:**
   - Standard packaging via `pyproject.toml` with console command `anima`.
   - GitHub Actions CI matrix testing Python 3.10, 3.11, and 3.12 across all pushes and PRs running both unit and E2E suites.
   - Developer task runner (`Makefile`) with `make test`, `make e2e`, `make test-all`, `make demo`, `make benchmark`.
   - MIT License and `CONTRIBUTING.md`.
3. **Core Engine Zero-Dependency Philosophy:**
   - Single-file runtime (`anima.py`) operating entirely on Python standard library (`xml.etree.ElementTree`, `sqlite3`, `difflib`, `subprocess`, `urllib`).
4. **Interactive Dashboard & PTG Engine:**
   - Live state transition graph mapping UI navigation and visual scoreboard (`ptg_dashboard.html`).
5. **Native Android APK Daemon & Service Harness (`android/`):**
   - Native Kotlin `AnimaAccessibilityService` with `dispatchGesture()`, `AccessibilityNodeInfo` streaming, and emergency kill-switch.
   - Google Gemini-inspired Screen Overlay (`FloatingOverlayService.kt`): animated edge-glow luminous border (`FLAG_NOT_TOUCHABLE`) signaling agent control + frosted bottom island capsule with live token stats and instant "Take Control" kill-switch.
   - `TaskerReceiver` broadcasting and receiving `io.agents.anima.RUN_TASK` for Tasker/MacroDroid automation.
6. **On-Device Local Inference & Android Packaging (`android/`):**
   - `LocalLiteRTPlanner` with `--local` and `--local-url` — an HTTP client for a model server the user runs; Python only, no model bundled.
   - Gradle build scripts (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`) for standalone APK compilation and deployment.
7. **End-to-End Verification & Multi-Screen Flow Validation:**
   - `test_e2e.py` — 5 comprehensive scenarios using `StatefulMockDevice` (no emulator required):
     1. `test_e2e_multi_screen_journey` — Launcher→Settings→Network→Wi-Fi toggle; cold compile + 0-LLM warm replay.
     2. `test_e2e_popup_interception_and_recovery` — mid-flight permission dialog auto-dismissed, task still completes.
     3. `test_e2e_cli_dual_mode_subprocess` — real subprocess; verifies exit code, COLD_COMPILED→WARM_REPLAY, PTG HTML created.
     4. `test_e2e_security_injection_protection` — adversarial shell metacharacters rejected by `ADBDevice._run()`.
     5. `test_e2e_parameter_substitution_form` — `FormInputPlanner`, `{time}` slot extraction, novel value substitution, 0-LLM replay.
   - `Makefile` updated: `make e2e`, `make test-all` (`discover -p "test_*.py"`).
   - `.github/workflows/ci.yml` updated: `test_e2e.py` added to CI pipeline.
   - **17 tests total (12 unit + 5 E2E) all passing in < 1 second.**

---

## 10. Phase 8 — Polish, Demo & Hackathon Readiness

The following items are approved backlog candidates for Phase 8. Each must be implemented as **atomic, incremental commits** (one logical unit per commit).

| Priority | Item | Status | Description |
| :--- | :--- | :--- | :--- |
| 🔴 **P0** | **Live Hackathon Demo Script** | ✅ **COMPLETED** | `make demo` runs a staged 3-act presentation: (1) Cold Run with UIFormer compression stats (measured real byte reduction), (2) Warm Replay at 0-LLM/~0.001s, (3) Self-Healing Chaos Test with injected layout drift + mid-flight popup. Rich ANSI-styled CLI output with live timing + token savings scoreboard against the stateless baseline. |
| 🟠 **P1** | **Biometric & Session-Expiry HITL** | ✅ **COMPLETED** | `BiometricGuard` in Python + `biometricHalt()` in Kotlin. Detects `com.android.systemui:id/biometric_prompt` and `biometric` markers mid-task, halts automation with `HITL_PAUSED` result / `releaseControlToUser`, and (Android) rings a gentle 250ms haptic pulse requesting the user to authenticate before resuming. Closes blind spot documented in §7.D. |
| 🟡 **P2** | **PTG Force-Directed Interactive Graph** | ✅ **COMPLETED** | `ptg_dashboard.html` upgraded from static edge list to a D3.js v7 force-directed graph with drag/charge/link simulation, color-coded edges (green = 0-LLM replay, pink = cold/heal), and arrowheads. Zero new Python dependencies — JS only. |
| 🟡 **P2** | **Skill Library Import/Export CLI** | ✅ **COMPLETED** | `anima --export-skills skills.json` / `anima --import-skills skills.json` shipped in Phase 1 `main()` via `SkillDB.export_json()/import_json()`; verified by `test_export_import_skills`. Enables community skill-sharing story. |

### Phase 8 Status
- ✅ **P0** `make demo` staged 3-act demo (12 tests remain hermetic: subprocess-verified by `test_e2e_staged_demo_subprocess`).
- ✅ **P1** Biometric & session-expiry HITL — Python `BiometricGuard` (`test_biometric_hitl_guard`) + Kotlin `biometricHalt()` with haptic chime.
- ✅ **P2** D3.js force-directed PTG dashboard (`test_ptg_force_directed_dashboard`).
- ✅ **P2** Skill import/export CLI already live (Phase 1 foundation).
- **Test suite grew to 20/20 passing tests (13 unit + 6 E2E + 1 demo subprocess).**

---

## 11. Architectural Decisions Log (Session-Recorded)

This section records key design decisions made during development sessions so future contributors understand the reasoning.

### 11.1 iOS Support — Explicitly Deferred
- **Decision:** iOS support is **deferred indefinitely** and not on the roadmap.
- **Reason:** Android's open `AccessibilityService` API, `adb`, `UiAutomator2`, and `WindowManager` overlay system have no direct iOS equivalent. iOS automation requires `XCUITest` (requires macOS + Xcode), `WebDriverAgent`, or private entitlements — all far outside the hackathon scope.
- **If revisited:** Would require a completely separate native layer (Swift/ObjC) and Apple Developer Program access.

### 11.2 Gemini-Inspired Screen Overlay — Approved Architecture
- **Decision:** Adopted the **dual-layer Google Gemini overlay** paradigm as the primary "agent-in-control" UX signal.
- **Rationale:** The Google Gemini / Circle-to-Search overlay is a pattern users already understand. Reusing this visual language eliminates the "Ghost in the Machine" panic response when an autonomous agent operates the device without any visible signal.
- **Implementation:**
  - Layer 1: `EdgeGlowView` — `FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_NO_LIMITS` full-screen ambient animated gradient border (Cyan→Indigo→Violet, 900ms pulsing). Passes all gestures through.
  - Layer 2: `BottomIslandCapsule` — frosted slate-900 pill anchored 80dp above navigation bar with live step description, token/cost scoreboard, and a red **"Take Control"** HITL kill-switch.
- **Rejection of alternatives:** Single toast/snackbar notifications were rejected (too transient, miss ongoing state). Persistent notification was rejected (not visible during full-screen app usage).

### 11.3 Zero External Pip Dependencies (Core Constraint)
- **Decision:** `anima.py` must remain pure Python standard library with **zero external pip dependencies**.
- **Allowed stdlib modules:** `xml.etree.ElementTree`, `sqlite3`, `difflib`, `subprocess`, `urllib.request`, `http.server`, `re`, `json`, `dataclasses`, `typing`, `pathlib`.
- **Rationale:** Maximizes portability, eliminates dependency-hell in CI, keeps the APK daemon's Python bridge footprint minimal, and demonstrates engineering discipline for hackathon judges.
- **Android layer** (`android/`) uses Kotlin + standard AndroidX — dependencies are acceptable there.

### 11.4 Incremental Commit Policy (Going Forward)
- **Decision:** All future development uses **atomic, incremental commits** — one logical unit of change per commit.
- **Format:** `type(scope): description` (conventional commits).
- **Examples:** `feat(demo): add staged cold/warm/chaos demo script`, `test(demo): add unit test for demo runner`, `docs(dev_plan): update phase 8 status`.
- **Never** bundle multiple feature additions in a single commit.

### 11.5 Git Signing — Always Use `--no-gpg-sign`
- **Decision:** All commits in this repo use `git commit --no-gpg-sign`.
- **Reason:** Global git config has `commit.gpgsign=true` with SSH signing at `/Users/dan/.ssh/id_ed25519_signing.pub`. Background git commits deadlock waiting for SSH passphrase without `--no-gpg-sign`.

### 11.6 Biometric HITL — Dual-Layer Guard, Both Runtimes
- **Decision:** The security boundary for biometric prompts is enforced identically in Python (`BiometricGuard.detect()` before every autonomous tap) and in Kotlin (`biometricHalt()` on `TYPE_WINDOW_STATE_CHANGED`).
- **Rationale:** The standalone APK executes without the Python bridge, and the Python CLI runs without the APK. Neither can rely on the other for a hard safety cut — the guard must exist at both trust boundaries.

---

## 12. Commit History Reference

| Commit | Phase | Description |
| :--- | :--- | :--- |
| `427964b` | 1–2 | `feat: complete MVP with visual fallback, popup interceptor, milestone verifier, and benchmark suite` |
| `62963da` | SSOT | `docs: add comprehensive dev_plan.md architecture blueprint for collaborators` |
| `d961cbd` | 3 | `feat(coords): add relative screen resolution normalization (0-1000 scale) for cross-device skill replay` |
| `938db0d` | 3 | `feat(params): add dynamic parameter slot extraction, active weight normalization, and IME keyboard auto-dismissal` |
| `5ef405c` | SSOT | `docs: update dev_plan.md with completed milestones and next stage status` |
| `50f6975` | 4 | `feat(ptg): add live Page Transition Graph engine and standalone HTML dashboard` |
| `7d0755f` | 5 | `feat(android): add phone-resident daemon scaffold and update SSOT roadmap` |
| `1948127` | 5 | `feat(overlay): implement Google Gemini-style edge-glow and floating bottom island HUD` |
| `398625d` | 6 | `feat(litert): add on-device local model planner, android gradle build setup, and update SSOT` |
| `0d1f17f` | 7 | `feat(e2e): add comprehensive 5-scenario end-to-end test suite and CI integration` |

### Phase 8 Working Log
- **`08` → `0d1f17f`**: Re-used existing P2 export/import CLI (already shipped in Phase 1 `main()`); backlog item closed without new code.
- **Demo craftsmanship decision:** `make demo` had run the bare CLI twice, which under-sells the pitch. Replaced with an in-process staged 3-act `run_demo()` (Cold→Warm→Chaos) using the hermetic `DemoDevice` — no API key, no emulator, fully deterministic. ANSI output, real byte-reduction measurements, and live timing, subprocess-verified by a new E2E test.
- **HITL placement decision (§11.6):** Python `BiometricGuard` checks raw XML *before* popup interception and planner dispatch on both cold and warm paths, so a biometric screen never reaches an autonomous tap. Kotlin `biometricHalt()` mirrors it because the standalone APK runs without the Python bridge.

---

## 13. Phase 9 — Buildable APK & On-Device Engine

### 13.1 Correcting the Phase 5–6 Status

Phases 5 and 6 were marked ✅ COMPLETED in §9 on the strength of the Kotlin source existing. The module had never been compiled — CI never touched it, and the repository had never contained a Gradle wrapper. An audit against the actual build found:

- **Three hard AAPT2 failures.** `@string/accessibility_service_description`, `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` were all referenced with no `res/values/` or `res/mipmap-*/` anywhere in the module.
- **A guaranteed runtime crash.** `FloatingOverlayService` declares `foregroundServiceType="specialUse"` without the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` `<property>` child that targetSdk 34 requires; `startForeground()` would have thrown `MissingForegroundServiceTypeException` on first launch.
- **No entry point.** No `<activity>` of any kind, so no launcher icon, and no way to grant the accessibility or overlay permissions — both of which require a user journey the app has to start.
- **No agent in the app.** `TaskerReceiver` captured the node tree and then set `val success = true`. Every on-device "0 LLM calls" claim was really the Python process on a laptop.
- **A missing `proguard-rules.pro`** referenced by the release build type.

None of this reflects badly on the design — the Kotlin itself compiles clean and the architecture held up. It reflects the absence of a build in the loop. Phase 9's first act is therefore an Android CI job, so "it compiles" is a check rather than a claim.

### 13.2 Scope

1. Gradle wrapper pinned to 8.9, standard `android/app/src/main/{kotlin,res}` layout, AGP 8.5.2 / Kotlin 1.9.24 / JDK 17.
2. Every resource, manifest and packaging blocker above, fixed.
3. `io.agents.anima.engine` — a Kotlin port of the runtime (models, UIFormer pruning, weighted matcher, SQLite skill store, parameter extractor, replay loop with drift healing) so the phone genuinely replays compiled skills with **zero network calls**.
4. `MainActivity` — permission gate with live status and deep links, goal runner, and the 3-act demo with an on-screen scoreboard.
5. Pure-JVM unit tests plus a cross-runtime test proving a `skills.json` written by Python scores identically in Kotlin.
6. On-device validation over ADB on a physical phone.

Working docs: `PLAN.md`, `CHECKLIST.md`, `CURRENT_PROGRESS.md`.

### 13.3 Decisions Log (continued from §11)

#### 13.3.1 Standard Gradle Layout — Adopted
- **Decision:** `android/` moved from a flat `src/` + `sourceSets` override to `android/app/src/main/{kotlin,res}` with a root/`:app` split.
- **Reason:** The override worked in principle but fought Android Studio, hid the missing-resource problem, and gave contributors no familiar shape to navigate.

#### 13.3.2 Kotlin Engine Mirrors the Python Contract Byte-for-Byte
- **Decision:** Identical SQLite schema, identical locator weights, identical snake_case JSON keys; enforced by a test.
- **Reason:** A skill compiled on a laptop must import into the phone unchanged. That portability is the cross-runtime story, and the cheapest way to keep it true is to make divergence fail a test.
- **Consequence:** Python's `difflib.SequenceMatcher` has no JVM stdlib equivalent, so the Gestalt ratio is reimplemented in Kotlin and pinned against values taken from real Python output.

#### 13.3.3 No Jetpack Compose
- **Decision:** XML layouts + Material components.
- **Reason:** A smaller dependency graph and faster, more predictable builds. The build has to be reproducible on a hackathon table, and Compose buys nothing for four static cards and a scoreboard.

#### 13.3.4 Published History Is Not Rewritten
- **Decision:** `d7036ea` is labelled `feat(demo)` but also carries the HITL/IPGuard and D3 changes, because `anima.py` was staged as a unit. It stays as-is.
- **Reason:** It is already on `origin/main` and teammates may have pulled it. Rewriting shared history for a cosmetic gain costs more than the imperfect label. Recorded here and in `CURRENT_PROGRESS.md` so it doesn't confuse a future bisect.

#### 13.3.5 The Demo Must Survive a Failed Permission Grant
- **Decision:** The 3-act demo runs against a bundled hermetic fixture and requires no device permissions, no target app and no network.
- **Reason:** Accessibility and overlay grants are multi-screen system journeys that can fail or stall on an unfamiliar phone in front of judges. The pitch cannot depend on them succeeding live.

#### 13.3.6 Essential-State Verification Is Advisory, Never Fatal
- **Decision:** A step that fails its milestone check is still executed, and the run still completes; unverified steps are counted and reported in the result.
- **Reason:** The check is a heuristic over screen diffs. A false negative aborting a live run is a far worse failure than a step that silently did nothing, and the count still surfaces the problem honestly.

### 13.4 Hardware Verification (Redmi Note 11, MIUI, Android 13)

The APK was built, installed and exercised on a physical device rather than an emulator.

| Check | Result |
| :--- | :--- |
| 3-act demo, on-screen | Cold 43ms / 1 call · **Warm 2ms / 0 LLM calls / $0.00** · Chaos 54ms self-healed |
| Real Settings UI via `RUN_TASK` | `toggle wifi` tapped (540,498), `wifi_on` 1 → 0 |
| Warm replay on hardware | `llmCalls=0` at 264ms and 687ms, each physically flipping Wi-Fi |
| Self-healing on a real app | Stale skill drifted, re-grounded, repaired in on-device SQLite |
| Network isolation | No network code and no `INTERNET` permission anywhere in the app |
| CI on a clean runner | Android job green in 2m27s, APK uploaded as a 4.7 MB artifact |

Four planner bugs surfaced only on real hardware, none of which any mock fixture could have caught — see §13.5. That is the argument for ecological validity in one paragraph: the fixtures were too tidy.

### 13.5 What Real Android Taught Us

1. **Labels are typography, not identifiers.** MIUI writes "Wi-Fi"; `"wifi" in "wi-fi"` is False. Matching now compares punctuation-stripped forms.
2. **The label is not the button.** The text lives on an inert `TextView` inside a clickable row. A matched non-clickable node now resolves to the smallest clickable node containing it.
3. **A mention is not a name.** Every Wi-Fi network row carries `"…,Connected,Wi-Fi signal full."`, so a mention outscored the switch and the agent opened a share-password dialog. An exact label match now outranks a substring.
4. **OEMs rename their widgets.** MIUI renders the master switch as a `CheckBox` with no text, no content-desc and no clickable flag — invisible to a filter keyed on those. `isCheckable` is now part of what makes a node meaningful, and a toggle goal prefers the row that encloses a switch-like widget.

Documentation carried its own bug: the `RUN_TASK` broadcast published in `android/README.md` could never have worked, because Android 8+ never delivers an implicit broadcast to a manifest-declared receiver. It reported success and did nothing.

### 13.6 Continuous Integration

`dev-sam` is pushed and the workflow runs four jobs on every push: the Python suite on 3.10/3.11/3.12, and an Android job that runs the JVM engine tests, assembles the debug APK and uploads it as a build artifact. Anyone on the team can pull a current build with `gh run download --branch dev-sam --name anima-debug-apk`, without installing a single Android tool.

The Android job earned its place immediately by failing on its first run: `android-actions/setup-android@v3` defaults to installing `tools platform-tools`, and the `tools` package was retired from the SDK repository, so the step exited 1 before Gradle ever started. The workflow now names the packages the build actually needs, matching `android/env.sh`. A module that had gone three phases without compiling now cannot regress silently.

### 13.7 The Wrong-Screen Bug (and why active-weight renormalization is dangerous)

The single worst defect found in Phase 9 did not fail loudly — it succeeded incorrectly. A compiled `toggle wifi` skill, replayed while the Bluetooth settings screen happened to be in front, turned **Bluetooth** off and reported `WARM_REPLAY success=true llmCalls=0`.

**Root cause.** MIUI's toggle row carries no resource-id, no text and no content-desc of its own — the label lives on an inert child. The compiled locator therefore held only a class name and a normalized position. `Matcher.score` divides by `active_weight`, the sum of the weights of the attributes that are actually populated, which is what makes a locator with a single strong attribute score 1.0. The same arithmetic turns *"I know nothing but the shape"* into a confident 1.0 against any `LinearLayout` at the same coordinates, on any screen, in any app:

```python
Matcher.score(wifi_row_locator, bluetooth_row) == 1.0
```

**The lesson.** Normalizing confidence by the evidence you happen to have is only sound when the evidence identifies something. Class and position do not. The fix restores identity to the locator rather than weakening the score:

1. **Label inheritance** — a clickable row with no label inherits the label inside it. Containers holding several labels inherit nothing, since a whole-screen container adopting the first text it finds is worse than no label at all.
2. **Contradiction veto** — a label the locator knows about, contradicted by the label the candidate carries (Gestalt ratio < 0.40), means *different element*, and scores 0. Ordinary wording drift still matches, so self-healing does not fire on every copy change.
3. **Specificity tie-break** — equal scores resolve to the tighter element, because an inherited label is shared with the container above it.

**Blast radius worth remembering:** an agent that fails visibly costs a retry; an agent that acts confidently on the wrong screen costs trust. Every future locator change should be tested against a same-shaped element on a *different* screen, which is what `WrongScreenGuardTest` and `test_locator_does_not_match_a_lookalike_row_on_another_screen` now pin.

### 13.8 Deterministic-First Routing, Implemented At Last

§2 has described a "Layer 1 fast-path: deterministic system APIs" since the first draft, and nothing implemented it. Typing "turn off bluetooth" into the app's goal box did nothing useful as a direct result: a GUI agent acts on what is in front of it, and pressing RUN leaves *Anima* in front. The executed taps landed on Anima's own goal input field — the text the user had just typed being, unsurprisingly, the best keyword match on screen for their own goal.

`IntentRouter` now maps a goal naming a system setting to the settings screen that owns it, launches it with `CLEAR_TOP` (Settings is a single task, so back-to-back goals would otherwise re-show the previous page), waits for it to render, and only then hands over to the perception loop. Zero LLM calls. Anima's own windows are also excluded from capture outright — the agent must never drive its own UI.

---

## 14. Phase 10 — Multi-Step Learning & Device-Agnostic Planning

> **Status: planned, not started.** Everything in this section is intent. Nothing here is implemented, and no claim in it should be read as a capability until its checklist item in `CHECKLIST.md` is ticked against a passing command.

### 14.1 Why This Phase Exists

Phase 9 proved the **replay** half of Anima on real hardware. The **learning** half does not exist yet, and two audits make the gap concrete.

**The cold path compiles exactly one step.** Both compile sites build a one-element list — `anima.py:1271` and `AnimaRuntime.kt:179`:

```python
steps=[Step(action=action, locator=Locator.from_node(target_node), param_slot=slot_name, value=val)]
```

There is no loop anywhere in the cold path. Every multi-step skill in this repository is a hand-written test fixture. "Set an alarm for 7:30 AM" — open Clock, Alarm tab, `+`, hour, minute, confirm — is unreachable by construction, and so is every example in §1 that made this project sound interesting. The replay side is already N-step-general (`for idx, step in enumerate(skill.steps)`), so the entire gap is on the compile side.

**There is no model in the APK.** `AnimaEngine.kt:15` hardcodes `private val planner: Planner = HeuristicPlanner()`, the module declares no `INTERNET` permission, and no ML dependency exists in `android/app/build.gradle.kts`. Grounding is token overlap against on-screen labels.

**And the OEM problem is the real driver.** Every planner fix in Phase 9 — punctuation-stripping so "wifi" matches "Wi-Fi", the actionable-ancestor redirect, preferring an exact label over a mention, the toggle-row rule that exists *only* because MIUI renders the Wi-Fi master switch as a `CheckBox` — is a rule hand-fitted to one skin on one phone. Samsung One UI, Pixel, ColorOS and HyperOS label, nest and class their widgets differently, and a German or Japanese device shares none of the English keywords at all. That approach scales to "the phones we tested on", not to "any make and model".

A model reading the pruned Agent-DOM generalises where keyword rules cannot: it can identify a row labelled *"Bluetooth-Verbindung"* containing a `CheckBox` as the Bluetooth toggle without anyone writing a German rule or a ColorOS rule. **Device-agnostic operation is the objective of this phase; the model is the means.** The heuristic planner remains as the offline floor and the fallback when no model is available.

### 14.2 Workstream A — Multi-Step Cold Planning

Replace the single `plan → act → compile` with a bounded `plan → act → observe` loop, in `AnimaRuntime.run` (Python, the cold tail from `anima.py:1248`) and `AnimaRuntime.coldCompile` (Kotlin, `AnimaRuntime.kt:137-206`, already factored into its own function).

Each iteration:

1. Capture and prune the screen.
2. **`BiometricGuard`** — halt to `HITL_PAUSED` before any autonomous tap. *Existing, unchanged.*
3. **`PopupInterceptor`** — a dismissal does not consume a step from the budget. *Existing.*
4. Compute a screen signature for cycle detection.
5. `planner.planStep(goal, domJson, nodes, history)` — the signature grows a `history` argument so a model can see what it already did; the heuristic ignores it except to avoid re-tapping an element it has already used.
6. A terminal `done` action ends the loop successfully; `null` means stuck and aborts.
7. Execute, re-capture, run `EssentialStateVerifier` for per-step progress.
8. Append to the trajectory.

**Loop guards — this is the whole safety story for autonomy:**

| Guard | Behaviour |
| :--- | :--- |
| **Step budget** | Hard cap (~12 actions), reported in the result. |
| **No-progress abort** | Two consecutive steps failing the essential-state check ends the run. |
| **Cycle detection** | A repeated screen signature with no intervening progress ends the run. |
| **Destructive-action guard** *(new)* | "Delete", "Remove", "Pay", "Buy", "Send", "Confirm purchase" and similar require HITL confirmation regardless of budget. |

The destructive-action guard is not optional. Single-step automation could only ever mis-tap once; an autonomous loop wandering a real app can reach a payment or deletion control on its own. Multi-step autonomy without this guard is not shippable.

**Compilation happens only on completion**, with the full N-step trajectory and per-step `ParameterExtractor` slot extraction. An aborted trajectory is reported and discarded — never saved. This follows directly from the Phase 9 wrong-screen bug (§13.7): a wrong skill is worse than no skill, because it *succeeds* incorrectly.

**Completion without a model.** The heuristic reports `done` when no remaining candidate scores above the floor *and* at least one step verified progress — "nothing left on screen matches the goal". Honest, and sufficient for toggle-shaped flows. A model planner returns `done` explicitly.

**Friction the audit surfaced, to be resolved during implementation:**

- Python has **two** single-step compile sites: the cold path and the visual-fallback branch (`anima.py:1227`). Kotlin has neither a visual fallback nor a `planVisual` method — it returns `MODE_FAILED` on empty nodes. Decide explicitly whether the loop covers visual fallback or excludes it.
- Kotlin's `ExecutionResult` has **no `stepsVerified` field** (Python does, added in Phase 9); it folds verification into message text. Align the two.
- `stepsExecuted` / `steps_executed` are hardcoded `1` literals in both cold returns. They must become derived counts.
- Kotlin's `Skill.steps` is an immutable `List<Step>`, so the loop needs a `MutableList` accumulator wrapped at the end.
- **`DemoDevice` is a single static screen**, purpose-built for a one-shot compile. A multi-step loop would be only trivially exercised by the 3-act demo. Extend the fixture to a multi-screen flow — `StatefulMockDevice` in `test_e2e.py` is the model — or the loop ships with no demo coverage.
- `test_e2e_multi_screen_journey` currently models a multi-screen journey as **three independent goals**, each cold-compiling one step. Under a one-goal/many-steps model that test needs redesigning, not just re-asserting.

### 14.3 Workstream B — A Real Planner Behind the Seam

The seam is already correct. `Planner` is a one-method interface, and both `AnimaRuntime.execute` and `DemoScript.run` accept it as a parameter — only `AnimaEngine.kt:15` hardcodes the implementation, and that becomes a constructor parameter with the heuristic as its default.

```
Planner (interface)
├── HeuristicPlanner     offline floor, default, always available   [exists today]
├── OnDevicePlanner      quantized Gemma via MediaPipe / LiteRT     [planned]
└── CloudPlanner         Gemini REST, opt-in, `cloud` flavor only   [planned]
```

- **Selection and capability gating.** A `PlannerFactory` resolves a stored preference against what is actually available: the on-device planner is offered only once a model file is present; the cloud planner exists only in the `cloud` flavor and only after explicit consent. It always falls back to the heuristic. Python's `HybridPlanner` (`anima.py:711`) is the pattern to copy — Kotlin has no such composer yet.
- **Prompt construction.** `UIFormer.toCompactJson(nodes)` already produces a compact Agent-DOM that is byte-compatible across both runtimes, e.g. `[{"id":2,"cls":"Switch","res_id":"switch_wifi","desc":"Wi-Fi","click":true,"center":[925,280]}]`. The prompt is that, plus the goal and the step history. The response is a strict JSON action — `{"action","target_index","value"}` — the same contract `LocalLiteRTPlanner` already speaks and `test_local_litert_planner` already pins. A parse failure falls back to the heuristic rather than failing the run.
- **Dependency reality.** The Android module has no networking library today (no OkHttp, Retrofit or Ktor) and no ML dependency. Cloud needs `HttpURLConnection` or a new dependency; on-device needs `com.google.mediapipe:tasks-genai` or the LiteRT equivalent. §11.3's zero-dependency rule is scoped to `anima.py` and **explicitly exempts the Android module**, so adding these is policy-consistent.

**Build flavors keep the strongest claim provable.** Cloud planning and model download both require `INTERNET`, and adding that permission to a single universal APK would destroy the one differentiator that is currently *provable rather than promised*: the APK cannot phone home, enforced by the manifest and checkable with `grep INTERNET`. Phase 10 therefore splits the module into an `offline` flavor (no `INTERNET`, heuristic + on-device only) and a `cloud` flavor. The offline flavor remains auditable in one command.

**Acceptance is cross-device, not single-device.** Workstream B is not complete when it works on the Redmi Note 11. It is complete when the same goal runs on **at least three unrelated OEM skins with no per-OEM code added**. Until that is demonstrated, the OEM problem has not been solved — it has only been moved.

### 14.4 Metric Honesty: `llmCalls` → `plannerCalls`

`llmCalls` counts *planner invocations*, not model calls. On-device that means a cold run currently reports `llmCalls=1` when no model exists, no network was touched, and the work was keyword matching. The warm-replay `llmCalls=0` claim is entirely true; the cold-run `1` is misleading in the other direction, and a reviewer who greps for `INTERNET` and finds nothing will rightly ask what that `1` was.

Phase 10 renames the counter to `plannerCalls` and reports `llmCalls` only when a model actually ran. This makes the pitch stronger, not weaker: *a cold run costs one planner call and zero dollars, because the planner runs on the phone.*

### 14.5 Decisions Log (continued from §13.3)

#### 14.5.1 Pluggable Planners Rather Than One Choice
- **Decision:** heuristic, on-device and cloud planners all sit behind the same interface, selectable at runtime, with the heuristic as the default.
- **Rationale:** no delivery decision gets locked in before the distribution question (§13 / production readiness) is settled, and the architecture is visible rather than hidden behind a hardcoded field.

#### 14.5.2 Build Flavors to Preserve the Zero-Network Property
- **Decision:** `offline` and `cloud` product flavors; the `offline` flavor never declares `INTERNET`.
- **Rationale:** "no network" is currently provable in one grep. A universal APK carrying `INTERNET` for an optional feature would reduce that to a promise, and promises are what §13.1 and §14.1 are about correcting.

#### 14.5.3 Autonomous Cold Runs, Bounded
- **Decision:** a new flow is learned autonomously up to a step budget, not confirmed step-by-step.
- **Rationale:** the compiled skill is available immediately and the demo is uninterrupted. Safety comes from the budget, no-progress and cycle aborts, the existing biometric HITL guard, and the new destructive-action guard — not from asking the user to approve every tap.

#### 14.5.4 An Incomplete Flow Is Never Compiled
- **Decision:** trajectories that abort are reported and discarded.
- **Rationale:** §13.7 — a skill that fires wrongly succeeds incorrectly, which costs more trust than failing visibly.

#### 14.5.5 Device-Agnosticism Is the Acceptance Bar
- **Decision:** Phase 10 is not complete until a goal runs on ≥3 unrelated OEM skins with no per-OEM code.
- **Rationale:** every Phase 9 planner rule was fitted to one MIUI device. Without a cross-device bar, Phase 10 would produce a second set of device-specific rules and call it generalisation.

---

## 15. The Pivot — Autonomous App Cartographer

### 15.1 What Changed and Why

Anima was built as a task-execution agent: a natural-language goal becomes taps, and a successful trajectory compiles into a replayable skill. The challenge we are now building for is a different problem — **explore an unfamiliar Android app with no goal at all, and come away with a complete structured understanding of it**: every screen, what each does, the elements and form fields on it, the journeys connecting them, and the app's brand and design language.

The underlying insight is the same one that motivated Anima: knowledge about an app is expensive to acquire by hand and stale the moment the app updates. The difference is what we emit. Anima emitted *procedures*; the cartographer emits a **declarative App Knowledge Pack** that another AI reads.

Full plan in `PLAN.md`; the schema contract is `KNOWLEDGE_PACK.md`; per-person briefs are `ASSIGNMENTS.md`.

### 15.2 What Carries Over

Phase 9 left a working on-device agent verified on real hardware, and the useful parts transfer cleanly:

| Asset | Transfers as |
| :--- | :--- |
| `UIFormer` pruning | The compaction engine. Already >60% reduction, already solving the 1.5MB problem the brief names. |
| Accessibility capture and gesture dispatch | The exploration execution plane. |
| `PageTransitionGraph` *shape* | The app map. Nodes are screens, edges are transitions — but the hash function is replaced, see §15.4. |
| `PopupInterceptor`, `BiometricGuard` | Crawl hygiene: dismiss consent dialogs, treat biometric prompts as a boundary. |
| `FloatingOverlayService` kill switch | Safety for an unattended crawler in an unfamiliar app. |
| Deterministic JSON discipline (`SkillJson.kt:18`) | Byte-stable pack output, now a hard requirement rather than a nicety. |
| Skill replay engine | **Journey verification** — see §15.3. |

Built from nothing: exploration policy, screenshots, scroll, LLM/VLM understanding, design extraction, the pack schema and store, gate passing with test credentials, and the viewer.

### 15.3 The Differentiator: an Executable Knowledge Pack

Documentation of an app is inert. Ours will not be: journeys in the pack carry enough structure to be **replayed on a real device to verify they are still true**. That is precisely the replay engine Phase 9 shipped and hardware-verified.

This answers the brief's own framing of the problem — *knowledge breaks the moment the app updates* — with a mechanism rather than a promise. A pack that can re-verify itself against a new app version, and diff what changed, is a materially different product from a prettier JSON file.

### 15.4 Stability Is the Hard Requirement, and Our Current Code Fails It

The brief requires output that is stable across repeat scans. `PageTransitionGraph.compute_screen_signature` (`anima.py:869`) cannot deliver that, and must not be ported:

- It uses Python's built-in `hash()` on a string, which is **salted per process**. Two runs of the same scan produce different screen IDs.
- It folds `n.id` — a prune-order index — into the signature whenever a node lacks a resource-id or text, so any reordering changes the hash.
- It reads only the first ten nodes, so screens sharing a toolbar collide.
- It truncates to four digits, which collides at any real scale.

The replacement is specified in `KNOWLEDGE_PACK.md`: SHA-256 over a canonical structural fingerprint — sorted resource-ids plus a class-path skeleton — explicitly excluding text, counts, bounds and timestamps. Element IDs are derived the same way and must survive changed row text.

A second, less obvious threat to stability is the model itself: `name`, `purpose` and `semantic` are LLM-authored, and models are not deterministic. Temperature 0 is necessary but insufficient. **Every model result is cached keyed by structural hash** so a rescan reuses prior text verbatim. The acceptance test is byte-identical `screens[]` across two consecutive scans.

### 15.5 Architecture: Modules, One Owner Each

The repo is single-module. Five people working simultaneously in one module is a merge-conflict machine, so it splits into `:core`, `:capture`, `:explore`, `:understand`, `:design`, `:store`, `:app`. Everything depends on `:core` and nothing else horizontal. The split is mechanical because the `engine` package already avoids `android.*` imports specifically so it unit-tests on a plain JVM.

### 15.6 Decisions Log (continued from §14.5)

#### 15.6.1 Hybrid, Swappable Understanding Backends
- **Decision:** one `ScreenUnderstander` interface with cloud, on-device and heuristic implementations; heuristic is the floor and always available.
- **Rationale:** the brief points at on-device models, but exploration needs many inferences per scan and on-device latency is a live-demo risk. A swappable seam lets us demo fast and still tell the privacy story honestly.

#### 15.6.2 No Demo Mode
- **Decision:** the product scans real apps. Fixtures exist for development and tests, never as a staged product path.
- **Rationale:** the previous 3-act demo was theatre built on a hermetic single-screen fixture. A judge asking "scan this app instead" must get a real answer.

#### 15.6.3 Retire the Task-Execution Scaffolding
- **Decision:** `DemoDevice`, `DemoScript`, the 3-act demo, `run_benchmark`, `IntentRouter`, `TaskerReceiver` and the skill-replay CLI flags are removed — roughly 1,000 lines.
- **Rationale:** all of it exists to serve *goal → replay a specific tap sequence*, the opposite of exploring never-seen screens. `run_benchmark` in particular compares against hand-typed constants rather than measurements, which is a pitch artifact, not a benchmark.

#### 15.6.4 minSdk 26 → 30
- **Decision:** raise minSdk to 30.
- **Rationale:** `AccessibilityService.takeScreenshot()` is API 30 and far cleaner than MediaProjection, which needs a consent dialog per session. Screenshots are required for design extraction, the viewer and the rebuild test. The cost is a small slice of old devices.

#### 15.6.5 Crawler Safety Is Part of the Product
- **Decision:** destructive-control deny-list, package-boundary enforcement, recovery ladder, hard budgets and a live kill switch are mandatory before any crawl of a real app.
- **Rationale:** an unattended agent inside a banking app is the genuine risk in this project. Single-step automation could mis-tap once; a crawler can reach a payment or deletion control on its own.
