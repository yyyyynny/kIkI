package com.langsense.app.eval

import com.langsense.app.util.HangulConverter
import com.langsense.app.util.TypoLanguageModel
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 한영타 판정 실측 검증(대용량 공개 데이터). 데이터는 저장소에 없으니 먼저 받는다:
 *   tools/eval-data/fetch.sh
 *   ./gradlew testDebugUnitTest --rerun --tests "com.langsense.app.eval.*" -i   (-i 로 수치 출력,
 *   --rerun 없으면 코드가 그대로일 때 Gradle 이 최신 상태로 보고 건너뛴다)
 * 데이터가 없으면(CI 등) 전부 건너뛴다. 방법·출처·기록된 수치는 docs/한영타_검증.md.
 * 판정 규칙이나 모델 파라미터를 바꿨다면 이 테스트로 전후 수치를 비교할 것.
 */
class TypoCorpusEvalTest {

    private val dataDir: File = listOf(
        System.getenv("KIKI_EVAL_DATA"),
        "../tools/eval-data/data", // Gradle 단위 테스트의 작업 디렉터리는 app/
        "tools/eval-data/data",
    ).filterNotNull().map(::File).firstOrNull { File(it, "words.txt").exists() } ?: File("../tools/eval-data/data")

    private fun data(name: String): File {
        val f = File(dataDir, name)
        assumeTrue("검증 데이터 없음 — tools/eval-data/fetch.sh 로 받을 것 ($f)", f.exists())
        return f
    }

    private fun detected(s: String) = HangulConverter.analyze(s).confidence >= THRESHOLD

    /**
     * 대문자 약어 + 영타 꼬리(`GUIdml`=GUI의). 오탐 측은 영어 글·단어 목록에 실제로 나오는 같은 형태
     * (`CNNfn`, `SQLite`), 감지 측은 NSMC 의 "대문자 약어+한글"(`CG가`)에서 한글만 영타로 바꾼 것.
     */
    @Test
    fun acronymTail() {
        val shape = Regex("^[A-Z]{2,}[a-z]{2,}$")
        val neg = HashMap<String, Int>()
        listOf("train.csv", "test.csv").forEach { f ->
            data(f).forEachLine { line -> line.split(Regex("[^A-Za-z]+")).forEach { w -> if (shape.matches(w)) neg.merge(w, 1, Int::plus) } }
        }
        data("words.txt").forEachLine { w -> w.trim().let { if (shape.matches(it)) neg.merge(it, 1, Int::plus) } }
        val negHits = neg.filterKeys(::detected)
        println("EVAL acronymTail 오탐: ${negHits.values.sum()}/${neg.values.sum()}회 ${negHits.keys.take(10)}")

        val pos = HashMap<String, Int>()
        val posRe = Regex("([A-Z]{2,})([가-힣]+)")
        listOf("ratings_train.txt", "ratings_test.txt").forEach { f ->
            data(f).forEachLine { line ->
                posRe.findAll(line).forEach { m ->
                    // NSMC 의 "OO"(인명 가림 자리표시자)는 실제 약어가 아니라 제외
                    if (m.groupValues[1].all { it == 'O' }) return@forEach
                    pos.merge(m.groupValues[1] + HangulConverter.convertKorToEng(m.groupValues[2]), 1, Int::plus)
                }
            }
        }
        val posTotal = pos.values.sum()
        val posHit = pos.filterKeys(::detected).values.sum()
        val rate = posHit.toDouble() / posTotal
        println("EVAL acronymTail 감지: $posHit/$posTotal (${"%.2f".format(rate * 100)}%)")

        assertTrue("약어+꼬리 형태 영어 오탐 발생: $negHits", negHits.isEmpty())
        assertTrue("약어+조사 감지율 하락: $rate", rate >= 0.90)
    }

    /** 실제 영어 기사 전체 단어(선택은 보통 단어 하나)의 오탐률. */
    @Test
    fun englishNewsFalsePositive() {
        var tokens = 0
        val hits = HashMap<String, Int>()
        listOf("train.csv", "test.csv").forEach { f ->
            data(f).forEachLine { line ->
                line.split(Regex("\\s+")).forEach { w ->
                    if (w.isEmpty() || w.none { it.isLetter() }) return@forEach
                    tokens++
                    if (detected(w)) hits.merge(w, 1, Int::plus)
                }
            }
        }
        val n = hits.values.sum()
        val rate = n.toDouble() / tokens
        println("EVAL 영어 기사 오탐: $n/$tokens (${"%.4f".format(rate * 100)}%) 상위 ${hits.entries.sortedByDescending { it.value }.take(10).map { "${it.key}×${it.value}" }}")
        assertTrue("영어 기사 오탐률 상승: $rate", rate <= 0.00003) // 기록 0.0016%, 여유 두 배
    }

