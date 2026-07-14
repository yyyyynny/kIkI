package com.langsense.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.TypedValue
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** 간편 메뉴 항목(표시 라벨 + 탭 동작). */
data class QuickMenuItem(val label: String, val onClick: () -> Unit)

/**
 * 플로팅 래디얼 메뉴 (추가 기능 1, orb_mockup.html 목업 v2 이식).
 *
 * 배지(앵커)를 탭하면 전체화면 스크림 위로 비눗방울 오브들이 배지에서 **부채꼴로 펼쳐지는** 메뉴.
 *  - 배치: [RadialFanLayout] 으로 배지 위치에 따라 팬 방향/반지름/각도를 적응(모서리에서도 화면 안,
 *    오브끼리 겹치지 않게 — Bug 3 와 연동).
 *  - 스포크: 배지 → 각 오브를 잇는 발광 선(목업 SVG). 앵커에는 원형 헤일로 + 4꼭지 별 스파클(정적).
 *  - 펼침/수납: 단일 마스터 애니메이터로 오브 위치(앵커→최종)·스케일·스크림·선을 한 프레임에 동기 갱신.
 *  - 닫힘: 빈 곳(스크림) 탭 또는 항목 탭. 항목 탭 시 해당 동작 실행 후 수납 애니메이션과 함께 닫힌다.
 *
 * ### 모션 원칙(불쾌한 골짜기 방지)
 * 과거의 오브별 랜덤 주기/진폭 부유와 스포크 위를 기어다니는 빛 점(travel dot)은 유기체처럼 보여
 * 제거했다. 부유는 **전 오브 동일 주기**([RadialMenuStyle.AMBIENT_PERIOD_MS])에 인덱스 기반 위상만
 * 준 잔잔한 물결이고, 그 외 연속 애니메이션은 없다(무지갯빛 sheen 도 정적).
 *
 * 모든 색/치수/타이밍은 [RadialMenuStyle] 에서 가져온다(하드코딩 금지). 라벨 텍스트는 주입받은
 * [QuickMenuItem] 의 것을 그대로 쓴다(레이블 임의 변경 금지).
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
    /** 저사양(움직임 줄이기) 모드: 펼친 뒤 부유 같은 연속 애니메이션을 끈다(펼침/수납은 유지). */
    private val reduceMotion: Boolean,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    /** 현재 앵커 좌표(픽셀). onLayout 마다 [anchorProvider] 로 갱신된다. */
    private var anchorX: Float = 0f
    private var anchorY: Float = 0f

    private val orbs = ArrayList<RadialOrbView>()
    /** 각 오브의 최종 중심 좌표(부채꼴 배치 결과). 스포크/애니메이션 보간에 사용. */
    private val finalCx = ArrayList<Float>()
    private val finalCy = ArrayList<Float>()

    private var entered = false
    private var collapsing = false
    /** 펼침 진행도(0=수납, 1=완전히 펼침). 선/스크림/스파클 알파 및 위치 보간 기준. */
    private var expandFraction = 0f
    private var animator: ValueAnimator? = null

    /** 휴지 상태 부유 애니메이터 — 펼침이 끝난 뒤, 메뉴가 열려 있는 동안만 무한 반복. */
    private var ambientAnimator: ValueAnimator? = null
    /** 부유 시작 시각(uptime). 절대 시간 기반이라 반복 경계에서 튀지 않고 ease-in 도 가능. */
    private var ambientStartUptime = 0L
    /** 펼침 완료 시점의 오브별 기준 translationY(부유는 이 값에 오프셋을 더해 표현). */
    private var restingTy = FloatArray(0)

    /** 스포크 그리기에서 매 프레임 재사용하는 오브 현재 중심 좌표 버퍼(프레임당 할당 제거). */
    private val drawCx = FloatArray(items.size)
    private val drawCy = FloatArray(items.size)

    private val overshoot = OvershootInterpolator(RadialMenuStyle.OVERSHOOT_TENSION)

    private val scrimPaint = Paint().apply { color = RadialMenuStyle.SCRIM_COLOR }
    private val lineGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RadialMenuStyle.LINE_GLOW
        strokeWidth = dp(RadialMenuStyle.LINE_GLOW_WIDTH_DP).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val lineCrispPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RadialMenuStyle.LINE_CRISP
        strokeWidth = dp(RadialMenuStyle.LINE_CRISP_WIDTH_DP).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val linePath = Path()

    /** 앵커 스파클(헤일로+별) 페인트. 헤일로 셰이더는 앵커가 움직일 때만 다시 만든다. */
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RadialMenuStyle.SPARKLE_STAR }
    private val starPath = Path()
    private var haloShaderX = Float.NaN
    private var haloShaderY = Float.NaN

    /** 오브 뷰 한 변(px): 방울 지름 + 좌우 bloom 여백. 라벨이 방울 안이라 정사각형이다. */
    private val orbSide = dp(RadialMenuStyle.ORB_DIAMETER_DP + 2 * RadialMenuStyle.ORB_GLOW_SPREAD_DP)

    init {
        setWillNotDraw(false) // 스크림/스포크/스파클을 직접 그리기 위해
        isClickable = true
        setOnClickListener { requestCollapse() } // 바깥(스크림) 탭 → 닫기

        items.forEach { item ->
            val orb = RadialOrbView(context).apply {
                label = item.label
                isClickable = true
                // 첫 프레임 깜빡임 방지: 레이아웃/애니메이션 전까지 숨겨둔다(펼침이 앵커 scale0 에서 시작).
                scaleX = 0f
                scaleY = 0f
                alpha = 0f
                // 탭: 해당 동작 실행 후 메뉴 수납.
                setOnClickListener { onOrbTapped(item) }
            }
            addView(orb, LayoutParams(orbSide, orbSide))
            orbs.add(orb)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (orbs.isEmpty()) return
        // 회전 등으로 다시 레이아웃될 때마다 배지의 현재 위치를 다시 읽는다(Bug 6).
        val (ax, ay) = anchorProvider()
        anchorX = ax.toFloat()
        anchorY = ay.toFloat()
        computeFanPositions()
        // 오브의 스케일/이동 기준점을 오브 중심으로 맞춰 배지에서 자라나듯 보이게.
        orbs.forEach {
            it.pivotX = it.width / 2f
            it.pivotY = it.orbCenterY
        }
        if (!entered) {
            entered = true
            startExpand()
        } else {
            // 재배치(회전 등) 시 현재 진행도로 위치만 다시 반영.
            applyFraction(expandFraction)
            // 부유 동작 중이면 기준점도 새 위치로 갱신(어긋남 방지).
            if (ambientAnimator != null) captureRestingPositions()
        }
    }

    /** [RadialFanLayout] 으로 각 오브의 최종 중심 좌표를 계산해 보관. */
    private fun computeFanPositions() {
        val positions = RadialFanLayout.compute(
            anchorX = anchorX,
            anchorY = anchorY,
            screenW = width.toFloat(),
            screenH = height.toFloat(),
            count = orbs.size,
            orbHalfW = orbSide / 2f,
            orbHalfH = orbSide / 2f,
            desiredRadius = dp(RadialMenuStyle.FAN_RADIUS_DP).toFloat(),
            desiredSpanRad = Math.toRadians(RadialMenuStyle.FAN_SPAN_DEG).toFloat(),
            minSpanRad = Math.toRadians(RadialMenuStyle.FAN_MIN_SPAN_DEG).toFloat(),
            minGapPx = dp(RadialMenuStyle.ORB_MIN_GAP_DP).toFloat(),
            boundsMarginPx = dp(RadialMenuStyle.BOUNDS_MARGIN_DP).toFloat()
        )
        finalCx.clear(); finalCy.clear()
        positions.forEach { finalCx.add(it.cx); finalCy.add(it.cy) }
    }

    // ── 펼침 / 수납 ───────────────────────────────────────────────────────────

    private fun startExpand() {
        collapsing = false
        applyFraction(0f) // 앵커 위치(scale 0)에서 시작하도록 먼저 반영
        val total = RadialMenuStyle.EXPAND_DURATION_MS + (orbs.size - 1) * RadialMenuStyle.EXPAND_STAGGER_MS
        // 펼침이 끝나면(자연 종료) 휴지 상태 부유를 시작한다.
        // 저사양 모드면 연속 애니메이션을 아예 시작하지 않아 펼친 뒤 완전히 정적이다(전력 최소).
        runAnimator(from = 0f, to = 1f, duration = total, onEnd = { if (!reduceMotion) startAmbient() })
    }

    /** 외부(스크림/항목 탭)에서 수납을 요청. 수납 애니메이션 후 [onDismiss] 로 윈도우 제거. */
    fun requestCollapse() {
        if (collapsing) return
        // 이미 윈도우에서 떨어졌으면(예: '숨기기'가 배지를 먼저 없애며 메뉴까지 제거) 애니메이션 없이 종료.
        if (!isAttachedToWindow) return
        collapsing = true
        stopAmbient()
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
            // 자연 종료에서만 onEnd(=윈도우 제거)를 호출한다. 취소(강제 제거/새 애니메이터 시작)에서는
            // 호출하지 않아 재진입 제거를 막는다.
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

    /**
     * 진행도 [f](0~1)를 모든 오브 위치/스케일과 스크림/선 다시 그리기에 반영.
     * 펼침/수납을 단일 기준으로 처리하므로 오브와 스포크가 항상 한 프레임에 같이 움직인다.
     */
    private fun applyFraction(f: Float) {
        val n = orbs.size
        val total = (RadialMenuStyle.EXPAND_DURATION_MS + (n - 1) * RadialMenuStyle.EXPAND_STAGGER_MS).toFloat()
        for (i in orbs.indices) {
            val orb = orbs[i]
            // 오브별 스태거: 펼침 땐 위에서부터, 시간축을 진행도로 환산해 개별 구간에 매핑.
            val startFrac = (i * RadialMenuStyle.EXPAND_STAGGER_MS) / total
            val endFrac = (i * RadialMenuStyle.EXPAND_STAGGER_MS + RadialMenuStyle.EXPAND_DURATION_MS) / total
            val local = ((f - startFrac) / (endFrac - startFrac)).coerceIn(0f, 1f)
            val eased = overshoot.getInterpolation(local)

            val fcx = finalCx.getOrElse(i) { anchorX }
            val fcy = finalCy.getOrElse(i) { anchorY }
            // 오브 중심이 (앵커 → 최종) 로 이동하도록 translation 계산(피벗=오브 중심).
            val curCx = lerp(anchorX, fcx, eased)
            val curCy = lerp(anchorY, fcy, eased)
            orb.translationX = curCx - orb.width / 2f - orb.left
            orb.translationY = curCy - orb.orbCenterY - orb.top
            orb.scaleX = eased
            orb.scaleY = eased
            orb.alpha = local
        }
        invalidate() // 스크림/스포크/스파클 다시 그리기
    }

    // ── 휴지(idle) 부유: 전 오브 동일 주기의 잔잔한 물결 ────────────────────────

    /**
     * 메뉴가 완전히 펼쳐진 뒤 오브가 잔잔하게 떠다니는 연출(목업 bob 4.4s). 메뉴가 열려 있는 동안만
     * 도는 무한 반복이라 배터리 영향은 메뉴 표시 시간으로 한정된다.
     *
     * 애니메이터는 **프레임 틱(다시 그리기 트리거)** 으로만 쓰고, 실제 부유 값은 [applyAmbient] 가
     * 절대 시간으로 계산한다. 시작 시 진폭을 0→최대로 끌어올려(ease-in) 펼침 직후 갑자기 흔들리는
     * 부자연스러움을 없앤다. 위상은 인덱스 × [RadialMenuStyle.BOB_PHASE_STEP] 의 **결정적** 오프셋 —
     * 과거의 오브별 랜덤 주기/진폭은 유기체처럼 보여(불쾌한 골짜기) 제거했다.
     */
    private fun startAmbient() {
        if (collapsing) return
        captureRestingPositions()
        ambientStartUptime = SystemClock.uptimeMillis()
        ambientAnimator?.cancel()
        ambientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RadialMenuStyle.AMBIENT_PERIOD_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { applyAmbient() }
            start()
        }
    }

    private fun stopAmbient() {
        ambientAnimator?.cancel()
        ambientAnimator = null
    }

    /** 펼침 완료 시점의 오브별 translationY 를 부유의 기준값으로 보관. */
    private fun captureRestingPositions() {
        restingTy = FloatArray(orbs.size) { orbs[it].translationY }
        ambientStartUptime = SystemClock.uptimeMillis() // 기준점 갱신 시 ease-in 도 다시 시작
    }

    /** 매 프레임 오브에 부유 오프셋을 적용하고(translationY) 스포크를 위해 다시 그린다. */
    private fun applyAmbient() {
        if (restingTy.size != orbs.size) return
        val amp = dp(RadialMenuStyle.BOB_AMP_DP).toFloat()
        val elapsed = (SystemClock.uptimeMillis() - ambientStartUptime).toFloat()
        // 진폭 ease-in 엔벌로프: 0→1 로 부드럽게(smoothstep) 올라와 시작 순간 속도가 0 에 가깝다.
        val env = smoothstep01(elapsed / RadialMenuStyle.AMBIENT_RAMP_MS)
        for (i in orbs.indices) {
            val phase = (elapsed / RadialMenuStyle.AMBIENT_PERIOD_MS + i * RadialMenuStyle.BOB_PHASE_STEP) *
                2f * PI.toFloat()
            orbs[i].translationY = restingTy[i] + sin(phase) * amp * env
        }
        invalidate() // 스포크 다시 그리기(오브 현재 위치 기준)
    }

    /** 0..1 구간 smoothstep(3t²−2t³). 범위 밖은 0/1 로 클램프. */
    private fun smoothstep01(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // ── 그리기(스크림 + 스포크 + 앵커 스파클) ───────────────────────────────────

    override fun dispatchDraw(canvas: Canvas) {
        // 스크림: 진행도(0~1)에 비례해 페이드. 색의 알파 채널에 진행도를 곱한다.
        val scrimBaseAlpha = (RadialMenuStyle.SCRIM_COLOR ushr 24) and 0xFF
        scrimPaint.alpha = (scrimBaseAlpha * expandFraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        drawConstellation(canvas)
        super.dispatchDraw(canvas) // 오브들을 선/스파클 위에 그린다
    }

    /** 배지→오브 스포크(글로우/크리스프 2겹) + 앵커의 헤일로·4꼭지 별(목업 SVG, 정적). */
    private fun drawConstellation(canvas: Canvas) {
        if (orbs.isEmpty()) return
        val a = expandFraction.coerceIn(0f, 1f)
        if (a <= 0f) return
        lineGlowPaint.alpha = (((RadialMenuStyle.LINE_GLOW ushr 24) and 0xFF) * a).roundToInt().coerceIn(0, 255)
        lineCrispPaint.alpha = (((RadialMenuStyle.LINE_CRISP ushr 24) and 0xFF) * a).roundToInt().coerceIn(0, 255)

        // 오브의 현재 중심(애니메이션 반영) 좌표 — 매 프레임 할당하지 않도록 재사용 버퍼에 채운다.
        val cx = drawCx
        val cy = drawCy
        for (i in orbs.indices) {
            val orb = orbs[i]
            cx[i] = orb.translationX + orb.left + orb.width / 2f
            cy[i] = orb.translationY + orb.top + orb.orbCenterY
        }

        val ax = anchorX
        val ay = anchorY
        // 스포크: 배지 → 각 오브 (목업은 스포크만 — 인접 오브 간 연결선은 두지 않는다)
        for (i in orbs.indices) {
            linePath.reset()
            linePath.moveTo(ax, ay)
            linePath.lineTo(cx[i], cy[i])
            canvas.drawPath(linePath, lineGlowPaint)
            canvas.drawPath(linePath, lineCrispPaint)
        }

        drawAnchorSparkle(canvas, ax, ay, a)
    }

    /** 앵커(배지 중심)의 원형 헤일로 + 4꼭지 별. 정적이며 펼침 진행도에 알파만 연동. */
    private fun drawAnchorSparkle(canvas: Canvas, ax: Float, ay: Float, vis: Float) {
        val haloR = dp(RadialMenuStyle.SPARKLE_HALO_RADIUS_DP).toFloat()
        // 헤일로 셰이더는 앵커 좌표가 바뀌었을 때만 재생성(프레임당 할당 방지).
        if (ax != haloShaderX || ay != haloShaderY) {
            haloPaint.shader = RadialGradient(
                ax, ay, haloR,
                intArrayOf(RadialMenuStyle.SPARKLE_HALO_CORE, RadialMenuStyle.SPARKLE_HALO_MID, 0x00CDEEFF),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
            haloShaderX = ax
            haloShaderY = ay
        }
        haloPaint.alpha = (255 * vis).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(ax, ay, haloR, haloPaint)

        // 4꼭지 별: 세로/가로 두 개의 얇은 마름모(목업 SVG path 이식).
        val arm = dp(RadialMenuStyle.SPARKLE_ARM_DP).toFloat()
        val half = dp(RadialMenuStyle.SPARKLE_ARM_HALF_WIDTH_DP).toFloat()
        starPath.reset()
        starPath.moveTo(ax, ay - arm); starPath.lineTo(ax + half, ay)
        starPath.lineTo(ax, ay + arm); starPath.lineTo(ax - half, ay); starPath.close()
        starPath.moveTo(ax - arm, ay); starPath.lineTo(ax, ay - half)
        starPath.lineTo(ax + arm, ay); starPath.lineTo(ax, ay + half); starPath.close()
        starPaint.alpha = (((RadialMenuStyle.SPARKLE_STAR ushr 24) and 0xFF) * vis).roundToInt().coerceIn(0, 255)
        canvas.drawPath(starPath, starPaint)
    }

    // ── 탭 처리 ───────────────────────────────────────────────────────────────

    private fun onOrbTapped(item: QuickMenuItem) {
        if (collapsing) return
        // 해당 기능 실행 후 메뉴 자동 수납(수납 애니메이션 끝나면 윈도우 제거).
        item.onClick()
        requestCollapse()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        stopAmbient()
        super.onDetachedFromWindow()
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).roundToInt()
}
