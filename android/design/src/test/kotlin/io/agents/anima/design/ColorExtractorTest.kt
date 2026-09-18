package io.agents.anima.design

import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.UiMode
import io.agents.anima.engine.PrunedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These assert named colours, not "whatever it was last time".
 *
 * That is the whole reason this module captures its own fixtures: the Settings
 * screen is a light one whose cards are visibly paler than the window behind
 * them, and the Clock is a dark app. A snapshot test would pass just as happily
 * with the two roles swapped.
 */
class ColorExtractorTest {

    private val extractor = ColorExtractor()

    @Test
    fun `separates a near-white card from the tinted window behind it`() {
        val colors = extractor.extract(listOf(Observations.settings()))

        // Material 3 puts a faint lavender tint on the window and near-white on
        // the cards. Under six units a channel apart, and the wrong way round if
        // you take the frame's most common colour, because the cards cover more
        // of the screen than the window does.
        assertEquals("#ECECF4", colors.background)
        assertEquals("#FCFCFC", colors.surface)
    }

    @Test
    fun `reads a dark app as dark even when the system is in light mode`() {
        val colors = extractor.extract(listOf(Observations.clock()))

        val background = Colors.fromHex(colors.background!!)
        assertTrue(
            "the Clock draws its own dark theme regardless of the system setting",
            Colors.luminance(background) < 500,
        )
        assertEquals("#0C0C14", colors.background)
    }

    @Test
    fun `on-primary contrasts with primary`() {
        val colors = extractor.extract(Observations.realCaptures())

        val primary = Colors.fromHex(colors.primary!!)
        val onPrimary = Colors.fromHex(colors.onPrimary!!)
        assertTrue(
            "on-primary must be legible on primary",
            Colors.contrast(primary, onPrimary) >= 300,
        )
    }

    @Test
    fun `no error token when no screen showed an error`() {
        // None of the captures contains an error state, and inventing one from
        // the nearest warm colour would put a claim in the pack that the scan
        // never saw.
        assertNull(extractor.extract(Observations.realCaptures()).error)
    }

    @Test
    fun `the palette leads with the roles it named`() {
        val colors = extractor.extract(listOf(Observations.settings()))

        assertTrue(colors.palette.contains(colors.background))
        assertTrue(colors.palette.contains(colors.surface))
        assertTrue(colors.palette.contains(colors.primary))
        assertTrue("palette stays a summary", colors.palette.size <= 6)
    }

    @Test
    fun `the same screenshot always produces the same tokens`() {
        val first = extractor.extract(Observations.realCaptures())
        val second = ColorExtractor().extract(Observations.realCaptures())

        assertEquals(first, second)
    }

    @Test
    fun `no screenshot means no colours rather than invented ones`() {
        val noPixels = ScreenObservation(
            packageName = "com.example.test",
            activity = null,
            nodes = listOf(
                PrunedNode(id = 1, className = "TextView", bounds = listOf(0, 0, 100, 100)),
            ),
            screenshot = null,
            screenSize = 1080 to 2400,
            uiMode = UiMode.LIGHT,
        )

        val colors = extractor.extract(listOf(noPixels))

        assertNull(colors.background)
        assertNull(colors.primary)
        assertTrue(colors.palette.isEmpty())
    }

    @Test
    fun `a corrupt screenshot is skipped rather than failing the scan`() {
        val broken = ScreenObservation(
            packageName = "com.example.test",
            activity = null,
            nodes = emptyList(),
            screenshot = byteArrayOf(1, 2, 3, 4),
            screenSize = 1080 to 2400,
            uiMode = UiMode.LIGHT,
        )

        val colors = extractor.extract(listOf(broken, Observations.settings()))

        assertNotNull("the good screen still contributes", colors.background)
    }
}
