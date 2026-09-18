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
        // Tokens of 1-2 characters ("a", "my", "to") match almost any label --
        // "a" alone matches "Storage" -- so they only add noise to the score.
        val tokens = wordRegex.findAll(goal).map { it.value.lowercase() }.filter { it.length > 2 }.toList()
        if (tokens.isEmpty()) return null

        var bestNode: PrunedNode? = null
        var bestScore = 0
        var bestArea = Long.MAX_VALUE
        val toggleGoal = wantsToggle(goal)

        for (n in nodes) {
            var score = 0
            val candidate = "${n.text ?: ""} ${n.contentDesc ?: ""} ${n.resourceId ?: ""}".lowercase()
            val candidateFlat = flatten(candidate)
            val labels = setOf(flatten(n.text), flatten(n.contentDesc))
            for (t in tokens) {
                val tFlat = flatten(t)
                if (candidate.contains(t) || candidateFlat.contains(tFlat)) score += 2
                // A label that *is* the goal word beats one that merely mentions
                // it. On a real Wi-Fi settings screen the toggle is labelled
                // "Wi-Fi", while every network row carries a content-desc like
                // "MyNetwork,Connected,Wi-Fi signal full." -- without this the
                // agent taps a network instead of the switch.
                if (tFlat.isNotEmpty() && tFlat in labels) score += 3
            }
            if (n.clickable && score > 0) score += 1
            // For a toggle goal, prefer the label sitting in a row that actually
            // owns a switch. A settings screen titled "Wi-Fi" carries the word in
            // its action bar too, and tapping that does nothing.
            if (score > 0 && toggleGoal && enclosesToggle(actionable(n, nodes), nodes)) score += 3
            // On a tie, the tighter element wins: with label inheritance a
            // whole-screen container can carry the same label as the row inside
            // it, and the row is what a human would tap.
            val area = (n.bounds[2] - n.bounds[0]).toLong() * (n.bounds[3] - n.bounds[1]).toLong()
            if (score > bestScore || (score == bestScore && score > 0 && area < bestArea)) {
                bestScore = score
                bestNode = n
                bestArea = area
            }
        }

        val target = bestNode ?: return null
        return PlannedAction("tap", actionable(target, nodes), null)
    }

    companion object {
        private val nonAlnum = Regex("[^a-z0-9]+")

        /**
         * Strips punctuation so goal words survive real-world label typography.
         *
         * Android labels are written for humans: "Wi-Fi", "Do not disturb",
         * "Bluetooth & devices". A plain substring test fails every one of those
         * against a goal like "toggle wifi" -- observed on a physical device,
         * where it caused the task to find no target at all.
         */
        fun flatten(text: String?): String = nonAlnum.replace((text ?: "").lowercase(), "")

        /**
         * Widget classes that carry an on/off state. MIUI renders the Wi-Fi master
         * switch as a CheckBox, AOSP as a Switch, others as a SlidingButton.
         */
        private val toggleClasses =
            listOf("switch", "togglebutton", "checkbox", "compoundbutton", "slidingbutton")

        private val toggleVerbs =
            listOf("toggle", "turn on", "turn off", "turn ", "enable", "disable", "switch")

        fun wantsToggle(goal: String): Boolean {
            val g = goal.lowercase()
            return toggleVerbs.any { g.contains(it) }
        }

        /** True when [container] geometrically encloses a switch-like widget. */
        fun enclosesToggle(container: PrunedNode, nodes: List<PrunedNode>): Boolean {
            val (x1, y1, x2, y2) = listOf(
                container.bounds[0], container.bounds[1], container.bounds[2], container.bounds[3],
            )
            for (n in nodes) {
                if (n === container) continue
                val cls = n.className.lowercase()
                if (toggleClasses.none { cls.contains(it) }) continue
                if (n.center[0] in x1..x2 && n.center[1] in y1..y2) return true
            }
            return false
        }

        /**
         * Redirects a matched label to the row that actually handles the tap.
         *
         * The node carrying the text is usually an inert TextView nested inside a
         * clickable container, so tapping the label itself does nothing. Falls back
         * to the smallest clickable node whose bounds contain this one -- the
         * closest actionable ancestor, without needing parent pointers.
         */
        fun actionable(node: PrunedNode, nodes: List<PrunedNode>): PrunedNode {
            if (node.clickable) return node
            val (cx, cy) = node.center[0] to node.center[1]
            var best: PrunedNode? = null
            var bestArea = Long.MAX_VALUE
            for (n in nodes) {
                if (!n.clickable) continue
                val (x1, y1, x2, y2) = listOf(n.bounds[0], n.bounds[1], n.bounds[2], n.bounds[3])
                if (cx in x1..x2 && cy in y1..y2) {
                    val area = (x2 - x1).toLong() * (y2 - y1).toLong()
                    if (area < bestArea) {
                        best = n
                        bestArea = area
                    }
                }
            }
            return best ?: node
        }
    }
}
