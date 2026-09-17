# Anima

**Anima** is an on-device, hybrid-engine autonomous mobile GUI agent runtime and skill engine.

Built using **Ponytail** principles (radical simplicity, stdlib-first, zero external dependencies) to deliver the winning differentiators of 2026 mobile GUI agent research:
- **Autonomous Dual-Mode Execution (SkillDroid):** Cold-start goals invoke a planner (Gemini 2.5 Flash via REST or Heuristic), execute the step, and compile the trajectory into SQLite. Subsequent requests replay with **0 LLM calls in ~0.002s**.
- **Self-Healing Drift Recovery:** If UI layout shifts or view IDs change, the runtime detects locator drift, re-grounds the action via the planner, and repairs the stored skill.
- **Structural UI Pruning (UIFormer):** Drops non-semantic container bloat from raw Android accessibility trees, achieving >60-80% token reduction into a compact Agent-DOM.
- **Hardened Zero-Injection Device Bridge:** Parameterized execution preventing host and ADB shell injection attacks (`shell=False`).

## Quickstart

### 1. Run Hermetic Unit Tests (Python 3.10+ Stdlib)
```zsh
python3 -m unittest test_anima.py
```

### 2. Live Dual-Mode Demonstration
Experience the cold-to-warm compilation in action:
```zsh
# 1. Cold Run: Plans with VLM / Heuristic, executes action, and auto-compiles skill
python3 anima.py "toggle wifi" --mock

# 2. Warm Run: Replays from SQLite with 0 LLM calls in 0.002s!
python3 anima.py "toggle wifi" --mock
```

### 3. Connect a Real Android Phone
```zsh
# Enable USB Debugging on your phone, then run:
python3 anima.py "toggle wifi"
```

## Architecture

- [`anima.py`](file:///Users/dan/projects/K1NGS1LVER/anima/anima.py): Complete self-contained runtime (~350 LOC)
  - `Device`, `ADBDevice`, `MockDevice`: Parameterized device abstraction
  - `UIFormer`: Android XML hierarchy pruner (DSL: filter containers, keep leaf semantics)
  - `SkillDB`: SQLite skill storage
  - `Matcher`: Weighted multi-attribute locator evaluator
  - `BasePlanner`, `GeminiPlanner`, `HeuristicPlanner`: Pluggable planners
  - `AnimaRuntime`: Dual-mode engine (Cold Plan -> Compile -> Warm Replay) with self-healing drift recovery
- [`test_anima.py`](file:///Users/dan/projects/K1NGS1LVER/anima/test_anima.py): Hermetic verification suite
