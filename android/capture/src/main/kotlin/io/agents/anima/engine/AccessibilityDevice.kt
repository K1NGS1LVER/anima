package io.agents.anima.engine

import android.util.Log
import io.agents.anima.AccessibilityNode
import io.agents.anima.AnimaAccessibilityService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The real execution plane: an [AgentDevice] backed by [AnimaAccessibilityService].
 *
 * Captured [AccessibilityNode]s (which carry an `android.graphics.Rect`) are converted into
 * [PrunedNode]s with resolution-invariant `relBounds` / `relCenter` in [0,1], so a skill compiled
 * on one phone still replays on a device with a different display size.
 *
 * All calls are synchronous: the accessibility gesture API is callback-based, so taps block on a
 * latch. This must therefore be driven from a background thread (as [io.agents.anima.TaskerReceiver]
 * does), never from the main thread.
 */
class AccessibilityDevice(
    private val service: AnimaAccessibilityService,
    private val settleMs: Long = DEFAULT_SETTLE_MS,
) : AgentDevice {

    companion object {
        const val TAG = "AnimaDevice"
        const val DEFAULT_SETTLE_MS = 150L
        const val GESTURE_TIMEOUT_MS = 3000L

        /** Converts one captured accessibility node into a normalized Agent-DOM node. */
        fun toPrunedNode(id: Int, node: AccessibilityNode, screenW: Int, screenH: Int): PrunedNode {
            val rect = node.bounds
            val bounds = listOf(rect.left, rect.top, rect.right, rect.bottom)
            val center = listOf((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)
            val (relBounds, relCenter) = UIFormer.normalize(bounds, center, screenW, screenH)
            return PrunedNode(
                id = id,
                // Simple name, matching UIFormer's `class.split(".")[-1]` on the Python side.
                className = node.className.substringAfterLast('.'),
                resourceId = node.resourceId?.takeIf { it.isNotEmpty() },
                text = node.text?.trim()?.takeIf { it.isNotEmpty() },
                contentDesc = node.contentDescription?.trim()?.takeIf { it.isNotEmpty() },
                clickable = node.isClickable,
                checked = node.isChecked,
                bounds = bounds,
                center = center,
                relBounds = relBounds,
                relCenter = relCenter,
            )
        }
    }

    override fun rawDump(): String = service.rawWindowDump()

    override fun screenSize(): Pair<Int, Int> = service.displaySize()

    override fun captureNodes(): List<PrunedNode> {
        val (screenW, screenH) = service.displaySize()
        return service.captureCurrentWindowNodes().mapIndexed { index, node ->
            toPrunedNode(index + 1, node, screenW, screenH)
        }
    }

    override fun tap(x: Int, y: Int) {
        val latch = CountDownLatch(1)
        service.dispatchTap(x.toFloat(), y.toFloat()) { latch.countDown() }
        try {
            latch.await(GESTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        settle()
    }

    override fun inputText(text: String) {
        val ok = service.dispatchInputText(text)
        if (!ok) Log.w(TAG, "inputText() found no focused editable field")
        settle()
    }

    override fun key(keycode: Int) {
        service.dispatchKey(keycode)
        settle()
    }

    /** Lets the UI settle so the next capture sees the post-action screen. */
    private fun settle() {
        try {
            Thread.sleep(settleMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
