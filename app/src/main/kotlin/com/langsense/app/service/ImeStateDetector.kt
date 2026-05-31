package com.langsense.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
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
 * ### 중복 발화 합치기 (Bug 6) + 저사양 최적화 (Bug 1)
 * 위 세 경로는 한 번의 한/영 전환에서 거의 동시에 여러 신호를 쏟아낸다(브로드캐스트 1 + 옵저버 2 +
 * 윈도우 이벤트 n). 과거에는 각 신호가 "즉시 + 150ms 후" 두 번씩 서브타입을 다시 읽어 발동시켜
 * 같은 전환이 여러 번 트리거됐고(→ 1회 지정에도 2회 이상 깜박임), 매 신호마다 중복된 조회/콜백이
 * 돌아 저사양 기기에 부담이 됐다. 이제 모든 신호는 [requestRecheck] 로 모여 [COALESCE_MS] 동안
 * **하나로 합쳐지고**, 마지막 신호 직후 단 한 번만 [runRecheck] → [emitIfChanged] 한다.
 * 결과적으로 한 전환당 [onLanguageChanged] 는 정확히 1회만 호출되고 불필요한 반복 조회가 사라진다.
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

    /** 합치는 구간 동안 들어온 윈도우 팝업 텍스트 힌트(삼성 내부 토글 대응). 매 [runRecheck] 후 비운다. */
    private var pendingPopupHint: String? = null

    /** 합쳐진 단 하나의 재확인 작업. 신호가 올 때마다 취소 후 재예약된다. */
    private val recheckRunnable = Runnable { runRecheck() }

    /** 발동 횟수(전환 1회당 1 증가). 한 전환에 정확히 1회만 늘어야 함을 로그로 검증하기 위함. */
    private var emitCount = 0

    private val imeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) = requestRecheck()
    }

    private val subtypeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = requestRecheck()
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
        pendingPopupHint = null
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
        requestRecheck(popupLang)
    }

    /**
     * 모든 감지 경로(Broadcast/Observer/윈도우 이벤트)의 신호를 [COALESCE_MS] 동안 하나로 합친다(Bug 6).
     *
     * 신호가 올 때마다 기존 예약을 취소하고 다시 예약하므로, 한 번의 전환에서 쏟아지는 여러 신호는
     * 마지막 신호 직후 **단 한 번의** [runRecheck] 로 수렴한다. 덕분에 1회 지정 시 플래시가 정확히
     * 1회만 발동하고(중복 제거), 신호마다 즉시+지연으로 두 번씩 서브타입을 읽던 반복 작업도 사라진다(Bug 1).
     *
     * @param popupHint 윈도우 이벤트에서 파싱한 팝업 언어(있으면 서브타입보다 우선 — 삼성 내부 토글 대응).
     */
    private fun requestRecheck(popupHint: String? = null) {
        if (popupHint != null && popupHint != ImeLocaleParser.UNKNOWN) pendingPopupHint = popupHint
        handler.removeCallbacks(recheckRunnable)
        handler.postDelayed(recheckRunnable, COALESCE_MS)
    }

    /**
     * 합쳐진 단일 재확인. 팝업 힌트가 있으면 우선(삼성 내부 한/영 토글은 서브타입이 안 바뀔 수 있음),
     * 없으면 서브타입을 직접 조회한다. 조회는 신호가 멎은 뒤 한 번뿐이라 갱신 지연을 충분히 흡수한다.
     */
    private fun runRecheck() {
        val hint = pendingPopupHint
        pendingPopupHint = null
        emitIfChanged(hint ?: currentSubtypeLang())
    }

    private fun emitIfChanged(lang: String) {
        if (lang == ImeLocaleParser.UNKNOWN) return
        if (lang == lastState?.locale) return
        lastState = ImeState(lang, currentSubtypeId(), System.currentTimeMillis())
        emitCount++
        // 한 전환당 정확히 1회만 찍혀야 한다(Bug 6 실기기 검증용 로그).
        Log.d(TAG, "language change emitted: $lang (emit #$emitCount)")
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
        private const val TAG = "ImeStateDetector"

        /** `Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE` 가 @hide 라 문자열 키를 직접 사용. */
        private const val KEY_SELECTED_SUBTYPE = "selected_input_method_subtype"

        /**
         * 여러 감지 신호를 합치는 구간(ms). 한/영 전환 1회가 만드는 신호 다발이 이 안에 들어오도록
         * 잡되, 신호가 멎은 뒤 서브타입이 갱신될 시간을 충분히 주는 값(과거 지연 재확인과 동일 수준).
         */
        private const val COALESCE_MS = 150L
    }
}
