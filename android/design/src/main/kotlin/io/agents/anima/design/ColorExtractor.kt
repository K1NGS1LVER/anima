package io.agents.anima.design

import io.agents.anima.core.ColorTokens
import io.agents.anima.core.ScreenObservation
import io.agents.anima.engine.PrunedNode

/**
 * Recovers the app's colour roles from the screenshots.
 *
 * The naive version of this — quantise the whole frame, print the top colours —
 * produces a histogram, not a design system. It cannot tell a card from the
 * window behind it, and it calls whatever happens to cover the most pixels
 * "primary". So this reads the pixels *through the node tree*: every element's
 * fill is sampled inside its own bounds with its children masked out, which is
 * what separates surface from background and what lets a small, saturated,
 * clickable element outrank a large grey one for the primary role.
 *
 * DETERMINISM (Y6): fixed sampling grids, integer arithmetic, and every sort
 * carries an explicit tie-break on the colour value itself. There is no random
 * seed to set because there is nothing random — the same screenshot always
 * produces the same tokens.
 */
class ColorExtractor(
    /** Sample step in pixels for whole-frame statistics. Fixed, so sampling is reproducible. */
    private val frameStride: Int = 4,
    /** Samples per axis inside one element when measuring its fill. */
    private val elementGrid: Int = 7,
    /** Two colours closer than this (squared RGB distance) are the same token. */
    private val mergeDistanceSquared: Int = 900,
    /**
     * Tolerance for deciding two *roles* are the same colour.
     *
     * Much tighter than [mergeDistanceSquared], because the pair this has to
     * separate is the hardest one on a Material 3 screen: a near-white card on a
     * faintly tinted window, which can be under six units apart per channel and
     * is still the difference between surface and background.
     */
    private val roleDistanceSquared: Int = 120,
    /** An element smaller than this many pixels is too small to carry a role. */
    private val minElementArea: Int = 48 * 48,
) {

    fun extract(observations: List<ScreenObservation>): ColorTokens {
        val frames = observations.mapNotNull { observation ->
            val bytes = observation.screenshot ?: return@mapNotNull null
            val raster = runCatching { Png.decode(bytes) }.getOrNull() ?: return@mapNotNull null
            Frame(raster, observation.nodes)
        }
        if (frames.isEmpty()) return EMPTY

        val areaByColor = LinkedHashMap<Int, Long>()
        val fills = ArrayList<Fill>()
        for (frame in frames) {
            accumulateFrame(frame.raster, areaByColor)
            fills += measureFills(frame)
        }

        val background = pickBackground(frames) ?: dominant(areaByColor) ?: return EMPTY
        val surface = pickSurface(fills, background)
        val primary = pickPrimary(fills, areaByColor, background, surface)
        val onPrimary = primary?.let { pickOnPrimary(frames, it) }
        val error = pickError(fills, areaByColor, primary)

        return ColorTokens(
            primary = primary?.let { Colors.toHex(it) },
            onPrimary = onPrimary?.let { Colors.toHex(it) },
            surface = surface?.let { Colors.toHex(it) },
            background = Colors.toHex(background),
            error = error?.let { Colors.toHex(it) },
            palette = buildPalette(areaByColor, listOfNotNull(primary, surface, background, error)),
        )
    }

    // --- frame statistics -------------------------------------------------

    /** Area of every quantised colour across the whole frame, by fixed-stride sampling. */
    private fun accumulateFrame(raster: Raster, into: MutableMap<Int, Long>) {
        var y = 0
        while (y < raster.height) {
            var x = 0
            while (x < raster.width) {
                val pixel = raster.at(x, y)
                if (Colors.alpha(pixel) >= OPAQUE) {
                    val key = quantise(pixel)
                    into[key] = (into[key] ?: 0L) + 1L
                }
                x += frameStride
            }
            y += frameStride
        }
    }

    /**
     * Snaps a colour to a 5-bit-per-channel cell, taking the cell's centre.
     *
     * Quantising at all is what stops antialiasing and gradients from splitting
     * one visual colour across thousands of histogram entries. Taking the centre
     * rather than an average keeps the result a pure function of the input —
     * an average would depend on which pixels happened to be sampled.
     */
    private fun quantise(argb: Int): Int {
        fun snap(channel: Int): Int = ((channel shr 3) shl 3) or 0x04
        return Colors.rgb(snap(Colors.red(argb)), snap(Colors.green(argb)), snap(Colors.blue(argb)))
    }

    /**
     * The window colour, read from a ring of pixels just inside the frame edge.
     *
     * Taking the most common colour in the whole frame gets this backwards on
     * exactly the layout it matters for: a list of cards covers more pixels than
     * the window behind it, so the card colour wins and surface and background
     * swap places. Cards inset from the edge; the window does not. So the edge
     * ring is the window, structurally, and that holds whether the app is light,
     * dark, or edge-to-edge.
     */
    private fun pickBackground(frames: List<Frame>): Int? {
        val counts = LinkedHashMap<Int, Long>()
        for (frame in frames) {
            val raster = frame.raster
            val inset = EDGE_INSET
            if (raster.width <= inset * 2 || raster.height <= inset * 2) continue

            fun sample(x: Int, y: Int) {
                if (!raster.inBounds(x, y)) return
                val pixel = raster.at(x, y)
                if (Colors.alpha(pixel) < OPAQUE) return
                val key = quantise(pixel)
                counts[key] = (counts[key] ?: 0L) + 1L
            }

            var y = 0
            while (y < raster.height) {
                sample(inset, y)
                sample(raster.width - 1 - inset, y)
                y += frameStride
            }
            var x = 0
            while (x < raster.width) {
                sample(x, raster.height - 1 - inset)
                x += frameStride
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
    }

    private fun dominant(areaByColor: Map<Int, Long>): Int? =
        areaByColor.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key

    // --- element fills ----------------------------------------------------

    /**
     * The fill of every element large enough to have one, with its children
     * masked out so a list row reports its own background and not its text.
     */
    private fun measureFills(frame: Frame): List<Fill> {
        val nodes = frame.nodes
            .filter { it.bounds.size == 4 && it.area() >= minElementArea }
            .sortedBy { it.id }
        val out = ArrayList<Fill>(nodes.size)

        for (node in nodes) {
            val children = nodes.filter { it !== node && it.isStrictlyInside(node) }
            val counts = LinkedHashMap<Int, Int>()
            val left = node.bounds[0]
            val top = node.bounds[1]
            val width = node.bounds[2] - left
            val height = node.bounds[3] - top

            for (row in 1..elementGrid) {
                for (column in 1..elementGrid) {
                    val x = left + width * column / (elementGrid + 1)
                    val y = top + height * row / (elementGrid + 1)
                    if (!frame.raster.inBounds(x, y)) continue
                    if (children.any { it.contains(x, y) }) continue
                    val pixel = frame.raster.at(x, y)
                    if (Colors.alpha(pixel) < OPAQUE) continue
                    val key = quantise(pixel)
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }

            val modal = counts.entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .firstOrNull() ?: continue
            // A fill only means something if the element is mostly that colour.
            // Anything patchier is an image or a gradient, not a token.
            if (modal.value * 2 < counts.values.sum()) continue
            out += Fill(modal.key, node.area().toLong(), node.clickable)
        }
        return out
    }

    // --- role classification ----------------------------------------------

    /**
     * Surface is what content sits *on*: the card colour, distinct from the
     * window behind it. Chosen by total area among elements whose fill differs
     * from the background, which is exactly how a card reads to the eye.
     */
    private fun pickSurface(fills: List<Fill>, background: Int): Int? {
        val byColor = LinkedHashMap<Int, Long>()
        for (fill in fills) {
            if (Colors.distanceSquared(fill.color, background) <= roleDistanceSquared) continue
            if (Colors.chroma(fill.color) > SURFACE_MAX_CHROMA) continue
            byColor[fill.color] = (byColor[fill.color] ?: 0L) + fill.area
        }
        return byColor.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
    }

    /**
     * Primary is the app's brand colour: saturated, deliberate, and usually on
     * something you can press. Scored as chroma against area rather than area
     * alone — a FAB is far smaller than the page behind it and far more
     * characteristic of the brand.
     */
    private fun pickPrimary(
        fills: List<Fill>,
        areaByColor: Map<Int, Long>,
        background: Int,
        surface: Int?,
    ): Int? {
        val scores = LinkedHashMap<Int, Long>()

        for (fill in fills) {
            if (!isCandidate(fill.color, background, surface)) continue
            val weight = if (fill.clickable) CLICKABLE_WEIGHT else 1L
            scores[fill.color] = (scores[fill.color] ?: 0L) +
                Colors.chroma(fill.color) * weight * isqrt(fill.area)
        }

        // Accent colours often live in icons and text rather than in any element
        // fill, so the frame histogram gets a say too — at a lower weight,
        // because it cannot tell foreground from background.
        for ((color, area) in areaByColor) {
            if (!isCandidate(color, background, surface)) continue
            if (area < MIN_ACCENT_SAMPLES) continue
            scores[color] = (scores[color] ?: 0L) + Colors.chroma(color) * isqrt(area)
        }

        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
    }

    /**
     * Integer square root, used to weight a colour by how much of the screen it
     * covers without letting area swamp chroma.
     *
     * Area alone picks the background; chroma alone picks whichever single icon
     * is most saturated, however tiny. Taking the root of the area balances the
     * two, and staying in integers keeps the ordering exactly reproducible.
     */
    private fun isqrt(value: Long): Long {
        if (value <= 0L) return 0L
        var root = 0L
        var remaining = value
        var bit = 1L shl 40
        while (bit > remaining) bit = bit shr 2
        while (bit != 0L) {
            if (remaining >= root + bit) {
                remaining -= root + bit
                root = (root shr 1) + bit
            } else {
                root = root shr 1
            }
            bit = bit shr 2
        }
        return root
    }

    private fun isCandidate(color: Int, background: Int, surface: Int?): Boolean {
        if (Colors.isNeutral(color)) return false
        if (Colors.distanceSquared(color, background) <= roleDistanceSquared) return false
        if (surface != null && Colors.distanceSquared(color, surface) <= roleDistanceSquared) return false
        return true
    }

    /**
     * What the app actually prints on top of primary, read from the pixels
     * inside primary-filled elements. Falls back to whichever of black or white
     * carries more contrast — correct by construction when nothing is drawn
     * there to measure.
     */
    private fun pickOnPrimary(frames: List<Frame>, primary: Int): Int {
        val counts = LinkedHashMap<Int, Long>()
        for (frame in frames) {
            val regions = frame.nodes.filter { node ->
                node.bounds.size == 4 && node.area() >= minElementArea
            }
            for (node in regions) {
                if (!isFilledWith(frame.raster, node, primary)) continue
                for (y in node.bounds[1] until node.bounds[3]) {
                    for (x in node.bounds[0] until node.bounds[2]) {
                        if (!frame.raster.inBounds(x, y)) continue
                        val pixel = frame.raster.at(x, y)
                        if (Colors.alpha(pixel) < OPAQUE) continue
                        val key = quantise(pixel)
                        if (Colors.distanceSquared(key, primary) <= ON_PRIMARY_MIN_DISTANCE) continue
                        if (Colors.contrast(key, primary) < ON_PRIMARY_MIN_CONTRAST) continue
                        counts[key] = (counts[key] ?: 0L) + 1L
                    }
                }
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
            ?: Colors.bestOn(primary)
    }

    /** Whether an element's centre region really is the given colour. */
    private fun isFilledWith(raster: Raster, node: PrunedNode, color: Int): Boolean {
        val x = (node.bounds[0] + node.bounds[2]) / 2
        val y = (node.bounds[1] + node.bounds[3]) / 2
        if (!raster.inBounds(x, y)) return false
        return Colors.distanceSquared(quantise(raster.at(x, y)), color) <= mergeDistanceSquared
    }

    /**
     * Error is a red, and only a red — Material reserves that band for it. If
     * the scanned screens never showed an error state there is no error token,
     * and saying so is more useful than promoting the nearest warm colour.
     */
    private fun pickError(fills: List<Fill>, areaByColor: Map<Int, Long>, primary: Int?): Int? {
        val candidates = LinkedHashMap<Int, Long>()
        for (fill in fills) {
            if (!Colors.isErrorLike(fill.color)) continue
            candidates[fill.color] = (candidates[fill.color] ?: 0L) + fill.area
        }
        for ((color, area) in areaByColor) {
            if (!Colors.isErrorLike(color)) continue
            if (area < MIN_ACCENT_SAMPLES) continue
            candidates[color] = (candidates[color] ?: 0L) + area
        }
        // A red-branded app would otherwise report the same colour twice.
        primary?.let { candidates.keys.removeAll { key -> Colors.distanceSquared(key, it) <= mergeDistanceSquared } }
        return candidates.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
    }

    /**
     * The palette, most-used first, with near-duplicates merged and the named
     * roles guaranteed a place — a palette that omitted its own primary would
     * be a strange thing to hand a designer.
     */
    private fun buildPalette(areaByColor: Map<Int, Long>, roles: List<Int>): List<String> {
        val ordered = areaByColor.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
            .map { it.key }

        val chosen = ArrayList<Int>()
        for (color in roles) {
            if (chosen.none { Colors.distanceSquared(it, color) <= mergeDistanceSquared }) chosen += color
        }
        for (color in ordered) {
            if (chosen.size >= MAX_PALETTE) break
            if (chosen.none { Colors.distanceSquared(it, color) <= mergeDistanceSquared }) chosen += color
        }
        return chosen.take(MAX_PALETTE).map { Colors.toHex(it) }
    }

    // --- helpers ----------------------------------------------------------

    private class Frame(val raster: Raster, val nodes: List<PrunedNode>)

    private class Fill(val color: Int, val area: Long, val clickable: Boolean)

    private fun PrunedNode.area(): Int {
        if (bounds.size != 4) return 0
        val width = bounds[2] - bounds[0]
        val height = bounds[3] - bounds[1]
        return if (width > 0 && height > 0) width * height else 0
    }

    private fun PrunedNode.contains(x: Int, y: Int): Boolean =
        bounds.size == 4 && x >= bounds[0] && x < bounds[2] && y >= bounds[1] && y < bounds[3]

    private fun PrunedNode.isStrictlyInside(other: PrunedNode): Boolean {
        if (bounds.size != 4 || other.bounds.size != 4) return false
        val inside = bounds[0] >= other.bounds[0] && bounds[1] >= other.bounds[1] &&
            bounds[2] <= other.bounds[2] && bounds[3] <= other.bounds[3]
        return inside && area() < other.area()
    }

    private companion object {
        val EMPTY = ColorTokens(null, null, null, null, null, emptyList())
        const val OPAQUE = 250
        const val EDGE_INSET = 2
        const val MAX_PALETTE = 6
        const val SURFACE_MAX_CHROMA = 32
        const val CLICKABLE_WEIGHT = 3L
        const val MIN_ACCENT_SAMPLES = 200L
        const val ON_PRIMARY_MIN_DISTANCE = 2500
        const val ON_PRIMARY_MIN_CONTRAST = 250
    }
}
