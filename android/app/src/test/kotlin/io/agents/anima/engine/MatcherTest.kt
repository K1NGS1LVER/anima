package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MatcherTest {

    private val delta = 1e-6

    private fun node(
        id: Int = 1,
        className: String = "android.widget.Switch",
        resourceId: String? = null,
        text: String? = null,
        contentDesc: String? = null,
        clickable: Boolean = true,
        bounds: List<Int> = listOf(0, 0, 10, 10),
        center: List<Int> = listOf(5, 5),
        relCenter: List<Double> = listOf(0.0, 0.0),
    ) = PrunedNode(
        id = id,
        className = className,
        resourceId = resourceId,
        text = text,
        contentDesc = contentDesc,
        clickable = clickable,
        checked = false,
        bounds = bounds,
        center = center,
        relBounds = listOf(0.0, 0.0, 0.0, 0.0),
        relCenter = relCenter,
    )

    @Test
    fun exactResourceIdAloneScoresOne() {
        val loc = Locator(resourceId = "com.android.settings:id/switch_wifi")
        val hit = node(resourceId = "com.android.settings:id/switch_wifi")
        assertEquals(1.0, Matcher.score(loc, hit), delta)
    }

    @Test
    fun mismatchedResourceIdAloneScoresZero() {
        val loc = Locator(resourceId = "a")
        assertEquals(0.0, Matcher.score(loc, node(resourceId = "b")), delta)
    }

    @Test
    fun contentDescriptionIsScoredFuzzily() {
        val loc = Locator(contentDesc = "Wi-Fi")
        // ratio("wi-fi", "wifi") == 0.8888..., normalized by the single active weight.
        assertEquals(0.8888888888888888, Matcher.score(loc, node(contentDesc = "WiFi")), 0.0001)
    }

    @Test
    fun classNameUsesSubstringContainment() {
        val loc = Locator(className = "Switch")
        assertEquals(1.0, Matcher.score(loc, node(className = "android.widget.Switch")), delta)
        assertEquals(0.0, Matcher.score(loc, node(className = "android.widget.Button")), delta)
    }

    @Test
    fun spatialScoreFallsOffWithDistance() {
        val loc = Locator(relCenter = listOf(0.5, 0.5))

        // dist 0.0 -> full spatial weight
        assertEquals(1.0, Matcher.score(loc, node(relCenter = listOf(0.5, 0.5))), delta)

        // dist 0.10 -> linear falloff: (1 - 0.10/0.15) == 0.3333...
        assertEquals(0.3333333, Matcher.score(loc, node(relCenter = listOf(0.5, 0.6))), 1e-5)

        // dist 0.20 -> beyond the 0.15 cutoff, contributes nothing
        assertEquals(0.0, Matcher.score(loc, node(relCenter = listOf(0.5, 0.7))), delta)
    }

    @Test
    fun boundsAreUsedOnlyWhenRelativeCenterIsAbsent() {
        val loc = Locator(bounds = listOf(0, 0, 10, 10))
        assertEquals(1.0, Matcher.score(loc, node(bounds = listOf(0, 0, 10, 10))), delta)
        assertEquals(0.0, Matcher.score(loc, node(bounds = listOf(1, 0, 10, 10))), delta)
    }

    @Test
    fun weightsSumToOne() {
        val sum = Matcher.W_RES + Matcher.W_DESC + Matcher.W_TEXT + Matcher.W_CLS + Matcher.W_SPATIAL
        assertEquals(1.0, sum, delta)
    }

    @Test
    fun subThresholdCandidatesReturnNull() {
        val loc = Locator(resourceId = "com.android.settings:id/switch_wifi", contentDesc = "Bluetooth")
        val nodes = listOf(node(id = 1, resourceId = "com.other:id/x", contentDesc = "Airplane"))
        assertNull(Matcher.findBest(loc, nodes))
    }

    @Test
    fun emptyNodeListReturnsNull() {
        assertNull(Matcher.findBest(Locator(resourceId = "a"), emptyList()))
    }

    @Test
    fun tiesGoToTheFirstNode() {
        val loc = Locator(resourceId = "same")
        val nodes = listOf(
            node(id = 7, resourceId = "same"),
            node(id = 9, resourceId = "same"),
        )
        val best = Matcher.findBest(loc, nodes)
        assertNotNull(best)
        assertEquals(7, best!!.id)
    }

    @Test
    fun bestOfSeveralCandidatesWins() {
        val loc = Locator(resourceId = "wifi", contentDesc = "Wi-Fi")
        val nodes = listOf(
            node(id = 1, resourceId = "bluetooth", contentDesc = "Bluetooth"),
            node(id = 2, resourceId = "wifi", contentDesc = "Wi-Fi"),
        )
        assertEquals(2, Matcher.findBest(loc, nodes)!!.id)
    }
}
