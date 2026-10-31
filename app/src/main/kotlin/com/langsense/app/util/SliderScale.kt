package com.langsense.app.util

/**
 * 슬라이더 막대 눈금 ↔ 설정값 변환(순수 함수).
 *
 * `%` 값은 막대를 0~100 전체 눈금으로 그려 "70%"가 막대의 70% 지점에 오게 한다(예전엔 50~90 범위를 막대 전체에
 * 펼쳐 70%가 가운데에 왔다 — 2026-10 S25+ 제보). 고를 수 없는 구간은 [toValue] 가 [min]/[max] 로 묶는다.
 * 그 외 단위(ms·회·배)는 범위 자체가 의미라 예전처럼 min~max 를 막대 전체에 펼친다.
 */
class SliderScale(val min: Int, val max: Int, val step: Int, percent: Boolean) {
    private val origin = if (percent) 0 else min
    private val end = if (percent) 100 else max

    /** 막대 칸 수(SeekBar.max). */
    val steps: Int get() = (end - origin) / step

    fun toProgress(value: Int): Int = ((value.coerceIn(min, max) - origin) / step).coerceIn(0, steps)

    fun toValue(progress: Int): Int = (origin + progress * step).coerceIn(min, max)
}
