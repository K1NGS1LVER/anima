package io.agents.anima

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** One-time launch transition: the product name becomes the scan-control world. */
class AnimaBurstView(context: Context, private val onFinished: () -> Unit) : View(context) {
    private val word = "ANIMA"
    private val type = Typeface.create("sans-serif", Typeface.BOLD)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = type; textAlign = Paint.Align.CENTER }
    private val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val colors = intArrayOf(Color.WHITE, Color.rgb(239, 242, 246), Color.rgb(186, 203, 255), Color.rgb(232, 185, 232), Color.rgb(242, 194, 184), Color.rgb(212, 240, 217))
    private var start = 0L
    private var burstAt = Long.MAX_VALUE
    private var finished = false
    private val durationScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    private val easing = PathInterpolator(.16f, 1f, .3f, 1f)

    init { setBackgroundColor(Color.BLACK); contentDescription = "Anima launch animation. Tap to continue."; isFocusable = true }
    fun start() { start = System.currentTimeMillis(); if (durationScale == 0f) { finish() } else postInvalidateOnAnimation() }
    override fun onTouchEvent(event: MotionEvent): Boolean { if (event.action == MotionEvent.ACTION_UP && !finished) triggerBurst(); return true }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); if (finished || start == 0L) return
        val now = System.currentTimeMillis(); val elapsed = (now - start) / durationScale
        val cx = width / 2f; val cy = height / 2f
        text.textSize = max(44f * resources.displayMetrics.scaledDensity, width * .17f)
        val totalWidth = text.measureText(word); val startX = cx - totalWidth / 2f
        var x = startX
        word.forEachIndexed { index, character ->
            val letterWidth = text.measureText(character.toString())
            val p = ((elapsed - index * 105f) / 600f).coerceIn(0f, 1f)
            val reveal = easing.getInterpolation(p)
            val burstProgress = if (burstAt == Long.MAX_VALUE) 0f else ((now - burstAt) / (260f * durationScale)).coerceIn(0f, 1f)
            text.alpha = ((1f - burstProgress) * 255).toInt()
            canvas.save(); canvas.translate(x + letterWidth / 2f, cy + (1f - reveal) * 24f * resources.displayMetrics.density); canvas.scale(.8f + .2f * reveal, .8f + .2f * reveal); canvas.drawText(character.toString(), 0f, 0f, text); canvas.restore()
            x += letterWidth
        }
        if (elapsed > 1250f && burstAt == Long.MAX_VALUE) triggerBurst()
        if (burstAt != Long.MAX_VALUE) drawBurst(canvas, cx, cy, now)
        if (!finished) postInvalidateOnAnimation()
    }
    private fun triggerBurst() { if (burstAt == Long.MAX_VALUE) { burstAt = System.currentTimeMillis(); performClick() } }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun drawBurst(canvas: Canvas, cx: Float, cy: Float, now: Long) {
        val p = ((now - burstAt) / (780f * durationScale)).coerceIn(0f, 1f)
        val ease = 1f - (1f - p) * (1f - p)
        val fade = if (p < .72f) 1f else 1f - ((p - .72f) / .28f)
        val radius = max(width, height) * (0.25f + ease * .9f)
        repeat(84) { index ->
            val angle = ((index * 137.508f) % 360f) * Math.PI.toFloat() / 180f
            val seed = ((index * 17) % 11) / 10f
            ray.color = colors[index % colors.size]; ray.alpha = (fade * 230).toInt(); ray.strokeWidth = (1.5f + (index % 3)) * resources.displayMetrics.density
            val distance = radius * (.32f + seed * .62f); val length = (16f + ease * 110f) * resources.displayMetrics.density
            val dx = cos(angle.toDouble()).toFloat(); val dy = sin(angle.toDouble()).toFloat()
            canvas.drawLine(cx + dx * distance, cy + dy * distance, cx + dx * (distance + length), cy + dy * (distance + length), ray)
        }
        if (p >= 1f) finish()
    }
    private fun finish() { if (!finished) { finished = true; onFinished() } }
}
