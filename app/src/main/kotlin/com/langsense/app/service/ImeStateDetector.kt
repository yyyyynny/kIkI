package com.langsense.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.langsense.app.model.ImeState
import com.langsense.app.util.ImeLocaleParser

/**
 * IME 서브타입(언어) 변경 감지 (Feature 1).
 *
 * 전환을 **즉시** 잡기 위해 세 가지 경로를 병행한다(가장 먼저 도착한 신호가 발동):
 *  1) [BroadcastReceiver] — `ACTION_INPUT_METHOD_CHANGED` (IME/서브타입 전체 전환)
 *  2) [ContentObserver]   — `Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE` 변경
 *     (한/영 토글 시 시스템 설정이 즉시 바뀌므로, 화면 변화 없이 가만히 있어도 감지됨 → Bug 4)
 *  3) `TYPE_WINDOW_STATE_CHANGED` + Samsung One UI 팝업 텍스트 (백스톱 / 삼성 내부 토글 대응)
 *
 * 어느 경로든 결과는 [emitIfChanged] 로 모이며, 이전 언어와 다를 때만 [onLanguageChanged] 호출.
 */
class ImeStateDetector(
    context: Context,
    private val onLanguageChanged: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastState: ImeState? = null
    private var registered = false

    private val imeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) = scheduleRecheck()
    }

    private val subtypeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = scheduleRecheck()
    }

    /** 서비스 시작 시 호출: 리스너 등록 + 현재 언어 캐시(플래시 없이). 현재 언어 코드 반환. */
    fun start(): String {
        if (!registered) {
            runCatching {
                ContextCompat.registerReceiver(
                    appContext,
                    imeChangeReceiver,
                    IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
            runCatching {
                val cr = appContext.contentResolver
                // Settings.Secure 의 일부 IME 키 상수는 @hide 이므로 안정적인 문자열 키를 직접 사용한다.
                cr.registerContentObserver(
                    Settings.Secure.getUriFor(KEY_SELECTED_SUBTYPE),
                    false, subtypeObserver
                )
                cr.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
                    false, subtypeObserver
                )
            }
            registered = true
        }
        val lang = currentSubtypeLang()
        if (lang != ImeLocaleParser.UNKNOWN) {
            lastState = ImeState(lang, currentSubtypeId(), System.currentTimeMillis())
        }
        return lang
    }

    /** 서비스 종료 시 호출: 리스너 해제 + 예약된 재확인 콜백 정리. */
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        if (!registered) return
        runCatching { appContext.unregisterReceiver(imeChangeReceiver) }
        runCatching { appContext.contentResolver.unregisterContentObserver(subtypeObserver) }
        registered = false
    }

    /** 접근성 윈도우 이벤트 경로(백스톱 + Samsung 팝업 텍스트 fallback). */
    fun onWindowStateChanged(event: AccessibilityEvent) {
        val popupLang = if (isSystemPopupSource(event)) {
            ImeLocaleParser.parseFromSystemPopupText(eventText(event))
        } else null
        emitIfChanged(popupLang ?: currentSubtypeLang())
    }

    /**
     * Broadcast/Observer 신호 후 서브타입을 재확인.
     * 설정 변경 직후 `currentInputMethodSubtype` 갱신에 짧은 지연이 있을 수 있어
     * 즉시 + 150ms 후 두 번 확인한다.
     */
    private fun scheduleRecheck() {
        emitIfChanged(currentSubtypeLang())
        handler.postDelayed({ emitIfChanged(currentSubtypeLang()) }, 150L)
    }

    private fun emitIfChanged(lang: String) {
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

    companion object {
        /** `Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE` 가 @hide 라 문자열 키를 직접 사용. */
        private const val KEY_SELECTED_SUBTYPE = "selected_input_method_subtype"
    }
}
