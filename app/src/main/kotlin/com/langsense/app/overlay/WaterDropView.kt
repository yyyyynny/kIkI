package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * 물방울(teardrop) 모양 버튼 — 간편 메뉴 항목 한 개를 그린다.
 *
 * 위가 뾰족하고 아래가 둥근 전형적인 물방울 실루엣을 [Path] 로 그리고, 위→아래 파란 그라데이션과
 * 상단 광택으로 "예쁜 물방울" 느낌을 낸다(원신 푸리나 물방울 느낌). 라벨은 둥근 부분 중앙에 흰 글씨.
 * 순수 커스텀 드로잉이라 별도 리소스가 필요 없다.
 */
@SuppressLint("ViewConstructor")
class WaterDropView(context: Context) : View(context) {

    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(12f)
        setShadowLayer(3f, 0f, 1f, 0x80000000.toInt())
    }
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildPath(w.toFloat(), h.toFloat())
        // 위(밝은 하늘색) → 아래(파랑) 그라데이션 = 물방울 광택감
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0xFFBFE6FF.toInt(), 0xFF2E8BE6.toInt(), Shader.TileMode.CLAMP
        )
    }

    /** 위 뾰족점 + 아래 반원(둥근 bulb) 으로 teardrop 경로 구성. */
    private fun buildPath(w: Float, h: Float) {
        val r = w / 2f
        val cx = w / 2f
        val by = h - r // bulb 중심 y
        path.reset()
        path.moveTo(cx, 0f) // 위 뾰족점
        path.cubicTo(cx + r * 0.55f, h * 0.18f, cx + r, by - r * 0.55f, cx + r, by) // 오른쪽
        path.arcTo(RectF(cx - r, by - r, cx + r, by + r), 0f, 180f, false) // 아래 반원
        path.cubicTo(cx - r, by - r * 0.55f, cx - r * 0.55f, h * 0.18f, cx, 0f) // 왼쪽 복귀
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        canvas.drawPath(path, fillPaint)

        // 상단 광택(작은 흰 타원)
        val r = width / 2f
        val by = height - r
        canvas.drawOval(width * 0.30f, by - r * 0.55f, width * 0.52f, by - r * 0.08f, shinePaint)

        // 라벨 — bulb 중앙
        if (label.isNotEmpty()) {
            val baseline = by + r * 0.10f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, width / 2f, baseline, textPaint)
        }
    }

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
