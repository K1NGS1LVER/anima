package io.agents.anima.explore

import io.agents.anima.core.ElementAction
import io.agents.anima.engine.PrunedNode

/**
 * One thing the crawler could do next.
 *
 * [orderKey] exists because two scans of the same app must visit screens in the
 * same order, and nothing else about a candidate is reliably stable: node ids
 * are assigned in capture order, scores tie constantly, and a hash-map
 * iteration order differs between the JVM and Android (the lesson already
 * recorded in SkillJson). The key is built from geometry and identity, both of
 * which are the same on both runs of the same screen.
 */
data class Candidate(
    val screenId: String,
    val node: PrunedNode,
    val action: ElementAction,
    val depth: Int,
    val score: Int,
) {
    /** Stable across scans, across devices of the same resolution, and across runtimes. */
    val orderKey: String = keyFor(screenId, node, action)

    /** Identity for "have we already done this?", independent of when it was found. */
    val actionKey: String get() = orderKey

    companion object {
        /**
         * The single source of truth for a candidate's stable key.
         *
         * Exists as a standalone function -- not just the [orderKey] property --
         * because [ScanOrchestrator] needs to recompute this exact key for
         * `(screenId, node, action)` triples read back out of a finished
         * [ScanOutcome] that are not backed by a live [Candidate]. Two copies of
         * this logic could drift; this is the one copy both sides call.
         */
        fun keyFor(screenId: String, node: PrunedNode, action: ElementAction): String = buildString {
            append(screenId).append('|')
            append(node.relBounds.joinToString(",")).append('|')
            append(node.className).append('|')
            append(node.resourceId ?: "").append('|')
            append(node.text ?: node.contentDesc ?: "").append('|')
            append(action.wire)
        }
    }
}

/**
 * The queue of unexplored actions.
 *
 * Not a `PriorityQueue`: that is a heap, and a heap gives no guarantee about
 * the order of equal elements. Half the candidates on a real screen score
 * identically, so the heap's arbitrary tie-breaking alone would be enough to
 * make two scans diverge — which is exactly the failure the byte-identical
 * requirement is about. A sorted set with a total order has no ties at all.
 */
class Frontier {

    private val comparator = compareByDescending<Candidate> { it.score }
        .thenBy { it.depth }
        .thenBy { it.orderKey }

    private val pending = sortedSetOf(comparator)
    private val enqueued = HashSet<String>()
    private val done = HashSet<String>()

    val size: Int get() = pending.size

    fun isEmpty(): Boolean = pending.isEmpty()

    /** @return true when the candidate was new. Re-offering a done action is a no-op. */
    fun offer(candidate: Candidate): Boolean {
        val key = candidate.actionKey
        if (key in done || key in enqueued) return false
        enqueued.add(key)
        pending.add(candidate)
        return true
    }

    /** The highest-value unexplored action, removed from the queue. */
    fun take(): Candidate? {
        val next = pending.firstOrNull() ?: return null
        pending.remove(next)
        enqueued.remove(next.actionKey)
        done.add(next.actionKey)
        return next
    }

    /**
     * The best candidate on [screenId] if there is one, otherwise the global
     * best.
     *
     * Staying on the current screen when it still has work is not an
     * optimisation, it is what keeps the walk cheap: the alternative is
     * navigating back to a screen we are already standing on, and every
     * navigation is several gestures that can each fail or land somewhere
     * unexpected. Ordering within each case is fully determined, so this stays
     * reproducible.
     */
    fun takePreferring(screenId: String?): Candidate? {
        if (screenId != null) {
            val local = pending.firstOrNull { it.screenId == screenId }
            if (local != null) {
                pending.remove(local)
                enqueued.remove(local.actionKey)
                done.add(local.actionKey)
                return local
            }
        }
        return take()
    }

    /**
     * Drops everything queued on [screenId].
     *
     * Used when a screen turns out to be a dead end or is abandoned: the
     * candidates stay marked done so they are never re-offered from elsewhere.
     */
    fun dropScreen(screenId: String) {
        val doomed = pending.filter { it.screenId == screenId }
        doomed.forEach {
            pending.remove(it)
            enqueued.remove(it.actionKey)
            done.add(it.actionKey)
        }
    }

    fun hasVisited(actionKey: String): Boolean = actionKey in done
}
