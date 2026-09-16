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
     * 16으로 잡았다(짧은 키 조합 자체는 2~4개면 충분하지만, 그 앞뒤로 섞여 들어오는 무관한
     * 타이핑 키까지 창이 버텨야 실제 원인이 밀려나지 않는다).
     */
    const val BUFFER_SIZE = 16

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
}
