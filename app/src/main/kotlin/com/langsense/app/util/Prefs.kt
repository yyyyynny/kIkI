package com.langsense.app.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * SharedPreferences 래퍼 — 모든 설정 항목의 단일 진입점.
 *
 * 설정 변경은 즉시 적용된다(서비스가 [register] 로 변경을 구독).
 * 색상은 "#RRGGBB" 문자열로 저장하고, 플래시 표시 시 [FLASH_ALPHA] 불투명도를 적용한다.
 */
class Prefs(context: Context) {

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- Feature 1: 플래시 ----
    var flashEnabled: Boolean
        get() = sp.getBoolean(KEY_FLASH_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_FLASH_ENABLED, v).apply()

    /** 깜박임 속도(1회 지속 시간) ms, 100~500. */
    var flashDurationMs: Int
        get() = sp.getInt(KEY_FLASH_DURATION, 200).coerceIn(100, 500)
        set(v) = sp.edit().putInt(KEY_FLASH_DURATION, v.coerceIn(100, 500)).apply()

    /** 깜박임 횟수 1~5. */
    var flashCount: Int
        get() = sp.getInt(KEY_FLASH_COUNT, 1).coerceIn(1, 5)
        set(v) = sp.edit().putInt(KEY_FLASH_COUNT, v.coerceIn(1, 5)).apply()

    // ---- Feature 2: 배지 ----
    var badgeEnabled: Boolean
        get() = sp.getBoolean(KEY_BADGE_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_BADGE_ENABLED, v).apply()

    /** 배지 위치(px). -1 이면 기본 위치(우하단)를 런타임에 계산. */
    var badgeX: Int
        get() = sp.getInt(KEY_BADGE_X, -1)
        set(v) = sp.edit().putInt(KEY_BADGE_X, v).apply()

    var badgeY: Int
        get() = sp.getInt(KEY_BADGE_Y, -1)
        set(v) = sp.edit().putInt(KEY_BADGE_Y, v).apply()

    fun setBadgePosition(x: Int, y: Int) =
        sp.edit().putInt(KEY_BADGE_X, x).putInt(KEY_BADGE_Y, y).apply()

    // ---- Feature 3: 포커스 없는 키 입력 경고 ----
    var noFocusEnabled: Boolean
        get() = sp.getBoolean(KEY_NOFOCUS_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_NOFOCUS_ENABLED, v).apply()

    /** 경고 임계 횟수 1~5. */
    var noFocusThreshold: Int
        get() = sp.getInt(KEY_NOFOCUS_THRESHOLD, 3).coerceIn(1, 5)
        set(v) = sp.edit().putInt(KEY_NOFOCUS_THRESHOLD, v.coerceIn(1, 5)).apply()

    // ---- Feature 4: 한영타 교체 ----
    var replaceEnabled: Boolean
        get() = sp.getBoolean(KEY_REPLACE_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_REPLACE_ENABLED, v).apply()

    /** 신뢰도 임계값(%) 50~90. */
    var replaceConfidence: Int
        get() = sp.getInt(KEY_REPLACE_CONFIDENCE, 70).coerceIn(50, 90)
        set(v) = sp.edit().putInt(KEY_REPLACE_CONFIDENCE, v.coerceIn(50, 90)).apply()

    // ---- 지원 언어 토글 ----
    fun isLangEnabled(lang: String): Boolean = when (lang) {
        ImeLocaleParser.KO -> sp.getBoolean(KEY_LANG_KO, true)
        ImeLocaleParser.EN -> sp.getBoolean(KEY_LANG_EN, true)
        // [일본어 비활성화] ImeLocaleParser.JA -> sp.getBoolean(KEY_LANG_JA, true)
        else -> true // 기타 언어는 항상 표시
    }

    fun setLangEnabled(lang: String, enabled: Boolean) {
        val key = when (lang) {
            ImeLocaleParser.KO -> KEY_LANG_KO
            ImeLocaleParser.EN -> KEY_LANG_EN
            // [일본어 비활성화] ImeLocaleParser.JA -> KEY_LANG_JA
            else -> return
        }
        sp.edit().putBoolean(key, enabled).apply()
    }

    // ---- 언어별 플래시 색상 ----
    fun colorHex(lang: String): String = when (lang) {
        ImeLocaleParser.KO -> sp.getString(KEY_COLOR_KO, DEFAULT_KO) ?: DEFAULT_KO
        ImeLocaleParser.EN -> sp.getString(KEY_COLOR_EN, DEFAULT_EN) ?: DEFAULT_EN
        // [일본어 비활성화] ImeLocaleParser.JA -> sp.getString(KEY_COLOR_JA, DEFAULT_JA) ?: DEFAULT_JA
        else -> DEFAULT_OTHER
    }

    fun setColorHex(lang: String, hex: String) {
        val key = when (lang) {
            ImeLocaleParser.KO -> KEY_COLOR_KO
            ImeLocaleParser.EN -> KEY_COLOR_EN
            // [일본어 비활성화] ImeLocaleParser.JA -> KEY_COLOR_JA
            else -> return
        }
        sp.edit().putString(key, normalizeHex(hex)).apply()
    }

    /** 플래시에 사용할 ARGB 색상(불투명도 [FLASH_ALPHA] 적용). 잘못된 코드는 기본 회색으로. */
    fun flashColorArgb(lang: String): Int {
        val rgb = try {
            Color.parseColor(colorHex(lang))
        } catch (e: IllegalArgumentException) {
            Color.parseColor(DEFAULT_OTHER)
        }
        val alpha = (FLASH_ALPHA * 255).toInt()
        return Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val NAME = "langsense_prefs"
        const val FLASH_ALPHA = 0.85f

        const val DEFAULT_KO = "#CC2D2D"
        const val DEFAULT_EN = "#1A6EBD"
        // [일본어 비활성화] 색 기본값/키 보존(현재 미사용, 추후 재도입 시 참조 복구).
        const val DEFAULT_JA = "#2D8C4E"
        const val DEFAULT_OTHER = "#555555"

        const val KEY_FLASH_ENABLED = "flash_enabled"
        const val KEY_FLASH_DURATION = "flash_duration_ms"
        const val KEY_FLASH_COUNT = "flash_count"
        const val KEY_BADGE_ENABLED = "badge_enabled"
        const val KEY_BADGE_X = "badge_x"
        const val KEY_BADGE_Y = "badge_y"
        const val KEY_NOFOCUS_ENABLED = "nofocus_enabled"
        const val KEY_NOFOCUS_THRESHOLD = "nofocus_threshold"
        const val KEY_REPLACE_ENABLED = "replace_enabled"
        const val KEY_REPLACE_CONFIDENCE = "replace_confidence"
        const val KEY_LANG_KO = "lang_ko"
        const val KEY_LANG_EN = "lang_en"
        const val KEY_LANG_JA = "lang_ja" // [일본어 비활성화] 미사용, 보존
        const val KEY_COLOR_KO = "color_ko"
        const val KEY_COLOR_EN = "color_en"
        const val KEY_COLOR_JA = "color_ja" // [일본어 비활성화] 미사용, 보존

        /** "#rrggbb" 형식으로 정규화. 유효하지 않으면 기본 회색. */
        fun normalizeHex(input: String): String {
            val s = input.trim()
            val withHash = if (s.startsWith("#")) s else "#$s"
            return try {
                Color.parseColor(withHash) // 검증만
                withHash.uppercase()
            } catch (e: IllegalArgumentException) {
                DEFAULT_OTHER
            }
        }
    }
}
