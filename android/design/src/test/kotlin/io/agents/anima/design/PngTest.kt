package io.agents.anima.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decoder has to be right before anything built on it means anything: a
 * colour extractor reading mis-unfiltered pixels would still produce confident,
 * plausible, wrong tokens.
 */
class PngTest {

    @Test
    fun `decodes a real screencap to its declared size`() {
        val bytes = Observations.settings().screenshot!!

        val raster = Png.decode(bytes)

        assertEquals(1080, raster.width)
        assertEquals(2400, raster.height)
        assertEquals(1080 * 2400, raster.pixels.size)
    }

    @Test
    fun `decoded pixels are opaque and in range`() {
        val raster = Png.decode(Observations.clock().screenshot!!)

        // Sampled rather than exhaustive: a filter bug corrupts whole scanlines,
        // so a spread of rows catches it without walking 2.5M pixels.
        for (y in 0 until raster.height step 97) {
            for (x in 0 until raster.width step 89) {
                val pixel = raster.at(x, y)
                assertEquals("screencap writes opaque frames", 255, Colors.alpha(pixel))
                assertTrue(Colors.red(pixel) in 0..255)
                assertTrue(Colors.green(pixel) in 0..255)
                assertTrue(Colors.blue(pixel) in 0..255)
            }
        }
    }

    @Test
    fun `the unfiltering is right, not merely plausible`() {
        // A broken Paeth or Average predictor still yields an image — it just
        // smears. The check is that a known-flat region really is flat: the
        // Clock's background is a solid dark, and any filter error shows up as
        // drift across the row.
        val raster = Png.decode(Observations.clock().screenshot!!)

        val y = raster.height / 2
        val first = raster.at(10, y)
        for (x in 10 until raster.width - 10 step 13) {
            assertEquals(
                "flat background must decode flat at x=$x",
                0,
                Colors.distanceSquared(first, raster.at(x, y)),
            )
        }
    }

    @Test
    fun `decoding is a pure function of the bytes`() {
        val bytes = Observations.settings().screenshot!!

        val first = Png.decode(bytes)
        val second = Png.decode(bytes)

        assertTrue(first.pixels.contentEquals(second.pixels))
    }

    @Test
    fun `rejects what it cannot read instead of guessing`() {
        assertFalse(Png.isPng(byteArrayOf(1, 2, 3)))
        assertFalse(Png.isPng(ByteArray(0)))

        val notPng = runCatching { Png.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)) }
        assertTrue(notPng.isFailure)
    }

    @Test
    fun `truncated data fails loudly`() {
        val whole = Observations.settings().screenshot!!

        val truncated = runCatching { Png.decode(whole.copyOf(whole.size / 2)) }

        assertTrue("a half-read frame must not decode to half an image", truncated.isFailure)
    }
}
