package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * 래디얼 메뉴의 **살아있는 유리 칩** 버튼 한 개 (design/reference/radialmenu.html `.orb` 이식).
 *
 * 완전한 원이 아니라 라운드 사각 유리 칩이며, 코너 반경([cornerRadiusPx])을 부모가 매 프레임
 * 갱신해 **끊임없이 형태가 미세하게 morph** 한다(참고 orbMorph). 그리는 순서:
 *  1. 외곽 글로우 2겹(BlurMaskFilter — 가까운 시안 + 먼 블루)
 *  2. 유리 채움(rgba(20,60,180,0.12))
 *  3. 안쪽 글로우(inset, blur 스트로크)
 *  4. 좌상단 광택 타원
 *  5. 은은한 시안 림(1.5dp) + 안쪽 얇은 링(1dp, inset 4dp)
 *  6. 라벨 — **칩 아래**, #cdeeff bold + 시안 글로우
 *
 * BlurMaskFilter 는 소프트웨어 레이어가 필요해 [LAYER_TYPE_SOFTWARE] 로 그린다(칩이 작고
 * 5개뿐이라 비용은 메뉴 표시 시간에 한정). 모든 색상/치수는 [RadialMenuStyle] 에서 가져온다.
 */
@SuppressLint("ViewConstructor")
class RadialOrbView(context: Context) : View(context) {

    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    /** 칩 코너 반경(px). 부모(QuickMenuOverlayView)가 morph 애니메이션으로 매 프레임 갱신. */
    var cornerRadiusPx: Float = dp(RadialMenuStyle.ORB_CORNER_DP)
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val glowNearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RadialMenuStyle.ORB_GLOW_NEAR
        maskFilter = BlurMaskFilter(dp(RadialMenuStyle.ORB_GLOW_NEAR_BLUR_DP), BlurMaskFilter.Blur.NORMAL)
    }
    private val glowFarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RadialMenuStyle.ORB_GLOW_FAR
        maskFilter = BlurMaskFilter(dp(RadialMenuStyle.ORB_GLOW_FAR_BLUR_DP), BlurMaskFilter.Blur.NORMAL)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RadialMenuStyle.ORB_FILL }
    private val insetGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RadialMenuStyle.ORB_INSET_GLOW
        strokeWidth = dp(6f)
        maskFilter = BlurMaskFilter(dp(RadialMenuStyle.ORB_INSET_GLOW_BLUR_DP), BlurMaskFilter.Blur.NORMAL)
    }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RadialMenuStyle.LABEL_COLOR
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(RadialMenuStyle.LABEL_TEXT_SP)
        setShadowLayer(dp(6f), 0f, 0f, RadialMenuStyle.LABEL_GLOW)
    }

    private val chipRect = RectF()
    private val tmpRect = RectF()

    /**
     * 뷰 안에서 칩 중심의 y 오프셋(px). 칩 위에 글로우 여백, 아래에 라벨 영역이 있으므로
     * 중심은 spread + 칩높이/2. [QuickMenuOverlayView] 가 배치(앵커 정렬)/피벗에 사용.
     */
    val orbCenterY: Float
        get() = dp(RadialMenuStyle.ORB_GLOW_SPREAD_DP) + dp(RadialMenuStyle.ORB_H_DP) / 2f

    init {
        // BlurMaskFilter 는 SW 레이어에서만 동작. 칩 하나는 작아 비용은 무시 가능(메뉴 표시 중 한정).
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val spread = dp(RadialMenuStyle.ORB_GLOW_SPREAD_DP)
        val chipW = dp(RadialMenuStyle.ORB_W_DP)
        val chipH = dp(RadialMenuStyle.ORB_H_DP)
        val left = (w - chipW) / 2f
        chipRect.set(left, spread, left + chipW, spread + chipH)

        // 좌상단 광택: 참고 .orb::before — ellipse at (38%, 32%) of the gloss box(top 8%, left 12%).
        val gx = chipRect.left + chipRect.width() * 0.32f
        val gy = chipRect.top + chipRect.height() * 0.26f
        glossPaint.shader = RadialGradient(
            gx, gy, chipRect.width() * 0.34f,
            intArrayOf(RadialMenuStyle.ORB_GLOSS_CORE, RadialMenuStyle.ORB_GLOSS_MID, 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val r = cornerRadiusPx

        // 1) 외곽 글로우 2겹 — 칩보다 살짝 크게 그려 번짐이 자연스럽게 퍼진다.
        tmpRect.set(chipRect); tmpRect.inset(-dp(5f), -dp(5f))
        canvas.drawRoundRect(tmpRect, r + dp(4f), r + dp(4f), glowFarPaint)
        tmpRect.set(chipRect); tmpRect.inset(-dp(1.5f), -dp(1.5f))
        canvas.drawRoundRect(tmpRect, r + dp(1f), r + dp(1f), glowNearPaint)

        // 2) 유리 채움
        canvas.drawRoundRect(chipRect, r, r, fillPaint)

        // 3) 안쪽 글로우(inset) — blur 스트로크를 칩 안쪽 경계에 두고 클립으로 밖 번짐을 차단.
        canvas.save()
        canvas.clipRect(chipRect)
        tmpRect.set(chipRect); tmpRect.inset(dp(2f), dp(2f))
        canvas.drawRoundRect(tmpRect, r - dp(2f), r - dp(2f), insetGlowPaint)
        canvas.restore()

        // 4) 좌상단 광택
        canvas.drawRoundRect(chipRect, r, r, glossPaint)

        // 5) 림 + 안쪽 링(참고: inset 4px, 코너는 그만큼 작게)
        tmpRect.set(chipRect)
        tmpRect.inset(rimPaint.strokeWidth / 2f, rimPaint.strokeWidth / 2f)
        canvas.drawRoundRect(tmpRect, r, r, rimPaint)
        val ringInset = dp(RadialMenuStyle.ORB_INNER_RING_INSET_DP)
        tmpRect.set(chipRect); tmpRect.inset(ringInset, ringInset)
        canvas.drawRoundRect(tmpRect, (r - ringInset).coerceAtLeast(dp(4f)), (r - ringInset).coerceAtLeast(dp(4f)), innerRingPaint)

        // 6) 라벨 — 칩 아래 영역 중앙(참고 .orb-label)
        if (label.isNotEmpty()) {
            val baseline = (chipRect.bottom + height) / 2f -
                (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, width / 2f, baseline, textPaint)
        }
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
