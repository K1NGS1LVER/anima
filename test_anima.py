"""test_anima.py - Hermetic verification tests for Anima runtime.
Runs with pure python3 -m unittest test_anima.py (stdlib).
"""

import os
import tempfile
import unittest
from anima import (
    ADBDevice,
    AnimaRuntime,
    HeuristicPlanner,
    Locator,
    MockDevice,
    Skill,
    SkillDB,
    Step,
    UIFormer,
)

SAMPLE_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
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

DRIFTED_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
      <node index="0" class="android.widget.TextView" text="Connected devices" bounds="[72,150][600,230]" />
      <!-- Wi-Fi shifted down because a new card was inserted above -->
      <node index="1" class="android.widget.TextView" text="Network &amp; internet" bounds="[72,500][600,580]" />
      <node index="2" class="android.widget.Switch" resource-id="com.android.settings:id/switch_wifi_v2" content-desc="Wi-Fi" clickable="true" bounds="[880,480][1020,600]" />
    </node>
  </node>
</hierarchy>
"""

class TestAnima(unittest.TestCase):
    def test_uiformer_pruner(self):
        pruner = UIFormer()
        nodes = pruner.prune(SAMPLE_XML)

        # Containers pruned, 4 semantic nodes retained
        self.assertEqual(len(nodes), 4)
        self.assertEqual(nodes[0].text, "Network & internet")
        self.assertEqual(nodes[1].resource_id, "com.android.settings:id/switch_wifi")
        self.assertEqual(nodes[1].center, [950, 340])

        # Verify token reduction via compact Agent-DOM
        raw_len = len(SAMPLE_XML)
        compact_json = pruner.to_compact_json(nodes)
        pruned_len = len(compact_json)
        reduction = (1.0 - (pruned_len / raw_len)) * 100
        self.assertGreater(reduction, 60.0)

    def test_security_rejection(self):
        adb = ADBDevice()
        with self.assertRaises(ValueError):
            adb._run(["shell", "input; rm -rf /"])
        with self.assertRaises(ValueError):
            adb._run(["shell", "input && echo pwned"])

    def test_cold_to_warm_autonomous_loop(self):
        """Tests the full autonomous loop:
        1. Cold goal -> planned by heuristic/VLM -> action dispatched -> skill compiled into SQLite.
        2. Warm goal -> matched in SQLite -> replayed with 0 LLM calls in <0.01s!
        """
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "test_skills.db")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            device = MockDevice(SAMPLE_XML)

            # Cold Run
            cold_res = runtime.run("toggle bluetooth", device)
            self.assertTrue(cold_res.success)
            self.assertEqual(cold_res.mode, "COLD_COMPILED")
            self.assertEqual(cold_res.llm_calls, 1)
            # Switch center for bluetooth is (950, 490)
            self.assertEqual(device.history[-1], ("tap", (950, 490)))

            # Warm Run (Second time -> Zero LLM calls)
            warm_res = runtime.run("toggle bluetooth", device)
            self.assertTrue(warm_res.success)
            self.assertEqual(warm_res.mode, "WARM_REPLAY")
            self.assertEqual(warm_res.llm_calls, 0)
            self.assertEqual(device.history[-1], ("tap", (950, 490)))

    def test_self_healing_drift(self):
        """Tests that when UI drift occurs, the runtime detects it,
        re-grounds via planner, repairs the skill in SQLite, and succeeds.
        """
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "drift_skills.db")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            
            # Step 1: Learn skill on initial XML
            device1 = MockDevice(SAMPLE_XML)
            runtime.run("toggle wifi", device1)

            # Step 2: Screen drifts (new element inserted, wifi shifted down)
            device2 = MockDevice(DRIFTED_XML)
            drift_res = runtime.run("toggle wifi", device2)
            self.assertTrue(drift_res.success)
            # Center of new location [880,480][1020,600] is (950, 540)
            self.assertEqual(device2.history[-1], ("tap", (950, 540)))
            self.assertIn("Self-healed", drift_res.message)

if __name__ == "__main__":
    unittest.main()
