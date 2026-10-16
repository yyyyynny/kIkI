package com.langsense.app.util

/**
 * 입력 통계(2026-10) — 순수 로직(JVM 테스트 대상). 저장은 [Prefs] 가 문자열로 한다.
 *
 * 두 가지를 센다. 모두 기기 안에만 두고 밖으로 보내지 않는다.
 * - **날짜별 집계**([Day]): 한/영 전환 횟수, "교체?" 칩의 결과(눌러서 교체 / 무시 / 길게 눌러 예외), 타수 측정을 켰으면
 *   친 키 수와 실제로 치던 시간.
 * - **단어별 집계**([Word]): 칩이 뜬 원문(영문 덩어리)과 교체 문자열, 결과별 횟수. 설정의 "입력 통계"에서
 *   "자주 나온 한영타"로 보여주고, 같은 데이터로 **그만 물을 단어**를 정한다([shouldSuppress]).
 */
object InputStats {

    enum class Outcome { ACCEPTED, IGNORED, EXCEPTED }

    class Day(
        val epochDay: Long,
        val switches: Int = 0,
        val accepted: Int = 0,
        val ignored: Int = 0,
        val excepted: Int = 0,
        val keys: Int = 0,
        val typingMs: Long = 0,
    ) {
        /** 칩이 뜬 횟수(결과가 난 것만 — 드래그 중 다른 칩으로 바뀐 것은 세지 않는다). */
        val suggested: Int get() = accepted + ignored + excepted
    }

    class Word(
        val original: String,
        val converted: String,
        val accepted: Int = 0,
        val ignored: Int = 0,
        val excepted: Int = 0,
        val lastDay: Long = 0,
    ) {
        val total: Int get() = accepted + ignored + excepted
    }

    /** 날짜별 집계를 남기는 일수. */
    const val KEEP_DAYS = 30

    /** 단어별 집계 상한 — 넘으면 횟수가 적고 오래된 것부터 버린다. */
    const val MAX_WORDS = 200

    /** 자동 제안(한/영 전환 직후)을 이만큼 무시하고 한 번도 누르지 않았으면 그 단어는 그만 묻는다. */
    const val SUPPRESS_AFTER_IGNORED = 3

    /** 타수 측정: 키 사이 간격이 이보다 길면 쉬던 시간으로 보고 치던 시간에 넣지 않는다. */
    const val MAX_TYPING_GAP_MS = 2000L

    // ── 날짜별 ──

    fun encodeDays(days: List<Day>): String = days.joinToString("\n") {
        "${it.epochDay},${it.switches},${it.accepted},${it.ignored},${it.excepted},${it.keys},${it.typingMs}"
    }

