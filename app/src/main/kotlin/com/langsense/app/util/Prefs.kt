package com.langsense.app.util

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * SharedPreferences 래퍼 — 모든 설정 항목의 단일 진입점.
 *
 * 설정 변경은 즉시 적용된다(서비스가 [register] 로 변경을 구독).
 * 색상은 "#RRGGBB" 문자열로 저장하고(알파 채널 없음), 표시 시점에 사용자가 고른 불투명도
 * ([flashOpacityPercent] / [badgeBgOpacityPercent])를 [alphaFromPercent] 로 ARGB 알파에 굽는다.
 */
class Prefs(context: Context) {

    private val appContext: Context = context.applicationContext

    val sp: SharedPreferences =
        appContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- Feature 1: 플래시 ----
    var flashEnabled: Boolean
        get() = sp.getBoolean(KEY_FLASH_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_FLASH_ENABLED, v).apply()

    /** 깜박임 속도(1회 지속 시간) ms, 100~500. */
    var flashDurationMs: Int
        get() = sp.getInt(KEY_FLASH_DURATION, 200).coerceIn(100, 500)
        set(v) = sp.edit().putInt(KEY_FLASH_DURATION, v.coerceIn(100, 500)).apply()

    /** 깜박임 횟수 1~5. */
    var flashCount: Int
        get() = sp.getInt(KEY_FLASH_COUNT, 1).coerceIn(1, 5)
        set(v) = sp.edit().putInt(KEY_FLASH_COUNT, v.coerceIn(1, 5)).apply()

    /**
     * 플래시 불투명도 %(20~100, 기본 [DEFAULT_FLASH_OPACITY_PCT]=85). 낮출수록 아래 화면이 비친다.
     * 화면에 칠해지는 최종 알파는 [alphaFromPercent] 가 계산한다(기본값이면 예전 상수 0.85f 와 동일한 216).
     */
    var flashOpacityPercent: Int
        get() = sp.getInt(KEY_FLASH_OPACITY, DEFAULT_FLASH_OPACITY_PCT).coerceIn(MIN_OPACITY_PCT, 100)
        set(v) = sp.edit().putInt(KEY_FLASH_OPACITY, v.coerceIn(MIN_OPACITY_PCT, 100)).apply()

    // ---- Feature 2: 배지 ----
    var badgeEnabled: Boolean
        get() = sp.getBoolean(KEY_BADGE_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_BADGE_ENABLED, v).apply()

    /** 배지 위치(px). -1 이면 기본 위치(우하단)를 런타임에 계산. */
    var badgeX: Int
        get() = sp.getInt(KEY_BADGE_X, -1)
        set(v) = sp.edit().putInt(KEY_BADGE_X, v).apply()

    var badgeY: Int
        get() = sp.getInt(KEY_BADGE_Y, -1)
        set(v) = sp.edit().putInt(KEY_BADGE_Y, v).apply()

    fun setBadgePosition(x: Int, y: Int) =
        sp.edit().putInt(KEY_BADGE_X, x).putInt(KEY_BADGE_Y, y).apply()

    /** 배지 크기 단계: 0=소, 1=중(기본=기존 외형), 2=대. */
    var badgeSize: Int
        get() = sp.getInt(KEY_BADGE_SIZE, 1).coerceIn(0, 2)
        set(v) = sp.edit().putInt(KEY_BADGE_SIZE, v.coerceIn(0, 2)).apply()

    /**
     * 배지 배경 불투명도 %(20~100, 기본 [DEFAULT_BADGE_BG_OPACITY_PCT]=80).
     * 기본값이면 최종 알파가 204(0xCC)라 예전 상수 0.8f 및 원래 외형 #CC000000 과 정확히 같다.
     */
    var badgeBgOpacityPercent: Int
        get() = sp.getInt(KEY_BADGE_BG_OPACITY, DEFAULT_BADGE_BG_OPACITY_PCT).coerceIn(MIN_OPACITY_PCT, 100)
        set(v) = sp.edit().putInt(KEY_BADGE_BG_OPACITY, v.coerceIn(MIN_OPACITY_PCT, 100)).apply()

