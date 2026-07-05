package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/**
 * 래디얼 메뉴의 유리구슬(glass orb) 버튼 한 개 (추가 기능 1, radial-menu.html `.orb` 이식).
 *
 * 둥근 사각형 유리구슬을 [Path]/그라데이션으로 그린다.
 *  - 외곽 글로우(시안 RadialGradient) → 둥근 사각형 유리 채움(세로 그라데이션) → 시안 림 → 상단 광택 →
 *    안쪽 얇은 링 순으로 겹쳐 HTML 의 유리/발광 질감을 근사한다.
 *  - 라벨은 오브 아래(HTML 처럼) 시안 글자 + 글로우 그림자로 표시.
 *
 * 모든 색상/치수는 [RadialMenuStyle] 에서 가져오며(하드코딩 금지), 별도 리소스가 필요 없다.
 * 위치 배치/펼침·수납 애니메이션은 [QuickMenuOverlayView] 가 담당하고, 여기선 외형만 그린다.
 */
@SuppressLint("ViewConstructor")
class RadialOrbView(context: Context) : View(context) {

    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RadialMenuStyle.ORB_RIM
        strokeWidth = dp(RadialMenuStyle.ORB_RIM_WIDTH_DP)
    }
    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RadialMenuStyle.ORB_INNER_RING
        strokeWidth = dp(RadialMenuStyle.ORB_INNER_RING_WIDTH_DP)
    }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RadialMenuStyle.LABEL_COLOR
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(RadialMenuStyle.LABEL_TEXT_SP)
        setShadowLayer(dp(6f), 0f, 0f, RadialMenuStyle.LABEL_GLOW)
    }

    private val orbRect = RectF()

    /** 오브(유리구슬) 박스 높이(px). 뷰 상단(글로우 여백 아래)에 오브, 그 아래에 라벨 영역. */
    private val orbBoxH: Float get() = dp(RadialMenuStyle.ORB_H_DP)

    /**
     * 뷰 안에서 오브 중심의 y 오프셋(px). 오브 위쪽에 글로우가 번질 여백(spread)이 있으므로
     * 중심은 spread + 박스높이/2 위치다. [QuickMenuOverlayView] 가 배치(앵커 정렬)에 사용.
     */
    val orbCenterY: Float get() = dp(RadialMenuStyle.ORB_GLOW_SPREAD_DP) + orbBoxH / 2f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val spread = dp(RadialMenuStyle.ORB_GLOW_SPREAD_DP)
        // 오브 박스: 가로 중앙, 세로 상단(글로우가 번질 여백을 좌우/상단에 둠).
        val left = spread
        val right = w - spread
        val top = spread
        val bottom = top + orbBoxH
        orbRect.set(left, top, right, bottom)

        // 유리 채움: 위(밝음) → 아래(기본) 세로 그라데이션.
        fillPaint.shader = LinearGradient(
            0f, top, 0f, bottom,
            RadialMenuStyle.ORB_FILL_TOP, RadialMenuStyle.ORB_FILL_BOTTOM, Shader.TileMode.CLAMP
        )

        // 외곽 글로우: 오브 중심에서 바깥으로 시안 → 투명 RadialGradient.
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val glowR = min(right - left, bottom - top) / 2f + spread
        glowPaint.shader = RadialGradient(
            cx, cy, glowR,
            intArrayOf(RadialMenuStyle.ORB_GLOW_INNER, RadialMenuStyle.ORB_GLOW_OUTER, 0x00000000),
            floatArrayOf(0.45f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )

        // 상단 광택: 좌상단 흰 RadialGradient.
        val glossCx = left + (right - left) * 0.32f
        val glossCy = top + orbBoxH * 0.30f
        glossPaint.shader = RadialGradient(
            glossCx, glossCy, (right - left) * 0.30f,
            intArrayOf(RadialMenuStyle.ORB_GLOSS, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        // 오브는 정원(둥근 사각형이 "네모 칸" 같다는 피드백 반영). 반지름은 박스의 절반.
        val cx = orbRect.centerX()
        val cy = orbRect.centerY()
        val r = min(orbRect.width(), orbRect.height()) / 2f

        // 1) 외곽 글로우
        val glowR = r + dp(RadialMenuStyle.ORB_GLOW_SPREAD_DP)
        canvas.drawCircle(cx, cy, glowR, glowPaint)
        // 2) 유리 채움(원)
        canvas.drawCircle(cx, cy, r, fillPaint)
        // 3) 상단 광택(좌상단 스페큘러) — 광택 그라데이션이 가장자리에서 투명해 원 안에 자연히 담긴다.
        canvas.drawCircle(cx, cy, r, glossPaint)
        // 4) 시안 림(원 안쪽에 들어오도록 굵기 절반만큼 줄여 그림)
        val rimInset = dp(RadialMenuStyle.ORB_RIM_WIDTH_DP) / 2f
        canvas.drawCircle(cx, cy, r - rimInset, rimPaint)
        // 5) 안쪽 얇은 링(살짝 inset)
        canvas.drawCircle(cx, cy, r - dp(4f), innerRingPaint)

        // 6) 라벨 — 오브 아래 영역 중앙
        if (label.isNotEmpty()) {
            val labelAreaTop = orbRect.bottom
            val labelAreaBottom = height.toFloat()
            val baseline = (labelAreaTop + labelAreaBottom) / 2f -
                (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, width / 2f, baseline, textPaint)
        }
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
