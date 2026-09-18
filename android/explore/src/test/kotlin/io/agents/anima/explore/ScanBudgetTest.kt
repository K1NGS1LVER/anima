package io.agents.anima.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every way a scan can end, and the guarantee that it says which one happened.
 *
 * "We found 42 screens and the frontier was empty" and "we found 42 screens and
 * ran out of time" are very different claims to put in front of a judge, and
 * the pack has to be able to tell them apart.
 */
class ScanBudgetTest {

    /** A clock the test drives, so nothing here waits on real time. */
    private class TestClock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test(expected = IllegalArgumentException::class)
    fun aZeroStepBudgetIsRejected() {
        ScanBudget(maxSteps = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNegativeTimeBudgetIsRejected() {
        ScanBudget(maxWallClockMs = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aZeroNoveltyLimitIsRejected() {
        // Zero would mean "stop before taking any action", which reads as a
        // configuration mistake rather than an intent worth honouring.
        ScanBudget(noveltyDecayLimit = 0)
    }

    @Test
    fun itSaysNothingWhileThereIsHeadroom() {
        val tracker = BudgetTracker(ScanBudget(), TestClock())
        tracker.recordStep()
        assertNull(tracker.exhausted())
    }

    @Test
    fun runningOutOfStepsIsReportedAsSuch() {
        val tracker = BudgetTracker(ScanBudget(maxSteps = 2), TestClock())
        tracker.recordStep()
        assertNull(tracker.exhausted())
        tracker.recordStep()
        assertEquals(StopReason.STEP_BUDGET, tracker.exhausted())
    }

    @Test
    fun runningOutOfScreensIsReportedAsSuch() {
        val tracker = BudgetTracker(ScanBudget(maxScreens = 1), TestClock())
        tracker.recordNewScreen()
        assertEquals(StopReason.SCREEN_BUDGET, tracker.exhausted())
    }

    @Test
    fun runningOutOfTimeIsReportedAsSuch() {
        val clock = TestClock()
        val tracker = BudgetTracker(ScanBudget(maxWallClockMs = 1000), clock)
        clock.now = 999
        assertNull(tracker.exhausted())
        clock.now = 1000
        assertEquals(StopReason.TIME_BUDGET, tracker.exhausted())
    }

    @Test
    fun aStreakOfActionsThatRevealNothingEndsTheScan() {
        // Without this, a list of 400 transactions offers 400 detail screens
        // that are structurally the same screen, and the crawler spends its
        // entire budget on them while learning nothing.
        val tracker = BudgetTracker(ScanBudget(noveltyDecayLimit = 3), TestClock())
        repeat(2) { tracker.recordStep() }
        assertNull(tracker.exhausted())
        tracker.recordStep()
        assertEquals(StopReason.NOVELTY_DECAY, tracker.exhausted())
    }

    @Test
    fun findingSomethingNewResetsThePatience() {
        val tracker = BudgetTracker(ScanBudget(noveltyDecayLimit = 3), TestClock())
        repeat(2) { tracker.recordStep() }
        tracker.recordNewScreen()
        repeat(2) { tracker.recordStep() }

        assertNull("a new screen must buy back the full novelty allowance", tracker.exhausted())
        assertEquals(2, tracker.stepsSinceNewScreen)
    }
}
