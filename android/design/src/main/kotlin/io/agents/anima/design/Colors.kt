package io.agents.anima.design

import java.util.Locale

/**
 * Colour arithmetic for token extraction and classification.
 *
 * Everything here is a pure function of an ARGB int, so the same pixels always
 * yield the same tokens — the Y6 requirement, met by construction rather than
 * by seeding a random number generator.
 */
object Colors {

    fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF
    fun red(argb: Int): Int = (argb ushr 16) and 0xFF
    fun green(argb: Int): Int = (argb ushr 8) and 0xFF
    fun blue(argb: Int): Int = argb and 0xFF

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** `#RRGGBB`, upper case. Alpha is dropped: design tokens name a colour, not a blend. */
    fun toHex(argb: Int): String =
        String.format(Locale.US, "#%02X%02X%02X", red(argb), green(argb), blue(argb))

    fun fromHex(hex: String): Int {
        val body = hex.removePrefix("#")
        require(body.length == 6) { "expected #RRGGBB, got $hex" }
        return rgb(
            body.substring(0, 2).toInt(16),
            body.substring(2, 4).toInt(16),
            body.substring(4, 6).toInt(16),
        )
    }

    /**
     * Relative luminance, WCAG 2.1, scaled to 0..10000.
     *
     * Integer-scaled on purpose: this feeds contrast comparisons and tie-breaks,
     * and a float there would make the ordering depend on rounding.
     */
    fun luminance(argb: Int): Int {
        val r = linear(red(argb))
        val g = linear(green(argb))
        val b = linear(blue(argb))
        return Math.round((0.2126 * r + 0.7152 * g + 0.0722 * b) * 10000).toInt()
    }

    private fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /** WCAG contrast ratio, scaled by 100 (so 4.5:1 is 450). */
    fun contrast(a: Int, b: Int): Int {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return ((lighter + 500).toLong() * 100 / (darker + 500)).toInt()
    }

    /** Max channel minus min channel, 0..255. Cheap, stable stand-in for chroma. */
    fun chroma(argb: Int): Int {
        val r = red(argb)
        val g = green(argb)
        val b = blue(argb)
        return maxOf(r, g, b) - minOf(r, g, b)
    }

    /** A colour with so little chroma it reads as white, grey or black. */
    fun isNeutral(argb: Int, threshold: Int = NEUTRAL_CHROMA): Boolean = chroma(argb) < threshold

    /** Hue in degrees 0..359, or -1 when the colour is neutral and has no meaningful hue. */
    fun hue(argb: Int): Int {
        val r = red(argb)
        val g = green(argb)
        val b = blue(argb)
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta == 0) return -1
        val degrees = when (max) {
            r -> 60.0 * (((g - b).toDouble() / delta) % 6.0)
            g -> 60.0 * (((b - r).toDouble() / delta) + 2.0)
            else -> 60.0 * (((r - g).toDouble() / delta) + 4.0)
        }
        val rounded = Math.round(degrees).toInt()
        return ((rounded % 360) + 360) % 360
    }

    /** Squared distance in RGB. Squared to keep it integer — only comparisons use it. */
    fun distanceSquared(a: Int, b: Int): Int {
        val dr = red(a) - red(b)
        val dg = green(a) - green(b)
        val db = blue(a) - blue(b)
        return dr * dr + dg * dg + db * db
    }

    /** Whether [argb] sits in the red band that a Material error colour occupies. */
    fun isErrorLike(argb: Int): Boolean {
        if (chroma(argb) < ERROR_MIN_CHROMA) return false
        val h = hue(argb)
        return h in 0..14 || h in 340..359
    }

    /** Black or white, whichever reads better on [background]. */
    fun bestOn(background: Int): Int {
        val white = rgb(255, 255, 255)
        val black = rgb(0, 0, 0)
        return if (contrast(background, white) >= contrast(background, black)) white else black
    }

    /** Below this, a colour is grey enough that calling it "primary" would be wrong. */
    const val NEUTRAL_CHROMA = 18

    private const val ERROR_MIN_CHROMA = 60
}
