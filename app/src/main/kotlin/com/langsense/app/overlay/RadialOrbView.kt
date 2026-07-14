package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * 래디얼 메뉴의 입체 비눗방울 오브 버튼 한 개 (orb_mockup.html 목업 v2 `.orb` 이식).
 *
 * 사용자 승인 목업의 레이어를 Canvas 로 재현한다(위에서부터 그리는 순서 역순):
 *  1. 외곽 다층 bloom(시안 RadialGradient)
 *  2. 구체 채움 — 중심이 좌상(36%, 30%)으로 치우친 오프셋 RadialGradient 5-stop(입체 구)
 *  3. 우하단 림 라이트(초승달 반사)
 *  4. 무지갯빛 sheen — 가장자리 밴드의 SweepGradient(**정적** — 회전 없음)
 *  5. 상단 광택(좌상단 소프트 흰 blob)
 *  6. 안쪽 밝은 링(비눗방울 막) + 바깥 흐린 이중 링
 *  7. 라벨 — **방울 안 중앙**, 흰색 bold + 시안 글로우(목업 text-shadow)
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

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spherePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimLightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RadialMenuStyle.ORB_INNER_RING
        strokeWidth = dp(RadialMenuStyle.ORB_INNER_RING_WIDTH_DP)
    }
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RadialMenuStyle.ORB_OUTER_RING
        strokeWidth = dp(RadialMenuStyle.ORB_OUTER_RING_WIDTH_DP)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RadialMenuStyle.LABEL_COLOR
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(RadialMenuStyle.LABEL_TEXT_SP)
        setShadowLayer(dp(6f), 0f, 0f, RadialMenuStyle.LABEL_GLOW)
    }

    /** 방울 반지름(px). 뷰는 정사각형(지름 + 좌우 bloom 여백)이다. */
    private val orbRadius: Float get() = dp(RadialMenuStyle.ORB_DIAMETER_DP) / 2f

    /**
     * 뷰 안에서 오브 중심의 y 오프셋(px). 라벨이 방울 안으로 들어가 뷰가 정사각형이 됐으므로
     * 중심은 정중앙이다. [QuickMenuOverlayView] 가 배치(앵커 정렬)와 스케일 피벗에 사용.
     */
    val orbCenterY: Float get() = height / 2f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        val r = orbRadius

        // 구체 채움: 목업 radial-gradient(circle at 36% 30%) — 중심을 좌상으로 오프셋한 5-stop.
        // (36%,30%) 는 지름 기준이므로 중심에서 (-0.28r, -0.40r), 반경은 farthest-corner ≈ 1.9r.
        spherePaint.shader = RadialGradient(
            cx - r * 0.28f, cy - r * 0.40f, r * 1.9f,
            intArrayOf(
                RadialMenuStyle.ORB_SPHERE_0,
                RadialMenuStyle.ORB_SPHERE_1,
                RadialMenuStyle.ORB_SPHERE_2,
                RadialMenuStyle.ORB_SPHERE_3,
                RadialMenuStyle.ORB_SPHERE_4
            ),
            floatArrayOf(0f, 0.28f, 0.58f, 0.84f, 1f),
            Shader.TileMode.CLAMP
        )

        // 림 라이트: 목업 .rim — circle at 70% 76%, 58%~76%~88% 밴드만 밝은 반사.
        rimLightPaint.shader = RadialGradient(
            cx + r * 0.40f, cy + r * 0.52f, r * 1.2f,
            intArrayOf(0x00C3F2FF, 0x00C3F2FF, RadialMenuStyle.ORB_RIM_LIGHT, 0x00C3F2FF),
            floatArrayOf(0f, 0.58f, 0.76f, 0.88f),
            Shader.TileMode.CLAMP
        )

        // 무지갯빛 sheen: 목업 .iris conic-gradient(from 200deg) → SweepGradient(정적).
        // conic 각도(0/45/110/200/270/360deg)를 비율(0~1)로 환산. 가장자리 밴드는 stroke 로 재현.
        sheenPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                0x0078FFF0, RadialMenuStyle.SHEEN_CYAN, 0x0096B4FF,
                RadialMenuStyle.SHEEN_PINK, RadialMenuStyle.SHEEN_AMBER, 0x0078FFF0
            ),
            floatArrayOf(0f, 0.125f, 0.306f, 0.556f, 0.75f, 1f)
        )
        sheenPaint.strokeWidth = r * 0.34f

        // 상단 광택: 목업 .spec — 좌상단(34%, 30%) 소프트 흰 blob.
        glossPaint.shader = RadialGradient(
            cx - r * 0.32f, cy - r * 0.40f, r * 0.55f,
            intArrayOf(RadialMenuStyle.ORB_GLOSS, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // 외곽 다층 bloom: 방울 가장자리부터 바깥으로 옅어지는 시안 발광.
        val glowR = r + dp(RadialMenuStyle.ORB_GLOW_SPREAD_DP)
        glowPaint.shader = RadialGradient(
            cx, cy, glowR,
            intArrayOf(
                RadialMenuStyle.ORB_GLOW_INNER,
                RadialMenuStyle.ORB_GLOW_INNER,
                RadialMenuStyle.ORB_GLOW_OUTER,
                0x005096FF
            ),
            floatArrayOf(0f, r / glowR * 0.9f, r / glowR, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val r = orbRadius

        // 1) 외곽 bloom
        canvas.drawCircle(cx, cy, r + dp(RadialMenuStyle.ORB_GLOW_SPREAD_DP), glowPaint)
        // 2) 구체 채움(입체 오프셋 그라데이션)
        canvas.drawCircle(cx, cy, r, spherePaint)
        // 3) 우하단 림 라이트
        canvas.drawCircle(cx, cy, r, rimLightPaint)
        // 4) 무지갯빛 sheen — 가장자리 밴드(스트로크 중심을 안쪽으로 넣어 원 안에 담는다)
        canvas.drawCircle(cx, cy, r - sheenPaint.strokeWidth / 2f, sheenPaint)
        // 5) 상단 광택
        canvas.drawCircle(cx, cy, r, glossPaint)
        // 6) 안쪽 밝은 링(비눗방울 막) + 바깥 흐린 이중 링
        canvas.drawCircle(cx, cy, r - dp(RadialMenuStyle.ORB_INNER_RING_WIDTH_DP) / 2f, innerRingPaint)
        canvas.drawCircle(cx, cy, r + dp(RadialMenuStyle.ORB_OUTER_RING_OFFSET_DP), outerRingPaint)

        // 7) 라벨 — 방울 안 중앙(목업)
        if (label.isNotEmpty()) {
            val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, cx, baseline, textPaint)
        }
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
