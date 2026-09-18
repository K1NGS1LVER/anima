package io.agents.anima

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.agents.anima.capture.AccessibilityExplorationDevice
import io.agents.anima.core.AppIdentity
import io.agents.anima.core.DeviceInfo
import io.agents.anima.core.StableIdEngine
import io.agents.anima.explore.Explorer
import io.agents.anima.explore.ScanBudget
import io.agents.anima.explore.ScanOrchestrator
import io.agents.anima.store.KnowledgeStore
import io.agents.anima.store.PackArchive
import io.agents.anima.understand.HeuristicUnderstander
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/**
 * **Hardware bring-up harness (S4), not a product feature.**
 *
 * `ScanService` is the real, shipping entry point, but it deliberately refuses
 * to run until `ScanEngineProvider.understander`/`.repository` are set by the
 * app's composition root -- and that root doesn't exist yet (it needs to
 * depend on `:understand` and `:store` concretely, which is `:app`'s job, and
 * nobody has wired it because the UI that would trigger a scan (N1-N4) hasn't
 * landed either). This receiver exists solely to exercise the real pipeline —
 * `Explorer` -> `ScanOrchestrator` -> `KnowledgeStore.save()` ->
 * `PackArchive.exportPack()` -- on real hardware before that UI exists, so S4
 * doesn't have to wait on N4.
 *
 * It uses [HeuristicUnderstander] (no network, no API key) rather than
 * [io.agents.anima.understand.HybridUnderstander], on purpose: this pass is
 * about proving the crawler and pack assembly work on a real device, not
 * about exercising a cloud model.
 *
 * Trigger from adb (debug build only, applicationId has the `.debug` suffix):
 * ```
 * adb shell am broadcast -a io.agents.anima.DEBUG_SCAN \
 *     -n io.agents.anima.debug/io.agents.anima.DebugScanReceiver \
 *     --es target_package com.android.settings
 * ```
 * Watch progress with `adb logcat -s DebugScan`. The finished `.animapack`
 * lands in `context.getExternalFilesDir(null)`, pullable with
 * `adb pull /sdcard/Android/data/io.agents.anima.debug/files/`.
 *
 * Remove this once N1-N4 land and a real composition root exists -- it is
 * scaffolding for a gap in sequencing, not a permanent second way to start a
 * scan.
 */
class DebugScanReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "DebugScan"
        const val ACTION_DEBUG_SCAN = "io.agents.anima.DEBUG_SCAN"
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_MAX_STEPS = "max_steps"
        const val EXTRA_MAX_SCREENS = "max_screens"

        /** True while a bring-up scan is running, so a second broadcast can't overlap it. */
        private val running = AtomicBoolean(false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_SCAN) return

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (targetPackage.isNullOrBlank()) {
            Log.e(TAG, "Missing required extra: $EXTRA_TARGET_PACKAGE")
            return
        }

        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "A bring-up scan is already running; ignoring.")
            return
        }

        val service = AnimaAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "AnimaAccessibilityService.instance is null -- enable the accessibility service first.")
            running.set(false)
            return
        }

        val budget = ScanBudget(
            maxSteps = intent.getIntExtra(EXTRA_MAX_STEPS, ScanBudget().maxSteps),
            maxScreens = intent.getIntExtra(EXTRA_MAX_SCREENS, ScanBudget().maxScreens),
        )

        val appContext = context.applicationContext
        AnimaAccessibilityService.shouldHalt.set(false)

        Thread({
            val startedAt = System.currentTimeMillis()
            try {
                Log.i(TAG, "Starting bring-up scan of $targetPackage (maxSteps=${budget.maxSteps}, maxScreens=${budget.maxScreens})")

                val identifier = StableIdEngine()
                val understander = HeuristicUnderstander()
                val repository = KnowledgeStore(appContext)

                val device = AccessibilityExplorationDevice(service, appContext)
                val explorer = Explorer(
                    device = device,
                    identifier = identifier,
                    budget = budget,
                    isAborted = { AnimaAccessibilityService.shouldHalt.get() },
                )
                val orchestrator = ScanOrchestrator(explorer, identifier, understander, repository)

                val result = orchestrator.scan(
                    app = appIdentityFor(appContext, targetPackage),
                    device = deviceInfoFor(service),
                )

                Log.i(
                    TAG,
                    "Scan finished: screens=${result.pack.screens.size} " +
                        "elements=${result.pack.screens.sumOf { it.elements.size }} " +
                        "stopReason=${result.pack.scan.coverage.stopReason} " +
                        "screenshots=${result.screenshots.size} " +
                        "durationMs=${System.currentTimeMillis() - startedAt}",
                )

                try {
                    repository.save(result.pack)
                    Log.i(TAG, "Saved to KnowledgeStore.")
                } catch (t: Throwable) {
                    Log.e(TAG, "KnowledgeStore.save() failed -- pack was produced but not persisted", t)
                }

                val outFile = outputFile(appContext, targetPackage, startedAt)
                try {
                    PackArchive.exportPack(result.pack, result.screenshots, outFile)
                    Log.i(TAG, "Exported .animapack -> ${outFile.absolutePath} (${outFile.length()} bytes)")
                } catch (t: Throwable) {
                    Log.e(TAG, "PackArchive.exportPack() failed", t)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Bring-up scan of $targetPackage crashed", t)
            } finally {
                running.set(false)
            }
        }, "DebugScan-thread").apply {
            isDaemon = true
            start()
        }
    }

    private fun outputFile(context: Context, targetPackage: String, startedAt: Long): File {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, "real-${targetPackage}-${fmt.format(startedAt)}.animapack")
    }

    /** Same shape as ScanService.appIdentityFor -- duplicated rather than shared across modules for a one-off debug tool. */
    private fun appIdentityFor(context: Context, targetPackage: String): AppIdentity = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(targetPackage, 0)
        val appInfo = pm.getApplicationInfo(targetPackage, 0)
        AppIdentity(
            packageName = targetPackage,
            label = pm.getApplicationLabel(appInfo).toString(),
            versionName = info.versionName,
            versionCode = info.longVersionCode,
        )
    } catch (e: Exception) {
        Log.w(TAG, "Could not read package info for $targetPackage; using a minimal AppIdentity", e)
        AppIdentity(packageName = targetPackage, label = targetPackage, versionName = null, versionCode = null)
    }

    private fun deviceInfoFor(service: AnimaAccessibilityService): DeviceInfo {
        val (width, height) = service.displaySize()
        return DeviceInfo(
            model = Build.MODEL ?: "unknown",
            sdk = Build.VERSION.SDK_INT,
            resolution = listOf(width, height),
            locale = Locale.getDefault().toString(),
        )
    }
}
