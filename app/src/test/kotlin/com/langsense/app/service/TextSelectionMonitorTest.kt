package com.langsense.app.service

import android.text.InputType
import com.langsense.app.util.HangulConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TextSelectionMonitor.pickAnalysis 순수 함수 검증 (안드로이드 의존성 없음).
 * 순수 JVM 테스트 — `./gradlew testDebugUnitTest` 로 실행.
 *
 * 특히 "한글과 영어가 섞인 선택"에 대한 회귀 테스트: 한때 "선택에 한글이 있으면 무조건
 * 역방향만" 방식이라 이미 정상 한글과 진짜 한영타가 섞인 선택에서 감지가 아예 안 되는
 * 버그가 있었다(2026-09).
 */
class TextSelectionMonitorTest {

    private val threshold = 0.70f

    @Test
    fun mixedHangulAndForwardTypo_stillDetected() {
        // "저"(정상 한글) + "dkssud"(진짜 한영타) 혼합 — 정방향이 이미 섞인 텍스트를 그대로
        // 잘 처리하므로(한글 부분은 보존, 라틴 부분만 변환) 감지돼야 한다.
        val result = TextSelectionMonitor.pickAnalysis("저 dkssud", threshold)
        assertTrue(result.confidence >= threshold)
        assertEquals("저 안녕", result.converted)
    }

    @Test
    fun pureForwardTypo_usesForwardOnly() {
        val result = TextSelectionMonitor.pickAnalysis("dkssud", threshold)
        assertTrue(result.confidence >= threshold)
        assertEquals("안녕", result.converted)
    }

    /** 정방향이 못 잡는 순수 역방향 케이스(한글만, 사전 완전일치)는 보조 경로로 감지돼야 한다. */
    @Test
    fun pureReverseTypo_fallsBackToReverse() {
        val result = TextSelectionMonitor.pickAnalysis("행", threshold)
        assertEquals(1f, result.confidence, 0.0001f)
        assertEquals("god", result.converted)
    }

    /** 진짜 한국어 문장(한글만, 사전에 없음)은 정방향도 역방향도 잡지 않아야 한다(오탐 방지). */
    @Test
    fun realKoreanSentence_notDetected() {
        for (s in listOf("안녕하세요", "나무위키", "오늘 날씨가 좋다")) {
            val result = TextSelectionMonitor.pickAnalysis(s, threshold)
            assertTrue(s, result.confidence < threshold)
        }
    }

    /** 정상 영어 문장(라틴만, 조합 실패율 높음)은 감지하지 않아야 한다. */
    @Test
    fun realEnglishSentence_notDetected() {
        val result = TextSelectionMonitor.pickAnalysis("hello world", threshold)
        assertTrue(result.confidence < threshold)
    }

    /** 이미 자연스러운 혼합 표현(정방향도 역방향도 안 걸림)은 그대로 둔다. */
    @Test
    fun naturalMixedText_notDetected() {
        val result = TextSelectionMonitor.pickAnalysis("hello 안녕", threshold)
        assertTrue(result.confidence < threshold)
    }

    /** 주변 문맥: 선택 앞뒤 창(선택 포함) 안의 한글 음절·낱자모를 본다. */
    @Test
    fun hasHangulNear_looksAroundSelectionOnly() {
        val text = "오늘 wha 먹을까"
        val s = text.indexOf("wha")
        assertTrue(TextSelectionMonitor.hasHangulNear(text, s, s + 3))
        assertEquals(false, TextSelectionMonitor.hasHangulNear("see you wha later", 8, 11))
        // 창(40자) 밖의 한글은 문맥이 아니다
        val far = "한글" + " ".repeat(60) + "wha"
        val f = far.indexOf("wha")
        assertEquals(false, TextSelectionMonitor.hasHangulNear(far, f, f + 3))
        // 선택 안의 한글도 문맥이다(ㅋㅋ 같은 낱자모 포함)
        assertTrue(TextSelectionMonitor.hasHangulNear("ㅋㅋ wha", 0, 6))
    }

