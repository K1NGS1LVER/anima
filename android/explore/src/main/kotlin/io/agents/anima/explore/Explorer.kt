package io.agents.anima.explore

import io.agents.anima.core.ElementAction
import io.agents.anima.core.ExplorationDevice
import io.agents.anima.core.ScreenIdentifier
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScrollDirection
import io.agents.anima.engine.BiometricGuard
import io.agents.anima.engine.PopupInterceptor
import io.agents.anima.engine.PrunedNode

/**
 * The autonomous crawl.
 *
 * capture → identify → enqueue what is new → take the best unexplored action →
 * act → observe → record the edge, until a budget stops it.
 *
 * Two invariants hold above everything else, and everything awkward in here
 * exists to protect one of them:
 *
 *  1. **It never taps a destructive control.** Enforced by not queueing them at
 *     all ([ExplorationPolicy.candidates]), so no later bug in this loop can
 *     reach one.
 *  2. **Two scans of the same app visit screens in the same order.** Every
 *     ordering decision is total and content-derived; nothing depends on
 *     timing, insertion order or map iteration order. Without this the
 *     byte-identical acceptance test cannot pass however good the hashing is.
 *
 * Free of `android.*` on purpose: the whole loop runs against a fake device in
 * unit tests, which is the only way to test a crawler's policy at all — on a
 * real phone the thing being tested and the thing being measured are the same
 * flaky process.
 */
