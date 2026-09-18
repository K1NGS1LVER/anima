package io.agents.anima.design

import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.SpacingTokens
import io.agents.anima.engine.PrunedNode

/**
 * Recovers the app's spacing system from element geometry alone.
 *
 * No pixels, no model, no device: the input is `PrunedNode.bounds`, which is why
 * this is the first thing in `:design` that can be built and trusted.
 *
 * The idea: an app laid out on an 8dp grid leaves 8dp-multiple gaps between
 * things far more often than chance. So measure every gap and margin on every
 * screen, convert to dp, and ask which candidate base explains more of them.
 *
 * DETERMINISM: integer arithmetic throughout, a fixed candidate order, and a
 * fixed tie-break. The same observations always produce the same tokens.
 */
class SpacingExtractor(
    /** Bases to test, in the order they are tried. 8 first: a tie goes to 8. */
    private val candidates: List<Int> = listOf(8, 4),
    /** A gap may miss its multiple by this much and still count. Rendering rounds. */
    private val toleranceDp: Int = 1,
    /** Gaps beyond this are section breaks or empty space, not grid steps. */
    private val maxGapDp: Int = 96,
    /** A value must appear this many times to earn a place in the scale. */
    private val minOccurrences: Int = 2,
    /**
     * Density of the frames, when the caller knows it.
     *
     * `ScreenObservation` does not carry density (Q6 against `Contracts.kt`), so
     * the default is to infer it from the frame size — a guess that is usually
     * right and sometimes a whole bucket wrong. Anything that *does* know the
     * real value, a test with a device's `wm density` in hand most of all,
     * should pass it rather than let the guess stand.
     */
    private val density: Density? = null,
) {

    fun extract(observations: List<ScreenObservation>): SpacingTokens {
        val measured = observations.flatMap { measure(it) }
        if (measured.size < MIN_EVIDENCE) {
            // Not enough geometry to infer anything. An empty scale is the
            // honest signal for that; a fabricated 4/8/16/24 would look like a
            // finding and be a guess.
            return SpacingTokens(baseDp = DEFAULT_BASE, scale = emptyList())
        }

        val base = candidates.maxByOrNull { candidate -> fitScore(measured, candidate) }
            ?: DEFAULT_BASE

        val counts = LinkedHashMap<Int, Int>()
        for (dp in measured) {
            val snapped = snap(dp, base) ?: continue
            if (snapped <= 0) continue
            counts[snapped] = (counts[snapped] ?: 0) + 1
        }

        val scale = counts.entries
            .filter { it.value >= minOccurrences }
            .map { it.key }
            .sorted()
            .take(MAX_SCALE_STEPS)

        return SpacingTokens(baseDp = base, scale = scale)
    }

    /** Every gap and margin on one screen, in dp. */
    internal fun measure(observation: ScreenObservation): List<Int> {
        val widthPx = observation.screenSize.first
        val density = this.density
            ?: Density.infer(widthPx, observation.screenSize.second)
        val nodes = observation.nodes.filter { it.bounds.size == 4 && it.isReal() }
        if (nodes.isEmpty()) return emptyList()

        val out = ArrayList<Int>()

        // Vertical gaps between elements that share horizontal space — the
        // stacked-rows case, which is most of a phone UI.
        val byTop = nodes.sortedWith(compareBy({ it.top }, { it.left }, { it.id }))
        for (i in byTop.indices) {
            val a = byTop[i]
            for (j in i + 1 until byTop.size) {
                val b = byTop[j]
                if (!overlapsHorizontally(a, b)) continue
                val gap = b.top - a.bottom
                if (gap <= 0) continue
                val dp = density.toDp(gap)
                if (dp in 1..maxGapDp) out.add(dp)
                break // nearest neighbour below only; further ones are sums
            }
        }

        // Horizontal gaps between elements that share a row.
        val byLeft = nodes.sortedWith(compareBy({ it.left }, { it.top }, { it.id }))
        for (i in byLeft.indices) {
            val a = byLeft[i]
            for (j in i + 1 until byLeft.size) {
                val b = byLeft[j]
                if (!overlapsVertically(a, b)) continue
                val gap = b.left - a.right
                if (gap <= 0) continue
                val dp = density.toDp(gap)
                if (dp in 1..maxGapDp) out.add(dp)
                break
            }
        }

        // Side margins: the distance from each screen edge to the nearest
        // element. Usually the single most reliable multiple on the screen.
        for (n in nodes) {
            val leftDp = density.toDp(n.left)
            if (leftDp in 1..maxGapDp) out.add(leftDp)
            val rightDp = density.toDp(widthPx - n.right)
            if (rightDp in 1..maxGapDp) out.add(rightDp)
        }

        return out
    }

    /** How much of the evidence a candidate base explains, 0..1, scaled to an Int. */
    internal fun fitScore(measured: List<Int>, base: Int): Int {
        if (measured.isEmpty()) return 0
        val hits = measured.count { snap(it, base) != null }
        return hits * 1000 / measured.size
    }

    /** The nearest multiple of [base] within tolerance, or null if this value is off-grid. */
    internal fun snap(dp: Int, base: Int): Int? {
        val nearest = Math.round(dp.toDouble() / base).toInt() * base
        return if (Math.abs(nearest - dp) <= toleranceDp) nearest else null
    }

    private fun overlapsHorizontally(a: PrunedNode, b: PrunedNode): Boolean =
        a.left < b.right && b.left < a.right

    private fun overlapsVertically(a: PrunedNode, b: PrunedNode): Boolean =
        a.top < b.bottom && b.top < a.bottom

    /** Zero-area nodes carry no geometry and would poison the histogram. */
    private fun PrunedNode.isReal(): Boolean = right > left && bottom > top

    private val PrunedNode.left: Int get() = bounds[0]
    private val PrunedNode.top: Int get() = bounds[1]
    private val PrunedNode.right: Int get() = bounds[2]
    private val PrunedNode.bottom: Int get() = bounds[3]

    private companion object {
        const val DEFAULT_BASE = 8
        const val MIN_EVIDENCE = 3
        const val MAX_SCALE_STEPS = 8
    }
}
