package com.langsense.app.util

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
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

    /**
     * 사용자 지정 테마가 적용 중일 때만 non-null — [themeColor] 가 테마 속성 대신 이 팔레트를
     * 준다. 스타일(XML)은 런타임 색을 담을 수 없어서 이 경로가 필요하다. 두 화면(온보딩/설정)이
     * 같은 prefs 로 [apply] 하므로 프로세스 전역 하나로 충분하다.
     */
    @Volatile
    var customPalette: UiPalette? = null
        private set

    fun nightModeFor(themeId: String): Int = when (themeId) {
        Prefs.THEME_LIGHT, Prefs.THEME_BEIGE -> AppCompatDelegate.MODE_NIGHT_NO
        Prefs.THEME_DARK, Prefs.THEME_CYBER, Prefs.THEME_HIGH_CONTRAST -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun styleFor(themeId: String): Int = when (themeId) {
        Prefs.THEME_CUSTOM -> R.style.Theme_LangSense_CustomLight
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
        if (themeId == Prefs.THEME_CUSTOM) {
            // 사용자 지정: 밝기 축(nightMode + 부모 스타일)은 배경 밝기로 정하고, 색은 팔레트가 준다.
            val palette = Prefs(activity).customPalette()
            customPalette = palette
            AppCompatDelegate.setDefaultNightMode(
                if (palette.isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            activity.setTheme(
                if (palette.isDark) R.style.Theme_LangSense_CustomDark else R.style.Theme_LangSense_CustomLight
            )
            return
        }
        customPalette = null
        AppCompatDelegate.setDefaultNightMode(nightModeFor(themeId))
        activity.setTheme(styleFor(themeId))
    }

    /**
     * 사용자 지정일 때 창 배경·상태바·내비게이션 바를 팔레트 색으로 칠한다. 이 셋은 스타일의
     * `windowBackground`/`statusBarColor` 에서 오는데, 스타일엔 런타임 색을 넣을 수 없으므로 창이
     * 만들어진 뒤(`setContentView` 이후) 직접 덮어쓴다. 기본 테마에선 아무것도 하지 않는다.
     */
    fun applyWindow(activity: AppCompatActivity) {
        val p = customPalette ?: return
        val window = activity.window
        window.setBackgroundDrawable(ColorDrawable(p.bg))
        window.statusBarColor = p.bg
        window.navigationBarColor = p.bg
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !p.isDark
            isAppearanceLightNavigationBars = !p.isDark
        }
    }

    /**
     * 화면을 다시 그려야 하는지 비교할 값 — 테마 id 에, 사용자 지정이면 4색까지 붙인다. 백스택의
     * 화면은 `onResume` 에서 자기가 적용한 값과 이걸 비교해 달라졌으면 스스로 재생성한다.
     */
    fun signature(context: Context, themeId: String): String {
        if (themeId != Prefs.THEME_CUSTOM) return themeId
        val prefs = Prefs(context)
        return themeId + Prefs.CUSTOM_SLOTS.joinToString(",", ":") { prefs.customThemeSeed(it) }
    }
}
