package com.langsense.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.langsense.app.util.ImeLocaleParser
import com.langsense.app.util.Prefs
import kotlin.math.roundToInt

/**
 * 모든 오버레이(WindowManager) 추가/제거를 담당하는 단일 래퍼.
 * 메인 스레드에서만 윈도우를 조작한다.
 */
class OverlayManager(private val context: Context, private val prefs: Prefs) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var flashView: FlashOverlayView? = null
    private var badgeView: BadgeOverlayView? = null
    private var badgeParams: WindowManager.LayoutParams? = null
    private var chipView: ReplaceChipView? = null
    private var chipDismiss: Runnable? = null

    private val overlayType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    // ---------------------------------------------------------------------
    // Feature 1 / 3: 플래시
    // ---------------------------------------------------------------------

    /** 언어 전환 플래시. 설정이 꺼져있거나 해당 언어가 비활성이면 무시. */
    fun showFlash(lang: String) = onMain {
        if (!prefs.flashEnabled) return@onMain
        if (!prefs.isLangEnabled(lang)) return@onMain
        flash(prefs.flashColorArgb(lang), ImeLocaleParser.displayName(lang))
    }

    /** 포커스 없는 키 입력 경고(동일한 플래시 방식, 회색 + 안내 텍스트). */
    fun showNoFocusWarning(message: String) = onMain {
        flash(0xD9555555.toInt(), message)
    }

    private fun flash(colorArgb: Int, text: String) {
        removeFlash()
        val view = FlashOverlayView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        runCatching { wm.addView(view, params) }.onFailure { return }
        flashView = view
        view.play(colorArgb, text, prefs.flashDurationMs, prefs.flashCount) { removeFlash() }
    }

    private fun removeFlash() {
        flashView?.let { runCatching { wm.removeView(it) } }
        flashView = null
    }

    // ---------------------------------------------------------------------
    // Feature 2: 배지
    // ---------------------------------------------------------------------

    fun showBadge(lang: String) = onMain {
        if (!prefs.badgeEnabled) {
            hideBadgeInternal()
            return@onMain
        }
        if (badgeView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }

            val view = BadgeOverlayView(context, wm, params) { x, y -> prefs.setBadgePosition(x, y) }
            view.setLanguage(ImeLocaleParser.badgeLabel(lang))

            // 저장 위치가 있으면 사용, 없으면 우하단 기본값
            if (prefs.badgeX >= 0 && prefs.badgeY >= 0) {
                params.x = prefs.badgeX
                params.y = prefs.badgeY
            } else {
                val (dx, dy) = BadgeOverlayView.defaultPosition(view)
                params.x = dx
                params.y = dy
            }
            runCatching { wm.addView(view, params) }.onFailure { return@onMain }
            badgeView = view
            badgeParams = params
        } else {
            badgeView?.setLanguage(ImeLocaleParser.badgeLabel(lang))
        }
    }

    fun updateBadge(lang: String) = showBadge(lang)

    fun hideBadge() = onMain { hideBadgeInternal() }

    private fun hideBadgeInternal() {
        badgeView?.let { runCatching { wm.removeView(it) } }
        badgeView = null
        badgeParams = null
    }

    // ---------------------------------------------------------------------
    // Feature 4: "교체?" 칩
    // ---------------------------------------------------------------------

    /**
     * 한영타 교체 칩 표시.
     * @param node 선택이 일어난 편집 노드
     * @param fullText 노드 전체 텍스트
     * @param selStart 선택 시작
     * @param selEnd 선택 끝
     * @param converted 변환된 한국어
     */
    fun showReplaceChip(
        node: AccessibilityNodeInfo,
        fullText: String,
        selStart: Int,
        selEnd: Int,
        converted: String
    ) = onMain {
        removeChip()
        val view = ReplaceChipView(context)
        view.bind(converted) {
            applyReplacement(node, fullText, selStart, selEnd, converted)
            removeChip()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight() + dp(8f)
        }
        runCatching { wm.addView(view, params) }.onFailure { return@onMain }
        chipView = view

        chipDismiss = Runnable { removeChip() }.also { handler.postDelayed(it, CHIP_TIMEOUT_MS) }
    }

    private fun applyReplacement(
        node: AccessibilityNodeInfo,
        fullText: String,
        selStart: Int,
        selEnd: Int,
        converted: String
    ) {
        val s = selStart.coerceIn(0, fullText.length)
        val e = selEnd.coerceIn(s, fullText.length)
        val newText = fullText.substring(0, s) + converted + fullText.substring(e)

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
        }
        val ok = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)

        if (!ok) {
            // fallback: 클립보드 복사 후 붙여넣기 (WebView/특수 에디터 대응)
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("langsense", converted))
            runCatching { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }
        }
    }

    private fun removeChip() {
        chipDismiss?.let { handler.removeCallbacks(it) }
        chipDismiss = null
        chipView?.let { runCatching { wm.removeView(it) } }
        chipView = null
    }

    // ---------------------------------------------------------------------
    // 정리
    // ---------------------------------------------------------------------

    fun removeAll() = onMain {
        removeFlash()
        hideBadgeInternal()
        removeChip()
    }

    // ---------------------------------------------------------------------
    // 헬퍼
    // ---------------------------------------------------------------------

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else handler.post(block)
    }

    private fun statusBarHeight(): Int {
        @Suppress("DiscouragedApi", "InternalInsetResource")
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24f)
    }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    companion object {
        const val CHIP_TIMEOUT_MS = 2000L
    }
}
