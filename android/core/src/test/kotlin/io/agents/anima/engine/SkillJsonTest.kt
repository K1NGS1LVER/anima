package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cross-runtime compatibility guarantee.
 *
 * The serialized keys must stay snake_case and in the field order `anima.py` writes, so a
 * `skills.json` moves between the Python runtime and the phone in either direction untouched.
 */
class SkillJsonTest {

    private val step = Step(
        action = "input_text",
        locator = Locator(
            resourceId = "com.android.settings:id/search",
            contentDesc = null,
            text = "Search settings",
            className = "EditText",
            bounds = listOf(10, 20, 30, 40),
            relBounds = listOf(0.1, 0.2, 0.3, 0.4),
            relCenter = listOf(0.2, 0.3),
        ),
        paramSlot = "time",
        value = "7:30 am",
    )

    private val literalJson = "[{\"action\":\"input_text\"," +
        "\"locator\":{\"resource_id\":\"com.android.settings:id/search\"," +
        "\"content_desc\":null," +
        "\"text\":\"Search settings\"," +
        "\"class_name\":\"EditText\"," +
        "\"bounds\":[10,20,30,40]," +
        "\"rel_bounds\":[0.1,0.2,0.3,0.4]," +
        "\"rel_center\":[0.2,0.3]}," +
        "\"param_slot\":\"time\"," +
        "\"value\":\"7:30 am\"}]"

    @Test
    fun serializesToTheSnakeCaseContract() {
        assertEquals(literalJson, SkillJson.stepsToJson(listOf(step)))
    }

    @Test
    fun deserializesTheSnakeCaseContract() {
        assertEquals(listOf(step), SkillJson.stepsFromJson(literalJson))
    }

    @Test
    fun roundTripsUnchanged() {
        val once = SkillJson.stepsToJson(listOf(step))
        val twice = SkillJson.stepsToJson(SkillJson.stepsFromJson(once))
        assertEquals(once, twice)
    }

    @Test
    fun nullLocatorFieldsSurviveTheRoundTrip() {
        val sparse = Step(action = "tap", locator = Locator(contentDesc = "Wi-Fi"))
        val json = SkillJson.stepsToJson(listOf(sparse))
        assertTrue(json.contains("\"resource_id\":null"))
        assertTrue(json.contains("\"bounds\":null"))
        assertTrue(json.contains("\"param_slot\":null"))

        val parsed = SkillJson.stepsFromJson(json)
        assertEquals(1, parsed.size)
        assertEquals("Wi-Fi", parsed[0].locator.contentDesc)
        assertNull(parsed[0].locator.resourceId)
        assertNull(parsed[0].locator.bounds)
        assertNull(parsed[0].paramSlot)
    }

    @Test
    fun quotesAndBackslashesAreEscaped() {
        val tricky = Step(action = "input_text", locator = Locator(text = "say \"hi\"\\"), value = "a\nb")
        val json = SkillJson.stepsToJson(listOf(tricky))
        val parsed = SkillJson.stepsFromJson(json)
        assertEquals("say \"hi\"\\", parsed[0].locator.text)
        assertEquals("a\nb", parsed[0].value)
    }

    @Test
    fun malformedJsonYieldsNoStepsInsteadOfThrowing() {
        assertTrue(SkillJson.stepsFromJson("not json at all").isEmpty())
        assertTrue(SkillJson.stepsFromJson(null).isEmpty())
        assertTrue(SkillJson.stepsFromJson("").isEmpty())
    }

    @Test
    fun skillLibraryExportMatchesThePythonExportShape() {
        val store = MemorySkillStore()
        store.save(Skill("toggle wifi", listOf(Step("tap", Locator(resourceId = "sw"))), successCount = 2))

        val exported = SkillsJsonIo.export(store)
        assertTrue(exported.startsWith("[{\"intent\":\"toggle wifi\",\"steps\":["))
        assertTrue(exported.endsWith("\"success\":2,\"failure\":0}]"))

        val reimported = MemorySkillStore()
        assertEquals(1, SkillsJsonIo.import(reimported, exported))
        assertEquals(2, reimported.get("toggle wifi")!!.successCount)
        assertEquals("sw", reimported.get("toggle wifi")!!.steps[0].locator.resourceId)
    }
}
