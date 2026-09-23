package com.langsense.app.util

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.annotation.AttrRes
import com.langsense.app.R

/**
 * 앱 화면 테마 팔레트 — `attrs.xml` 의 색 역할을 한 덩어리로 묶은 것.
 *
 * 쓰임은 두 가지다.
 * 1. **사용자 지정 테마**: 사용자가 고른 4색(배경·카드·글자·강조)에서 나머지 역할을 [derive] 가
 *    만든다. 안드로이드 테마(style)는 컴파일 시점에 고정이라 런타임 색을 넣을 수 없으므로,
 *    [ThemeManager] 가 이 팔레트를 들고 있고 [themeColor] 가 테마 속성 대신 여기서 색을 준다.
 * 2. **테마 미리보기**: 테마 선택 카드가 각 테마의 실제 색을 보여 주도록 [forTheme] 로 읽는다.
 *
 * 역할 이름과 의미는 기본 테마들과 1:1 이다(`colors.xml` 참조) — 사용자 지정도 "기본 테마와
 * 같은 구조에 색만 다른 테마"가 되게 하려는 것.
 */
data class UiPalette(
    val bg: Int,
    val surface: Int,
    val onSurface: Int,
    val onSurfaceMuted: Int,
    val accent: Int,
    val onAccent: Int,
    val accentContainer: Int,
    val onAccentContainer: Int,
    val divider: Int,
    val muted: Int,
    val statusOk: Int,
    val statusOkContainer: Int,
    val statusNeed: Int,
    val statusNeedContainer: Int,
    val rippleOnAccent: Int,
    val rippleOnTonal: Int,
    /** 채움 컨테이너(필·스텝 칩·톤 버튼)의 테두리. 대부분 투명, 고대비/사이버펑크만 보인다. */
    val outline: Int,
    val isDark: Boolean,
) {

    /** 테마 속성 id → 이 팔레트의 색. 팔레트가 다루지 않는 속성이면 null. */
    fun colorFor(@AttrRes attr: Int): Int? = when (attr) {
        R.attr.uiBg -> bg
        R.attr.uiSurface -> surface
        R.attr.uiOnSurface -> onSurface
        R.attr.uiOnSurfaceMuted -> onSurfaceMuted
        R.attr.uiAccent -> accent
        R.attr.uiOnAccent -> onAccent
        R.attr.uiAccentContainer -> accentContainer
        R.attr.uiOnAccentContainer -> onAccentContainer
        R.attr.uiDivider -> divider
        R.attr.uiMuted -> muted
        R.attr.statusOk -> statusOk
        R.attr.statusOkContainer -> statusOkContainer
        R.attr.statusNeed -> statusNeed
        R.attr.statusNeedContainer -> statusNeedContainer
        R.attr.uiRippleOnAccent -> rippleOnAccent
        R.attr.uiRippleOnTonal -> rippleOnTonal
        R.attr.uiOutline -> outline
        else -> null
    }

    companion object {

        /** 명암비 기준(WCAG AA, 본문 글자). 기본 테마들의 colors.xml 주석과 같은 기준이다. */
        const val TEXT_CONTRAST = 4.5

        /** 이보다 어두운 배경(상대휘도)이면 다크 테마로 본다 — 흰 글자와 검은 글자의 명암비가 같아지는 지점 근처. */
        const val DARK_LUMINANCE = 0.18

        /**
         * 사용자가 고른 4색에서 전체 팔레트를 만든다 — 기본 테마들이 역할별로 색을 잡은 방식을
         * 그대로 따른다(순수 함수, JVM 테스트 대상).
         *
         * "고른 색을 그대로 바르기"만 하면 안 된다: 연한 노랑 강조를 흰 카드에 고르면 제목 글자가
         * 안 읽히고, 어두운 배경에 어두운 글자를 고르면 화면이 통째로 안 보인다. 그래서
         * - 글자·강조는 배경/카드 위에서 [TEXT_CONTRAST] 가 나올 때까지만 진하게(또는 밝게) 민다
         *   — 이미 충분하면 사용자가 고른 색 그대로다.
         * - 나머지(보조 글자, 구분선, 톤 컨테이너, 상태색, 리플)는 기본 테마와 같은 관계로 파생한다.
         * 배경이 어두우면(상대휘도 < [DARK_LUMINANCE]) 다크 테마로 보고 파생 방향을 뒤집는다.
         */
        fun derive(bg: Int, surfaceSeed: Int, text: Int, accentSeed: Int): UiPalette {
            val isDark = ColorMath.luminance(bg) < DARK_LUMINANCE
            val light = !isDark
            // 배경과 카드가 밝기 반대편이면(밝은 배경 + 검은 카드) 어떤 글자색도 둘 다에서 읽힐 수
            // 없다 — 카드를 배경 쪽 밝기로 끌어오되 고른 색의 기운만 살짝 남긴다. 그래도 중간 밝기라
            // 글자가 4.5 에 못 미칠 수 있어(무작위 검증에서 발견) 바탕 쪽으로 한 번 더 민다.
            val sided = if ((ColorMath.luminance(surfaceSeed) < DARK_LUMINANCE) != isDark) {
                ColorMath.mix(bg, surfaceSeed, 0.15)
            } else {
                surfaceSeed
            }
            val surface = ColorMath.readableGround(sided, light, TEXT_CONTRAST)
            val grounds = listOf(bg, surface)
            fun readable(fg: Int, on: List<Int>) = ColorMath.ensureContrast(fg, on, TEXT_CONTRAST, towardDark = light)
            val onSurface = readable(text, grounds)
            val muted = readable(ColorMath.mix(onSurface, surface, 0.38), grounds)
            var accent = readable(accentSeed, grounds)
            // 어두운 바탕에서 밝혀 올린 중간 밝기 강조색은 흰 글자·검은 글자 어느 쪽도 4.5 가 안
            // 나올 수 있다(최악 4.3) — 버튼 글자가 읽히도록 한 번 더 민다(어두운 바탕 대비는 오히려 커진다).
            if (ColorMath.contrast(ColorMath.readableOn(accent), accent) < TEXT_CONTRAST) {
                accent = ColorMath.ensureContrast(accent, listOf(ColorMath.NEAR_BLACK), TEXT_CONTRAST, towardDark = false)
            }
            val onAccent = ColorMath.readableOn(accent)
            val accentContainer = ColorMath.readableGround(
                ColorMath.mix(accent, surface, if (isDark) 0.72 else 0.87), light, TEXT_CONTRAST
            )
            val onAccentContainer = readable(accent, listOf(accentContainer))
            val divider = ColorMath.mix(surface, onSurface, if (isDark) 0.16 else 0.12)
            val okBase = if (isDark) 0xFF4ADE80.toInt() else 0xFF12703A.toInt()
            val needBase = if (isDark) 0xFFF87171.toInt() else 0xFFB91C1C.toInt()
            val okContainer = ColorMath.readableGround(
                ColorMath.mix(okBase, surface, if (isDark) 0.80 else 0.88), light, TEXT_CONTRAST
            )
            val needContainer = ColorMath.readableGround(
                ColorMath.mix(needBase, surface, if (isDark) 0.80 else 0.88), light, TEXT_CONTRAST
            )
            return UiPalette(
                bg = bg,
                surface = surface,
                onSurface = onSurface,
                onSurfaceMuted = muted,
                accent = accent,
                onAccent = onAccent,
                accentContainer = accentContainer,
                onAccentContainer = onAccentContainer,
                divider = divider,
                muted = muted,
                statusOk = readable(okBase, listOf(okContainer)),
                statusOkContainer = okContainer,
                statusNeed = readable(needBase, listOf(needContainer)),
                statusNeedContainer = needContainer,
                rippleOnAccent = ColorMath.withAlpha(onAccent, 0x33),
                rippleOnTonal = ColorMath.withAlpha(accent, 0x33),
                outline = 0,
                isDark = isDark,
            )
        }

        /**
         * 기본 테마 [themeId] 의 실제 팔레트(테마 선택 카드의 미리보기용). 시스템/라이트/다크는
         * 같은 스타일에 밝기만 다르므로, 밝기 한정자를 강제한 Context 위에서 속성을 읽는다.
         * 사용자 지정은 저장된 4색으로 [derive].
         */
        fun forTheme(context: Context, themeId: String): UiPalette {
            if (themeId == Prefs.THEME_CUSTOM) return Prefs(context).customPalette()
            val night = when (themeId) {
                Prefs.THEME_LIGHT, Prefs.THEME_BEIGE -> false
                Prefs.THEME_DARK, Prefs.THEME_CYBER, Prefs.THEME_HIGH_CONTRAST -> true
                else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            }
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), ThemeManager.styleFor(themeId))
            return fromTheme(themed, night)
        }

        /** [context] 테마의 속성에서 직접 읽는다([themeColor] 의 사용자 지정 우회 없이). */
        private fun fromTheme(context: Context, isDark: Boolean): UiPalette {
            val attrs = intArrayOf(
                R.attr.uiBg, R.attr.uiSurface, R.attr.uiOnSurface, R.attr.uiOnSurfaceMuted,
                R.attr.uiAccent, R.attr.uiOnAccent, R.attr.uiAccentContainer, R.attr.uiOnAccentContainer,
                R.attr.uiDivider, R.attr.uiMuted, R.attr.statusOk, R.attr.statusOkContainer,
                R.attr.statusNeed, R.attr.statusNeedContainer, R.attr.uiRippleOnAccent,
                R.attr.uiRippleOnTonal, R.attr.uiOutline,
            )
            // obtainStyledAttributes 는 속성 배열이 id 오름차순이어야 인덱스가 맞는다.
            val order = attrs.indices.sortedBy { attrs[it] }
            val sorted = IntArray(attrs.size) { attrs[order[it]] }
            val values = IntArray(attrs.size)
            val ta = context.obtainStyledAttributes(sorted)
            try {
                for (i in sorted.indices) values[order[i]] = ta.getColor(i, 0)
            } finally {
                ta.recycle()
            }
            return UiPalette(
                values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
                values[8], values[9], values[10], values[11], values[12], values[13], values[14],
                values[15], values[16], isDark,
            )
        }
    }
}
