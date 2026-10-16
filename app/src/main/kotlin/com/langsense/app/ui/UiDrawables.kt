package com.langsense.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.langsense.app.R
import com.langsense.app.util.themeColor

/**
 * 앱 화면의 공용 배경(카드·필·스텝 칩·버튼)을 **코드로** 만든다 — `res/drawable/bg_*.xml` 과
 * 같은 모양·치수다.
 *
 * XML 드로어블이 있는데 왜 또: XML 의 `?attr/...` 는 스타일에서만 색을 읽는데, 사용자 지정 테마의
 * 색은 스타일이 아니라 런타임 팔레트(ThemeManager.customPalette)에 있다. 여기서는 색을
 * [themeColor] 로 읽으므로 기본 테마에선 XML 과 같은 결과, 사용자 지정에선 고른 색이 나온다.
 * 기본/사용자 지정 모두 이 한 경로를 타게 해서 "사용자 지정만 다르게 그려지는" 틈을 없앤다.
 */
object UiDrawables {

    fun card(ctx: Context): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(ctx, 16f)
        setColor(ctx.themeColor(R.attr.uiSurface))
        setStroke(strokeWidth(ctx), ctx.themeColor(R.attr.uiDivider))
    }

    /** 상태 필 — 채움색만 상태별로 다르다. 테두리는 [R.attr.uiOutline](대부분 투명). */
    fun pill(ctx: Context, fill: Int): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(ctx, 999f)
        setColor(fill)
        setStroke(strokeWidth(ctx), ctx.themeColor(R.attr.uiOutline))
    }

    fun stepChip(ctx: Context): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ctx.themeColor(R.attr.uiAccentContainer))
        setStroke(strokeWidth(ctx), ctx.themeColor(R.attr.uiOutline))
    }

    fun buttonPrimary(ctx: Context): Drawable = RippleDrawable(
        ColorStateList.valueOf(ctx.themeColor(R.attr.uiRippleOnAccent)),
        GradientDrawable().apply {
            cornerRadius = dp(ctx, 12f)
            setColor(ctx.themeColor(R.attr.uiAccent))
        },
        null
    )

    fun buttonTonal(ctx: Context): Drawable = RippleDrawable(
        ColorStateList.valueOf(ctx.themeColor(R.attr.uiRippleOnTonal)),
        GradientDrawable().apply {
            cornerRadius = dp(ctx, 12f)
            setColor(ctx.themeColor(R.attr.uiAccentContainer))
            setStroke(strokeWidth(ctx), ctx.themeColor(R.attr.uiOutline))
        },
        null
    )

    /**
     * 레이아웃 XML 의 `android:tag="bg=card fg=onSurface"` 표시를 읽어 색을 다시 입힌다.
     * `fg` = 글자색 역할, `bg` = 위 배경 종류. 태그가 없는 뷰는 건드리지 않는다.
     */
    fun bindTags(root: View) {
        val ctx = root.context
        (root.tag as? String)?.split(' ')?.forEach { spec ->
            val (key, value) = spec.split('=').takeIf { it.size == 2 } ?: return@forEach
            when (key) {
                "fg" -> fgAttr(value)?.let { (root as? TextView)?.setTextColor(ctx.themeColor(it)) }
                "bg" -> when (value) {
                    "card" -> card(ctx)
                    "stepChip" -> stepChip(ctx)
                    "buttonPrimary" -> buttonPrimary(ctx)
                    "buttonTonal" -> buttonTonal(ctx)
                    else -> null
                }?.let { root.background = it }
            }
        }
        if (root is ViewGroup) for (i in 0 until root.childCount) bindTags(root.getChildAt(i))
    }

    private fun fgAttr(role: String): Int? = when (role) {
        "onSurface" -> R.attr.uiOnSurface
        "onSurfaceMuted" -> R.attr.uiOnSurfaceMuted
        "muted" -> R.attr.uiMuted
        "accent" -> R.attr.uiAccent
        "onAccent" -> R.attr.uiOnAccent
        "onAccentContainer" -> R.attr.uiOnAccentContainer
        else -> null
    }

    private fun strokeWidth(ctx: Context): Int {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(R.attr.uiStrokeWidth, tv, true)) {
            TypedValue.complexToDimensionPixelSize(tv.data, ctx.resources.displayMetrics)
        } else {
            dp(ctx, 1f).toInt()
        }
    }

    private fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density
}
