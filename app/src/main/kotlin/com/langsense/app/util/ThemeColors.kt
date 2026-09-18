package com.langsense.app.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * 테마 속성(`R.attr.ui*`)에서 색을 읽는다.
 *
 * `context.getColor(R.color.ui_accent)` 는 리소스 id 로 리터럴을 읽을 뿐 테마를 보지 않아
 * `setTheme()` 만으로는 앱 색이 바뀌지 않는다. 화면 코드가 이 함수를 거치면 색이 테마에서
 * 해석되므로, 나중에 팔레트만 다르게 바인딩한 테마를 추가하는 것으로 런타임 색 교체가 된다.
 *
 * 구현은 `SettingsActivity.themeRipple()` 이 `selectableItemBackground` 에 이미 쓰고 있는
 * `TypedValue` + `resolveAttribute` 관용구와 동일하다(코드베이스 기존 패턴).
 *
 * 색을 직접 지정한 attr(`@color/...` 바인딩 포함)은 `TypedValue.data` 에 해석된 ARGB 가 실려
 * 오므로 그 값을 쓰고, 혹시 ColorStateList 같은 참조로 오는 경우만 리소스로 되짚는다.
 *
 * ⚠️ 새 파일로 둔 이유: 기존 util 파일들은 전부 도메인 한 가지씩(HangulConverter, Prefs …)을
 * 맡고 있어 UI 테마 헬퍼를 얹을 자연스러운 자리가 없다. 화면 두 곳(Main/Settings)이 공유하는
 * 최소 확장 하나만 담는다.
 */
@ColorInt
fun Context.themeColor(@AttrRes attr: Int): Int {
    val tv = TypedValue()
    if (!theme.resolveAttribute(attr, tv, true)) return 0
    return if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
        tv.data
    } else {
        getColor(tv.resourceId)
    }
}
