package com.langsense.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
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
import com.langsense.app.util.HardwareKeyboardDetector
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

    /** 외장(하드웨어) 키보드 연결 감지 (추가 기능 2: 터치 키보드 제외). */
    private var keyboardDetector: HardwareKeyboardDetector? = null

    private var currentLang: String = ImeLocaleParser.UNKNOWN
    private var initialized = false

    /**
     * 편집칸에 글자가 실제로 들어간 마지막 시각(uptime). text/selection 변경 이벤트가 편집 노드에서
     * 올 때 갱신한다. 포커스 없는 키 입력 경고의 오발동(입력은 되는데 경고가 뜨는 문제)을 막는 데 쓴다.
     * onKeyEvent/onAccessibilityEvent 가 모두 메인 스레드에서 호출되므로 이 값은 메인 스레드에서만 접근한다.
     */
    private var lastEditableActivityAt = 0L

    /**
     * 포커스 없는 키 입력 평가(접근성 노드 IPC)를 처리하는 백그라운드 스레드 (Bug 1).
     * onKeyEvent 가 도는 메인 스레드에서 무거운 조회를 빼내 키 디스패치가 막히지 않게 한다.
     */
    private var keyEvalThread: HandlerThread? = null
    private var keyEvalHandler: Handler? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        // ★ 재연결 방어: onServiceConnected 는 같은 인스턴스에서 다시 호출될 수 있다(설정 변경/재바인드).
        // 정리 없이 재초기화하면 BroadcastReceiver/ContentObserver 가 중복 등록되어 한 번의 한/영 전환에
        // 여러 detector 가 각각 발동 → "매번 2회 이상 깜박임"의 근본 원인이 된다. 먼저 이전 상태를 정리.
        if (initialized) cleanup()

        prefs = Prefs(this)
        overlay = OverlayManager(this, prefs)

        // 포커스 조회(IPC)를 메인 스레드에서 분리할 백그라운드 스레드 시작 (Bug 1).
        keyEvalThread = HandlerThread("kIkI-keyeval").also {
            it.start()
            keyEvalHandler = Handler(it.looper)
        }

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

        // 외장 키보드 연결 감지 시작(추가 기능 2). 상태 변화 시 배지/기능 활성 상태 갱신.
        keyboardDetector = HardwareKeyboardDetector(this) { onKeyboardPresenceChanged() }
            .also { it.start() }

        prefs.register(this)

        // 리스너 등록 + 현재 언어 캐시 + 초기 배지
        currentLang = imeDetector.start()
        refreshBadge()

        initialized = true
    }

    /**
     * 기능 활성 여부 (추가 기능 2). "터치 키보드 제외"가 꺼져 있으면 항상 활성(기존 동작),
     * 켜져 있으면 외장 키보드가 연결돼 있을 때만 활성(소프트 키보드만 쓰는 동안 전부 비활성).
     */
    private fun featuresEnabled(): Boolean =
        !prefs.excludeTouchKeyboard || (keyboardDetector?.connected == true)

    /** 배지 표시/숨김을 배지 설정과 기능 활성 여부에 따라 일관되게 반영. */
    private fun refreshBadge() {
        if (prefs.badgeEnabled && featuresEnabled()) overlay.showBadge(currentLang)
        else overlay.hideBadge()
    }

    /** 외장 키보드 연결/해제 시: 배지 갱신(켜지면 표시, 꺼지면 숨김 — 배지가 닫히며 간편 메뉴도 함께 닫힘). */
    private fun onKeyboardPresenceChanged() {
        if (!initialized) return
        refreshBadge()
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
        // 터치 키보드 제외 ON + 외장 키보드 없음 → 플래시/배지 모두 비활성(추가 기능 2).
        if (!featuresEnabled()) return
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
                // 터치 키보드 제외 ON + 외장 키보드 없음 → 한영타 교체 비활성(추가 기능 2).
                if (featuresEnabled()) selectionMonitor.onSelectionChanged(event)
            }
            // 글자가 실제 편집칸에 들어갔다는 가장 직접적인 신호(포커스 경고 오발동 방지용).
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ->
                markEditableActivity(event)
        }
    }

    /**
     * 이벤트 소스가 편집 가능한 노드면 "최근 입력 실착" 시각을 갱신(텍스트 내용은 읽지 않음).
     *
     * (Bug 1) `event.source?.isEditable` 은 접근성 노드 IPC 라 빠른 연타 시 글자마다 들어오는
     * text-changed 이벤트에서 이를 매번 수행하면 메인 루퍼에 부담이 쌓인다. 이미 충분히 최근에
     * 편집 활동을 확인했다면([EDIT_RECHECK_MS] 이내) 재확인(IPC)을 생략한다 — 비교를 통과한 키 입력은
     * 어차피 "포커스 있음"으로 간주되므로 결과가 같다. 갱신은 실제로 editable 일 때만 한다(비편집
     * 이벤트로 억제 창이 잘못 늘어나지 않게).
     */
    private fun markEditableActivity(event: AccessibilityEvent) {
        if (!prefs.noFocusEnabled) return // 포커스 경고가 꺼져 있으면 노드 접근조차 하지 않음
        val now = SystemClock.uptimeMillis()
        if (now - lastEditableActivityAt < EDIT_RECHECK_MS) return // 이미 최근 확인됨 → IPC 생략
        val editable = runCatching { event.source?.isEditable }.getOrNull()
        if (editable == true) lastEditableActivityAt = now
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        val e = event ?: return false
        if (!initialized) return false
        // 터치 키보드 제외 ON + 외장 키보드 없음 → 포커스 없는 키 입력 경고 비활성(추가 기능 2).
        // (이 경우 물리 키 이벤트 자체가 거의 없지만, 가상 키 등 예외를 위해 게이트한다)
        if (!featuresEnabled()) return false
        // (Bug 1) 메인(디스패치) 스레드에서는 키 이벤트 속성만 보는 저비용 판정만 동기로 하고 즉시 반환한다.
        // 무거운 포커스 조회(노드 트리 IPC)는 백그라운드 스레드로 넘겨 키 디스패치가 멈추지 않게 한다.
        if (!keyMonitor.isTypingCandidate(e)) return false
        // 최근 입력 실착 여부는 메인 스레드에서 저렴하게 스냅샷(타임스탬프 비교)해 백그라운드로 전달.
        val recent = SystemClock.uptimeMillis() - lastEditableActivityAt < RECENT_INPUT_MS
        keyEvalHandler?.post {
            keyMonitor.handleCandidate(
                recentEditableActivity = recent,
                focusProbe = { hasActiveEditableFocus() },
                focusReProbe = { hasAnyEditableFocus() }
            )
        }
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

    /**
     * 외장 키보드 연결/해제는 보통 [HardwareKeyboardDetector] 의 InputDeviceListener 가 잡지만,
     * 도킹/구성 변경 등 일부 경로에서 키보드 구성이 바뀔 수 있어 백스톱으로 한 번 더 확인한다(추가 기능 2).
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (initialized) keyboardDetector?.recheck()
    }

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
        keyboardDetector?.stop()
        keyboardDetector = null
        if (::overlay.isInitialized) overlay.removeAll()
        // 백그라운드 키 평가 스레드 정리(예약된 평가 제거 후 루퍼 종료) — 누수 방지.
        keyEvalHandler?.removeCallbacksAndMessages(null)
        keyEvalThread?.quitSafely()
        keyEvalThread = null
        keyEvalHandler = null
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (!initialized) return
        when (key) {
            // 배지 표시 설정 또는 "터치 키보드 제외" 토글이 바뀌면 배지 표시/숨김을 다시 평가(추가 기능 2).
            Prefs.KEY_BADGE_ENABLED, Prefs.KEY_EXCLUDE_TOUCH_KEYBOARD -> refreshBadge()
            // 배지 크기/색은 표시 중인 배지에 즉시 재적용(꺼져 있으면 다음 표시 때 반영).
            Prefs.KEY_BADGE_SIZE, Prefs.KEY_BADGE_BG_COLOR, Prefs.KEY_BADGE_TEXT_COLOR -> {
                if (prefs.badgeEnabled && featuresEnabled()) overlay.updateBadge(currentLang)
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

        /**
         * (Bug 1) 편집 활동 재확인 간격(ms). 이 시간 안에 이미 편집 활동을 확인했으면 글자마다
         * 다시 `isEditable`(IPC)을 보지 않는다. [RECENT_INPUT_MS] 보다 짧아 "최근 입력 실착" 판정은
         * 그대로 유지하면서 연타 시 IPC 폭주만 줄인다.
         */
        private const val EDIT_RECHECK_MS = 300L
    }
}
