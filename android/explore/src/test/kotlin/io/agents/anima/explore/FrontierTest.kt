package io.agents.anima.explore

import io.agents.anima.core.ElementAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frontier's ordering is the foundation the stability requirement stands
 * on. If two scans drain it differently, screen ids get assigned in a different
 * sequence and no amount of careful hashing downstream can make the two packs
 * byte-identical.
 */
class FrontierTest {

    private fun candidate(
        screen: String = "scr_a",
        label: String,
        top: Int = 100,
        score: Int = 10,
        depth: Int = 0,
        action: ElementAction = ElementAction.TAP,
    ) = Candidate(
        screenId = screen,
        node = node(1, label, 40, top, 1040, top + 120),
        action = action,
        depth = depth,
        score = score,
    )

    @Test
    fun theSameActionIsNeverQueuedTwice() {
        val frontier = Frontier()
        assertTrue(frontier.offer(candidate(label = "Accounts")))
        assertFalse("identical candidate must be rejected", frontier.offer(candidate(label = "Accounts")))
        assertEquals(1, frontier.size)
    }

    @Test
    fun anActionAlreadyTakenIsNeverOfferedAgain() {
        // Screens are re-observed constantly -- every time the crawler backs
        // into one it re-enumerates the same elements. Without this the
        // frontier would refill itself forever and the scan would never end.
        val frontier = Frontier()
        frontier.offer(candidate(label = "Accounts"))
        frontier.take()

        assertFalse("a completed action must not come back", frontier.offer(candidate(label = "Accounts")))
        assertTrue(frontier.isEmpty())
    }

    @Test
    fun itDrainsByScoreThenDepthThenKey() {
        val frontier = Frontier()
        frontier.offer(candidate(label = "deep", score = 50, depth = 3))
        frontier.offer(candidate(label = "shallow", score = 50, depth = 1))
        frontier.offer(candidate(label = "best", score = 90, depth = 9))

        assertEquals("best", frontier.take()?.node?.text)
        assertEquals("score ties must break on depth, shallower first", "shallow", frontier.take()?.node?.text)
        assertEquals("deep", frontier.take()?.node?.text)
        assertNull(frontier.take())
    }

    @Test
    fun candidatesThatScoreIdenticallyStillHaveATotalOrder() {
        // This is the property that matters. On a real screen most candidates
        // score the same, and a PriorityQueue -- a heap -- gives no guarantee at
        // all about the order of equal elements. Two runs would diverge on the
        // first list screen. A sorted set with a total comparator has no ties.
        val labels = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot")

        // Position is derived from the label, not from insertion order: on a
        // real screen the same element sits in the same place on both scans,
        // and only the sequence the crawler happens to enumerate them in
        // differs. Tying `top` to the loop index would make the two runs
        // genuinely different screens and test nothing.
        fun drain(order: List<String>): List<String> {
            val frontier = Frontier()
            order.forEach { label ->
                frontier.offer(candidate(label = label, top = 100 + labels.indexOf(label) * 130, score = 25))
            }
            return generateSequence { frontier.take() }.map { it.node.text!! }.toList()
        }

        assertEquals(
            "insertion order must not influence drain order",
            drain(labels),
            drain(labels.reversed()),
        )
        assertEquals(labels.size, drain(labels).size)
    }

    @Test
    fun itPrefersTheScreenAlreadyInFrontOfIt() {
        // Not an optimisation: the alternative is navigating back to a screen we
        // are already standing on, and every navigation is several gestures that
        // can each fail or land somewhere unexpected.
        val frontier = Frontier()
        frontier.offer(candidate(screen = "scr_elsewhere", label = "Tempting", score = 99))
        frontier.offer(candidate(screen = "scr_here", label = "Local", score = 5))

        assertEquals("Local", frontier.takePreferring("scr_here")?.node?.text)
        assertEquals("Tempting", frontier.takePreferring("scr_here")?.node?.text)
    }

    @Test
    fun itFallsBackToTheGlobalBestWhenThisScreenIsDone() {
        val frontier = Frontier()
        frontier.offer(candidate(screen = "scr_elsewhere", label = "Only option", score = 5))

        assertEquals("Only option", frontier.takePreferring("scr_here")?.node?.text)
    }

    @Test
    fun abandoningAScreenRetiresItsActionsForGood() {
        val frontier = Frontier()
        frontier.offer(candidate(screen = "scr_dead", label = "One"))
        frontier.offer(candidate(screen = "scr_dead", label = "Two"))
        frontier.offer(candidate(screen = "scr_live", label = "Three"))

        frontier.dropScreen("scr_dead")

        assertEquals(1, frontier.size)
        assertEquals("Three", frontier.take()?.node?.text)
        assertFalse(
            "a dropped action must not be re-queued when the screen is seen again",
            frontier.offer(candidate(screen = "scr_dead", label = "One")),
        )
    }
}
