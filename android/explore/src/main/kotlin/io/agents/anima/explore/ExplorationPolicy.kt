package io.agents.anima.explore

import io.agents.anima.core.ElementAction
import io.agents.anima.core.ScreenObservation
import io.agents.anima.engine.HeuristicPlanner
import io.agents.anima.engine.PrunedNode

/**
 * What to do on a screen, and in what order.
 *
 * The scoring is deliberately boring and deterministic. A learned or sampled
 * policy would explore better on average and would make the byte-identical
 * requirement impossible to meet, which is a bad trade: a pack that is 10%
 * more complete but different on every run fails the headline acceptance test.
 *
 * Every weight below is an ordering hint, not a probability. Ties are broken by
 * [Candidate.orderKey], never by discovery order.
 */
object ExplorationPolicy {

    // Navigation first: these are what actually reveal new screens.
    private const val SCORE_NAV = 60
    private const val SCORE_LIST_ITEM = 45
    private const val SCORE_BUTTON = 40
    private const val SCORE_INPUT = 35
    private const val SCORE_TOGGLE = 15
    private const val SCORE_GENERIC_CLICKABLE = 25
    private const val SCORE_SCROLL = 30

    /** Shallower screens first, so the map fills out broadly before it goes deep. */
    private const val DEPTH_PENALTY = 4

    private val NAV_HINTS = listOf(
        "menu", "drawer", "navigation", "tab", "more", "settings", "profile",
        "account", "home", "back", "next", "continue", "getstarted", "skip",
        "signin", "login", "register", "signup", "verify", "submit",
    )

    private val NAV_CLASSES = listOf(
        "BottomNavigationView", "TabLayout", "NavigationView", "Toolbar", "ActionBar",
    )

    private val LIST_ITEM_CLASSES = listOf("ListView", "RecyclerView", "GridView")

    /**
     * Every action worth trying on this screen, already scored.
     *
     * Destructive controls are filtered out here rather than at execution time
     * so they are never even queued: a control that is not in the frontier
     * cannot be reached by a later bug in the loop.
     */
    fun candidates(
        screenId: String,
        observation: ScreenObservation,
        depth: Int,
    ): List<Candidate> {
        val out = ArrayList<Candidate>()
        for (node in observation.nodes) {
            if (SafetyEnvelope.isDestructive(node)) continue

            if (node.editable) {
                out.add(Candidate(screenId, node, ElementAction.INPUT, depth, score(node, ElementAction.INPUT, depth)))
                continue
            }
            if (node.scrollable) {
                out.add(Candidate(screenId, node, ElementAction.SCROLL, depth, score(node, ElementAction.SCROLL, depth)))
            }
            if (node.clickable) {
                out.add(Candidate(screenId, node, ElementAction.TAP, depth, score(node, ElementAction.TAP, depth)))
            }
        }
        return out
    }

    fun score(node: PrunedNode, action: ElementAction, depth: Int): Int {
        val base = when (action) {
            ElementAction.SCROLL -> SCORE_SCROLL
            ElementAction.INPUT -> SCORE_INPUT
            ElementAction.TAP -> tapScore(node)
            else -> SCORE_GENERIC_CLICKABLE
        }
        return base - depth * DEPTH_PENALTY
    }

    private fun tapScore(node: PrunedNode): Int {
        val label = HeuristicPlanner.flatten(node.text ?: node.contentDesc)
        val resId = HeuristicPlanner.flatten(node.resourceId?.substringAfterLast('/'))
        val cls = node.className

        // A toggle changes state on the current screen and almost never
        // navigates, so it is worth recording and rarely worth spending an
        // early step on.
        if (node.checked || cls.contains("Switch") || cls.contains("CheckBox")) return SCORE_TOGGLE

        if (NAV_CLASSES.any { cls.contains(it) }) return SCORE_NAV
        if (NAV_HINTS.any { label == it || resId.contains(it) }) return SCORE_NAV
        if (LIST_ITEM_CLASSES.any { cls.contains(it) }) return SCORE_LIST_ITEM
        if (cls.contains("Button") || cls.contains("ImageButton")) return SCORE_BUTTON
        return SCORE_GENERIC_CLICKABLE
    }

    /**
     * Whether a screen plausibly has more content below the fold.
     *
     * Cheap and structural on purpose: asking a model this question once per
     * screen would cost more than the scroll it is deciding about.
     */
    fun needsScrollPass(observation: ScreenObservation): Boolean =
        observation.nodes.any { it.scrollable }
}
