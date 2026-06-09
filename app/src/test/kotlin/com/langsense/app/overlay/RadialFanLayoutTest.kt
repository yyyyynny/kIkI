package com.langsense.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.hypot
import org.junit.Test

/**
 * RadialFanLayout 배치 규칙 검증 (Bug 3 — 화면 끝 오브 겹침).
 * 순수 JVM 테스트 — `./gradlew testDebugUnitTest` 로 실행.
 *
 * 핵심 보장:
 *  1. 인접 오브가 최소 간격(MIN_GAP) 미만으로 겹치지 않는다 — 어떤 앵커 위치에서도.
 *  2. 공간이 충분하면 모든 오브가 화면 경계 안에 들어온다.
 */
class RadialFanLayoutTest {

    // 테스트용 표준 파라미터(QuickMenuOverlayView 와 동일 수준, 단위는 px 로 간주).
    private val orbHalfW = 30f
    private val orbHalfH = 38f
    private val desiredRadius = 118f
    private val desiredSpan = Math.toRadians(150.0).toFloat()
    private val minSpan = Math.toRadians(70.0).toFloat()
    private val minGap = 66f
    private val margin = 8f

    private fun compute(ax: Float, ay: Float, w: Float = 1000f, h: Float = 1600f, n: Int = 5) =
        RadialFanLayout.compute(
            anchorX = ax, anchorY = ay, screenW = w, screenH = h, count = n,
            orbHalfW = orbHalfW, orbHalfH = orbHalfH,
            desiredRadius = desiredRadius, desiredSpanRad = desiredSpan,
            minSpanRad = minSpan, minGapPx = minGap, boundsMarginPx = margin
        )

    /** 인접 오브 중심 거리가 최소 간격 이상인지(겹침 없음). 부동소수 오차 약간 허용. */
    private fun assertNoOverlap(positions: List<RadialFanLayout.OrbPosition>) {
        for (i in 0 until positions.size - 1) {
            val a = positions[i]
            val b = positions[i + 1]
            val d = hypot(a.cx - b.cx, a.cy - b.cy)
            assertTrue(
                "인접 오브 간 거리 $d 가 최소 간격 $minGap 보다 작음(겹침)",
                d >= minGap - 0.5f
            )
        }
    }

    /** 모든 오브가 화면 경계 안(여백 포함)에 있는지. */
    private fun assertInBounds(
        positions: List<RadialFanLayout.OrbPosition>,
        w: Float = 1000f, h: Float = 1600f
    ) {
        positions.forEach { p ->
            assertTrue("오브 x=${p.cx} 가 화면 왼쪽 밖", p.cx >= margin + orbHalfW - 0.5f)
            assertTrue("오브 x=${p.cx} 가 화면 오른쪽 밖", p.cx <= w - margin - orbHalfW + 0.5f)
            assertTrue("오브 y=${p.cy} 가 화면 위쪽 밖", p.cy >= margin + orbHalfH - 0.5f)
            assertTrue("오브 y=${p.cy} 가 화면 아래쪽 밖", p.cy <= h - margin - orbHalfH + 0.5f)
        }
    }

    @Test
    fun center_anchor_noOverlap_inBounds() {
        val pos = compute(500f, 800f)
        assertEquals(5, pos.size)
        assertNoOverlap(pos)
        assertInBounds(pos)
    }

    @Test
    fun bottomRight_corner_noOverlap() {
        // 우하단 모서리(기본 배지 위치와 유사) — 가장 문제됐던 케이스.
        val pos = compute(1000f - 40f, 1600f - 40f)
        assertNoOverlap(pos)
    }

    @Test
    fun allFourCorners_noOverlap() {
        val corners = listOf(
            20f to 20f,                 // 좌상
            1000f - 20f to 20f,         // 우상
            20f to 1600f - 20f,         // 좌하
            1000f - 20f to 1600f - 20f  // 우하
        )
        corners.forEach { (x, y) ->
            val pos = compute(x, y)
            assertEquals(5, pos.size)
            assertNoOverlap(pos)
        }
    }

    @Test
    fun edges_noOverlap() {
        val edges = listOf(
            500f to 10f,          // 상단 중앙
            500f to 1600f - 10f,  // 하단 중앙
            10f to 800f,          // 좌측 중앙
            1000f - 10f to 800f   // 우측 중앙
        )
        edges.forEach { (x, y) ->
            assertNoOverlap(compute(x, y))
        }
    }

    @Test
    fun centerAnchor_usesDesiredRadius() {
        // 공간이 충분한 정중앙 부근에서는 이상적 반지름이 유지되어야 한다(평소 외형 보존).
        val pos = compute(500f, 800f)
        val r = hypot(pos[0].cx - 500f, pos[0].cy - 800f)
        assertEquals(desiredRadius.toDouble(), r.toDouble(), 1.0)
    }

    @Test
    fun singleOrb_returnsOne_inBounds() {
        val pos = compute(1000f - 40f, 1600f - 40f, n = 1)
        assertEquals(1, pos.size)
        assertInBounds(pos)
    }

    @Test
    fun smallScreen_stillNoOverlap() {
        // 좁은 화면에서도 겹침 방지를 우선(경계를 살짝 넘더라도 서로 겹치지 않아야).
        val pos = RadialFanLayout.compute(
            anchorX = 300f, anchorY = 250f, screenW = 600f, screenH = 500f, count = 5,
            orbHalfW = orbHalfW, orbHalfH = orbHalfH,
            desiredRadius = desiredRadius, desiredSpanRad = desiredSpan,
            minSpanRad = minSpan, minGapPx = minGap, boundsMarginPx = margin
        )
        assertNoOverlap(pos)
    }
}
