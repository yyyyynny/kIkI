package com.langsense.app.service

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * 포커스 없는 키 입력 경고 (Feature 3).
 *
 * 입력 포커스(편집 가능한 노드)가 없는 상태에서 "문자 입력 키"가 연속 [thresholdProvider] 회
 * 이상 눌리면 [onWarn] 을 호출한다.
 *
 * ### 오발동 방지 (Bug 1)
 * 블루투스 키보드로 정상 타이핑 중에도 `findFocus(FOCUS_INPUT)` 가 순간적으로 null 을
 * 반환할 때가 있어(렌더 타이밍), 이를 그대로 믿으면 경고가 도배된다. 다음으로 방어한다:
 *  - **단축키 제외**: Ctrl/Alt/Meta 조합은 타이핑이 아니므로 무시.
 *  - **재확인(grace)**: 포커스가 없다고 나오면 [focusReProbe] 로 한 번 더 확인하여
 *    일시적 null 을 거른다. 한 번이라도 편집 포커스가 잡히면 카운터를 즉시 초기화.
 *  - **경고 쿨다운**: 경고를 띄운 직후 [WARN_COOLDOWN_MS] 동안은 다시 띄우지 않아 도배 방지.
 *
 * ### 저사양 최적화 (Bug 1)
 * 포커스 조회([focusProbe]/[focusReProbe])는 접근성 노드 트리를 훑는 비싼 작업이라 키마다 미리
 * 계산하지 않는다. **기능 ON + ACTION_DOWN + 비반복 + 실제 문자 키** 게이트를 모두 통과한 뒤에만
 * 1차(저비용) 조회를 하고, 그래도 없을 때만 2차(전체 윈도우 순회) 재확인을 한다. 모디파이어·반복·
 * 단축키·기능 OFF 상황에서는 트리를 전혀 건드리지 않는다.
 *
 * onKeyEvent 는 절대 이벤트를 소비하지 않는다(항상 false).
 */
class KeyEventMonitor(
    private val enabledProvider: () -> Boolean,
    private val thresholdProvider: () -> Int,
    private val onWarn: () -> Unit
) {
    private var noFocusKeyCount = 0
    private var lastWarnAt = 0L

    /**
     * @param event 키 이벤트
     * @param focusProbe 1차(저비용) 편집 포커스 조회 — 게이트 통과 후에만 호출(지연 평가)
     * @param focusReProbe 1차가 없을 때만 호출하는 2차(전체 윈도우) 재확인(일시적 null 방어)
     * @return 항상 false (이벤트 소비 금지)
     */
    fun onKeyEvent(
        event: KeyEvent,
        focusProbe: () -> Boolean,
        focusReProbe: () -> Boolean
    ): Boolean {
        if (!enabledProvider()) {
            noFocusKeyCount = 0
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return false // 길게 눌러 반복되는 이벤트는 1회로만 취급
        if (!isTypingKey(event)) return false

        // 여기까지 통과한 "문자 입력 키"에 한해서만 포커스를 조회한다(저사양 최적화).
        // 1차가 없을 때만 2차 재확인이 돌아 일시적 null 을 거른다(단락 평가).
        val focused = focusProbe() || focusReProbe()
        if (focused) {
            noFocusKeyCount = 0
            return false
        }

        noFocusKeyCount++
        if (noFocusKeyCount >= thresholdProvider()) {
            val now = SystemClock.uptimeMillis()
            if (now - lastWarnAt >= WARN_COOLDOWN_MS) {
                onWarn()
                lastWarnAt = now
            }
            noFocusKeyCount = 0
        }
        return false
    }

    /** 포커스 획득 등으로 카운터를 초기화. */
    fun reset() {
        noFocusKeyCount = 0
    }

    /**
     * 실제 문자를 만들어내는 키만 카운트.
     * 제외: Shift/Ctrl/Alt/Meta 등 모디파이어, Ctrl/Alt/Meta 조합 단축키, dead key.
     */
    private fun isTypingKey(event: KeyEvent): Boolean {
        if (KeyEvent.isModifierKey(event.keyCode)) return false
        // Ctrl/Alt/Meta 가 눌린 조합은 단축키이지 타이핑이 아니다.
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        // unicodeChar != 0 이면 문자 생성 키 (스페이스 포함). 조합(dead key) 플래그는 제외.
        val unicode = event.unicodeChar and KeyCharacterMap.COMBINING_ACCENT.inv()
        return unicode != 0
    }

    companion object {
        /** 경고 1회 후 재경고 억제 시간. */
        const val WARN_COOLDOWN_MS = 2500L
    }
}
