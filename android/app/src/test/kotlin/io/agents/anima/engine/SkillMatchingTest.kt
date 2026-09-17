package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Template and skill-lookup behaviour. Exercised through [MemorySkillStore], which is the
 * hermetic twin of the SQLite [SkillStore] (same serialized rows, same lookup order).
 */
class SkillMatchingTest {

    private fun tapStep(res: String = "com.android.deskclock:id/fab") =
        Step(action = "tap", locator = Locator(resourceId = res, className = "Button"))

    @Test
    fun templateIsRecognizedAndSlotIsExtracted() {
        val store = MemorySkillStore()
        store.save(Skill("set alarm for {time}", listOf(tapStep())))

        val (skill, params) = store.findMatch("Set alarm for 7:30 am")

        assertNotNull(skill)
        assertEquals("set alarm for {time}", skill!!.intent)
        assertEquals(mapOf("time" to "7:30 am"), params)
    }

    @Test
    fun exactMatchWinsWithNoParameters() {
        val store = MemorySkillStore()
        store.save(Skill("toggle wifi", listOf(tapStep()), successCount = 3))

        val (skill, params) = store.findMatch("  Toggle wifi  ")

        assertNotNull(skill)
        assertEquals("toggle wifi", skill!!.intent)
        assertEquals(3, skill.successCount)
        assertTrue(params.isEmpty())
    }

    @Test
    fun noMatchReturnsNullAndEmptyParams() {
        val store = MemorySkillStore()
        store.save(Skill("set alarm for {time}", listOf(tapStep())))

        val (skill, params) = store.findMatch("order a pizza")

        assertNull(skill)
        assertTrue(params.isEmpty())
    }

    @Test
    fun intentKeysAreTrimmedAndLowercased() {
        val store = MemorySkillStore()
        store.save(Skill("  Toggle WIFI ", listOf(tapStep())))

        assertNotNull(store.get("toggle wifi"))
        assertNotNull(store.get("TOGGLE WIFI"))
        assertEquals(1, store.size())
    }

    @Test
    fun underscoredSlotNamesWork() {
        // Kotlin named capture groups reject underscores, so the matcher uses positional groups
        // and maps them back by name. This is the case that would break a naive port.
        val extracted = TemplateMatcher.match("message {contact_name} saying {body_text}", "message alex saying hello")

        assertNotNull(extracted)
        assertEquals("alex", extracted!!["contact_name"])
        assertEquals("hello", extracted["body_text"])
    }

    @Test
    fun templateDetectionMirrorsSqlLike() {
        assertTrue(TemplateMatcher.isTemplate("set alarm for {time}"))
        assertFalse(TemplateMatcher.isTemplate("toggle wifi"))
        assertFalse(TemplateMatcher.isTemplate("weird } brace { order"))
    }

    @Test
    fun templateLiteralsAreRegexEscaped() {
        // A '.' in the intent must match a literal dot, not "any character".
        assertNotNull(TemplateMatcher.match("open a.b with {value}", "open a.b with chrome"))
        assertNull(TemplateMatcher.match("open a.b with {value}", "open axb with chrome"))
    }

    @Test
    fun templateThatDoesNotMatchReturnsNull() {
        assertNull(TemplateMatcher.match("set alarm for {time}", "cancel alarm"))
    }
}
