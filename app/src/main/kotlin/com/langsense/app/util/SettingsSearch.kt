package com.langsense.app.util

/**
 * 설정 화면 검색 엔진(순수 로직 — 안드로이드 의존성 없음, JVM 테스트 대상).
 *
 * 항목마다 이름([Item.name])·경로([Item.path])·태그 여러 개([Item.tags])를 두고 다음을 지원한다:
 * - **여러 단어(AND)**: "배지 색" → 두 단어가 각각 이름/경로/태그 어딘가에 있으면 일치.
 * - **띄어쓰기·대소문자 무시**: "선택되지않음" = "선택되지 않음".
 * - **초성**: "ㅂㅈ" → 배지, "ㅌㅁ" → 테마.
 * - **한영타 보정**: 원래 검색어로 하나도 안 나오면 두벌식으로 바꿔 다시 찾는다("qowl" → 배지,
 *   "ㅇㅁ가" → dark). 이 앱이 고치려는 바로 그 실수를 검색칸에서도 받아 준다.
 * - **오타 1글자 허용**: 그래도 없으면 자모 1개 차이까지 허용("배치" → 배지).
 * 보정 단계는 **앞 단계가 0건일 때만** 쓴다 — 짧은 검색어를 한영타로 바꾸면 흔한 음절 하나("가")가
 * 되어 엉뚱한 항목이 섞이기 때문이다.
 *
 * 순위: 이름 > 태그 > 경로, 같은 칸 안에선 완전 일치 > 앞부분 일치 > 포함 > 초성.
 */
object SettingsSearch {

    /**
     * 검색 대상 1개. [anchors] 는 상세 화면에서 이 설정 줄을 찾아 스크롤할 때 쓰는 표시 문구 후보 —
     * 앞에서부터 화면에 있는 첫 번째로 간다(조건부로만 보이는 설정은 뒤에 대체 위치를 둔다).
     */
    data class Item(
        val name: String,
        val path: String,
        val group: String,
        val tags: List<String>,
        val anchors: List<String> = listOf(name),
    )

    /** [matchedTag] 는 이름이 아니라 태그로 걸렸을 때 그 태그(결과 줄에 "태그: …"로 보여 준다). */
    data class Hit(val item: Item, val score: Int, val matchedTag: String?)

    enum class Mode { DIRECT, KEYBOARD_FIX, TYPO }

    /** [corrected] 는 보정 검색이면 실제로 찾은 검색어(화면에 "…로 찾았어요" 안내용). */
    data class Result(val hits: List<Hit>, val mode: Mode, val corrected: String?)

    fun search(items: List<Item>, rawQuery: String): Result {
        val query = rawQuery.trim()
        if (query.isEmpty()) return Result(emptyList(), Mode.DIRECT, null)

        val direct = rank(items, query, typo = false)
        if (direct.isNotEmpty()) return Result(direct, Mode.DIRECT, null)

        // 한영타 보정: 라틴 글자가 있으면 한글로, 한글이 있으면 영문으로 바꿔 본다.
        for (fixed in keyboardVariants(query)) {
            val hits = rank(items, fixed, typo = false)
            if (hits.isNotEmpty()) return Result(hits, Mode.KEYBOARD_FIX, fixed)
        }

        val typo = rank(items, query, typo = true)
        return Result(typo, Mode.TYPO, null)
    }

    private fun keyboardVariants(query: String): List<String> {
        val out = ArrayList<String>(2)
        if (query.any { it in 'a'..'z' || it in 'A'..'Z' }) {
            HangulConverter.convertEngToKor(query).takeIf { it != query }?.let { out += it }
        }
        if (HangulConverter.containsHangul(query)) {
            HangulConverter.convertKorToEng(query).takeIf { it != query }?.let { out += it }
        }
        return out
    }

    private fun rank(items: List<Item>, query: String, typo: Boolean): List<Hit> {
        val terms = query.split(Regex("\\s+")).map(::normalize).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return items.mapNotNull { item ->
            var total = 0
            var tag: String? = null
            for (term in terms) {
                val m = bestMatch(item, term, typo) ?: return@mapNotNull null
                total += m.first
                if (m.second != null && tag == null) tag = m.second
            }
            Hit(item, total, tag)
        }.sortedByDescending { it.score } // 안정 정렬 — 동점이면 원래 순서(화면 배치 순서)
    }

