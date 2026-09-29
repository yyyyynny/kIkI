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
     * 고빈도 영단어 안전망([analyze] 참조). 두벌식에서 모음키와 자음키가 번갈아 오는 영단어는
     * 한글 음절로 100% 조합돼(the→솓, and→뭉, work→재가 …) 조합률만 보던 옛 판정이 최고
     * 신뢰도를 주던 부류다.
     *
     * ⚠️ [TypoLanguageModel] 도입(2026-09) 이후로는 **주 방어가 아니다** — 검증에서 이 171개는
     * 임계값 3.0 기준 단 하나도 모델을 통과하지 못했다(즉 목록이 없어도 전부 억제된다). 사용자가
     * 설정에서 임계값을 크게 낮췄을 때를 위한 보험으로만 남겨 둔다.
     */
    private val ENGLISH_STOPWORDS_BASE: Set<String> = setOf(
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
        "work", "year", "your", "hello", "fuck",
        "about", "after", "again", "being", "could", "every", "first", "great", "house",
        "large", "other", "place", "right", "small", "still", "their", "there", "these",
        "thing", "think", "those", "three", "under", "water", "where", "which", "while",
        "world", "would", "write"
    )

    /**
     * [TypoLanguageModel] 이 통계로 잡지 못하는 잔여 예외 — 전부 **기술 약어**다. 대량 검증에서
     * 모델 임계값을 넘겨 새는 것이 이 8개뿐이었다(라틴 3글자 이상 기준). 두벌식으로 치면 그럴듯한
     * 한글이 되면서 영어 글자 배열로도 자연스러워 통계가 갈리는, 본질적으로 모호한 경우다.
     *
     * ⚠️ 2026-09 이전에는 이 자리에 대량 검증으로 손수 추출한 626개 목록
     * (`ENGLISH_STOPWORDS_FREQUENT`)이 있었다. 오탐이 나올 때마다 단어를 찾아 추가하는 방식이라
     * 목록이 끝없이 커지는 문제가 있었는데, 우도비 모델 도입으로 그 626개 중 619개가 통계만으로
     * 억제돼 목록을 지웠다 — 새 오탐이 나와도 대부분 모델이 이미 처리하므로 여기에 손으로 추가할
     * 일은 드물어야 한다(추가하기 전에 먼저 모델 점수를 확인할 것).
     */
    private val ENGLISH_TECH_ABBREVIATIONS: Set<String> = setOf(
        "apr", "dna", "dns", "eos", "ghz", "rpg", "sms", "smtp"
    )

    private val ENGLISH_STOPWORDS: Set<String> = ENGLISH_STOPWORDS_BASE + ENGLISH_TECH_ABBREVIATIONS

    /**
     * [analyzeReverse] 전용 화이트리스트(정방향의 [ENGLISH_STOPWORDS] 와 별개 — 그 이유는
     * [analyzeReverse] 문서 참조). 아주 짧고 흔하게 오타로 나올 법한 감탄사/약어만 신중하게
     * 담는다. 확장 시 반드시 실제 한국어 말뭉치로 오탐 여부를 재검증할 것 — `work`/`to`/`so`
     * 처럼 짧고 흔한 기능어를 넣으면 `재가`/`새`/`내` 같은 흔한 한글과 우연히 충돌한다.
     */
    private val REVERSE_TYPO_WORDS: Set<String> = setOf("god", "sos")

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
     * 한영타 신뢰도 (0.0 ~ 1.0). 판정은 [TypoLanguageModel] 의 우도비 — "이건 실제 영어다" 와
     * "이건 한글을 영문 자판에서 친 것이다" 두 가설을 함께 저울질한다. 자세한 근거와 성능은
     * [analyze] 및 [TypoLanguageModel] 문서 참조.
     */
    fun detectEnglishToKorean(input: String): Float = analyze(input).confidence

    /** [analyze] 결과. 변환 1회로 신뢰도와 변환 문자열을 함께 얻는다(핫패스 이중 변환 방지). */
    data class Analysis(val confidence: Float, val converted: String)

    /**
     * 한영타 판정 + 변환을 한 번에. 선택 변경 이벤트는 드래그 중 초당 수십 회 오고 전부 메인
     * 스레드에서 처리되므로, 정규식·중간 리스트·이중 변환 없이 단일 패스로 끝낸다.
     *
     * 선택 전체를 한 덩어리로 보지 않고 **공백/한글 경계로 토큰화해 토큰마다 따로 판정한 뒤
     * 최댓값**을 쓴다. 한 선택 안에 여러 후보가 섞일 수 있기 때문이다 — 진짜 한영타 옆에 다른
     * 라틴 조각(두문자어 "SF"/"TV", `dvd`/`ost` 같은 실제 영단어, URL 등)이 있으면, 합쳐서 보던
     * 시절엔 그 조각들 때문에 신호가 희석돼 감지를 놓쳤다(실제 문장 뒤에 `dkssud` 를 붙인 500건
     * 중 31건 미탐 → 토큰별 판정으로 100% 감지, 2026-09).
     *
     * 토큰 하나의 판정은 [TypoLanguageModel.judge] 가 담당한다 — CapsLock 꺼짐/켜짐 두 한영타
     * 가설을 영어 단어·한국어 글 속 라틴 문자열(Shift 증인 사전)·약어와 맞붙인다(2026-09 재설계).
     * 여기서는 판정 대상만 거른다:
     * - 라틴 3글자 미만([TypoLanguageModel.MIN_LATIN_LENGTH]) 토큰은 판정하지 않는다.
     * - [ENGLISH_STOPWORDS] 정확 일치는 마지막 안전망(모델이 놓치는 소수 예외 전용).
     * - [exceptions](사용자가 설정에서 등록한 예외 단어, 소문자)는 판정도 교체도 하지 않는다.
     *
     * [koreanContext] = 선택 주변(또는 선택 안)에 한글이 있다 — 한국어 문서 속 선택이면 같은 라틴
     * 조각도 영어 단어보다 한영타일 가능성이 높다([TypoLanguageModel.judge] 참조).
     *
     * ⚠️ 이미 완성형 한글/호환 자모인 문자는 토큰화에서 경계로 취급해 판정에서 제외한다 —
     * 포함시키면 긴 정상 한글 문장에 짧은 한영타 조각이 섞였을 때 신호가 묻힌다.
     * [Analysis.converted] 는 토큰마다 따로 정한다 — 영어 단어(`cpu`)는 그대로 두고 한영타만
     * 바꾼다([convertInTypoContext]). 한글·공백은 원문 그대로 보존된다.
     */
    fun analyze(input: String, koreanContext: Boolean = false, exceptions: Set<String> = emptySet()): Analysis {
        // 라틴 토큰(공백/한글로 구분되는 조각) 하나 = 원문 텍스트([text], 구두점/숫자 포함 —
        // convertEngToKor 입력용) + 글자만 모아 소문자화한 것([letters], 스톱워드 비교·매핑
        // 비율 계산용) + 매핑 가능 글자 수. [text] 와 [letters] 를 분리해 두는 이유: "1cm" 처럼
        // 숫자가 붙으면 원문 그대로는 사전(cm)과 일치하지 않아 억제가 무력화된다(2026-09 발견
        // — "1cm" 오탐).
        val tokens = mutableListOf<LatinToken>()
        val tok = StringBuilder()
        val tokLetters = StringBuilder()
        var tokStart = 0
        var tokMappable = 0
        var tokUpper = 0
        var tokLatin = 0
        var tokLatinUpper = 0
        var anyLetter = false
        fun flushToken() {
            if (tok.isEmpty()) return
            val letters = tokLetters.toString()
            val allUpper = letters.isNotEmpty() && tokUpper == letters.length
            tokens.add(LatinToken(tokStart, tok.toString(), letters, tokMappable, allUpper, tokLatinUpper * 2 > tokLatin))
            tok.setLength(0)
            tokLetters.setLength(0)
            tokMappable = 0
            tokUpper = 0
            tokLatin = 0
            tokLatinUpper = 0
        }
        for ((i, c) in input.withIndex()) {
            val code = c.code
            val isHangul = code in HANGUL_BASE..HANGUL_LAST || code in COMPAT_JAMO_START..COMPAT_JAMO_END
            if (isHangul || c.isWhitespace()) {
                flushToken()
                continue
            }
            if (tok.isEmpty()) tokStart = i
            if (c.isLetter()) {
                anyLetter = true
                if (c.isUpperCase()) tokUpper++
                if (isLatin(c)) {
                    tokLatin++
                    if (c in 'A'..'Z') tokLatinUpper++
                }
                tokLetters.append(c.lowercaseChar())
                if (engToJamo(c) != null) tokMappable++
            }
            tok.append(c)
        }
        flushToken()
        if (!anyLetter) return Analysis(0f, input)

        // 토큰마다 [TypoLanguageModel.judge] 로 판정하고 그 중 최댓값을 선택 전체의 신뢰도로
        // 쓴다 — 혼합 선택("dkssud the")에서 "the" 는 낮은 점수로 자연히 탈락하고 "dkssud" 만
        // 후보로 남는다. 스톱워드 정확 일치는 이제 안전망일 뿐이다(아래 [ENGLISH_STOPWORDS] 주석).
        var best = 0f
        var bestCaps = false
        for (t in tokens) {
            val letters = t.letters.length
            if (letters < TypoLanguageModel.MIN_LATIN_LENGTH || t.mappable == 0) continue
            if (t.letters in ENGLISH_STOPWORDS || t.letters in exceptions) continue
            val j = TypoLanguageModel.judge(t.text, t.letters, t.mappable, t.allUpper, koreanContext)
            if (j.confidence > best) {
                best = j.confidence
                bestCaps = j.capsLock
            }
        }
        if (best == 0f) return Analysis(0f, input)

        // 교체 문자열: 토큰마다 따로 정한다. 선택 전체를 통째로 변환하면 진짜 한영타 옆의 영어
        // 단어까지 바뀐다(`cpu wjdakf` → `체ㅕ 정말`). 공백·한글은 원문 그대로 둔다.
        // CapsLock 으로 판정됐으면 대문자가 과반인 토큰은 대소문자를 뒤집어 변환한다(`GKArP`→함께 —
        // 예전엔 CapsLock 을 몰라 "GKA계"로 망가뜨렸다). 소문자가 과반인 토큰은 도중에 CapsLock 을
        // 끈 것이라 그대로 둔다.
        val out = StringBuilder(input.length)
        var last = 0
        for (t in tokens) {
            out.append(input, last, t.start)
            if (t.letters in exceptions) out.append(t.text)
            else out.append(convertInTypoContext(t, capsLock = bestCaps && t.upperMajority))
            last = t.start + t.text.length
        }
        out.append(input, last, input.length)
        return Analysis(best, out.toString())
    }

    /** [analyze] 의 라틴 토큰(공백/한글로 구분되는 조각). */
    private class LatinToken(
        /** 입력 안에서의 시작 위치. */
        val start: Int,
        /** 원문 텍스트(구두점/숫자 포함 — convertEngToKor 입력용). */
        val text: String,
        /** 글자만 모아 소문자화한 것(스톱워드 비교·모델 입력용). */
        val letters: String,
        val mappable: Int,
        val allUpper: Boolean,
        /** 라틴 글자 중 대문자가 과반 — CapsLock 으로 판정된 선택에서 대소문자를 뒤집어 변환할 대상. */
        val upperMajority: Boolean,
    )

    /**
     * 이미 한영타로 판정된 선택 안에서 토큰 하나를 어떻게 교체할지 정한다. 선택 전체를 통째로
     * 변환하던 시절엔 진짜 한영타 옆의 영어 단어까지 바뀌었다(`cpu wjdakf` → `체ㅕ 정말`, 사용자
     * 제보 2026-09). 이제 라틴 글자 구간마다 세 가설 중 우도가 가장 큰 것을 고른다:
     * 1. 영어 그대로(`cpu`) — 영어 단어/약어 모델([TypoLanguageModel.englishOrAcronymLogProb])
     * 2. 전부 한글(`dkssud`→`안녕`, `zzz`→`ㅋㅋㅋ`) — 구어체 한국어 모델 + 문맥 사전확률
     * 3. 영어 + 한글(`cpusms`→`cpu는`) — 앞은 영어, 뒤는 "영어 바로 뒤 첫 단위" 실측 분포(조사 위주)
     *
     * 문맥 사전확률([CONTEXT_TOKEN_PRIOR] + [CONTEXT_PRIOR_PER_LETTER])은 "같은 선택에 이미 확실한
     * 한영타가 있으니 나머지도 한글일 가능성이 높다"는 것이다. 단, 실제 구어체에서 거의 안 나오는
     * 전이가 필요한 변환(`체ㅕ`: 음절 뒤 낱모음 ㅕ, -12.7)은 이 사전확률을 받지 못한다
     * ([MALFORMED_TRANSITION_LOG]) — 사용자 표현 그대로 "한국어로도 매우 이상한" 결과라 문맥이
     * 구제할 수 없다.
     *
     * 파라미터는 NSMC test(학습에 안 쓴 5만 문장)에서 세 집합으로 정했다: (A) 순수 한영타 문장
     * 4.5만 — 전부 한글로 복원돼야 함, (B) 영어가 원래 섞인 실제 리뷰 2.3천 — 영어는 지켜야 함,
     * (C) 한국어 문장에 소문자 기술 용어(cpu/gpu/ssd…)를 끼운 6천 — 절반은 조사 붙임.
     * | 복원 정확도 | 통째 변환(이전) | 현재 |
     * |---|---|---|
     * | A 순수 한영타 | 97.95% | 97.31% |
     * | B 실제 혼합 | 0% | 75.05% |
     * | C 기술 용어 | 0% | 84.86% |
     * A 의 실패 약 2%는 IME 자체의 모호성(`닼ㅋ`/`다ㅋㅋ`)이라 이전 방식도 똑같이 틀리고, 새로 생긴
     * 0.6%p 는 한 글자 조각(`o`/`w`)·이모티콘(`-t-`)·드문 속어(`rid`→`걍`) 정도다.
     * C 에 남은 실패(`xml`→`틔`, `gif`→`햘`, `fps`→`렌`)는 변환 결과가 멀쩡한 음절이라 본질적으로
     * 모호하다.
     */
    private fun convertInTypoContext(t: LatinToken, capsLock: Boolean): String {
        if (t.mappable == 0) return t.text // 숫자·기호뿐
        // 라틴 글자 연속 구간마다 따로 판단한다 — 구두점·숫자로 붙은 조각(`whgdkdy...OSTeh`,
        // `10wjawnazz`)은 서로 다른 단어다. convertEngToKor 도 그 경계에서 조합을 끊으므로 동치다.
        val text = t.text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (!isLatin(text[i])) {
                out.append(text[i])
                i++
                continue
            }
            var j = i
            while (j < text.length && isLatin(text[j])) j++
            out.append(convertRunInTypoContext(text.substring(i, j), capsLock))
            i = j
        }
        return out.toString()
    }

    private fun isLatin(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    /**
     * [convertInTypoContext] 의 라틴 글자 구간 하나(전부 a-z/A-Z). [capsLock] 이면 CapsLock 을 켠 채
     * 친 것으로 보고 대소문자를 뒤집은 글자열([typed])로 한글 가설을 세운다 — 영어 가설은 원문 그대로.
     */
    private fun convertRunInTypoContext(run: String, capsLock: Boolean): String {
        // 전부 대문자는 약어(OST/EBS) — 단, Shift 가 한글에서 의미 있는 키로만 이뤄졌으면(`WW`→ㅉㅉ)
        // 대문자가 영어의 증거가 못 되므로 모델에 맡긴다. CapsLock 중엔 대문자가 기본 상태라 이 규칙을
        // 쓰지 않고 우도 비교에 맡긴다(`GUI` 처럼 한글로 깨지는 약어는 영어 쪽이 자연히 이긴다).
        if (!capsLock && run.length >= 2 && run.all { it.isUpperCase() } && !run.all { it in ENG_UPPER_TO_JAMO }) return run
        val typed = if (capsLock) TypoLanguageModel.swapCase(run) else run

        var bestText = run
        var bestLl = englishLl(run) + logLatinNotGlued(run.length)
        val whole = convertEngToKor(typed)
        TypoLanguageModel.koreanInformal(whole)?.let { ko ->
            // 실제 구어체에서 거의 안 나오는 전이가 필요한 변환(`체ㅕ`)은 문맥 가산점을 못 받는다.
            val prior = if (ko.rarestTransition >= MALFORMED_TRANSITION_LOG) CONTEXT_TOKEN_PRIOR else 0.0
            val ll = koreanLl(typed, ko.logProb) + prior
            if (ll >= bestLl) { bestLl = ll; bestText = whole }
        }

        // 영어 접두 + 한글 접미(`cpusms`→`cpu는`, `Brmq`→`B급`). 접미엔 음절이 하나는 있어야 한다.
        for (k in 1 until run.length) {
            val suffix = typed.substring(k)
            val suffixKo = convertEngToKor(suffix)
            if (suffixKo.none { it.code in HANGUL_BASE..HANGUL_LAST }) continue
            val ko = TypoLanguageModel.koreanInformalLogProb(suffixKo, afterLatin = true) ?: continue
            val ll = englishLl(run.substring(0, k)) + logLatinGlued(k) + koreanLl(suffix, ko)
            if (ll > bestLl) {
                bestLl = ll
                bestText = run.substring(0, k) + suffixKo
            }
        }
        return bestText
    }


    /**
     * 길이 n 인 라틴 연속 구간이 공백 없이 한글로 이어질 확률(NSMC train 실측, 13,342구간).
     * 짧은 약어는 절반이 조사와 붙지만(`B급`/`cg가`/`ost도`) 4글자 이상 단어는 10% 안팎이다.
     */
    private val LATIN_GLUED_RATE = doubleArrayOf(0.0, 0.585, 0.560, 0.471, 0.106, 0.100, 0.077, 0.103)

    private fun logLatinGlued(n: Int) = Math.log(LATIN_GLUED_RATE[n.coerceIn(1, 7)])
    private fun logLatinNotGlued(n: Int) = Math.log(1 - LATIN_GLUED_RATE[n.coerceIn(1, 7)])

    /**
     * [run] 을 "의도한 영어"로 볼 때의 로그우도 — 단어+약어 모델에, 섞인 대소문자(`gpuRk`)는
     * 영어에서 드문 형태라 벌점을 더한다. 전부 대문자(`OST`)나 첫 글자만 대문자는 정상.
     */
    private fun englishLl(run: String): Double {
        var ll = TypoLanguageModel.englishOrAcronymLogProb(run.lowercase())
        val uppers = run.count { it.isUpperCase() }
        val innerUppers = uppers - (if (run[0].isUpperCase()) 1 else 0)
        if (uppers < run.length && innerUppers > 0) ll += EN_MIXED_CASE_LOG * innerUppers
        return ll
    }

    /**
     * [run] 을 "한글 자판으로 친 것"으로 볼 때의 로그우도(변환 결과의 구어체 로그확률 [koLogProb]) +
     * 문맥 가산점. 두벌식에서 Shift 는 Q/W/E/R/T/O/P 에만 의미가 있으므로, 그 외 키의 대문자
     * (`B급` 의 B)는 한글로 치던 사람이 굳이 누를 이유가 없는 흔적이라 벌점이다.
     */
    private fun koreanLl(run: String, koLogProb: Double): Double {
        var ll = koLogProb
        for (c in run) if (c.isUpperCase() && c !in ENG_UPPER_TO_JAMO) ll += KO_NEEDLESS_SHIFT_LOG
        return ll + CONTEXT_PRIOR_PER_LETTER * run.length
    }

    /** 문맥 사전확률: 조각 전체가 한글이라는 가설에 주는 로그오즈(약 2만:1). */
    private const val CONTEXT_TOKEN_PRIOR = 10.0

    /** 문맥 사전확률: 한글로 읽는 글자마다 더하는 몫(긴 조각일수록 한글 쪽 근거가 쌓인다). */
    private const val CONTEXT_PRIOR_PER_LETTER = 1.0

    /**
     * 이보다 드문 전이(실측 약 3천 단위에 1번 미만)가 하나라도 필요하면 "기형 변환"으로 보고
     * [CONTEXT_TOKEN_PRIOR] 를 주지 않는다 — `체ㅕ`(-12.7), `ㅕ기`(url, -11.9), `메ㅔ`(app, -12.3).
     * `ㅋㅋ`/`ㅠㅠ`/`ㅈㄴ`/`ㅁㅊ` 같은 흔한 구어체 전이는 모두 이보다 훨씬 흔하다.
     */
    private const val MALFORMED_TRANSITION_LOG = -8.0

    /** 영어 단어 안쪽 대문자 1개의 로그 벌점 — 실측상 영어 글 토큰의 0.26%만 이런 형태다. */
    private val EN_MIXED_CASE_LOG = Math.log(0.0026)

    /** 한글로 치던 중 Shift 가 의미 없는 키에 대문자가 나올 로그확률(오입력 수준으로 드묾). */
    private val KO_NEEDLESS_SHIFT_LOG = Math.log(0.001)

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
     * 토큰(공백으로 나눈 조각, [analyze]의 상용어 토큰화와 동일한 방식)이 [REVERSE_TYPO_WORDS]
     * 사전에 정확히 일치할 때만 신뢰도 1.0 을 준다. 사전에 없는 단어는 절대 감지하지 않아(미탐
     * 증가) 오탐을 원천 차단한다 — "영문을 읽던 중 칩이 튀어나오는 오탐 비용이 더 크다"는
     * 이 앱의 기존 원칙과 같은 트레이드오프.
     *
     * ⚠️ 정방향의 `ENGLISH_STOPWORDS`(짧고 흔한 기능어까지 포함해 일부러 넓게 잡음)를 그대로
     * 재사용했다가 실제 한국어 말뭉치(네이버 영화리뷰 20만 문장) 대량 검증에서 `재가`→`work`,
     * `쟤가`→`wOrk` 같은 흔한 표현이 오탐되는 걸 발견했다(2026-09). 두 방향은 위험 프로파일이
     * 반대다 — 정방향은 사전이 넓을수록 안전(진짜 한영타를 더 많이 억제해서 놓치는 리스크만
     * 있음)하지만, 역방향은 사전이 넓을수록 위험(사전 단어와 우연히 일치하는 흔한 한글이
     * 늘어남)하다. 그래서 역방향은 실제로 흔히 오타로 나오는 아주 짧은 단어만 담은 별도의
     * 좁은 화이트리스트를 쓴다(확장 시 반드시 대량 말뭉치로 재검증할 것).
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
            if (allMatch && tok.toString() !in REVERSE_TYPO_WORDS) allMatch = false
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
