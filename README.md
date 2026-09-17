# Anima ⚡️

[![CI](https://github.com/K1NGS1LVER/anima/actions/workflows/ci.yml/badge.svg)](https://github.com/K1NGS1LVER/anima/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Python: 3.10+](https://img.shields.io/badge/Python-3.10%2B-blue.svg)](https://www.python.org/)
[![Zero Dependencies](https://img.shields.io/badge/Dependencies-0%20(Pure%20Stdlib)-brightgreen.svg)](https://github.com/K1NGS1LVER/anima)

**Anima** is an on-device, hybrid-engine autonomous mobile GUI agent runtime and skill engine.

Built to solve the core architectural bottlenecks of current academic and commercial mobile agent frameworks (AppAgent, AutoDroid, Mobile-Agent-v3): **severe token bloat ($0.90+/task)**, **high latency (up to 180s/step)**, **stateless amnesia**, and **shell injection vulnerabilities**.

---

## 🎯 Key Innovations & Architecture

```
                  [ Natural Language Goal ]
                             │
                             ▼
               [ Layer 0: Security Guard ]
         (Anti-Injection Parameterized Arrays)
                             │
                             ▼
         [ Layer 1: Intent & SQLite Skill Router ]
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
   [ Match Found ]                   [ Cache Miss ]
            │                                 │
            ▼                                 ▼
[ Layer 2: Speculative Replay ]      [ Layer 3: Hybrid Perception ]
 • 0 LLM Calls                        • UIFormer Structural Pruning
 • Weighted Multi-Attribute Locators  • Visual Fallback (Screenshots)
 • Sub-millisecond Execution          • Gemini 2.5 Flash / Heuristic
            │                                 │
            └───────────────┬─────────────────┘
                            ▼
           [ Layer 4: Verification & Auto-Compile ]
            • Popup Interceptor & Essential-State Verifier
            • Auto-compile Trajectory -> SQLite Skill Library
            • Self-Healing Drift Recovery on Layout Shift
```

1. **Skill Compilation & Stateful Replay ("Compile Once, Reuse Forever"):**  
   Standard agents re-reason every task step-by-step from scratch using costly LLMs. Anima compiles successful first-time trajectories into parameterized SQLite skill templates. On repeat tasks, Anima executes with **0 LLM calls**, achieving **instant execution** and zero API cost.
2. **Structural UI Pruning (UIFormer DSL):**  
   Raw Android accessibility trees account for **80–99% of agent token costs**. Anima's pruning engine strips non-semantic container nodes (`FrameLayout`, `LinearLayout`, `ViewGroup`) while preserving clickable semantics, cutting prompt payloads by **>60–80%**.
3. **Self-Healing Drift Recovery:**  
   If an app updates its layout, view IDs, or element positions, Anima detects locator drift, triggers the planner to re-ground the target element, and automatically **repairs the stored skill** in SQLite.
4. **Visual Grounding Fallback:**  
   If an app renders on an unlabelled custom canvas (games, Flutter), Anima automatically falls back to screenshot-based coordinate planning.
5. **Zero-Trust Security Boundary:**  
   Strictly rejects dangerous shell characters (`;`, `&&`, `|`, `` ` ``) and uses parameterized array arguments (`shell=False`) across all ADB calls, eliminating Remote Code Execution (RCE) vulnerabilities.

---

## ⚡️ Quickstart

Anima has **zero third-party dependencies** outside standard Python 3.10+.

### 1. Run Hermetic Unit & End-to-End Tests
```zsh
# Run fast unit tests (18 tests in ~0.6s):
make test

# Run multi-screen End-to-End journeys (6 scenarios in ~1.7s):
make e2e

# Run all 24 hermetic tests:
make test-all
```
*24 hermetic Python tests (18 unit + 6 E2E), plus 72 Kotlin engine tests, validating multi-screen navigation, dynamic parameter slots, popup auto-dismissal, UIFormer compression, on-device LiteRT execution, and security injection rejection.*

### 2. The Hackathon Demo — One Command
```zsh
make demo
```
Runs the full staged 3-act presentation on the hermetic `DemoDevice` (no emulator, no API key):
1. **Cold Run** — UIFormer prunes the XML, planner picks an action, skill compiles to SQLite.
2. **Warm Replay** — same goal replayed at **0 LLM calls in ~0.001s, $0.00**.
3. **Self-Healing Chaos** — injected permission popup + layout drift, auto-recovered and repaired.

Also export the live **D3 force-directed** Page Transition Graph dashboard:
```zsh
python3 anima.py "toggle wifi" --mock --ptg
```
*Opens `ptg_dashboard.html` in any browser — draggable nodes, color-coded edges (green = 0-LLM replay, pink = cold/heal), and the token/latency scoreboard.*

### 3. Run Benchmark Suite
Compare Anima against stateless LLM agents:
```zsh
python3 anima.py --benchmark
```

### 4. Connect a Physical Android Phone or On-Device Local Model
```zsh
# Physical Android Phone via ADB:
python3 anima.py "toggle wifi"

# 100% Offline On-Device Quantized Model (Gemma 4 via LiteRT-LM):
python3 anima.py "toggle wifi" --mock --local
```

*(Optional: Set `export GEMINI_API_KEY="your-key"` to enable cloud VLM multimodal reasoning over the REST API).*

### 5. Build & Install the Android Daemon APK
The phone-resident app runs the same engine on-device, with **no network calls at all**.

```zsh
# One-time toolchain setup (macOS) -- see android/README.md for the full walkthrough:
brew install openjdk@17 gradle
brew install --cask android-commandlinetools android-platform-tools
yes | sdkmanager --licenses
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"

make apk           # -> android/app/build/outputs/apk/debug/app-debug.apk
make apk-install   # installs to the connected phone
make android-test  # hermetic JVM engine tests, no device needed
```

Open **Anima** on the phone: it walks you through the two permission grants, then **RUN 3-ACT DEMO** plays Cold → Warm → Chaos on-screen with a live scoreboard. That demo needs no target app, no network and no permissions, by design.

Drive it from a laptop over the Tasker/MacroDroid intent API (the component
target is required — Android 8+ drops implicit broadcasts to manifest receivers):
```zsh
adb shell "am broadcast -a io.agents.anima.RUN_TASK \
  -n io.agents.anima.debug/io.agents.anima.TaskerReceiver --es goal 'toggle wifi'"
```
Anima broadcasts `io.agents.anima.TASK_COMPLETED` carrying the engine's real `success`, `latency_ms` and `llm_calls` — `0` on a replayed skill.

CI builds the APK on every push and uploads it as an artifact, so you can grab a build without any local Android toolchain.

---

## 📊 Benchmark Comparison

| Metric | Baseline Stateless Agent (AppAgent / AutoDroid) | Anima Hybrid-Engine Runtime |
| :--- | :--- | :--- |
| **Routine Task Latency** | ~180 seconds | **~0.001 seconds (sub-millisecond)** |
| **LLM Calls (Repeat Tasks)** | 1–5 costly calls | **0 LLM calls** |
| **Token Cost (Repeat Tasks)**| ~$0.90 / task | **$0.00 (Zero API cost)** |
| **XML Token Footprint** | ~1.5 MB uncompressed | **< 300 bytes (pruned Agent-DOM)** |
| **UI Drift Tolerance** | Fails on layout update | **Self-healing automatic repair** |
| **Command Execution** | Vulnerable to shell injection | **Hardened parameterized dispatch** |

---

## 🛠 Repository Structure

```
anima/
├── .github/workflows/ci.yml # CI: Python matrix (3.10-3.12) + Android APK build
├── android/                 # Native Android daemon -- standard Gradle project
│   ├── env.sh               # One-source toolchain setup (JDK 17 pin, SDK paths)
│   ├── gradlew              # Committed wrapper, pinned to Gradle 8.9
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── res/             # Vector icons, dark theme, layouts, service config
│       └── kotlin/io/agents/anima/
│           ├── MainActivity.kt              # Permission gate, goal runner, 3-act demo
│           ├── AnimaAccessibilityService.kt # UI capture, gestures, emergency halt
│           ├── FloatingOverlayService.kt    # Edge-glow + island HUD + kill-switch
│           ├── TaskerReceiver.kt            # io.agents.anima.RUN_TASK intent API
│           └── engine/                      # On-device skill engine (port of anima.py)
├── anima.py                 # Unified mobile GUI runtime (~1400 LOC, pure stdlib)
│   ├── Device               # Parameterized ADB & Mock controller
│   ├── UIFormer             # DSL structural tree pruner
│   ├── SkillDB              # SQLite skill storage & JSON export/import
│   ├── Matcher              # 5-attribute weighted locator evaluator
│   ├── Planners             # Gemini 2.5 Flash REST, Visual Fallback, Heuristic
│   ├── Interceptors         # Popup Interceptor & Essential-State Verifier
│   ├── PageTransitionGraph  # State hashing & interactive HTML dashboard
│   └── AnimaRuntime         # Dual-mode execution engine & benchmark harness
├── test_anima.py            # Hermetic unit validation suite
├── test_e2e.py              # Multi-screen end-to-end journeys
├── pyproject.toml           # Packaging metadata & console entrypoint (`anima`)
├── Makefile                 # Task runner (`make test`, `make demo`, `make apk`)
├── dev_plan.md              # Master engineering plan & single source of truth (SSOT)
├── PLAN.md                  # Current phase plan & decisions
├── CHECKLIST.md             # Task status, each tied to a verification command
├── CURRENT_PROGRESS.md      # Running log & environment state for contributors
├── CONTRIBUTING.md          # Contribution guidelines
├── LICENSE                  # MIT License
└── README.md                # Project documentation
```

---

## 📄 License
MIT License. Copyright (c) 2026 Anima Contributors.