    /** 단어 하나가 항목에 걸리는 가장 좋은 방식: (점수, 태그로 걸렸으면 그 태그). */
    private fun bestMatch(item: Item, term: String, typo: Boolean): Pair<Int, String?>? {
        var best: Pair<Int, String?>? = null
        fun consider(score: Int, tag: String?) {
            if (score > 0 && (best == null || score > best!!.first)) best = score to tag
        }
        consider(fieldScore(item.name, term, typo, NAME_WEIGHTS), null)
        for (t in item.tags) consider(fieldScore(t, term, typo, TAG_WEIGHTS), t)
        consider(fieldScore(item.path, term, typo, PATH_WEIGHTS), null)
        return best
    }

    private class Weights(val exact: Int, val prefix: Int, val contains: Int, val choseong: Int, val typo: Int)

    private val NAME_WEIGHTS = Weights(100, 80, 60, 40, 30)
    private val TAG_WEIGHTS = Weights(55, 45, 35, 25, 20)
    private val PATH_WEIGHTS = Weights(20, 20, 15, 10, 0)

    private fun fieldScore(field: String, term: String, typo: Boolean, w: Weights): Int {
        val f = normalize(field)
        if (f.isEmpty()) return 0
        if (typo) {
            // 오타 단계: 단어 단위로 자모 편집거리 1 이내(짧은 단어는 오탐이 많아 2음절 이상만).
            if (term.length < 2) return 0
            val t = jamo(term)
            val words = field.split(Regex("[\\s·/()\\-,]+")).map(::normalize).filter { it.length >= 2 }
            return if (words.any { editDistanceAtMostOne(jamo(it), t) }) w.typo else 0
        }
        return when {
            f == term -> w.exact
            f.startsWith(term) -> w.prefix
            f.contains(term) -> w.contains
            isChoseongQuery(term) && choseong(f).contains(term) -> w.choseong
            else -> 0
        }
    }

    /** 소문자 + 공백·가운뎃점·괄호 등 구분 기호 제거(띄어쓰기 무시). */
    fun normalize(s: String): String = buildString(s.length) {
        for (c in s) {
            if (c.isWhitespace() || c in "·()[]{}-_/.,:;!?\"'“”‘’") continue
            append(c.lowercaseChar())
        }
    }

    private const val HANGUL_BASE = 0xAC00
    private const val HANGUL_LAST = 0xD7A3
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private val JUNG = listOf("ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅗㅏ", "ㅗㅐ", "ㅗㅣ", "ㅛ",
        "ㅜ", "ㅜㅓ", "ㅜㅔ", "ㅜㅣ", "ㅠ", "ㅡ", "ㅡㅣ", "ㅣ")
    private val JONG = listOf("", "ㄱ", "ㄲ", "ㄱㅅ", "ㄴ", "ㄴㅈ", "ㄴㅎ", "ㄷ", "ㄹ", "ㄹㄱ", "ㄹㅁ", "ㄹㅂ", "ㄹㅅ",
        "ㄹㅌ", "ㄹㅍ", "ㄹㅎ", "ㅁ", "ㅂ", "ㅂㅅ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")

    /** 검색어가 전부 초성용 자음(ㄱ~ㅎ)이면 초성 검색으로 본다. */
    private fun isChoseongQuery(term: String) = term.isNotEmpty() && term.all { it in CHO }

    /** 완성형 음절은 초성으로, 그 외 글자는 그대로. */
    fun choseong(s: String): String = buildString(s.length) {
        for (c in s) {
            val code = c.code
            if (code in HANGUL_BASE..HANGUL_LAST) append(CHO[(code - HANGUL_BASE) / 588]) else append(c)
        }
    }

    /** 완성형 음절을 자모열로 풀어 쓴다(겹모음·겹받침도 낱자로) — 오타 1글자 판정용. */
    fun jamo(s: String): String = buildString(s.length * 3) {
        for (c in s) {
            val code = c.code
            if (code in HANGUL_BASE..HANGUL_LAST) {
                val idx = code - HANGUL_BASE
                append(CHO[idx / 588])
                append(JUNG[(idx % 588) / 28])
                append(JONG[idx % 28])
            } else {
                append(c)
            }
        }
    }

    /** 두 문자열의 편집거리(삽입·삭제·치환)가 1 이하인지. */
    fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (a == b) return true
        val (s, l) = if (a.length <= b.length) a to b else b to a
        if (l.length - s.length > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < s.length && j < l.length) {
            if (s[i] == l[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (s.length == l.length) i++ // 치환
            j++ // 긴 쪽에서 하나 건너뜀(삽입/삭제)
        }
        return edits + (l.length - j) + (s.length - i) <= 1
    }
}
