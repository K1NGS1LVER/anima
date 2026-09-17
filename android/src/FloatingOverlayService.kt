package io.agents.anima

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * FloatingOverlayService
 *
 * Foreground service managing the floating HUD overlay and emergency kill-switch.
 * Provides real-time execution feedback (Replay vs Cold LLM, latency, tokens saved)
 * and guarantees Human-in-the-Loop (HITL) physical safety with an instant stop trigger.
 */
class FloatingOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "anima_agent_hud_channel"
        private const val NOTIFICATION_ID = 1001
        private var instance: FloatingOverlayService? = null

        fun updateState(status: String) {
            instance?.statusTextView?.text = "State: $status"
        }

        fun updateScoreboard(llmCalls: Int, latencyMs: Long) {
            instance?.scoreboardTextView?.text = "LLMs: $llmCalls | Latency: ${latencyMs}ms"
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var statusTextView: TextView? = null
    private var scoreboardTextView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundServiceWithNotification()
        setupFloatingHUD()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null && windowManager != null) {
            windowManager?.removeView(overlayView)
        }
        instance = null
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Anima Autonomous HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running Anima Agent Floating Controller"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Anima Runtime Active")
                .setContentText("Autonomous mobile agent is listening for commands")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupFloatingHUD() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E60F172A")) // Dark theme slate-900 with 90% alpha
            setPadding(24, 18, 24, 18)
        }

        val titleView = TextView(this).apply {
            text = "⚡️ Anima Agent HUD"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 14f
            paint.isFakeBoldText = true
        }
        container.addView(titleView)

        statusTextView = TextView(this).apply {
            text = "State: IDLE"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        container.addView(statusTextView)

        scoreboardTextView = TextView(this).apply {
            text = "LLMs: 0 | Latency: 0ms"
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 11f
        }
        container.addView(scoreboardTextView)

        // Emergency Kill-Switch Button
        val stopButton = Button(this).apply {
            text = "STOP"
            setBackgroundColor(Color.parseColor("#EF4444")) // Crimson red
            setTextColor(Color.WHITE)
            textSize = 11f
            setOnClickListener {
                AnimaAccessibilityService.instance?.emergencyHalt()
                statusTextView?.text = "State: ABORTED BY USER"
            }
        }
        container.addView(stopButton)

        // Dragging gesture listener
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(container, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        overlayView = container
        windowManager?.addView(overlayView, layoutParams)
    }
}
