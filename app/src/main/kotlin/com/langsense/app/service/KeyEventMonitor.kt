package com.langsense.app.service

import android.view.KeyEvent

/**
 * 포커스 없는 키 입력 경고 (Feature 3).
 *
 * 입력 포커스(편집 가능한 노드)가 없는 상태에서 "문자 입력 키"가 [thresholdProvider] 회
 * 이상 눌리면 [onWarn] 을 호출한다. 포커스를 얻으면 카운터를 초기화한다.
 *
 * onKeyEvent 는 절대 이벤트를 소비하지 않는다(항상 false).
 */
class KeyEventMonitor(
    private val enabledProvider: () -> Boolean,
    private val thresholdProvider: () -> Int,
    private val onWarn: () -> Unit
) {
    private var noFocusKeyCount = 0

    /**
     * @param event 키 이벤트
     * @param hasEditableFocus 현재 편집 가능한 노드에 포커스가 있는지
     * @return 항상 false (이벤트 소비 금지)
     */
    fun onKeyEvent(event: KeyEvent, hasEditableFocus: Boolean): Boolean {
        if (!enabledProvider()) {
            noFocusKeyCount = 0
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (!isTypingKey(event)) return false

        if (hasEditableFocus) {
            noFocusKeyCount = 0
        } else {
            noFocusKeyCount++
            if (noFocusKeyCount >= thresholdProvider()) {
                onWarn()
                noFocusKeyCount = 0
            }
        }
        return false
    }

    fun reset() {
        noFocusKeyCount = 0
    }

    /** 실제 문자를 만들어내는 키만 카운트 (Shift/Ctrl/방향키/볼륨 등 제외). */
    private fun isTypingKey(event: KeyEvent): Boolean {
        if (KeyEvent.isModifierKey(event.keyCode)) return false
        // unicodeChar != 0 이면 문자 생성 키 (스페이스 포함). 조합 문자 플래그는 제외.
        val unicode = event.unicodeChar and KeyEvent.COMBINING_ACCENT.inv()
        return unicode != 0
    }
}
