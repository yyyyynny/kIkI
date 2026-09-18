package com.langsense.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ── Prefs.alphaFromPercent — 불투명도 %(0~100) → ARGB 알파(0~255) ──────────────

    /**
     * 기본값에서 예전 하드코딩 상수와 **바이트 단위로 같아야** 한다.
     * 예전: 플래시 `(0.85f * 255).toInt()` = 216, 배지 `(0.8f * 255).toInt()` = 204(0xCC).
     * 반올림(`Math.round(85 * 2.55f)` = 217)으로 구현하면 여기서 깨진다 — 그게 이 테스트의 요점.
     */
    @Test
    fun alphaFromPercent_defaults_matchLegacyConstants() {
        assertEquals(216, Prefs.alphaFromPercent(Prefs.DEFAULT_FLASH_OPACITY_PCT))
        assertEquals(204, Prefs.alphaFromPercent(Prefs.DEFAULT_BADGE_BG_OPACITY_PCT))
        assertEquals((0.85f * 255).toInt(), Prefs.alphaFromPercent(85))
        assertEquals((0.8f * 255).toInt(), Prefs.alphaFromPercent(80))
    }

    @Test
    fun alphaFromPercent_bounds() {
        assertEquals(0, Prefs.alphaFromPercent(0))
        assertEquals(255, Prefs.alphaFromPercent(100))
        assertEquals(51, Prefs.alphaFromPercent(Prefs.MIN_OPACITY_PCT))
    }

    /** 범위 밖 입력은 저장값이 손상돼도 그리기가 깨지지 않도록 클램프된다. */
    @Test
    fun alphaFromPercent_outOfRange_clamped() {
        assertEquals(0, Prefs.alphaFromPercent(-40))
        assertEquals(255, Prefs.alphaFromPercent(1000))
    }

    // ── Prefs.isMarkerFresh — NEW / "이사 갔어요" 표시의 자동 만료 ────────────────

    /**
     * 이 기능의 핵심은 "릴리스마다 손으로 지우지 않아도 사라진다"이다.
     * 도입 버전부터 [Prefs.MARKER_LIFESPAN_VERSIONS] 동안만 참이고, 그 뒤로는 영원히 거짓.
     */
    @Test
    fun isMarkerFresh_expiresAfterLifespan() {
        val since = 2
        val last = since + Prefs.MARKER_LIFESPAN_VERSIONS - 1
        for (v in since..last) {
            assertTrue("version=$v 에서는 아직 보여야 한다", Prefs.isMarkerFresh(since, v))
        }
        assertFalse("수명이 끝난 다음 버전부터는 사라져야 한다", Prefs.isMarkerFresh(since, last + 1))
        assertFalse(Prefs.isMarkerFresh(since, last + 50))
    }

    /** 도입 버전보다 이전 버전에서는 보여주지 않는다(마커를 미리 심어 둘 수 있게). */
    @Test
    fun isMarkerFresh_notShownBeforeIntroduced() {
        assertFalse(Prefs.isMarkerFresh(5, 4))
        assertFalse(Prefs.isMarkerFresh(5, 1))
        assertTrue(Prefs.isMarkerFresh(5, 5))
    }

    /** 등록된 마커는 전부 도입 버전이 있어야 한다 — 빠뜨리면 그 마커는 조용히 안 뜬다. */
    @Test
    fun markerSince_coversEveryDeclaredMarker() {
        val declared = listOf(
            Prefs.MARKER_THEME, Prefs.MARKER_FLASH_OPACITY, Prefs.MARKER_BADGE_OPACITY,
            Prefs.MARKER_MOVED_DIAG_PAUSE, Prefs.MARKER_MOVED_QUICK_MENU
        )
        declared.forEach { assertTrue("$it 의 도입 버전이 없다", Prefs.MARKER_SINCE.containsKey(it)) }
        assertEquals(declared.size, Prefs.MARKER_SINCE.size)
    }

    /** %가 커지면 알파도 단조 증가해야 한다(슬라이더가 역행하거나 멈추지 않음). */
    @Test
    fun alphaFromPercent_monotonic() {
        var prev = -1
        for (pct in 0..100) {
            val a = Prefs.alphaFromPercent(pct)
            assertTrue("pct=$pct 에서 역행: $prev -> $a", a >= prev)
            prev = a
        }
    }
}