    /** 한국어 문맥이면 짧은 한영타도 잡는다(영어 문서에선 보수적으로 둔다). */
    @Test
    fun koreanContext_detectsShortTypo() {
        assertTrue(TextSelectionMonitor.pickAnalysis("wha", threshold).confidence < threshold)
        val r = TextSelectionMonitor.pickAnalysis("wha", threshold, koreanContext = true)
        assertTrue(r.confidence >= threshold)
        assertEquals("좀", r.converted)
    }

    // ── 선택 주변 정보(2026-10-01): 이웃 증거 · 자동 대문자 · 문장 중간 대문자 ─────────────────

    private fun pick(text: String, sel: String, editable: Boolean = true, autocap: Boolean = true): HangulConverter.Analysis {
        val a = text.indexOf(sel)
        val b = a + sel.length
        val kc = TextSelectionMonitor.hasHangulNear(text, a, b)
        val ctx = TextSelectionMonitor.selectionContext(text, a, b, editable, collectNeighbors = !kc, autocap = autocap)
        return TextSelectionMonitor.pickAnalysis(sel, threshold, kc, emptySet(), ctx)
    }

    @Test
    fun selectionContext_neighborsAreLatinTokensOutsideSelection() {
        val ctx = TextSelectionMonitor.selectionContext("rmsid wha tlfgek", 6, 9, editable = true)
        assertEquals(listOf("rmsid", "tlfgek"), ctx.neighbors)
        assertEquals('d', ctx.charBefore)
        // 한글 조각·숫자만인 조각은 이웃이 아니다
        assertEquals(listOf("abc"), TextSelectionMonitor.selectionContext("가나 123 wha abc", 7, 10, true).neighbors)
    }

    /** 창(앞뒤 40자) 경계에 걸친 토큰은 잘리지 않고 통째로 읽고, 창 밖 토큰은 넣지 않는다. */
    @Test
    fun selectionContext_windowBoundaryTokenReadWhole() {
        val text = "aaaa " + "x".repeat(50) + " wha " + "y".repeat(45) + " far"
        val s = text.indexOf(" wha ") + 1
        val ctx = TextSelectionMonitor.selectionContext(text, s, s + 3, editable = true)
        assertEquals(listOf("x".repeat(50), "y".repeat(45)), ctx.neighbors)
    }

    @Test
    fun selectionContext_charBeforeSkipsSpacesForSentenceStart() {
        assertEquals('.', TextSelectionMonitor.selectionContext("Hi.  Dkssud", 5, 11, true).charBefore)
        assertEquals(null, TextSelectionMonitor.selectionContext("Dkssud", 0, 6, true).charBefore)
    }

