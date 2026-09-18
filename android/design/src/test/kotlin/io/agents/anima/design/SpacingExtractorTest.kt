package io.agents.anima.design

import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.UiMode
import io.agents.anima.engine.PrunedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacingExtractorTest {

    // The synthetic fixtures in this file are drawn at 3.0 (48px = 16dp), so the
    // density is pinned rather than inferred: these tests are about the grid
    // arithmetic, and letting a guess sit underneath them would mean a change to
    // density inference showed up here as a spacing bug.
    private val extractor = SpacingExtractor(density = Observations.SYNTHETIC_DENSITY)

    @Test
    fun `recovers an 8dp grid from a real dump`() {
        val home = Observations.load("example_bank_home")

        // The dump must survive pruning, or the rest of this asserts nothing.
        assertEquals(8, home.nodes.size)

        val spacing = extractor.extract(listOf(home))

        assertEquals(8, spacing.baseDp)
        assertEquals(listOf(8, 16), spacing.scale)
    }

    @Test
    fun `picks 4 when the layout is genuinely on a 4dp grid`() {
        // Gaps of 12dp and 20dp are multiples of 4 and not of 8, so a base of 8
        // explains none of them. This is the case that makes the fit score earn
        // its keep rather than always answering 8.
        val screen = observation(
            node(1, 0, 0, 1080, 100),
            node(2, 0, 136, 1080, 236),   // gap 36px = 12dp
            node(3, 0, 296, 1080, 396),   // gap 60px = 20dp
            node(4, 0, 432, 1080, 532),   // gap 36px = 12dp
            node(5, 0, 592, 1080, 692),   // gap 60px = 20dp
        )

        val spacing = extractor.extract(listOf(screen))

        assertEquals(4, spacing.baseDp)
        assertEquals(listOf(12, 20), spacing.scale)
    }

    @Test
    fun `a tie between 4 and 8 goes to 8`() {
        // Every gap here is a multiple of both, so both bases score 1000.
        val screen = observation(
            node(1, 0, 0, 1080, 100),
            node(2, 0, 124, 1080, 224),   // 24px = 8dp
            node(3, 0, 272, 1080, 372),   // 48px = 16dp
            node(4, 0, 396, 1080, 496),   // 24px = 8dp
        )

        assertEquals(8, extractor.extract(listOf(screen)).baseDp)
    }

    @Test
    fun `an empty scale rather than an invented one when there is no evidence`() {
        val spacing = extractor.extract(listOf(observation()))

        assertEquals(8, spacing.baseDp)
        assertTrue(spacing.scale.isEmpty())
    }

    @Test
    fun `zero-area nodes do not poison the histogram`() {
        val withJunk = observation(
            node(1, 0, 0, 1080, 100),
            node(2, 500, 150, 500, 150),  // collapsed, zero area
            node(3, 0, 124, 1080, 224),
        )
        val without = observation(
            node(1, 0, 0, 1080, 100),
            node(3, 0, 124, 1080, 224),
        )

        assertEquals(
            extractor.extract(listOf(without)),
            extractor.extract(listOf(withJunk)),
        )
    }

    @Test
    fun `the same observations always produce the same tokens`() {
        val home = Observations.load("example_bank_home")

        val first = extractor.extract(listOf(home))
        val second = extractor.extract(listOf(home))
        // Rebuilt from the same source, so node identity cannot be carrying it.
        val third = SpacingExtractor(density = Observations.SYNTHETIC_DENSITY)
            .extract(listOf(Observations.load("example_bank_home")))

        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun `gaps wider than a section break are ignored`() {
        val screen = observation(
            node(1, 0, 0, 1080, 100),
            node(2, 0, 124, 1080, 224),    // 8dp, counted
            node(3, 0, 1300, 1080, 1400),  // 358dp, far beyond maxGapDp
        )

        val spacing = extractor.extract(listOf(screen))

        assertTrue(spacing.scale.none { it > 96 })
    }

    // --- helpers ---------------------------------------------------------

    private fun observation(vararg nodes: PrunedNode) = ScreenObservation(
        packageName = "com.example.test",
        activity = null,
        nodes = nodes.toList(),
        screenshot = null,
        screenSize = 1080 to 2400,
        uiMode = UiMode.LIGHT,
    )

    private fun node(id: Int, left: Int, top: Int, right: Int, bottom: Int) = PrunedNode(
        id = id,
        className = "TextView",
        text = "n$id",
        bounds = listOf(left, top, right, bottom),
        center = listOf((left + right) / 2, (top + bottom) / 2),
    )
}
