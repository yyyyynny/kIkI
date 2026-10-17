package com.langsense.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderScaleTest {

    @Test
    fun 퍼센트는_막대_0에서_100_눈금에_놓인다() {
        val s = SliderScale(50, 90, 5, percent = true)
        assertEquals(20, s.steps)
        assertEquals(14, s.toProgress(70)) // 70% = 막대의 70% 지점
        assertEquals(50, s.toValue(0))     // 고를 수 없는 구간은 최솟값으로 묶임
        assertEquals(90, s.toValue(20))
    }

    @Test
    fun 퍼센트가_아니면_범위를_막대_전체에_펼친다() {
        val s = SliderScale(100, 500, 50, percent = false)
        assertEquals(8, s.steps)
        assertEquals(2, s.toProgress(200))
        assertEquals(200, s.toValue(2))
    }
}
