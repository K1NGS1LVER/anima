package io.agents.anima.design

import io.agents.anima.core.ComponentToken
import io.agents.anima.core.ScreenObservation
import io.agents.anima.engine.PrunedNode

/**
 * Finds the components the app builds screens out of.
 *
 * A component is a *recurring* pattern, so this counts rather than describes:
 * anything seen once is an element, and only what repeats is a component worth
 * putting in a catalog. Each entry carries the screens it was seen on, which is
 * what makes the catalog auditable — a reader can go and look.
 *
 * Classification is structural, from the node tree, with the fill and radius
 * measured from the pixels. That ordering matters: the tree says what a thing
 * *is* (clickable, editable, a row in a list), and the pixels say what it
 * *looks like*. Guessing the kind from appearance alone is how a card becomes a
 * button.
 *
 * DETERMINISM (Y6): fixed kind order, medians rather than means, and a final
 * sort by name.
 */
class ComponentExtractor(
    /** A pattern must appear at least this often to be a component. */
    private val minInstances: Int = 2,
    /** Known density, when the caller has it. Otherwise inferred from the frame. */
    private val density: Density? = null,
) {

    fun extract(observations: List<ScreenObservation>): List<ComponentToken> {
        val instances = LinkedHashMap<String, MutableList<Instance>>()

        for ((index, observation) in observations.withIndex()) {
            val raster = observation.screenshot
                ?.let { runCatching { Png.decode(it) }.getOrNull() }
            val density = this.density
                ?: Density.infer(observation.screenSize.first, observation.screenSize.second)
            val nodes = observation.nodes.sortedBy { it.id }

            for (node in nodes) {
                val kind = classify(node, nodes) ?: continue
                val heightPx = node.bounds[3] - node.bounds[1]
                val fill = raster?.let { sampleFill(it, node, nodes) }
                instances.getOrPut(kind) { ArrayList() } += Instance(
                    screen = index,
                    heightDp = density.toDp(heightPx),
                    fill = fill,
                    text = fill?.let { Colors.bestOn(it) },
                    radiusDp = raster?.let { r ->
                        ShapeExtractor(density = density).measureRadius(r, node)?.let { density.toDp(it) }
                    },
                )
            }
        }

        return instances.entries
            .filter { it.value.size >= minInstances }
            .map { (kind, found) -> toToken(kind, found) }
            .sortedBy { it.name }
    }

    /**
     * What kind of component this node is, or null when it is not one.
     *
     * Order is significant: a search field is both editable and clickable, and
     * calling it an input rather than a button is the useful answer.
     */
    private fun classify(node: PrunedNode, all: List<PrunedNode>): String? {
        if (node.bounds.size != 4) return null
        val width = node.bounds[2] - node.bounds[0]
        val height = node.bounds[3] - node.bounds[1]
        if (width <= 0 || height <= 0) return null

        if (node.editable || node.className.contains("EditText")) return "Input"
        if (!node.clickable) return null
        if (node.className.contains("FloatingActionButton")) return "FloatingActionButton"
        if (node.className.contains("Button") || node.className.contains("ImageButton")) return "Button"

        // A list row is the repeated, tappable band that makes up a list. Having
        // siblings of the same width and height is what separates it from a
        // one-off banner that happens to be wide — a row is only a row because
        // there are others like it.
        val siblings = all.count { other ->
            other.bounds.size == 4 &&
                other.clickable &&
                (other.bounds[2] - other.bounds[0]) == width &&
                Math.abs((other.bounds[3] - other.bounds[1]) - height) <= ROW_HEIGHT_TOLERANCE_PX
        }
        return if (siblings >= minInstances) "ListRow" else "Card"
    }

    /**
     * The element's own fill, with its children masked out.
     *
     * Sampling the single centre pixel reads the element's *text* whenever text
     * runs through the middle, which is most labelled controls. That is how the
     * first rebuild came out with a navy search bar and a navy Storage row.
     */
    private fun sampleFill(raster: Raster, node: PrunedNode, all: List<PrunedNode>): Int? =
        Sampling.modalFill(raster, node, all) { quantise(it) }

    /** Matches the colour cells the palette uses, so fills land on palette entries. */
    private fun quantise(argb: Int): Int {
        fun snap(channel: Int): Int = ((channel shr 3) shl 3) or 0x04
        return Colors.rgb(snap(Colors.red(argb)), snap(Colors.green(argb)), snap(Colors.blue(argb)))
    }

    /**
     * One catalog entry from all its sightings.
     *
     * Medians, not means: one oddly sized instance should not move the reported
     * height, and a median of an even-sized list takes the lower middle so the
     * choice never depends on ordering.
     */
    private fun toToken(kind: String, found: List<Instance>): ComponentToken {
        val heights = found.map { it.heightDp }.sorted()
        val radii = found.mapNotNull { it.radiusDp }.sorted()
        val fill = modal(found.mapNotNull { it.fill })

        return ComponentToken(
            name = kind,
            fill = fill?.let { Colors.toHex(it) },
            text = fill?.let { Colors.toHex(Colors.bestOn(it)) },
            radiusDp = radii.medianOrNull(),
            heightDp = heights.medianOrNull(),
            // The field is named for the screens it was seen on, so that is what
            // it counts — not the number of instances, which would report 10 for
            // a single screen holding a list of 10 rows.
            seenOn = found.map { it.screen }.distinct().size,
        )
    }

    private fun modal(values: List<Int>): Int? = values
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()
        ?.key

    private fun List<Int>.medianOrNull(): Int? = if (isEmpty()) null else this[(size - 1) / 2]

    private class Instance(
        val screen: Int,
        val heightDp: Int,
        val fill: Int?,
        val text: Int?,
        val radiusDp: Int?,
    )

    private companion object {
        const val ROW_HEIGHT_TOLERANCE_PX = 12
    }
}
