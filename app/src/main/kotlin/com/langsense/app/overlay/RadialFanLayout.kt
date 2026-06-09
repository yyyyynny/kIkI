package com.langsense.app.overlay

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 래디얼(부채꼴) 메뉴의 오브 배치 좌표를 계산하는 순수 기하 계산기 (Bug 3 + 추가 기능 1 공용).
 *
 * 안드로이드 의존성이 전혀 없는 순수 Kotlin 이라 JVM 단위 테스트로 배치 규칙을 검증한다.
 * 배지(앵커)가 화면 어디에 있든 다음을 보장한다.
 *  1. **방향 자동 전환**: 부채꼴 중심을 화면 중앙 쪽으로 향하게 해 화면 안으로 펼친다.
 *  2. **가용 공간 적응**: 양 끝 오브가 화면 밖으로 나가지 않도록 반지름(과 필요 시 부채꼴 각도)을 줄인다.
 *  3. **최소 간격 보장**: 인접 오브 중심 거리가 [minGapPx] 미만으로 좁아지지 않게 반지름 하한을 둔다
 *     (모서리에서 오브가 한 점에 겹쳐 쌓이던 문제 해결).
 *
 * 화면이 너무 좁아 (2)와 (3)을 동시에 만족할 수 없으면, 겹침 방지를 우선해 최소 간격을 지키고
 * 부채꼴을 최대한 좁힌 배치를 돌려준다(오브가 화면 경계를 살짝 넘더라도 서로 겹치지는 않게).
 */
object RadialFanLayout {

    /** 오브 한 개의 배치 결과(중심 좌표 + 배치 각도). */
    data class OrbPosition(val cx: Float, val cy: Float, val angleRad: Float)

    /**
     * @param anchorX 앵커(배지 중심) x
     * @param anchorY 앵커(배지 중심) y
     * @param screenW 배치 영역 너비(px)
     * @param screenH 배치 영역 높이(px)
     * @param count 오브 개수
     * @param orbHalfW 오브 가로 절반(px) — 화면 경계 판정용
     * @param orbHalfH 오브 세로 절반(px) — 화면 경계 판정용
     * @param desiredRadius 이상적 반지름(px) — 공간이 충분하면 이 값을 그대로 쓴다
     * @param desiredSpanRad 이상적 부채꼴 각도(rad) — 공간이 충분하면 이 값을 그대로 쓴다
     * @param minSpanRad 부채꼴 최소 각도(rad) — 모서리에서 부채꼴이 좁아질 수 있는 한계
     * @param minGapPx 인접 오브 중심 거리 최소값(px) — 겹침 방지 핵심
     * @param boundsMarginPx 화면 가장자리 여백(px)
     */
    fun compute(
        anchorX: Float,
        anchorY: Float,
        screenW: Float,
        screenH: Float,
        count: Int,
        orbHalfW: Float,
        orbHalfH: Float,
        desiredRadius: Float,
        desiredSpanRad: Float,
        minSpanRad: Float,
        minGapPx: Float,
        boundsMarginPx: Float
    ): List<OrbPosition> {
        if (count <= 0) return emptyList()

        // 부채꼴 중심 방향 = 앵커에서 화면 중앙을 향하는 각도(방향 자동 전환).
        val cxScreen = screenW / 2f
        val cyScreen = screenH / 2f
        val baseAngle = if (hypot(cxScreen - anchorX, cyScreen - anchorY) < 1f) {
            // 앵커가 화면 정중앙이면 방향이 모호하므로 아래쪽으로 편다(임의의 합리적 기본값).
            (Math.PI / 2).toFloat()
        } else {
            atan2(cyScreen - anchorY, cxScreen - anchorX)
        }

        if (count == 1) {
            // 오브 1개: 간격 개념이 없으므로 화면에 들어오는 선에서 이상적 반지름만 적용.
            val r = fitRadius(anchorX, anchorY, baseAngle, screenW, screenH, orbHalfW, orbHalfH, boundsMarginPx)
                .coerceAtMost(desiredRadius)
                .coerceAtLeast(0f)
            return listOf(orbAt(anchorX, anchorY, baseAngle, r))
        }

        // 이상적 각도부터 최소 각도까지 단계적으로 좁혀 가며 "화면에 들어가면서 최소 간격도 지키는" 배치를 찾는다.
        val steps = SPAN_STEPS
        for (s in 0..steps) {
            val span = desiredSpanRad - (desiredSpanRad - minSpanRad) * (s.toFloat() / steps)
            val delta = span / (count - 1)
            // 이 각도 간격에서 최소 간격을 지키려면 반지름이 최소 rMin 이상이어야 한다(현 = 2·r·sin(Δ/2)).
            val rMin = minGapPx / (2f * sin(delta / 2f))
            // 모든 오브가 화면 안에 들어오는 최대 반지름.
            val rFit = fitRadiusForFan(
                anchorX, anchorY, baseAngle, span, count,
                screenW, screenH, orbHalfW, orbHalfH, boundsMarginPx
            )
            if (rFit >= rMin) {
                // 최소 간격(rMin) 이상, 화면 적합(rFit) 이하 범위에서 이상적 반지름에 가장 가깝게.
                val r = desiredRadius.coerceIn(rMin, rFit)
                return placeFan(anchorX, anchorY, baseAngle, span, count, r)
            }
        }

        // 어떤 각도로도 화면 안에 다 넣으면서 간격을 지킬 수 없는 좁은 화면: 겹침 방지를 우선한다.
        // 부채꼴을 최소로 좁히고 최소 간격을 지키는 반지름으로 배치(경계를 살짝 넘더라도 겹치지 않게).
        val delta = minSpanRad / (count - 1)
        val rMin = minGapPx / (2f * sin(delta / 2f))
        return placeFan(anchorX, anchorY, baseAngle, minSpanRad, count, rMin)
    }

