package io.agents.anima.engine

/** A single grounded action produced by a planner. */
data class PlannedAction(
    val action: String,        // "tap" | "input_text" | "key"
    val target: PrunedNode,
    val value: String? = null,
)

/**
 * Cold-start / drift-recovery grounding.
 *
 * The Kotlin engine is strictly on-device: there is no network planner here, by design.
 */
interface Planner {
    fun planStep(goal: String, domJson: String, nodes: List<PrunedNode>): PlannedAction?
}

/**
 * Deterministic keyword-overlap planner, ported from `HeuristicPlanner` in `anima.py`.
 *
 * Scores each node by how many goal tokens appear in its text / content-desc / resource-id,
 * with a small bonus for clickable nodes, and taps the winner. Fully offline and deterministic,
 * which is exactly what the live demo needs.
 */
class HeuristicPlanner : Planner {

    private val wordRegex = Regex("""\w+""")

    override fun planStep(goal: String, domJson: String, nodes: List<PrunedNode>): PlannedAction? {
        val tokens = wordRegex.findAll(goal).map { it.value.lowercase() }.toList()
        if (tokens.isEmpty()) return null

        var bestNode: PrunedNode? = null
        var bestScore = 0

        for (n in nodes) {
            var score = 0
            val candidate = "${n.text ?: ""} ${n.contentDesc ?: ""} ${n.resourceId ?: ""}".lowercase()
            for (t in tokens) {
                if (candidate.contains(t)) score += 2
            }
            if (n.clickable && score > 0) score += 1
            if (score > bestScore) {
                bestScore = score
                bestNode = n
            }
        }

        val target = bestNode ?: return null
        return PlannedAction("tap", target, null)
    }
}
