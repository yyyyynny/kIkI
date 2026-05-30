package com.langsense.app.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.langsense.app.util.HangulConverter

/**
 * 드래그 선택 + 한영타 판정 (Feature 4).
 *
 * TYPE_VIEW_TEXT_SELECTION_CHANGED 이벤트에서 선택 텍스트를 추출하고
 * [HangulConverter.detectEnglishToKorean] 신뢰도가 임계값 이상이면 [onDetected] 호출.
 */
class TextSelectionMonitor(
    private val enabledProvider: () -> Boolean,
    private val confidencePercentProvider: () -> Int,
    private val onDetected: (
        node: AccessibilityNodeInfo,
        fullText: String,
        selStart: Int,
        selEnd: Int,
        converted: String
    ) -> Unit
) {
    fun onSelectionChanged(event: AccessibilityEvent) {
        if (!enabledProvider()) return
        val node = event.source ?: return
        val text = node.text?.toString() ?: return
        if (text.isEmpty()) return

        var selStart = node.textSelectionStart
        var selEnd = node.textSelectionEnd
        if (selStart > selEnd) {
            val t = selStart; selStart = selEnd; selEnd = t
        }
        if (selStart < 0 || selEnd <= selStart || selEnd > text.length) return

        val selected = text.substring(selStart, selEnd)
        if (selected.isBlank() || selected.length < MIN_SELECTION) return

        val confidence = HangulConverter.detectEnglishToKorean(selected)
        if (confidence * 100f < confidencePercentProvider()) return

        val converted = HangulConverter.convertEngToKor(selected)
        if (converted == selected) return // 변환 결과가 동일하면 의미 없음

        onDetected(node, text, selStart, selEnd, converted)
    }

    companion object {
        const val MIN_SELECTION = 2
    }
}