    /** 배지 배경색(#RRGGBB). 표시 시 [badgeBgOpacityPercent] 가 적용된다. */
    var badgeBgColorHex: String
        get() = sp.getString(KEY_BADGE_BG_COLOR, DEFAULT_BADGE_BG) ?: DEFAULT_BADGE_BG
        set(v) = sp.edit().putString(KEY_BADGE_BG_COLOR, normalizeHex(v)).apply()

    /** 배지 글씨색(#RRGGBB). 기본 흰색(불투명). */
    var badgeTextColorHex: String
        get() = sp.getString(KEY_BADGE_TEXT_COLOR, DEFAULT_BADGE_TEXT) ?: DEFAULT_BADGE_TEXT
        set(v) = sp.edit().putString(KEY_BADGE_TEXT_COLOR, normalizeHex(v)).apply()

    /**
     * 배지 배경 ARGB(반투명 [badgeBgOpacityPercent] 적용). 기본값은 기존 #CC000000 과 100% 동일.
     *
     * ⚠️ 불투명도를 여기서 **굽는** 것이 중요하다 — `OverlayManager` 의 배지 스타일 시그니처가
     * 이 최종 ARGB 를 그대로 담고 있어(저사양 원칙 ④의 "같은 스타일이면 재적용 생략"), 불투명도만
     * 바꿔도 시그니처가 달라져 재적용이 자동으로 일어난다. 반대로 불투명도를 `applyStyle` 에
     * 별도 인자로 넘기면 시그니처가 그대로라 "값을 바꿔도 배지가 안 변하는" 조용한 버그가 된다.
     */
    fun badgeBgColorArgb(): Int {
        val rgb = parseColorOrDefault(badgeBgColorHex, DEFAULT_BADGE_BG)
        return Color.argb(
            alphaFromPercent(badgeBgOpacityPercent), Color.red(rgb), Color.green(rgb), Color.blue(rgb)
        )
    }

