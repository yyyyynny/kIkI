package com.langsense.app.service

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import com.langsense.app.util.HangulConverter

/**
 * 드래그 선택 + 한영타 판정 (Feature 4).
 *
 * 서비스가 TYPE_VIEW_TEXT_SELECTION_CHANGED 이벤트의 소스 노드를 **한 번만** 얻어 넘긴다
 * (이전엔 입력 실착 확인과 여기서 각각 `event.source` 를 호출해 이벤트당 노드 IPC 가 2회였다).
 * 선택 구간이 한영타면 [onDetected] 로 노드 소유권을 넘기고 true 를 반환한다 — 그 외에는 노드를
 * 건드리지 않으며(recycle 은 호출자 책임) false.
 *
 * 비용 원칙: 선택 변경은 드래그 중 초당 수십 회 오고 전부 메인 스레드다. 전체 텍스트를 복사하지
 * 않고(`CharSequence` 로 받아 선택 구간만 `subSequence`), 전체 텍스트 `toString()` 은 실제로 칩을
 * 띄우는 순간에만 한다. 방향 판정([pickAnalysis])은 대부분 [HangulConverter.analyze] 1회로 끝나고,
 * 정방향이 약하고 한글이 섞여 있을 때만 [HangulConverter.analyzeReverse] 도 계산한다.
 */
