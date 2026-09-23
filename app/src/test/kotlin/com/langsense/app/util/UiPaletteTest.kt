package com.langsense.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UiPaletteTest {

    private fun hex(h: String) = ColorMath.parseHex(h)!!

    /** 이미 잘 읽히는 색은 사용자가 고른 그대로 둬야 한다(필요 이상으로 바꾸지 않기). */
    @Test
    fun derive_keepsReadableSeedsUnchanged() {
        val p = UiPalette.derive(hex("#F4F6FA"), hex("#FFFFFF"), hex("#1A1C20"), hex("#2563EB"))
        assertFalse(p.isDark)
        assertEquals("#FFFFFF", ColorMath.toHex(p.surface))
        assertEquals("#1A1C20", ColorMath.toHex(p.onSurface))
        assertEquals("#2563EB", ColorMath.toHex(p.accent))
    }

    /** 흰 바탕에 연회색 글자·노랑 강조처럼 안 읽히는 조합은 읽힐 만큼만 진하게 보정된다. */
    @Test
    fun derive_fixesUnreadableSeeds() {
        val p = UiPalette.derive(hex("#FFFFFF"), hex("#FFFFFF"), hex("#BBBBBB"), hex("#FFEB3B"))
        assertTrue(ColorMath.contrast(p.onSurface, p.surface) >= 4.5)
        assertTrue(ColorMath.contrast(p.accent, p.surface) >= 4.5)
    }

    /**
     * 어떤 4색을 골라도 글자로 쓰이는 모든 쌍이 WCAG AA(4.5:1)를 넘어야 한다 — 무작위 2천 조합.
     * 기본 테마들의 colors.xml 주석이 검증하는 것과 같은 쌍들이다.
     */
    @Test
    fun derive_anySeeds_allTextPairsReadable() {
        val rnd = Random(20260923)
        fun c() = ColorMath.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
        repeat(2000) {
            val p = UiPalette.derive(c(), c(), c(), c())
            val pairs = listOf(
                "onSurface/surface" to ColorMath.contrast(p.onSurface, p.surface),
                "onSurface/bg" to ColorMath.contrast(p.onSurface, p.bg),
                "muted/surface" to ColorMath.contrast(p.onSurfaceMuted, p.surface),
                "muted/bg" to ColorMath.contrast(p.onSurfaceMuted, p.bg),
                "accent/surface" to ColorMath.contrast(p.accent, p.surface),
                "accent/bg" to ColorMath.contrast(p.accent, p.bg),
                "onAccent/accent" to ColorMath.contrast(p.onAccent, p.accent),
                "onAccentContainer/container" to ColorMath.contrast(p.onAccentContainer, p.accentContainer),
                "ok/container" to ColorMath.contrast(p.statusOk, p.statusOkContainer),
                "need/container" to ColorMath.contrast(p.statusNeed, p.statusNeedContainer),
            )
            for ((name, v) in pairs) assertTrue("$name=$v in $p", v >= 4.5)
        }
    }

    @Test
    fun colorMath_contrastMatchesWcagReference() {
        assertEquals(21.0, ColorMath.contrast(ColorMath.BLACK, ColorMath.WHITE), 0.01)
        // colors.xml 주석의 수치와 같은 공식인지: 라이트 테마 status_ok on container = 5.45:1
        assertEquals(5.45, ColorMath.contrast(hex("#12703A"), hex("#E4F5EA")), 0.01)
    }

    @Test
    fun colorMath_hexRoundTrip() {
        assertEquals("#2563EB", ColorMath.toHex(hex("#2563eb")))
        assertEquals(null, ColorMath.parseHex("#12345"))
        assertEquals(null, ColorMath.parseHex("zzzzzz"))
    }
}
