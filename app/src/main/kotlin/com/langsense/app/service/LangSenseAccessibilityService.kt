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
import android.view.accessibility.AccessibilityWindowInfo
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
        initialized = true
        // 초기 소프트 키보드 표시 상태 반영 후 배지 표시(기능 OFF면 windows 순회 없이 배지만 평가).
        refreshSoftKeyboardState()
        refreshBadge()
    }

    /** 소프트 키보드(IME 창)가 현재 화면에 떠 있는지 캐시. "터치 키보드 제외" 게이트 기준(추가 기능 2). */
    private var softKeyboardVisible = false

    /**
     * 기능 활성 여부 (추가 기능 2).
     * "터치 키보드 제외"가 꺼져 있으면 항상 활성(기존 동작). 켜져 있으면 **소프트 키보드(터치 입력)가
     * 떠 있지 않을 때만** 활성 — 즉 외장 키보드로 입력 중(소프트 키보드 숨김)이면 활성, 화면 터치
     * 키보드를 쓰는 동안(소프트 키보드 표시)이면 플래시/배지/경고/교체를 모두 끈다.
     *
     * 'HW 키보드 연결 여부'가 아니라 '소프트 키보드 표시 여부'로 판정하는 이유: 외장 키보드를 상시
     * 연결해 두는 사용자는 연결 기준이면 늘 활성이라 터치 입력 시 꺼지지 않는다(기존 미작동 원인).
     * 소프트 키보드 표시 여부가 "지금 어느 키보드로 입력하는가"를 직접 반영해 두 요건을 동시에 만족한다.
     */
    private fun featuresEnabled(): Boolean =
        !prefs.excludeTouchKeyboard || !softKeyboardVisible

    /** IME 창 높이 측정 재사용 버퍼(메인 스레드 전용 — windows 콜백/이벤트가 모두 메인). */
    private val imeBoundsBuf = android.graphics.Rect()

    /**
     * 접근성 윈도우에 "실제 화면 터치 키보드(IME 입력뷰)"가 떠 있으면 표시 중으로 본다.
     *
     * ⚠️ 치명 버그 방지: 단순히 `TYPE_INPUT_METHOD` 창의 **존재**만으로 판정하면 안 된다.
     * 외장(블루투스) 키보드를 쓰는 사용자가 시스템 설정에서 '하드웨어 키보드 툴바'(클립보드/추천
     * strip)를 켜 두면, 이 **얇은 툴바도 TYPE_INPUT_METHOD 창**이라 존재만 보면 외장 키보드로
     * 입력 중인데도 "터치 키보드 켜짐"으로 오인해 기능 전체가 꺼졌다(사용자 보고 버그).
     *
     * 그래서 IME 창의 **높이**로 구분한다: 실제 터치 키보드는 화면을 크게 가리지만(보통 30%+),
     * HW 키보드 툴바는 얇은 띠(보통 한 자릿수 %)다. 화면 높이의
     * [IME_KEYBOARD_MIN_SCREEN_FRACTION] 이상을 차지하는 IME 창이 하나라도 있을 때만
     * '터치 키보드 표시'로 본다(툴바만 떠 있으면 외장 키보드 입력으로 보고 기능 유지).
     */
    private fun computeSoftKeyboardVisible(): Boolean = runCatching {
        val screenH = resources.displayMetrics.heightPixels
        if (screenH <= 0) return@runCatching false
        val minKeyboardPx = screenH * IME_KEYBOARD_MIN_SCREEN_FRACTION
        windows.any { w ->
            if (w?.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) return@any false
            w.getBoundsInScreen(imeBoundsBuf)
            imeBoundsBuf.height() >= minKeyboardPx // 얇은 HW 키보드 툴바는 '키보드 표시'로 보지 않음
        }
    }.getOrDefault(false)

    /**
     * 소프트 키보드 표시 상태를 갱신하고, 바뀌면 배지 표시를 다시 평가.
     * 기능 OFF(기본)면 비싼 windows 순회를 하지 않아 일반 사용자에겐 부하가 없다.
     */
    private fun refreshSoftKeyboardState() {
        if (!initialized) return
        if (!prefs.excludeTouchKeyboard) {
            if (softKeyboardVisible) { softKeyboardVisible = false; refreshBadge() }
            return
        }
        val now = computeSoftKeyboardVisible()
        if (now != softKeyboardVisible) {
            softKeyboardVisible = now
            refreshBadge()
        }
    }

    /** 배지 표시/숨김을 배지 설정과 기능 활성 여부에 따라 일관되게 반영. */
    private fun refreshBadge() {
        if (prefs.badgeEnabled && featuresEnabled()) overlay.showBadge(currentLang)
        else overlay.hideBadge()
    }

    /** 외장 키보드 연결/해제 시: 소프트 키보드 표시 상태가 함께 바뀌므로 재평가(추가 기능 2). */
    private fun onKeyboardPresenceChanged() {
        if (!initialized) return
        refreshSoftKeyboardState()
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
            // 설정 리스너 타이밍과 무관하게 즉시 배지를 숨긴다(이중 안전 — 숨기기 직후 미반영 방지).
            refreshBadge()
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
        // 터치 키보드 제외 ON + 소프트 키보드 표시 중 → 플래시/배지 모두 비활성(추가 기능 2).
        if (!featuresEnabled()) return
        overlay.showFlash(lang)
        overlay.updateBadge(lang)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!initialized) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                imeDetector.onWindowStateChanged(event)
                refreshSoftKeyboardState()
            }
            // 소프트 키보드(IME) 창의 등장/소멸은 주로 이 이벤트로 온다 → 표시 상태 재평가(추가 기능 2).
            // (기능 OFF 면 refreshSoftKeyboardState 가 즉시 빠져나가 일반 사용자에겐 부하가 없다)
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> refreshSoftKeyboardState()
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                markEditableActivity(event)
                // 터치 키보드 제외 ON + 소프트 키보드 표시 중 → 한영타 교체 비활성(추가 기능 2).
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
        // 터치 키보드 제외 ON + 소프트 키보드 표시 중 → 포커스 없는 키 입력 경고 비활성(추가 기능 2).
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
        if (!initialized) return
        keyboardDetector?.recheck()
        refreshSoftKeyboardState()
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
            Prefs.KEY_BADGE_ENABLED -> refreshBadge()
            // "터치 키보드 제외"를 켜면 현재 소프트 키보드 표시 상태를 즉시 계산해 반영(추가 기능 2).
            Prefs.KEY_EXCLUDE_TOUCH_KEYBOARD -> refreshSoftKeyboardState().also { refreshBadge() }
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

        /**
         * IME 창을 '실제 화면 터치 키보드'로 인정하는 최소 높이(화면 높이 대비 비율).
         * 외장 키보드 툴바(클립보드/추천 strip, 보통 한 자릿수 %)와 실제 터치 키보드(보통 30%+)
         * 사이에 넉넉한 여백이 있어, 0.22 면 기기/화면 방향이 달라도 안정적으로 둘을 가른다.
         * (이 값 미만 높이의 IME 창은 툴바로 보고 기능을 끄지 않는다 — 외장 키보드 툴바 오인 버그 방지)
         */
        private const val IME_KEYBOARD_MIN_SCREEN_FRACTION = 0.22f
    }
}
