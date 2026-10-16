package com.langsense.app.util

import com.langsense.app.util.InputStats.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputStatsTest {

    @Test
    fun days_addAndRoundTrip() {
        var days = InputStats.addToDay(emptyList(), 100, switches = 1)
        days = InputStats.addToDay(days, 100, outcome = Outcome.ACCEPTED)
        days = InputStats.addToDay(days, 100, outcome = Outcome.IGNORED)
        days = InputStats.addToDay(days, 101, keys = 50, typingMs = 10_000)
        val back = InputStats.decodeDays(InputStats.encodeDays(days))
        assertEquals(2, back.size)
        assertEquals(1, back[0].switches)
        assertEquals(2, back[0].suggested)
        assertEquals(1, back[0].accepted)
        assertEquals(50, back[1].keys)
    }

    /** 30일보다 오래된 날은 버린다. 깨진 줄은 건너뛴다. */
    @Test
    fun days_pruneOldAndSkipBroken() {
        var days = InputStats.addToDay(emptyList(), 1, switches = 1)
        days = InputStats.addToDay(days, 1L + InputStats.KEEP_DAYS, switches = 1)
        assertEquals(listOf(1L + InputStats.KEEP_DAYS), days.map { it.epochDay })
        assertEquals(1, InputStats.decodeDays("5,1,0,0,0,0,0\n깨진줄\n6,x,0,0,0,0,0").size)
    }

    @Test
    fun sumRecent_onlyWindow() {
        var days = InputStats.addToDay(emptyList(), 10, switches = 3)
        days = InputStats.addToDay(days, 4, switches = 5)
        assertEquals(3, InputStats.sumRecent(days, 10, 1).switches)
        assertEquals(3, InputStats.sumRecent(days, 10, 6).switches)
        assertEquals(8, InputStats.sumRecent(days, 10, 7).switches)
    }

    @Test
    fun typing_gapAndSpeed() {
        assertEquals(0L, InputStats.typingGap(0, 1000))
        assertEquals(300L, InputStats.typingGap(1000, 1300))
        assertEquals(0L, InputStats.typingGap(1000, 1000 + InputStats.MAX_TYPING_GAP_MS + 1)) // 쉬던 시간
        assertNull(InputStats.keysPerMinute(InputStats.Day(1, keys = 10, typingMs = 5_000)))
        assertEquals(300, InputStats.keysPerMinute(InputStats.Day(1, keys = 300, typingMs = 60_000)))
    }

    /** 같은 단어는 대소문자를 무시하고 합친다. 탭·줄바꿈은 저장 형식을 깨지 않게 공백으로. */
    @Test
    fun words_mergeCaseInsensitiveAndRoundTrip() {
        var w = InputStats.recordWord(emptyList(), "dkssud", "안녕", Outcome.ACCEPTED, 1)
        w = InputStats.recordWord(w, "Dkssud", "안녕", Outcome.IGNORED, 2)
        w = InputStats.recordWord(w, "a\tb", "ㅁ ㅠ", Outcome.IGNORED, 2)
        val back = InputStats.decodeWords(InputStats.encodeWords(w))
        assertEquals(2, back.size)
        val d = back.first { InputStats.wordKey(it.original) == "dkssud" }
        assertEquals(1, d.accepted)
        assertEquals(1, d.ignored)
        assertEquals("a b", back.first { it.original.startsWith("a") }.original)
    }

    /** 상한을 넘으면 횟수가 적고 오래된 것부터 버리되 방금 기록한 것은 남긴다. */
    @Test
    fun words_capKeepsNewest() {
        var w = emptyList<InputStats.Word>()
        for (i in 0 until InputStats.MAX_WORDS) w = InputStats.recordWord(w, "w$i", "x", Outcome.IGNORED, i.toLong())
        w = InputStats.recordWord(w, "w5", "x", Outcome.IGNORED, 300) // 횟수 2
        w = InputStats.recordWord(w, "new", "x", Outcome.IGNORED, 301)
        assertEquals(InputStats.MAX_WORDS, w.size)
        assertTrue(w.any { it.original == "new" })
        assertTrue(w.any { it.original == "w5" })
        assertFalse(w.any { it.original == "w0" })
    }

    /** 3번 무시 + 교체 0번이면 그만 묻고, 한 번이라도 교체했으면 계속 묻는다. */
    @Test
    fun suppress_afterIgnoredWithoutAccept() {
        var w = emptyList<InputStats.Word>()
        repeat(InputStats.SUPPRESS_AFTER_IGNORED - 1) { w = InputStats.recordWord(w, "soso", "내내", Outcome.IGNORED, 1) }
        assertFalse(InputStats.shouldSuppress(w, "soso"))
        w = InputStats.recordWord(w, "soso", "내내", Outcome.IGNORED, 1)
        assertTrue(InputStats.shouldSuppress(w, "SOSO"))
        w = InputStats.recordWord(w, "soso", "내내", Outcome.ACCEPTED, 2)
        assertFalse(InputStats.shouldSuppress(w, "soso"))
    }

    @Test
    fun topWords_byTotalThenRecent() {
        var w = InputStats.recordWord(emptyList(), "a", "ㅁ", Outcome.IGNORED, 1)
        w = InputStats.recordWord(w, "b", "ㅠ", Outcome.IGNORED, 5)
        w = InputStats.recordWord(w, "c", "ㅊ", Outcome.ACCEPTED, 2)
        w = InputStats.recordWord(w, "c", "ㅊ", Outcome.ACCEPTED, 3)
        assertEquals(listOf("c", "b", "a"), InputStats.topWords(w, 3).map { it.original })
    }
}
