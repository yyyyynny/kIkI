package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.widget.FrameLayout
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 간편 메뉴 항목(표시 라벨 + 탭 동작). */
data class QuickMenuItem(val label: String, val onClick: () -> Unit)

/**
 * 배지를 탭하면 그 주위에 발광 오브 버튼들이 부채꼴로 "톡톡" 솟아오르는 간편 메뉴 (전체화면 오버레이).
 *
 * - 반투명 스크림 + 빈 곳/항목 탭 시 닫힘(닫을 때도 페이드+축소 애니메이션).
 * - 오브는 앵커(배지 중심)에서 화면 중앙 방향으로 부채꼴(약 150°)로 배치하고 화면 밖으로
 *   나가지 않게 clamp 한다.
 * - 등장: 아래에서 위로 스프링 바운스 / 드래그: 탄성 추종 후 제자리 복귀 / 탭: scale down.
 *   (구 물방울 [WaterDropView] → 발광 오브 [OrbView] 로 교체)
 */
@SuppressLint("ViewConstructor")
class QuickMenuOverlayView(
    context: Context,
    private val anchorX: Int,
    private val anchorY: Int,
    items: List<QuickMenuItem>,
    glowEnabled: Boolean,
    /** 실제 윈도우 제거(애니메이션이 끝난 뒤 호출됨). */
    private val onRemove: () -> Unit
) : FrameLayout(context) {

    private val orbs = ArrayList<OrbView>()
    private var entered = false
    private var closing = false

    init {
        setBackgroundColor(0x66000000.toInt()) // 반투명 스크림
        isClickable = true
        alpha = 0f
        setOnClickListener { close() } // 바깥(스크림) 탭 → 닫기

        items.forEach { item ->
            val orb = OrbView(context, glowEnabled).apply {
                label = item.label
                onTap = {
                    // 메뉴를 닫는 연출을 시작한 뒤 동작 실행(메뉴가 사라지는 게 먼저 보이도록).
                    close()
                    item.onClick()
                }
            }
            // 컨테이너를 오브보다 넉넉히 잡아 외곽 글로우/그림자가 잘리지 않게 한다.
            addView(orb, LayoutParams(dp(84f), dp(84f)))
            orbs.add(orb)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (orbs.isEmpty()) return

        val n = orbs.size
        val w = width.toFloat()
        val h = height.toFloat()
        // 앵커에서 화면 중앙을 향하는 방향을 기준으로 부채꼴 배치(코너에 있어도 화면 안쪽으로 퍼짐).
        val baseAngle = atan2((h / 2f) - anchorY, (w / 2f) - anchorX)
        val span = Math.toRadians(150.0).toFloat()
        val radius = dp(140f).toFloat()

        for (i in 0 until n) {
            val orb = orbs[i]
            val frac = if (n == 1) 0.5f else i.toFloat() / (n - 1)
            val angle = baseAngle - span / 2f + span * frac
            val cw = orb.width.toFloat()
            val ch = orb.height.toFloat()
            var cx = anchorX + radius * cos(angle)
            var cy = anchorY + radius * sin(angle)
            cx = cx.coerceIn(cw / 2f, w - cw / 2f)
            cy = cy.coerceIn(ch / 2f, h - ch / 2f)
            // 자식은 (0,0) 원점에 추가되므로 목표 좌상단이 곧 "제자리" translation 이 된다.
            orb.setHome(cx - cw / 2f - orb.left, cy - ch / 2f - orb.top)
        }

        if (!entered) {
            entered = true
            playEntrance()
        }
    }

    private fun playEntrance() {
        animate().alpha(1f).setDuration(160L).start()
        val enterOffset = dp(44f).toFloat() // 아래에서 위로 솟는 거리
        orbs.forEachIndexed { i, orb ->
            orb.enterFrom(enterOffset, startDelayMs = i * 45L)
        }
    }

    /** 닫기 연출: 오브는 페이드+축소, 스크림은 페이드아웃 → 끝나면 윈도우 제거. */
    fun close() {
        if (closing) return
        closing = true
        isClickable = false
        animate().alpha(0f).setDuration(170L).start()
        if (orbs.isEmpty()) {
            postDelayed({ onRemove() }, 170L)
            return
        }
        var remaining = orbs.size
        orbs.forEach { orb ->
            orb.exit {
                remaining--
                if (remaining == 0) onRemove()
            }
        }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).roundToInt()
}
