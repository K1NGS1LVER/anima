"""anima.py - On-Device Autonomous Mobile GUI Agent Runtime & Skill Engine
Built with Ponytail principles: Stdlib-first, zero third-party dependencies, single-file core.

Features:
1. Safe ADB / Mock Device abstraction (parameterized execution, shell=False).
2. UIFormer Structural Pruning DSL (50-80% token reduction into compact Agent-DOM).
3. SkillDroid SQLite Skill Library & Weighted Multi-Attribute Locators.
4. Speculative Replay Engine (0 LLM calls, sub-second execution).
5. Autonomous Dual-Mode Runtime (Cold-start VLM planning -> Auto-compilation -> Warm replay).
6. Self-Healing Drift Recovery (re-grounds and repairs broken locators).
"""

import argparse
import json
import os
import re
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass, field
from difflib import SequenceMatcher
from typing import Any, Dict, List, Optional, Tuple

DANGEROUS_CHARS = re.compile(r"[;&|`$<>]")
BOUNDS_REGEX = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")

# ---------------------------------------------------------------------------
# 1. Device Abstraction (ADB + Mock)
# ---------------------------------------------------------------------------

class Device:
    """Base interface for device interactions."""
    def dump_xml(self) -> str:
        raise NotImplementedError
    def tap(self, x: int, y: int) -> None:
        raise NotImplementedError
    def input_text(self, text: str) -> None:
        raise NotImplementedError
    def key(self, keycode: int) -> None:
        raise NotImplementedError

class ADBDevice(Device):
    """Hardened ADB device controller using sanitized array arguments (shell=False)."""
    def __init__(self, serial: Optional[str] = None):
        self.cmd_prefix = ["adb"] + (["-s", serial] if serial else [])

    def _run(self, args: List[str]) -> str:
        # ponytail: prevent shell injection in 3 lines without a heavy security framework
        for arg in args:
            if DANGEROUS_CHARS.search(arg):
                raise ValueError(f"Security error: dangerous character in argument: {arg}")
        res = subprocess.run(self.cmd_prefix + args, capture_output=True, text=True, check=True)
        return res.stdout

    def dump_xml(self) -> str:
        # Direct dump to stdout avoids /sdcard disk writes
        return self._run(["exec-out", "uiautomator", "dump", "/dev/tty"])

    def tap(self, x: int, y: int) -> None:
        self._run(["shell", "input", "tap", str(x), str(y)])

    def input_text(self, text: str) -> None:
        safe_text = text.replace(" ", "%s")
        self._run(["shell", "input", "text", safe_text])

    def key(self, keycode: int) -> None:
        self._run(["shell", "input", "keyevent", str(keycode)])

class MockDevice(Device):
    """Hermetic in-memory mock device for fast deterministic unit testing."""
    def __init__(self, xml: str):
        self.xml = xml
        self.history: List[Tuple[str, Any]] = []

    def dump_xml(self) -> str:
        return self.xml

    def tap(self, x: int, y: int) -> None:
        self.history.append(("tap", (x, y)))

    def input_text(self, text: str) -> None:
        self.history.append(("input_text", text))

    def key(self, keycode: int) -> None:
        self.history.append(("key", keycode))

# ---------------------------------------------------------------------------
# 2. UIFormer Structural Pruning Engine (DSL)
# ---------------------------------------------------------------------------

@dataclass
class PrunedNode:
    id: int
    class_name: str
    resource_id: Optional[str]
    text: Optional[str]
    content_desc: Optional[str]
    clickable: bool
    bounds: List[int]
    center: List[int]

