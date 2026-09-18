package io.agents.anima.explore

/**
 * The ceilings that make a scan end.
 *
 * "Explore until done" has no natural end on a real app: a list of 400
 * transactions offers 400 detail screens that are structurally the same screen.
 * So a scan stops on whichever limit it meets first, and the pack records which
 * one, because "we found 42 screens and the frontier was empty" and "we found
 * 42 screens and ran out of time" are very different claims to put in front of
 * a judge.
 *
 * Defaults are set against the display budget in RELEASE_READINESS.md: a
 * ~30-screen app in under four minutes.
 */
data class ScanBudget(
    val maxSteps: Int = 220,
    val maxScreens: Int = 60,
    val maxDepth: Int = 8,
    val maxWallClockMs: Long = 4 * 60 * 1000L,
    /**
     * Consecutive actions that reveal no new screen before the scan concludes
     * there is nothing left to find. This is what stops a crawler burning its
     * whole step budget opening the 400 rows of a transaction list.
     */
    val noveltyDecayLimit: Int = 18,
) {
    init {
        require(maxSteps > 0) { "maxSteps must be positive" }
        require(maxScreens > 0) { "maxScreens must be positive" }
        require(maxDepth > 0) { "maxDepth must be positive" }
        require(maxWallClockMs > 0) { "maxWallClockMs must be positive" }
        require(noveltyDecayLimit > 0) { "noveltyDecayLimit must be positive" }
    }
}

/** Why a scan ended. Recorded verbatim in `scan.coverage.stop_reason`. */
enum class StopReason {
    /** Everything reachable was reached. The only reason that means "complete". */
    FRONTIER_EXHAUSTED,
    STEP_BUDGET,
    SCREEN_BUDGET,
    TIME_BUDGET,
    NOVELTY_DECAY,
    /** The user hit the kill switch, or the service went away. */
    ABORTED,
    /** The app could not be launched, or nothing was ever readable. */
    FAILED_TO_START;

    val wire: String get() = name.lowercase()
}

/**
 * Mutable progress against a [ScanBudget].
 *
 * Kept separate from the budget so the limits stay immutable and printable, and
 * so a test can drive a tracker to any state without constructing a scan.
 */
class BudgetTracker(
    val budget: ScanBudget,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val startedAt: Long = clock()

    var steps: Int = 0
        private set
    var screens: Int = 0
        private set
    var stepsSinceNewScreen: Int = 0
        private set

    val elapsedMs: Long get() = clock() - startedAt

    fun recordStep() {
        steps += 1
        stepsSinceNewScreen += 1
    }

    fun recordNewScreen() {
        screens += 1
        stepsSinceNewScreen = 0
    }

    /** The reason to stop now, or null to keep going. */
    fun exhausted(): StopReason? = when {
        steps >= budget.maxSteps -> StopReason.STEP_BUDGET
        screens >= budget.maxScreens -> StopReason.SCREEN_BUDGET
        elapsedMs >= budget.maxWallClockMs -> StopReason.TIME_BUDGET
        stepsSinceNewScreen >= budget.noveltyDecayLimit -> StopReason.NOVELTY_DECAY
        else -> null
    }
}
