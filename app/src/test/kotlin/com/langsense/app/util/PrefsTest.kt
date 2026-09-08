package com.langsense.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prefs.resolveQuickMenuOrder 순수 함수 검증 (Context 불필요).
 * 순수 JVM 테스트 — `./gradlew testDebugUnitTest` 로 실행.
 *
 * 계약(2026-08 재설계): 저장값은 "9개 액션 풀 중 사용자가 고른 것만, 그 순서대로, 1~5개"다.
 * 선택 안 한 항목은 백필되지 않는다 — 저장값이 없거나 완전히 손상됐을 때만 기본 5개로 폴백한다.
 */
class PrefsTest {

    private val fullPool = Prefs.QUICK_MENU_ACTION_IDS
    private val defaultOrder = Prefs.QUICK_MENU_ACTION_IDS.take(Prefs.MAX_QUICK_MENU_ITEMS)

    @Test
    fun resolveQuickMenuOrder_nullOrBlank_returnsDefault() {
        assertEquals(defaultOrder, Prefs.resolveQuickMenuOrder(null))
        assertEquals(defaultOrder, Prefs.resolveQuickMenuOrder(""))
    }

    @Test
    fun resolveQuickMenuOrder_fullPoolPermutation_cappedAtFive() {
        val reversed = fullPool.reversed()
        val result = Prefs.resolveQuickMenuOrder(reversed.joinToString(","))
        assertEquals(Prefs.MAX_QUICK_MENU_ITEMS, result.size)
        assertEquals(reversed.take(Prefs.MAX_QUICK_MENU_ITEMS), result)
    }

    @Test
    fun resolveQuickMenuOrder_unknownIds_areDropped() {
        val raw = "unknown_id," + defaultOrder.joinToString(",") + ",also_unknown"
        assertEquals(defaultOrder, Prefs.resolveQuickMenuOrder(raw))
    }

    @Test
    fun resolveQuickMenuOrder_duplicateIds_collapsedToSelectedCountOnly() {
        val raw = "${Prefs.ACTION_HIDE_BADGE},${Prefs.ACTION_HIDE_BADGE}," +
            "${Prefs.ACTION_OPEN_APP},${Prefs.ACTION_OPEN_APP}"
        val result = Prefs.resolveQuickMenuOrder(raw)
        // 백필 없음 — 실제로 선택된 2개만 반환(중복은 1회로 축약).
        assertEquals(listOf(Prefs.ACTION_HIDE_BADGE, Prefs.ACTION_OPEN_APP), result)
    }

    @Test
    fun resolveQuickMenuOrder_singleSaved_returnsOnlyThatOne() {
        // 백필 폐기: 1개만 저장하면 1개만 반환한다(예전엔 나머지가 기본 순서로 뒤에 붙었음).
        assertEquals(listOf(Prefs.ACTION_HIDE_BADGE), Prefs.resolveQuickMenuOrder(Prefs.ACTION_HIDE_BADGE))
    }

    @Test
    fun resolveQuickMenuOrder_threeSelected_returnsExactlyThreeInOrder() {
        val raw = "${Prefs.ACTION_TOGGLE_LOWSPEC},${Prefs.ACTION_OPEN_APP},${Prefs.ACTION_TOGGLE_NOFOCUS}"
        val result = Prefs.resolveQuickMenuOrder(raw)
        assertEquals(
            listOf(Prefs.ACTION_TOGGLE_LOWSPEC, Prefs.ACTION_OPEN_APP, Prefs.ACTION_TOGGLE_NOFOCUS),
            result
        )
    }

    @Test
    fun resolveQuickMenuOrder_moreThanFiveSaved_cappedAtFive() {
        assertEquals(9, fullPool.size) // 전제 확인
        val result = Prefs.resolveQuickMenuOrder(fullPool.joinToString(","))
        assertEquals(Prefs.MAX_QUICK_MENU_ITEMS, result.size)
        assertEquals(fullPool.take(Prefs.MAX_QUICK_MENU_ITEMS), result)
    }

    @Test
    fun resolveQuickMenuOrder_allUnknown_fallsBackToDefaultFive() {
        // 저장값이 있어도 전부 미상 id 라 유효 선택이 0개가 되면(손상된 상태) 기본 5개로 폴백.
        assertEquals(defaultOrder, Prefs.resolveQuickMenuOrder("garbage,more_garbage"))
    }

    @Test
    fun resolveQuickMenuOrder_newActionIds_areIncludedInPool() {
        val newIds = listOf(
            Prefs.ACTION_TOGGLE_NOFOCUS, Prefs.ACTION_TOGGLE_TOUCHKB,
            Prefs.ACTION_TOGGLE_LOWSPEC, Prefs.ACTION_CYCLE_BADGE_SIZE
        )
        newIds.forEach { assertTrue(it in fullPool) }
    }
}
