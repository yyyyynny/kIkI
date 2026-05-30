package com.langsense.app.model

/**
 * 현재 IME(입력기) 언어 상태 스냅샷.
 * 이전 상태와 [locale] 이 다를 때만 플래시를 발동한다.
 */
data class ImeState(
    val locale: String,
    val subtypeId: Int,
    val timestamp: Long
)
