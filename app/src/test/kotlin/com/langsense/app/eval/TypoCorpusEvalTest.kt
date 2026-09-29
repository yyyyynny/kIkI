package com.langsense.app.eval

import com.langsense.app.util.HangulConverter
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

    private companion object {
        /** 설정 기본값(신뢰도 임계값 70%). */
        const val THRESHOLD = 0.7f
    }
}
