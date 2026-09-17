package io.agents.anima

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.agents.anima.databinding.ActivityMainBinding
import io.agents.anima.databinding.ItemActResultBinding
import io.agents.anima.engine.AccessibilityDevice
import io.agents.anima.engine.AnimaEngine
import io.agents.anima.engine.ExecutionResult
import io.agents.anima.engine.IntentRouter
import java.util.Locale

/**
 * MainActivity — the judge-facing onboarding screen.
 *
 * Four sections: brand header, a live permission gate (Accessibility Service +
 * Display Over Other Apps), a single-goal runner against the real accessibility
 * device, and the 3-act hackathon demo with a live instrument-panel scoreboard.
 *
 * The 3-act demo runs against a bundled hermetic fixture (see AnimaEngine.
 * runThreeActDemo) and deliberately needs neither permission, so the headline
 * demo always works on stage even if permission-granting is fumbled live.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var engine: AnimaEngine

    private var isDemoRunning = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, getString(R.string.msg_notifications_denied), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine = AnimaEngine(applicationContext)

        binding.btnAccessibilityEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlayEnable.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnRunGoal.setOnClickListener { runGoal() }
        binding.btnRunDemo.setOnClickListener { runDemo() }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    // ---------------------------------------------------------------------
    // Permission gate
    // ---------------------------------------------------------------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * NOTE: applicationId is `io.agents.anima` in release and `io.agents.anima.debug`
     * in debug builds, but the accessibility service class name never changes.
     * Always resolve against the runtime `packageName`, never a hardcoded id.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("$packageName/io.agents.anima.AnimaAccessibilityService")
    }

    private fun isOverlayPermissionGranted(): Boolean = Settings.canDrawOverlays(this)

    private fun refreshPermissionState() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        setStatusRow(binding.dotAccessibility, binding.tvAccessibilityStatus, accessibilityEnabled)

        val overlayEnabled = isOverlayPermissionGranted()
        setStatusRow(binding.dotOverlay, binding.tvOverlayStatus, overlayEnabled)

        binding.btnRunGoal.isEnabled = accessibilityEnabled && !isDemoRunning
        binding.tvGoalHint.visibility = if (accessibilityEnabled) View.GONE else View.VISIBLE
    }

    private fun setStatusRow(dot: View, statusText: TextView, enabled: Boolean) {
        dot.setBackgroundResource(
            if (enabled) R.drawable.bg_status_dot_enabled else R.drawable.bg_status_dot_disabled
        )
        statusText.text = getString(if (enabled) R.string.status_enabled else R.string.status_disabled)
        statusText.setTextColor(
            ContextCompat.getColor(this, if (enabled) R.color.green_400 else R.color.red_500)
        )
    }

    // ---------------------------------------------------------------------
    // Run a goal
    // ---------------------------------------------------------------------

    private fun runGoal() {
        val service = AnimaAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.msg_accessibility_service_disabled), Toast.LENGTH_LONG).show()
            return
        }
        val goal = binding.etGoal.text?.toString()?.trim().orEmpty()
        if (goal.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_goal_empty), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRunGoal.isEnabled = false
        binding.btnRunGoal.text = getString(R.string.btn_running)

        // Layer 1 deterministic fast-path: a GUI agent can only act on what is
        // in front of it, and pressing RUN leaves *this* screen in front. Route
        // the goal to the system screen that owns the setting first.
        val destination = IntentRouter.destinationFor(goal)
        if (destination != null) {
            Toast.makeText(
                this,
                getString(R.string.msg_routing_to, destination.label),
                Toast.LENGTH_SHORT,
            ).show()
        }

        Thread {
            val routed = destination != null && IntentRouter.launch(this, destination)
            if (routed) Thread.sleep(SCREEN_SETTLE_MS)

            val result = engine.run(goal, AccessibilityDevice(service))
            runOnUiThread {
                binding.btnRunGoal.text = getString(R.string.btn_run)
                binding.btnRunGoal.isEnabled = isAccessibilityServiceEnabled()
                val message = if (!routed && service.isOwnUiInForeground()) {
                    // Anima's own UI is excluded from capture, so there is
                    // genuinely nothing to act on -- say so plainly instead of
                    // reporting an opaque planning failure.
                    getString(R.string.msg_open_target_app)
                } else {
                    result.message
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // ---------------------------------------------------------------------
    // 3-act demo + live scoreboard
    // ---------------------------------------------------------------------

    private fun runDemo() {
        isDemoRunning = true
        binding.btnRunDemo.isEnabled = false
        binding.btnRunDemo.text = getString(R.string.btn_demo_running)
        binding.btnRunGoal.isEnabled = false
        binding.tvDemoEmpty.visibility = View.GONE
        binding.containerActResults.removeAllViews()
        binding.tvDemoSummary.text = ""

        Thread {
            val results = engine.runThreeActDemo { actName, result ->
                runOnUiThread { appendActResult(actName, result) }
            }
            runOnUiThread {
                isDemoRunning = false
                binding.btnRunDemo.isEnabled = true
                binding.btnRunDemo.text = getString(R.string.btn_run_demo)
                binding.btnRunGoal.isEnabled = isAccessibilityServiceEnabled()
                renderSummary(results)
            }
        }.start()
    }

    private fun appendActResult(actName: String, result: ExecutionResult) {
        val item = ItemActResultBinding.inflate(layoutInflater, binding.containerActResults, false)
        item.tvActName.text = actName
        item.tvActMode.text = result.mode
        item.tvActLatency.text = formatLatency(result.latencyMs)
        item.tvActLlmCalls.text = result.llmCalls.toString()
        item.tvActCost.text = formatCost(result.llmCalls)
        item.dotActStatus.setBackgroundResource(
            if (result.success) R.drawable.bg_status_dot_enabled else R.drawable.bg_status_dot_disabled
        )
        binding.containerActResults.addView(item.root)
    }

    private fun renderSummary(results: List<ExecutionResult>) {
        if (results.isEmpty()) return
        val totalLatencyMs = results.sumOf { it.latencyMs }
        val totalLlmCalls = results.sumOf { it.llmCalls }
        binding.tvDemoSummary.text = getString(
            R.string.demo_summary_format,
            formatLatency(totalLatencyMs),
            formatCost(totalLlmCalls)
        )
    }

    private fun formatLatency(ms: Long): String =
        if (ms >= 1000) String.format(Locale.US, "%.2fs", ms / 1000.0) else "${ms}ms"

    private fun formatCost(llmCalls: Int): String {
        val cost = llmCalls * COST_PER_LLM_CALL
        return if (cost == 0.0) "$0.00" else String.format(Locale.US, "$%.4f", cost)
    }

    companion object {
        /**
         * How long to let a routed system screen render before the agent looks
         * at it. Too short and the capture sees the outgoing activity.
         */
        private const val SCREEN_SETTLE_MS = 1200L

        // One pruned-DOM planner call is ~1,200 tokens. Keep this in step with the
        // figure anima.py prints in its benchmark, so the on-phone scoreboard and
        // the laptop CLI never quote different numbers at the same judge.
        private const val COST_PER_LLM_CALL = 0.0024
    }
}