class TextSelectionMonitor(
    private val confidencePercentProvider: () -> Int,
    /** 설정의 한영타 예외 단어(소문자) — 판정·교체에서 제외. */
    private val exceptionWordsProvider: () -> Set<String> = { emptySet() },
    private val onDetected: (
        node: AccessibilityNodeInfo,
        fullText: String,
        selStart: Int,
        selEnd: Int,
        converted: String
    ) -> Unit
) {
    /** @return true 면 [onDetected] 로 [node] 소유권을 넘겼다(호출자는 recycle 하지 않는다). */
    fun onSelectionChanged(node: AccessibilityNodeInfo): Boolean {
        val text: CharSequence = runCatching { node.text }.getOrNull() ?: return false
        if (text.isEmpty()) return false

        var selStart = runCatching { node.textSelectionStart }.getOrDefault(-1)
        var selEnd = runCatching { node.textSelectionEnd }.getOrDefault(-1)
        if (selStart > selEnd) {
            val t = selStart; selStart = selEnd; selEnd = t
        }
        if (selStart < 0 || selEnd <= selStart || selEnd > text.length) return false

        // 전체 선택(Ctrl+A) 같은 대량 선택은 한영타 교정 대상이 아니다. 상한이 없으면 메인 스레드에서
        // 수만 자를 변환하고, 교체 시 전체 텍스트를 Binder 로 넘겨 트랜잭션 한도(≈1MB)를 넘길 수 있다.
        val len = selEnd - selStart
        if (len < MIN_SELECTION || len > MAX_SELECTION) return false

        val selected = text.subSequence(selStart, selEnd).toString()
        if (selected.isBlank()) return false

        val threshold = confidencePercentProvider() / 100f
        val koreanContext = hasHangulNear(text, selStart, selEnd)
        // 편집 가능 여부는 노드에 이미 담긴 값이라 IPC 가 없다. 읽기 전용 글(남이 쓴 기사·메시지)은 영어 이웃도 증거로 쓴다.
        val editable = runCatching { node.isEditable }.getOrDefault(false)
        val inputType = if (editable) runCatching { node.inputType }.getOrDefault(0) else 0
        val context = selectionContext(text, selStart, selEnd, editable, collectNeighbors = !koreanContext, autocap = mayAutocap(editable, inputType))
        val analysis = pickAnalysis(selected, threshold, koreanContext, exceptionWordsProvider(), context)
        if (analysis.confidence < threshold) return false
        if (analysis.converted == selected) return false // 변환 결과가 동일하면 의미 없음

        onDetected(node, text.toString(), selStart, selEnd, analysis.converted)
        return true
    }

    companion object {
        const val MIN_SELECTION = 2

        /** 한영타 교정이 의미 있는 선택 길이 상한(문자). 단어~짧은 문장 범위를 넉넉히 덮는다. */
        const val MAX_SELECTION = 200

        /** 주변 문맥을 볼 범위(선택 앞뒤 글자 수) — 한 문장 남짓. */
        const val CONTEXT_WINDOW = 40

        /**
         * 선택 앞뒤 [CONTEXT_WINDOW] 글자(선택 포함) 안에 한글이 있는가 = 한국어 문서 속 선택인가.
         * 그러면 같은 `wha` 라도 영어보다 한영타(좀)일 가능성이 높다([HangulConverter.analyze] 의
         * koreanContext). 전체 텍스트를 복사하지 않고 [text] 에서 좁은 범위만 한 글자씩 본다.
         * 순수 함수(JVM 테스트 대상).
         */
        fun hasHangulNear(text: CharSequence, selStart: Int, selEnd: Int): Boolean {
            val from = (selStart - CONTEXT_WINDOW).coerceAtLeast(0)
            val to = (selEnd + CONTEXT_WINDOW).coerceAtMost(text.length)
            for (i in from until to) {
                val c = text[i].code
                if (c in 0xAC00..0xD7A3 || c in 0x3131..0x318E) return true
            }
            return false
        }

        /**
         * 선택 주변 정보([HangulConverter.SelectionContext])를 만든다 — 순수 함수(JVM 테스트 대상).
         * - 선택 바로 앞의 공백·탭 아닌 글자(문장 첫머리 판정용, 자동 대문자 가설).
         * - [collectNeighbors] 면 앞뒤 [CONTEXT_WINDOW] 글자 창과 겹치는 라틴 토큰(선택과 겹치는 것 제외) — 이웃 증거.
         *   창 경계에 걸친 토큰은 최대 [MAX_NEIGHBOR_TOKEN] 글자까지 넓혀 온전히 읽는다. 주변에 한글이 있으면
         *   한국어 문맥 판정이 이미 훨씬 강해 이웃은 쓰지 않으므로 모으지 않는다(저사양).
         */
        fun selectionContext(
            text: CharSequence,
            selStart: Int,
            selEnd: Int,
            editable: Boolean,
            collectNeighbors: Boolean = true,
            autocap: Boolean = true,
        ): HangulConverter.SelectionContext {
            var i = selStart - 1
            while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
            val before = if (i >= 0) text[i] else null
            if (!collectNeighbors) return HangulConverter.SelectionContext(emptyList(), before, editable, autocap)
            val lo = (selStart - CONTEXT_WINDOW).coerceAtLeast(0)
            val hi = (selEnd + CONTEXT_WINDOW).coerceAtMost(text.length)
            var from = lo
            var guard = 0
            while (from > 0 && !HangulConverter.isTokenBoundary(text[from - 1]) && guard < MAX_NEIGHBOR_TOKEN) {
                from--
                guard++
            }
            var to = hi
            guard = 0
            while (to < text.length && !HangulConverter.isTokenBoundary(text[to]) && guard < MAX_NEIGHBOR_TOKEN) {
                to++
                guard++
            }
            val neighbors = ArrayList<String>()
            var s = -1
            var hasLatin = false
            for (p in from..to) {
                val c = if (p < to) text[p] else ' '
                if (HangulConverter.isTokenBoundary(c)) {
                    if (s >= 0) {
                        val overlapsSelection = s < selEnd && p > selStart
                        if (hasLatin && !overlapsSelection && p > lo && s < hi) neighbors.add(text.subSequence(s, p).toString())
                        s = -1
                        hasLatin = false
                    }
                } else {
                    if (s < 0) s = p
                    if (c in 'a'..'z' || c in 'A'..'Z') hasLatin = true
                }
            }
            return HangulConverter.SelectionContext(neighbors, before, editable, autocap)
        }

        /**
         * 이 칸에 문장 첫 글자 자동 대문자가 붙을 수 있는가. 편집 칸은 inputType 이 대문자를 요청할 때만(안드로이드
         * TextKeyListener 규칙, 알 수 없으면(0) 가능으로 본다). 읽기 전용 글은 쓴 사람 기기에서 붙었을 수 있어 늘 가능.
         */
        fun mayAutocap(editable: Boolean, inputType: Int): Boolean {
            if (!editable || inputType == 0) return true
            val caps = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            return inputType and caps != 0
        }

        /** 창 경계에 걸친 이웃 토큰을 넓혀 읽는 최대 글자 수. */
        const val MAX_NEIGHBOR_TOKEN = 40

        /**
         * 정방향/역방향 중 최종 판정을 고르는 순수 함수(안드로이드 의존성 없음 — 단위 테스트 대상).
         *
         * [koreanContext] 는 선택 주변에 한글이 있는지([hasHangulNear]) — 선택 안에 한글이 있어도 같은
         * 뜻이라 여기서 함께 켠다. [exceptions] 는 사용자 예외 단어.
         *
         * 정방향(영→한, [HangulConverter.analyze])을 먼저 본다 — 이 함수는 이미 섞인 텍스트를 잘
         * 처리한다(`"저 dkssud"` 를 선택하면 이미 있는 한글 "저"는 그대로 두고 "dkssud" 만 조합
         * 판정해 `"저 안녕"` 을 제안). 정방향이 [threshold] 에 못 미치고 선택에 한글이 섞여 있을
         * 때만 역방향(한→영, [HangulConverter.analyzeReverse])도 추가로 계산해 더 나은 쪽을 쓴다 —
         * 대부분의 실제 선택(순수 한영타/순수 정상 한글)은 정방향 1회로 끝나 저사양 원칙(이중 변환
         * 방지)을 지키면서, `god`→`행` 처럼 정방향이 못 잡는 순수 역방향 케이스도 커버한다.
         *
         * ⚠️ 한때 "선택에 한글이 있으면 무조건 역방향만, 없으면 정방향만" 으로 양자택일했는데,
         * 이러면 `"저 dkssud"` 처럼 이미 정상 한글과 진짜 한영타가 섞인 선택에서 역방향(사전
         * 완전일치라는 엄격한 조건)만 타 감지가 아예 안 되는 회귀였다(2026-09 발견·수정).
         */
        fun pickAnalysis(
            selected: String,
            threshold: Float,
            koreanContext: Boolean = false,
            exceptions: Set<String> = emptySet(),
            context: HangulConverter.SelectionContext? = null,
        ): HangulConverter.Analysis {
            val forward = HangulConverter.analyze(selected, koreanContext || HangulConverter.containsHangul(selected), exceptions, context)
            if (forward.confidence >= threshold || !HangulConverter.containsHangul(selected)) return forward
            val reverse = HangulConverter.analyzeReverse(selected)
            return if (reverse.confidence > forward.confidence) reverse else forward
        }
    }
}
