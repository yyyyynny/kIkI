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

        // 리스너 등록 + 현재 언어 캐시 + 초기 배지
        currentLang = imeDetector.start()
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
        // 포커스 조회는 게이트 통과 후에만 지연 평가된다(저사양 최적화 Bug 1).
        // 1차는 활성 윈도우만 보는 저비용 조회, 2차는 그래도 없을 때만 도는 전체 윈도우 백업.
        keyMonitor.onKeyEvent(
            event,
            focusProbe = { hasActiveEditableFocus() },
            focusReProbe = { hasAnyEditableFocus() }
        )
        return false
    }

    /** 1차(저비용): 활성 윈도우의 편집 가능한 입력 포커스만 확인. 키마다 호출되므로 가볍게 유지. */
    private fun hasActiveEditableFocus(): Boolean =
        runCatching { rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.isEditable }
            .getOrNull() == true

    /**
     * 2차(백업): 전체 윈도우를 순회해 편집 포커스를 탐색(멀티윈도우/IME 분리 대응).
     * 활성 윈도우가 잠시 비어있을 때 일시적 null 오판을 막기 위한 것으로, 1차가 실패할 때만 호출된다.
     */
    private fun hasAnyEditableFocus(): Boolean {
        runCatching {
            for (w in windows) {
                val node = w?.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: continue
                if (node.isEditable) return true
            }
        }
        return false
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
        if (::imeDetector.isInitialized) imeDetector.stop()
        if (::overlay.isInitialized) overlay.removeAll()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (!initialized) return
        when (key) {
            Prefs.KEY_BADGE_ENABLED -> {
                if (prefs.badgeEnabled) overlay.showBadge(currentLang) else overlay.hideBadge()
            }
            // 배지 크기/색은 표시 중인 배지에 즉시 재적용(꺼져 있으면 다음 표시 때 반영).
            Prefs.KEY_BADGE_SIZE, Prefs.KEY_BADGE_BG_COLOR, Prefs.KEY_BADGE_TEXT_COLOR -> {
                if (prefs.badgeEnabled) overlay.updateBadge(currentLang)
            }
            // 그 외 설정(플래시 색/속도/횟수, 임계값 등)은 사용 시점에 prefs 에서 즉시 읽으므로 별도 처리 불필요.
        }
    }
}
