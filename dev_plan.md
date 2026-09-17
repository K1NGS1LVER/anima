# Anima: Master Engineering Plan & Technical Architecture Document

> **Document Classification:** Internal Technical Blueprint & Developer Roadmap  
> **Target Platform:** Android (Native APK / Embedded SDK) + Host-Tethered Python Runtime  
> **Audience:** Core Engineering Contributors, AI Researchers, and Hackathon Collaborators  
> **Status:** Active Execution (Phase 1 & 2 Complete)

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
| **SkillDroid** | **Skill Compilation & 5-Attribute Weighted Locators** (`res_id` @ 0.40, `desc` @ 0.25, `text` @ 0.20, `class` @ 0.10, `bounds` @ 0.05) | Replaces stateless LLM re-derivation with persistent SQLite skill templates. Cuts task execution time from 180s to <0.01s with **0 LLM calls** on repeat runs. |
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
   - Houses the local SQLite skill library, LiteRT-LM model runtime, Accessibility Service, and WindowManager Floating HUD overlay.
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
* **Quantized On-Device Models:** 4-bit / 8-bit quantized models (Gemma 4 via LiteRT-LM) requiring ~1.5–2.5GB RAM.
* **Hardware Acceleration:** Delegates local inference to the smartphone NPU/GPU via Android NNAPI, falling back to CPU on legacy chipsets.
* **Thermal & Battery Protection:** Mechanical skill replay turns off model inference completely, consuming negligible battery during repetitive automation.

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

## 9. Modular Implementation Roadmap

```
[ Phase 1: Core Foundation (COMPLETED) ]
  • Safe ADB & Mock Device abstraction (shell=False)
  • UIFormer DSL Pruning Engine (>60% token reduction)
  • SkillDroid SQLite Storage & 5-Attribute Weighted Locators
  • Speculative 0-LLM Replay Engine

[ Phase 2: Autonomous Prototype & Safety (COMPLETED) ]
  • Cold-start VLM planning (Gemini 2.5 Flash REST API + Heuristic)
  • Automated trajectory compilation into SQLite
  • Self-healing locator drift recovery
  • Visual Grounding Fallback on empty XML
  • Popup Interceptor & Essential-State Verifier
  • Packaging (pyproject.toml, Makefile, CI matrix, LICENSE)

[ Phase 3: Relative Coordinate Normalization & Parameter Slots (CURRENT) ]
  • Screen resolution agnostic relative coordinate mapping (0–1000 scale)
  • Extraction of dynamic parameter slots (e.g., {alarm_time}, {contact_name})
  • Regex + embedding hybrid intent router

[ Phase 4: Native Android APK Daemon ]
  • Kotlin AccessibilityService listener & gesture dispatcher
  • SYSTEM_ALERT_WINDOW persistent floating HUD with kill switch
  • On-device LiteRT-LM (Gemma 4) integration
  • Exported automation intent for Tasker / MacroDroid

[ Phase 5: Live Dashboard & Visualizer ]
  • Streamlit interactive Page Transition Graph (PTG)
  • Real-time token consumption and cost savings scoreboard
```
