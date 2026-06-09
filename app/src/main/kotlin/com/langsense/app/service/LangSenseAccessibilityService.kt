package com.langsense.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.langsense.app.R
import com.langsense.app.overlay.OverlayManager
import com.langsense.app.overlay.QuickMenuItem
import com.langsense.app.ui.MainActivity
import com.langsense.app.ui.SettingsActivity
import com.langsense.app.util.ImeLocaleParser
import com.langsense.app.util.Prefs

/**
 * kIkI 접근성 서비스 — 모든 감지의 진입점. (클래스명은 식별자라 LangSenseAccessibilityService 유지)
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

    /**
     * 편집칸에 글자가 실제로 들어간 마지막 시각(uptime). text/selection 변경 이벤트가 편집 노드에서
     * 올 때 갱신한다. 포커스 없는 키 입력 경고의 오발동(입력은 되는데 경고가 뜨는 문제)을 막는 데 쓴다.
     */
    private var lastEditableActivityAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        // ★ 재연결 방어: onServiceConnected 는 같은 인스턴스에서 다시 호출될 수 있다(설정 변경/재바인드).
        // 정리 없이 재초기화하면 BroadcastReceiver/ContentObserver 가 중복 등록되어 한 번의 한/영 전환에
        // 여러 detector 가 각각 발동 → "매번 2회 이상 깜박임"의 근본 원인이 된다. 먼저 이전 상태를 정리.
        if (initialized) cleanup()

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

        overlay.setQuickMenuItems(buildQuickMenuItems())

        prefs.register(this)

        // 리스너 등록 + 현재 언어 캐시 + 초기 배지
        currentLang = imeDetector.start()
        if (prefs.badgeEnabled) overlay.showBadge(currentLang)

        initialized = true
    }

    /**
     * 배지 탭 시 뜨는 물방울 간편 메뉴 항목. 앱/설정 열기 + 주요 기능 즉석 토글.
     * 토글 값은 탭 시점에 prefs 에서 읽으므로 한 번만 구성해도 항상 현재 상태로 동작한다.
     */
    private fun buildQuickMenuItems(): List<QuickMenuItem> = listOf(
        QuickMenuItem(getString(R.string.quick_app)) { launchActivity(MainActivity::class.java) },
        QuickMenuItem(getString(R.string.quick_settings)) { launchActivity(SettingsActivity::class.java) },
        QuickMenuItem(getString(R.string.quick_flash)) {
            toggle(R.string.quick_flash, prefs.flashEnabled) { prefs.flashEnabled = it }
        },
        QuickMenuItem(getString(R.string.quick_replace)) {
            toggle(R.string.quick_replace, prefs.replaceEnabled) { prefs.replaceEnabled = it }
        },
        QuickMenuItem(getString(R.string.quick_badge)) {
            prefs.badgeEnabled = false
            toastMsg(getString(R.string.quick_badge_hidden))
        }
    )

    private fun launchActivity(cls: Class<*>) {
        runCatching {
            startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** 기능 ON/OFF 토글 + 새 상태를 토스트로 안내. */
    private fun toggle(labelRes: Int, current: Boolean, set: (Boolean) -> Unit) {
        val next = !current
        set(next)
        val state = getString(if (next) R.string.quick_state_on else R.string.quick_state_off)
        toastMsg("${getString(labelRes)} $state")
    }

    private fun toastMsg(msg: String) {
        runCatching { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
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
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                markEditableActivity(event)
                selectionMonitor.onSelectionChanged(event)
            }
            // 글자가 실제 편집칸에 들어갔다는 가장 직접적인 신호(포커스 경고 오발동 방지용).
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ->
                markEditableActivity(event)
        }
    }

    /** 이벤트 소스가 편집 가능한 노드면 "최근 입력 실착" 시각을 갱신(텍스트 내용은 읽지 않음). */
    private fun markEditableActivity(event: AccessibilityEvent) {
        if (!prefs.noFocusEnabled) return // 포커스 경고가 꺼져 있으면 노드 접근조차 하지 않음
        val editable = runCatching { event.source?.isEditable }.getOrNull()
        if (editable == true) lastEditableActivityAt = SystemClock.uptimeMillis()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        if (!initialized) return false
        // 포커스 조회는 게이트 통과 후에만 지연 평가된다(저사양 최적화 Bug 1).
        // 최근 입력 실착이 있으면 포커스 조회 없이 "포커스 있음"으로 처리(오경고 방지).
        keyMonitor.onKeyEvent(
            event,
            recentEditableActivity = {
                SystemClock.uptimeMillis() - lastEditableActivityAt < RECENT_INPUT_MS
            },
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

    companion object {
        /**
         * 직전 입력 실착으로 "포커스 있음"을 인정하는 시간(ms). 타이핑 중 편집 이벤트 간격을 넉넉히
         * 덮어 오경고를 막되, 입력칸을 떠난 뒤에는 곧 경고가 정상 동작하도록 너무 길지 않게 둔다.
         */
        private const val RECENT_INPUT_MS = 1200L
    }
}
