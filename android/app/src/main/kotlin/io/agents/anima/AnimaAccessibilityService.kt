package io.agents.anima

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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

        /** Android keycodes the engine may dispatch (see [dispatchKey]). */
        const val KEYCODE_HOME = 3
        const val KEYCODE_BACK = 4
        const val KEYCODE_APP_SWITCH = 187

        /** Depth guard for [rawWindowDump] so a pathological tree can never hang a run. */
        private const val MAX_DUMP_DEPTH = 40
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
            if (isBiometricPromptVisible()) {
                biometricHalt()
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
     * Biometric & Session-Expiry HITL (dev_plan §7.D): pauses the agent when a
     * biometric prompt (fingerprint / face) appears, vibrates a gentle chime,
     * and yields control to the user for authentication.
     */
    private fun isBiometricPromptVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        val markers = listOf(
            "com.android.systemui:id/biometric_prompt",
            "biometric",
            "fingerprint",
            "Confirm your pattern"
        )
        val visibleText = root.text?.toString()?.orEmpty() + root.contentDescription?.toString().orEmpty()
        return markers.any { visibleText.contains(it, ignoreCase = true) }
    }

    fun biometricHalt() {
        if (!shouldHalt.get()) {
            shouldHalt.set(true)
            isExecuting.set(false)
            // Gentle haptic pulse to request user authentication
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(250)
                }
            }
            FloatingOverlayService.releaseControlToUser("BIOMETRIC PROMPT — AUTH REQUIRED")
            Log.w(TAG, "BIOMETRIC PROMPT DETECTED: Automation paused, user authentication required.")
        }
    }

    /** The package currently in the foreground, or null if no window is readable. */
    fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()

    /** True when the screen the agent would act on is Anima's own UI. */
    fun isOwnUiInForeground(): Boolean = foregroundPackage() == packageName

    /**
     * Traverses the active window and produces a flattened list of UI nodes.
     *
     * Anima's own windows are never included. Without this the agent happily
     * reads the goal out of its own input field and taps that -- the goal text
     * is, after all, the single best keyword match for the goal on screen.
     */
    fun captureCurrentWindowNodes(): List<AccessibilityNode> {
        val root = rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() == packageName) return emptyList()
        val nodes = mutableListOf<AccessibilityNode>()
        traverseNode(root, nodes)
        return nodes
    }

    private fun traverseNode(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNode>) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Only keep interactive or semantically meaningful nodes (UIFormer logic)
        // isCheckable matters: a Wi-Fi master switch often has no text, no
        // content-desc and is not itself clickable (the row around it is), so
        // without this the agent cannot see toggles at all.
        val isMeaningful = node.isClickable || node.isScrollable || node.isEditable ||
                node.isCheckable ||
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
                    bounds = bounds,
                    isChecked = node.isChecked
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
     * Engine support: the physical display size in pixels, used to normalize node geometry into
     * resolution-invariant [0,1] coordinates so compiled skills survive a change of device.
     */
    fun displaySize(): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val width = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
        val height = if (metrics.heightPixels > 0) metrics.heightPixels else 2400
        return Pair(width, height)
    }

    /**
     * Engine support: a flattened text dump of the whole active window (class names, view ids,
     * text and content descriptions). This is what the engine's BiometricGuard scans, so it must
     * include *every* node, not just the semantically meaningful ones.
     */
    fun rawWindowDump(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        root.packageName?.let { sb.append(it).append('\n') }
        appendNodeDump(root, sb, 0)
        return sb.toString()
    }

    private fun appendNodeDump(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > MAX_DUMP_DEPTH) return
        sb.append(node.className ?: "").append(' ')
            .append(node.viewIdResourceName ?: "").append(' ')
            .append(node.text ?: "").append(' ')
            .append(node.contentDescription ?: "").append('\n')
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            appendNodeDump(child, sb, depth + 1)
        }
    }

    /**
     * Engine support: maps an Android keycode onto the global actions an accessibility service is
     * allowed to perform. Anything unrecognized falls back to BACK, which is what the replay loop
     * uses to dismiss the soft keyboard after typing.
     */
    fun dispatchKey(keycode: Int): Boolean {
        if (shouldHalt.get()) return false
        return when (keycode) {
            KEYCODE_HOME -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            KEYCODE_APP_SWITCH -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            else -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
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
    val bounds: Rect,
    val isChecked: Boolean = false
)
