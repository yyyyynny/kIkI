package com.langsense.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** 간편 메뉴 항목(표시 라벨 + 탭 동작). */
data class QuickMenuItem(val label: String, val onClick: () -> Unit)

/**
 * 플로팅 래디얼 메뉴 (추가 기능 1) — **design/reference/radialmenu.html 충실 이식**.
 *
 * 배지(앵커)를 탭하면 전체화면 스크림 위로 살아있는 유리 칩([RadialOrbView])들이 부채꼴로
 * 펼쳐진다. 참고 파일의 연출을 그대로 옮겼다:
 *  - **곡선 선**: 배지→칩 스포크 5 + 인접 칩 호 4. 중점을 배지 쪽으로 당긴 2차 베지어,
 *    양끝은 배지/칩 가장자리까지 트리밍. glow+crisp 2겹, 칩 터치 중 연결선 하이라이트.
 *  - **빛 점(travel dot)**: 각 곡선을 따라 흐른다(속도 ≈95dp/s, 선별 0.5s 스태거, 양끝 페이드).
 *  - **살아있는 칩**: 코너 반경 morph(칩별 랜덤 주기 3~5s) + 부유(진폭 5~10dp·주기 3.8~5.3s
 *    랜덤, 지연 i×0.42s).
 *  - **탭 버스트**: 5~8개 입자 방사(0.4s) + 스프링 바운스 후 수납.
 *  - **트윙클 별 8개 + 부유 먼지**가 메뉴 영역에 흩어져 명멸.
 * 참고 파일처럼 선/별/먼지는 칩 최종 위치 기준의 **정적 지오메트리**이며(부유는 트리밍 여백이
 * 흡수), 펼침 진행도에 따라 페이드된다.
 *
 * 저사양 모드([reduceMotion]): 연속 애니메이션(모프/부유/빛 점/먼지) 없이 정적 표시(별은
 * 낮은 고정 알파). 펼침/수납/탭 버스트는 유지.
 *
 * 모든 색/치수/타이밍은 [RadialMenuStyle] 에서 가져온다(하드코딩 금지 — 원본은 참고 HTML).
 * 라벨 텍스트는 주입받은 [QuickMenuItem] 의 것을 그대로 쓴다(레이블 임의 변경 금지).
 */
