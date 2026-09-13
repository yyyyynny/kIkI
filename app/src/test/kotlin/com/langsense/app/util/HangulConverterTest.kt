package com.langsense.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HangulConverter 두벌식 오토마타 검증.
 * 순수 JVM 테스트(안드로이드 의존성 없음) — `./gradlew testDebugUnitTest` 로 실행.
 */
class HangulConverterTest {

    @Test
    fun engToKor_basic() {
        assertEquals("나무위키", HangulConverter.convertEngToKor("skandnlzl"))
        assertEquals("안드로이드", HangulConverter.convertEngToKor("dksemfhdlem"))
        assertEquals("안녕", HangulConverter.convertEngToKor("dkssud"))
        assertEquals("한글", HangulConverter.convertEngToKor("gksrmf"))
        assertEquals("이렇게", HangulConverter.convertEngToKor("dlfjgrp"))
    }

    @Test
    fun engToKor_preservesNonLetters() {
        assertEquals("강 장치", HangulConverter.convertEngToKor("rkd wkdcl"))
        assertEquals("ㅇ마123", HangulConverter.convertEngToKor("dak123"))
    }

    @Test
    fun engToKor_linking_dokkaebibul() {
        // 종성 뒤 모음 → 연음(도깨비불)
        assertEquals("앉아", HangulConverter.convertEngToKor("dkswdk")) // ㄵ → ㄴ + ㅈ 이동
        assertEquals("일거", HangulConverter.convertEngToKor("dlfrj"))  // ㄺ → ㄹ 남고 ㄱ 이동
        assertEquals("읽어", HangulConverter.convertEngToKor("dlfrdj")) // 명시적 ㅇ
    }

    @Test
    fun korToEng_reverse() {
        assertEquals("skandnlzl", HangulConverter.convertKorToEng("나무위키"))
        assertEquals("dksemfhdlem", HangulConverter.convertKorToEng("안드로이드"))
        assertEquals("dkssud", HangulConverter.convertKorToEng("안녕"))
        assertEquals("gksrmf", HangulConverter.convertKorToEng("한글"))
    }

    @Test
    fun korToEng_compoundJongAndJung() {
        assertEquals("ekfr", HangulConverter.convertKorToEng("닭"))  // 복합 종성 ㄺ
        assertEquals("rkqt", HangulConverter.convertKorToEng("값"))  // 복합 종성 ㅄ
        assertEquals("dml", HangulConverter.convertKorToEng("의"))   // 복합 중성 ㅢ
        assertEquals("dho", HangulConverter.convertKorToEng("왜"))   // 복합 중성 ㅙ
    }

    @Test
    fun roundTrip_engKorEng() {
        for (s in listOf("skandnlzl", "dksemfhdlem", "dkssud", "gksrmf", "dlfjgrp")) {
            assertEquals(s, HangulConverter.convertKorToEng(HangulConverter.convertEngToKor(s)))
        }
    }

    @Test
    fun detect_hangulTyped_isHighConfidence() {
        assertTrue(HangulConverter.detectEnglishToKorean("skandnlzl") >= 0.70f)
        assertTrue(HangulConverter.detectEnglishToKorean("dksemfhdlem") >= 0.70f)
        assertTrue(HangulConverter.detectEnglishToKorean("dkssud") >= 0.70f)
        assertTrue(HangulConverter.detectEnglishToKorean("gksrmf") >= 0.70f)
        assertTrue(HangulConverter.detectEnglishToKorean("dlfjgrp") >= 0.70f)
    }

    @Test
    fun detect_englishWords_isLowConfidence() {
        // 자모로 매핑되더라도 조합 실패(낱자모)가 많아 신뢰도가 낮다.
        assertTrue(HangulConverter.detectEnglishToKorean("hello") < 0.70f)
        assertTrue(HangulConverter.detectEnglishToKorean("computer") < 0.70f)
        assertTrue(HangulConverter.detectEnglishToKorean("language") < 0.70f)
        assertTrue(HangulConverter.detectEnglishToKorean("keyboard") < 0.70f)
        assertTrue(HangulConverter.detectEnglishToKorean("function") < 0.70f)
    }

    @Test
    fun detect_empty_isZero() {
        assertEquals(0f, HangulConverter.detectEnglishToKorean(""), 0.0001f)
        assertEquals(0f, HangulConverter.detectEnglishToKorean("123 !@#"), 0.0001f)
    }

