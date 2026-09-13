package com.langsense.app.util

/**
 * 두벌식(QWERTY) ↔ 한국어 변환 + 한영타 신뢰도 판정.
 *
 * "한영타" = 영어 자판 상태에서 한국어를 친 결과 (예: `dkssud` → `안녕`).
 *
 * 핵심은 [convertEngToKor] 의 두벌식 오토마타로, 실제 한국어 IME 와 동일하게
 * - 초성/중성/종성 조합
 * - 복합 중성(ㅘ/ㅚ/ㅢ …) 결합
 * - 복합 종성(ㄺ/ㄼ/ㅄ …) 결합
 * - 연음(도깨비불) 현상: 종성 뒤에 모음이 오면 종성이 다음 음절의 초성으로 이동
 * 을 모두 처리한다.
 *
 * 외부 라이브러리/안드로이드 의존성이 없는 순수 Kotlin 이라 JVM 단위 테스트가 가능하다.
 * 모든 처리는 온디바이스 로컬 연산이며 외부 전송이 전혀 없다.
 *
 * 참고: 두벌식 자판 배열은 공개된 표준 배열(KS X 5002)이며 알고리즘은 직접 구현.
 */
object HangulConverter {

    private const val HANGUL_BASE = 0xAC00
    private const val HANGUL_LAST = 0xD7A3
    private const val JUNG_COUNT = 21
    private const val JONG_COUNT = 28

    // 호환 자모 범위 (조합되지 못하고 남은 낱자모 판별용)
    private const val COMPAT_JAMO_START = 0x3130
    private const val COMPAT_JAMO_END = 0x318F

    /**
     * 한영타 오탐 차단용 영어 상용어 목록([detectEnglishToKorean] 참조).
     *
     * 두벌식에서 모음키(y u i o p h j k l b n m)와 자음키가 번갈아 오는 영단어는 한글 음절로
     * 100% 조합되어(the→솓, and→뭉, for→랙, with→쟈소, work→재가 …) 조합률 기반 판정이
     * 최고 신뢰도를 준다. 길이·음절수 같은 구조 신호로는 진짜 한영타(dkssud=안녕)와 분리되지
     * 않으므로, 고빈도 영단어를 어휘로 직접 배제한다.
     */
    private val ENGLISH_STOPWORDS: Set<String> = setOf(
        "a", "i", "an", "am", "as", "at", "be", "by", "do", "go", "he", "if", "in", "is", "it",
        "me", "my", "no", "of", "on", "or", "so", "to", "up", "us", "we",
        "all", "and", "any", "are", "but", "can", "day", "did", "for", "get", "god", "had",
        "has", "her", "him", "his", "how", "its", "let", "man", "may", "new", "not", "now",
        "off", "old", "one", "our", "out", "own", "put", "say", "see", "she", "sos", "the", "too", "two",
        "use", "was", "way", "who", "why", "you",
        "also", "back", "been", "best", "both", "call", "come", "does", "down", "each", "even",
        "find", "form", "from", "give", "good", "have", "here", "home", "into", "just", "know",
        "last", "life", "like", "line", "long", "look", "made", "make", "many", "more", "most",
        "much", "must", "name", "need", "next", "only", "open", "over", "part", "same", "show",
        "side", "some", "such", "take", "tell", "than", "that", "them", "then", "they", "this",
        "time", "very", "want", "well", "went", "were", "what", "when", "will", "with", "word",
        "work", "year", "your", "hello",
        "about", "after", "again", "being", "could", "every", "first", "great", "house",
        "large", "other", "place", "right", "small", "still", "their", "there", "these",
        "thing", "think", "those", "three", "under", "water", "where", "which", "while",
        "world", "would", "write"
    )

    /** 소문자 QWERTY → 한국어 자모 (두벌식) */
    private val ENG_TO_JAMO: Map<Char, Char> = mapOf(
        'q' to 'ㅂ', 'w' to 'ㅈ', 'e' to 'ㄷ', 'r' to 'ㄱ', 't' to 'ㅅ',
        'y' to 'ㅛ', 'u' to 'ㅕ', 'i' to 'ㅑ', 'o' to 'ㅐ', 'p' to 'ㅔ',
        'a' to 'ㅁ', 's' to 'ㄴ', 'd' to 'ㅇ', 'f' to 'ㄹ', 'g' to 'ㅎ',
        'h' to 'ㅗ', 'j' to 'ㅓ', 'k' to 'ㅏ', 'l' to 'ㅣ',
        'z' to 'ㅋ', 'x' to 'ㅌ', 'c' to 'ㅊ', 'v' to 'ㅍ',
        'b' to 'ㅠ', 'n' to 'ㅜ', 'm' to 'ㅡ'
    )

