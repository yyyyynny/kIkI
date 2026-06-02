package com.langsense.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.langsense.app.R
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
    // 같은 색 플래시가 아주 짧은 간격으로 다시 들어오면(잔존 중복 발화) 건너뛰는 렌더 단계 안전망.
    // 근본 원인은 ImeStateDetector(합치기/불응기/멱등)에서 처리하며, 이건 마지막 시각 방어일 뿐이다.
    private var lastFlashColorArgb = 0
    private var lastFlashAt = 0L
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
        // 렌더 단계 안전망: 동일 색 플래시가 FLASH_DEDUP_MS 안에 다시 오면 중복으로 보고 건너뛴다.
        // (다른 언어는 색이 달라 정상 표시됨. 사람은 같은 언어로 이만큼 빨리 두 번 전환할 수 없음.)
        val now = SystemClock.uptimeMillis()
        if (colorArgb == lastFlashColorArgb && now - lastFlashAt < FLASH_DEDUP_MS) return
        lastFlashColorArgb = colorArgb
        lastFlashAt = now

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
        // [7] 기본 윈도우 enter/exit 애니메이션 제거 → 색이 좌→우로 채워지지 않고 전체 화면이
        // 한 프레임에 꽉 찬 상태로 나타난다. (페이드아웃은 뷰 알파 애니메이션이 따로 처리)
        params.windowAnimations = 0
        runCatching { wm.addView(view, params) }.onFailure { return }
        flashView = view
        // onEnd 시점에 다른 플래시로 교체되었을 수 있으므로 자기 자신일 때만 제거(오제거 방지).
        view.play(colorArgb, text, prefs.flashDurationMs, prefs.flashCount) { removeFlashIf(view) }
    }

    private fun removeFlash() {
        flashView?.let {
            it.cancel()
            runCatching { wm.removeView(it) }
        }
        flashView = null
    }

    /** 현재 표시 중인 플래시가 [view] 와 동일할 때만 제거한다. */
    private fun removeFlashIf(view: FlashOverlayView) {
        if (flashView === view) removeFlash()
    }

    // ---------------------------------------------------------------------
    // Feature 2: 배지
    // ---------------------------------------------------------------------

    fun showBadge(lang: String) = onMain {
        if (!prefs.badgeEnabled) {
            hideBadgeInternal()
            return@onMain
        }
        val label = ImeLocaleParser.badgeLabel(lang)
        val existing = badgeView
        if (existing == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }

            val view = BadgeOverlayView(context, wm, params) { x, y -> prefs.setBadgePosition(x, y) }
            applyBadgeStyle(view)
            view.setLanguage(label)

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
            // 재사용: 라벨 + 크기/색 스타일을 다시 적용(설정 변경 즉시 반영).
            applyBadgeStyle(existing)
            existing.setLanguage(label)
        }
    }

    /** 현재 설정(크기/배경색/글씨색)을 배지에 적용. */
    private fun applyBadgeStyle(view: BadgeOverlayView) =
        view.applyStyle(prefs.badgeSize, prefs.badgeBgColorArgb(), prefs.badgeTextColorArgb())

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

    /**
     * 선택 영역을 변환 결과로 교체.
     *
     * 1차로 [AccessibilityNodeInfo.ACTION_SET_TEXT] 로 전체 텍스트를 통째로 교체한다.
     * 삼성 노트·일부 WebView/특수 에디터는 ACTION_SET_TEXT 를 무시하거나 false 를 반환하므로
     * (Bug 3) 클립보드 + 선택영역 재설정 + 붙여넣기로 fallback 한다.
     */
    private fun applyReplacement(
        node: AccessibilityNodeInfo,
        fullText: String,
        selStart: Int,
        selEnd: Int,
        converted: String
    ) {
        val s = selStart.coerceIn(0, fullText.length)
        val e = selEnd.coerceIn(s, fullText.length)

        // 노드가 갱신되었을 수 있으므로 최신 텍스트를 우선 사용(없으면 캡처된 fullText).
        val liveText = node.text?.toString() ?: fullText
        val base = if (e <= liveText.length) liveText else fullText
        val es = s.coerceIn(0, base.length)
        val ee = e.coerceIn(es, base.length)
        val newText = base.substring(0, es) + converted + base.substring(ee)

        if (trySetText(node, newText)) return
        pasteFallback(node, converted, es, ee)
    }

    /** 1차: ACTION_SET_TEXT. editable 이 아니거나 false 반환 시 실패로 간주. */
    private fun trySetText(node: AccessibilityNodeInfo, newText: String): Boolean {
        if (!node.isEditable) return false
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
        }
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
    }

    /**
     * 2차(fallback): 클립보드에 변환 결과를 넣고, 교체 대상 영역을 선택한 뒤 붙여넣기.
     * 붙여넣기는 "현재 선택 영역을 대체"하므로 선택영역 재설정이 핵심이다.
     * 그래도 실패하면 클립보드에는 변환 결과가 남아 사용자가 직접 붙여넣을 수 있다.
     */
    private fun pasteFallback(node: AccessibilityNodeInfo, converted: String, s: Int, e: Int) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        runCatching { cm?.setPrimaryClip(ClipData.newPlainText("langsense", converted)) }

        // 포커스 확보 → 교체 대상 영역 선택 → 붙여넣기
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, s)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, e)
        }
        val selectionSet = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        }.getOrDefault(false)

        val pasted = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)

        if (!selectionSet || !pasted) {
            // 붙여넣기까지 실패: 변환 결과가 클립보드에 있음을 사용자에게 안내.
            handler.post {
                runCatching {
                    Toast.makeText(context, R.string.replace_copied_fallback, Toast.LENGTH_SHORT).show()
                }
            }
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

        /** 같은 색 플래시 중복 억제 창(ms). 근본 방어(ImeStateDetector) 뒤의 렌더 단계 마지막 안전망. */
        const val FLASH_DEDUP_MS = 350L
    }
}
