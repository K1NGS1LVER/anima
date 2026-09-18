package io.agents.anima.explore

/**
 * Builds the short strings shown in [ScanService]'s persistent notification.
 *
 * Kept free of `android.*` on purpose, same reasoning as [Explorer]: a
 * notification's text is pure formatting, and formatting is worth testing on
 * the JVM without dragging in `Notification`/`Service` and a Robolectric (or
 * instrumented) test harness just to check a string.
 */
object ScanNotificationText {

    /** Title shown for the whole lifetime of a running scan. */
    fun title(targetPackage: String): String = "Scanning $targetPackage"

    /** Before the first observation lands. */
    fun starting(targetPackage: String): String = "Starting scan of $targetPackage..."

    /**
     * Steady-state progress text: how much has been found and how long it has
     * taken. `elapsedMs` is rounded down to the second -- a notification has no
     * use for millisecond precision and a ticking sub-second count just looks
     * like flicker.
     */
    fun progress(screensFound: Int, elapsedMs: Long): String {
        val elapsedSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val elapsed = "%d:%02d".format(minutes, seconds)
        val screens = if (screensFound == 1) "1 screen" else "$screensFound screens"
        return "$screens found - $elapsed elapsed"
    }

    /** A short current-action string, appended to progress when there is room. */
    fun action(label: String?, step: Int): String {
        val what = label?.takeIf { it.isNotBlank() }?.let { truncate(it, ACTION_LABEL_MAX_CHARS) } ?: "element"
        return "Step $step: $what"
    }

    /** Shown briefly while the crawler is recovering from a lost screen. */
    fun recovering(reason: String): String = "Recovering (${truncate(reason, REASON_MAX_CHARS)})..."

    /** Final notification text once the scan loop has returned an outcome. */
    fun finished(screenCount: Int, steps: Int, stopReason: StopReason): String {
        val screens = if (screenCount == 1) "1 screen" else "$screenCount screens"
        return "Scan complete: $screens, $steps steps (${stopReason.wire})"
    }

    /** Shown when the user or the global kill switch stopped the scan early. */
    fun stopped(screenCount: Int, steps: Int, byUser: Boolean): String {
        val screens = if (screenCount == 1) "1 screen" else "$screenCount screens"
        val who = if (byUser) "user" else "kill switch"
        return "Scan stopped by $who: $screens, $steps steps"
    }

    private fun truncate(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars - 1).trimEnd() + "…"

    private const val ACTION_LABEL_MAX_CHARS = 40
    private const val REASON_MAX_CHARS = 40
}
