package io.agents.anima.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScanNotificationText] is the one piece of [ScanService] that runs free of
 * `android.*`, so it is the one piece exercised here on the plain JVM.
 */
class ScanNotificationTextTest {

    @Test
    fun startingNamesTheTargetPackage() {
        assertEquals(
            "Starting scan of com.example.app...",
            ScanNotificationText.starting("com.example.app"),
        )
    }

    @Test
    fun progressPluralizesScreenCount() {
        assertEquals("0 screens found - 0:00 elapsed", ScanNotificationText.progress(0, 0))
        assertEquals("1 screen found - 0:00 elapsed", ScanNotificationText.progress(1, 0))
        assertEquals("2 screens found - 0:00 elapsed", ScanNotificationText.progress(2, 0))
    }

    @Test
    fun progressFormatsElapsedTimeAsMinutesAndSeconds() {
        assertEquals("3 screens found - 0:09 elapsed", ScanNotificationText.progress(3, 9_000))
        assertEquals("3 screens found - 1:05 elapsed", ScanNotificationText.progress(3, 65_000))
        // ScanBudget.maxWallClockMs default is 240_000ms -- confirm it renders sanely.
        assertEquals("3 screens found - 4:00 elapsed", ScanNotificationText.progress(3, 240_000))
    }

    @Test
    fun progressNeverGoesNegativeOnAnOddClock() {
        // Defensive: a clock() lambda could in principle report elapsed < 0 (e.g. a
        // mocked clock in a test), and the notification should not show "-1:00".
        assertEquals("0 screens found - 0:00 elapsed", ScanNotificationText.progress(0, -500))
    }

    @Test
    fun actionFallsBackToElementWhenLabelIsMissing() {
        assertEquals("Step 4: element", ScanNotificationText.action(null, 4))
        assertEquals("Step 4: element", ScanNotificationText.action("   ", 4))
    }

    @Test
    fun actionUsesTheGivenLabel() {
        assertEquals("Step 7: Sign in", ScanNotificationText.action("Sign in", 7))
    }

    @Test
    fun actionTruncatesLongLabels() {
        val longLabel = "a".repeat(100)
        val result = ScanNotificationText.action(longLabel, 1)
        assertTrue(result.length < 60)
        assertTrue(result.endsWith("…"))
    }

    @Test
    fun recoveringIncludesTheReason() {
        assertEquals("Recovering (no readable window)...", ScanNotificationText.recovering("no readable window"))
    }

    @Test
    fun finishedSummarizesScreensStepsAndStopReason() {
        assertEquals(
            "Scan complete: 12 screens, 88 steps (frontier_exhausted)",
            ScanNotificationText.finished(12, 88, StopReason.FRONTIER_EXHAUSTED),
        )
        assertEquals(
            "Scan complete: 1 screen, 1 steps (time_budget)",
            ScanNotificationText.finished(1, 1, StopReason.TIME_BUDGET),
        )
    }

    @Test
    fun stoppedDistinguishesUserFromKillSwitch() {
        assertEquals("Scan stopped by user: 5 screens, 10 steps", ScanNotificationText.stopped(5, 10, byUser = true))
        assertEquals("Scan stopped by kill switch: 5 screens, 10 steps", ScanNotificationText.stopped(5, 10, byUser = false))
    }

    @Test
    fun titleNamesTheTargetPackage() {
        assertEquals("Scanning com.example.app", ScanNotificationText.title("com.example.app"))
    }
}