class UIFormer:
    """Prunes Android accessibility trees by dropping non-interactive structural containers."""
    CONTAINERS = {
        "android.widget.FrameLayout",
        "android.widget.LinearLayout",
        "android.view.ViewGroup",
        "android.widget.RelativeLayout",
        "androidx.recyclerview.widget.RecyclerView",
    }

    @staticmethod
    def parse_bounds(b_str: str) -> Tuple[List[int], List[int]]:
        m = BOUNDS_REGEX.match(b_str or "")
        if m:
            l, t, r, b = map(int, m.groups())
            return [l, t, r, b], [(l + r) // 2, (t + b) // 2]
        return [0, 0, 0, 0], [0, 0]

    def to_compact_json(self, nodes: List[PrunedNode]) -> str:
        """Serializes pruned nodes into a compact JSON Agent-DOM for minimal prompt token payload."""
        compact = []
        for n in nodes:
            item: Dict[str, Any] = {"id": n.id, "cls": n.class_name}
            if n.resource_id:
                item["res_id"] = n.resource_id.split("/")[-1] if "/" in n.resource_id else n.resource_id
            if n.text:
                item["text"] = n.text
            if n.content_desc:
                item["desc"] = n.content_desc
            if n.clickable:
                item["click"] = True
            item["center"] = n.center
            compact.append(item)
        return json.dumps(compact, separators=(",", ":"))

    def prune(self, raw_xml: str) -> List[PrunedNode]:
        root = ET.fromstring(raw_xml)
        nodes: List[PrunedNode] = []
        counter = 1

        def walk(node: ET.Element):
            nonlocal counter
            a = node.attrib
            cls = a.get("class", "")
            clickable = a.get("clickable", "false") == "true"
            text = (a.get("text") or "").strip() or None
            desc = (a.get("content-desc") or "").strip() or None
            res_id = (a.get("resource-id") or "").strip() or None

            has_semantics = bool(text or desc or clickable or a.get("checkable") == "true")
            is_container = cls in self.CONTAINERS

            if has_semantics and (not is_container or clickable):
                bounds, center = self.parse_bounds(a.get("bounds", ""))
                if bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                    nodes.append(PrunedNode(
                        id=counter,
                        class_name=cls.split(".")[-1],
                        resource_id=res_id,
                        text=text,
                        content_desc=desc,
                        clickable=clickable,
                        bounds=bounds,
                        center=center
                    ))
                    counter += 1

            for child in node:
                walk(child)

        walk(root)
        return nodes

# ---------------------------------------------------------------------------
# 3. Skill Schema & Weighted Multi-Attribute Matcher (SkillDroid)
# ---------------------------------------------------------------------------

@dataclass
class Locator:
    resource_id: Optional[str] = None
    content_desc: Optional[str] = None
    text: Optional[str] = None
    class_name: Optional[str] = None
    bounds: Optional[List[int]] = None

    @classmethod
    def from_node(cls, node: PrunedNode) -> "Locator":
        return cls(
            resource_id=node.resource_id,
            content_desc=node.content_desc,
            text=node.text,
            class_name=node.class_name,
            bounds=node.bounds
        )

@dataclass
class Step:
    action: str  # "tap", "input_text", "key"
    locator: Locator
    param_slot: Optional[str] = None
    value: Optional[str] = None

@dataclass
class Skill:
    intent: str
    steps: List[Step]
    success_count: int = 0
    failure_count: int = 0

class Matcher:
    """Computes weighted multi-attribute score (SkillDroid weights)."""
    WEIGHTS = {"res": 0.40, "desc": 0.25, "text": 0.20, "cls": 0.10, "bounds": 0.05}

    @classmethod
    def score(cls, loc: Locator, node: PrunedNode) -> float:
        score = 0.0
        if loc.resource_id and loc.resource_id == node.resource_id:
            score += cls.WEIGHTS["res"]
        if loc.content_desc and node.content_desc:
            score += SequenceMatcher(None, loc.content_desc.lower(), node.content_desc.lower()).ratio() * cls.WEIGHTS["desc"]
        if loc.text and node.text:
            score += SequenceMatcher(None, loc.text.lower(), node.text.lower()).ratio() * cls.WEIGHTS["text"]
        if loc.class_name and loc.class_name.lower() in node.class_name.lower():
            score += cls.WEIGHTS["cls"]
        if loc.bounds and loc.bounds == node.bounds:
            score += cls.WEIGHTS["bounds"]
        return score

    @classmethod
    def find_best(cls, loc: Locator, nodes: List[PrunedNode], threshold: float = 0.50) -> Optional[PrunedNode]:
        best = None
        best_score = 0.0
        for n in nodes:
            s = cls.score(loc, n)
            if s > best_score:
                best_score, best = s, n
        return best if best_score >= threshold else None

# ---------------------------------------------------------------------------
# 4. SQLite Skill Database
# ---------------------------------------------------------------------------

class SkillDB:
    """Minimal SQLite storage for compiled skills."""
    def __init__(self, db_path: str = "skills.db"):
        self.conn = sqlite3.connect(db_path)
        self._init_db()

    def _init_db(self):
        with self.conn:
            self.conn.execute("""
                CREATE TABLE IF NOT EXISTS skills (
                    intent TEXT PRIMARY KEY,
                    steps_json TEXT NOT NULL,
                    success_count INTEGER DEFAULT 0,
                    failure_count INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)

    def save(self, skill: Skill):
        steps_dict = [
            {"action": s.action, "locator": asdict(s.locator), "param_slot": s.param_slot, "value": s.value}
            for s in skill.steps
        ]
        with self.conn:
            self.conn.execute(
                """INSERT OR REPLACE INTO skills 
                   (intent, steps_json, success_count, failure_count) VALUES (?, ?, ?, ?)""",
                (skill.intent.strip().lower(), json.dumps(steps_dict), skill.success_count, skill.failure_count)
            )

    def get(self, intent: str) -> Optional[Skill]:
        cur = self.conn.cursor()
        cur.execute("SELECT steps_json, success_count, failure_count FROM skills WHERE intent = ?", (intent.strip().lower(),))
        row = cur.fetchone()
        if not row:
            return None
        raw_steps, succ, fail = row
        steps = [
            Step(
                action=s["action"],
                locator=Locator(**s["locator"]),
                param_slot=s.get("param_slot"),
                value=s.get("value")
            )
            for s in json.loads(raw_steps)
        ]
        return Skill(intent=intent, steps=steps, success_count=succ, failure_count=fail)

# ---------------------------------------------------------------------------
# 5. Cold-Start Planners (Gemini 2.5 Flash REST + Heuristic Fallback)
# ---------------------------------------------------------------------------

class BasePlanner:
    def plan_step(self, goal: str, dom_json: str, nodes: List[PrunedNode]) -> Optional[Tuple[str, PrunedNode, Optional[str]]]:
        """Returns: (action, target_node, value)"""
        raise NotImplementedError

class GeminiPlanner(BasePlanner):
    """High-speed VLM planner calling Gemini 2.5 Flash via REST API (pure stdlib urllib)."""
    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.getenv("GEMINI_API_KEY")

    def plan_step(self, goal: str, dom_json: str, nodes: List[PrunedNode]) -> Optional[Tuple[str, PrunedNode, Optional[str]]]:
        if not self.api_key:
            return None

        prompt = (
            f"User Goal: {goal}\n"
            f"Current Screen UI Nodes (Agent-DOM JSON):\n{dom_json}\n\n"
            f"Select the next action to accomplish the goal. Return ONLY a JSON object:\n"
            f'{{"action": "tap"|"input_text"|"key", "target_id": <int id from list>, "value": <optional string>}}'
        )
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={self.api_key}"
        payload = json.dumps({"contents": [{"parts": [{"text": prompt}]}]}).encode("utf-8")
        req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})

        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                text = data["candidates"][0]["content"]["parts"][0]["text"]
                clean_json = re.search(r"\{.*\}", text, re.DOTALL)
                if clean_json:
                    parsed = json.loads(clean_json.group(0))
                    target_id = parsed.get("target_id")
                    action = parsed.get("action", "tap")
                    val = parsed.get("value")
                    node = next((n for n in nodes if n.id == target_id), None)
                    if node:
                        return action, node, val
        except Exception as e:
            print(f"Gemini API error, falling back to heuristic: {e}", file=sys.stderr)
        return None

class HeuristicPlanner(BasePlanner):
    """Hermetic keyword/semantic matcher on Agent-DOM when offline or without API key."""
    def plan_step(self, goal: str, dom_json: str, nodes: List[PrunedNode]) -> Optional[Tuple[str, PrunedNode, Optional[str]]]:
        tokens = [t.lower() for t in re.findall(r"\w+", goal)]
        best_node = None
        best_score = 0

        for n in nodes:
            score = 0
            candidate_text = f"{n.text or ''} {n.content_desc or ''} {n.resource_id or ''}".lower()
            for t in tokens:
                if t in candidate_text:
                    score += 2
            if n.clickable and score > 0:
                score += 1
            if score > best_score:
                best_score = score
                best_node = n

        if best_node:
            return "tap", best_node, None
        return None

class HybridPlanner(BasePlanner):
    """Tries Gemini REST API first; seamlessly falls back to Heuristic planner."""
    def __init__(self, api_key: Optional[str] = None):
        self.gemini = GeminiPlanner(api_key)
        self.heuristic = HeuristicPlanner()

    def plan_step(self, goal: str, dom_json: str, nodes: List[PrunedNode]) -> Optional[Tuple[str, PrunedNode, Optional[str]]]:
        decision = self.gemini.plan_step(goal, dom_json, nodes)
        if decision:
            return decision
        return self.heuristic.plan_step(goal, dom_json, nodes)

# ---------------------------------------------------------------------------
# 6. Autonomous Dual-Mode Runtime (Cold Plan + Warm Replay + Self-Healing)
# ---------------------------------------------------------------------------

@dataclass
class ExecutionResult:
    mode: str  # "WARM_REPLAY" (0 LLM) or "COLD_COMPILED"
    success: bool
    steps_executed: int
    llm_calls: int
    latency_seconds: float
    message: str

class AnimaRuntime:
    """The central orchestrator: Intent -> Warm Replay OR Cold Planning -> Auto-Compilation."""
    def __init__(self, db_path: str = "skills.db", planner: Optional[BasePlanner] = None):
        self.db = SkillDB(db_path)
        self.pruner = UIFormer()
        self.planner = planner or HybridPlanner()

    def run(self, goal: str, device: Device, params: Optional[Dict[str, str]] = None) -> ExecutionResult:
        start_time = time.time()
        skill = self.db.get(goal)

        # -------------------------------------------------------------------
        # 1. Warm Path: Speculative Replay with Zero LLM Calls
        # -------------------------------------------------------------------
        if skill:
            executed = 0
            drift_detected = False
            for idx, step in enumerate(skill.steps):
                xml = device.dump_xml()
                nodes = self.pruner.prune(xml)
                target = Matcher.find_best(step.locator, nodes)

                if not target:
                    # Self-healing trigger: UI drifted, re-ground step via planner
                    drift_detected = True
                    dom_json = self.pruner.to_compact_json(nodes)
                    recovered = self.planner.plan_step(goal, dom_json, nodes)
                    if recovered:
                        action, target, val = recovered
                        # Update stored locator to repair the skill
                        step.locator = Locator.from_node(target)
                        self.db.save(skill)
                    else:
                        return ExecutionResult(
                            mode="WARM_REPLAY",
                            success=False,
                            steps_executed=executed,
                            llm_calls=1,
                            latency_seconds=time.time() - start_time,
                            message=f"Locator drift at step {idx}; recovery failed"
                        )

                if step.action == "tap":
                    device.tap(target.center[0], target.center[1])
                elif step.action == "input_text":
                    val = (params or {}).get(step.param_slot or "", step.value or "")
                    device.input_text(val)
                elif step.action == "key":
                    device.key(int(step.value or 4))
                executed += 1

            skill.success_count += 1
            self.db.save(skill)
            return ExecutionResult(
                mode="WARM_REPLAY",
                success=True,
                steps_executed=executed,
                llm_calls=1 if drift_detected else 0,
                latency_seconds=time.time() - start_time,
                message="Speculative replay succeeded with 0 LLM calls" if not drift_detected else "Self-healed drifted locator and succeeded"
            )

        # -------------------------------------------------------------------
        # 2. Cold Path: VLM Planning & Automatic Skill Compilation
        # -------------------------------------------------------------------
        xml = device.dump_xml()
        nodes = self.pruner.prune(xml)
        dom_json = self.pruner.to_compact_json(nodes)

        decision = self.planner.plan_step(goal, dom_json, nodes)
        if not decision:
            return ExecutionResult(
                mode="COLD_COMPILED",
                success=False,
                steps_executed=0,
                llm_calls=1,
                latency_seconds=time.time() - start_time,
                message=f"Could not plan action for goal: {goal}"
            )

        action, target_node, val = decision
        if action == "tap":
            device.tap(target_node.center[0], target_node.center[1])
        elif action == "input_text":
            device.input_text(val or "")
        elif action == "key":
            device.key(int(val or 4))

        # Auto-compile trajectory into SQLite skill for future 0-LLM reuse
        new_skill = Skill(
            intent=goal,
            steps=[
                Step(
                    action=action,
                    locator=Locator.from_node(target_node),
                    value=val
                )
            ],
            success_count=1
        )
        self.db.save(new_skill)

        return ExecutionResult(
            mode="COLD_COMPILED",
            success=True,
            steps_executed=1,
            llm_calls=1,
            latency_seconds=time.time() - start_time,
            message="Cold-start planned and compiled into SQLite skill"
        )

# ---------------------------------------------------------------------------
# 7. Interactive CLI Runner
# ---------------------------------------------------------------------------

SAMPLE_CLI_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
      <node index="0" class="android.widget.TextView" text="Network &amp; internet" bounds="[72,300][600,380]" />
      <node index="1" class="android.widget.Switch" resource-id="com.android.settings:id/switch_wifi" content-desc="Wi-Fi" clickable="true" bounds="[880,280][1020,400]" />
      <node index="2" class="android.widget.TextView" text="Bluetooth" bounds="[72,450][600,530]" />
      <node index="3" class="android.widget.Switch" resource-id="com.android.settings:id/switch_bt" content-desc="Bluetooth" clickable="true" bounds="[880,430][1020,550]" />
    </node>
  </node>
</hierarchy>
"""

def main():
    parser = argparse.ArgumentParser(description="Anima Mobile Agent Runtime")
    parser.add_argument("goal", nargs="?", default="toggle wifi", help="Goal to execute (e.g. 'toggle wifi')")
    parser.add_argument("--mock", action="store_true", help="Use hermetic mock device with sample XML")
    parser.add_argument("--db", default="skills.db", help="Path to SQLite skills database")
    args = parser.parse_args()

    device = MockDevice(SAMPLE_CLI_XML) if args.mock else ADBDevice()
    runtime = AnimaRuntime(db_path=args.db)

    print(f"\n[Goal] '{args.goal}' on {'MockDevice' if args.mock else 'ADBDevice'}")
    res = runtime.run(args.goal, device)
    print(f"Mode:            {res.mode}")
    print(f"Success:         {res.success}")
    print(f"LLM Calls:       {res.llm_calls}")
    print(f"Latency:         {res.latency_seconds:.4f}s")
    print(f"Status:          {res.message}\n")

if __name__ == "__main__":
    main()
