package io.agents.anima.design

/**
 * Pixel-to-dp conversion for this module.
 *
 * `ScreenObservation` carries `screenSize` in pixels but **no density**, so
 * there is no way to convert a measured pixel gap into the dp the pack reports.
 * Until that field exists, density is inferred from the frame width against the
 * standard Android buckets. That is a real approximation and it is confined to
 * this one file so there is exactly one place to fix.
 *
 * Raised as Q6 against `Contracts.kt`: `ScreenObservation` should carry
 * `densityDpi`. When it does, [infer] is deleted and [Density.of] takes the
 * real value.
 */
@JvmInline
value class Density(val scale: Double) {

    /** Device pixels to dp, rounded to the nearest whole dp. */
    fun toDp(px: Int): Int = Math.round(px / scale).toInt()

    /** dp back to device pixels. Used by the rebuild renderer. */
    fun toPx(dp: Int): Int = Math.round(dp * scale).toInt()

    companion object {
        fun of(densityDpi: Int): Density = Density(densityDpi / 160.0)

        /**
         * Best guess at the density of a frame, from its width in pixels.
         *
         * Phone frames cluster hard around a few widths, so this is right far
         * more often than it is wrong — but it IS a guess, and a tablet or a
         * foldable will fall through to the nearest bucket. The extractors that
         * use it round to whole dp afterwards, which absorbs a bucket-edge
         * error of a pixel or two but not a wrong bucket.
         *
         * Prefer [infer] with both dimensions where they are known: width alone
         * cannot separate the two densities that share a 1080px width, and
         * getting that wrong skews every measurement by 14%.
         */
        fun infer(screenWidthPx: Int): Density = when {
            screenWidthPx >= 1400 -> Density(4.0)  // xxxhdpi, 1440 wide
            screenWidthPx >= 1000 -> Density(2.625) // 420dpi, the common 1080-wide phone
            screenWidthPx >= 680 -> Density(2.0)   // xhdpi,    720 wide
            screenWidthPx > 0 -> Density(1.5)      // hdpi,     480 wide
            else -> Density(2.625)
        }

        /**
         * Density from both frame dimensions, which is markedly better than
         * width alone at the one width where it matters.
         *
         * 1080px wide covers two different densities and the aspect ratio
         * separates them: the tall 20:9 phones that ship 1080x2400 are 420dpi
         * (measured on the project's own reference device), while the older 16:9
         * 1080x1920 generation is a true xxhdpi 480. Treating both as 480 makes
         * a 16dp gap measure as 14dp on every modern phone, which is enough to
         * move the inferred grid base from 8 to 4.
         */
        fun infer(screenWidthPx: Int, screenHeightPx: Int): Density {
            val shortSide = minOf(screenWidthPx, screenHeightPx)
            val longSide = maxOf(screenWidthPx, screenHeightPx)
            if (shortSide in 1000..1200 && longSide > 0) {
                // 16:9 is 1.78; anything appreciably taller is a modern frame.
                val tall = longSide * 100 / shortSide > 185
                return if (tall) Density(2.625) else Density(3.0)
            }
            return infer(shortSide)
        }
    }
}
