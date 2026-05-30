package com.langsense.app.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 전체화면 플래시 오버레이 (Feature 1, 3).
 *
 * 색상 배경 + 중앙 언어명 텍스트를 [count] 회 깜박인다.
 * 각 깜박임은 "즉시 표시 → [durationMs] 동안 페이드아웃" 으로 주변시 인지에 최적화.
 */
class FlashOverlayView(context: Context) : FrameLayout(context) {

    private val label = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 28f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    init {
        addView(
            label,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        // 그림자로 어떤 배경색에서도 텍스트 가독성 확보
        label.setShadowLayer(8f, 0f, 0f, Color.argb(180, 0, 0, 0))
    }

    /**
     * 플래시 재생.
     * @param colorArgb 불투명도가 이미 적용된 ARGB 색상
     * @param text 중앙 표시 텍스트
     * @param durationMs 1회 페이드아웃 시간(=깜박임 속도)
     * @param count 깜박임 횟수
     * @param onEnd 모든 깜박임 종료 후 콜백(오버레이 제거용)
     */
    fun play(colorArgb: Int, text: String, durationMs: Int, count: Int, onEnd: () -> Unit) {
        setBackgroundColor(colorArgb)
        label.text = text
        playCycle(count.coerceAtLeast(1), durationMs.toLong(), onEnd)
    }

    /** 진행 중인 애니메이션/예약 콜백을 모두 취소한다(다른 플래시로 교체될 때 누수·오제거 방지). */
    fun cancel() {
        animate().cancel()
        removeCallbacks(null)
    }

    override fun onDetachedFromWindow() {
        // 윈도우에서 제거되면 더 이상 콜백이 의미 없으므로 정리한다.
        animate().cancel()
        removeCallbacks(null)
        super.onDetachedFromWindow()
    }

    private fun playCycle(remaining: Int, durationMs: Long, onEnd: () -> Unit) {
        animate().cancel()
        alpha = 1f
        animate()
            .alpha(0f)
            .setStartDelay(durationMs / 2) // 잠깐 보이게 유지 후 페이드아웃
            .setDuration(durationMs)
            .withEndAction {
                // 윈도우에서 이미 떨어졌다면(다른 플래시로 교체됨) 콜백을 무시한다.
                if (!isAttachedToWindow) return@withEndAction
                if (remaining > 1) {
                    postDelayed({ playCycle(remaining - 1, durationMs, onEnd) }, 60L)
                } else {
                    onEnd()
                }
            }
            .start()
    }
}
