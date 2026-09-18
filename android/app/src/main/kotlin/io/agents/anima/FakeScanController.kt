package io.agents.anima

import android.os.Handler
import android.os.Looper

/** Temporary app seam until :explore publishes its ScanController contract (G6). */
class FakeScanController {
    data class State(val screens: Int = 0, val action: String = "Ready to scan", val seconds: Int = 0, val running: Boolean = false, val paused: Boolean = false, val complete: Boolean = false)
    interface Listener { fun onState(state: State) }
    private val handler = Handler(Looper.getMainLooper())
    private var listener: Listener? = null
    private var state = State()
    private val tick = object : Runnable {
        override fun run() {
            if (!state.running) return
            val elapsed = state.seconds + 1
            state = state.copy(seconds = elapsed, screens = maxOf(state.screens, elapsed / 3), action = listOf("Reading the current screen", "Following a safe path", "Capturing structure", "Checking new routes")[elapsed % 4], running = elapsed < 18, complete = elapsed >= 18)
            listener?.onState(state)
            if (state.running) handler.postDelayed(this, 1000)
        }
    }
    fun observe(next: Listener) { listener = next; next.onState(state) }
    fun start() { state = state.copy(running = true, paused = false, complete = false, action = "Preparing a safe scan"); handler.removeCallbacks(tick); listener?.onState(state); handler.post(tick) }
    fun pause() { state = state.copy(running = false, paused = true, action = "Paused — your phone is in your control"); handler.removeCallbacks(tick); listener?.onState(state) }
    fun resume() { state = state.copy(running = true, paused = false); listener?.onState(state); handler.post(tick) }
    fun stop() { state = state.copy(running = false, paused = false, complete = false, action = "Scan stopped — no pack was saved"); handler.removeCallbacks(tick); listener?.onState(state) }
}
