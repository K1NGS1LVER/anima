package io.agents.anima.explore

import io.agents.anima.core.ElementAction
import io.agents.anima.core.ScreenObservation

/**
 * What one scan produced, before any model has described it and before it has
 * been turned into a pack.
 *
 * Deliberately raw: `:explore` observes and records, `:understand` names,
 * `:design` extracts and `:store` assembles. Keeping the crawler's output free
 * of anything an LLM authored is what lets the stability test attribute a
 * difference between two scans to the right module.
 */
data class ScanOutcome(
    val targetPackage: String,
    /** First observation of each screen, keyed by stable screen id, in visit order. */
    val screens: Map<String, ScreenObservation>,
    val edges: List<ObservedEdge>,
    val stopReason: StopReason,
    val steps: Int,
    val durationMs: Long,
    val frontierRemaining: Int,
    /** Screens seen but deliberately not explored, with the reason. */
    val skipped: List<SkippedScreen>,
) {
    val screenCount: Int get() = screens.size
    val elementCount: Int get() = screens.values.sumOf { it.nodes.size }
}

/** A transition the crawler actually performed and observed. Never inferred. */
data class ObservedEdge(
    val from: String,
    val to: String,
    /** [Candidate.orderKey] of the action taken, so the edge can be replayed. */
    val viaActionKey: String,
    val action: ElementAction,
)

data class SkippedScreen(val screenId: String, val reason: String)

/**
 * Live progress, for the scan-control UI.
 *
 * The brief's product is an app a normal user runs, and a user watching a phone
 * drive itself for four minutes with no feedback assumes it has hung. Every
 * callback here exists to be rendered.
 */
interface ScanListener {
    fun onScanStarted(targetPackage: String) {}
    fun onScreenDiscovered(screenId: String, observation: ScreenObservation, total: Int) {}
    fun onAction(action: ElementAction, label: String?, step: Int) {}
    fun onRecovering(reason: String) {}
    fun onScanFinished(outcome: ScanOutcome) {}

    companion object {
        val NONE: ScanListener = object : ScanListener {}
    }
}