    /** 자동 대문자는 편집 칸이 대문자를 요청할 때만(TextKeyListener 규칙). 읽기 전용·알 수 없음(0)은 가능으로 본다. */
    @Test
    fun mayAutocap_followsInputTypeCapFlags() {
        val text = InputType.TYPE_CLASS_TEXT
        assertFalse(TextSelectionMonitor.mayAutocap(true, text))
        assertTrue(TextSelectionMonitor.mayAutocap(true, text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertTrue(TextSelectionMonitor.mayAutocap(true, 0))
        assertTrue(TextSelectionMonitor.mayAutocap(false, text))
    }

    /** 앞뒤가 한영타면 흔한 영어 보호 단어(`wha`)도 판정한다 — 홀로 있으면 영어로 둔다. */
    @Test
    fun neighbors_typoSentenceJudgesCommonEnglishWord() {
        val inTypo = pick("rmsid wha tlfgek", "wha")
        assertTrue(inTypo.confidence >= threshold)
        assertEquals("좀", inTypo.converted)
        assertTrue(pick("wha", "wha").confidence < threshold)
    }

    /** 영어 이웃은 읽기 전용 글(남이 쓴 영어)에서만 사전확률을 내린다 — 내가 치는 칸의 한영타는 그대로 잡는다. */
    @Test
    fun neighbors_englishLowersOnlyInReadOnlyText() {
        val text = "Buy this game, sork tlfgek it."
        assertTrue(pick(text, "sork", editable = false).confidence < threshold)
        assertTrue(pick(text, "sork", editable = true).confidence >= threshold)
    }

    /** 문장 중간의 첫 대문자(고유명사 모양)는 자동 대문자로 설명이 안 돼 한영타로 보지 않는다. */
    @Test
    fun midSentenceCapital_properNounNotDetected() {
        assertTrue(pick("He met Dhaka officials today.", "Dhaka").confidence < threshold)
    }

    /**
     * 문장 첫머리 자동 대문자: Shift 가 무의미한 키(`Gksmf`)는 그대로 같은 자모, Shift 키(`Rmfoeh` = 끄래도)는 대문자를
     * 요청하는 칸에서만 되돌린 읽기(그래도)로 교체한다. 영어 단어의 대소문자는 바꾸지 않는다.
     */
    @Test
    fun autocap_sentenceStartReadsLowercase() {
        assertEquals("하늘 을 보라", pick("Gksmf dmf qhfk", "Gksmf dmf qhfk").converted)
        val sentence = "Rmfoeh wkf ehlf rjtdla"
        assertEquals("그래도 잘 될 것임", pick(sentence, sentence, autocap = true).converted)
        assertEquals("끄래도 잘 될 것임", pick(sentence, sentence, autocap = false).converted)
        assertEquals("That's 안녕", pick("That's dkssud", "That's dkssud").converted)
    }

    // ── 한/영 전환 직후 제안(2026-10) ──

    private fun run(text: String): String? =
        TextSelectionMonitor.latinRunBeforeCursor(text, text.length)?.let { text.substring(it.first, it.last + 1) }

    /** 커서 앞 "한글 없는 덩어리": 뒤 공백은 건너뛰고, 한글·줄바꿈에서 멈춘다. 영문이 없으면 null. */
    @Test
    fun latinRunBeforeCursor_stopsAtHangulAndNewline() {
        assertEquals("wkf audtj", run("진짜 wkf audtj"))
        assertEquals("dkssud", run("dkssud "))
        assertEquals("dkssud", run("hello\ndkssud"))
        assertEquals(null, run("안녕"))
        assertEquals(null, run("진짜 123"))
        assertEquals(null, TextSelectionMonitor.latinRunBeforeCursor("dkssud", 0))
    }

    private fun suggest(text: String): String? =
        TextSelectionMonitor.switchSuggestion(text, text.length, threshold)?.converted

    /** 전환 직후엔 판정 모델이 일부러 빼 둔 짧은 조각(`zz`=ㅋㅋ, `sj`=너)도 받는다 — "곧바로 한글로 바꿨다"가 증거. */
    @Test
    fun switchSuggestion_acceptsTypoAndShortColloquial() {
        assertEquals("안녕", suggest("dkssud"))
        assertEquals("ㅋㅋ", suggest("진짜 zz"))
        assertEquals("너", suggest("sj"))
        assertEquals("ㅠㅠ", suggest("bb"))
    }

    /** 일부러 친 영문은 그대로 — 대문자 약어, 영어 문장, 숫자 단위, 3글자 이상 음절뿐인 약어(`bgm`). */
    @Test
    fun switchSuggestion_keepsIntendedEnglish() {
        for (t in listOf("EBS", "made in china", "5cm", "bgm", "hello", "the", "OOO", "B")) {
            assertEquals(t, null, suggest(t))
        }
    }

    /** 예외 단어는 보조 판정에서도 막는다. */
    @Test
    fun switchSuggestion_respectsExceptions() {
        assertEquals(null, TextSelectionMonitor.switchSuggestion("zz", 2, threshold, setOf("zz"))?.converted)
    }
}
