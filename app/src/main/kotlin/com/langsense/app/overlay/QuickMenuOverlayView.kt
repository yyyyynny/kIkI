package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 간편 메뉴 항목(표시 라벨 + 탭 동작). */
data class QuickMenuItem(val label: String, val onClick: () -> Unit)

/**
 * 배지를 탭하면 그 주위에 물방울 버튼들이 부채꼴로 "톡톡" 퍼지는 간편 메뉴 (전체화면 오버레이).
 *
 * - 반투명 스크림 + 빈 곳/항목 탭 시 닫힘.
 * - 물방울은 앵커(배지 중심)에서 화면 중앙 방향으로 부채꼴(약 150°)로 배치하고 화면 밖으로 나가지
 *   않게 clamp 한다. 등장 시 오버슈트 스케일 애니메이션으로 예쁘게 튀어나온다.
 */
@SuppressLint("ViewConstructor")
class QuickMenuOverlayView(
    context: Context,
    private val anchorX: Int,
    private val anchorY: Int,
    items: List<QuickMenuItem>,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val drops = ArrayList<WaterDropView>()
    private var entered = false

    init {
        setBackgroundColor(0x66000000.toInt()) // 반투명 스크림
        isClickable = true
        alpha = 0f
        setOnClickListener { onDismiss() } // 바깥(스크림) 탭 → 닫기

        items.forEach { item ->
            val drop = WaterDropView(context).apply {
                label = item.label
                isClickable = true
                setOnClickListener {
                    onDismiss()
                    item.onClick()
                }
            }
            addView(drop, LayoutParams(dp(60f), dp(76f)))
            drops.add(drop)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (drops.isEmpty()) return

        val n = drops.size
        val w = width.toFloat()
        val h = height.toFloat()
        // 앵커에서 화면 중앙을 향하는 방향을 기준으로 부채꼴 배치(코너에 있어도 화면 안쪽으로 퍼짐).
        val baseAngle = atan2((h / 2f) - anchorY, (w / 2f) - anchorX)
        val span = Math.toRadians(150.0).toFloat()
        val radius = dp(118f).toFloat()

        for (i in 0 until n) {
            val drop = drops[i]
            val frac = if (n == 1) 0.5f else i.toFloat() / (n - 1)
            val angle = baseAngle - span / 2f + span * frac
            val cw = drop.width.toFloat()
            val ch = drop.height.toFloat()
            var cx = anchorX + radius * cos(angle)
            var cy = anchorY + radius * sin(angle)
            cx = cx.coerceIn(cw / 2f, w - cw / 2f)
            cy = cy.coerceIn(ch / 2f, h - ch / 2f)
            drop.translationX = cx - cw / 2f - drop.left
            drop.translationY = cy - ch / 2f - drop.top
        }

        if (!entered) {
            entered = true
            playEntrance()
        }
    }

    private fun playEntrance() {
        animate().alpha(1f).setDuration(160L).start()
        drops.forEachIndexed { i, drop ->
            drop.scaleX = 0f
            drop.scaleY = 0f
            drop.animate()
                .scaleX(1f).scaleY(1f)
                .setStartDelay(i * 40L)
                .setDuration(280L)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).roundToInt()
}
