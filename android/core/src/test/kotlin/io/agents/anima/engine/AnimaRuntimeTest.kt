package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end control flow on the hermetic [DemoDevice]: cold compile, warm 0-call replay,
 * self-healing under drift + popup, and the HITL biometric guard.
 */
class AnimaRuntimeTest {

    private val planner = HeuristicPlanner()

    @Test
    fun coldRunPlansExecutesAndCompilesASkill() {
        val store = MemorySkillStore()
        val device = DemoDevice()

        val result = AnimaRuntime.execute(store, planner, "toggle wifi", device)

        assertEquals(AnimaRuntime.MODE_COLD, result.mode)
        assertTrue(result.success)
        assertEquals(1, result.stepsExecuted)
        assertTrue(device.wifiChecked)

        val compiled = store.get("toggle wifi")
        assertNotNull(compiled)
        assertEquals(1, compiled!!.steps.size)
        assertEquals("tap", compiled.steps[0].action)
        assertEquals("com.android.settings:id/switch_wifi", compiled.steps[0].locator.resourceId)
        assertEquals(1, compiled.successCount)
    }

    @Test
    fun warmReplayMakesZeroPlannerCalls() {
        val store = MemorySkillStore()
        AnimaRuntime.execute(store, planner, "toggle wifi", DemoDevice())

        val device = DemoDevice()
        val result = AnimaRuntime.execute(store, planner, "toggle wifi", device)

        assertEquals(AnimaRuntime.MODE_WARM, result.mode)
        assertTrue(result.success)
        assertEquals(0, result.llmCalls)
        assertTrue(device.wifiChecked)
        assertEquals(2, store.get("toggle wifi")!!.successCount)
    }

    @Test
    fun chaosRunDismissesThePopupAndHealsTheDriftedLocator() {
        val store = MemorySkillStore()
        AnimaRuntime.execute(store, planner, "toggle wifi", DemoDevice())

        val device = DemoDevice(drift = true)
        device.hasPopup = true
        val result = AnimaRuntime.execute(store, planner, "toggle wifi", device)

        assertEquals(AnimaRuntime.MODE_WARM, result.mode)
        assertTrue(result.success)
        assertEquals(1, result.llmCalls)
        assertTrue(result.message.contains("Self-healed"))
        assertFalse(device.hasPopup)
        assertTrue(device.wifiChecked)

        // The repaired locator was persisted in place, mid-replay.
        assertEquals(
            "com.android.settings:id/switch_wifi_v2",
            store.get("toggle wifi")!!.steps[0].locator.resourceId,
        )
    }

    @Test
    fun threeActDemoRunsColdThenWarmThenSelfHeal() {
        val seen = ArrayList<String>()
        val results = DemoScript.run(planner = planner) { act, _ -> seen.add(act) }

        assertEquals(3, results.size)
        assertEquals(listOf(DemoScript.ACT_COLD, DemoScript.ACT_WARM, DemoScript.ACT_CHAOS), seen)

        assertEquals(AnimaRuntime.MODE_COLD, results[0].mode)

        assertEquals(AnimaRuntime.MODE_WARM, results[1].mode)
        assertEquals(0, results[1].llmCalls)
        assertTrue(results[1].success)

        assertEquals(AnimaRuntime.MODE_WARM, results[2].mode)
        assertTrue(results[2].success)
        assertTrue(results[2].message.contains("Self-healed"))
        assertTrue(results.all { it.latencyMs >= 0 })
    }

    @Test
    fun biometricPromptPausesForHumanAuthentication() {
        val store = MemorySkillStore()
        AnimaRuntime.execute(store, planner, "toggle wifi", DemoDevice())

        val result = AnimaRuntime.execute(store, planner, "toggle wifi", BiometricDevice())

        assertEquals(AnimaRuntime.MODE_HITL, result.mode)
        assertFalse(result.success)
        assertEquals(0, result.stepsExecuted)
        assertEquals(0, result.llmCalls)
    }

    @Test
    fun coldRunOnAnEmptyScreenFailsCleanly() {
        val result = AnimaRuntime.execute(MemorySkillStore(), planner, "toggle wifi", EmptyDevice())

        assertEquals(AnimaRuntime.MODE_FAILED, result.mode)
        assertFalse(result.success)
        assertEquals(0, result.stepsExecuted)
    }

    @Test
    fun parameterizedSkillTypesTheExtractedSlotValue() {
        val store = MemorySkillStore()
        store.save(
            Skill(
                "set alarm for {time}",
                listOf(
                    Step(
                        action = "input_text",
                        locator = Locator(contentDesc = "Wi-Fi"),
                        paramSlot = "time",
                        value = "8:00 am",
                    )
                ),
            )
        )
        val device = RecordingDemoDevice()

        val result = AnimaRuntime.execute(store, planner, "set alarm for 7:30 am", device)

        assertTrue(result.success)
        assertEquals(listOf("7:30 am"), device.typed)
        // The IME is always dismissed after typing.
        assertEquals(listOf(AnimaRuntime.KEYCODE_BACK), device.keys)
    }

    // ------------------------------------------------------------- test doubles

    /** A screen showing a system biometric prompt. */
    private class BiometricDevice : AgentDevice {
        override fun rawDump(): String =
            "com.android.systemui\nandroid.widget.FrameLayout com.android.systemui:id/biometric_prompt\n"

        override fun captureNodes(): List<PrunedNode> = emptyList()
        override fun screenSize(): Pair<Int, Int> = Pair(1080, 2400)
        override fun tap(x: Int, y: Int) = Unit
        override fun inputText(text: String) = Unit
        override fun key(keycode: Int) = Unit
    }

    /** A screen with nothing actionable on it. */
    private class EmptyDevice : AgentDevice {
        override fun rawDump(): String = ""
        override fun captureNodes(): List<PrunedNode> = emptyList()
        override fun screenSize(): Pair<Int, Int> = Pair(1080, 2400)
        override fun tap(x: Int, y: Int) = Unit
        override fun inputText(text: String) = Unit
        override fun key(keycode: Int) = Unit
    }

    /** The Wi-Fi demo screen, recording typed text and keycodes. */
    private class RecordingDemoDevice : AgentDevice {
        val typed = ArrayList<String>()
        val keys = ArrayList<Int>()
        private val inner = DemoDevice()

        override fun rawDump(): String = inner.rawDump()
        override fun captureNodes(): List<PrunedNode> = inner.captureNodes()
        override fun screenSize(): Pair<Int, Int> = inner.screenSize()
        override fun tap(x: Int, y: Int) = inner.tap(x, y)
        override fun inputText(text: String) {
            typed.add(text)
        }

        override fun key(keycode: Int) {
            keys.add(keycode)
        }
    }
}