    /** 부채꼴 각도/반지름이 정해졌을 때 오브 중심들을 계산. */
    private fun placeFan(
        anchorX: Float,
        anchorY: Float,
        baseAngle: Float,
        span: Float,
        count: Int,
        radius: Float
    ): List<OrbPosition> = (0 until count).map { i ->
        val frac = i.toFloat() / (count - 1)
        val angle = baseAngle - span / 2f + span * frac
        orbAt(anchorX, anchorY, baseAngle = angle, radius = radius)
    }

    private fun orbAt(anchorX: Float, anchorY: Float, baseAngle: Float, radius: Float): OrbPosition =
        OrbPosition(
            cx = anchorX + radius * cos(baseAngle),
            cy = anchorY + radius * sin(baseAngle),
            angleRad = baseAngle
        )

    /** 부채꼴 전체(모든 오브)가 화면 안에 들어오는 최대 반지름. */
    private fun fitRadiusForFan(
        anchorX: Float,
        anchorY: Float,
        baseAngle: Float,
        span: Float,
        count: Int,
        screenW: Float,
        screenH: Float,
        orbHalfW: Float,
        orbHalfH: Float,
        margin: Float
    ): Float {
        var rFit = Float.MAX_VALUE
        for (i in 0 until count) {
            val frac = i.toFloat() / (count - 1)
            val angle = baseAngle - span / 2f + span * frac
            val r = fitRadius(anchorX, anchorY, angle, screenW, screenH, orbHalfW, orbHalfH, margin)
            if (r < rFit) rFit = r
        }
        return rFit.coerceAtLeast(0f)
    }

    /**
     * 앵커에서 [angle] 방향으로 갈 때 오브 중심이 화면 경계 안([margin+half, screen-margin-half])을
     * 벗어나지 않는 최대 반지름. 축별로 따로 구해 작은 값을 취한다.
     */
    private fun fitRadius(
        anchorX: Float,
        anchorY: Float,
        angle: Float,
        screenW: Float,
        screenH: Float,
        orbHalfW: Float,
        orbHalfH: Float,
        margin: Float
    ): Float {
        val loX = margin + orbHalfW
        val hiX = screenW - margin - orbHalfW
        val loY = margin + orbHalfH
        val hiY = screenH - margin - orbHalfH

        val rx = axisLimit(anchorX, cos(angle), loX, hiX)
        val ry = axisLimit(anchorY, sin(angle), loY, hiY)
        return minOf(rx, ry).coerceAtLeast(0f)
    }

    /** 한 축에서 (origin + r·dir) 이 [lo, hi] 안에 머무는 최대 r. dir≈0 이면 제한 없음. */
    private fun axisLimit(origin: Float, dir: Float, lo: Float, hi: Float): Float {
        if (lo > hi) return 0f // 화면이 오브보다 좁은 퇴화 케이스
        return when {
            dir > EPS -> (hi - origin) / dir
            dir < -EPS -> (lo - origin) / dir
            else -> Float.MAX_VALUE // 이 축으로 거의 움직이지 않음
        }
    }

    private const val EPS = 1e-4f

    /** 이상적 각도→최소 각도 사이를 몇 단계로 시도할지(촘촘할수록 더 넓은 부채꼴을 살릴 확률↑). */
    private const val SPAN_STEPS = 8
}
