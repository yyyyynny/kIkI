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

    /**
     * 사용자 제보(2026-09): 한영타 옆의 영어 단어까지 통째로 변환돼 `cpu`→`체ㅕ`, `gpu`→`헤ㅕ`
     * 가 됐다. 감지는 그대로 되면서, 교체 문자열에서는 영어 단어를 지켜야 한다.
     */
    /**
     * 홀로 선 자음 한 글자(초성 줄임)는 한영타 선택 안에서 한글로 교체돼야 한다 — 사용자 제보(2026-10):
     * `w rkxdms` 가 `ㅈ 같은` 이 아니라 `w 같은` 으로 바뀌었다. 구두점·대문자가 붙은 한 글자(`T.T`,
     * `'s`)와 관사 `a` 는 영어 그대로.
     */
    @Test
    fun analyze_convertsLoneConsonantInTypoSelection() {
        assertEquals("ㅈ 같은", HangulConverter.analyze("w rkxdms").converted)
        assertEquals("ㅈ 같은", HangulConverter.analyze("w rkxdms", koreanContext = true).converted)
        assertEquals("태클 ㄴ", HangulConverter.analyze("xozmf s").converted)
        assertEquals("ㄴ 같은", HangulConverter.analyze("s rkxdms").converted)
        assertEquals("에휴. T.T", HangulConverter.analyze("dpgb. T.T").converted)
        assertEquals("굳 it's", HangulConverter.analyze("rne it's").converted)
        assertEquals("내용은 a 급", HangulConverter.analyze("sodyddms a rmq").converted)
    }

    @Test
    fun analyze_keepsEnglishWordsNextToTypo() {
        val cases = mapOf(
            "cpu wjdakf whgek" to "cpu 정말 좋다",
            "gpu tjdsmddl whgdkwlsek" to "gpu 성능이 좋아진다",
            "cpu gpu rkqtdl dhffkTek" to "cpu gpu 값이 올랐다",
            "ram 16rlrk vlfdygksep" to "ram 16기가 필요한데",
            "usb zpdlqmf rkqt" to "usb 케이블 값",
        )
        for ((typo, want) in cases) {
            val result = HangulConverter.analyze(typo)
            assertTrue("$typo -> conf=${result.confidence}", result.confidence >= 0.70f)
            assertEquals(typo, want, result.converted)
        }
    }

    /** 영어 단어에 조사가 붙어 한 덩어리로 친 경우 — 영어는 두고 조사만 바꾼다. */
    @Test
    fun analyze_splitsEnglishWordAndParticle() {
        val cases = mapOf(
            "cpusms qkRnjTek" to "cpu는 바꿨다",
            "gpurk vlfdygo" to "gpu가 필요해",
            "OSTeh whgdkdy" to "OST도 좋아요",
            // B 는 Shift 가 한글에서 의미 없는 키 — 대문자로 쳤다는 것 자체가 영어의 증거
            "Brmq dudghk" to "B급 영화",
        )
        for ((typo, want) in cases) assertEquals(typo, want, HangulConverter.analyze(typo).converted)
    }

    /** 순수 한영타 문장은 예전처럼 전부 바뀌어야 한다 — 구어체 낱자모(ㅋㅋ/ㅠㅠ/ㅉㅉ) 포함. */
    @Test
    fun analyze_pureTypoSentence_fullyConverted() {
        val cases = mapOf(
            "dlrjs wjdakf woalTek" to "이건 정말 재밌다",
            "dkssud zzz" to "안녕 ㅋㅋㅋ",
            "tkfkdgo bb" to "사랑해 ㅠㅠ",
            // 전부 대문자여도 Shift 가 의미 있는 키(W=ㅉ)뿐이면 약어로 단정하지 않는다
            "wjdakf WW" to "정말 ㅉㅉ",
        )
        for ((typo, want) in cases) assertEquals(typo, want, HangulConverter.analyze(typo).converted)
    }

    /** `체ㅕ` 는 실제 구어체에 거의 없는 전이(음절 뒤 낱모음 ㅕ)라 "기형"으로 판정돼야 한다. */
    @Test
    fun informalModel_flagsMalformedButNotCommonJamo() {
        assertTrue(TypoLanguageModel.koreanInformal("체ㅕ")!!.rarestTransition < -8.0)
        assertTrue(TypoLanguageModel.koreanInformal("헤ㅕ")!!.rarestTransition < -8.0)
        assertTrue(TypoLanguageModel.koreanInformal("ㅋㅋㅋ")!!.rarestTransition >= -8.0)
        assertTrue(TypoLanguageModel.koreanInformal("좋아ㅠㅠ")!!.rarestTransition >= -8.0)
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

    /** 대문자 약어 + 영타 조사(`GUIdml`=GUI의)는 단독 선택이어도 감지되고, 약어는 그대로 남는다. */
    @Test
    fun analyze_acronymWithTypoTail_detected() {
        for ((typo, expected) in listOf(
            "GUIdml" to "GUI의", "CLIdhk" to "CLI와", "GUIfmf" to "GUI를",
            "CGrk" to "CG가", "OSTeh" to "OST도", "GUIdml." to "GUI의.",
        )) {
            val a = HangulConverter.analyze(typo)
            assertTrue("$typo conf=${a.confidence}", a.confidence >= 0.7f)
            assertEquals(expected, a.converted)
        }
    }

    /** 영어 글에 실제로 나오는 "대문자 + 소문자" 표기는 감지하지 않는다(AG News 실측 오탐 후보 포함). */
    @Test
    fun analyze_acronymWithEnglishTail_notDetected() {
        for (w in listOf("CNNfn", "WEek", "NDak", "FAdm", "ITWeb", "EBay", "SQLite", "NVidia", "ZENworks", "IPOed")) {
            val c = HangulConverter.analyze(w).confidence
            assertTrue("$w conf=$c", c < 0.7f)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2026-09 재설계(가설별 판정) 회귀 고정 — 근거·수치는 docs/한영타_검증.md
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Shift 물리: 두벌식에서 Shift 가 의미 있는 키는 Q·W·E·R·T·O·P(쌍자음·ㅒㅖ) 뿐이다. 그 외 키의
     * 대문자는 한글을 치던 사람이 누를 이유가 없어 오히려 약어·화학식의 증거다. 예전엔 첫 글자 외
     * 대문자면 무조건 가산점이라 `sNl` 89.7%, `CoA`/`GPa`/`SBTi` 가 오탐이었다.
     */
    @Test
    fun analyze_needlessShift_isEvidenceAgainstTypo() {
        for (w in listOf("sNl", "CoA", "GPa", "SBTi", "DoS", "WLANs")) {
            assertTrue(w, HangulConverter.detectEnglishToKorean(w) < 0.70f)
        }
        // Q·W·E·R·T·O·P 대문자는 여전히 한영타 신호(함께·했다)
        for (w in listOf("gkaRp", "goTek")) assertTrue(w, HangulConverter.detectEnglishToKorean(w) >= 0.70f)
    }

    /**
     * CapsLock 을 켠 채 친 한영타 — 대소문자를 뒤집어 판정하고 교체도 뒤집어 변환한다. 예전엔 전부
     * 대문자면 약어로 보고 건너뛰었고(`DKSSUD` 0%), `GKArP` 는 감지는 되는데 "GKA계"로 망가뜨렸다.
     */
    @Test
    fun analyze_capsLockTypo_detectedAndConverted() {
        val cases = mapOf(
            "DKSSUD" to "안녕",
            "WJDAKF WHGEK" to "정말 좋다",
            "GKArP" to "함께",
            "GOtEK" to "했다", // CapsLock 중 Shift+T = 소문자 t = ㅆ
            "QOTHD" to "배송",
            "RKAKSGO" to "가만해",
        )
        for ((typo, want) in cases) {
            val a = HangulConverter.analyze(typo)
            assertTrue("$typo conf=${a.confidence}", a.confidence >= 0.70f)
            assertEquals(typo, want, a.converted)
        }
    }

    /**
     * 3글자 전부 대문자는 약어가 압도적이라(한국어 글 속 3글자 라틴 토큰의 약 70%) CapsLock 으로
     * 뒤집어 보지 않는다 — 뒤집기만 하면 그중 10%가 오탐(`DLC`→잋, `DLF`→일)이었다.
     */
    @Test
    fun analyze_shortAllCapsAcronyms_notTreatedAsCapsLock() {
        for (w in listOf("SNS", "DLC", "DLF", "GKS", "FPS", "TBS", "NASA", "KOREA")) {
            assertTrue(w, HangulConverter.detectEnglishToKorean(w) < 0.70f)
        }
        // CapsLock 한영타 문장 옆의 약어는 약어로 남는다
        assertEquals("GUI 정말 좋다", HangulConverter.analyze("GUI wjdakf whgek").converted)
    }

    /**
     * Shift 증인 사전: 한국어 글에서 Shift 가 무의미한 키에 대문자로 쓰인 적이 있는 라틴 문자열은
     * 한글 영타일 수 없다 — 소문자로 써도 약어로 본다. 예전 오탐: `sns`→눈 74.6%, `snl`→뉘 76.3%,
     * 스팀 리뷰 10만 건에서 `dlc` 432회·`fps` 143회.
     */
    @Test
    fun analyze_lowercaseAcronymsInKoreanText_notDetected() {
        for (w in listOf("sns", "snl", "dlc", "fps", "Sns", "Dlc")) {
            assertTrue(w, HangulConverter.analyze(w).confidence < 0.70f)
            assertTrue("$w (한국어 문맥)", HangulConverter.analyze(w, koreanContext = true).confidence < 0.70f)
        }
        assertTrue(HangulConverter.analyze("오랜만에 sns에서 핫해서", koreanContext = true).confidence < 0.70f)
    }

    /**
     * 어절 위치별 모델 + 자주 쓰는 어절 기억: 예전에 가장 많이 놓친 한영타는 드문 단어가 아니라 가장
     * 흔한 짧은 단어였다(`sjan`=너무 — NSMC 5만 문장에서 2,803회 누락, `rmsid`=그냥, `dho`=왜).
     */
    @Test
    fun analyze_commonShortWords_detected() {
        for (w in listOf("sjan", "rmsid", "dho", "dks", "wkf")) {
            assertTrue(w, HangulConverter.detectEnglishToKorean(w) >= 0.70f)
        }
    }

    /** 주변(선택 안 포함)에 한글이 있으면 짧은 조각도 한영타 쪽으로 기운다 — 영어 단어는 그대로 영어. */
    @Test
    fun analyze_koreanContext_helpsShortTyposOnly() {
        assertTrue(HangulConverter.analyze("wha").confidence < 0.70f)
        assertTrue(HangulConverter.analyze("wha", koreanContext = true).confidence >= 0.70f)
        for (w in listOf("hello", "computer", "keyboard", "language", "video", "check")) {
            assertTrue(w, HangulConverter.analyze(w, koreanContext = true).confidence < 0.70f)
        }
    }

    /** 사용자 예외 단어(설정): 판정도 교체도 하지 않는다 — 같은 선택의 다른 한영타만 바뀐다. */
    @Test
    fun analyze_userExceptions_skipped() {
        assertEquals(0f, HangulConverter.analyze("dkssud", exceptions = setOf("dkssud")).confidence, 0.0001f)
        val a = HangulConverter.analyze("dkssud wjdakf whgek", exceptions = setOf("dkssud"))
        assertTrue(a.confidence >= 0.70f)
        assertEquals("dkssud 정말 좋다", a.converted)
    }

    /**
     * 음절 뒤 구어체 낱자모(ㅋㅋ/ㅎㅎ/ㅠㅠ)가 붙은 한영타 — 예전엔 낱자모를 전부 최저 확률로 봐 12%만
     * 잡았다(`고맙습니다ㅠㅠ`). 반대로 낱자모만인 토큰(`zzz`=조는 소리, `bbbb`=엄지척)과 늘여 쓴 영어
     * (`soooo`→내ㅐㅐ)는 영문 그대로 쓰는 경우라 잡지 않는다(NSMC 실측 오탐).
     */
    @Test
    fun analyze_trailingColloquialJamo() {
        // ⚠️ 받침이 될 수 있는 자음은 앞 음절에 붙는다(`whgspdygg`→좋네욯ㅎ — 실제 IME 도 같다). 그래서 붙지 않는
        // 모음 ㅠ, 또는 이미 받침이 있는 음절 뒤의 자음으로 고정한다.
        for ((typo, want) in listOf("rhakqtmqslekbb" to "고맙습니다ㅠㅠ", "dlTdjdybb" to "있어요ㅠㅠ", "woalTdmazz" to "재밌음ㅋㅋ")) {
            val a = HangulConverter.analyze(typo)
            assertTrue("$typo conf=${a.confidence}", a.confidence >= 0.70f)
            assertEquals(want, a.converted)
        }
        for (w in listOf("zzz", "bbbb", "sooooooooo", "cooooooool")) {
            assertTrue(w, HangulConverter.analyze(w).confidence < 0.70f)
            assertTrue("$w (한국어 문맥)", HangulConverter.analyze(w, koreanContext = true).confidence < 0.70f)
        }
    }
}
