package com.langsense.app.util

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.langsense.app.R

/**
 * 앱 화면(온보딩·설정) 테마 적용기.
 *
 * 테마는 **두 축을 함께** 설정해야 한다:
 *  1. `AppCompatDelegate.setDefaultNightMode` — `Configuration.uiMode` 의 밝기 비트. 이것만으로는
 *     라이트/다크 두 값밖에 표현할 수 없어(리소스 한정자에 "베이지" 같은 축이 없다) 고정 팔레트
 *     3종을 만들 수 없다.
 *  2. `Activity.setTheme` — 고정 팔레트(베이지/사이버펑크/고대비) 스타일. 다만 이쪽은 AppCompat 이
 *     자동 재생성을 해 주지 않으므로 호출자가 직접 [AppCompatActivity.recreate] 해야 한다.
 *
 * 나이트모드를 팔레트와 같은 방향으로 **핀 고정**하는 이유: AppCompat 내부 위젯 리소스(다이얼로그,
 * EditText 커서, 오버플로 등)는 우리 attr 이 아니라 uiMode 를 따른다. 어두운 팔레트에 라이트
 * 나이트모드가 걸리면 그 부분만 흰 배경으로 튄다.
 */
object ThemeManager {

    fun nightModeFor(themeId: String): Int = when (themeId) {
        Prefs.THEME_LIGHT, Prefs.THEME_BEIGE -> AppCompatDelegate.MODE_NIGHT_NO
        Prefs.THEME_DARK, Prefs.THEME_CYBER, Prefs.THEME_HIGH_CONTRAST -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun styleFor(themeId: String): Int = when (themeId) {
        Prefs.THEME_BEIGE -> R.style.Theme_LangSense_Beige
        Prefs.THEME_CYBER -> R.style.Theme_LangSense_Cyber
        Prefs.THEME_HIGH_CONTRAST -> R.style.Theme_LangSense_HighContrast
        // system/light/dark 는 DayNight 테마 하나로 처리하고 밝기는 nightMode 가 가른다.
        else -> R.style.Theme_LangSense
    }

    /**
     * ⚠️ 호출자는 반드시 `super.onCreate()` **보다 먼저** 이 함수를 불러야 한다 —
     * `windowBackground` 은 `super.onCreate()` 안에서 Window 가 붙을 때 확정되므로, 이후에 부르면
     * 다른 색은 바뀌어도 배경만 이전 테마로 남는다.
     */
    fun apply(activity: AppCompatActivity, themeId: String) {
        AppCompatDelegate.setDefaultNightMode(nightModeFor(themeId))
        activity.setTheme(styleFor(themeId))
    }
}
