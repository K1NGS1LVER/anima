package io.agents.anima.explore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The surface a scan-control screen builds against. Backed today by
 * [FakeScanController] for development, and eventually by a real
 * implementation that binds to `ScanService` (see the honesty note on
 * [pause] below, and the integration note at the bottom of this file).
 *
 * `start`/`stop` map onto real crawler operations. `pause` does not, yet --
 * the underlying Explorer has a one-way kill switch (`isAborted`), not a
 * resumable checkpoint, so a real pause/resume would need changes to Explorer
 * itself. Rather than fake a capability that doesn't exist, `pause()` is
 * documented as requesting a stop at the next screen boundary -- functionally
 * a graceful stop, not a true pause. Any implementation is required to honor
 * that contract rather than silently doing something else.
 */
interface ScanController {
    /** Current state, observable by a UI. Starts at [ScanPhase.IDLE]. */
    val state: StateFlow<ScanUiState>

    fun start(targetPackage: String, budget: ScanBudget = ScanBudget())

    /**
     * Requests a graceful stop at the next screen boundary. NOT a true
     * pause -- see the class doc. A UI must not promise the user it can
     * resume from here; label the control "Stop" or "Finish early", not
     * "Pause", until a real pause exists.
     */
    fun pause()

    fun stop()
}

/** Where a scan is in its lifecycle. */
enum class ScanPhase { IDLE, RUNNING, STOPPING, FINISHED, FAILED }

/**
 * A live progress snapshot -- what N4's screen needs: screens found, current
 * action, elapsed. Deliberately NOT the raw ScanOutcome (which holds full
 * ScreenObservations with screenshot bytes for every screen) -- a UI polling
 * this every action would be copying megabytes of pixel data per tick for no
 * reason. currentAction is a short, human-terse description, not a raw
 * ElementAction -- "tapping 'Sign in'" reads better in a progress row than
 * "TAP".
 */
data class ScanUiState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val targetPackage: String? = null,
    val screensFound: Int = 0,
    val currentAction: String? = null,
    val elapsedMs: Long = 0,
    /** Set only when phase == FINISHED or FAILED. */
    val stopReason: String? = null,
    val recovering: Boolean = false,
)

/**
 * A scripted, in-memory ScanController for building and testing the
 * scan-control screen with no device, no service, and no crawler. Emits a
 * pre-set sequence of ScanUiState transitions on [start], one every [tickMs]
 * on a background coroutine, so a UI (or a test) can observe every phase a
 * real scan would move through without any of them existing yet.
 *
 * The timer uses `Dispatchers.Default`, not `Dispatchers.Main`, specifically
 * so this class stays usable from a plain JVM unit test -- there is no
 * Android `Looper` to fake here.
 */
class FakeScanController(
    private val script: List<ScanUiState> = defaultScript(),
    private val tickMs: Long = 400,
) : ScanController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ScanUiState())
    override val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var job: Job? = null

    override fun start(targetPackage: String, budget: ScanBudget) {
        job?.cancel()
        _state.value = ScanUiState(phase = ScanPhase.RUNNING, targetPackage = targetPackage)
        job = scope.launch {
            for (snapshot in script) {
                delay(tickMs)
                _state.value = snapshot.copy(targetPackage = targetPackage)
                if (snapshot.phase == ScanPhase.FINISHED || snapshot.phase == ScanPhase.FAILED) {
                    break
                }
            }
        }
    }

    /**
     * Interrupts the script and settles into [ScanPhase.STOPPING], then
     * finishes shortly after -- standing in for "the real scan reaches the
     * next screen boundary and stops there".
     */
    override fun pause() {
        val current = _state.value
        if (current.phase != ScanPhase.RUNNING) return
        job?.cancel()
        _state.value = current.copy(phase = ScanPhase.STOPPING, currentAction = "Wrapping up")
        job = scope.launch {
            delay(tickMs)
            _state.value = _state.value.copy(
                phase = ScanPhase.FINISHED,
                currentAction = null,
                stopReason = StopReason.ABORTED.wire,
            )
        }
    }

    /** Interrupts the script immediately and jumps to a terminal state. */
    override fun stop() {
        job?.cancel()
        val current = _state.value
        if (current.phase == ScanPhase.FINISHED || current.phase == ScanPhase.FAILED) return
        _state.value = current.copy(
            phase = ScanPhase.FINISHED,
            currentAction = null,
            stopReason = StopReason.ABORTED.wire,
        )
    }

    companion object {
        /** A plausible ~6-screen scan a UI can render without a real device. */
        fun defaultScript(): List<ScanUiState> = listOf(
            ScanUiState(
                phase = ScanPhase.RUNNING,
                screensFound = 0,
                currentAction = "Launching target app",
                elapsedMs = 400,
            ),
            ScanUiState(
                phase = ScanPhase.RUNNING,
                screensFound = 1,
                currentAction = "Reading home screen",
                elapsedMs = 900,
            ),
            ScanUiState(
                phase = ScanPhase.RUNNING,
                screensFound = 2,
                currentAction = "Tapping 'Sign in'",
                elapsedMs = 1600,
            ),
            ScanUiState(
                phase = ScanPhase.RUNNING,
                screensFound = 2,
                currentAction = "Recovering from an unexpected dialog",
                elapsedMs = 2300,
                recovering = true,
            ),
            ScanUiState(
                phase = ScanPhase.RUNNING,
                screensFound = 3,
                currentAction = "Tapping 'Settings'",
                elapsedMs = 3000,
            ),
            ScanUiState(
                phase = ScanPhase.RUNNING,
                screensFound = 4,
                currentAction = "Scrolling 'Notifications' list",
                elapsedMs = 3800,
            ),
            ScanUiState(
                phase = ScanPhase.RUNNING,
                screensFound = 5,
                currentAction = "Tapping 'Profile'",
                elapsedMs = 4500,
            ),
            ScanUiState(
                phase = ScanPhase.RUNNING,
                screensFound = 6,
                currentAction = "Backing out to home",
                elapsedMs = 5200,
            ),
            ScanUiState(
                phase = ScanPhase.FINISHED,
                screensFound = 6,
                currentAction = null,
                elapsedMs = 5900,
                stopReason = StopReason.FRONTIER_EXHAUSTED.wire,
            ),
        )
    }
}

// Integration note for the real, service-backed implementation:
//
// A real ScanController will not own a coroutine that invents progress -- it
// will bind to `ScanService` (or receive a `ScanListener` from whatever binds
// it) and translate `ScanListener` callbacks (`onScanStarted`,
// `onScreenDiscovered`, `onAction`, `onRecovering`, `onScanFinished`) into
// `ScanUiState` updates as they arrive, plus a ticking `elapsedMs` derived
// from wall-clock time rather than a fixed script. `start()` will need to
// actually launch/bind the service and hand it a target package and budget;
// `stop()` will call through to the service's real abort path (Explorer's
// `isAborted` kill switch) instead of synthesizing a terminal state; and
// `pause()` must still only request a stop at the next screen boundary --
// the honesty constraint in the class doc applies to the real implementation
// at least as much as it does to this fake one, since a real UI is where a
// user could actually be misled by a "Pause" that doesn't resume.
