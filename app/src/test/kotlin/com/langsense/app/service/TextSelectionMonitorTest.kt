package com.langsense.app.service

import org.junit.Assert.assertEquals
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
}
