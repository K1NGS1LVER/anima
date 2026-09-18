package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParameterExtractorTest {

    @Test
    fun timeValueBecomesTimeSlot() {
        val (templated, slot) = ParameterExtractor.parameterizeGoal("set alarm for 7:30 am", "7:30 am")
        assertEquals("set alarm for {time}", templated)
        assertEquals("time", slot)
    }

    @Test
    fun twentyFourHourTimeIsAlsoATimeSlot() {
        val (templated, slot) = ParameterExtractor.parameterizeGoal("set alarm for 07:30", "07:30")
        assertEquals("set alarm for {time}", templated)
        assertEquals("time", slot)
    }

    @Test
    fun emailValueBecomesEmailSlot() {
        val (templated, slot) =
            ParameterExtractor.parameterizeGoal("email sam.shine@example.com now", "sam.shine@example.com")
        assertEquals("email {email} now", templated)
        assertEquals("email", slot)
    }

    @Test
    fun longDigitRunBecomesNumberSlot() {
        val (templated, slot) = ParameterExtractor.parameterizeGoal("dial 5551234", "5551234")
        assertEquals("dial {number}", templated)
        assertEquals("number", slot)
    }

    @Test
    fun anythingElseFallsBackToValueSlot() {
        val (templated, slot) = ParameterExtractor.parameterizeGoal("search for ramen", "ramen")
        assertEquals("search for {value}", templated)
        assertEquals("value", slot)
    }

    @Test
    fun everyOccurrenceIsReplaced() {
        val (templated, slot) = ParameterExtractor.parameterizeGoal("ramen ramen", "ramen")
        assertEquals("{value} {value}", templated)
        assertEquals("value", slot)
    }

    @Test
    fun valueThatIsNotASubstringLeavesTheGoalUnchanged() {
        val (templated, slot) = ParameterExtractor.parameterizeGoal("turn on wifi", "bluetooth")
        assertEquals("turn on wifi", templated)
        assertNull(slot)
    }

    @Test
    fun blankInputLeavesTheGoalUnchanged() {
        assertEquals(Pair("turn on wifi", null), ParameterExtractor.parameterizeGoal("turn on wifi", null))
        assertEquals(Pair("turn on wifi", null), ParameterExtractor.parameterizeGoal("turn on wifi", "   "))
    }

    @Test
    fun slotNamesArePatternOrdered() {
        assertEquals("time", ParameterExtractor.slotNameFor("7:30 am"))
        assertEquals("email", ParameterExtractor.slotNameFor("a@b.co"))
        assertEquals("number", ParameterExtractor.slotNameFor("12345"))
        assertEquals("value", ParameterExtractor.slotNameFor("ramen"))
    }
}
