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
     *
     * ⚠️ 이미 완성형 한글/호환 자모인 문자는 신뢰도 계산(매핑 비율·조합 성공률·스톱워드 토큰화
     * 전부)에서 제외한다(대량 검증으로 발견, 2026-09). 포함시켰을 때 세 가지 문제가 있었다:
     * ① 긴 정상 한글 문장에 짧은 한영타 조각이 섞이면 전체 글자 수 대비 매핑 비율(`mapRatio`)이
     * 희석돼 신뢰도가 임계값 밑으로 떨어져 감지를 놓친다("저 dkssud" 처럼 아주 짧을 때만 우연히
     * 통과) ② 라틴 부분이 순수 상용어("work")뿐이어도 섞인 한글 토큰 때문에 "선택 전체가
     * 상용어만은 아님" 판정이 돼 스톱워드 억제가 무력화된다 ③ (mapRatio 만 좁혔을 때 새로 발견)
     * 긴 문장 속 진짜 영어 두문자어("SF", "OO", "CG")가 조합 실패(낱자모)해도, 이미 있던 한글
     * 음절이 압도적으로 많아 조합 성공률(`composeRatio`)이 희석 없이 여전히 높게 나와 실제
     * 오탐이 발생했다(네이버 영화리뷰 20만 문장 대량 검증). 그래서 한글이 아닌 문자만 이어붙인
     * [latinOnly] 를 따로 만들어 매핑 비율·조합 성공률·스톱워드 토큰화를 **전부** 그 문자열
     * 기준으로 계산한다 — 최종 [Analysis.converted] 만 원본 전체([convertEngToKor] 가 이미
     * 한글을 보존)를 쓴다. ④ (①~③ 수정 뒤 대량 검증에서 새로 발견, 2026-09) 위 ③처럼 전체
     * 선택을 하나로 합쳐 판정하면, 진짜 한영타 옆에 조합 실패하는 **다른** 라틴 조각(다른 두문자어
     * "SF"/"TV"/"CG"/"OO", `dvd`, `ost`, `well made movie` 같은 여러 개의 진짜 영단어, URL 등)가
     * 함께 있을 때 그 조각들의 조합 실패가 진짜 한영타 신호를 희석시켜 신뢰도가 임계값 밑으로
     * 떨어진다(실제 문장 뒤에 `dkssud` 를 붙인 500건 검증 중 31건 미탐, 원인 규명). 이번엔 반대로
     * 한 선택 안에 "여러 후보가 섞여 있을 수 있다"는 게 문제이므로, 라틴 조각을 공백/한글 경계로
     * **토큰화**해 토큰마다 mapRatio·composeRatio·스톱워드 여부를 **개별** 계산하고 그 중
     * 최댓값을 선택 전체의 신뢰도로 쓴다 — 진짜 한영타 토큰 하나만 있어도 다른 토큰들의 조합
     * 실패에 묻히지 않는다(스톱워드 억제도 토큰 단위라 "선택 전체가 상용어로만 이뤄지면 0" 규칙이
     * 자동으로 성립해 옛 `allStop` 전역 플래그가 불필요해졌다).
     */
    fun analyze(input: String): Analysis {
        // 라틴 토큰(공백/한글로 구분되는 조각) 하나 = 원문 텍스트([text], 구두점/숫자 포함 —
        // convertEngToKor 입력용) + 글자만 모아 소문자화한 것([letters], 스톱워드 비교·매핑
        // 비율 계산용) + 매핑 가능 글자 수. [text] 와 [letters] 를 분리해 두는 이유: "1cm" 처럼
        // 숫자가 붙으면 원문 그대로는 사전(cm)과 일치하지 않아 억제가 무력화된다(2026-09 발견
        // — "1cm" 오탐).
        data class LatinToken(
            val text: String,
            val letters: String,
            val mappable: Int,
            val allUpper: Boolean,
        )

        val tokens = mutableListOf<LatinToken>()
        val tok = StringBuilder()
        val tokLetters = StringBuilder()
        var tokMappable = 0
        var tokUpper = 0
        var anyLetter = false
        fun flushToken() {
            if (tok.isEmpty()) return
            val letters = tokLetters.toString()
            tokens.add(LatinToken(tok.toString(), letters, tokMappable, letters.isNotEmpty() && tokUpper == letters.length))
            tok.setLength(0)
            tokLetters.setLength(0)
            tokMappable = 0
            tokUpper = 0
        }
        for (c in input) {
            val code = c.code
            val isHangul = code in HANGUL_BASE..HANGUL_LAST || code in COMPAT_JAMO_START..COMPAT_JAMO_END
            if (isHangul || c.isWhitespace()) {
                flushToken()
                continue
            }
            if (c.isLetter()) {
                anyLetter = true
                tokLetters.append(c.lowercaseChar())
                if (c.isUpperCase()) tokUpper++
                if (engToJamo(c) != null) tokMappable++
            }
            tok.append(c)
        }
        flushToken()
        if (!anyLetter) return Analysis(0f, input)

        // 토큰마다 [TypoLanguageModel] 우도비로 판정하고 그 중 최댓값을 선택 전체의 신뢰도로
        // 쓴다 — 혼합 선택("dkssud the")에서 "the" 는 낮은 점수로 자연히 탈락하고 "dkssud" 만
        // 후보로 남는다. 스톱워드 정확 일치는 이제 안전망일 뿐이다(아래 [ENGLISH_STOPWORDS] 주석).
        var best = 0f
        for (t in tokens) {
            val letters = t.letters.length
            if (letters < TypoLanguageModel.MIN_LATIN_LENGTH || t.mappable == 0) continue
            if (t.letters in ENGLISH_STOPWORDS) continue
            // 전부 대문자인 토큰은 영어 약어(SNS/DLC/EJSM …)로 본다 — 한국어 문장 5천 개 검증에서
            // 남은 오탐이 전부 이 형태였다. 두벌식에서 Shift 는 쌍자음/복합모음 7개에만 쓰여
            // 진짜 한영타가 통째로 대문자가 되는 일은 사실상 없다(CapsLock 입력은 나머지 글자가
            // 아예 매핑되지 않아 어차피 조합에 실패한다).
            if (t.allUpper) continue
            val convertedTok = convertEngToKor(t.text)
            val score = TypoLanguageModel.score(t.letters, convertedTok) ?: continue
            // 매핑 불가 글자가 섞였으면(자판에 없는 문자) 그만큼 확신을 낮춘다.
            val mapRatio = t.mappable.toFloat() / letters
            val conf = TypoLanguageModel.confidence(score) * mapRatio
            if (conf > best) best = conf
        }
        if (best == 0f) return Analysis(0f, input)
        val converted = convertEngToKor(input) // 최종 반환값(교체용) — 한글은 그대로 보존됨
        return Analysis(best, converted)
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