    /** 배지 글씨 ARGB(불투명). 기본값은 기존 흰색과 동일. */
    fun badgeTextColorArgb(): Int {
        val rgb = parseColorOrDefault(badgeTextColorHex, DEFAULT_BADGE_TEXT)
        return Color.argb(255, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }

    // ---- Feature 3: 포커스 없는 키 입력 경고 ----
    var noFocusEnabled: Boolean
        get() = sp.getBoolean(KEY_NOFOCUS_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_NOFOCUS_ENABLED, v).apply()

    /** 경고 임계 횟수 1~5. */
    var noFocusThreshold: Int
        get() = sp.getInt(KEY_NOFOCUS_THRESHOLD, 3).coerceIn(1, 5)
        set(v) = sp.edit().putInt(KEY_NOFOCUS_THRESHOLD, v.coerceIn(1, 5)).apply()

    // ---- Feature 4: 한영타 교체 ----
    var replaceEnabled: Boolean
        get() = sp.getBoolean(KEY_REPLACE_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_REPLACE_ENABLED, v).apply()

    /** 신뢰도 임계값(%) 50~90. */
    var replaceConfidence: Int
        get() = sp.getInt(KEY_REPLACE_CONFIDENCE, 70).coerceIn(50, 90)
        set(v) = sp.edit().putInt(KEY_REPLACE_CONFIDENCE, v.coerceIn(50, 90)).apply()

    // ---- 추가 기능 2: 터치 키보드 제외 ----
    /**
     * ON 이면 외장(하드웨어) 키보드가 연결돼 있을 때만 기능이 동작하고, 소프트(터치) 키보드만
     * 쓰는 동안에는 플래시/배지/경고/교체를 전부 끈다. 기본 OFF(=항상 동작, 기존 동작 보존).
     */
    var excludeTouchKeyboard: Boolean
        get() = sp.getBoolean(KEY_EXCLUDE_TOUCH_KEYBOARD, false)
        set(v) = sp.edit().putBoolean(KEY_EXCLUDE_TOUCH_KEYBOARD, v).apply()

    /**
     * 외장 키보드 연결/해제 시 토스트로 안내(2026-09 추가). "터치 키보드 제외"와 독립적인
     * 옵션 — 켜져 있으면 이 옵션만으로도 [HardwareKeyboardDetector] 가 생성된다. 정보성이고
     * 침습적이지 않아 기본 ON(블루투스 키보드 배터리가 나가 연결이 끊긴 걸 모르고 계속 입력해
     * 한영타가 반복되는 상황을 조기에 알아챌 수 있게).
     */
    var keyboardConnectNotify: Boolean
        get() = sp.getBoolean(KEY_KEYBOARD_CONNECT_NOTIFY, true)
        set(v) = sp.edit().putBoolean(KEY_KEYBOARD_CONNECT_NOTIFY, v).apply()

    /**
     * "터치 키보드 제외" 하위 옵션(2026-09 추가) — 전환 원인 진단(추가 기능 3)이 켜져 있을 때,
     * 터치 키보드가 떠 있는 동안엔 그 진단 기록도 함께 멈출지. 기본 **OFF**: 진단은 "왜
     * 전환됐는가"를 밝히는 별개의 관심사라 보고, 터치 키보드 제외 여부와 무관하게 계속 기록한다
     * (즉 기본값 그대로면 터치 키보드 제외를 켜도 진단은 계속 동작). "터치 키보드 제외" 자체가
     * OFF 면 이 옵션은 아무 효과가 없다(`LangSenseAccessibilityService.featuresEnabled()` 가
     * 항상 true 라서).
     */
    var diagnosticPausedByTouchKeyboardExclude: Boolean
        get() = sp.getBoolean(KEY_DIAGNOSTIC_PAUSED_BY_TOUCH_EXCLUDE, false)
        set(v) = sp.edit().putBoolean(KEY_DIAGNOSTIC_PAUSED_BY_TOUCH_EXCLUDE, v).apply()

    // ---- 추가 기능 3: 전환 원인 진단 ----
    /**
     * ON 이면 실제 언어 전환이 감지된 시점에 최근 눌린 물리 키 조합을 [lastSwitchTriggerKeys] 로
     * 남긴다 — One UI 물리 키보드 설정에 숨어 있는 "언어 전환 바로가기"처럼 원인 모를 자동 전환을
     * 사용자가 직접 찾아낼 수 있게(2026-09 추가). 기본 OFF: 켜져 있으면 이 기능과 무관하게 모든
     * 물리 키가 시스템→앱 필터를 한 번 더 거쳐야 해서(`FLAG_REQUEST_FILTER_KEY_EVENTS`, 키당
     * Binder 왕복) 상시 비용이 있다 — 평소엔 꺼두고 원인 모를 전환이 반복될 때만 잠깐 켜서
     * 확인한 뒤 다시 끄는 용도.
     */
    var diagnosticKeyLoggingEnabled: Boolean
        get() = sp.getBoolean(KEY_DIAGNOSTIC_KEY_LOGGING, false)
        set(v) = sp.edit().putBoolean(KEY_DIAGNOSTIC_KEY_LOGGING, v).apply()

    /** 마지막으로 캡처된 전환 직전 키 조합(예: "SHIFT_LEFT + SPACE"). 없으면 빈 문자열. */
    var lastSwitchTriggerKeys: String
        get() = sp.getString(KEY_LAST_TRIGGER_KEYS, "") ?: ""
        set(v) = sp.edit().putString(KEY_LAST_TRIGGER_KEYS, v).apply()

    /** [lastSwitchTriggerKeys] 를 캡처한 시각(epoch ms, 화면 표시용). 0 이면 아직 캡처된 적 없음. */
    var lastSwitchTriggerAt: Long
        get() = sp.getLong(KEY_LAST_TRIGGER_AT, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_TRIGGER_AT, v).apply()

    fun clearLastSwitchTrigger() =
        sp.edit().remove(KEY_LAST_TRIGGER_KEYS).remove(KEY_LAST_TRIGGER_AT).apply()

    // ---- 플로팅 메뉴(배지 탭 래디얼 메뉴) ----
    /**
     * 저사양(움직임 줄이기) 모드. ON 이면 메뉴를 펼친 뒤의 연속 애니메이션(오브 morph/부유/별/먼지/
     * 선 호흡)을 끄고 유리 블러도 뺀다(펼침/수납 애니메이션은 유지).
     *
     * 사용자가 설정에서 명시적으로 정하지 않았으면([radialReduceMotionIsAuto]) 기기 사양으로 자동
     * 판정한다 — 풀모션 메뉴는 비합성 애니메이션 32개(backdrop-filter 오브 5 + SVG path morph 18 +
     * 블러 9)라 4GB 급 태블릿(Galaxy Tab S6 Lite 등)에서 60fps 유지가 안 된다.
     */
    var radialReduceMotion: Boolean
        get() = if (sp.contains(KEY_RADIAL_REDUCE_MOTION)) {
            sp.getBoolean(KEY_RADIAL_REDUCE_MOTION, false)
        } else {
            autoReduceMotion()
        }
        set(v) = sp.edit().putBoolean(KEY_RADIAL_REDUCE_MOTION, v).apply()

    /** 저사양 모드가 사용자 명시값이 아니라 자동 판정 중인지. */
    val radialReduceMotionIsAuto: Boolean
        get() = !sp.contains(KEY_RADIAL_REDUCE_MOTION)

    /**
     * 자동 판정: 저RAM 기기이거나 총 메모리 ≤ 4GiB(하드웨어 — 1회 계산 캐시), 또는 시스템 애니메이션
     * 배율이 0(사용자가 "애니메이션 제거"를 켠 상태 — 실시간 반영, WebView 의 prefers-reduced-motion 과
     * 같은 값을 따르므로 HTML 판정과 일치).
     */
    private fun autoReduceMotion(): Boolean {
        val hw = lowSpecHardware ?: runCatching {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            am.isLowRamDevice || mi.totalMem <= LOW_SPEC_TOTAL_MEM_BYTES
        }.getOrDefault(false).also { lowSpecHardware = it }
        return hw || !ValueAnimator.areAnimatorsEnabled()
    }

    /**
     * 퀵메뉴에 실제로 넣을 항목(id 리스트, 1~[MAX_QUICK_MENU_ITEMS]개, 사용자가 고른 것만 —
     * 백필 없음). 콤마 조인 문자열로 저장하고, 읽을 때 [resolveQuickMenuOrder] 로 보정한다
     * (저장값이 없거나 완전히 손상됐을 때만 기본 5개로 폴백. 정상적인 1~5개 선택은 그대로 반환).
     */
    var quickMenuOrder: List<String>
        get() = resolveQuickMenuOrder(sp.getString(KEY_QUICK_MENU_ORDER, null))
        set(v) = sp.edit().putString(KEY_QUICK_MENU_ORDER, v.joinToString(",")).apply()

    /** 래디얼 메뉴 강조색(#RRGGBB). 오브 림/글자/별/버스트 등. 기본은 기존 은은한 하늘색. */
    var radialAccentColorHex: String
        get() = sp.getString(KEY_RADIAL_ACCENT_COLOR, DEFAULT_RADIAL_ACCENT) ?: DEFAULT_RADIAL_ACCENT
        set(v) = sp.edit().putString(KEY_RADIAL_ACCENT_COLOR, normalizeHex(v)).apply()

    /** 래디얼 메뉴 글로우색(#RRGGBB). 선/오브의 확산 발광. 기본은 기존 짙은 파랑. */
    var radialGlowColorHex: String
        get() = sp.getString(KEY_RADIAL_GLOW_COLOR, DEFAULT_RADIAL_GLOW) ?: DEFAULT_RADIAL_GLOW
        set(v) = sp.edit().putString(KEY_RADIAL_GLOW_COLOR, normalizeHex(v)).apply()

    /**
     * 배지 탭 시 할 일. 기본 [BADGE_TAP_MENU](퀵메뉴 열기) — 또는 5개 액션 id 중 하나를 골라
     * 메뉴를 거치지 않고 탭 한 번에 바로 실행하게 할 수 있다. 미상 값은 [BADGE_TAP_MENU] 로 폴백.
     */
    var badgeTapAction: String
        get() {
            val v = sp.getString(KEY_BADGE_TAP_ACTION, BADGE_TAP_MENU) ?: BADGE_TAP_MENU
            return if (v == BADGE_TAP_MENU || v in QUICK_MENU_ACTION_IDS) v else BADGE_TAP_MENU
        }
        set(v) = sp.edit().putString(KEY_BADGE_TAP_ACTION, v).apply()

    // ---- 지원 언어 토글 ----
    fun isLangEnabled(lang: String): Boolean = when (lang) {
        ImeLocaleParser.KO -> sp.getBoolean(KEY_LANG_KO, true)
        ImeLocaleParser.EN -> sp.getBoolean(KEY_LANG_EN, true)
        // [일본어 비활성화] ImeLocaleParser.JA -> sp.getBoolean(KEY_LANG_JA, true)
        else -> true // 기타 언어는 항상 표시
    }

    fun setLangEnabled(lang: String, enabled: Boolean) {
        val key = when (lang) {
            ImeLocaleParser.KO -> KEY_LANG_KO
            ImeLocaleParser.EN -> KEY_LANG_EN
            // [일본어 비활성화] ImeLocaleParser.JA -> KEY_LANG_JA
            else -> return
        }
        sp.edit().putBoolean(key, enabled).apply()
    }

    // ---- 언어별 플래시 색상 ----
    fun colorHex(lang: String): String = when (lang) {
        ImeLocaleParser.KO -> sp.getString(KEY_COLOR_KO, DEFAULT_KO) ?: DEFAULT_KO
        ImeLocaleParser.EN -> sp.getString(KEY_COLOR_EN, DEFAULT_EN) ?: DEFAULT_EN
        // [일본어 비활성화] ImeLocaleParser.JA -> sp.getString(KEY_COLOR_JA, DEFAULT_JA) ?: DEFAULT_JA
        else -> DEFAULT_OTHER
    }

    fun setColorHex(lang: String, hex: String) {
        val key = when (lang) {
            ImeLocaleParser.KO -> KEY_COLOR_KO
            ImeLocaleParser.EN -> KEY_COLOR_EN
            // [일본어 비활성화] ImeLocaleParser.JA -> KEY_COLOR_JA
            else -> return
        }
        sp.edit().putString(key, normalizeHex(hex)).apply()
    }

    /** 플래시에 사용할 ARGB 색상(불투명도 [flashOpacityPercent] 적용). 잘못된 코드는 기본 회색으로. */
    fun flashColorArgb(lang: String): Int {
        val rgb = parseColorOrDefault(colorHex(lang), DEFAULT_OTHER)
        return Color.argb(
            alphaFromPercent(flashOpacityPercent), Color.red(rgb), Color.green(rgb), Color.blue(rgb)
        )
    }

    /** 포커스 없음 경고 플래시의 ARGB — 색은 고정 회색, 불투명도는 플래시 설정을 따른다. */
    fun warningFlashArgb(): Int =
        Color.argb(alphaFromPercent(flashOpacityPercent), 0x55, 0x55, 0x55)

    /** "#RRGGBB" 파싱(실패 시 default, default 도 실패하면 회색). 플래시/배지 색 공용. */
    private fun parseColorOrDefault(hex: String, default: String): Int =
        try {
            Color.parseColor(hex)
        } catch (e: IllegalArgumentException) {
            try { Color.parseColor(default) } catch (e2: IllegalArgumentException) { Color.GRAY }
        }

    // ---- 화면 테마 (앱 화면 전용 — 오버레이는 각자의 색 설정을 쓴다) ----

    /**
     * 사용자가 고른 앱 화면 테마 id([UI_THEME_IDS] 중 하나, 기본 [THEME_SYSTEM]).
     * 저장값이 손상되거나 모르는 값이면 조용히 기본으로 폴백한다([badgeTapAction] 과 같은 패턴).
     */
    var uiTheme: String
        get() = (sp.getString(KEY_UI_THEME, THEME_SYSTEM) ?: THEME_SYSTEM)
            .let { if (it in UI_THEME_IDS) it else THEME_SYSTEM }
        set(v) = sp.edit().putString(KEY_UI_THEME, if (v in UI_THEME_IDS) v else THEME_SYSTEM).apply()

    // ---- "NEW" 배지 / "이사 갔어요" 안내의 자동 만료 ----

    /**
     * 사용자가 이미 열어 봐서 NEW 표시를 끌 마커 id 집합.
     *
     * ⚠️ `getStringSet` 이 돌려주는 Set 은 **그대로 수정하면 안 된다**(문서상 미정의 동작).
     * 항상 복사본을 만들어 다시 넣는다.
     */
    private var seenMarkers: Set<String>
        get() = sp.getStringSet(KEY_SEEN_MARKERS, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_SEEN_MARKERS, v).apply()

    /** [markerId] 를 이미 본 것으로 기록(멱등). */
    fun markSeen(markerId: String) {
        val cur = seenMarkers
        if (markerId in cur) return
        seenMarkers = cur + markerId
    }

    /**
     * 지금 [markerId] 에 NEW(또는 이사 안내)를 보여야 하는가.
     *
     * 두 겹으로 **저절로** 사라진다 — 릴리스마다 손으로 지우는 코드가 없어야 잊지 않는다:
     *  1. 사용자가 그 화면을 한 번 열면 [markSeen] 으로 기록돼 그 사람에겐 즉시 사라진다.
     *  2. 안 열어 본 사람에게도, 표시를 도입한 버전에서 [MARKER_LIFESPAN_VERSIONS] 만큼 지나면
     *     [isMarkerFresh] 가 false 가 되어 코드가 알아서 안 보여준다.
     */
    fun shouldShowMarker(markerId: String, sinceVersion: Int, currentVersion: Int): Boolean =
        isMarkerFresh(sinceVersion, currentVersion) && markerId !in seenMarkers

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val NAME = "langsense_prefs"

        /** 불투명도 설정의 하한 — 이보다 낮으면 사실상 안 보여서 "고장났다"로 오인된다. */
        const val MIN_OPACITY_PCT = 20

        /** 플래시 기본 불투명도 %. 예전 하드코딩 상수 0.85f 를 그대로 옮긴 값. */
        const val DEFAULT_FLASH_OPACITY_PCT = 85

        /**
         * 불투명도 %(0~100) → ARGB 알파(0~255).
         *
         * ⚠️ **정수 나눗셈(버림)이어야 한다.** 예전 코드가 `(0.85f * 255).toInt()` = 216 이었으므로
         * 같은 216 이 나와야 기본값에서 외형이 한 바이트도 안 바뀐다. `Math.round(pct * 2.55f)` 로
         * 쓰면 85 에서 217 이 나와 어긋난다(80 → 204 = 0xCC 도 마찬가지).
         *
         * Android 의존성이 없는 순수 함수라 JVM 단위 테스트 대상.
         */
        fun alphaFromPercent(pct: Int): Int = pct.coerceIn(0, 100) * 255 / 100

        /** 하드웨어 저사양 판정 캐시(프로세스 수명 동안 불변). */
        @Volatile
        private var lowSpecHardware: Boolean? = null

        /** 이 총 메모리 이하면 저사양으로 본다(4GiB — Galaxy Tab S6 Lite 급). */
        private const val LOW_SPEC_TOTAL_MEM_BYTES = 4L shl 30

        /** 배지 배경 기본 불투명도 %. 기본 배경(#000000)에 적용하면 기존 #CC000000 과 동일(알파 204). */
        const val DEFAULT_BADGE_BG_OPACITY_PCT = 80
        const val DEFAULT_BADGE_BG = "#000000"
        const val DEFAULT_BADGE_TEXT = "#FFFFFF"

        const val DEFAULT_KO = "#CC2D2D"
        const val DEFAULT_EN = "#1A6EBD"
        // [일본어 비활성화] 색 기본값/키 보존(현재 미사용, 추후 재도입 시 참조 복구).
        const val DEFAULT_JA = "#2D8C4E"
        const val DEFAULT_OTHER = "#555555"

        const val KEY_FLASH_ENABLED = "flash_enabled"
        const val KEY_FLASH_DURATION = "flash_duration_ms"
        const val KEY_FLASH_COUNT = "flash_count"
        const val KEY_FLASH_OPACITY = "flash_opacity_pct"
        const val KEY_BADGE_BG_OPACITY = "badge_bg_opacity_pct"
        const val KEY_BADGE_ENABLED = "badge_enabled"
        const val KEY_BADGE_X = "badge_x"
        const val KEY_BADGE_Y = "badge_y"
        const val KEY_BADGE_SIZE = "badge_size"
        const val KEY_BADGE_BG_COLOR = "badge_bg_color"
        const val KEY_BADGE_TEXT_COLOR = "badge_text_color"
        const val KEY_NOFOCUS_ENABLED = "nofocus_enabled"
        const val KEY_NOFOCUS_THRESHOLD = "nofocus_threshold"
        const val KEY_REPLACE_ENABLED = "replace_enabled"
        const val KEY_REPLACE_CONFIDENCE = "replace_confidence"
        const val KEY_EXCLUDE_TOUCH_KEYBOARD = "exclude_touch_keyboard"
        const val KEY_KEYBOARD_CONNECT_NOTIFY = "keyboard_connect_notify"
        const val KEY_DIAGNOSTIC_PAUSED_BY_TOUCH_EXCLUDE = "diagnostic_paused_by_touch_exclude"
        const val KEY_DIAGNOSTIC_KEY_LOGGING = "diagnostic_key_logging"
        const val KEY_LAST_TRIGGER_KEYS = "last_switch_trigger_keys"
        const val KEY_LAST_TRIGGER_AT = "last_switch_trigger_at"
        const val KEY_RADIAL_REDUCE_MOTION = "radial_reduce_motion"
        const val KEY_QUICK_MENU_ORDER = "quick_menu_order"
        const val KEY_BADGE_TAP_ACTION = "badge_tap_action"
        const val KEY_RADIAL_ACCENT_COLOR = "radial_accent_color"
        const val KEY_RADIAL_GLOW_COLOR = "radial_glow_color"
        const val KEY_UI_THEME = "ui_theme"
        const val KEY_SEEN_MARKERS = "seen_markers"

        // ---- 화면 테마 id ----
        /** 기기의 밝게/어둡게 설정을 따라간다(기본). 자체 팔레트가 아니라 라이트/다크로 해석된다. */
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_BEIGE = "beige"
        const val THEME_CYBER = "cyber"
        const val THEME_HIGH_CONTRAST = "high_contrast"

        /** 설정 화면에 보여줄 순서 그대로. 기본값(system)이 맨 앞. */
        val UI_THEME_IDS = listOf(
            THEME_SYSTEM, THEME_LIGHT, THEME_DARK, THEME_BEIGE, THEME_CYBER, THEME_HIGH_CONTRAST
        )

        // ---- NEW / 이사 안내 마커 ----

        /**
         * 마커를 도입한 버전에서 이만큼 지나면 아무도 안 봤어도 표시를 멈춘다.
         *
         * 이 숫자가 있는 이유: "다음 릴리스에서 NEW 를 지운다"는 수동 절차는 **반드시 잊어버려서**
         * 1년 전 기능에 NEW 가 붙어 있게 된다. 마커를 달 때 도입 버전만 적어 두면 만료는 코드가 한다.
         */
        const val MARKER_LIFESPAN_VERSIONS = 3

        /** 마커 id — 새 마커를 추가할 땐 여기 상수 + [MARKER_SINCE] 한 줄만 적으면 된다. */
        const val MARKER_THEME = "theme"
        const val MARKER_FLASH_OPACITY = "flash_opacity"
        const val MARKER_BADGE_OPACITY = "badge_opacity"
        const val MARKER_MOVED_DIAG_PAUSE = "moved_diag_pause"
        const val MARKER_MOVED_QUICK_MENU = "moved_quick_menu"

        /** 각 마커가 도입된 versionCode. 만료 계산의 유일한 입력. */
        val MARKER_SINCE = mapOf(
            MARKER_THEME to 2,
            MARKER_FLASH_OPACITY to 2,
            MARKER_BADGE_OPACITY to 2,
            MARKER_MOVED_DIAG_PAUSE to 2,
            MARKER_MOVED_QUICK_MENU to 2
        )

        /**
         * 마커가 아직 유효한 기간 안인가(순수 함수 — JVM 단위 테스트 대상).
         *
         * 도입 버전보다 이전 버전에서는 보여주지 않는다(마커를 미리 심어 둘 수 있게).
         */
        fun isMarkerFresh(sinceVersion: Int, currentVersion: Int): Boolean =
            currentVersion in sinceVersion until (sinceVersion + MARKER_LIFESPAN_VERSIONS)

        /** 원본 HTML 의 은은한 하늘색/짙은 파랑(CLAUDE.md Feature 5 참조) — 커스터마이즈 기본값. */
        const val DEFAULT_RADIAL_ACCENT = "#CDEEFF"
        const val DEFAULT_RADIAL_GLOW = "#4DA8FF"

        // 퀵메뉴 액션 id — LangSenseAccessibilityService 의 액션 레지스트리 키와 일치해야 한다.
        const val ACTION_OPEN_APP = "open_app"
        const val ACTION_OPEN_SETTINGS = "open_settings"
        const val ACTION_TOGGLE_FLASH = "toggle_flash"
        const val ACTION_TOGGLE_REPLACE = "toggle_replace"
        const val ACTION_HIDE_BADGE = "hide_badge"
        const val ACTION_TOGGLE_NOFOCUS = "toggle_nofocus"
        const val ACTION_TOGGLE_TOUCHKB = "toggle_touchkb"
        const val ACTION_TOGGLE_LOWSPEC = "toggle_lowspec"
        const val ACTION_CYCLE_BADGE_SIZE = "cycle_badge_size"

        /**
         * 액션 풀(9개). 앞 5개는 예전에 고정돼 있던 순서·id 와 100% 동일 → 업데이트해도 저장값이
         * 없는 기존 사용자는 [resolveQuickMenuOrder] 의 폴백으로 무변화. 뒤 4개가 신규 추가분.
         */
        val QUICK_MENU_ACTION_IDS = listOf(
            ACTION_OPEN_APP, ACTION_OPEN_SETTINGS, ACTION_TOGGLE_FLASH, ACTION_TOGGLE_REPLACE, ACTION_HIDE_BADGE,
            ACTION_TOGGLE_NOFOCUS, ACTION_TOGGLE_TOUCHKB, ACTION_TOGGLE_LOWSPEC, ACTION_CYCLE_BADGE_SIZE
        )

        /** 메뉴에 동시에 뜨는 최대 항목 수 — `radialmenu.html` 팬 지오메트리의 가변 상한. */
        const val MAX_QUICK_MENU_ITEMS = 5

        /** 배지 탭 = 퀵메뉴 열기(기존 동작, 기본값). */
        const val BADGE_TAP_MENU = "menu"

        /**
         * 저장된 항목 문자열을 유효 id·중복 없음·최대 [MAX_QUICK_MENU_ITEMS]개로 보정하는 순수
         * 함수(Context 불필요 — 단위 테스트 대상). 사용자가 고른 것만 그 순서대로 반환하며(백필
         * 없음 — "선택 안 한 건 안 보인다"), 결과가 0개가 되는 경우(저장값 없음/전부 미상 id 등
         * 손상된 상태)에만 기본 5개로 폴백한다.
         */
        fun resolveQuickMenuOrder(raw: String?): List<String> {
            val saved = raw?.split(",")?.map { it.trim() }.orEmpty()
            val result = LinkedHashSet<String>()
            saved.forEach { if (it in QUICK_MENU_ACTION_IDS) result.add(it) }
            val capped = result.toList().take(MAX_QUICK_MENU_ITEMS)
            return capped.ifEmpty { QUICK_MENU_ACTION_IDS.take(MAX_QUICK_MENU_ITEMS) }
        }
        const val KEY_LANG_KO = "lang_ko"
        const val KEY_LANG_EN = "lang_en"
        const val KEY_LANG_JA = "lang_ja" // [일본어 비활성화] 미사용, 보존
        const val KEY_COLOR_KO = "color_ko"
        const val KEY_COLOR_EN = "color_en"
        const val KEY_COLOR_JA = "color_ja" // [일본어 비활성화] 미사용, 보존

        /** "#rrggbb" 형식으로 정규화. 유효하지 않으면 기본 회색. */
        fun normalizeHex(input: String): String = normalizeHexOrNull(input) ?: DEFAULT_OTHER

        /**
         * [normalizeHex] 와 같지만 유효하지 않으면 기본값 대신 null.
         * 사용자 입력을 저장하기 전에는 이쪽을 써서, 잘못된 입력이 기존 색을 회색으로
         * 덮어쓰지 않게 한다(입력 도중 포커스가 빠지는 경우 등).
         */
        fun normalizeHexOrNull(input: String): String? {
            val s = input.trim()
            val withHash = if (s.startsWith("#")) s else "#$s"
            return try {
                Color.parseColor(withHash) // 검증만
                withHash.uppercase()
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
