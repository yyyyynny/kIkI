package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
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
    private val onPositionSaved: (x: Int, y: Int) -> Unit
) : TextView(context) {

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    init {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setBackgroundResource(com.langsense.app.R.drawable.bg_badge)
        val h = dp(4f)
        val w = dp(8f)
        setPadding(w, h, w, h)
        minWidth = dp(40f)
        setupTouch()
    }

    fun setLanguage(label: String) {
        text = label
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
                    if (dragging) onPositionSaved(params.x, params.y)
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
