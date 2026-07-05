package com.langsense.app.util

import android.view.InputDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 외장 키보드 판정 순수 로직 검증 (추가 기능 2).
 * InputDevice 의 상수만 사용하므로 순수 JVM 단위 테스트로 검증 가능.
 */
class HardwareKeyboardDetectorTest {

    @Test
    fun realBluetoothKeyboard_qualifies() {
        // 가상 아님 + 알파벳 자판 + SOURCE_KEYBOARD → 외장 키보드로 인정.
        assertTrue(
            HardwareKeyboardDetector.qualifies(
                isVirtual = false,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                sources = InputDevice.SOURCE_KEYBOARD
            )
        )
    }

    @Test
    fun virtualKeyboard_doesNotQualify() {
        // 시스템 가상 키보드(소프트)는 제외.
        assertFalse(
            HardwareKeyboardDetector.qualifies(
                isVirtual = true,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                sources = InputDevice.SOURCE_KEYBOARD
            )
        )
    }

    @Test
    fun nonAlphabeticKeyboard_doesNotQualify() {
        // 게임패드 등 비알파벳 키보드는 제외(오탐 방지).
        assertFalse(
            HardwareKeyboardDetector.qualifies(
                isVirtual = false,
                keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC,
                sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD
            )
        )
    }

    @Test
    fun alphabeticButNoKeyboardSource_doesNotQualify() {
        // SOURCE_KEYBOARD 비트가 없으면 제외.
        assertFalse(
            HardwareKeyboardDetector.qualifies(
                isVirtual = false,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                sources = InputDevice.SOURCE_TOUCHSCREEN
            )
        )
    }
}