class Explorer(
    private val device: ExplorationDevice,
    private val identifier: ScreenIdentifier,
    private val budget: ScanBudget = ScanBudget(),
    private val listener: ScanListener = ScanListener.NONE,
    /** The kill switch. Checked every step, because "every step" is the only honest promise. */
    private val isAborted: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private companion object {
        /** Back presses to try before giving up and cold-starting the app. */
        const val MAX_BACK_LADDER = 4

        /** Consecutive unreadable captures before the scan concludes it is stuck. */
        const val MAX_BLIND_CAPTURES = 3

        /** Text typed into a field when no test credential fits. Never plausible data. */
        const val PROBE_TEXT = "anima"
    }

    fun scan(targetPackage: String, credentials: Map<String, String> = emptyMap()): ScanOutcome {
        val startedAt = clock()
        val tracker = BudgetTracker(budget, clock)
        val frontier = Frontier()
        val screens = LinkedHashMap<String, ScreenObservation>()
        val edges = ArrayList<ObservedEdge>()
        val skipped = ArrayList<SkippedScreen>()
        val depthOf = HashMap<String, Int>()

        listener.onScanStarted(targetPackage)

        if (device.currentPackage() != targetPackage && !device.launch(targetPackage)) {
            return finish(targetPackage, screens, edges, StopReason.FAILED_TO_START, tracker, startedAt, frontier, skipped)
        }

        var current = observeOnTarget(targetPackage, skipped)
            ?: return finish(targetPackage, screens, edges, StopReason.FAILED_TO_START, tracker, startedAt, frontier, skipped)
        var currentId = record(current, 0, screens, depthOf, frontier, tracker, skipped)
        var blindCaptures = 0

        while (true) {
            if (isAborted()) {
                return finish(targetPackage, screens, edges, StopReason.ABORTED, tracker, startedAt, frontier, skipped)
            }
            tracker.exhausted()?.let {
                return finish(targetPackage, screens, edges, it, tracker, startedAt, frontier, skipped)
            }

            val candidate = frontier.takePreferring(currentId)
                ?: return finish(targetPackage, screens, edges, StopReason.FRONTIER_EXHAUSTED, tracker, startedAt, frontier, skipped)

            // The best action may be on a screen we have since navigated away
            // from. Walking back to it is a best effort: when it fails the
            // candidate is simply dropped, because a crawler that insists on
            // reaching one element will spend its entire budget trying.
            if (candidate.screenId != currentId) {
                val arrived = navigateTo(candidate.screenId, targetPackage, screens, skipped)
                if (arrived == null) {
                    blindCaptures += 1
                    if (blindCaptures >= MAX_BLIND_CAPTURES) {
                        return finish(targetPackage, screens, edges, StopReason.NOVELTY_DECAY, tracker, startedAt, frontier, skipped)
                    }
                    continue
                }
                current = arrived
                currentId = identifier.screenId(arrived)
                if (currentId != candidate.screenId) continue
            }
            blindCaptures = 0

            tracker.recordStep()
            listener.onAction(candidate.action, candidate.node.text ?: candidate.node.contentDesc, tracker.steps)
            val acted = perform(candidate, credentials)

            val after = observeOnTarget(targetPackage, skipped)
            if (after == null) {
                // Lost the app entirely. The ladder below is the recovery.
                listener.onRecovering("no readable window")
                val recovered = recover(targetPackage, skipped) ?: continue
                current = recovered
                currentId = identifier.screenId(recovered)
                continue
            }

            val afterId = identifier.screenId(after)
            if (acted && afterId != currentId) {
                edges.add(ObservedEdge(currentId, afterId, candidate.actionKey, candidate.action))
            }

            val depth = (depthOf[currentId] ?: 0) + if (afterId == currentId) 0 else 1
            currentId = afterId
            current = after
            record(after, depth, screens, depthOf, frontier, tracker, skipped)
        }
    }

    /**
     * Registers a screen and queues its actions the first time it is seen.
     * @return the screen's stable id.
     */
    private fun record(
        observation: ScreenObservation,
        depth: Int,
        screens: LinkedHashMap<String, ScreenObservation>,
        depthOf: HashMap<String, Int>,
        frontier: Frontier,
        tracker: BudgetTracker,
        skipped: MutableList<SkippedScreen>,
    ): String {
        val id = identifier.screenId(observation)
        if (id in screens) return id

        screens[id] = observation
        depthOf[id] = depth
        tracker.recordNewScreen()
        listener.onScreenDiscovered(id, observation, screens.size)

        // A biometric or lock-screen prompt is a boundary, not a puzzle. The
        // agent stops here deliberately: driving an authentication screen is
        // the one behaviour that would make this app indefensible.
        if (BiometricGuard.detect(flatten(observation))) {
            skipped.add(SkippedScreen(id, "authentication prompt"))
            return id
        }

        if (depth >= budget.maxDepth) {
            skipped.add(SkippedScreen(id, "depth budget"))
            return id
        }

        ExplorationPolicy.candidates(id, observation, depth).forEach { frontier.offer(it) }
        return id
    }

    private fun perform(candidate: Candidate, credentials: Map<String, String>): Boolean {
        val node = candidate.node
        val cx = node.center.getOrElse(0) { 0 }
        val cy = node.center.getOrElse(1) { 0 }
        return when (candidate.action) {
            ElementAction.TAP -> device.tap(cx, cy)
            ElementAction.INPUT -> device.inputText(cx, cy, credentialFor(node, credentials))
            ElementAction.SCROLL -> device.scroll(ScrollDirection.DOWN, node.bounds)
            ElementAction.BACK -> device.back()
            else -> false
        }
    }

    /**
     * Test data for a field, chosen by its own label.
     *
     * Deliberately dumb: typing the *right shape* of value is what gets a form
     * past validation and reveals the screen behind it, and a wrong-shaped
     * value produces an error screen that is itself worth recording. Deciding
     * a field's semantic type properly is `:understand`'s job, and when that
     * lands this falls back to it rather than growing more keywords.
     */
    private fun credentialFor(node: PrunedNode, credentials: Map<String, String>): String {
        val hint = ((node.text ?: "") + " " + (node.contentDesc ?: "") + " " + (node.resourceId ?: ""))
            .lowercase()
        for ((key, value) in credentials.entries.sortedBy { it.key }) {
            if (hint.contains(key.lowercase())) return value
        }
        return credentials["default"] ?: PROBE_TEXT
    }

    /** Captures, dismissing any popup in the way, and only while still inside the target app. */
    private fun observeOnTarget(targetPackage: String, skipped: MutableList<SkippedScreen>): ScreenObservation? {
        var observation = device.observe() ?: return null

        when (SafetyEnvelope.locate(observation.packageName, targetPackage)) {
            SafetyEnvelope.Location.ON_TARGET -> Unit
            SafetyEnvelope.Location.SYSTEM_DIALOG -> {
                // A consent dialog on first launch belongs to this scan. Dismiss
                // it and re-read rather than treating it as a screen of the app.
                if (dismissPopup(observation)) {
                    observation = device.observe() ?: return null
                }
            }
            SafetyEnvelope.Location.OFF_TARGET -> return recover(targetPackage, skipped)
            SafetyEnvelope.Location.UNREADABLE -> return null
        }

        if (dismissPopup(observation)) {
            observation = device.observe() ?: return null
        }
        return if (observation.packageName == targetPackage) observation else null
    }

    /**
     * Taps a known dismissal button when one is on screen.
     *
     * Reuses [PopupInterceptor]'s vocabulary rather than its entry point: that
     * one takes an `AgentDevice` and a goal string, neither of which a crawler
     * with no goal has.
     */
    private fun dismissPopup(observation: ScreenObservation): Boolean {
        val button = observation.nodes.firstOrNull { node ->
            node.clickable &&
                (node.text ?: node.contentDesc)?.trim()?.lowercase() in PopupInterceptor.POPUP_BUTTONS
        } ?: return false
        return device.tap(button.center.getOrElse(0) { 0 }, button.center.getOrElse(1) { 0 })
    }

    /**
     * The recovery ladder: back, then home, then a cold relaunch.
     *
     * Ordered by how much it costs the scan. Back usually returns from wherever
     * a stray tap went. Home plus relaunch always works but loses all in-app
     * navigation state, so it is last.
     */
    private fun recover(targetPackage: String, skipped: MutableList<SkippedScreen>): ScreenObservation? {
        listener.onRecovering("left $targetPackage")
        repeat(MAX_BACK_LADDER) {
            device.back()
            if (device.currentPackage() == targetPackage) {
                return device.observe()
            }
        }
        device.home()
        if (!device.launch(targetPackage)) {
            skipped.add(SkippedScreen("", "could not relaunch $targetPackage"))
            return null
        }
        return device.observe()?.takeIf { it.packageName == targetPackage }
    }

    /** Backs out until [screenId] is in front, or gives up. */
    private fun navigateTo(
        screenId: String,
        targetPackage: String,
        screens: Map<String, ScreenObservation>,
        skipped: MutableList<SkippedScreen>,
    ): ScreenObservation? {
        repeat(MAX_BACK_LADDER) {
            device.back()
            val observation = observeOnTarget(targetPackage, skipped) ?: return null
            if (identifier.screenId(observation) == screenId) return observation
        }
        return null
    }

    /** A flat text view of a screen, for the guards that scan for markers. */
    private fun flatten(observation: ScreenObservation): String =
        observation.nodes.joinToString("\n") { node ->
            listOfNotNull(node.className, node.resourceId, node.text, node.contentDesc).joinToString(" ")
        }

    private fun finish(
        targetPackage: String,
        screens: Map<String, ScreenObservation>,
        edges: List<ObservedEdge>,
        reason: StopReason,
        tracker: BudgetTracker,
        startedAt: Long,
        frontier: Frontier,
        skipped: List<SkippedScreen>,
    ): ScanOutcome = ScanOutcome(
        targetPackage = targetPackage,
        screens = screens,
        edges = edges,
        stopReason = reason,
        steps = tracker.steps,
        durationMs = clock() - startedAt,
        frontierRemaining = frontier.size,
        skipped = skipped,
    ).also(listener::onScanFinished)
}
