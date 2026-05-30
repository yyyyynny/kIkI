package com.langsense.app.util

import android.os.Build
import android.view.inputmethod.InputMethodSubtype

/**
 * IME 서브타입의 locale 파싱 + Samsung One UI 시스템 팝업 텍스트 → 언어 추론(fallback).
 *
 * 반환되는 언어 코드는 정규화된 2글자: "ko" / "en" / "ja" / "zh" / 기타.
 */
object ImeLocaleParser {

    const val KO = "ko"
    const val EN = "en"
    const val JA = "ja"
    const val ZH = "zh"
    const val UNKNOWN = "unknown"

    /** InputMethodSubtype → 정규화된 언어 코드. */
    @Suppress("DEPRECATION")
    fun parseLocale(subtype: InputMethodSubtype?): String {
        subtype ?: return UNKNOWN
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            subtype.languageTag.ifEmpty { subtype.locale }
        } else {
            subtype.locale
        }
        return normalize(raw)
    }

    /** 임의의 locale 문자열(ko_KR, en-US 등)을 2글자 코드로 정규화. */
    fun normalize(localeRaw: String?): String {
        val locale = localeRaw?.lowercase().orEmpty()
        if (locale.isEmpty()) return UNKNOWN
        return when {
            locale.startsWith("ko") -> KO
            locale.startsWith("ja") -> JA
            locale.startsWith("en") -> EN
            locale.startsWith("zh") -> ZH
            else -> locale.take(2)
        }
    }

    /**
     * Samsung One UI IME 전환 시스템 팝업 텍스트에서 언어 추론 (fallback).
     * One UI 버전별 텍스트 패턴 차이를 흡수한다. 매칭 실패 시 null.
     *
     * | One UI | 한국어 패턴        | 영어 패턴            |
     * |--------|-------------------|---------------------|
     * | 3.x    | "한국어"          | "English"           |
     * | 5.x    | "한국어"          | "English (US)"      |
     * | 6.x    | "한국어"/"Korean" | "English"           |
     * | 7.x    | "한국어"          | "English"           |
     * | 8.x    | (실기기 확인 TBD) | (실기기 확인 TBD)    |
     */
    fun parseFromSystemPopupText(text: String?): String? {
        text ?: return null
        return when {
            text.contains("한국어") || text.contains("Korean", ignoreCase = true) -> KO
            text.contains("English", ignoreCase = true) -> EN
            text.contains("日本語") || text.contains("Japanese", ignoreCase = true) -> JA
            text.contains("中文") || text.contains("Chinese", ignoreCase = true) -> ZH
            else -> null
        }
    }

    /** 화면 표시용 라벨 (플래시 중앙 텍스트). */
    fun displayName(lang: String): String = when (lang) {
        KO -> "한국어"
        EN -> "English"
        JA -> "日本語"
        ZH -> "中文"
        else -> lang.uppercase()
    }

    /** 배지용 짧은 라벨. */
    fun badgeLabel(lang: String): String = when (lang) {
        KO -> "한"
        EN -> "EN"
        JA -> "日"
        ZH -> "中"
        else -> lang.take(2).uppercase()
    }
}
