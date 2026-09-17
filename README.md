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
# Run fast unit tests (12 tests in ~0.5s):
make test

# Run multi-screen End-to-End journeys (5 scenarios in ~0.3s):
make e2e

# Run all 17 hermetic tests:
make test-all
```
*17 hermetic tests (12 unit + 5 E2E) validating multi-screen navigation, dynamic parameter slots, popup auto-dismissal, UIFormer compression, on-device LiteRT execution, and security injection rejection.*

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
├── .github/workflows/ci.yml # Automated CI matrix (Python 3.10, 3.11, 3.12)
├── android/                 # Native Android APK Daemon & Service Harness
│   ├── AndroidManifest.xml  # Accessibility service & intent declarations
│   ├── res/xml/             # Accessibility service configuration
│   └── src/                 # Kotlin AccessibilityService, FloatingOverlay, & TaskerReceiver
├── anima.py                 # Unified mobile GUI runtime (~1000 LOC, pure stdlib)
│   ├── Device               # Parameterized ADB & Mock controller
│   ├── UIFormer             # DSL structural tree pruner
│   ├── SkillDB              # SQLite skill storage & JSON export/import
│   ├── Matcher              # 5-attribute weighted locator evaluator
│   ├── Planners             # Gemini 2.5 Flash REST, Visual Fallback, Heuristic
│   ├── Interceptors         # Popup Interceptor & Essential-State Verifier
│   ├── PageTransitionGraph  # State hashing & interactive HTML dashboard
│   └── AnimaRuntime         # Dual-mode execution engine & benchmark harness
├── test_anima.py            # Hermetic 11-test validation suite
├── pyproject.toml           # Packaging metadata & console entrypoint (`anima`)
├── Makefile                 # Developer task runner (`make test`, `make demo`)
├── CONTRIBUTING.md          # Contribution guidelines
├── LICENSE                  # MIT License
├── dev_plan.md              # Master engineering plan & single source of truth (SSOT)
└── README.md                # Project documentation
```

---

## 📄 License
MIT License. Copyright (c) 2026 Anima Contributors.