    /**
     * 자음-모음-자음 배열이 되는 흔한 영단어는 한글 음절로 100% 조합돼(the→솓, and→뭉) 조합률만
     * 보면 최고 신뢰도가 나온다 — 억제되는지 확인(단어를 더블탭했을 때 "교체?" 칩이 뜨던 오탐).
     * [TypoLanguageModel] 도입(2026-09) 후로는 확률 모델이라 값이 정확히 0 이 아닐 수 있어
     * "임계값 미만"으로 검사한다(칩은 임계값 이상일 때만 뜨므로 사용자 체감은 동일).
     */
    @Test
    fun detect_commonEnglishWords_suppressed() {
        val words = listOf(
            "the", "and", "for", "with", "when", "then", "they", "them", "than",
            "work", "down", "such", "also", "their", "who", "did", "she", "form",
            "go", "do", "to", "so", "an", "am", "hello", "sp",
            // 스톱워드 목록에 없지만 통계 모델이 억제하는 실제 영단어들(대량 검증으로 확인)
            "abacus", "aback", "abash", "absorb", "city", "video", "check", "dog", "wow"
        )
        for (w in words) {
            assertTrue(w, HangulConverter.detectEnglishToKorean(w) < 0.70f)
        }
        assertTrue("The.", HangulConverter.detectEnglishToKorean("The.") < 0.70f)
        assertTrue("WHEN", HangulConverter.detectEnglishToKorean("WHEN") < 0.70f)
        assertTrue("the and for", HangulConverter.detectEnglishToKorean("the and for") < 0.70f)
        // 전체 대문자 약어(한국어 문장에 흔히 섞임) — 한국어 문장 5천 개 검증에서 남던 오탐
        assertTrue("SNS", HangulConverter.detectEnglishToKorean("SNS") < 0.70f)
        assertTrue("DLC", HangulConverter.detectEnglishToKorean("DLC") < 0.70f)
        assertTrue(
            "SNS in sentence",
            HangulConverter.detectEnglishToKorean("중국은 SNS를 이용하여 확산하고 있다") < 0.70f
        )
    }

    /**
     * 억제 장치가 진짜 한영타까지 삼키지 않는지.
     * ⚠️ 한 음절짜리 한영타(`dho`=왜, `sp`=네 …)는 자동 제안이 안 뜰 수 있다 — 라틴 2글자 이하는
     * 아예 판정하지 않고([TypoLanguageModel.MIN_LATIN_LENGTH]), 3글자 1음절도 정보가 적어 점수가
     * 임계값에 못 미치는 경우가 있다. 영문을 읽던 중 칩이 튀어나오는 오탐의 체감 비용이 더 크다는
     * 이 앱의 기존 원칙에 따라 감수한 트레이드오프(CLAUDE.md Feature 4 참조).
     */
    @Test
    fun detect_hangulTyped_notSuppressed() {
        for (s in listOf("dkssud", "rkawk", "dlfjgrp", "ehs", "dlfjs", "gksrmf", "zjvl", "apdlf", "tkfkd")) {
            assertTrue(s, HangulConverter.detectEnglishToKorean(s) >= 0.70f)
        }
        // 상용어가 섞여 있어도 진짜 한영타 토큰이 있으면 그쪽이 채택된다.
        assertTrue(HangulConverter.detectEnglishToKorean("dkssud the") >= 0.70f)
    }

    /**
     * 대량 fuzz 검증(네이버 영화리뷰 문장 500개 + "dkssud")에서 발견한 회귀 고정(2026-09).
     * 전체 선택을 하나로 합쳐 판정하면, 진짜 한영타 옆의 다른 라틴 조각(다른 두문자어·영단어·
     * URL 등)의 조합 실패가 신호를 희석시켜 임계값 밑으로 떨어졌다(500건 중 31건 미탐). 토큰별
     * 개별 판정 + 최댓값 채택으로 수정 — 아래는 그 실패 샘플 중 일부를 고정한 것.
     */
    @Test
    fun analyze_typoNextToOtherLatinTokens_stillDetected() {
        val threshold = 0.70f
        val cases = listOf(
            "유치한 SF액션물. 셀마 블레어만 기억에 남음 dkssud",
            "우리나라 TV드라마가 3배는 낫다.. dkssud",
            "dvd방에서 보다가 졸려서 나옴 dkssud",
            "THE BEST EVER dkssud",
            "well made movie. dkssud",
        )
        for (c in cases) {
            val result = HangulConverter.analyze(c)
            assertTrue("$c -> conf=${result.confidence}", result.confidence >= threshold)
            assertTrue(c, result.converted.endsWith("안녕"))
        }
    }

