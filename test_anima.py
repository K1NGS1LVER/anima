"""test_anima.py - Comprehensive hermetic verification test suite for Anima.
Runs with pure python3 -m unittest test_anima.py (stdlib).
"""

import os
import tempfile
import unittest
from anima import (
    ADBDevice,
    AnimaRuntime,
    EssentialStateVerifier,
    HeuristicPlanner,
    LocalLiteRTPlanner,
    Locator,
    MockDevice,
    PopupInterceptor,
    PrunedNode,
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
      <node index="1" class="android.widget.Switch" resource-id="com.android.settings:id/switch_wifi" content-desc="Wi-Fi" clickable="true" checkable="true" checked="false" bounds="[880,280][1020,400]" />
      <node index="2" class="android.widget.TextView" text="Bluetooth" bounds="[72,450][600,530]" />
      <node index="3" class="android.widget.Switch" resource-id="com.android.settings:id/switch_bt" content-desc="Bluetooth" clickable="true" checkable="true" checked="false" bounds="[880,430][1020,550]" />
    </node>
  </node>
</hierarchy>
"""

DRIFTED_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
      <node index="0" class="android.widget.TextView" text="Connected devices" bounds="[72,150][600,230]" />
      <node index="1" class="android.widget.TextView" text="Network &amp; internet" bounds="[72,500][600,580]" />
      <node index="2" class="android.widget.Switch" resource-id="com.android.settings:id/switch_wifi_v2" content-desc="Wi-Fi" clickable="true" checkable="true" checked="false" bounds="[880,480][1020,600]" />
    </node>
  </node>
</hierarchy>
"""

POPUP_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.TextView" text="Allow Anima to access location?" bounds="[100,800][980,1000]" />
    <node index="1" class="android.widget.Button" text="Allow" clickable="true" bounds="[600,1100][900,1200]" />
    <node index="2" class="android.widget.Button" text="Don't allow" clickable="true" bounds="[200,1100][500,1200]" />
  </node>
</hierarchy>
"""

class TestAnima(unittest.TestCase):
    def test_uiformer_pruner(self):
        pruner = UIFormer()
        nodes = pruner.prune(SAMPLE_XML)

        self.assertEqual(len(nodes), 4)
        self.assertEqual(nodes[0].text, "Network & internet")
        self.assertEqual(nodes[1].resource_id, "com.android.settings:id/switch_wifi")
        self.assertEqual(nodes[1].center, [950, 340])

        raw_len = len(SAMPLE_XML)
        compact_json = pruner.to_compact_json(nodes)
        reduction = (1.0 - (len(compact_json) / raw_len)) * 100
        self.assertGreater(reduction, 60.0)

    def test_security_rejection(self):
        adb = ADBDevice()
        with self.assertRaises(ValueError):
            adb._run(["shell", "input; rm -rf /"])
        with self.assertRaises(ValueError):
            adb._run(["shell", "input && echo pwned"])

    def test_cold_to_warm_autonomous_loop(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "test_skills.db")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            device = MockDevice(SAMPLE_XML)

            # Cold Run
            cold_res = runtime.run("toggle bluetooth", device)
            self.assertTrue(cold_res.success)
            self.assertEqual(cold_res.mode, "COLD_COMPILED")
            self.assertEqual(cold_res.llm_calls, 1)
            self.assertEqual(device.history[-1], ("tap", (950, 490)))

            # Warm Run (Zero LLM calls)
            warm_res = runtime.run("toggle bluetooth", device)
            self.assertTrue(warm_res.success)
            self.assertEqual(warm_res.mode, "WARM_REPLAY")
            self.assertEqual(warm_res.llm_calls, 0)
            self.assertEqual(device.history[-1], ("tap", (950, 490)))

    def test_self_healing_drift(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "drift_skills.db")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            
            # Step 1: Learn on initial XML
            device1 = MockDevice(SAMPLE_XML)
            runtime.run("toggle wifi", device1)

            # Step 2: UI shifts
            device2 = MockDevice(DRIFTED_XML)
            drift_res = runtime.run("toggle wifi", device2)
            self.assertTrue(drift_res.success)
            self.assertEqual(device2.history[-1], ("tap", (950, 540)))
            self.assertIn("Self-healed", drift_res.message)

    def test_visual_fallback(self):
        """When accessibility XML is empty (canvas/game), visual fallback is triggered."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "vis_skills.db")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            # Device with empty XML hierarchy
            empty_device = MockDevice(xml="")

            res = runtime.run("tap play button", empty_device)
            self.assertTrue(res.success)
            self.assertEqual(res.mode, "VISUAL_FALLBACK")
            self.assertEqual(empty_device.history[-1], ("tap", (540, 960)))

    def test_popup_interception(self):
        """Verifies system popups/permission dialogs are intercepted and dismissed."""
        pruner = UIFormer()
        popup_nodes = pruner.prune(POPUP_XML)
        device = MockDevice(POPUP_XML)

        handled = PopupInterceptor.check_and_handle(popup_nodes, device, goal="toggle wifi")
        self.assertTrue(handled)
        # Center of "Allow" [600,1100][900,1200] is (750, 1150)
        self.assertEqual(device.history[-1], ("tap", (750, 1150)))

    def test_essential_state_progress(self):
        """Verifies A3 milestone verification detects state change."""
        pruner = UIFormer()
        initial_nodes = pruner.prune(SAMPLE_XML)
        wifi_node = initial_nodes[1]

        # Post-action nodes where checked state changed from false to true
        post_xml = SAMPLE_XML.replace('checked="false"', 'checked="true"')
        post_nodes = pruner.prune(post_xml)

        progress = EssentialStateVerifier.verify_progress(initial_nodes, post_nodes, wifi_node)
        self.assertTrue(progress)

    def test_export_import_skills(self):
        """Verifies skill library JSON export and import."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "skills.db")
            export_path = os.path.join(tmpdir, "exported.json")
            db = SkillDB(db_path)

            skill = Skill(
                intent="turn on hotspot",
                steps=[Step(action="tap", locator=Locator(resource_id="id/hotspot"))]
            )
            db.save(skill)

            # Export
            n = db.export_json(export_path)
            self.assertEqual(n, 1)

            # Import into clean DB
            new_db_path = os.path.join(tmpdir, "new_skills.db")
            new_db = SkillDB(new_db_path)
            imported = new_db.import_json(export_path)
            self.assertEqual(imported, 1)

            retrieved = new_db.get("turn on hotspot")
            self.assertIsNotNone(retrieved)
            self.assertEqual(retrieved.steps[0].locator.resource_id, "id/hotspot")

    def test_cross_resolution_skill_replay(self):
        """Verifies skills recorded on 1080p resolve and execute accurately on 1440p displays."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "res_skills.db")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())

            # 1. Device A: 1080x2400 phone
            dev_1080 = MockDevice(SAMPLE_XML, screen_size=(1080, 2400))
            cold_res = runtime.run("toggle bluetooth", dev_1080)
            self.assertTrue(cold_res.success)
            self.assertEqual(dev_1080.history[-1], ("tap", (950, 490)))

            # 2. Device B: 1440x3200 phone (scale = 1.333x)
            # Switch bounds: [880*1.333, 430*1.333][1020*1.333, 550*1.333] -> [1173, 573][1360, 733]
            xml_1440 = SAMPLE_XML.replace("[0,0][1080,2400]", "[0,0][1440,3200]") \
                                  .replace("[880,430][1020,550]", "[1173,573][1360,733]")
            dev_1440 = MockDevice(xml_1440, screen_size=(1440, 3200))

            warm_res = runtime.run("toggle bluetooth", dev_1440)
            self.assertTrue(warm_res.success)
            self.assertEqual(warm_res.mode, "WARM_REPLAY")
            self.assertEqual(warm_res.llm_calls, 0)
            # Center of [1173,573][1360,733] is (1266, 653)
            self.assertEqual(dev_1440.history[-1], ("tap", (1266, 653)))

    def test_parameter_slot_skill_replay(self):
        """Verifies dynamic slot extraction: 'set alarm for 07:30' compiles a template
        that replays 'set alarm for 09:15' injecting '09:15' with 0 LLM calls."""
        import re
        from anima import BasePlanner

        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "param_skills.db")

            class CustomInputPlanner(BasePlanner):
                def plan_step(self, goal, dom_json, nodes):
                    m = re.search(r"\b\d{1,2}:\d{2}\b", goal)
                    val = m.group(0) if m else "12:00"
                    return "input_text", nodes[0], val
                def plan_visual(self, goal, screenshot):
                    return None

            runtime = AnimaRuntime(db_path=db_path, planner=CustomInputPlanner())
            dev = MockDevice(SAMPLE_XML)

            # Cold run: compiles to "set alarm for {time}"
            cold_res = runtime.run("set alarm for 07:30", dev)
            self.assertTrue(cold_res.success)
            self.assertEqual(dev.history[-2], ("input_text", "07:30"))
            self.assertEqual(dev.history[-1], ("key", 4))  # IME soft keyboard dismissed

            # Warm run with novel time: "09:15"
            warm_res = runtime.run("set alarm for 09:15", dev)
            self.assertTrue(warm_res.success)
            self.assertEqual(warm_res.mode, "WARM_REPLAY")
            self.assertEqual(warm_res.llm_calls, 0)
            self.assertEqual(dev.history[-2], ("input_text", "09:15"))
            self.assertEqual(dev.history[-1], ("key", 4))

    def test_page_transition_graph(self):
        """Verifies Page Transition Graph (PTG) transition logging and HTML dashboard generation."""
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "ptg_skills.db")
            html_path = os.path.join(tmpdir, "dashboard.html")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            dev = MockDevice(SAMPLE_XML)

            # 1. Run cold goal
            runtime.run("toggle wifi", dev)
            # 2. Run warm replay
            runtime.run("toggle wifi", dev)

            # Assert transitions were recorded
            self.assertGreaterEqual(len(runtime.ptg.edges), 2)
            self.assertGreaterEqual(len(runtime.ptg.nodes), 1)

            # Export HTML dashboard
            out = runtime.export_ptg(html_path)
            self.assertEqual(out, html_path)
            self.assertTrue(os.path.exists(html_path))

            with open(html_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("⚡️ Anima Page Transition Graph (PTG)", content)
            self.assertIn("Token Savings vs Baseline", content)

    def test_biometric_hitl_guard(self):
        """Closes blind spot §7.D: biometric/session prompts halt automation for user auth."""
        from anima import BiometricGuard, ExecutionResult
        biometric_xml = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.TextView" resource-id="com.android.systemui:id/biometric_prompt" text="Unlock to continue" bounds="[100,800][980,1000]" />
  </node>
</hierarchy>
"""
        self.assertTrue(BiometricGuard.detect(biometric_xml))
        self.assertFalse(BiometricGuard.detect(SAMPLE_XML))

        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "bio_skills.db")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            dev = MockDevice(biometric_xml)

            res = runtime.run("toggle wifi", dev)
            self.assertFalse(res.success)
            self.assertEqual(res.mode, "HITL_PAUSED")
            self.assertEqual(res.llm_calls, 0)
            # No autonomous tap was dispatched onto the sensitive screen
            self.assertEqual(dev.history, [])

    def test_ptg_force_directed_dashboard(self):
        """Verifies the PTG dashboard ships an interactive D3.js force-directed graph."""
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "ptgfd.db")
            html_path = os.path.join(tmpdir, "ptg_fd.html")
            runtime = AnimaRuntime(db_path=db_path, planner=HeuristicPlanner())
            dev = MockDevice(SAMPLE_XML)

            runtime.run("toggle wifi", dev)
            runtime.run("toggle wifi", dev)
            runtime.export_ptg(html_path)

            with open(html_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("d3.v7.min.js", content)
            self.assertIn("forceSimulation", content)
            self.assertIn('id="graph"', content)

    def test_local_litert_planner(self):
        """Verifies on-device LocalLiteRTPlanner parsing and offline fallback compilation."""
        import http.server
        import json
        import threading

        # 1. Test with a mock local LiteRT model HTTP server
        class MockLiteRTHandler(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                content_length = int(self.headers.get("Content-Length", 0))
                self.rfile.read(content_length)
                response = {
                    "choices": [
                        {
                            "message": {
                                "content": json.dumps({"action": "tap", "target_index": 1, "value": None})
                            }
                        }
                    ]
                }
                body = json.dumps(response).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, format, *args):
                pass  # Silence HTTP server access logs in tests

        server = http.server.HTTPServer(("127.0.0.1", 0), MockLiteRTHandler)
        port = server.server_port
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()

        try:
            with tempfile.TemporaryDirectory() as tmpdir:
                db_path = os.path.join(tmpdir, "local_skills.db")
                planner = LocalLiteRTPlanner(endpoint_url=f"http://127.0.0.1:{port}/v1/chat/completions")
                runtime = AnimaRuntime(db_path=db_path, planner=planner)
                dev = MockDevice(SAMPLE_XML)

                # Cold execution via on-device mock server
                res_cold = runtime.run("toggle wifi", dev)
                self.assertTrue(res_cold.success)
                self.assertEqual(res_cold.mode, "COLD_COMPILED")
                self.assertEqual(res_cold.llm_calls, 1)

                # Warm replay with 0 LLM calls
                res_warm = runtime.run("toggle wifi", dev)
                self.assertTrue(res_warm.success)
                self.assertEqual(res_warm.mode, "WARM_REPLAY")
                self.assertEqual(res_warm.llm_calls, 0)
        finally:
            server.shutdown()
            server.server_close()

        # 2. Test fallback when local model server is completely offline
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "offline_skills.db")
            offline_planner = LocalLiteRTPlanner(endpoint_url="http://127.0.0.1:59999/v1/chat/completions", timeout=0.1)
            runtime = AnimaRuntime(db_path=db_path, planner=offline_planner)
            dev = MockDevice(SAMPLE_XML)

            # Cold run should not crash, but fallback to heuristic gracefully
            res_fallback = runtime.run("toggle wifi", dev)
            self.assertTrue(res_fallback.success)
            self.assertEqual(res_fallback.mode, "COLD_COMPILED")

if __name__ == "__main__":
    unittest.main()
