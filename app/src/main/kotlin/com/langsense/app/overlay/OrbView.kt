package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * 발광 원형 오브 버튼 — 간편 메뉴 항목 한 개. (구 [WaterDropView] 물방울을 전면 교체)
 *
 * 원신 푸리나 스킬 연출의 "빛나는 파랑-보라 구체" 느낌:
 *  - [RadialGradient] 구체 본체(중심 밝은 하늘색 → 파랑 → 보라 외곽)
 *  - [BlurMaskFilter] 외곽 글로우(glow) + 부드러운 drop shadow → 떠 있는 느낌
 *  - 상단 specular 하이라이트로 입체감
 *
 * 애니메이션(모두 [SpringAnimation] 물리 기반, Compose 불필요):
 *  - 등장: 아래에서 위로 스프링 바운스 ([enterFrom])
 *  - 드래그: 손가락을 탄성 있게 따라오고, 놓으면 제자리로 복귀 (터치 처리)
 *  - 탭: 살짝 눌리는 scale down 피드백
 *  - 소멸: 페이드아웃 + 축소 ([exit])
 *
 * 글로우 blur 는 저사양 기기에서 비용이 있어 [glowEnabled]=false 면 blur 없이
 * 얇은 반투명 링으로 대체한다(라이트 모드). 순수 커스텀 드로잉이라 리소스가 필요 없다.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class OrbView(context: Context, private val glowEnabled: Boolean) : View(context) {

    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    /** 부채꼴 배치상의 "제자리" translation. 부모가 [setHome] 으로 지정. */
    private var homeTx = 0f
    private var homeTy = 0f

    /** 단순 탭(드래그 아님) 시 실행할 동작. */
    var onTap: (() -> Unit)? = null

    // ---- Paints ----
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GLOW_COLOR
        if (glowEnabled) maskFilter = BlurMaskFilter(blurPx(GLOW_BLUR_DP), BlurMaskFilter.Blur.NORMAL)
        else style = Paint.Style.STROKE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55000000
        if (glowEnabled) maskFilter = BlurMaskFilter(blurPx(SHADOW_BLUR_DP), BlurMaskFilter.Blur.NORMAL)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(13f)
        setShadowLayer(4f, 0f, 1f, 0xB0000000.toInt())
    }

    // ---- Springs (물리 기반) ----
    private val springX = spring(DynamicAnimation.TRANSLATION_X)
    private val springY = spring(DynamicAnimation.TRANSLATION_Y)
    private val springScaleX = spring(DynamicAnimation.SCALE_X)
    private val springScaleY = spring(DynamicAnimation.SCALE_Y)

    // ---- Touch ----
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false
    private val touchSlop = blurPx(8f)

    init {
        // BlurMaskFilter 는 일부 기기에서 SW 레이어가 더 안전. 오브 1개는 작아 비용 무시 가능.
        if (glowEnabled) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = orbRadius()
        val cx = w / 2f
        val cy = h / 2f
        // 본체: 중심 살짝 위(specular 쪽)에서 시작하는 파랑→보라 구체 그라데이션.
        bodyPaint.shader = RadialGradient(
            cx, cy - r * 0.25f, r * 1.15f,
            intArrayOf(0xFFBFE3FF.toInt(), 0xFF4D8BF5.toInt(), 0xFF6B3CEA.toInt(), 0xFF4A23B8.toInt()),
            floatArrayOf(0f, 0.45f, 0.82f, 1f),
            Shader.TileMode.CLAMP
        )
        // 상단 하이라이트: 작고 밝은 흰 점 → 광택.
        highlightPaint.shader = RadialGradient(
            cx - r * 0.30f, cy - r * 0.42f, r * 0.55f,
            intArrayOf(0xCCFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val r = orbRadius()
        val cx = width / 2f
        val cy = height / 2f

        // 1) 떠 있는 느낌의 부드러운 그림자(아래로 약간 오프셋)
        if (glowEnabled) {
            canvas.drawCircle(cx, cy + r * 0.30f, r * 0.92f, shadowPaint)
            // 2) 외곽 글로우(발광)
            canvas.drawCircle(cx, cy, r * 1.02f, glowPaint)
        } else {
            // 라이트 모드: blur 없이 얇은 반투명 링으로 글로우를 흉내.
            glowPaint.strokeWidth = blurPx(3f)
            canvas.drawCircle(cx, cy, r + blurPx(2f), glowPaint)
        }

        // 3) 구체 본체
        canvas.drawCircle(cx, cy, r, bodyPaint)
        // 4) 상단 specular 하이라이트
        canvas.drawCircle(cx, cy, r, highlightPaint)

        // 5) 라벨
        if (label.isNotEmpty()) {
            val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, cx, baseline, textPaint)
        }
    }

    // ---------------------------------------------------------------------
    // 배치 / 애니메이션
    // ---------------------------------------------------------------------

    /** 부채꼴 제자리 translation 지정(부모 onLayout 에서 호출). */
    fun setHome(tx: Float, ty: Float) {
        homeTx = tx
        homeTy = ty
    }

    /** 등장: 제자리보다 [offsetY] 만큼 아래·축소·투명 상태에서 위로 스프링 바운스. */
    fun enterFrom(offsetY: Float, startDelayMs: Long) {
        translationX = homeTx
        translationY = homeTy + offsetY
        scaleX = 0.35f
        scaleY = 0.35f
        alpha = 0f
        postDelayed({
            animate().alpha(1f).setDuration(140L).start()
            springY.animateToFinalPosition(homeTy)
            springScaleX.animateToFinalPosition(1f)
            springScaleY.animateToFinalPosition(1f)
        }, startDelayMs)
    }

    /** 소멸: 페이드아웃 + 축소되며 사라짐. 끝나면 [onEnd]. */
    fun exit(onEnd: () -> Unit) {
        springX.cancel(); springY.cancel(); springScaleX.cancel(); springScaleY.cancel()
        animate()
            .alpha(0f)
            .scaleX(0.2f)
            .scaleY(0.2f)
            .setDuration(170L)
            .withEndAction { onEnd() }
            .start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                pressFeedback(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                    dragging = true
                    pressFeedback(false) // 드래그 시작이면 눌림 해제
                }
                if (dragging) {
                    // 손가락을 탄성 있게 "따라옴": 스프링 목표를 손가락 위치로 갱신 → 약간 지연되며 트레일.
                    springX.animateToFinalPosition(homeTx + dx)
                    springY.animateToFinalPosition(homeTy + dy)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressFeedback(false)
                if (dragging) {
                    // 놓으면 제자리로 스프링 복귀.
                    springX.animateToFinalPosition(homeTx)
                    springY.animateToFinalPosition(homeTy)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    onTap?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** 탭 눌림 피드백: 살짝 scale down(누름) / 복귀(뗌). */
    private fun pressFeedback(pressed: Boolean) {
        val target = if (pressed) 0.88f else 1f
        springScaleX.animateToFinalPosition(target)
        springScaleY.animateToFinalPosition(target)
    }

    private fun spring(property: DynamicAnimation.ViewProperty): SpringAnimation =
        SpringAnimation(this, property).apply {
            spring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY // 0.5 → 탱탱한 바운스
                stiffness = SPRING_STIFFNESS
            }
        }

    private fun orbRadius(): Float {
        // 글로우/그림자 여백을 남기고 구체 반지름 산정.
        val pad = if (glowEnabled) blurPx(14f) else blurPx(6f)
        return (minOf(width, height) / 2f) - pad
    }

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun blurPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    companion object {
        // const 는 .toInt() 컴파일타임 호출을 허용하지 않으므로 일반 val 로 둔다.
        private val GLOW_COLOR = 0x9959B0FF.toInt() // 반투명 하늘파랑 글로우
        private const val GLOW_BLUR_DP = 16f
        private const val SHADOW_BLUR_DP = 12f
        private const val SPRING_STIFFNESS = 380f // LOW(200)~MEDIUM(1500) 사이, 트레일+복귀 균형
    }
}
