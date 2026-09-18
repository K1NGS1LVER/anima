package io.agents.anima.explore

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FakeScanController is what N4's scan-control screen actually compiles
 * against (see EXECUTION_PLAN.md), so its transitions need to be exercised
 * as carefully as a real controller's would be -- a UI author reads these
 * tests to learn what to expect from [ScanController.state].
 *
 * Every test uses a short [FakeScanController.tickMs] and bounds its waits
 * with `withTimeout`, so nothing here depends on real wall-clock sleep
 * timing to pass -- only on the eventual sequence of states.
 */
class FakeScanControllerTest {

    private val timeoutMs = 5_000L

    @Test
    fun initialStateIsIdle() {
        val controller = FakeScanController(tickMs = 5)
        assertEquals(ScanPhase.IDLE, controller.state.value.phase)
    }

    @Test
    fun startReachesRunningThenFinished() = runBlocking {
        val controller = FakeScanController(tickMs = 5)

        controller.start("com.example.target")

        withTimeout(timeoutMs) {
            controller.state.first { it.phase == ScanPhase.RUNNING }
        }
        val finalState = withTimeout(timeoutMs) {
            controller.state.first { it.phase == ScanPhase.FINISHED }
        }

        assertEquals("com.example.target", finalState.targetPackage)
        assertNotNull("a terminal state must record why the scan stopped", finalState.stopReason)
    }

    @Test
    fun stopMovesToTerminalStateAndScriptDoesNotResume() = runBlocking {
        val controller = FakeScanController(tickMs = 20)

        controller.start("com.example.target")
        withTimeout(timeoutMs) {
            controller.state.first { it.phase == ScanPhase.RUNNING }
        }

        controller.stop()
        val afterStop = controller.state.value
        assertEquals(ScanPhase.FINISHED, afterStop.phase)
        assertNotNull(afterStop.stopReason)

        // If the cancelled script were somehow still ticking, waiting out its
        // whole remaining length would surface it here.
        delay(20 * 20)
        assertEquals(afterStop, controller.state.value)
    }

    @Test
    fun screensFoundIsNonDecreasingUpToTerminalState() = runBlocking {
        val controller = FakeScanController(tickMs = 5)
        val emissions = mutableListOf<ScanUiState>()

        controller.start("com.example.target")
        // A single collection chain, so the terminal state that satisfies
        // `first` is guaranteed to have already been recorded by `onEach` --
        // two independent collectors on a conflated StateFlow could race and
        // let one miss the value the other just matched on.
        withTimeout(timeoutMs) {
            controller.state
                .onEach { emissions.add(it) }
                .first { it.phase == ScanPhase.FINISHED || it.phase == ScanPhase.FAILED }
        }

        var last = 0
        var sawTerminal = false
        for (snapshot in emissions) {
            assertTrue(
                "screensFound regressed from $last to ${snapshot.screensFound}",
                snapshot.screensFound >= last,
            )
            last = snapshot.screensFound
            if (snapshot.phase == ScanPhase.FINISHED || snapshot.phase == ScanPhase.FAILED) {
                sawTerminal = true
                break
            }
        }
        assertTrue("expected to observe a terminal state", sawTerminal)
    }
}
