package io.agents.anima

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.agents.anima.engine.AccessibilityDevice
import io.agents.anima.engine.AnimaEngine
import org.json.JSONObject

/**
 * TaskerReceiver
 *
 * External automation gateway implementing the PokeClaw Intent Interface.
 * Enables Tasker, MacroDroid, Termux, and ADB scripts to invoke Anima agentic tasks
 * without requiring desktop tethering or manual interaction.
 *
 * Example ADB invocation:
 * adb shell am broadcast -a io.agents.anima.RUN_TASK --es goal "Set alarm for 7:30 AM" --es params '{"time":"07:30"}'
 */
class TaskerReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AnimaTasker"
        const val ACTION_RUN_TASK = "io.agents.anima.RUN_TASK"
        const val ACTION_TASK_COMPLETED = "io.agents.anima.TASK_COMPLETED"
        const val EXTRA_GOAL = "goal"
        const val EXTRA_PARAMS = "params"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_LATENCY = "latency_ms"
        const val EXTRA_LLM_CALLS = "llm_calls"

        /**
         * A receiver instance is recreated for every broadcast, so the engine (and with it the
         * SQLite skill library) is cached process-wide instead of reopened per task.
         */
        @Volatile
        private var sharedEngine: AnimaEngine? = null

        fun engine(context: Context): AnimaEngine {
            val existing = sharedEngine
            if (existing != null) return existing
            return synchronized(this) {
                sharedEngine ?: AnimaEngine(context.applicationContext).also { sharedEngine = it }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_TASK) return

        val goal = intent.getStringExtra(EXTRA_GOAL)
        if (goal.isNullOrBlank()) {
            Log.e(TAG, "Received RUN_TASK without required 'goal' extra.")
            broadcastResult(context, success = false, message = "Missing goal")
            return
        }

        val paramsJson = intent.getStringExtra(EXTRA_PARAMS)
        val paramsMap = mutableMapOf<String, String>()
        if (!paramsJson.isNullOrBlank()) {
            try {
                val json = JSONObject(paramsJson)
                json.keys().forEach { key ->
                    paramsMap[key] = json.optString(key)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse params JSON: $paramsJson", e)
            }
        }

        Log.i(TAG, "Received autonomous task: '$goal' with params: $paramsMap")

        val service = AnimaAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "Cannot run task: AnimaAccessibilityService is not enabled or running.")
            broadcastResult(context, success = false, message = "Accessibility service disabled")
            return
        }

        // Trigger Gemini-style Edge Glow & Bottom Island Overlay
        FloatingOverlayService.showAgentControl(goal, isReplay = true)

        // Execute task asynchronously on device execution thread
        Thread {
            val startTime = System.currentTimeMillis()
            try {
                AnimaAccessibilityService.isExecuting.set(true)
                AnimaAccessibilityService.shouldHalt.set(false)

                // Execute on the real device through the on-device Anima engine:
                // warm speculative replay when a compiled skill matches, otherwise a cold
                // heuristic plan that is auto-compiled into the SQLite skill library.
                // Zero network calls.
                val device = AccessibilityDevice(service)
                val result = engine(context).run(goal, device, paramsMap)

                val latencyMs = result.latencyMs
                Log.i(
                    TAG,
                    "Task '$goal' -> ${result.mode} success=${result.success} " +
                        "steps=${result.stepsExecuted} llmCalls=${result.llmCalls} " +
                        "latency=${latencyMs}ms :: ${result.message}"
                )

                if (result.success) {
                    FloatingOverlayService.showCompletion(
                        latencyMs = latencyMs,
                        llmCalls = result.llmCalls
                    )
                } else {
                    FloatingOverlayService.releaseControlToUser(result.message)
                }

                broadcastResult(
                    context,
                    success = result.success,
                    latencyMs = latencyMs,
                    llmCalls = result.llmCalls,
                    message = result.message
                )
            } catch (e: Exception) {
                Log.e(TAG, "Task execution failed", e)
                FloatingOverlayService.releaseControlToUser("Error: ${e.message}")
                broadcastResult(
                    context,
                    success = false,
                    latencyMs = System.currentTimeMillis() - startTime,
                    message = e.message
                )
            } finally {
                AnimaAccessibilityService.isExecuting.set(false)
            }
        }.start()
    }

    private fun broadcastResult(
        context: Context,
        success: Boolean,
        latencyMs: Long = 0,
        llmCalls: Int = 0,
        message: String? = null
    ) {
        val resultIntent = Intent(ACTION_TASK_COMPLETED).apply {
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_LATENCY, latencyMs)
            putExtra(EXTRA_LLM_CALLS, llmCalls)
            if (message != null) putExtra("message", message)
            setPackage(context.packageName)
        }
        context.sendBroadcast(resultIntent)
    }
}
