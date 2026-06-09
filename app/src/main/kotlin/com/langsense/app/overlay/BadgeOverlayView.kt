package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 상시 언어 배지 (Feature 2). 화면 모서리에 현재 입력 언어를 항상 표시.
 * 드래그로 위치 변경 가능하며, 드래그 종료 시 위치를 콜백으로 저장한다.
 */
@SuppressLint("ViewConstructor")
class BadgeOverlayView(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onTap: () -> Unit,
    private val onPositionSaved: (x: Int, y: Int) -> Unit
) : TextView(context) {

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    init {
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        // 크기·색은 applyStyle 로 적용한다(설정 변경 시 재호출로 즉시 반영). 기본값은 중(1)+기존 색.
        applyStyle(SIZE_MEDIUM, DEFAULT_BG_ARGB, DEFAULT_TEXT_ARGB)
        setupTouch()
    }

    fun setLanguage(label: String) {
        text = label
    }

    /**
     * 배지 탭 피드백 펄스 (추가 기능 1, radial-menu.html 의 badge pulse 이식).
     * 래디얼 메뉴를 열거나 닫을 때 배지가 살짝 커졌다 돌아오는 짧은 스케일 연출. 외형/설정은 바꾸지 않는다.
     */
    fun pulse() {
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        animate()
            .scaleX(PULSE_SCALE).scaleY(PULSE_SCALE)
            .setDuration(PULSE_HALF_MS)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(PULSE_HALF_MS).start()
            }
            .start()
    }

    /**
     * 크기 단계(0=소, 1=중, 2=대)와 배경/글씨 ARGB 를 적용한다(Feature 2 커스터마이즈).
     * 중(1) + 기본 색은 기존 외형(14sp / 패딩 8·4dp / minWidth 40dp / #CC000000·흰색)과 동일하다.
     * 둥근 모서리(6dp)는 기존 bg_badge 드로어블과 동일하게 코드로 그린다.
     */
    fun applyStyle(sizeLevel: Int, bgArgb: Int, textArgb: Int) {
        val spec = sizeSpec(sizeLevel)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, spec.textSp)
        setTextColor(textArgb)
        val padH = dp(spec.padHDp)
        val padV = dp(spec.padVDp)
        setPadding(padH, padV, padH, padV)
        minWidth = dp(spec.minWDp)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(6f).toFloat()
            setColor(bgArgb)
        }
    }

    private data class SizeSpec(val textSp: Float, val padHDp: Float, val padVDp: Float, val minWDp: Float)

    private fun sizeSpec(level: Int): SizeSpec = when (level) {
        SIZE_SMALL -> SizeSpec(12f, 6f, 3f, 32f)
        SIZE_LARGE -> SizeSpec(18f, 11f, 6f, 52f)
        else -> SizeSpec(14f, 8f, 4f, 40f) // 중(기본) = 기존 외형
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).roundToInt()
                    val dy = (event.rawY - downRawY).roundToInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragging = true
                    params.x = clampX(startX + dx)
                    params.y = clampY(startY + dy)
                    runCatching { windowManager.updateViewLayout(this, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 드래그면 위치 저장, 단순 탭이면 간편 메뉴 열기.
                    if (dragging) onPositionSaved(params.x, params.y) else onTap()
                    true
                }
                else -> false
            }
        }
    }

    private val touchSlop: Int
        get() = dp(6f)

    private fun clampX(x: Int): Int {
        val max = (screenWidth() - width).coerceAtLeast(0)
        return x.coerceIn(0, max)
    }

    private fun clampY(y: Int): Int {
        val max = (screenHeight() - height).coerceAtLeast(0)
        return y.coerceIn(0, max)
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels
    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    ).roundToInt()

    companion object {
        const val SIZE_SMALL = 0
        const val SIZE_MEDIUM = 1
        const val SIZE_LARGE = 2

        /** 탭 펄스: 최대 배율과 반(half) 지속 시간(ms). */
        private const val PULSE_SCALE = 1.12f
        private const val PULSE_HALF_MS = 130L

        /** init 기본값(=기존 외형). OverlayManager 가 곧바로 prefs 값으로 applyStyle 재호출한다. */
        private const val DEFAULT_BG_ARGB = 0xCC000000.toInt()
        private const val DEFAULT_TEXT_ARGB = 0xFFFFFFFF.toInt()

        /** 우하단 기본 위치 계산 (gravity TOP|START 기준 좌표). */
        fun defaultPosition(view: View): Pair<Int, Int> {
            val dm = view.resources.displayMetrics
            val margin = (16 * dm.density).roundToInt()
            val w = (56 * dm.density).roundToInt()
            val h = (28 * dm.density).roundToInt()
            return (dm.widthPixels - w - margin) to (dm.heightPixels - h - margin * 4)
        }
    }
}
