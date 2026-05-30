package com.langsense.app.service

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.langsense.app.R
import com.langsense.app.overlay.OverlayManager
import com.langsense.app.util.ImeLocaleParser
import com.langsense.app.util.Prefs

/**
 * LangSense 접근성 서비스 — 모든 감지의 진입점.
 *
 * - onAccessibilityEvent: 윈도우 상태 변경(언어 전환) / 텍스트 선택(한영타)
 * - onKeyEvent: 포커스 없는 키 입력 카운트 (절대 소비하지 않음)
 *
 * 보안 원칙: 키스트로크/화면 내용을 수집·저장·전송하지 않으며 모든 처리는 온디바이스 로컬.
 */
class LangSenseAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayManager
    private lateinit var imeDetector: ImeStateDetector
    private lateinit var keyMonitor: KeyEventMonitor
    private lateinit var selectionMonitor: TextSelectionMonitor

    private var currentLang: String = ImeLocaleParser.UNKNOWN
    private var initialized = false

    override fun onServiceConnected() {
        super.onServiceConnected()

        prefs = Prefs(this)
        overlay = OverlayManager(this, prefs)

        imeDetector = ImeStateDetector(this) { lang -> onLanguageChanged(lang) }

        keyMonitor = KeyEventMonitor(
            enabledProvider = { prefs.noFocusEnabled },
            thresholdProvider = { prefs.noFocusThreshold },
            onWarn = { overlay.showNoFocusWarning(getString(R.string.overlay_no_focus)) }
        )

        selectionMonitor = TextSelectionMonitor(
            enabledProvider = { prefs.replaceEnabled },
            confidencePercentProvider = { prefs.replaceConfidence }
        ) { node, fullText, selStart, selEnd, converted ->
            overlay.showReplaceChip(node, fullText, selStart, selEnd, converted)
        }

        prefs.register(this)

        // 현재 언어 캐시 + 초기 배지
        currentLang = imeDetector.primeCurrent()
        if (prefs.badgeEnabled) overlay.showBadge(currentLang)

        initialized = true
    }

    private fun onLanguageChanged(lang: String) {
        currentLang = lang
        overlay.showFlash(lang)
        overlay.updateBadge(lang)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!initialized) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                imeDetector.onWindowStateChanged(event)
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ->
                selectionMonitor.onSelectionChanged(event)
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        if (!initialized) return false
        // 포커스 확인 후 카운트. 반환값은 항상 false (이벤트 소비 금지).
        keyMonitor.onKeyEvent(event, hasEditableFocus())
        return false
    }

    private fun hasEditableFocus(): Boolean {
        val focused = runCatching {
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull() ?: return false
        return focused.isEditable
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        initialized = false
        if (::prefs.isInitialized) prefs.unregister(this)
        if (::overlay.isInitialized) overlay.removeAll()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (!initialized) return
        when (key) {
            Prefs.KEY_BADGE_ENABLED -> {
                if (prefs.badgeEnabled) overlay.showBadge(currentLang) else overlay.hideBadge()
            }
            // 그 외 설정(플래시 색/속도/횟수, 임계값 등)은 사용 시점에 prefs 에서 즉시 읽으므로 별도 처리 불필요.
        }
    }
}