    /**
     * 위 수정의 부작용으로 새로 발견한 오탐 고정(2026-09): 숫자가 바로 붙은 짧은 단위 약어
     * ("1cm")는 스톱워드 비교용 토큰 텍스트가 "1cm"(사전엔 "cm"만 있음)가 되어 억제가 무력화됐다.
     * 토큰의 글자만 모은 별도 버퍼로 스톱워드를 비교하도록 수정.
     */
    @Test
    fun analyze_unitAbbreviationWithDigit_notFalsePositive() {
        assertEquals(0f, HangulConverter.detectEnglishToKorean("1cm"), 0.0001f)
        assertEquals(0f, HangulConverter.detectEnglishToKorean("급소를 1cm만 비껴가도 산다."), 0.0001f)
    }

    /**
     * 통계 모델([TypoLanguageModel]) 회귀 고정(2026-09) — 예전에는 오탐이 나올 때마다 그 단어를
     * 스톱워드 목록에 손으로 추가해야 했다(한때 626개까지 늘어남). 우도비 모델 도입으로 목록 없이
     * 억제되는지 확인한다. 아래 단어들은 어느 스톱워드 목록에도 없다.
     */
    @Test
    fun analyze_statisticalModel_suppressesUnlistedEnglishWords() {
        for (w in listOf("abacus", "aback", "abash", "absorb", "acidify", "absurdly")) {
            assertTrue(w, HangulConverter.detectEnglishToKorean(w) < 0.70f)
        }
        // 억제를 강화해도 진짜 한영타는 그대로 잡혀야 한다.
        for (s in listOf("dkssud", "rkawk", "dlfjgrp")) {
            assertTrue(s, HangulConverter.detectEnglishToKorean(s) >= 0.70f)
        }
    }

    // ---------------------------------------------------------------------
    // 역방향(한글 자판으로 잘못 친 영어) — analyzeReverse
    // ---------------------------------------------------------------------

    @Test
    fun containsHangul_detectsSyllablesAndLooseJamo() {
        assertTrue(HangulConverter.containsHangul("행"))
        assertTrue(HangulConverter.containsHangul("hello 행"))
        assertTrue(HangulConverter.containsHangul("ㅇ")) // 조합 안 된 낱자모(호환 자모)
        assertTrue(HangulConverter.containsHangul(HangulConverter.convertEngToKor("dak"))) // "ㅇ마"
        assertEquals(false, HangulConverter.containsHangul("hello"))
        assertEquals(false, HangulConverter.containsHangul("123!@#"))
    }

    /** god/sos 를 한글 자판으로 치면 각각 행/낸 이 나온다 — 두벌식 매핑: g=ㅎ o=ㅐ d=ㅇ, s=ㄴ o=ㅐ s=ㄴ. */
    @Test
    fun analyzeReverse_commonEnglishStopword_highConfidence() {
        assertEquals("god", HangulConverter.convertKorToEng("행"))
        assertEquals("sos", HangulConverter.convertKorToEng("낸"))
        assertEquals(1f, HangulConverter.analyzeReverse("행").confidence, 0.0001f)
        assertEquals("god", HangulConverter.analyzeReverse("행").converted)
        assertEquals(1f, HangulConverter.analyzeReverse("낸").confidence, 0.0001f)
    }

    /** 사전에 없는 임의의 한글은(진짜 한국어 문장 포함) 절대 감지하지 않는다 — 오탐 원천 차단. */
    @Test
    fun analyzeReverse_nonDictionaryHangul_isZero() {
        for (s in listOf("안녕하세요", "나무위키", "한글", "오늘 날씨가 좋다", "행복")) {
            assertEquals(s, 0f, HangulConverter.analyzeReverse(s).confidence, 0.0001f)
        }
    }

    @Test
    fun analyzeReverse_noHangul_isZero() {
        assertEquals(0f, HangulConverter.analyzeReverse("hello").confidence, 0.0001f)
        assertEquals(0f, HangulConverter.analyzeReverse("123!@#").confidence, 0.0001f)
        assertEquals(0f, HangulConverter.analyzeReverse("").confidence, 0.0001f)
    }

    /** 상용어 여러 개로만 이뤄져도(전부 사전 매치) 감지된다 — 정방향의 "전부 상용어면 억제"와는 반대. */
    @Test
    fun analyzeReverse_allStopwordTokens_stillDetected() {
        // "행" -> god, 두 번 선택해도 각 토큰이 전부 사전에 있으면 그대로 통과.
        assertEquals(1f, HangulConverter.analyzeReverse("행 행").confidence, 0.0001f)
    }
}
