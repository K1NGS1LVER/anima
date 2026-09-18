package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cross-runtime guarantee.
 *
 * `skills_from_python.json` in test resources is not hand-written: it is the
 * literal output of `SkillDB.export_json()` from anima.py, produced by cold-
 * compiling "toggle wifi" against [SETTINGS_XML] below. If the Kotlin engine
 * ever drifts from the Python one -- a renamed JSON key, a different weight, a
 * changed rounding rule -- this test fails, which is the whole point.
 *
 * The expected scores are likewise taken from the Python Matcher, not derived
 * from the Kotlin implementation:
 *
 *     node 1 'TextView' score=0.0000000000
 *     node 2 'Switch'   score=1.0000000000
 */
class CrossRuntimeCompatibilityTest {

    private fun pythonExport(): String =
        javaClass.classLoader!!.getResourceAsStream("skills_from_python.json")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun aSkillCompiledByPythonLoadsIntoTheKotlinStore() {
        val store = MemorySkillStore()
        val imported = SkillsJsonIo.import(store, pythonExport())
        assertEquals("one skill imported from the Python export", 1, imported)

        val skill = store.get("toggle wifi")
        assertNotNull("intent key must survive the trip", skill)
        assertEquals(1, skill!!.steps.size)
        assertEquals(1, skill.successCount)
        assertEquals(0, skill.failureCount)

        val step = skill.steps[0]
        assertEquals("tap", step.action)
        assertNull(step.paramSlot)
        assertNull(step.value)

        // Every locator field Python wrote, read back under the Kotlin names.
        val loc = step.locator
        assertEquals("com.android.settings:id/switch_wifi", loc.resourceId)
        assertEquals("Wi-Fi", loc.contentDesc)
        assertNull(loc.text)
        assertEquals("Switch", loc.className)
        assertEquals(listOf(880, 280, 1020, 400), loc.bounds)
        assertEquals(listOf(0.8148, 0.1167, 0.9444, 0.1667), loc.relBounds)
        assertEquals(listOf(0.8796, 0.1417), loc.relCenter)
    }

    @Test
    fun thePythonLocatorScoresIdenticallyAgainstKotlinPrunedNodes() {
        val store = MemorySkillStore()
        SkillsJsonIo.import(store, pythonExport())
        val loc = store.get("toggle wifi")!!.steps[0].locator

        val nodes = UIFormer.prune(SETTINGS_XML, 1080 to 2400)
        assertEquals(2, nodes.size)

        val textView = nodes.first { it.className == "TextView" }
        val switch = nodes.first { it.className == "Switch" }

        // Reference values produced by anima.py's Matcher.score, to 10 places.
        assertEquals(0.0, Matcher.score(loc, textView), 1e-9)
        assertEquals(1.0, Matcher.score(loc, switch), 1e-9)

        // And the replay loop resolves to the same element Python tapped.
        assertEquals(switch, Matcher.findBest(loc, nodes))
        assertEquals(listOf(950, 340), Matcher.findBest(loc, nodes)!!.center)
    }

    @Test
    fun kotlinReExportIsReadableAsThePythonShape() {
        val store = MemorySkillStore()
        SkillsJsonIo.import(store, pythonExport())

        // Round-trip through the Kotlin writer and back: the skill must survive
        // unchanged, so a library edited on the phone still imports on a laptop.
        val reExported = SkillsJsonIo.export(store)
        val reImported = MemorySkillStore()
        assertEquals(1, SkillsJsonIo.import(reImported, reExported))

        val original = store.get("toggle wifi")!!
        val round = reImported.get("toggle wifi")!!
        assertEquals(original.intent, round.intent)
        assertEquals(original.successCount, round.successCount)
        assertEquals(original.steps[0].locator, round.steps[0].locator)

        // The Python importer keys off these exact names; guard them explicitly
        // rather than trusting the round trip to notice a rename on both sides.
        for (key in listOf(
            "\"intent\"", "\"steps\"", "\"success\"", "\"failure\"",
            "\"action\"", "\"locator\"", "\"param_slot\"", "\"value\"",
            "\"resource_id\"", "\"content_desc\"", "\"class_name\"",
            "\"rel_bounds\"", "\"rel_center\"", "\"bounds\"", "\"text\""
        )) {
            assert(reExported.contains(key)) { "missing Python-contract key $key" }
        }
    }

    private companion object {
        /** The exact screen the Python export above was compiled against. */
        const val SETTINGS_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" bounds="[0,0][1080,2400]">
      <node index="0" class="android.widget.TextView" text="Network &amp; internet" bounds="[72,300][600,380]" />
      <node index="1" class="android.widget.Switch" resource-id="com.android.settings:id/switch_wifi" content-desc="Wi-Fi" clickable="true" checkable="true" checked="false" bounds="[880,280][1020,400]" />
    </node>
  </node>
</hierarchy>
"""
    }
}
