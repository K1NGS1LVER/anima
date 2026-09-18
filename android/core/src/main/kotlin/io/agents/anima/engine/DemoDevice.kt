package io.agents.anima.engine

/**
 * Hermetic in-memory device: the Kotlin twin of `DemoDevice` in `anima.py`.
 *
 * A Wi-Fi settings screen with an injectable permission popup and an injectable layout drift.
 * No `android.*` dependency, no target app, no network — the 3-act demo therefore cannot flake.
 */
class DemoDevice(private val drift: Boolean = false) : AgentDevice {

    var wifiChecked: Boolean = false
    var hasPopup: Boolean = false

    /** Every tap the demo dispatched, for assertions and on-screen narration. */
    val taps: MutableList<Pair<Int, Int>> = ArrayList()

    override fun rawDump(): String = currentXml()

    override fun captureNodes(): List<PrunedNode> = UIFormer.prune(currentXml(), screenSize())

    override fun screenSize(): Pair<Int, Int> = Pair(SCREEN_W, SCREEN_H)

    override fun tap(x: Int, y: Int) {
        taps.add(Pair(x, y))

        if (hasPopup) {
            if (x in 200..880 && y in 900..1050) hasPopup = false
            return
        }
        // Original hit-box, plus the drifted one so the chaos act genuinely flips the toggle.
        val hitOriginal = x in 850..1000 && y in 220..340
        val hitDrifted = x in 700..860 && y in 500..620
        if (hitOriginal || hitDrifted) wifiChecked = !wifiChecked
    }

    override fun inputText(text: String) {
        // No text fields on the demo screen.
    }

    override fun key(keycode: Int) {
        // No-op: the demo screen has no IME and no navigation stack.
    }

    private fun currentXml(): String {
        if (hasPopup) return POPUP_XML
        val xml = if (drift) DRIFT_XML else WIFI_XML
        return if (wifiChecked) xml.replace("checked=\"false\"", "checked=\"true\"") else xml
    }

    companion object {
        const val SCREEN_W = 1080
        const val SCREEN_H = 2400

        val WIFI_XML: String = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
                <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
                  <node index="0" class="android.widget.TextView" text="Network &amp; internet" bounds="[72,300][600,380]" />
                  <node index="1" class="android.widget.Switch" resource-id="com.android.settings:id/switch_wifi" content-desc="Wi-Fi" clickable="true" checkable="true" checked="false" bounds="[850,220][1000,340]" />
                </node>
              </node>
            </hierarchy>
        """.trimIndent()

        /** The same screen after a layout update: resource-id shifted, bounds moved, desc kept. */
        val DRIFT_XML: String = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
                <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
                  <node index="0" class="android.widget.TextView" text="Network &amp; internet" bounds="[72,300][600,380]" />
                  <node index="1" class="android.widget.Switch" resource-id="com.android.settings:id/switch_wifi_v2" content-desc="Wi-Fi" clickable="true" checkable="true" checked="false" bounds="[700,500][860,620]" />
                </node>
              </node>
            </hierarchy>
        """.trimIndent()

        val POPUP_XML: String = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
                <node index="0" class="android.widget.LinearLayout" bounds="[100,600][980,1400]">
                  <node index="0" class="android.widget.TextView" text="Allow Anima to access location?" bounds="[150,700][930,850]" />
                  <node index="1" class="android.widget.Button" resource-id="com.android.permissioncontroller:id/permission_allow_button" text="While using the app" clickable="true" bounds="[200,900][880,1050]" />
                </node>
              </node>
            </hierarchy>
        """.trimIndent()
    }
}

/**
 * The staged 3-act hackathon demo, driven entirely on-device.
 *
 *   Act 1  cold compile   — heuristic planner grounds the goal and compiles a skill
 *   Act 2  warm replay    — same goal, 0 planner calls, sub-millisecond
 *   Act 3  chaos          — permission popup + layout drift, self-heals and repairs the skill
 */
object DemoScript {

    const val ACT_COLD = "ACT 1 | COLD COMPILATION"
    const val ACT_WARM = "ACT 2 | WARM 0-LLM SPECULATIVE REPLAY"
    const val ACT_CHAOS = "ACT 3 | SELF-HEALING CHAOS TEST"

    const val DEMO_GOAL = "toggle wifi"

    fun run(
        planner: Planner = HeuristicPlanner(),
        repo: SkillRepository = MemorySkillStore(),
        onAct: (actName: String, result: ExecutionResult) -> Unit,
    ): List<ExecutionResult> {
        val results = ArrayList<ExecutionResult>(3)

        val cold = AnimaRuntime.execute(repo, planner, DEMO_GOAL, DemoDevice())
        results.add(cold)
        onAct(ACT_COLD, cold)

        val warm = AnimaRuntime.execute(repo, planner, DEMO_GOAL, DemoDevice())
        results.add(warm)
        onAct(ACT_WARM, warm)

        val chaosDevice = DemoDevice(drift = true)
        chaosDevice.hasPopup = true
        val chaos = AnimaRuntime.execute(repo, planner, DEMO_GOAL, chaosDevice)
        results.add(chaos)
        onAct(ACT_CHAOS, chaos)

        return results
    }
}
