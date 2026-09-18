package io.agents.anima.design

import io.agents.anima.core.UiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The composed extractor — Y2, Y4, Y6 and Y7 as they land in the pack. */
class DesignSystemTest {

    private val extractor = AnimaDesignExtractor(density = Observations.REFERENCE_DENSITY)

    @Test
    fun `reads Material 3's own type scale off a Material 3 screen`() {
        val design = extractor.extract(listOf(Observations.settings()))

        // Settings is stock Material 3: list titles are 16sp and summaries 14sp.
        // Independently known numbers, which is what makes this a calibration
        // and not a snapshot.
        val sizes = design.typography.map { it.sizeSp.toInt() }
        assertTrue("expected a 16sp title, got $sizes", sizes.contains(16))
        assertTrue("expected 14sp body, got $sizes", sizes.contains(14))
    }

    @Test
    fun `family and weight are null rather than fabricated`() {
        val design = extractor.extract(Observations.realCaptures())

        assertTrue(design.typography.isNotEmpty())
        assertTrue(
            "a uiautomator dump carries no font family; claiming one would be invention",
            design.typography.all { it.family == null },
        )
    }

    @Test
    fun `catalogues the row that Settings repeats`() {
        val design = extractor.extract(listOf(Observations.settings()))

        val row = design.components.firstOrNull { it.name == "ListRow" }
        assertNotNull("a list of ten identical rows is a component", row)
        assertEquals("seen on the one screen it was captured from", 1, row!!.seenOn)
        assertNotNull("a row has a measured height", row.heightDp)
    }

    @Test
    fun `a component fill is the element's background, not its text`() {
        val design = extractor.extract(listOf(Observations.settings()))

        val row = design.components.first { it.name == "ListRow" }
        val fill = Colors.fromHex(row.fill!!)

        // Sampling the centre pixel of a labelled row reads the label, which is
        // dark; the row itself is near-white. This is the assertion that keeps
        // the rebuild from drawing navy rows.
        assertTrue(
            "row fill ${row.fill} should be light, not the text colour",
            Colors.luminance(fill) > 5000,
        )
    }

    @Test
    fun `reports only the modes actually captured`() {
        val design = extractor.extract(Observations.realCaptures())

        // Every fixture was captured with the system in light mode. Emitting a
        // dark entry would mean inventing a palette the scan never saw — Y7
        // treats single-mode as the normal case.
        assertTrue(design.modes.containsKey(UiMode.LIGHT.wire))
        assertTrue(
            "no dark captures, so no dark mode claim",
            !design.modes.containsKey(UiMode.DARK.wire),
        )
    }

    @Test
    fun `tone of voice is left to the understander`() {
        // The field belongs to ScreenUnderstander.toneOfVoice. Two answers to
        // one question in the same pack is worse than one.
        assertNull(extractor.extract(Observations.realCaptures()).toneOfVoice)
    }

    @Test
    fun `the whole design system is byte-stable across runs`() {
        val first = extractor.extract(Observations.realCaptures())
        val second = AnimaDesignExtractor(density = Observations.REFERENCE_DENSITY)
            .extract(Observations.realCaptures())

        assertEquals(first, second)
    }

    @Test
    fun `crawl order does not change the tokens`() {
        val forwards = Observations.realCaptures()
        val backwards = forwards.reversed()

        assertEquals(
            "a pack must not depend on which screen the crawler saw first",
            extractor.extract(forwards),
            extractor.extract(backwards),
        )
    }

    @Test
    fun `an empty scan produces an empty design system rather than throwing`() {
        val design = AnimaDesignExtractor().extract(emptyList())

        assertNull(design.colors.primary)
        assertTrue(design.typography.isEmpty())
        assertTrue(design.components.isEmpty())
        assertTrue(design.modes.isEmpty())
    }
}
