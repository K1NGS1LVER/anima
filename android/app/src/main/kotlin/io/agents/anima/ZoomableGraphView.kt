package io.agents.anima

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/** Small, dependency-free map canvas. Real pack graph nodes replace the preview nodes at G4. */
class ZoomableGraphView(context: Context) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(186, 203, 255); strokeWidth = 3f }
    private val node = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18, 20, 25) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 185, 232); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f }
    private var scale = 1f; private var offsetX = 0f; private var offsetY = 0f; private var lastX = 0f; private var lastY = 0f
    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() { override fun onScale(detector: ScaleGestureDetector): Boolean { scale = (scale * detector.scaleFactor).coerceIn(.65f, 2.4f); invalidate(); return true } })
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawColor(Color.rgb(10, 11, 14)); canvas.save(); canvas.translate(offsetX, offsetY); canvas.scale(scale, scale); val points = arrayOf(90f to 120f, 300f to 90f, 300f to 250f, 510f to 170f); canvas.drawLine(130f, 120f, 260f, 90f, line); canvas.drawLine(130f, 120f, 260f, 250f, line); canvas.drawLine(340f, 90f, 470f, 170f, line); points.forEachIndexed { index, point -> canvas.drawRoundRect(point.first - 42, point.second - 30, point.first + 42, point.second + 30, 16f, 16f, node); canvas.drawRoundRect(point.first - 42, point.second - 30, point.first + 42, point.second + 30, 16f, 16f, rim); canvas.drawText(listOf("Home", "Wi‑Fi", "Bluetooth", "Details")[index], point.first - 32, point.second + 8, text) }; canvas.restore() }
    override fun onTouchEvent(event: MotionEvent): Boolean { pinch.onTouchEvent(event); if (event.pointerCount > 1) return true; when (event.actionMasked) { MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }; MotionEvent.ACTION_MOVE -> { offsetX += event.x - lastX; offsetY += event.y - lastY; lastX = event.x; lastY = event.y; invalidate() } }; return true }
}