@SuppressLint("ViewConstructor")
class QuickMenuOverlayView(
    context: Context,
    /**
     * 앵커(배지 중심) 좌표를 그때그때 다시 조회하는 콜백(Bug 6). 생성 시점 좌표를 고정값으로 들고
     * 있으면, 메뉴가 열린 채로 화면이 회전해 배지 위치가 바뀌어도 예전 좌표를 중심으로 부채꼴이
     * 펼쳐진다. onLayout(회전 시에도 호출됨)마다 이 콜백으로 최신 좌표를 다시 읽어 어긋남을 없앤다.
     */
    private val anchorProvider: () -> Pair<Int, Int>,
    items: List<QuickMenuItem>,
    /** 저사양(움직임 줄이기) 모드: 펼친 뒤 연속 애니메이션을 끈다(펼침/수납은 유지). */
    private val reduceMotion: Boolean,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    // ── 상태 ─────────────────────────────────────────────────────────────────
    private var anchorX = 0f
    private var anchorY = 0f

    private val orbs = ArrayList<RadialOrbView>()
    private val finalCx = ArrayList<Float>()
    private val finalCy = ArrayList<Float>()

    private var entered = false
    private var collapsing = false
    /** 펼침 진행도(0=수납, 1=펼침). 선/스크림/입자 알파 및 위치 보간 기준. */
    private var expandFraction = 0f
    private var animator: ValueAnimator? = null

    /** 살아있는 연출(모프/부유/빛 점/별/먼지)의 프레임 틱. 메뉴가 붙어 있는 동안만 돈다. */
    private var liveAnimator: ValueAnimator? = null
    /** 연출 기준 시각(uptime) — 모든 주기 계산의 t0. */
    private var openedAt = 0L

    /** 펼침 애니메이션이 계산한 칩별 기준 translation(부유 오프셋은 여기에 더한다). */
    private val baseTx: FloatArray
    private val baseTy: FloatArray

    /** 터치 중인 칩 인덱스(-1 = 없음) — 연결선 하이라이트용(참고 .lit). */
    private var litIndex = -1

    // ── 칩별 랜덤 파라미터(참고 파일이 랜덤을 의도 — 살아있는 느낌) ─────────────
    private val rnd = Random()
    private val morphPeriodMs = LongArray(items.size) {
        randLong(RadialMenuStyle.MORPH_PERIOD_MIN_MS, RadialMenuStyle.MORPH_PERIOD_MAX_MS)
    }
    private val morphPhase = FloatArray(items.size) { rnd.nextFloat() }
    private val bobAmpPx = FloatArray(items.size) {
        dpF(randF(RadialMenuStyle.BOB_AMP_MIN_DP, RadialMenuStyle.BOB_AMP_MAX_DP))
    }
    private val bobPeriodMs = LongArray(items.size) {
        randLong(RadialMenuStyle.BOB_PERIOD_MIN_MS, RadialMenuStyle.BOB_PERIOD_MAX_MS)
    }

    // ── 곡선 선(스포크+호) 지오메트리 — onLayout 에서 재계산 ─────────────────────
    private class CurvedLine(
        val ax: Float, val ay: Float,   // 시작(트리밍 후)
        val cx: Float, val cy: Float,   // 2차 베지어 제어점
        val bx: Float, val by: Float,   // 끝(트리밍 후)
        val lengthPx: Float,
        val orbA: Int, val orbB: Int,   // 연결된 칩 인덱스(-1 = 배지)
        val path: Path
    )

    private val lines = ArrayList<CurvedLine>()

    // ── 별/먼지(메뉴 영역에 랜덤 배치 — onLayout 에서 생성) ─────────────────────
    private class Star(val x: Float, val y: Float, val sizePx: Float, val periodMs: Long, val delayMs: Long)
    private class Dust(
        val x: Float, val y: Float, val rPx: Float,
        val alphaLo: Float, val periodMs: Long, val phase: Float,
        val driftX: Float, val driftY: Float
    )

    private val stars = ArrayList<Star>()
    private val dusts = ArrayList<Dust>()

    // ── 탭 버스트 입자 ────────────────────────────────────────────────────────
    private class Burst(val x: Float, val y: Float, val angle: Float, val dist: Float, val startAt: Long)

    private val bursts = ArrayList<Burst>()

    private val overshoot = OvershootInterpolator(RadialMenuStyle.OVERSHOOT_TENSION)

    // ── 페인트 ───────────────────────────────────────────────────────────────
    private val scrimPaint = Paint().apply { color = RadialMenuStyle.SCRIM_COLOR }
    // 참고 파일의 선 글로우는 blur 지만, 전체화면 뷰에 BlurMaskFilter 를 쓰면 SW 레이어가 강제돼
    // 매 프레임 화면 전체를 다시 래스터화한다(빛 점 애니메이션과 함께면 치명적). 넓은 저알파
    // 스트로크 2겹(칩 자체의 글로우가 blur 담당)으로 근사한다.
    private val lineGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RadialMenuStyle.LINE_GLOW
        strokeWidth = dpF(RadialMenuStyle.LINE_GLOW_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND
    }
    private val lineCrispPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RadialMenuStyle.LINE_CRISP
        strokeWidth = dpF(RadialMenuStyle.LINE_CRISP_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RadialMenuStyle.STAR_COLOR }
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RadialMenuStyle.DUST_COLOR }
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RadialMenuStyle.BURST_COLOR }
    private val starPath = Path()

    /** 칩 뷰 크기(px): 폭 = 칩 + 좌우 글로우 여백, 높이 = 글로우 + 칩 + 라벨. */
    private val orbViewW = dp(RadialMenuStyle.ORB_W_DP + 2 * RadialMenuStyle.ORB_GLOW_SPREAD_DP)
    private val orbViewH = dp(
        RadialMenuStyle.ORB_GLOW_SPREAD_DP + RadialMenuStyle.ORB_H_DP + RadialMenuStyle.LABEL_AREA_DP
    )

    init {
        setWillNotDraw(false) // 스크림/선/입자를 직접 그린다
        isClickable = true
        setOnClickListener { requestCollapse() } // 빈 곳(스크림) 탭 → 닫기

        baseTx = FloatArray(items.size)
        baseTy = FloatArray(items.size)

        items.forEachIndexed { index, item ->
            val orb = RadialOrbView(context).apply {
                label = item.label
                isClickable = true
                // 첫 프레임 깜빡임 방지: 레이아웃/애니메이션 전까지 숨겨둔다.
                scaleX = 0f
                scaleY = 0f
                alpha = 0f
                setOnClickListener { onOrbTapped(index, item) }
                // 터치 중 연결선 하이라이트(참고 hover→lit). false 반환으로 클릭 처리는 그대로.
                setOnTouchListener { _, ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { litIndex = index; invalidate() }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { litIndex = -1; invalidate() }
                    }
                    false
                }
            }
            addView(orb, LayoutParams(orbViewW, orbViewH))
            orbs.add(orb)
        }
    }

    // ── 레이아웃/지오메트리 ───────────────────────────────────────────────────

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (orbs.isEmpty()) return
        val (ax, ay) = anchorProvider()
        anchorX = ax.toFloat()
        anchorY = ay.toFloat()
        computeFanPositions()
        buildCurvedLines()
        scatterStarsAndDust()
        orbs.forEach {
            it.pivotX = it.width / 2f
            it.pivotY = it.orbCenterY
        }
        if (!entered) {
            entered = true
            openedAt = SystemClock.uptimeMillis()
            startExpand()
            if (!reduceMotion) startLive()
        } else {
            applyFraction(expandFraction)
        }
    }

    private fun computeFanPositions() {
        val orbCenterY = orbs.first().orbCenterY
        val positions = RadialFanLayout.compute(
            anchorX = anchorX,
            anchorY = anchorY,
            screenW = width.toFloat(),
            screenH = height.toFloat(),
            count = orbs.size,
            orbHalfW = orbViewW / 2f,
            orbHalfH = maxOf(orbCenterY, orbViewH - orbCenterY),
            desiredRadius = dpF(RadialMenuStyle.FAN_RADIUS_DP),
            desiredSpanRad = Math.toRadians(RadialMenuStyle.FAN_SPAN_DEG).toFloat(),
            minSpanRad = Math.toRadians(RadialMenuStyle.FAN_MIN_SPAN_DEG).toFloat(),
            minGapPx = dpF(RadialMenuStyle.ORB_MIN_GAP_DP),
            boundsMarginPx = dpF(RadialMenuStyle.BOUNDS_MARGIN_DP)
        )
        finalCx.clear(); finalCy.clear()
        positions.forEach { finalCx.add(it.cx); finalCy.add(it.cy) }
    }

    /**
     * 곡선 선(스포크 5 + 인접 호 4)을 칩 **최종 위치** 기준으로 만든다(참고 파일과 동일 —
     * 부유로 칩이 살짝 움직여도 트리밍 여백이 흡수하므로 선은 다시 계산하지 않는다).
     */
    private fun buildCurvedLines() {
        lines.clear()
        val trimBadge = dpF(RadialMenuStyle.LINE_TRIM_BADGE_DP)
        val trimOrb = dpF(RadialMenuStyle.LINE_TRIM_ORB_DP)
        val trimArc = dpF(RadialMenuStyle.LINE_TRIM_ARC_DP)
        for (i in orbs.indices) {
            addCurvedLine(anchorX, anchorY, finalCx[i], finalCy[i], trimBadge, trimOrb, -1, i)
        }
        for (i in 0 until orbs.size - 1) {
            addCurvedLine(finalCx[i], finalCy[i], finalCx[i + 1], finalCy[i + 1], trimArc, trimArc, i, i + 1)
        }
    }

    private fun addCurvedLine(
        x1: Float, y1: Float, x2: Float, y2: Float,
        trim1: Float, trim2: Float, orbA: Int, orbB: Int
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy).coerceAtLeast(1f)
        if (len <= trim1 + trim2 + dpF(4f)) return // 너무 짧으면(칩이 겹칠 만큼 가까움) 선 생략
        val ux = dx / len
        val uy = dy / len
        val ax = x1 + ux * trim1
        val ay = y1 + uy * trim1
        val bx = x2 - ux * trim2
        val by = y2 - uy * trim2
        // 제어점: 중점을 배지(앵커) 쪽으로 살짝 당김 → 유기적인 곡선(참고 pull 24px)
        val mx = (ax + bx) / 2f
        val my = (ay + by) / 2f
        var cdx = anchorX - mx
        var cdy = anchorY - my
        val cl = hypot(cdx, cdy).coerceAtLeast(1f)
        cdx /= cl; cdy /= cl
        val pull = dpF(RadialMenuStyle.LINE_PULL_DP)
        val cx = mx + cdx * pull
        val cy = my + cdy * pull
        // 곡선 근사 길이(빛 점 속도 계산용)
        val chord = hypot(bx - ax, by - ay)
        val poly = hypot(cx - ax, cy - ay) + hypot(bx - cx, by - cy)
        val curveLen = (chord + poly) / 2f
        val path = Path().apply {
            moveTo(ax, ay)
            quadTo(cx, cy, bx, by)
        }
        lines.add(CurvedLine(ax, ay, cx, cy, bx, by, curveLen, orbA, orbB, path))
    }

    /** 트윙클 별/먼지를 칩·앵커를 감싸는 영역에 랜덤 배치(참고 STARS/dust 이식). */
    private fun scatterStarsAndDust() {
        var minX = anchorX; var maxX = anchorX
        var minY = anchorY; var maxY = anchorY
        for (i in orbs.indices) {
            minX = minOf(minX, finalCx[i]); maxX = maxOf(maxX, finalCx[i])
            minY = minOf(minY, finalCy[i]); maxY = maxOf(maxY, finalCy[i])
        }
        val pad = dpF(56f)
        minX = (minX - pad).coerceAtLeast(0f); maxX = (maxX + pad).coerceAtMost(width.toFloat())
        minY = (minY - pad).coerceAtLeast(0f); maxY = (maxY + pad).coerceAtMost(height.toFloat())
        val w = (maxX - minX).coerceAtLeast(1f)
        val h = (maxY - minY).coerceAtLeast(1f)

        stars.clear()
        repeat(RadialMenuStyle.STAR_COUNT) { i ->
            stars.add(
                Star(
                    x = minX + rnd.nextFloat() * w,
                    y = minY + rnd.nextFloat() * h,
                    sizePx = dpF(randF(RadialMenuStyle.STAR_SIZE_MIN_DP, RadialMenuStyle.STAR_SIZE_MAX_DP)),
                    periodMs = RadialMenuStyle.STAR_PERIOD_BASE_MS + i * RadialMenuStyle.STAR_PERIOD_STEP_MS,
                    delayMs = i * RadialMenuStyle.STAR_DELAY_STEP_MS
                )
            )
        }
        dusts.clear()
        repeat(RadialMenuStyle.DUST_COUNT) {
            dusts.add(
                Dust(
                    x = minX + rnd.nextFloat() * w,
                    y = minY + rnd.nextFloat() * h,
                    rPx = dpF(randF(RadialMenuStyle.DUST_R_MIN_DP, RadialMenuStyle.DUST_R_MAX_DP)),
                    alphaLo = randF(RadialMenuStyle.DUST_ALPHA_LO_MIN, RadialMenuStyle.DUST_ALPHA_LO_MAX),
                    periodMs = randLong(RadialMenuStyle.DUST_PERIOD_MIN_MS, RadialMenuStyle.DUST_PERIOD_MAX_MS),
                    phase = rnd.nextFloat(),
                    driftX = dpF((rnd.nextFloat() - 0.5f) * 2f * RadialMenuStyle.DUST_DRIFT_X_DP),
                    driftY = -dpF(4f + rnd.nextFloat() * RadialMenuStyle.DUST_DRIFT_Y_DP)
                )
            )
        }
    }

    // ── 펼침 / 수납 ───────────────────────────────────────────────────────────

    private fun startExpand() {
        collapsing = false
        applyFraction(0f)
        val total = RadialMenuStyle.EXPAND_DURATION_MS + (orbs.size - 1) * RadialMenuStyle.EXPAND_STAGGER_MS
        runAnimator(from = 0f, to = 1f, duration = total, onEnd = null)
    }

    /** 외부(스크림/항목 탭)에서 수납을 요청. 수납 애니메이션 후 [onDismiss] 로 윈도우 제거. */
    fun requestCollapse() {
        if (collapsing) return
        if (!isAttachedToWindow) return
        collapsing = true
        stopLive()
        runAnimator(
            from = expandFraction, to = 0f,
            duration = RadialMenuStyle.COLLAPSE_DURATION_MS,
            onEnd = { onDismiss() }
        )
    }

    private fun runAnimator(from: Float, to: Float, duration: Long, onEnd: (() -> Unit)?) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            addUpdateListener {
                expandFraction = it.animatedValue as Float
                applyFraction(expandFraction)
            }
            // 자연 종료에서만 onEnd(=윈도우 제거)를 호출(취소 시 재진입 제거 방지).
            var cancelled = false
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (!cancelled) onEnd?.invoke()
                }
            })
            start()
        }
    }

    /** 진행도 [f]를 칩 기준 위치/스케일에 반영하고(부유 오프셋 합산) 전체를 다시 그린다. */
    private fun applyFraction(f: Float) {
        val n = orbs.size
        val total = (RadialMenuStyle.EXPAND_DURATION_MS + (n - 1) * RadialMenuStyle.EXPAND_STAGGER_MS).toFloat()
        val now = SystemClock.uptimeMillis()
        for (i in orbs.indices) {
            val orb = orbs[i]
            val startFrac = (i * RadialMenuStyle.EXPAND_STAGGER_MS) / total
            val endFrac = (i * RadialMenuStyle.EXPAND_STAGGER_MS + RadialMenuStyle.EXPAND_DURATION_MS) / total
            val local = ((f - startFrac) / (endFrac - startFrac)).coerceIn(0f, 1f)
            val eased = overshoot.getInterpolation(local)
            val curCx = lerp(anchorX, finalCx.getOrElse(i) { anchorX }, eased)
            val curCy = lerp(anchorY, finalCy.getOrElse(i) { anchorY }, eased)
            baseTx[i] = curCx - orb.width / 2f - orb.left
            baseTy[i] = curCy - orb.orbCenterY - orb.top
            orb.translationX = baseTx[i]
            orb.translationY = baseTy[i] + bobOffset(i, now)
            orb.scaleX = eased
            orb.scaleY = eased
            orb.alpha = local
        }
        invalidate()
    }

    // ── 살아있는 연출(모프/부유/빛 점/별/먼지) — 프레임 틱 ─────────────────────

    private fun startLive() {
        liveAnimator?.cancel()
        liveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { onLiveTick() }
            start()
        }
    }

    private fun stopLive() {
        liveAnimator?.cancel()
        liveAnimator = null
    }

    private fun onLiveTick() {
        val now = SystemClock.uptimeMillis()
        val elapsed = now - openedAt
        for (i in orbs.indices) {
            // 칩 morph: 코너 반경이 살아 숨쉬듯 오르내림(칩별 랜덤 주기/위상).
            val p = (elapsed.toFloat() / morphPeriodMs[i] + morphPhase[i]) * 2f * PI.toFloat()
            orbs[i].cornerRadiusPx = dpF(RadialMenuStyle.ORB_CORNER_DP) +
                dpF(RadialMenuStyle.ORB_CORNER_MORPH_DP) * sin(p)
            // 부유(bob): 기준 위치 + 오프셋(진행 중 펼침과 합산돼도 안전).
            orbs[i].translationY = baseTy[i] + bobOffset(i, now)
        }
        invalidate() // 빛 점/별/먼지/버스트 다시 그리기
    }

    /** CSS bob(0%→0, 50%→-amp, 100%→0, ease-in-out, 지연 i×0.42s)의 오프셋. */
    private fun bobOffset(i: Int, now: Long): Float {
        if (reduceMotion) return 0f
        val t = now - openedAt - i * RadialMenuStyle.BOB_DELAY_STEP_MS
        if (t <= 0) return 0f
        val progress = (t.toFloat() / bobPeriodMs[i]) % 1f
        val s = sin(PI.toFloat() * progress)
        return -bobAmpPx[i] * s * s // sin² — 0→-amp→0 부드러운 왕복
    }

    // ── 그리기 ───────────────────────────────────────────────────────────────

    override fun dispatchDraw(canvas: Canvas) {
        val a = expandFraction.coerceIn(0f, 1f)
        val scrimBaseAlpha = (RadialMenuStyle.SCRIM_COLOR ushr 24) and 0xFF
        scrimPaint.alpha = (scrimBaseAlpha * a).roundToInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        if (a > 0f) {
            val now = SystemClock.uptimeMillis()
            drawLines(canvas, a)
            if (!reduceMotion) drawTravelDots(canvas, now, a)
            drawStars(canvas, now, a)
            if (!reduceMotion) drawDust(canvas, now, a)
            drawBursts(canvas, now)
        }
        super.dispatchDraw(canvas) // 칩들을 선/입자 위에 그린다
    }

    /** 곡선 스포크/호 — glow + crisp 2겹, 터치 중인 칩의 연결선은 lit(참고 .lit). */
    private fun drawLines(canvas: Canvas, vis: Float) {
        for (line in lines) {
            lineGlowPaint.alpha =
                (((RadialMenuStyle.LINE_GLOW ushr 24) and 0xFF) * vis).roundToInt().coerceIn(0, 255)
            canvas.drawPath(line.path, lineGlowPaint)
            val lit = litIndex >= 0 && (line.orbA == litIndex || line.orbB == litIndex)
            if (lit) {
                lineCrispPaint.color = RadialMenuStyle.LINE_LIT
                lineCrispPaint.strokeWidth = dpF(RadialMenuStyle.LINE_LIT_WIDTH_DP)
            } else {
                lineCrispPaint.color = RadialMenuStyle.LINE_CRISP
                lineCrispPaint.strokeWidth = dpF(RadialMenuStyle.LINE_CRISP_WIDTH_DP)
            }
            lineCrispPaint.alpha =
                (((lineCrispPaint.color ushr 24) and 0xFF) * vis).roundToInt().coerceIn(0, 255)
            canvas.drawPath(line.path, lineCrispPaint)
        }
    }

    /** 빛 점: 각 곡선을 따라 흐름(선별 스태거, 양끝 페이드 — 참고 travel-dot). */
    private fun drawTravelDots(canvas: Canvas, now: Long, vis: Float) {
        val core = dpF(RadialMenuStyle.TRAVEL_DOT_R_DP)
        for ((k, line) in lines.withIndex()) {
            val durMs = line.lengthPx / dpF(RadialMenuStyle.TRAVEL_DOT_SPEED_DP_S) * 1000f
            val t0 = openedAt + k * RadialMenuStyle.TRAVEL_DOT_STAGGER_MS
            if (now < t0 || durMs <= 0f) continue
            val t = ((now - t0) / durMs) % 1f
            // 2차 베지어 위의 점
            val u = 1f - t
            val px = u * u * line.ax + 2 * u * t * line.cx + t * t * line.bx
            val py = u * u * line.ay + 2 * u * t * line.cy + t * t * line.by
            // 양끝 페이드: 0→0.85(10%) 유지(90%)→0
            val edge = when {
                t < 0.1f -> t / 0.1f
                t > 0.9f -> (1f - t) / 0.1f
                else -> 1f
            }
            val alpha = RadialMenuStyle.TRAVEL_DOT_MAX_ALPHA * edge * vis
            if (alpha <= 0.01f) continue
            dotPaint.color = RadialMenuStyle.TRAVEL_DOT_GLOW
            dotPaint.alpha = (((RadialMenuStyle.TRAVEL_DOT_GLOW ushr 24) and 0xFF) * edge * vis)
                .roundToInt().coerceIn(0, 255)
            canvas.drawCircle(px, py, core * 2.4f, dotPaint)
            dotPaint.color = RadialMenuStyle.TRAVEL_DOT_COLOR
            dotPaint.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(px, py, core, dotPaint)
        }
    }

    /** 트윙클 별(십자 2-rect + 살짝 회전). 저사양 모드에선 낮은 고정 알파의 정적 별. */
    private fun drawStars(canvas: Canvas, now: Long, vis: Float) {
        for (star in stars) {
            val alpha: Float
            val rot: Float
            if (reduceMotion) {
                alpha = 0.35f * vis
                rot = 0f
            } else {
                val t = now - openedAt - star.delayMs
                if (t < 0) continue
                val p = (t.toFloat() / star.periodMs) % 1f
                // 참고 values 0;0.85;0 — 삼각 파형
                alpha = RadialMenuStyle.STAR_MAX_ALPHA * (1f - kotlin.math.abs(p * 2f - 1f)) * vis
                rot = 15f * sin(p * PI.toFloat())
            }
            if (alpha <= 0.02f) continue
            starPaint.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
            val h = star.sizePx
            val w = star.sizePx * 0.25f
            starPath.reset()
            starPath.moveTo(0f, -h); starPath.lineTo(w, 0f); starPath.lineTo(0f, h)
            starPath.lineTo(-w, 0f); starPath.close()
            starPath.moveTo(-h, 0f); starPath.lineTo(0f, -w); starPath.lineTo(h, 0f)
            starPath.lineTo(0f, w); starPath.close()
            canvas.save()
            canvas.translate(star.x, star.y)
            canvas.rotate(rot)
            canvas.drawPath(starPath, starPaint)
            canvas.restore()
        }
    }

    /** 부유 먼지: 느리게 떠오르며 명멸(참고 .dust). */
    private fun drawDust(canvas: Canvas, now: Long, vis: Float) {
        val elapsed = now - openedAt
        for (dust in dusts) {
            val p = (elapsed.toFloat() / dust.periodMs + dust.phase) % 1f
            val wave = sin(p * 2f * PI.toFloat())
            val alpha = (dust.alphaLo + RadialMenuStyle.DUST_ALPHA_SPAN * (wave * 0.5f + 0.5f)) * vis
            dustPaint.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(
                dust.x + dust.driftX * wave,
                dust.y + dust.driftY * (wave * 0.5f + 0.5f),
                dust.rPx, dustPaint
            )
        }
    }

    /** 탭 버스트 입자(0.4s 수명 — 참고 burst). */
    private fun drawBursts(canvas: Canvas, now: Long) {
        if (bursts.isEmpty()) return
        val life = RadialMenuStyle.BURST_LIFE_MS.toFloat()
        val it = bursts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val p = (now - b.startAt) / life
            if (p >= 1f) { it.remove(); continue }
            val d = b.dist * p
            burstPaint.alpha = (255 * 0.95f * (1f - p)).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(
                b.x + cos(b.angle) * d,
                b.y + sin(b.angle) * d,
                dpF(RadialMenuStyle.BURST_R_DP) * (1f - 0.8f * p),
                burstPaint
            )
        }
    }

    // ── 탭 처리 ───────────────────────────────────────────────────────────────

    private fun onOrbTapped(index: Int, item: QuickMenuItem) {
        if (collapsing) return
        spawnBurst(finalCx.getOrElse(index) { anchorX }, finalCy.getOrElse(index) { anchorY })
        item.onClick()
        requestCollapse()
    }

    /** 칩 중심에서 입자 5~8개 방사. 살아있는 틱이 없어도(저사양) 짧은 애니메이터로 프레임 공급. */
    private fun spawnBurst(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val count = RadialMenuStyle.BURST_COUNT_MIN +
            rnd.nextInt(RadialMenuStyle.BURST_COUNT_MAX - RadialMenuStyle.BURST_COUNT_MIN + 1)
        repeat(count) { k ->
            val angle = (2.0 * PI * k / count).toFloat() + rnd.nextFloat() * 0.5f
            val dist = dpF(randF(RadialMenuStyle.BURST_DIST_MIN_DP, RadialMenuStyle.BURST_DIST_MAX_DP))
            bursts.add(Burst(x, y, angle, dist, now))
        }
        if (liveAnimator == null) {
            // 저사양 모드: 버스트 수명 동안만 프레임 틱(짧고 유한 — 전력 영향 미미).
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = RadialMenuStyle.BURST_LIFE_MS
                addUpdateListener { invalidate() }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        stopLive()
        super.onDetachedFromWindow()
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun randF(min: Float, max: Float): Float = min + rnd.nextFloat() * (max - min)

    private fun randLong(min: Long, max: Long): Long = min + (rnd.nextFloat() * (max - min)).toLong()

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).roundToInt()

    private fun dpF(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
