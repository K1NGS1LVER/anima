package io.agents.anima.design

import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.TypeToken
import io.agents.anima.engine.PrunedNode

/**
 * Recovers the type scale from the screens.
 *
 * WHAT THE TREE ACTUALLY EXPOSES, and why this is measured rather than read:
 * a `uiautomator` dump carries no `textSize`, no `typeface` and no `textStyle`.
 * Those attributes exist on the `View`, not on the accessibility node the dump
 * is built from. So a claim of "family Inter, 28sp, weight 700" cannot be read
 * off the tree — it has to be inferred from the one typographic signal the tree
 * does carry, which is the height of a text node's bounds.
 *
 * That inference is sound for size: a single-line text view's height tracks its
 * font size closely. It is not sound for family or weight, so those are left
 * null rather than guessed, and [TypeToken.family] stays null until a VLM fills
 * it in. Reporting a font name this module cannot see would be a fabrication in
 * a pack whose whole value is being trustworthy about the app it scanned.
 *
 * DETERMINISM (Y6): integer bucketing and sorts with explicit tie-breaks.
 */
class TypographyExtractor(
    /** Text heights are clustered; two sizes within this many sp are one role. */
    private val clusterToleranceSp: Int = 2,
    /** A role must be seen this many times to be part of the scale. */
    private val minOccurrences: Int = 2,
    /** Known density, when the caller has it. Otherwise inferred from the frame. */
    private val density: Density? = null,
) {

    fun extract(observations: List<ScreenObservation>): List<TypeToken> {
        val sizes = ArrayList<Int>()
        for (observation in observations) {
            val density = densityFor(observation)
            for (node in observation.nodes) {
                if (!node.carriesText()) continue
                val sp = estimateSizeSp(node, density) ?: continue
                sizes += sp
            }
        }
        if (sizes.isEmpty()) return emptyList()

        val clusters = cluster(sizes)
            .filter { it.count >= minOccurrences || sizes.size < minOccurrences * 2 }
            .sortedWith(compareByDescending<Cluster> { it.sizeSp }.thenByDescending { it.count })
        if (clusters.isEmpty()) return emptyList()

        return assignRoles(clusters)
    }

    /**
     * Font size in sp from a text node's height.
     *
     * A text view's height is its line box: the font's ascent-to-descent plus
     * padding. Dividing by [LINE_BOX_RATIO] backs out the point size. Nodes
     * tall enough to be multi-line are skipped rather than divided, because
     * their height measures the paragraph and not the type.
     */
    private fun estimateSizeSp(node: PrunedNode, density: Density): Int? {
        if (node.bounds.size != 4) return null
        val heightPx = node.bounds[3] - node.bounds[1]
        if (heightPx <= 0) return null
        val heightDp = density.toDp(heightPx)
        if (heightDp < MIN_LINE_DP || heightDp > MAX_LINE_DP) return null

        val text = node.text ?: node.contentDesc ?: return null
        if (text.isBlank()) return null
        // Rough multi-line guard: a box far taller than one line of its own
        // width's worth of text is a paragraph, and its height says nothing
        // about the font size.
        if (heightDp > MAX_SINGLE_LINE_DP) return null

        val sp = Math.round(heightDp / LINE_BOX_RATIO).toInt()
        return if (sp in MIN_SP..MAX_SP) sp else null
    }

    /** Greedy one-dimensional clustering. Deterministic: input is sorted first. */
    private fun cluster(sizes: List<Int>): List<Cluster> {
        val sorted = sizes.sorted()
        val clusters = ArrayList<Cluster>()
        var start = 0
        while (start < sorted.size) {
            var end = start
            while (end + 1 < sorted.size && sorted[end + 1] - sorted[start] <= clusterToleranceSp) end++
            val members = sorted.subList(start, end + 1)
            // The median is the representative: it does not drift when one
            // oversized heading joins the cluster, and on an even-sized cluster
            // the lower middle is taken so the choice is not a coin flip.
            clusters += Cluster(sizeSp = members[(members.size - 1) / 2], count = members.size)
            start = end + 1
        }
        return clusters
    }

    /**
     * Names the clusters, largest first.
     *
     * The roles are a fixed ladder rather than thresholds on absolute size,
     * because "display" means the biggest type this app uses, and an app with a
     * restrained scale still has a largest size. Caption is the exception: it is
     * only named when it really is smaller than body.
     */
    private fun assignRoles(clusters: List<Cluster>): List<TypeToken> {
        val roles = when (clusters.size) {
            1 -> listOf("body")
            2 -> listOf("title", "body")
            3 -> listOf("display", "title", "body")
            else -> listOf("display", "title", "body", "caption")
        }
        val chosen = if (clusters.size <= roles.size) {
            clusters
        } else {
            // More clusters than roles: keep the most-used ones so the scale
            // reflects the app's actual habits, then re-order by size.
            clusters.sortedWith(compareByDescending<Cluster> { it.count }.thenByDescending { it.sizeSp })
                .take(roles.size)
                .sortedWith(compareByDescending<Cluster> { it.sizeSp }.thenByDescending { it.count })
        }

        return chosen.mapIndexed { index, cluster ->
            TypeToken(
                role = roles[index],
                family = null,   // not observable from the node tree; see the class comment
                sizeSp = cluster.sizeSp.toDouble(),
                weight = weightFor(roles[index]),
            )
        }
    }

    /**
     * Weight is not observable either. These are the Material defaults for each
     * role, which is a documented convention rather than a measurement — the
     * honest reading of this field is "what the role usually is", and the VLM
     * pass is what can correct it.
     */
    private fun weightFor(role: String): Int = when (role) {
        "display" -> 700
        "title" -> 600
        "caption" -> 400
        else -> 400
    }

    private fun densityFor(observation: ScreenObservation): Density =
        density ?: Density.infer(observation.screenSize.first, observation.screenSize.second)

    /**
     * Whether this node's height measures a line of type.
     *
     * The class check is what makes the rest of this trustworthy. A container
     * repeats its children's text as its own — on the reference Settings capture
     * the search bar is a 72dp `LinearLayout` labelled "Search Settings" wrapping
     * a 27dp `TextView` with the same string. Measuring the container reports a
     * 53sp display face that the app does not have. Only a real text view's
     * height is its line box.
     */
    private fun PrunedNode.carriesText(): Boolean {
        val label = text ?: contentDesc
        if (label.isNullOrBlank() || editable) return false
        return className.contains("TextView")
    }

    private class Cluster(val sizeSp: Int, val count: Int)

    private companion object {
        /**
         * Line box height divided by font size for the default Android
         * typeface. Roboto's ascent-to-descent is about 1.16em and text views
         * add a little padding, so a 14sp label lands near 19dp tall.
         *
         * Calibrated against the committed Settings fixture, where Material 3
         * puts list titles at 16sp and summaries at 14sp: those measure 21.7dp
         * and 18.9dp, giving 1.36 and 1.35. The earlier 1.45 read both of them
         * one sp short.
         */
        const val LINE_BOX_RATIO = 1.35

        const val MIN_LINE_DP = 10
        const val MAX_LINE_DP = 96
        const val MAX_SINGLE_LINE_DP = 80
        const val MIN_SP = 8
        const val MAX_SP = 60
    }
}
