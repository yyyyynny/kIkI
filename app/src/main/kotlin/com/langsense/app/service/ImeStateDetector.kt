package com.langsense.app.service

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.langsense.app.model.ImeState
import com.langsense.app.util.ImeLocaleParser

/**
 * IME 서브타입(언어) 변경 감지 (Feature 1).
 *
 * 우선순위:
 *  1) Samsung One UI 시스템 팝업 텍스트 (전환 순간의 명시적 신호)
 *  2) InputMethodManager.currentInputMethodSubtype 의 locale (정상 상태)
 *
 * 이전 상태와 언어 코드가 다를 때만 [onLanguageChanged] 를 호출한다.
 */
class ImeStateDetector(
    context: Context,
    private val onLanguageChanged: (String) -> Unit
) {
    private val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private var lastState: ImeState? = null

    /** 서비스 시작 시 현재 언어를 캐시(플래시 없이)하고 코드 반환. */
    fun primeCurrent(): String {
        val lang = currentSubtypeLang()
        if (lang != ImeLocaleParser.UNKNOWN) {
            lastState = ImeState(lang, currentSubtypeId(), System.currentTimeMillis())
        }
        return lang
    }

    fun onWindowStateChanged(event: AccessibilityEvent) {
        val popupLang = if (isSystemPopupSource(event)) {
            ImeLocaleParser.parseFromSystemPopupText(eventText(event))
        } else null

        val lang = popupLang ?: currentSubtypeLang()
        if (lang == ImeLocaleParser.UNKNOWN) return
        if (lang == lastState?.locale) return

        lastState = ImeState(lang, currentSubtypeId(), System.currentTimeMillis())
        onLanguageChanged(lang)
    }

    private fun currentSubtypeLang(): String =
        ImeLocaleParser.parseLocale(runCatching { imm.currentInputMethodSubtype }.getOrNull())

    private fun currentSubtypeId(): Int =
        runCatching { imm.currentInputMethodSubtype?.hashCode() }.getOrNull() ?: -1

    /** Samsung/시스템 팝업으로 보이는 이벤트만 텍스트 fallback 대상으로. */
    private fun isSystemPopupSource(event: AccessibilityEvent): Boolean {
        val pkg = event.packageName?.toString() ?: return false
        return pkg == "android" || pkg.contains("samsung", ignoreCase = true) ||
            pkg.contains("inputmethod", ignoreCase = true) || pkg.contains("honeyboard", ignoreCase = true)
    }

    private fun eventText(event: AccessibilityEvent): String =
        event.text.joinToString(" ") { it?.toString().orEmpty() }
}
