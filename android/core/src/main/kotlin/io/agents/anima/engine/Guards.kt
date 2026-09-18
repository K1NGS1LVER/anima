package io.agents.anima.engine

/**
 * Detects and dismisses system popups / permission dialogs that obstruct execution.
 * Ported from `PopupInterceptor` in `anima.py`.
 */
object PopupInterceptor {

    val POPUP_BUTTONS: Set<String> = setOf(
        "allow",
        "while using the app",
        "only this time",
        "ok",
        "dismiss",
        "cancel",
        "close",
    )

    private val GOAL_OPT_OUT = listOf("allow", "permission", "dialog", "dismiss")

    /**
     * Taps the first popup-dismissal button on screen.
     *
     * @return true when something was dismissed (the caller must re-capture the screen).
     */
    fun checkAndHandle(nodes: List<PrunedNode>, device: AgentDevice, goal: String): Boolean {
        // If the user goal is itself about a dialog, never auto-intercept it.
        val goalLower = goal.lowercase()
        if (GOAL_OPT_OUT.any { goalLower.contains(it) }) return false

        for (n in nodes) {
            val label = (n.text ?: n.contentDesc ?: "").trim().lowercase()
            if (label in POPUP_BUTTONS && n.clickable) {
                device.tap(n.center.getOrElse(0) { 0 }, n.center.getOrElse(1) { 0 })
                return true
            }
        }
        return false
    }
}

/**
 * Detects biometric / session-expiry prompts mid-task so the agent halts and hands control back
 * to the user instead of driving a sensitive screen.
 *
 * Note: the Python original compared mixed-case markers against a lower-cased dump, which made two
 * of its markers unreachable. This port fixes that — both sides are lower-cased.
 */
object BiometricGuard {

    val MARKERS: List<String> = listOf(
        "com.android.systemui:id/biometric_prompt",
        "biometric_prompt",
        "android:id/passwordentry",
        "confirm your pattern",
        "biometric",
    )

    fun detect(rawDump: String?): Boolean {
        if (rawDump.isNullOrEmpty()) return false
        val flat = rawDump.lowercase()
        return MARKERS.any { flat.contains(it) }
    }
}

/**
 * Verifies functional progress (essential state) rather than brittle layout equality.
 *
 * In `anima.py` this is dead code; here it is wired into the replay loop as an observability
 * signal. A negative verdict never aborts a run — a false negative must not be able to break a
 * live demo — it is only reported in [ExecutionResult.message].
 */
object EssentialStateVerifier {

    fun verifyProgress(
        initialNodes: List<PrunedNode>,
        postNodes: List<PrunedNode>,
        actionNode: PrunedNode,
    ): Boolean {
        // 1. Did the target element's check/toggle state flip?
        val matching = postNodes.filter { it.resourceId == actionNode.resourceId }
        if (matching.isNotEmpty() && matching[0].checked != actionNode.checked) return true

        // 2. Did the screen transition or surface new text?
        val initialTexts = initialNodes.mapNotNull { it.text }.toSet()
        val postTexts = postNodes.mapNotNull { it.text }.toSet()
        if (postTexts != initialTexts) return true

        // 3. Simple hierarchy diff.
        return initialNodes.size != postNodes.size
    }
}
