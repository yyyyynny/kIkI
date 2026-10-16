package com.langsense.app.util

/**
 * 테마 팔레트 계산용 색 연산(순수 Kotlin — `android.graphics.Color` 는 JVM 단위 테스트에서
 * 동작하지 않아 비트 연산으로 직접 구현한다). 색은 전부 ARGB Int.
 *
 * 명암비는 WCAG 2.x 정의(상대휘도, (L1+0.05)/(L2+0.05))를 그대로 쓴다 — colors.xml 주석의
 * 수치들과 같은 공식이다.
 */
object ColorMath {

    fun alpha(c: Int) = (c ushr 24) and 0xFF
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun withAlpha(c: Int, a: Int): Int = (a.coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)

    /** [a] 에서 [b] 쪽으로 t(0..1)만큼 섞은 불투명 색. */
    fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(x: Int, y: Int) = Math.round(x + (y - x) * t).toInt()
        return rgb(ch(red(a), red(b)), ch(green(a), green(b)), ch(blue(a), blue(b)))
    }

    /** WCAG 상대휘도(0 = 검정, 1 = 흰색). */
    fun luminance(c: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(red(c)) + 0.7152 * lin(green(c)) + 0.0722 * lin(blue(c))
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * [fg] 를 [backgrounds] 전부에 대해 명암비 [target] 이상이 될 때까지 밝히거나 어둡게 한다
     * (배경이 밝으면 검정 쪽, 어두우면 흰색 쪽). 이미 충분하면 그대로 돌려준다 — 사용자가 고른
     * 색을 필요 이상으로 바꾸지 않기 위해 조금씩(5%) 움직이며 처음 통과하는 값을 쓴다.
     */
    fun ensureContrast(fg: Int, backgrounds: List<Int>, target: Double, towardDark: Boolean? = null): Int {
        fun ok(c: Int) = backgrounds.all { contrast(c, it) >= target }
        if (ok(fg)) return fg
        val dark = towardDark ?: (backgrounds.map { luminance(it) }.average() > 0.18)
        val pole = if (dark) BLACK else WHITE
        for (step in 1..20) {
            val c = mix(fg, pole, step * 0.05)
            if (ok(c)) return c
        }
        return pole
    }

    /**
     * 바탕으로 쓰일 색 [ground] 를, 그 위의 글자가 [target] 을 낼 수 있을 때까지 민다 — 밝은 테마면
     * 흰색 쪽(검은 글자가 읽히게), 어두운 테마면 검정 쪽. 글자색만 조정해서는 해결이 안 되는 경우
     * (바탕이 중간 밝기라 흰 글자도 검은 글자도 4.5 에 못 미침)를 위한 것이다.
     */
    fun readableGround(ground: Int, lightTheme: Boolean, target: Double): Int {
        val textPole = if (lightTheme) BLACK else WHITE
        if (contrast(textPole, ground) >= target) return ground
        val pole = if (lightTheme) WHITE else BLACK
        for (step in 1..20) {
            val c = mix(ground, pole, step * 0.05)
            if (contrast(textPole, c) >= target) return c
        }
        return pole
    }

    /** [bg] 위에 올릴 글자색 — 거의 흰색/거의 검정 중 명암비가 큰 쪽. */
    fun readableOn(bg: Int): Int =
        if (contrast(WHITE, bg) >= contrast(NEAR_BLACK, bg)) WHITE else NEAR_BLACK

    /** "#RRGGBB" → 불투명 ARGB. 형식이 틀리면 null. */
    fun parseHex(hex: String?): Int? {
        val h = hex?.trim()?.removePrefix("#") ?: return null
        if (h.length != 6 || h.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) return null
        return (0xFF shl 24) or h.toInt(16)
    }

    fun toHex(c: Int): String = "#%06X".format(c and 0xFFFFFF)

    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val NEAR_BLACK = 0xFF111317.toInt()
}
