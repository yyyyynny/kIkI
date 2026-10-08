package com.langsense.app.util

/**
 * 전환 원인 진단(추가 기능 3)의 순수 로직 — "원인 모를 자동 한/영 전환"이 실제로 무엇 때문인지
 * 사용자가 스스로 찾을 수 있게, 언어 전환 직전에 눌린 물리 키를 사람이 읽을 수 있는 설명으로
 * 남긴다. 실사례(2026-09): One UI 물리 키보드 설정에 깊숙이 숨어 있던 "언어 전환 바로가기"가
 * Space + 다른 키 조합에서 의도치 않게 발동했는데, 사용자가 정확히 어떤 키였는지조차 몰라
 * 원인을 찾는 데 오래 걸렸다 — 그 경험에서 나온 기능.
 *
 * Android 의존성이 없는 순수 Kotlin이라 JVM 단위 테스트가 가능하다([LangSenseAccessibilityService]
 * 가 실제 `KeyEvent`/링 버퍼 상태를 여기 넘기기만 한다).
 */
object KeyTriggerDiagnostics {

    /**
     * 진단 링 버퍼 크기. "최근 2초"가 아니라 "최근 이만큼의 키 다운"만 기억하는 구조라, 너무
     * 작으면 빠르게 타이핑하는 도중(초당 5~8타) 전환이 일어났을 때 정작 원인 키가 그 뒤에 눌린
     * 정상 타이핑 키들에 밀려 캡처 시점엔 이미 사라져 있을 수 있다(2026-09 발견 — 느리게 치다
     * 전환된 경우만 잘 잡히던 결함). 반복 이벤트를 [LangSenseAccessibilityService] 가 애초에
     * 기록하지 않는 것과 함께, 2초 창 안에 들어올 수 있는 "새로 눌린" 키 수에 여유를 두기 위해
     * 32로 잡았다(짧은 키 조합 자체는 2~4개면 충분하지만, 그 앞뒤로 섞여 들어오는 무관한 타이핑
     * 키까지 창이 버텨야 실제 원인이 밀려나지 않는다 — 2의 거듭제곱이라 딱 떨어지기도 하고,
     * `IntArray`/`LongArray` 각 32개는 메모리상 무시할 수준이라 더 키울 이유는 있어도 줄일
     * 이유는 없다).
     */
    const val BUFFER_SIZE = 32

    /**
     * 언어 전환이 감지된 시점 기준, 이 시간 안에 눌린 키만 "그 전환을 유발했을 가능성이 있는 키"로
     * 본다. `ImeStateDetector` 자체의 지연(신호 합치기 `COALESCE_MS`=150 + 굶주림방지 400 + no-op
     * 백오프 최대 600 + 권위 역행 재확인 최대 250)을 넉넉히 덮는 값이다.
     */
    const val CORRELATE_WINDOW_MS = 2000L

    /**
     * 원형 버퍼(`keyCodes`/`atUptimes`, 같은 길이·같은 인덱스로 짝지어짐, `writeIndex` 는 다음에
     * 쓸 자리 = 버퍼가 가득 찼다면 가장 오래된 값이 있는 자리)에서 [now] 기준 [windowMs] 안에 든
     * 키들을, **오래된 순서 → 최신 순서**로 훑어 처음 등장한 순서를 유지한 채 중복(길게 눌러 반복된
     * 키)을 제거하고 [keyName] 으로 이름을 붙여 반환한다. 아직 한 번도 채워지지 않은 슬롯
     * (`at == 0L`)은 무시한다.
     */
    fun recentKeyNames(
        keyCodes: IntArray,
        atUptimes: LongArray,
        writeIndex: Int,
        now: Long,
        windowMs: Long = CORRELATE_WINDOW_MS,
        keyName: (Int) -> String
    ): List<String> {
        require(keyCodes.size == atUptimes.size) { "keyCodes/atUptimes 길이가 달라야 할 이유가 없다" }
        val size = keyCodes.size
        if (size == 0) return emptyList()
        val seen = LinkedHashSet<Int>()
        for (offset in 0 until size) {
            val i = (writeIndex + offset) % size
            val at = atUptimes[i]
            if (at == 0L || now - at > windowMs) continue
            seen.add(keyCodes[i])
        }
        return seen.map(keyName)
    }

