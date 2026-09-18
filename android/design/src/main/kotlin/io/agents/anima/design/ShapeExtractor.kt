package io.agents.anima.design

import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ShapeTokens
import io.agents.anima.engine.PrunedNode

/**
 * Measures the app's corner radii from the screenshots.
 *
 * Radius is not in the node tree at all — it is a drawable property, invisible
 * to accessibility. But it is plainly visible in the pixels, so it is measured
 * geometrically: walk in along a corner's diagonal until the element's own fill
 * starts, and that inset determines the radius.
 *
 * For a quarter-circle of radius `r`, the arc's closest approach to the corner
 * along the diagonal is `r - r/sqrt(2)`, so an observed inset `d` implies
 * `r = d / (1 - 1/sqrt(2))`, about `3.414 * d`. A square corner gives `d = 0`
 * and a radius of zero, which is the correct answer for a square corner.
 *
 * DETERMINISM (Y6): fixed scan order, integer results, explicit tie-breaks.
 */
class ShapeExtractor(
    /** How far along the diagonal to look before concluding the corner is not rounded. */
    private val maxInsetPx: Int = 96,
    /** Colour tolerance (squared RGB) for "this pixel is the element's fill". */
    private val fillToleranceSquared: Int = 300,
    /**
     * How different the pixel outside a corner must be before there is an edge
     * worth measuring.
     *
     * This has to be far smaller than [fillToleranceSquared] or the common case
     * defeats it: a Material 3 card is a near-white on a faintly tinted window,
     * a difference of a few units per channel. Requiring a strong contrast here
     * silently reported every rounded card on such a screen as having no
     * corners at all.
     */
    private val minEdgeContrastSquared: Int = 60,
    /** A radius must be seen this often to be a token rather than an artefact. */
    private val minOccurrences: Int = 2,
    /** Elements smaller than this cannot be measured reliably. */
    private val minSidePx: Int = 64,
    /** How far from an element's centre to look for the edge of the shape it sits in. */
    private val maxRegionPx: Int = 600,
    /** Known density, when the caller has it. Otherwise inferred from the frame. */
    private val density: Density? = null,
) {

    fun extract(observations: List<ScreenObservation>): ShapeTokens {
        val radii = ArrayList<Int>()

        for (observation in observations) {
            val bytes = observation.screenshot ?: continue
            val raster = runCatching { Png.decode(bytes) }.getOrNull() ?: continue
            val density = this.density
                ?: Density.infer(observation.screenSize.first, observation.screenSize.second)

            val nodes = observation.nodes
                .filter { it.measurable() }
                .sortedBy { it.id }

            for (node in nodes) {
                val radiusPx = measureRadius(raster, node) ?: continue
                val dp = density.toDp(radiusPx)
                if (dp in 0..MAX_RADIUS_DP) radii += snap(dp)
            }
        }

        if (radii.isEmpty()) return ShapeTokens(radiusDp = emptyList())

        val counts = LinkedHashMap<Int, Int>()
        for (dp in radii) counts[dp] = (counts[dp] ?: 0) + 1

        val kept = counts.entries
            .filter { it.value >= minOccurrences }
            .map { it.key }
            .sorted()

        // Everything square is a real finding, not an empty one: it says the app
        // has no rounding, which a rebuild needs to know.
        return ShapeTokens(radiusDp = kept.take(MAX_TOKENS))
    }

    /**
     * The corner radius of one element in pixels, or null when it has no
     * measurable edge.
     *
     * Only the top-left corner is measured. Android shapes are overwhelmingly
     * uniform across corners, and measuring one corner keeps a mixed-radius
     * shape from contributing four different numbers that are each half-true.
     */
    internal fun measureRadius(raster: Raster, node: PrunedNode): Int? {
        val left = node.bounds[0]
        val top = node.bounds[1]
        val right = node.bounds[2]
        val bottom = node.bounds[3]
        if (right <= left || bottom <= top) return null

        // The fill is read from the middle of the element, far from any edge.
        val centreX = (left + right) / 2
        val centreY = (top + bottom) / 2
        if (!raster.inBounds(centreX, centreY)) return null
        val fill = raster.at(centreX, centreY)
        if (Colors.alpha(fill) < OPAQUE) return null

        // WHERE THE SHAPE ACTUALLY IS. A node's bounds are its touch target, not
        // the rectangle the app drew: on the reference Settings capture every
        // list row reports the full 1080px width while the card it draws is
        // inset by 42px on each side. Probing at the bounds corner therefore
        // measures the gap between cards, or falls off the frame entirely, and
        // reports no rounding on a screen made entirely of rounded cards. So the
        // drawn edge is found first, by walking out from the centre until the
        // fill stops.
        // The scan is deliberately not bounded by the node: a Material 3 list
        // draws one rounded card around a *group* of rows, so the rounded corner
        // belonging to a given row is often well above that row's own top edge.
        // Bounding the search at the node reported no radius for exactly the
        // rows that sit inside a rounded group. Every row of a group finds the
        // same card corner, which is the right answer for all of them.
        //
        // Several probes per direction, keeping the outermost answer, because a
        // single scan through the middle of a row runs into the row's own icon
        // and stops there. On the reference Settings capture that put the "left
        // edge" at the icon, 147px inside the card, and the corner measured from
        // it was a flat patch of card face — a radius of zero for every rounded
        // card on the screen. Content can only ever stop a scan early, so the
        // outermost of several probes is the real edge.
        val drawnLeft = outermostEdge(raster, fill, centreX, centreY, top, bottom, horizontal = true)
            ?: return null
        val drawnTop = outermostEdge(raster, fill, centreX, centreY, left, right, horizontal = false)
            ?: return null

        // Whatever sits outside that edge has to contrast with the fill, or
        // there is no visible corner and any radius would be invented.
        val outsideX = (drawnLeft - EDGE_PROBE).coerceAtLeast(0)
        val outsideY = (drawnTop - EDGE_PROBE).coerceAtLeast(0)
        if (!raster.inBounds(outsideX, outsideY)) return null
        if (Colors.distanceSquared(fill, raster.at(outsideX, outsideY)) <= minEdgeContrastSquared) return null

        val limit = minOf(maxInsetPx, (centreX - drawnLeft), (centreY - drawnTop))
        for (inset in 0..limit) {
            val x = drawnLeft + inset
            val y = drawnTop + inset
            if (!raster.inBounds(x, y)) return null
            if (Colors.distanceSquared(raster.at(x, y), fill) <= fillToleranceSquared) {
                return Math.round(inset * DIAGONAL_TO_RADIUS).toInt()
            }
        }
        return null
    }

    /**
     * The outermost edge found by probing several parallel lines.
     *
     * [from]..[to] is the element's span across the scan direction; the probes
     * are spread evenly through it so at least one of them misses the row's
     * content. Probes that find nothing are ignored rather than failing the
     * measurement, since a probe that runs off the frame says nothing about
     * where the shape ends.
     */
    private fun outermostEdge(
        raster: Raster,
        fill: Int,
        centreX: Int,
        centreY: Int,
        from: Int,
        to: Int,
        horizontal: Boolean,
    ): Int? {
        var best: Int? = null
        for (index in 1..EDGE_PROBES) {
            val offset = from + (to - from) * index / (EDGE_PROBES + 1)
            val found = if (horizontal) {
                scanEdge(raster, centreX, offset, fill, stepX = -1, stepY = 0, limit = maxRegionPx)
            } else {
                scanEdge(raster, offset, centreY, fill, stepX = 0, stepY = -1, limit = maxRegionPx)
            } ?: continue
            if (best == null || found < best!!) best = found
        }
        return best
    }

    /**
     * Walks from the centre outwards until the fill stops for good, returning
     * the last coordinate that is still fill — the drawn edge.
     *
     * The confirmation window is the point. A grouped list draws a hairline
     * divider between its rows in the same colour family as the border, and a
     * scan that stopped at the first non-fill pixel took that divider for the
     * edge of the card. The corner it then measured was in the middle of a flat
     * card face, which is fill, which reported a radius of zero for every
     * rounded card on the screen. A real edge stays non-fill; a divider does not.
     *
     * Returns null when the fill runs all the way to the node's bounds, because
     * then the edge is outside what was searched and the corner it belongs to
     * cannot be attributed to this element.
     */
    private fun scanEdge(
        raster: Raster,
        fromX: Int,
        fromY: Int,
        fill: Int,
        stepX: Int,
        stepY: Int,
        limit: Int,
    ): Int? {
        if (limit <= 0) return null
        for (step in 1..limit) {
            val x = fromX + stepX * step
            val y = fromY + stepY * step
            if (!raster.inBounds(x, y)) return null
            if (Colors.distanceSquared(raster.at(x, y), fill) <= fillToleranceSquared) continue

            var confirmed = true
            for (ahead in 1..EDGE_CONFIRM) {
                val aheadX = x + stepX * ahead
                val aheadY = y + stepY * ahead
                if (!raster.inBounds(aheadX, aheadY)) break
                if (Colors.distanceSquared(raster.at(aheadX, aheadY), fill) <= fillToleranceSquared) {
                    confirmed = false
                    break
                }
            }
            if (!confirmed) continue

            val lastFillX = x - stepX
            val lastFillY = y - stepY
            return if (stepX != 0) lastFillX else lastFillY
        }
        return null
    }

    /**
     * Rounds to the radii apps actually use.
     *
     * Antialiasing puts the measured inset a pixel either side of the truth, and
     * an unsnapped scale would report 15dp and 17dp as two different tokens for
     * what the designer drew once as 16.
     */
    private fun snap(dp: Int): Int {
        if (dp <= 1) return 0
        return COMMON_RADII.minByOrNull { candidate ->
            val distance = Math.abs(candidate - dp)
            // Ties go to the smaller radius, so the tie-break is total.
            distance * 1000 + candidate
        } ?: dp
    }

    private fun PrunedNode.measurable(): Boolean {
        if (bounds.size != 4) return false
        return (bounds[2] - bounds[0]) >= minSidePx && (bounds[3] - bounds[1]) >= minSidePx
    }

    private companion object {
        /** 1 / (1 - 1/sqrt(2)): converts a diagonal inset into a radius. */
        const val DIAGONAL_TO_RADIUS = 3.4142135623730951

        const val OPAQUE = 250
        const val EDGE_PROBE = 2

        /** Pixels a non-fill run must persist to count as an edge and not a divider. */
        const val EDGE_CONFIRM = 6

        /** Parallel probes per direction, so a row's own icon cannot pass for its edge. */
        const val EDGE_PROBES = 5
        const val MAX_RADIUS_DP = 64
        const val MAX_TOKENS = 5

        /** The Material shape scale, which is what most apps land on. */
        val COMMON_RADII = listOf(0, 4, 8, 12, 16, 20, 24, 28, 32, 40, 48)
    }
}