    /** 영어 사전 단어(대소문자 그대로) 오탐률. */
    @Test
    fun englishDictionaryFalsePositive() {
        var total = 0
        val hits = ArrayList<String>()
        data("words.txt").forEachLine { w ->
            val t = w.trim()
            if (t.isEmpty() || t.none { it.isLetter() }) return@forEachLine
            total++
            if (detected(t)) hits += t
        }
        val rate = hits.size.toDouble() / total
        println("EVAL 영어 사전 오탐: ${hits.size}/$total (${"%.3f".format(rate * 100)}%) 예 ${hits.take(15)}")
        assertTrue("영어 사전 오탐률 상승: $rate", rate <= 0.0005)
    }

    /**
     * NSMC test 5만 문장 중 감지되는 문장 수(참고용). NSMC 에는 리뷰 자체가 영타로 쓰인 진짜 한영타
     * 문장이 섞여 있어 "감지 = 오탐"이 아니므로 단정하지 않고 표본만 출력한다.
     */
    @Test
    fun koreanReviewsReport() {
        var sentences = 0
        val hits = ArrayList<String>()
        data("ratings_test.txt").forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size < 2 || parts[0] == "id" || parts[1].isBlank()) return@forEachLine
            sentences++
            val a = HangulConverter.analyze(parts[1])
            if (a.confidence >= THRESHOLD) hits += "${parts[1]} => ${a.converted}"
        }
        println("EVAL NSMC test 감지 문장: ${hits.size}/$sentences")
        hits.take(10).forEach { println("EVAL   $it") }
    }

    /** NSMC test 의 한글 어절(라틴 없는 것)을 영타로 되돌린 것 — 출현 횟수 포함. */
    private fun nsmcTypos(): Map<String, Int> {
        val out = HashMap<String, Int>()
        data("ratings_test.txt").forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size < 2 || parts[0] == "id") return@forEachLine
            for (w in parts[1].split(' ')) {
                if (w.none { it in '가'..'힣' } || w.any { it in 'a'..'z' || it in 'A'..'Z' }) continue
                out.merge(HangulConverter.convertKorToEng(w), 1, Int::plus)
            }
        }
        return out
    }

    private fun latinLetters(s: String) = s.count { it in 'a'..'z' || it in 'A'..'Z' }

    /**
     * 한영타 감지율(어절 단위, 라틴 3글자 이상) — 주변 문맥 없음(통째로 잘못 친 경우)과 한국어 문맥
     * (한국어 문서 속 일부만 잘못 친 경우). 기록: 97.46% / 99.30%(2026-09-30 강한 문맥 가산·구어체 어절 기억,
     * 재설계 직후 96.4% / 97.6%, 재설계 전 94.0%).
     */
    @Test
    fun typoDetectionRate() {
        val typos = nsmcTypos()
        var total = 0
        var plain = 0
        var context = 0
        for ((w, n) in typos) {
            if (latinLetters(w) < 3) continue
            total += n
            if (HangulConverter.analyze(w).confidence >= THRESHOLD) plain += n
            if (HangulConverter.analyze(w, koreanContext = true).confidence >= THRESHOLD) context += n
        }
        val r0 = plain.toDouble() / total
        val r1 = context.toDouble() / total
        println("EVAL 한영타 감지(NSMC 어절): 문맥없음 ${"%.2f".format(r0 * 100)}%  한국어문맥 ${"%.2f".format(r1 * 100)}%  ($total 회)")
        assertTrue("한영타 감지율 하락: $r0", r0 >= 0.965)
        assertTrue("한국어 문맥 감지율 하락: $r1", r1 >= 0.99)
    }

    /**
     * CapsLock 을 켠 채 친 한영타(위 어절의 대소문자를 뒤집은 것). 3글자 전부 대문자는 약어가
     * 압도적이라 일부러 CapsLock 으로 보지 않으므로 4글자 이상만 잰다. 기록: 97.0%(이전 약 14%).
     */
    @Test
    fun capsLockDetectionRate() {
        var total = 0
        var hit = 0
        for ((w, n) in nsmcTypos()) {
            if (latinLetters(w) < 4) continue
            total += n
            if (HangulConverter.analyze(TypoLanguageModel.swapCase(w)).confidence >= THRESHOLD) hit += n
        }
        val rate = hit.toDouble() / total
        println("EVAL CapsLock 한영타 감지(4글자 이상): ${"%.2f".format(rate * 100)}%  ($total 회)")
        assertTrue("CapsLock 감지율 하락: $rate", rate >= 0.95)
    }

    /**
     * 선택 단위(문장): 사용자는 영타로 친 **구간 전체**를 고르는 경우가 많다(모드를 모른 채 한동안 친 것).
     * NSMC test 문장의 한글 어절을 전부 영타로 바꾼 문장을 통째로 판정한다(주변 한글 없음 — 문장 전체가 영타).
     * 반대편은 영어 기사(AG News test) 문장을 통째로 골랐을 때 칩이 뜨는 비율. 기록: 감지 99.5% / 오탐 0.005%.
     */
    @Test
    fun sentenceSelection() {
        var total = 0
        var hit = 0
        data("ratings_test.txt").forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size < 2 || parts[0] == "id") return@forEachLine
            var eligible = false
            val typed = parts[1].split(' ').joinToString(" ") { w ->
                if (w.none { it in '가'..'힣' } || w.any { it in 'a'..'z' || it in 'A'..'Z' }) w
                else HangulConverter.convertKorToEng(w).also { if (latinLetters(it) >= 3) eligible = true }
            }
            if (!eligible) return@forEachLine
            total++
            if (detected(typed)) hit++
        }
        var en = 0
        var enHit = 0
        val splitter = Regex("(?<=[.!?])\\s+")
        data("test.csv").forEachLine { line ->
            // "분류","제목","본문" — 제목·본문을 문장으로 나눈다(따옴표 안 쉼표는 대충 넘겨도 문장 단위엔 영향 없음)
            val body = line.split("\",\"").drop(1).joinToString(". ").trim('"')
            for (s in body.split(splitter)) {
                if (s.none { it in 'a'..'z' || it in 'A'..'Z' }) continue
                en++
                if (detected(s)) enHit++
            }
        }
        val rate = hit.toDouble() / total
        val fp = enHit.toDouble() / en
        println("EVAL 문장 선택: 한영타 문장 감지 ${"%.2f".format(rate * 100)}% ($total 문장) / 영어 기사 문장 오탐 ${"%.3f".format(fp * 100)}% ($enHit/$en)")
        assertTrue("문장 단위 감지율 하락: $rate", rate >= 0.99)
        assertTrue("영어 문장 오탐 상승: $fp", fp <= 0.0005)
    }

    /**
     * "Shift 증인" 원리로 라벨 없이 만든 오탐 집합: NSMC **train** 에서 Shift 가 무의미한 키에 대문자로
     * 쓰인 적이 있는 라틴 문자열(= 한글 영타일 수 없는 진짜 라틴 문자열, `SNS`/`CG`/`OST`)이 NSMC
     * **test** 에 어떤 모양(소문자 `sns` 포함)으로 나오든 한영타로 잡으면 오탐이다. 예전 모델은 `sns`
     * →눈, `snl`→뉘 를 잡았다. 한국어 문맥(리뷰 문장 속)을 켜고 잰다.
     */
    @Test
    fun witnessedLatinInKoreanText_notDetected() {
        val witnessed = HashSet<String>()
        val runRe = Regex("[A-Za-z]+")
        data("ratings_train.txt").forEachLine { line ->
            runRe.findAll(line).forEach { m ->
                val r = m.value
                val shiftless = r.withIndex().any { (i, c) -> c in 'A'..'Z' && c.lowercaseChar() !in "qwertop" && (i > 0 || r.length > 1 && r.all { it in 'A'..'Z' }) }
                if (shiftless) witnessed += r.lowercase()
            }
        }
        var total = 0
        val hits = HashMap<String, Int>()
        val tokenRe = Regex("[^\\s가-힣ㄱ-ㅣ]+")
        data("ratings_test.txt").forEachLine { line ->
            tokenRe.findAll(line).forEach { m ->
                val t = m.value
                val runs = runRe.findAll(t).map { it.value.lowercase() }.toList()
                if (runs.isEmpty() || runs.any { it !in witnessed } || latinLetters(t) < 3) return@forEach
                total++
                if (HangulConverter.analyze(t, koreanContext = true).confidence >= THRESHOLD) hits.merge(t, 1, Int::plus)
            }
        }
        val n = hits.values.sum()
        println("EVAL 증인 라틴 문자열 오탐(NSMC test, 한국어 문맥): $n/$total ${hits.entries.sortedByDescending { it.value }.take(10).map { "${it.key}×${it.value}" }}")
        assertTrue("증인 라틴 문자열 오탐 증가: $hits", n.toDouble() / total <= 0.005)
    }

    private companion object {
        /** 설정 기본값(신뢰도 임계값 70%). */
        const val THRESHOLD = 0.7f
    }
}
