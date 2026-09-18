package io.agents.anima.design

import io.agents.anima.engine.PrunedNode

/**
 * Reading an element's own fill out of a screenshot.
 *
 * Shared because getting it wrong is subtle and looks fine until you render it:
 * a single sample at the element's centre lands on the element's *text* about as
 * often as on its background, so a search field comes back dark slate instead of
 * white, and every rebuild drawn from it has a navy search bar.
 *
 * The fix is to sample a grid, skip points covered by a child element, and take
 * the mode — with a majority requirement, so a patterned or image-filled element
 * reports nothing rather than a colour picked out of a gradient.
 */
internal object Sampling {

    /** Points per axis. Odd, so the exact centre is never the only sample. */
    private const val GRID = 7
    private const val OPAQUE = 250

    /**
     * The modal colour inside [node] with its children masked out, or null when
     * the element has no single dominant colour.
     *
     * [quantise] maps a raw pixel onto the caller's colour cells so that
     * antialiasing does not split one visual colour across many samples.
     */
    fun modalFill(
        raster: Raster,
        node: PrunedNode,
        others: List<PrunedNode>,
        quantise: (Int) -> Int,
    ): Int? {
        if (node.bounds.size != 4) return null
        val left = node.bounds[0]
        val top = node.bounds[1]
        val width = node.bounds[2] - left
        val height = node.bounds[3] - top
        if (width <= 0 || height <= 0) return null

        val children = others.filter { it !== node && it.isStrictlyInside(node) }
        val counts = LinkedHashMap<Int, Int>()

        for (row in 1..GRID) {
            for (column in 1..GRID) {
                val x = left + width * column / (GRID + 1)
                val y = top + height * row / (GRID + 1)
                if (!raster.inBounds(x, y)) continue
                if (children.any { it.contains(x, y) }) continue
                val pixel = raster.at(x, y)
                if (Colors.alpha(pixel) < OPAQUE) continue
                val key = quantise(pixel)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }

        val modal = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .firstOrNull() ?: return null
        val total = counts.values.sum()
        return if (modal.value * 2 >= total) modal.key else null
    }

    fun PrunedNode.contains(x: Int, y: Int): Boolean =
        bounds.size == 4 && x >= bounds[0] && x < bounds[2] && y >= bounds[1] && y < bounds[3]

    fun PrunedNode.area(): Int {
        if (bounds.size != 4) return 0
        val width = bounds[2] - bounds[0]
        val height = bounds[3] - bounds[1]
        return if (width > 0 && height > 0) width * height else 0
    }

    fun PrunedNode.isStrictlyInside(other: PrunedNode): Boolean {
        if (bounds.size != 4 || other.bounds.size != 4) return false
        val inside = bounds[0] >= other.bounds[0] && bounds[1] >= other.bounds[1] &&
            bounds[2] <= other.bounds[2] && bounds[3] <= other.bounds[3]
        return inside && area() < other.area()
    }
}
