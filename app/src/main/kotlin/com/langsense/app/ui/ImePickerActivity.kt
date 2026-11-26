package com.langsense.app.ui

import android.app.Activity
import android.view.inputmethod.InputMethodManager

/**
 * 배지 길게 누르기 → 입력기 선택 창. 시스템은 입력 포커스를 가진 창의 앱만 선택 창을 띄우게 한다(Android 9+
 * canShowInputMethodPicker — 아니면 예외 없이 조용히 무시). 배지 창은 포커스를 못 받으므로, 투명한 이 화면이 잠깐
 * 포커스를 받아 선택 창을 띄우고, 선택 창이 닫혀 포커스가 돌아오면 닫힌다.
 */
class ImePickerActivity : Activity() {
    private var shown = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !shown) {
            shown = true
            // 포커스 알림이 입력기 서비스에 닿은 뒤에 불러야 한다(같은 프레임에 부르면 아직 포커스 앱이 아니라 무시됨).
            // 한계: 고정 지연 — 확장: 무시되는 기기가 나오면 지연을 늘리거나 재시도.
            window.decorView.postDelayed({
                if (runCatching { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }.isFailure) finish()
            }, PICKER_DELAY_MS)
        } else if (hasFocus) {
            finish() // 선택 창이 닫혀 포커스가 돌아왔다. 선택 창이 떠 있는 동안 닫으면 시스템이 선택 창도 함께 닫는다.
        }
    }

    private companion object {
        const val PICKER_DELAY_MS = 100L
    }
}