    /** 대문자(Shift) QWERTY → 쌍자음/복합모음 (두벌식) */
    private val ENG_UPPER_TO_JAMO: Map<Char, Char> = mapOf(
        'Q' to 'ㅃ', 'W' to 'ㅉ', 'E' to 'ㄸ', 'R' to 'ㄲ', 'T' to 'ㅆ',
        'O' to 'ㅒ', 'P' to 'ㅖ'
    )

    /** 초성 19개 (표준) */
    private val CHOSUNG = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    /** 중성 21개 (표준) */
    private val JUNGSUNG = charArrayOf(
        'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ',
        'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    )

    /** 종성 28개 (인덱스 0 = 받침 없음) */
    private val JONGSUNG = charArrayOf(
        '\u0000', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ',
        'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ',
        'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    /** 복합 중성 결합: (선행 중성, 결합 모음) → 복합 중성 */
    private val JUNG_COMPOUND: Map<Pair<Char, Char>, Char> = mapOf(
        ('ㅗ' to 'ㅏ') to 'ㅘ', ('ㅗ' to 'ㅐ') to 'ㅙ', ('ㅗ' to 'ㅣ') to 'ㅚ',
        ('ㅜ' to 'ㅓ') to 'ㅝ', ('ㅜ' to 'ㅔ') to 'ㅞ', ('ㅜ' to 'ㅣ') to 'ㅟ',
        ('ㅡ' to 'ㅣ') to 'ㅢ'
    )

    /** 복합 종성 결합: (선행 종성, 결합 자음) → 복합 종성 */
    private val JONG_COMPOUND: Map<Pair<Char, Char>, Char> = mapOf(
        ('ㄱ' to 'ㅅ') to 'ㄳ', ('ㄴ' to 'ㅈ') to 'ㄵ', ('ㄴ' to 'ㅎ') to 'ㄶ',
        ('ㄹ' to 'ㄱ') to 'ㄺ', ('ㄹ' to 'ㅁ') to 'ㄻ', ('ㄹ' to 'ㅂ') to 'ㄼ',
        ('ㄹ' to 'ㅅ') to 'ㄽ', ('ㄹ' to 'ㅌ') to 'ㄾ', ('ㄹ' to 'ㅍ') to 'ㄿ',
        ('ㄹ' to 'ㅎ') to 'ㅀ', ('ㅂ' to 'ㅅ') to 'ㅄ'
    )

    /**
     * 연음 분리: 종성(단일/복합) → (남는 종성, 다음 음절로 넘어가는 초성).
     * 종성 뒤에 모음이 오면 호출된다. 복합 종성은 뒷 자음만 넘어가고 앞 자음은 종성으로 남는다.
     */
    private val JONG_LINK_SPLIT: Map<Char, Pair<Char, Char>> = buildMap {
        // 복합 종성 분리
        put('ㄳ', 'ㄱ' to 'ㅅ'); put('ㄵ', 'ㄴ' to 'ㅈ'); put('ㄶ', 'ㄴ' to 'ㅎ')
        put('ㄺ', 'ㄹ' to 'ㄱ'); put('ㄻ', 'ㄹ' to 'ㅁ'); put('ㄼ', 'ㄹ' to 'ㅂ')
        put('ㄽ', 'ㄹ' to 'ㅅ'); put('ㄾ', 'ㄹ' to 'ㅌ'); put('ㄿ', 'ㄹ' to 'ㅍ')
        put('ㅀ', 'ㄹ' to 'ㅎ'); put('ㅄ', 'ㅂ' to 'ㅅ')
        // 단일 종성: 통째로 다음 초성으로 이동 (남는 종성 없음 = '\u0000')
        for (i in 1 until JONGSUNG.size) {
            val c = JONGSUNG[i]
            if (!containsKey(c) && CHOSUNG.contains(c)) put(c, '\u0000' to c)
        }
    }

    // 역변환(한타→영문)용 자모 → QWERTY 매핑
    private val JAMO_TO_ENG: Map<Char, String> = buildMap {
        ENG_TO_JAMO.forEach { (eng, jamo) -> put(jamo, eng.toString()) }
        ENG_UPPER_TO_JAMO.forEach { (eng, jamo) -> put(jamo, eng.toString()) }
        // 복합 중성 → 두 모음의 영문 조합
        JUNG_COMPOUND.forEach { (pair, comp) ->
            put(comp, eng(pair.first) + eng(pair.second))
        }
        // 복합 종성 → 두 자음의 영문 조합
        JONG_COMPOUND.forEach { (pair, comp) ->
            put(comp, eng(pair.first) + eng(pair.second))
        }
    }

    private fun eng(jamo: Char): String =
        ENG_TO_JAMO.entries.firstOrNull { it.value == jamo }?.key?.toString()
            ?: ENG_UPPER_TO_JAMO.entries.firstOrNull { it.value == jamo }?.key?.toString()
            ?: ""

    // ---------------------------------------------------------------------
    // 공개 API
    // ---------------------------------------------------------------------

    /**
     * 한영타 신뢰도 (0.0 ~ 1.0).
     *
     * 입력 영문을 두벌식으로 변환했을 때 "완성형 한글 음절"이 얼마나 잘 조합되는지로 판정한다.
     * - 매핑 가능한 영문자 비율 (실제 영단어는 자판 밖 글자가 거의 없지만, 핵심 신호는 조합률)
     * - 조합 결과 중 완성형 음절 vs 조합 실패해 남은 낱자모 비율
     * 실제 영어 단어는 모음/자음 배열이 한글 조합 규칙과 어긋나 낱자모가 많이 남으므로 낮게 나온다.
     */
    fun detectEnglishToKorean(input: String): Float = analyze(input).confidence

    /** [analyze] 결과. 변환 1회로 신뢰도와 변환 문자열을 함께 얻는다(핫패스 이중 변환 방지). */
    data class Analysis(val confidence: Float, val converted: String)

    /**
     * 한영타 판정 + 변환을 한 번에. 선택 변경 이벤트는 드래그 중 초당 수십 회 오고 전부 메인
     * 스레드에서 처리되므로, 정규식·중간 리스트·이중 변환 없이 단일 패스로 끝낸다.
     */
    fun analyze(input: String): Analysis {
        var letters = 0
        var mappable = 0
        // 영어 상용어 억제: 두벌식에서 자음-모음-자음 배열이 되는 흔한 영단어는 한글로 "완벽히"
        // 조합돼(the→솓, and→뭉, with→쟈소 …) 조합률만으로는 100% 가 나온다. 구조 신호로는
        // 진짜 한영타와 구분되지 않으므로 어휘로 거른다 — 선택이 상용어(들)로만 이뤄졌으면
        // 실제 영어로 보고 0. 혼합 선택("dkssud the")은 억제하지 않는다.
        // 토큰 = 공백으로 나눈 조각에서 글자만 모아 소문자화한 것(구두점/숫자는 무시).
        var sawToken = false
        var allStop = true
        val tok = StringBuilder()
        fun flushToken() {
            if (tok.isEmpty()) return
            sawToken = true
            if (allStop && tok.toString() !in ENGLISH_STOPWORDS) allStop = false
            tok.setLength(0)
        }
        for (c in input) {
            if (c.isWhitespace()) {
                flushToken()
                continue
            }
            if (c.isLetter()) {
                letters++
                if (engToJamo(c) != null) mappable++
                tok.append(c.lowercaseChar())
            }
        }
        flushToken()
        if (letters == 0 || mappable == 0) return Analysis(0f, input)
        if (sawToken && allStop) return Analysis(0f, input)
        val mapRatio = mappable.toFloat() / letters

        val converted = convertEngToKor(input)
        var syllables = 0
        var looseJamo = 0
        for (ch in converted) {
            val code = ch.code
            when {
                code in HANGUL_BASE..HANGUL_LAST -> syllables++
                code in COMPAT_JAMO_START..COMPAT_JAMO_END -> looseJamo++
            }
        }
        val units = syllables + looseJamo
        if (units == 0) return Analysis(0f, converted)
        val composeRatio = syllables.toFloat() / units
        return Analysis(composeRatio * mapRatio, converted)
    }

    /** 입력에 완성형 한글 음절 또는 조합되지 못한 호환 자모가 하나라도 있으면 true. */
    fun containsHangul(input: String): Boolean = input.any {
        val code = it.code
        code in HANGUL_BASE..HANGUL_LAST || code in COMPAT_JAMO_START..COMPAT_JAMO_END
    }

    /**
     * 한글 → 영타 방향의 한영타 판정 + 변환(역방향, 2026-09 추가) — "한글 자판 상태에서 영어를
     * 친" 경우(예: `god`를 한글 자판으로 치면 `행`)를 잡는다.
     *
     * [analyze]와 반대로 강한 신호가 없다: [convertKorToEng] 는 완성형 한글 음절이면 항상 어떤
     * 알파벳으로 변환되므로(조합 성공/실패라는 구분 자체가 없음), "그 결과가 실제로 의도된 영어
     * 단어인가"는 조합만으로 알 수 없다. 그래서 최대한 보수적으로 판정한다: 변환 결과의 모든
     * 토큰(공백으로 나눈 조각, [analyze]의 상용어 토큰화와 동일한 방식)이 [ENGLISH_STOPWORDS]
     * 사전에 정확히 일치할 때만 신뢰도 1.0 을 준다. 사전에 없는 단어는 절대 감지하지 않아(미탐
     * 증가) 오탐을 원천 차단한다 — "영문을 읽던 중 칩이 튀어나오는 오탐 비용이 더 크다"는
     * 이 앱의 기존 원칙과 같은 트레이드오프.
     */
    fun analyzeReverse(input: String): Analysis {
        val converted = convertKorToEng(input)
        if (converted == input) return Analysis(0f, input) // 한글이 전혀 없었음
        var sawToken = false
        var allMatch = true
        val tok = StringBuilder()
        fun flushToken() {
            if (tok.isEmpty()) return
            sawToken = true
            if (allMatch && tok.toString() !in ENGLISH_STOPWORDS) allMatch = false
            tok.setLength(0)
        }
        for (c in converted) {
            if (c.isWhitespace()) {
                flushToken()
                continue
            }
            if (c.isLetter()) tok.append(c.lowercaseChar())
        }
        flushToken()
        if (!sawToken || !allMatch) return Analysis(0f, converted)
        return Analysis(1f, converted)
    }

    /**
     * 영타 → 한글 변환 (두벌식 조합 오토마타).
     * 공백/숫자/기호/이미 한글인 문자는 그대로 보존한다.
     */
    fun convertEngToKor(input: String): String {
        val out = StringBuilder()
        val state = SyllableState()

        for (ch in input) {
            val jamo = engToJamo(ch)
            if (jamo == null) {
                // 자모가 아닌 문자: 진행 중인 음절을 확정하고 원문자 그대로 출력
                state.flushTo(out)
                out.append(ch)
                continue
            }
            if (isVowel(jamo)) {
                feedVowel(state, jamo, out)
            } else {
                feedConsonant(state, jamo, out)
            }
        }
        state.flushTo(out)
        return out.toString()
    }

    /**
     * 한글 → 영타 변환 (역방향). 완성형 음절을 자모로 분해해 두벌식 키로 되돌린다.
     * 한글이 아닌 문자는 그대로 보존한다.
     */
    fun convertKorToEng(input: String): String {
        val out = StringBuilder()
        for (ch in input) {
            val code = ch.code
            when {
                code in HANGUL_BASE..HANGUL_LAST -> {
                    val sIndex = code - HANGUL_BASE
                    val choIdx = sIndex / (JUNG_COUNT * JONG_COUNT)
                    val jungIdx = (sIndex % (JUNG_COUNT * JONG_COUNT)) / JONG_COUNT
                    val jongIdx = sIndex % JONG_COUNT
                    out.append(JAMO_TO_ENG[CHOSUNG[choIdx]] ?: "")
                    out.append(JAMO_TO_ENG[JUNGSUNG[jungIdx]] ?: "")
                    if (jongIdx > 0) out.append(JAMO_TO_ENG[JONGSUNG[jongIdx]] ?: "")
                }
                JAMO_TO_ENG.containsKey(ch) -> out.append(JAMO_TO_ENG[ch])
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    // ---------------------------------------------------------------------
    // 오토마타 내부 구현
    // ---------------------------------------------------------------------

    /** 조합 중인 한 음절의 상태. cho/jung = -1 은 비어있음, jong = 0 은 받침 없음. */
    private class SyllableState {
        var cho = -1
        var jung = -1
        var jong = 0

        fun reset() {
            cho = -1; jung = -1; jong = 0
        }

        /** 현재 음절을 확정해 [out] 에 기록하고 상태를 비운다. */
        fun flushTo(out: StringBuilder) {
            when {
                cho >= 0 && jung >= 0 -> {
                    val code = HANGUL_BASE + (cho * JUNG_COUNT + jung) * JONG_COUNT + jong
                    out.append(code.toChar())
                }
                cho >= 0 -> out.append(CHOSUNG[cho])         // 초성만: 낱자모로 출력
                jung >= 0 -> out.append(JUNGSUNG[jung])       // 중성만: 낱자모로 출력
                jong > 0 -> out.append(JONGSUNG[jong])        // 방어적 처리(정상 흐름에선 발생 안 함)
            }
            reset()
        }
    }

    private fun feedConsonant(s: SyllableState, jamo: Char, out: StringBuilder) {
        when {
            s.cho < 0 && s.jung < 0 -> {
                // 빈 상태: 새 초성 시작
                s.cho = CHOSUNG.indexOf(jamo)
                if (s.cho < 0) out.append(jamo) // 초성이 될 수 없는 자모(방어)
            }
            s.cho < 0 -> {
                // 중성만 있던 낱모음 뒤 자음: 모음 확정 후 새 초성
                s.flushTo(out)
                s.cho = CHOSUNG.indexOf(jamo)
            }
            s.jung < 0 -> {
                // 초성만 있고 또 자음: 앞 초성을 낱자로 확정 후 새 초성
                s.flushTo(out)
                s.cho = CHOSUNG.indexOf(jamo)
            }
            s.jong == 0 -> {
                // 초성+중성, 받침 없음 → 종성으로 시도
                val ji = JONGSUNG.indexOf(jamo)
                if (ji > 0) s.jong = ji
                else { s.flushTo(out); s.cho = CHOSUNG.indexOf(jamo) } // 종성 불가(ㄸㅃㅉ 등)
            }
            else -> {
                // 초성+중성+종성 → 복합 종성 결합 시도
                val cur = JONGSUNG[s.jong]
                val combined = JONG_COMPOUND[cur to jamo]
                if (combined != null) {
                    s.jong = JONGSUNG.indexOf(combined)
                } else {
                    s.flushTo(out); s.cho = CHOSUNG.indexOf(jamo)
                }
            }
        }
    }

    private fun feedVowel(s: SyllableState, jamo: Char, out: StringBuilder) {
        when {
            s.cho < 0 && s.jung < 0 -> {
                // 빈 상태: 초성 없는 낱모음
                s.jung = JUNGSUNG.indexOf(jamo)
            }
            s.cho < 0 -> {
                // 낱모음 뒤 또 모음 → 복합 중성 시도, 안되면 분리
                val comb = JUNG_COMPOUND[JUNGSUNG[s.jung] to jamo]
                if (comb != null) s.jung = JUNGSUNG.indexOf(comb)
                else { s.flushTo(out); s.jung = JUNGSUNG.indexOf(jamo) }
            }
            s.jung < 0 -> {
                // 초성만 → 중성 채움 (정상 CV)
                s.jung = JUNGSUNG.indexOf(jamo)
            }
            s.jong == 0 -> {
                // 초성+중성, 받침 없음 → 복합 중성 시도, 안되면 새 음절(초성 없는 모음)
                val comb = JUNG_COMPOUND[JUNGSUNG[s.jung] to jamo]
                if (comb != null) {
                    s.jung = JUNGSUNG.indexOf(comb)
                } else {
                    s.flushTo(out); s.jung = JUNGSUNG.indexOf(jamo)
                }
            }
            else -> {
                // 초성+중성+종성 뒤 모음 → 연음: 종성을 다음 음절 초성으로 이동
                val split = JONG_LINK_SPLIT[JONGSUNG[s.jong]]
                if (split != null) {
                    val (remain, movedCho) = split
                    s.jong = if (remain == '\u0000') 0 else JONGSUNG.indexOf(remain)
                    s.flushTo(out)
                    s.cho = CHOSUNG.indexOf(movedCho)
                    s.jung = JUNGSUNG.indexOf(jamo)
                } else {
                    // 분리 불가(방어): 음절 확정 후 새 모음
                    s.flushTo(out)
                    s.jung = JUNGSUNG.indexOf(jamo)
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 헬퍼
    // ---------------------------------------------------------------------

    /** 영문자 1개 → 한국어 자모. 대문자는 쌍자음/복합모음 우선, 없으면 소문자 매핑. */
    private fun engToJamo(ch: Char): Char? =
        ENG_UPPER_TO_JAMO[ch] ?: ENG_TO_JAMO[ch] ?: ENG_TO_JAMO[ch.lowercaseChar()]

    private fun isVowel(jamo: Char): Boolean = JUNGSUNG.contains(jamo)
}
