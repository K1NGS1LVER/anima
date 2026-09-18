package io.agents.anima.explore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.agents.anima.AnimaAccessibilityService
import io.agents.anima.capture.AccessibilityExplorationDevice
import io.agents.anima.core.ElementAction
import io.agents.anima.core.ScreenObservation
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that runs one [Explorer] scan to completion.
 *
 * A scan can run for up to [ScanBudget.maxWallClockMs] (4 minutes by default).
 * A plain background thread doing that is exactly what Android's background
 * execution limits exist to kill, so this owns a persistent notification and
 * a foreground service type for the whole run, following the same
 * notification conventions as [io.agents.anima.FloatingOverlayService]
 * (`IMPORTANCE_LOW` channel, `Notification.Builder`, `startForeground`).
 *
 * Not a bound service in this version: callers start it with [start] and stop
 * it with [stop] (or the notification's own Stop action). A later controller
 * (S3) may bind to it or talk to it over broadcasts -- this service does not
 * assume that shape.
 */
class ScanService : Service() {

    companion object {
        private const val TAG = "ScanService"

        private const val CHANNEL_ID = "anima_scan_channel"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "io.agents.anima.explore.action.START_SCAN"
        const val ACTION_STOP = "io.agents.anima.explore.action.STOP_SCAN"

        const val EXTRA_TARGET_PACKAGE = "io.agents.anima.explore.extra.TARGET_PACKAGE"
        const val EXTRA_MAX_STEPS = "io.agents.anima.explore.extra.MAX_STEPS"
        const val EXTRA_MAX_SCREENS = "io.agents.anima.explore.extra.MAX_SCREENS"
        const val EXTRA_MAX_DEPTH = "io.agents.anima.explore.extra.MAX_DEPTH"
        const val EXTRA_MAX_WALL_CLOCK_MS = "io.agents.anima.explore.extra.MAX_WALL_CLOCK_MS"
        const val EXTRA_NOVELTY_DECAY_LIMIT = "io.agents.anima.explore.extra.NOVELTY_DECAY_LIMIT"

        /**
         * Starts a scan of [targetPackage]. Extras encode the individual
         * [ScanBudget] fields rather than the data class itself -- `ScanBudget`
         * is not `Parcelable`/`Serializable` and doesn't need to become either
         * just to cross an Intent.
         */
        fun start(context: Context, targetPackage: String, budget: ScanBudget = ScanBudget()) {
            val intent = Intent(context, ScanService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                putExtra(EXTRA_MAX_STEPS, budget.maxSteps)
                putExtra(EXTRA_MAX_SCREENS, budget.maxScreens)
                putExtra(EXTRA_MAX_DEPTH, budget.maxDepth)
                putExtra(EXTRA_MAX_WALL_CLOCK_MS, budget.maxWallClockMs)
                putExtra(EXTRA_NOVELTY_DECAY_LIMIT, budget.noveltyDecayLimit)
            }
            context.startForegroundService(intent)
        }

        /** Requests a stop. See [ScanService.handleStopAction] for what "stop" means here. */
        fun stop(context: Context) {
            val intent = Intent(context, ScanService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        private fun budgetFromIntent(intent: Intent): ScanBudget {
            val defaults = ScanBudget()
            return ScanBudget(
                maxSteps = intent.getIntExtra(EXTRA_MAX_STEPS, defaults.maxSteps),
                maxScreens = intent.getIntExtra(EXTRA_MAX_SCREENS, defaults.maxScreens),
                maxDepth = intent.getIntExtra(EXTRA_MAX_DEPTH, defaults.maxDepth),
                maxWallClockMs = intent.getLongExtra(EXTRA_MAX_WALL_CLOCK_MS, defaults.maxWallClockMs),
                noveltyDecayLimit = intent.getIntExtra(EXTRA_NOVELTY_DECAY_LIMIT, defaults.noveltyDecayLimit),
            )
        }
    }

    /**
     * Set the moment a Stop action (notification button or [stop]) is handled.
     * Lets the final notification say "you stopped this" instead of "the
     * global kill switch stopped this" -- those are different events worth
     * telling apart, even though both end the scan via the same [Explorer]
     * `isAborted` check.
     */
    private val localStopRequested = AtomicBoolean(false)

    private var scanThread: Thread? = null
    private var scanStartedAt: Long = 0L
    private var currentTargetPackage: String? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> handleStopAction()
            ACTION_START -> handleStartAction(intent)
            else -> {
                Log.w(TAG, "ScanService started with no recognized action (${intent?.action}); stopping.")
                stopSelf()
            }
        }
        // We never want the system to resurrect this service on its own and
        // silently re-launch a scan the user never asked to resume.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scanThread = null
    }

    private fun handleStartAction(intent: Intent) {
        if (scanThread?.isAlive == true) {
            Log.w(TAG, "ScanService received ACTION_START while a scan is already running; ignoring.")
            return
        }

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (targetPackage.isNullOrBlank()) {
            Log.e(TAG, "ScanService started without ${EXTRA_TARGET_PACKAGE}; nothing to scan. Stopping.")
            stopSelf()
            return
        }

        val understander = ScanEngineProvider.understander
        val repository = ScanEngineProvider.repository
        if (understander == null || repository == null) {
            Log.e(
                TAG,
                "ScanEngineProvider.understander or .repository is unset -- the app's composition " +
                    "root must configure ScanEngineProvider before starting a scan. Refusing to run " +
                    "a scan whose output could never be saved. Stopping.",
            )
            stopSelf()
            return
        }

        val accessibilityService = AnimaAccessibilityService.instance
        if (accessibilityService == null) {
            Log.e(
                TAG,
                "AnimaAccessibilityService.instance is null -- the accessibility service is not " +
                    "connected (not enabled by the user, or not started yet). Cannot drive a scan. Stopping.",
            )
            stopSelf()
            return
        }

        val budget = budgetFromIntent(intent)
        localStopRequested.set(false)
        scanStartedAt = System.currentTimeMillis()
        currentTargetPackage = targetPackage

        startForeground(NOTIFICATION_ID, buildNotification(ScanNotificationText.starting(targetPackage)))

        val device = AccessibilityExplorationDevice(accessibilityService, applicationContext)
        val explorer = Explorer(
            device = device,
            identifier = ScanEngineProvider.identifier,
            budget = budget,
            listener = notificationListener(),
            isAborted = { AnimaAccessibilityService.shouldHalt.get() || localStopRequested.get() },
        )

        // Not a coroutine: :explore has no kotlinx-coroutines dependency (only
        // :capture does), and adding one just for this single background call
        // would be a new dependency for no real benefit over a plain Thread.
        scanThread = Thread({
            try {
                explorer.scan(targetPackage)
            } catch (t: Throwable) {
                Log.e(TAG, "Scan of $targetPackage crashed", t)
            } finally {
                stopSelf()
            }
        }, "ScanService-scan-thread").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Handles both the notification's Stop button and [stop]: sets the local
     * flag (so the final notification can say "you stopped this") *and* the
     * accessibility service's global kill switch (the same one the floating
     * overlay's "Take Control" button uses), per the spec -- a scan abort
     * should always be a real abort, not just a locally-scoped one that leaves
     * gesture dispatch elsewhere still thinking it's clear to act.
     *
     * The foreground service itself is stopped by the scan thread noticing
     * `isAborted()` and returning, not here -- there is no scan to stop if
     * ACTION_STOP arrives with none running, and calling stopSelf() twice is
     * harmless but pointless.
     */
    private fun handleStopAction() {
        Log.i(TAG, "ScanService: stop requested.")
        localStopRequested.set(true)
        AnimaAccessibilityService.shouldHalt.set(true)
        if (scanThread?.isAlive != true) {
            // Nothing running to notice the flag; stop the (idle) foreground service ourselves.
            stopSelf()
        }
    }

    private fun notificationListener(): ScanListener = object : ScanListener {
        override fun onScanStarted(targetPackage: String) {
            updateNotification(ScanNotificationText.starting(targetPackage))
        }

        override fun onScreenDiscovered(screenId: String, observation: ScreenObservation, total: Int) {
            updateNotification(ScanNotificationText.progress(total, System.currentTimeMillis() - scanStartedAt))
        }

        override fun onAction(action: ElementAction, label: String?, step: Int) {
            updateNotification(ScanNotificationText.action(label, step))
        }

        override fun onRecovering(reason: String) {
            updateNotification(ScanNotificationText.recovering(reason))
        }

        override fun onScanFinished(outcome: ScanOutcome) {
            // TODO(integration): ScanOrchestrator (S1) lands separately in a
            // parallel worktree and doesn't exist here yet. This service's job
            // stops at producing a clean ScanOutcome; turning it into a
            // KnowledgePack (via ScanEngineProvider.understander) and saving it
            // (via ScanEngineProvider.repository) is the next integration step
            // once both S1 and this land. Deliberately not guessing at
            // ScanOrchestrator's constructor here -- that would fail to compile
            // now and likely again on merge if the guess is wrong.
            Log.i(
                TAG,
                "Scan finished: target=${outcome.targetPackage} screens=${outcome.screenCount} " +
                    "steps=${outcome.steps} stopReason=${outcome.stopReason.wire} " +
                    "durationMs=${outcome.durationMs} frontierRemaining=${outcome.frontierRemaining} " +
                    "skipped=${outcome.skipped.size}",
            )

            val finalText = if (outcome.stopReason == StopReason.ABORTED) {
                ScanNotificationText.stopped(outcome.screenCount, outcome.steps, byUser = localStopRequested.get())
            } else {
                ScanNotificationText.finished(outcome.screenCount, outcome.steps, outcome.stopReason)
            }
            updateNotification(finalText, showStopAction = false)

            // The scan thread calls stopSelf() right after this listener returns
            // (see the `finally` block in handleStartAction). A plain stopSelf()
            // on a foreground service also cancels its notification, which would
            // make this completion text disappear the instant it appears.
            // Detaching first keeps it on screen as an ordinary notification.
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    private fun updateNotification(text: String, showStopAction: Boolean = true) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, showStopAction))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Anima App Scan",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progress of an autonomous exploration scan"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, showStopAction: Boolean = true): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(ScanNotificationText.title(currentTargetPackageOrUnknown()))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(showStopAction)
            .setOnlyAlertOnce(true)

        if (showStopAction) {
            val stopIntent = Intent(this, ScanService::class.java).apply { action = ACTION_STOP }
            val stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopPendingIntent,
                ).build(),
            )
        }

        return builder.build()
    }

    private fun currentTargetPackageOrUnknown(): String = currentTargetPackage ?: "target app"
}