    /** 깨진 줄은 건너뛴다(저장값 손상이 화면을 죽이지 않게). 날짜 오름차순. */
    fun decodeDays(s: String?): List<Day> {
        if (s.isNullOrBlank()) return emptyList()
        return s.lineSequence().mapNotNull { line ->
            val p = line.split(',')
            if (p.size != 7) return@mapNotNull null
            runCatching {
                Day(p[0].toLong(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toLong())
            }.getOrNull()
        }.sortedBy { it.epochDay }.toList()
    }

    /** [today] 칸에 값을 더하고 [KEEP_DAYS] 보다 오래된 날은 버린다. */
    fun addToDay(
        days: List<Day>,
        today: Long,
        switches: Int = 0,
        outcome: Outcome? = null,
        keys: Int = 0,
        typingMs: Long = 0,
    ): List<Day> {
        val old = days.firstOrNull { it.epochDay == today } ?: Day(today)
        val updated = Day(
            today,
            old.switches + switches,
            old.accepted + if (outcome == Outcome.ACCEPTED) 1 else 0,
            old.ignored + if (outcome == Outcome.IGNORED) 1 else 0,
            old.excepted + if (outcome == Outcome.EXCEPTED) 1 else 0,
            old.keys + keys,
            old.typingMs + typingMs,
        )
        return (days.filter { it.epochDay != today && it.epochDay > today - KEEP_DAYS } + updated).sortedBy { it.epochDay }
    }

    /** 최근 [n]일(오늘 포함) 합계. */
    fun sumRecent(days: List<Day>, today: Long, n: Int): Day {
        val r = days.filter { it.epochDay > today - n && it.epochDay <= today }
        return Day(
            today, r.sumOf { it.switches }, r.sumOf { it.accepted }, r.sumOf { it.ignored }, r.sumOf { it.excepted },
            r.sumOf { it.keys }, r.sumOf { it.typingMs },
        )
    }

    /** 분당 타수. 치던 시간이 너무 짧으면(10초 미만) 값이 들쭉날쭉해 null. */
    fun keysPerMinute(day: Day): Int? =
        if (day.typingMs < 10_000L || day.keys == 0) null else (day.keys * 60_000L / day.typingMs).toInt()

    /** 직전 키와의 간격 중 "치던 시간"으로 셀 몫. 처음이거나 쉬었으면 0. */
    fun typingGap(previousAt: Long, now: Long): Long {
        if (previousAt <= 0L) return 0L
        val gap = now - previousAt
        return if (gap in 1..MAX_TYPING_GAP_MS) gap else 0L
    }

    // ── 단어별 ──

    private fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').trim()

    /** 같은 단어인지 가르는 키 — 대소문자는 무시한다(`Dkssud`·`dkssud` 는 같은 실수). */
    fun wordKey(original: String): String = clean(original).lowercase()

    fun encodeWords(words: List<Word>): String = words.joinToString("\n") {
        "${clean(it.original)}\t${clean(it.converted)}\t${it.accepted}\t${it.ignored}\t${it.excepted}\t${it.lastDay}"
    }

    fun decodeWords(s: String?): List<Word> {
        if (s.isNullOrBlank()) return emptyList()
        return s.lineSequence().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size != 6 || p[0].isEmpty()) return@mapNotNull null
            runCatching { Word(p[0], p[1], p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toLong()) }.getOrNull()
        }.toList()
    }

    /** 칩 결과 하나를 단어별 집계에 더한다. 상한을 넘으면 횟수가 적고 오래된 것부터 버린다. */
    fun recordWord(words: List<Word>, original: String, converted: String, outcome: Outcome, today: Long): List<Word> {
        val key = wordKey(original)
        if (key.isEmpty()) return words
        val old = words.firstOrNull { wordKey(it.original) == key }
        val updated = Word(
            clean(original), clean(converted),
            (old?.accepted ?: 0) + if (outcome == Outcome.ACCEPTED) 1 else 0,
            (old?.ignored ?: 0) + if (outcome == Outcome.IGNORED) 1 else 0,
            (old?.excepted ?: 0) + if (outcome == Outcome.EXCEPTED) 1 else 0,
            today,
        )
        val rest = words.filter { wordKey(it.original) != key }
        val all = rest + updated
        if (all.size <= MAX_WORDS) return all
        val drop = all.filter { it !== updated }
            .sortedWith(compareBy<Word> { it.total }.thenBy { it.lastDay })
            .take(all.size - MAX_WORDS).toSet()
        return all.filter { it !in drop }
    }

    /**
     * 자동 제안(한/영 전환 직후)을 그만 띄울지 — [SUPPRESS_AFTER_IGNORED] 번 이상 무시했고 한 번도 눌러 교체한 적이 없을 때.
     * 드래그로 직접 선택한 경우엔 쓰지 않는다(사용자가 일부러 고르는 것이라). 통계를 지우면 다시 묻는다.
     */
    fun shouldSuppress(words: List<Word>, original: String): Boolean {
        val key = wordKey(original)
        val w = words.firstOrNull { wordKey(it.original) == key } ?: return false
        return w.accepted == 0 && w.ignored >= SUPPRESS_AFTER_IGNORED
    }

    /** 자주 나온 순(같으면 최근 순) 상위 [n]개. */
    fun topWords(words: List<Word>, n: Int): List<Word> =
        words.sortedWith(compareByDescending<Word> { it.total }.thenByDescending { it.lastDay }).take(n)
}
