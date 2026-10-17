package com.langsense.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import com.langsense.app.util.ColorMath
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 포토샵식 원형 색 고르기(2026-10 사용자 요청): 바깥 고리에서 색상, 가운데 사각형에서 채도(가로)·명도(세로).
 * 외부 라이브러리 없이 Canvas 그라데이션만 쓴다. 손가락을 고리에서 시작하면 끝까지 색상만, 사각형에서 시작하면 끝까지
 * 채도·명도만 바뀐다(중간에 영역을 넘어가도 튀지 않게).
 */
class ColorWheelView(context: Context) : View(context) {

    var onColorChanged: ((Int) -> Unit)? = null

    private var hue = 0f
    private var sat = 1f
    private var value = 1f

    private val density = resources.displayMetrics.density
    private val ringWidth = 22f * density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringWidth }
    private val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * density }
    private val knobShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f * density; color = 0x55000000
    }
    private val square = RectF()
    private var cx = 0f
    private var cy = 0f
    private var ringRadius = 0f

    /** 0 = 아직 모름, 1 = 고리, 2 = 사각형 — 누른 곳으로 정하고 뗄 때까지 유지. */
    private var dragTarget = 0

    val color: Int get() = ColorMath.hsvToRgb(hue, sat, value)

    fun setColor(c: Int) {
        val hsv = ColorMath.rgbToHsv(c)
        // 무채색은 색상이 정의되지 않으므로 지금 고리 위치를 유지한다(흰색을 넣으면 고리가 빨강으로 튀지 않게).
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        updateSquareShader()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = min(w, (280 * density).toInt())
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        ringRadius = min(w, h) / 2f - ringWidth / 2f - 4f * density
        val hues = IntArray(7) { ColorMath.hsvToRgb(it * 60f, 1f, 1f) }
        ringPaint.shader = SweepGradient(cx, cy, hues, null)
        // 고리 안쪽에 내접하는 정사각형(여백 6dp)
        val half = (ringRadius - ringWidth / 2f - 6f * density) / sqrt(2f)
        square.set(cx - half, cy - half, cx + half, cy + half)
        updateSquareShader()
    }

    private fun updateSquareShader() {
        if (square.isEmpty) return
        val pure = ColorMath.hsvToRgb(hue, 1f, 1f)
        val satShader = LinearGradient(square.left, 0f, square.right, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        val valShader = LinearGradient(0f, square.top, 0f, square.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        squarePaint.shader = ComposeShader(satShader, valShader, PorterDuff.Mode.SRC_OVER)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        canvas.drawRect(square, squarePaint)
        // 고리 손잡이
        val a = Math.toRadians(hue.toDouble())
        val hx = cx + ringRadius * Math.cos(a).toFloat()
        val hy = cy + ringRadius * Math.sin(a).toFloat()
        drawKnob(canvas, hx, hy, ringWidth / 2f)
        // 사각형 손잡이
        drawKnob(canvas, square.left + sat * square.width(), square.top + (1f - value) * square.height(), 9f * density)
    }

    private fun drawKnob(canvas: Canvas, x: Float, y: Float, r: Float) {
        canvas.drawCircle(x, y, r, knobShadow)
        knobPaint.color = Color.WHITE
        canvas.drawCircle(x, y, r, knobPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val d = hypot(x - cx, y - cy)
                dragTarget = when {
                    square.contains(x, y) -> 2
                    d in (ringRadius - ringWidth)..(ringRadius + ringWidth) -> 1
                    else -> return false
                }
                parent?.requestDisallowInterceptTouchEvent(true) // 스크롤에 손가락을 뺏기지 않게
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragTarget = 0; return true }
        }
        if (dragTarget == 1) {
            // 화면 좌표(y 아래)에서 atan2 는 시계 방향 각도 — SweepGradient 의 진행 방향과 같다.
            hue = ((Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()) + 360f) % 360f
            if (sat == 0f || value == 0f) { sat = 1f; value = 1f } // 무채색에서 고리를 만지면 그 색이 보이게
            updateSquareShader()
        } else if (dragTarget == 2) {
            sat = ((x - square.left) / square.width()).coerceIn(0f, 1f)
            value = (1f - (y - square.top) / square.height()).coerceIn(0f, 1f)
        }
        invalidate()
        onColorChanged?.invoke(color)
        return true
    }
}
