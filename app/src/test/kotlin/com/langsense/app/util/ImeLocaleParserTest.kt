package com.langsense.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeLocaleParserTest {

    /** 삼성 패스·삼성 지문 창은 언어 팝업 출처가 아니다(지문 인증 때 가짜 전환의 원인이었다). */
    @Test
    fun popupPackage_onlySystemAndInputMethods() {
        assertTrue(ImeLocaleParser.isPopupPackage("android"))
        assertTrue(ImeLocaleParser.isPopupPackage("com.samsung.android.honeyboard"))
        assertTrue(ImeLocaleParser.isPopupPackage("com.google.android.inputmethod.latin"))
        assertFalse(ImeLocaleParser.isPopupPackage("com.samsung.android.samsungpass"))
        assertFalse(ImeLocaleParser.isPopupPackage("com.samsung.android.biometrics.app.setting"))
        assertFalse(ImeLocaleParser.isPopupPackage(null))
    }

    @Test
    fun popupText_parsesShortLanguageNames() {
        assertEquals(ImeLocaleParser.EN, ImeLocaleParser.parseFromSystemPopupText("English (US)"))
        assertEquals(ImeLocaleParser.KO, ImeLocaleParser.parseFromSystemPopupText("한국어"))
        assertNull(ImeLocaleParser.parseFromSystemPopupText("지문을 인식하세요"))
        assertTrue("English (US)".length <= ImeLocaleParser.POPUP_TEXT_MAX)
    }
}
