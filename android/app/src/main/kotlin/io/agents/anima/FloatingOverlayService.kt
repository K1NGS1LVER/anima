package io.agents.anima

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * FloatingOverlayService
 *
 * Implements a Google Gemini-inspired Mobile Screen Overlay system:
 * 1. Edge-Glow Ambient Rim: A full-screen non-touchable border overlay that casts
 *    an animated, luminous cyan/indigo gradient glow around the phone's bezels,
 *    visually indicating to the user that the Anima agent has taken autonomous control.
 * 2. Bottom Floating Island: A frosted translucent capsule anchored at the screen bottom
 *    displaying live agent status, token/latency scoreboard, and an instant "Take Control"
 *    emergency kill-switch button.
 */
class FloatingOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "anima_agent_hud_channel"
        private const val NOTIFICATION_ID = 1001
        private var instance: FloatingOverlayService? = null

        fun showAgentControl(goal: String, isReplay: Boolean = true) {
            val inst = instance ?: return
            Handler(Looper.getMainLooper()).post {
                inst.edgeGlowView?.visibility = View.VISIBLE
                inst.edgeGlowView?.startPulsing()
                inst.goalTitleView?.text = "⚡️ " + if (isReplay) "Replaying Skill" else "Reasoning Cold Run"
                inst.statusTextView?.text = goal
                inst.scoreboardTextView?.text = if (isReplay) "0 LLM Calls • ~0.001s • 100% Free" else "1 LLM Call • UIFormer Pruned"
                inst.bottomPillView?.visibility = View.VISIBLE
            }
        }

        fun updateStep(action: String, targetDesc: String) {
            val inst = instance ?: return
            Handler(Looper.getMainLooper()).post {
                inst.statusTextView?.text = "$action: $targetDesc"
            }
        }

        fun showCompletion(latencyMs: Long, llmCalls: Int) {
            val inst = instance ?: return
            Handler(Looper.getMainLooper()).post {
                inst.goalTitleView?.text = "✅ Task Completed"
                inst.scoreboardTextView?.text = "${llmCalls} LLMs • ${latencyMs}ms"
                inst.edgeGlowView?.stopPulsing()
                Handler(Looper.getMainLooper()).postDelayed({
                    inst.edgeGlowView?.visibility = View.GONE
                    inst.bottomPillView?.visibility = View.GONE
                }, 2500)
            }
        }

        fun releaseControlToUser(reason: String = "User Took Control") {
            val inst = instance ?: return
            Handler(Looper.getMainLooper()).post {
                inst.edgeGlowView?.stopPulsing()
                inst.edgeGlowView?.visibility = View.GONE
                inst.goalTitleView?.text = "🛑 $reason"
                inst.statusTextView?.text = "Autonomous execution halted"
                Handler(Looper.getMainLooper()).postDelayed({
                    inst.bottomPillView?.visibility = View.GONE
                }, 2000)
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var edgeGlowView: EdgeGlowView? = null
    private var bottomPillView: View? = null
    private var goalTitleView: TextView? = null
    private var statusTextView: TextView? = null
    private var scoreboardTextView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundServiceWithNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupEdgeGlowOverlay()
        setupBottomIslandCapsule()
    }

    override fun onDestroy() {
        super.onDestroy()
        edgeGlowView?.stopPulsing()
        if (edgeGlowView != null && windowManager != null) {
            windowManager?.removeView(edgeGlowView)
        }
        if (bottomPillView != null && windowManager != null) {
            windowManager?.removeView(bottomPillView)
        }
        instance = null
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Anima Autonomous Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays Gemini-style agent control overlay"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Anima Screen Overlay Active")
                .setContentText("Autonomous mobile agent is ready to execute")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Sets up the full-screen ambient perimeter glow that mimics Google Gemini's screen-takeover aura.
     * Crucially uses FLAG_NOT_TOUCHABLE so synthetic taps pass through unhindered to underlying apps.
     */
    private fun setupEdgeGlowOverlay() {
        val edgeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        edgeGlowView = EdgeGlowView(this).apply {
            visibility = View.GONE
        }
        windowManager?.addView(edgeGlowView, edgeParams)
    }

    /**
     * Sets up the bottom floating island card with frosted dark styling and instant "Take Control" button.
     */
    private fun setupBottomIslandCapsule() {
        val pillParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
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
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80 // Offset above the Android home navigation gesture bar
        }

        // Frosted glass dark capsule layout
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 20, 24, 20)

            // Rounded pill shape with dark slate background and subtle cyan border
            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 64f
                setColor(Color.parseColor("#E60F172A")) // Slate-900 with 90% opacity
                setStroke(3, Color.parseColor("#38BDF8")) // Light cyan rim
            }
            background = backgroundDrawable
        }

        // Text information column
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        goalTitleView = TextView(this).apply {
            text = "⚡️ Anima Agent Active"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            paint.isFakeBoldText = true
        }
        textCol.addView(goalTitleView)

        statusTextView = TextView(this).apply {
            text = "Waiting for instructions..."
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        textCol.addView(statusTextView)

        scoreboardTextView = TextView(this).apply {
            text = "0 LLM Calls • Sub-millisecond"
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 10f
        }
        textCol.addView(scoreboardTextView)

        container.addView(textCol)

        // Instant "Take Control / Stop" Emergency Button
        val stopButton = Button(this).apply {
            text = "Take Control"
            textSize = 11f
            setTextColor(Color.WHITE)
            isAllCaps = false
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 48f
                setColor(Color.parseColor("#EF4444")) // Vibrant Crimson
            }
            background = btnBg
            setPadding(28, 12, 28, 12)
            setOnClickListener {
                AnimaAccessibilityService.instance?.emergencyHalt()
                releaseControlToUser("User Took Control")
            }
        }
        container.addView(stopButton)

        bottomPillView = container
        bottomPillView?.visibility = View.GONE
        windowManager?.addView(bottomPillView, pillParams)
    }

    /**
     * Custom view that renders an animated luminous perimeter edge glow around the screen.
     */
    class EdgeGlowView(context: Context) : View(context) {
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f // 14px thick ambient edge lighting
        }
        private var pulseAlpha = 220
        private var animator: ValueAnimator? = null

        fun startPulsing() {
            animator?.cancel()
            animator = ValueAnimator.ofInt(120, 255).apply {
                duration = 900
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    pulseAlpha = it.animatedValue as Int
                    invalidate()
                }
                start()
            }
        }

        fun stopPulsing() {
            animator?.cancel()
            animator = null
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            // Gemini gradient: Cyan -> Royal Blue -> Violet -> Cyan
            borderPaint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(
                    Color.argb(pulseAlpha, 56, 189, 248),  // Sky Blue
                    Color.argb(pulseAlpha, 99, 102, 241),  // Indigo
                    Color.argb(pulseAlpha, 168, 85, 247),  // Purple
                    Color.argb(pulseAlpha, 56, 189, 248)   // Sky Blue
                ),
                null,
                Shader.TileMode.CLAMP
            )

            // Draw border inset by half the stroke width to hug display edges
            val inset = borderPaint.strokeWidth / 2f
            canvas.drawRoundRect(
                inset, inset, w - inset, h - inset,
                48f, 48f, // Rounded screen corners (modern phone display shape)
                borderPaint
            )
        }
    }
}
