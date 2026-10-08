package com.langsense.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KeyTriggerDiagnostics.recentKeyNames 순수 함수 검증(Android 의존성 없음).
 * 전환 원인 진단(추가 기능 3)의 핵심 — 원형 버퍼에서 시간 창 안의 키만 순서·중복 제거해 뽑는 로직.
 */
class KeyTriggerDiagnosticsTest {

    private val name: (Int) -> String = { "K$it" }

    @Test
    fun recentKeyNames_emptyBuffer_returnsEmpty() {
        val codes = IntArray(4)
        val ats = LongArray(4)
        val result = KeyTriggerDiagnostics.recentKeyNames(codes, ats, writeIndex = 0, now = 1000L, keyName = name)
        assertTrue(result.isEmpty())
    }

    @Test
    fun recentKeyNames_singleEntryWithinWindow_returnsIt() {
        val codes = intArrayOf(0, 0, 62, 0) // 슬롯 2 에 keyCode 62(SPACE 가정)
        val ats = longArrayOf(0, 0, 900L, 0)
        val result = KeyTriggerDiagnostics.recentKeyNames(
            codes, ats, writeIndex = 3, now = 1000L, windowMs = 2000L, keyName = name
        )
        assertEquals(listOf("K62"), result)
    }

    @Test
    fun recentKeyNames_entryOutsideWindow_excluded() {
        val codes = intArrayOf(59) // SHIFT_LEFT 가정
        val ats = longArrayOf(100L) // now=5000, window=2000 -> 4900ms 전, 창 밖
        val result = KeyTriggerDiagnostics.recentKeyNames(
            codes, ats, writeIndex = 0, now = 5000L, windowMs = 2000L, keyName = name
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun recentKeyNames_multipleDistinctKeys_orderPreservedOldestFirst() {
        // writeIndex=0 이면 슬롯 0이 가장 오래된 값(다음에 덮어쓸 자리) — 버퍼가 가득 찬 상태.
        val codes = intArrayOf(59, 62, 29) // SHIFT, SPACE, A(가정) 순서로 눌림
        val ats = longArrayOf(700L, 800L, 900L)
        val result = KeyTriggerDiagnostics.recentKeyNames(
            codes, ats, writeIndex = 0, now = 1000L, windowMs = 2000L, keyName = name
        )
        assertEquals(listOf("K59", "K62", "K29"), result)
    }

    @Test
    fun recentKeyNames_duplicateKeyCode_collapsedOnce() {
        // 같은 키(예: 길게 눌려 반복 DOWN)가 여러 슬롯에 기록돼도 한 번만 나온다.
        val codes = intArrayOf(62, 62, 62)
        val ats = longArrayOf(700L, 800L, 900L)
        val result = KeyTriggerDiagnostics.recentKeyNames(
            codes, ats, writeIndex = 0, now = 1000L, windowMs = 2000L, keyName = name
        )
        assertEquals(listOf("K62"), result)
    }

    @Test
    fun recentKeyNames_ringWrapAround_readsOldestFirstAcrossWrap() {
        // 버퍼 크기 4, writeIndex=2 → 실제 순서는 인덱스 2,3,0,1 (오래된→최신).
        val codes = intArrayOf(/*0*/ 3, /*1*/ 4, /*2*/ 1, /*3*/ 2)
        val ats = longArrayOf(/*0*/ 300L, /*1*/ 400L, /*2*/ 100L, /*3*/ 200L)
        val result = KeyTriggerDiagnostics.recentKeyNames(
            codes, ats, writeIndex = 2, now = 500L, windowMs = 2000L, keyName = name
        )
        assertEquals(listOf("K1", "K2", "K3", "K4"), result)
    }

    @Test
    fun recentKeyNames_zeroTimestamp_treatedAsUnfilledSlot() {
        // at == 0L 인 슬롯(버퍼가 아직 다 안 찬 초기 상태)은 항상 제외된다 — now 가 아무리 작아도.
        val codes = intArrayOf(7, 0, 0, 0)
        val ats = longArrayOf(0L, 0L, 0L, 0L)
        val result = KeyTriggerDiagnostics.recentKeyNames(
            codes, ats, writeIndex = 1, now = 0L, windowMs = 2000L, keyName = name
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun describe_joinsWithPlus() {
        assertEquals("K1 + K2", KeyTriggerDiagnostics.describe(listOf("K1", "K2")))
    }

    @Test
    fun describe_empty_returnsNull() {
        assertEquals(null, KeyTriggerDiagnostics.describe(emptyList()))
    }

    // ── 키 없는 전환의 원인 분류(2026-10) ──

    /** 실기기 기록 그대로: 지문 창에서 영어로, 앱으로 돌아오며 한국어로 — 둘 다 시스템이 한 전환. 30초 지나면 사용자 전환. */
    @Test
    fun autoSwitch_systemScreenAndItsRevert() {
        val bio = "com.samsung.android.biometrics.app.setting"
        assertTrue(KeyTriggerDiagnostics.isAutoSwitch(bio, "en", 1000, 0, null))
        assertTrue(KeyTriggerDiagnostics.isAutoSwitch("viva.republica.toss", "ko", 5000, 1000, "ko"))
        assertEquals(false, KeyTriggerDiagnostics.isAutoSwitch("viva.republica.toss", "ko", 1000 + KeyTriggerDiagnostics.AUTO_REVERT_MS + 1, 1000, "ko"))
        assertEquals(false, KeyTriggerDiagnostics.isAutoSwitch("com.sec.android.app.launcher", "en", 5000, 1000, "ko"))
        assertTrue(KeyTriggerDiagnostics.isSystemAuthScreen("com.samsung.android.samsungpass"))
    }

    /** 원인 우선순위와 저장 형식 왕복. 예전 문장 형식 기록은 해석하지 않는다(null). */
    @Test
    fun cause_classificationAndRoundTrip() {
        val ctx = KeyTriggerDiagnostics.SwitchContext("setting", touchKeyboard = true, front = "com.sec.android.app.launcher")
        val back = KeyTriggerDiagnostics.SwitchContext.decode(ctx.encode())
        assertEquals(ctx, back)
        assertEquals(KeyTriggerDiagnostics.Cause.TOUCH_KEYBOARD, KeyTriggerDiagnostics.cause("", back))
        assertEquals(KeyTriggerDiagnostics.Cause.KEYS, KeyTriggerDiagnostics.cause("SHIFT_LEFT + SPACE", back))
        assertEquals(KeyTriggerDiagnostics.Cause.SYSTEM_SCREEN, KeyTriggerDiagnostics.cause("SPACE", back!!.copy(auto = true)))
        assertEquals(KeyTriggerDiagnostics.Cause.KEYBOARD_POPUP, KeyTriggerDiagnostics.cause("", KeyTriggerDiagnostics.SwitchContext("popup:android/7")))
        assertEquals(KeyTriggerDiagnostics.Cause.UNKNOWN, KeyTriggerDiagnostics.cause("", KeyTriggerDiagnostics.SwitchContext("setting")))
        assertEquals(null, KeyTriggerDiagnostics.SwitchContext.decode("근거: 설정값 · 앞 화면: x"))
    }
}
