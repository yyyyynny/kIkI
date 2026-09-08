package com.langsense.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prefs.resolveQuickMenuOrder 순수 함수 검증 (Context 불필요).
 * 순수 JVM 테스트 — `./gradlew testDebugUnitTest` 로 실행.
 */
class PrefsTest {

    private val defaultOrder = Prefs.QUICK_MENU_ACTION_IDS

    @Test
    fun resolveQuickMenuOrder_nullOrBlank_returnsDefault() {
        assertEquals(defaultOrder, Prefs.resolveQuickMenuOrder(null))
        assertEquals(defaultOrder, Prefs.resolveQuickMenuOrder(""))
    }

    @Test
    fun resolveQuickMenuOrder_fullValidPermutation_preserved() {
        val reversed = defaultOrder.reversed()
        assertEquals(reversed, Prefs.resolveQuickMenuOrder(reversed.joinToString(",")))
    }

    @Test
    fun resolveQuickMenuOrder_unknownIds_areDropped() {
        val raw = "unknown_id," + defaultOrder.joinToString(",") + ",also_unknown"
        assertEquals(defaultOrder, Prefs.resolveQuickMenuOrder(raw))
    }

    @Test
    fun resolveQuickMenuOrder_duplicateIds_keptOnce() {
        val raw = "${Prefs.ACTION_HIDE_BADGE},${Prefs.ACTION_HIDE_BADGE}," +
            "${Prefs.ACTION_OPEN_APP},${Prefs.ACTION_OPEN_APP}"
        val result = Prefs.resolveQuickMenuOrder(raw)
        assertEquals(5, result.size)
        assertEquals(1, result.count { it == Prefs.ACTION_HIDE_BADGE })
        assertEquals(1, result.count { it == Prefs.ACTION_OPEN_APP })
    }

    @Test
    fun resolveQuickMenuOrder_partialSaved_missingAppendedInDefaultOrder() {
        // hide_badge 를 맨 앞으로 옮기고 나머지는 저장 안 한 경우 — 나머지는 기본 순서로 뒤에 붙는다.
        val raw = Prefs.ACTION_HIDE_BADGE
        val expected = listOf(Prefs.ACTION_HIDE_BADGE) +
            defaultOrder.filter { it != Prefs.ACTION_HIDE_BADGE }
        assertEquals(expected, Prefs.resolveQuickMenuOrder(raw))
    }

    @Test
    fun resolveQuickMenuOrder_alwaysExactlyFive() {
        assertEquals(5, Prefs.resolveQuickMenuOrder(null).size)
        assertEquals(5, Prefs.resolveQuickMenuOrder("garbage,more_garbage").size)
        assertEquals(5, Prefs.resolveQuickMenuOrder(Prefs.ACTION_OPEN_APP).size)
    }
}
