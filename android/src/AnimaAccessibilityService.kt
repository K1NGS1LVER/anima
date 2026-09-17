package io.agents.anima

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AnimaAccessibilityService
 *
 * The core Android-native accessibility execution plane for the Anima autonomous agent runtime.
 * Provides on-device UI inspection, UIFormer semantic node extraction, native gesture
 * synthesis (taps, swipes, text typing), and emergency safety kill-switch interception.
 */
class AnimaAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AnimaAgent"
        var instance: AnimaAccessibilityService? = null
            private set
        val isExecuting = AtomicBoolean(false)
        val shouldHalt = AtomicBoolean(false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Anima Accessibility Service connected successfully.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isExecuting.set(false)
        Log.i(TAG, "Anima Accessibility Service destroyed.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Auto-dismiss known transient system popups or permissions if flagged
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg.contains("com.android.permissioncontroller") || pkg.contains("packageinstaller")) {
                handlePermissionDialog(event)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Anima Accessibility Service interrupted.")
        emergencyHalt()
    }

    /**
     * Emergency Kill Switch: immediately aborts active automation.
     */
    fun emergencyHalt() {
        shouldHalt.set(true)
        isExecuting.set(false)
        FloatingOverlayService.releaseControlToUser("EMERGENCY HALT TRIGGERED")
        Log.w(TAG, "EMERGENCY HALT TRIGGERED: Active automation stopped.")
    }

    /**
     * Traverses the active window and produces a flattened list of UI nodes.
     */
    fun captureCurrentWindowNodes(): List<AccessibilityNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = mutableListOf<AccessibilityNode>()
        traverseNode(root, nodes)
        return nodes
    }

    private fun traverseNode(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNode>) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Only keep interactive or semantically meaningful nodes (UIFormer logic)
        val isMeaningful = node.isClickable || node.isScrollable || node.isEditable ||
                !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()

        if (isMeaningful && bounds.width() > 0 && bounds.height() > 0) {
            list.add(
                AccessibilityNode(
                    resourceId = node.viewIdResourceName,
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    className = node.className?.toString() ?: "android.view.View",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    bounds = bounds
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, list)
        }
    }

    /**
     * Synthesizes and dispatches a native tap gesture at screen coordinates (x, y).
     */
    fun dispatchTap(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null) {
        if (shouldHalt.get()) {
            onComplete?.invoke(false)
            return
        }

        val path = Path().apply {
            moveTo(x, y)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap executed at ($x, $y)")
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap cancelled at ($x, $y)")
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Synthesizes and dispatches a swipe gesture between two points.
     */
    fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (shouldHalt.get()) {
            onComplete?.invoke(false)
            return
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe executed from ($startX, $startY) to ($endX, $endY)")
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe cancelled")
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Injects text into the currently focused editable view.
     */
    fun dispatchInputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        return if (focused != null && focused.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            Log.w(TAG, "No editable field currently focused for text injection.")
            false
        }
    }

    /**
     * Dismisses transient permission dialogs automatically (allow or deny).
     */
    private fun handlePermissionDialog(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        val allowNodes = root.findAccessibilityNodeInfosByText("Allow")
            .ifEmpty { root.findAccessibilityNodeInfosByText("While using the app") }

        for (node in allowNodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Auto-dismissed permission dialog with Allow.")
                return
            }
        }
    }
}

/**
 * Lightweight representation of an Android accessibility node for matching.
 */
data class AccessibilityNode(
    val resourceId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val bounds: Rect
)
