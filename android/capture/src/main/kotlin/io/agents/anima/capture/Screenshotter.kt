package io.agents.anima.capture

import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a full-resolution screenshot into something a pack can afford to carry.
 *
 * A 1080x2400 PNG is around 2 MB. Forty of them is 80 MB, against a whole-pack
 * budget of 6 MB — so the pack's size problem is not the JSON, it is the
 * pixels. Downscaling to a 720 px longest edge and encoding lossy WebP gets one
 * screen to roughly 30–50 KB while staying entirely readable as a thumbnail,
 * as a side-by-side reference for the rebuild test, and as VLM input.
 *
 * Quality is dropped in steps rather than once, because a dense screenshot at
 * quality 80 can still exceed the budget while a sparse one at 80 is nowhere
 * near it, and picking a single quality low enough for the worst case would
 * make every other screen look bad for nothing.
 */
object Screenshotter {

    /** `KNOWLEDGE_PACK.md`: one screenshot ≤ 60 KB, longest edge 720 px. */
    const val MAX_EDGE_PX = 720
    const val MAX_BYTES = 60 * 1024

    private val QUALITY_LADDER = intArrayOf(80, 65, 50, 40, 30)

    /**
     * @return WebP bytes within budget, or null when [bitmap] is unusable.
     *   The caller treats null as "no pixels for this screen", never as an error:
     *   a scan that stops because one screenshot failed is worse than a pack
     *   with one screen missing its image.
     */
    fun encode(bitmap: Bitmap?, maxEdgePx: Int = MAX_EDGE_PX, maxBytes: Int = MAX_BYTES): ByteArray? {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null

        val scaled = downscale(bitmap, maxEdgePx)
        var best: ByteArray? = null
        for (quality in QUALITY_LADDER) {
            val bytes = compress(scaled, quality) ?: continue
            best = bytes
            if (bytes.size <= maxBytes) break
        }
        if (scaled !== bitmap) scaled.recycle()
        return best
    }

    /** Preserves aspect ratio; returns the input untouched when it already fits. */
    fun downscale(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdgePx) return bitmap
        val factor = maxEdgePx.toDouble() / longest
        val w = max(1, (bitmap.width * factor).roundToInt())
        val h = max(1, (bitmap.height * factor).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray? = try {
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        if (bitmap.compress(format, quality, out)) out.toByteArray() else null
    } catch (e: Exception) {
        null
    }
}
