package io.agents.anima

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

        /**
         * Windows above this many are a symptom, not a screen: a toast storm or
         * a leaking overlay. Capturing them all would blow the step budget.
         */
        private const val MAX_WINDOWS = 8

        /** The platform rate-limits screenshots; waiting longer than this is waiting for nothing. */
        private const val SCREENSHOT_TIMEOUT_MS = 2500L
    }

    /**
     * The last activity announced by a window-state change.
     *
     * There is no supported way to ask "what activity is in front" from an
     * accessibility service; the only place the name appears is in the class
     * name of a TYPE_WINDOW_STATE_CHANGED event, and only when the foreground
     * window is an Activity. So this is a best-effort signal -- good enough to
     * strengthen a screen fingerprint, never good enough to be required by it.
     */
    private val lastActivity = AtomicReference<String?>(null)

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
            val cls = event.className?.toString()
            if (!cls.isNullOrEmpty() && pkg.isNotEmpty() && cls.startsWith(pkg)) {
                lastActivity.set(cls)
            }
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
            // Label inheritance: a tappable row often carries no label of its
            // own -- the text lives on an inert child. Without a label such a
            // row is identified only by class and position, which do not
            // distinguish a Wi-Fi row from a Bluetooth row at the same spot on
            // another screen. Give the container the label it visually has.
            val ownText = node.text?.toString()
            val inheritedText = if (
                ownText.isNullOrBlank() &&
                node.contentDescription.isNullOrBlank() &&
                node.isClickable
            ) {
                descendantLabel(node)
            } else {
                ownText
            }
            list.add(
                AccessibilityNode(
                    resourceId = node.viewIdResourceName,
                    text = inheritedText,
                    contentDescription = node.contentDescription?.toString(),
                    className = node.className?.toString() ?: "android.view.View",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    bounds = bounds,
                    isChecked = node.isChecked,
                    isScrollable = node.isScrollable
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, list)
        }
    }

    /**
     * The single label a container visually presents, if it has one.
     *
     * Bails out when the subtree holds several labels: a whole-screen container
     * would otherwise inherit whatever text happened to come first, which is
     * worse than having no label at all.
     */
    private fun descendantLabel(node: AccessibilityNodeInfo, maxLabels: Int = 3): String? {
        val labels = mutableListOf<String>()
        collectLabels(node, labels, maxLabels)
        return if (labels.size in 1..maxLabels) labels.first() else null
    }

    private fun collectLabels(node: AccessibilityNodeInfo, out: MutableList<String>, limit: Int) {
        for (i in 0 until node.childCount) {
            if (out.size > limit) return
            val child = node.getChild(i) ?: continue
            val label = child.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                ?: child.contentDescription?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
            if (label != null) out.add(label)
            collectLabels(child, out, limit)
        }
    }

    /**
     * Every readable window, not just the focused one.
     *
     * `rootInActiveWindow` is one window. A permission dialog, a bottom sheet,
     * an autocomplete popup and a system toast are separate windows, and a
     * crawler that reads only the active one either misses the dialog entirely
     * or -- worse -- reads the screen *behind* it and taps a control the user
     * cannot currently see. `flagRetrieveInteractiveWindows` has been set in
     * accessibility_service_config.xml since the first commit; nothing ever
     * called getWindows() to use it.
     *
     * Ordering is by layer descending then window id, never by the system's
     * arrival order, because two scans of the same screen have to produce the
     * same node list in the same sequence for the pack to be byte-identical.
     */
    fun captureAllWindowNodes(): List<AccessibilityNode> {
        val ordered = try {
            windows.filterNotNull()
                .filter { it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                .sortedWith(compareByDescending<AccessibilityWindowInfo> { it.layer }.thenBy { it.id })
                .take(MAX_WINDOWS)
        } catch (e: Exception) {
            // Some OEM builds throw out of getWindows() while the window list is
            // being rebuilt. One missed frame is not worth ending a scan.
            Log.w(TAG, "getWindows() failed; falling back to the active window", e)
            emptyList()
        }
        if (ordered.isEmpty()) return captureCurrentWindowNodes()

        val nodes = mutableListOf<AccessibilityNode>()
        val seen = HashSet<String>()
        for (window in ordered) {
            val root = try { window.root } catch (e: Exception) { null } ?: continue
            if (root.packageName?.toString() == packageName) continue
            val fromThisWindow = mutableListOf<AccessibilityNode>()
            traverseNode(root, fromThisWindow)
            for (node in fromThisWindow) {
                // Overlapping windows report the same node twice. Identity here
                // is the tuple that a locator would match on anyway.
                val key = "${node.className}|${node.resourceId}|${node.text}|${node.bounds.flattenToString()}"
                if (seen.add(key)) nodes.add(node)
            }
        }
        return nodes
    }

    /** The activity in front, when the system last told us. Never required, only used. */
    fun currentActivity(): String? = lastActivity.get()

    /**
     * A screenshot of the display, synchronously.
     *
     * API 30+, which is why minSdk moved to 30. The alternative, MediaProjection,
     * puts a consent dialog in front of the user once per session -- acceptable
     * for a screen recorder, not for an app whose whole proposition is that it
     * explores unattended.
     *
     * @return the raw bitmap, or null on refusal. The platform rate-limits this
     *   call, and a refused screenshot is normal: the scan continues without
     *   pixels for that screen rather than stalling on them.
     */
    fun takeScreenshotSync(timeoutMs: Long = SCREENSHOT_TIMEOUT_MS): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val latch = CountDownLatch(1)
        val result = AtomicReference<Bitmap?>(null)
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                { it.run() },
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val buffer = screenshot.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            // Hardware bitmaps cannot be read pixel by pixel, and
                            // every consumer here -- WebP encoding, colour
                            // quantization -- needs to do exactly that.
                            result.set(bitmap?.copy(Bitmap.Config.ARGB_8888, false))
                            bitmap?.recycle()
                            buffer.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Screenshot arrived but could not be read", e)
                        } finally {
                            latch.countDown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.d(TAG, "Screenshot refused, code=$errorCode")
                        latch.countDown()
                    }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "takeScreenshot() threw", e)
            return null
        }
        return try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            result.get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
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
    val isChecked: Boolean = false,
    /**
     * Read since the first version to decide whether a node was interesting,
     * and then discarded. The crawler needs it kept: without it there is no way
     * to tell a screen it has finished reading from one with more content below
     * the fold, and on a list screen most elements are below the fold.
     */
    val isScrollable: Boolean = false
)