    /** [recentKeyNames] 결과를 "A + B" 형태로 합친다. 빈 리스트면 null(호출부가 안내 문구로 대체). */
    fun describe(names: List<String>): String? = names.takeIf { it.isNotEmpty() }?.joinToString(" + ")

    // ── 키 없는 전환의 원인 분류(2026-10, S25+ 실사용 제보) ──

    /**
     * 시스템 화면이 키보드 언어를 바꿨다가 되돌린 뒤, 그 "되돌림"까지 자동 전환으로 보는 시간. 지문 창은 사용자가
     * 손가락을 올릴 때까지 떠 있으므로 넉넉히 잡는다.
     */
    const val AUTO_REVERT_MS = 30_000L

    /**
     * 키보드 언어를 스스로 바꾸는 시스템 화면인지 — 실기기 진단 기록에서 확인한 것: 삼성 지문 창(토스·네이버페이·갤러리
     * 잠금 해제), 삼성 패스. 이 화면이 앞에 뜨면 삼성 키보드가 영어로 바꾸고, 닫히면 되돌린다.
     * 한계: 다른 제조사·앱의 인증 화면은 목록에 없다 — 확장: 진단 기록의 "앞 화면"을 보고 패키지를 추가.
     */
    fun isSystemAuthScreen(pkg: String?): Boolean = pkg != null && (
        pkg.contains("biometrics", ignoreCase = true) || pkg.contains("samsungpass", ignoreCase = true)
        )

    /** 이 전환이 시스템이 한 자동 전환인지 — 시스템 화면이 앞일 때, 또는 직후 그 전 언어로 되돌아갈 때. */
    fun isAutoSwitch(front: String?, lang: String, now: Long, autoAt: Long, langBeforeAuto: String?): Boolean =
        isSystemAuthScreen(front) || (langBeforeAuto == lang && now - autoAt in 0..AUTO_REVERT_MS)

    /** 전환 순간의 상황. 진단 기록에 "키=값;…" 으로 저장하고, 사람이 읽을 문구는 화면이 만든다. */
    data class SwitchContext(
        val basis: String = "",
        val touchKeyboard: Boolean = false,
        val front: String? = null,
        val auto: Boolean = false,
    ) {
        fun encode(): String = listOfNotNull(
            basis.takeIf { it.isNotEmpty() }?.let { "basis=$it" },
            "touch=1".takeIf { touchKeyboard },
            front?.let { "front=$it" },
            "auto=1".takeIf { auto },
        ).joinToString(";")

        companion object {
            /** 예전 기록(사람이 읽는 문장, '=' 없음)이면 null — 화면이 그 문장을 그대로 보여 준다. */
            fun decode(s: String): SwitchContext? {
                if ('=' !in s) return null
                val m = s.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
                return SwitchContext(m["basis"].orEmpty(), m["touch"] == "1", m["front"], m["auto"] == "1")
            }
        }
    }

    enum class Cause { KEYS, SYSTEM_SCREEN, TOUCH_KEYBOARD, KEYBOARD_POPUP, UNKNOWN }

    /** 기록 한 건의 원인 — 시스템 화면 > 물리 키 > 터치 키보드 > 키보드 팝업 > 알 수 없음 순. */
    fun cause(keys: String, ctx: SwitchContext?): Cause = when {
        ctx?.auto == true -> Cause.SYSTEM_SCREEN
        keys.isNotEmpty() -> Cause.KEYS
        ctx?.touchKeyboard == true -> Cause.TOUCH_KEYBOARD
        ctx?.basis?.startsWith("popup") == true -> Cause.KEYBOARD_POPUP
        else -> Cause.UNKNOWN
    }
}
