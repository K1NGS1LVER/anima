package io.agents.anima.capture

import android.content.Context
import android.provider.Settings
import android.util.Log
import io.agents.anima.AnimaAccessibilityService
import io.agents.anima.core.ExplorationDevice
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScrollDirection
import io.agents.anima.core.UiMode
import io.agents.anima.engine.AccessibilityDevice
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [ExplorationDevice] over a real phone.
 *
 * Every method blocks: the accessibility gesture API is callback-based, and an
 * explorer that fires a tap and immediately captures reads the *previous*
 * screen, records a transition that never happened, and poisons the graph. So
 * gestures wait on a latch and then wait again for the UI to settle. This class
 * must therefore be driven from a background thread, never the main one.
 */
class AccessibilityExplorationDevice(
    private val service: AnimaAccessibilityService,
    private val context: Context,
    private val settleMs: Long = DEFAULT_SETTLE_MS,
) : ExplorationDevice {

    companion object {
        const val TAG = "AnimaExploreDevice"

        /**
         * Long enough for a screen transition and its entry animation. Shorter
         * values were tried on the MIUI test device and produced captures of
         * half-drawn screens, which hash as new screens and inflate the graph
         * with duplicates that do not reproduce on the next scan.
         */
        const val DEFAULT_SETTLE_MS = 700L
        const val GESTURE_TIMEOUT_MS = 5000L
        const val SCROLL_DURATION_MS = 320L

        /**
         * The swipe spans the middle 60% of the scrollable area. Starting at the
         * very edge catches the system back gesture on one side and the
         * notification shade on the other; both take the crawler out of the app.
         */
        private const val SWIPE_SPAN = 0.6f
    }

    override fun observe(): ScreenObservation? {
        val (w, h) = service.displaySize()
        val captured = service.captureAllWindowNodes()
        if (captured.isEmpty()) return null
        val nodes = captured.mapIndexed { index, node ->
            AccessibilityDevice.toPrunedNode(index + 1, node, w, h)
        }
        return ScreenObservation(
            packageName = service.foregroundPackage() ?: return null,
            activity = service.currentActivity(),
            nodes = nodes,
            screenshot = Screenshotter.encode(service.takeScreenshotSync()),
            screenSize = w to h,
            uiMode = currentUiMode(),
        )
    }

    override fun tap(x: Int, y: Int): Boolean {
        val ok = awaitGesture { done -> service.dispatchTap(x.toFloat(), y.toFloat()) { done(it) } }
        settle(settleMs)
        return ok
    }

    override fun inputText(x: Int, y: Int, text: String): Boolean {
        // Focus first: dispatchInputText writes into whatever is focused, and
        // on a form with several fields that is rarely the one we mean.
        tap(x, y)
        val ok = service.dispatchInputText(text)
        if (!ok) Log.w(TAG, "inputText found no focused editable field at ($x, $y)")
        settle(settleMs)
        return ok
    }

    override fun scroll(direction: ScrollDirection, boundsPx: List<Int>?): Boolean {
        val (w, h) = service.displaySize()
        val l = boundsPx?.getOrNull(0) ?: 0
        val t = boundsPx?.getOrNull(1) ?: 0
        val r = boundsPx?.getOrNull(2) ?: w
        val b = boundsPx?.getOrNull(3) ?: h
        if (r <= l || b <= t) return false

        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val dx = (r - l) * SWIPE_SPAN / 2f
        val dy = (b - t) * SWIPE_SPAN / 2f

        // A swipe moves the content the opposite way to the finger: to see what
        // is further DOWN the page, the finger travels upward.
        val swipe = when (direction) {
            ScrollDirection.DOWN -> Swipe(cx, cy + dy, cx, cy - dy)
            ScrollDirection.UP -> Swipe(cx, cy - dy, cx, cy + dy)
            ScrollDirection.RIGHT -> Swipe(cx + dx, cy, cx - dx, cy)
            ScrollDirection.LEFT -> Swipe(cx - dx, cy, cx + dx, cy)
        }

        val ok = awaitGesture { done ->
            service.dispatchSwipe(swipe.sx, swipe.sy, swipe.ex, swipe.ey, SCROLL_DURATION_MS) { done(it) }
        }
        settle(settleMs)
        return ok
    }

    override fun back(): Boolean {
        val ok = service.dispatchKey(AnimaAccessibilityService.KEYCODE_BACK)
        settle(settleMs)
        return ok
    }

    override fun home(): Boolean {
        val ok = service.dispatchKey(AnimaAccessibilityService.KEYCODE_HOME)
        settle(settleMs)
        return ok
    }

    override fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "No launch intent for $packageName")
            return false
        }
        return try {
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            settle(settleMs * 2)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch $packageName", e)
            false
        }
    }

    override fun currentPackage(): String? = service.foregroundPackage()

    /**
     * Needs `WRITE_SECURE_SETTINGS`, which a normal app cannot hold. On a device
     * where it has been granted over adb the light/dark diff works; everywhere
     * else this returns false and the pack honestly records one mode instead of
     * claiming a comparison it never made.
     */
    override fun setUiMode(mode: UiMode): Boolean = try {
        Settings.Secure.putInt(
            context.contentResolver,
            "ui_night_mode",
            if (mode == UiMode.DARK) 2 else 1,
        )
        settle(settleMs * 2)
        currentUiMode() == mode
    } catch (e: SecurityException) {
        Log.i(TAG, "Cannot switch theme without WRITE_SECURE_SETTINGS; scanning one mode only")
        false
    } catch (e: Exception) {
        false
    }

    override fun settle(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun currentUiMode(): UiMode {
        val flags = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (flags == android.content.res.Configuration.UI_MODE_NIGHT_YES) UiMode.DARK else UiMode.LIGHT
    }

    /** Dispatches a callback-based gesture and blocks until it reports back. */
    private fun awaitGesture(dispatch: ((Boolean) -> Unit) -> Unit): Boolean {
        val latch = CountDownLatch(1)
        val result = AtomicBoolean(false)
        dispatch { ok ->
            result.set(ok)
            latch.countDown()
        }
        return try {
            // A timeout is a failed gesture, not a successful one: reporting
            // success here would have the explorer record a transition that the
            // device never performed.
            if (latch.await(GESTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) result.get() else false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
}

private data class Swipe(val sx: Float, val sy: Float, val ex: Float, val ey: Float)
