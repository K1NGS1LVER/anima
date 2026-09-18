package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cross-runtime guarantee: these expected values were produced by CPython itself, e.g.
 *   python3 -c "from difflib import SequenceMatcher; print(SequenceMatcher(None,'wi-fi','wifi').ratio())"
 * If this test fails, the Kotlin matcher and the Python matcher no longer agree.
 */
class TextRatioTest {

    private val delta = 0.0001

    @Test
    fun identicalStringsScoreOne() {
        assertEquals(1.0, TextRatio.ratio("wifi", "wifi"), delta)
    }

    @Test
    fun hyphenatedVariantMatchesPython() {
        assertEquals(0.8888888888888888, TextRatio.ratio("wi-fi", "wifi"), delta)
    }

    @Test
    fun symmetricalForSwappedArguments() {
        assertEquals(0.8888888888888888, TextRatio.ratio("wifi", "wi-fi"), delta)
    }

    @Test
    fun pluralVariantMatchesPython() {
        assertEquals(0.9333333333333333, TextRatio.ratio("settings", "setting"), delta)
    }

    @Test
    fun disjointStringsScoreZero() {
        assertEquals(0.0, TextRatio.ratio("allow", "deny"), delta)
    }

    @Test
    fun twoEmptySequencesScoreOne() {
        // Python returns 1.0 for two empty sequences.
        assertEquals(1.0, TextRatio.ratio("", ""), delta)
    }

    @Test
    fun oneEmptySequenceScoresZero() {
        assertEquals(0.0, TextRatio.ratio("", "wifi"), delta)
        assertEquals(0.0, TextRatio.ratio("wifi", ""), delta)
    }

    @Test
    fun multiBlockMatchesPython() {
        // python3: SequenceMatcher(None, 'turn on wifi', 'wifi').ratio() -> 0.5
        assertEquals(0.5, TextRatio.ratio("turn on wifi", "wifi"), delta)
        // python3: SequenceMatcher(None, 'ok', 'okay').ratio() -> 0.6666666666666666
        assertEquals(0.6666666666666666, TextRatio.ratio("ok", "okay"), delta)
    }
}
