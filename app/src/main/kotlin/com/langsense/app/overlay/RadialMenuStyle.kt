package com.langsense.app.overlay

/**
 * 플로팅 래디얼 메뉴(추가 기능 1)의 모든 시각/치수/타이밍 상수 단일 진입점.
 *
 * **권위 원본은 `design/reference/radialmenu.html`**(사용자 제공 참고 구현) — 색상은 그 CSS 의
 * rgba 값을 그대로 ARGB 정수로 옮겼고(임의 변경 금지), 치수는 dp(밀도 독립) 단위 Float,
 * 타이밍은 ms(Long). px≈dp 로 1:1 환산한다.
 *
 * 핵심(참고 파일의 요구): 오브는 완전한 원이 아니라 **살아 숨쉬는 라운드 사각 유리 칩**
 * (코너 반경이 16↔28 로 계속 morph), 선은 **곡선**이며 빛 점이 흐르고, 탭 버스트·트윙클 별·
 * 부유 먼지가 함께 있어야 한다. 림은 은은하게(강조 테두리 금지), 라벨은 칩 아래.
 *
 * 라벨 텍스트(숨기기/앱/플래시/설정/한영타)는 strings.xml 의 quick_* 리소스를 서비스가 주입하므로
 * 여기서 중복 정의하지 않는다(레이블 임의 변경 금지 — 기존 리소스 그대로 사용).
 */
object RadialMenuStyle {

    // ── 스크림 ────────────────────────────────────────────────────────────────
    /** 전체화면 스크림 — rgba(0,0,0,0.25). */
    const val SCRIM_COLOR = 0x40000000.toInt()

    // ── 유리 칩(오브) — .orb ─────────────────────────────────────────────────
    /** 칩 크기(dp) — 참고 80×56px. */
    const val ORB_W_DP = 80f
    const val ORB_H_DP = 56f

    /** 코너 반경(dp): 기본 20, morph 로 16↔28 을 오간다(orbMorph keyframes). */
    const val ORB_CORNER_DP = 20f
    const val ORB_CORNER_MORPH_DP = 8f // 20±8 → 12..28 이 아니라 sin 파형으로 16~28 근사(참고 참조)

    /** 칩별 morph 주기(ms) 범위 — 참고: 3~5s 랜덤(살아 숨쉬는 느낌). */
    const val MORPH_PERIOD_MIN_MS = 3000L
    const val MORPH_PERIOD_MAX_MS = 5000L

    /** 유리 채움 — rgba(20,60,180,0.12). (backdrop blur 는 Canvas 로 불가 — 글로우/광택으로 보강) */
    const val ORB_FILL = 0x1F143CB4.toInt()

    /** 림 — border 1.5px rgba(180,240,255,0.55). 은은한 유리 막(강조 아님). */
    const val ORB_RIM = 0x8CB4F0FF.toInt()
    const val ORB_RIM_WIDTH_DP = 1.5f

    /** 안쪽 얇은 링 — 1px rgba(127,210,255,0.20), inset 4px. */
    const val ORB_INNER_RING = 0x337FD2FF.toInt()
    const val ORB_INNER_RING_WIDTH_DP = 1f
    const val ORB_INNER_RING_INSET_DP = 4f

    /** 좌상단 광택(스페큘러) — rgba(255,255,255,0.72) → rgba(220,245,255,0.32) → 투명. */
    const val ORB_GLOSS_CORE = 0xB7FFFFFF.toInt()
    const val ORB_GLOSS_MID = 0x52DCF5FF.toInt()

    /** 외곽 글로우 — 0 0 20px 6px rgba(120,200,255,.70) / 0 0 50px 14px rgba(80,150,255,.40). */
    const val ORB_GLOW_NEAR = 0xB278C8FF.toInt()
    const val ORB_GLOW_FAR = 0x665096FF.toInt()
    const val ORB_GLOW_NEAR_BLUR_DP = 8f
    const val ORB_GLOW_FAR_BLUR_DP = 16f

    /** 안쪽 글로우 — inset 0 0 22px 6px rgba(127,210,255,0.12). */
    const val ORB_INSET_GLOW = 0x1F7FD2FF.toInt()
    const val ORB_INSET_GLOW_BLUR_DP = 7f

    /** 글로우가 칩 밖으로 번질 여백(dp) — 뷰 크기 계산용(먼 글로우 blur 를 담는다). */
    const val ORB_GLOW_SPREAD_DP = 18f

    // ── 라벨 — .orb-label (칩 아래) ─────────────────────────────────────────
    const val LABEL_COLOR = 0xFFCDEEFF.toInt()
    const val LABEL_TEXT_SP = 12f
    const val LABEL_GLOW = 0xF27FD2FF.toInt() // rgba(127,210,255,0.95)
    const val LABEL_AREA_DP = 24f

    // ── 선(곡선 스포크 + 인접 호) — .line-glow / .line-crisp ─────────────────
    const val LINE_GLOW = 0x4C4DA8FF.toInt()   // #4da8ff @0.30
    const val LINE_GLOW_WIDTH_DP = 4f
    const val LINE_GLOW_BLUR_DP = 4f
    const val LINE_CRISP = 0x8CCDEEFF.toInt()  // #cdeeff @0.55
    const val LINE_CRISP_WIDTH_DP = 1.2f
    /** 터치 중 연결선 하이라이트 — .lit @0.95 / 1.8px. */
    const val LINE_LIT = 0xF2CDEEFF.toInt()
    const val LINE_LIT_WIDTH_DP = 1.8f
    /** 곡선 제어점: 중점을 배지 쪽으로 당기는 거리(dp) — 참고 pull 24px. */
    const val LINE_PULL_DP = 24f
    /** 선 양끝 트리밍(dp): 배지 가장자리 / 칩 가장자리 / 인접 호 양끝. */
    const val LINE_TRIM_BADGE_DP = 24f
    const val LINE_TRIM_ORB_DP = 40f
    const val LINE_TRIM_ARC_DP = 46f

    // ── 빛 점(travel dot) ────────────────────────────────────────────────────
    const val TRAVEL_DOT_COLOR = 0xFFCDEEFF.toInt()
    const val TRAVEL_DOT_GLOW = 0x807FD2FF.toInt()
    const val TRAVEL_DOT_R_DP = 2.6f
    /** 흐르는 속도(dp/초) — 참고 dur = len/95. */
    const val TRAVEL_DOT_SPEED_DP_S = 95f
    /** 선(스포크·호)별 시작 지연 간격 — 참고 delay = ci*0.5s. */
    const val TRAVEL_DOT_STAGGER_MS = 500L
    const val TRAVEL_DOT_MAX_ALPHA = 0.85f

    // ── 탭 버스트 입자 — .burst-particle ────────────────────────────────────
    const val BURST_COLOR = 0xFF7FD2FF.toInt()
    const val BURST_COUNT_MIN = 5
    const val BURST_COUNT_MAX = 8
    const val BURST_DIST_MIN_DP = 34f
    const val BURST_DIST_MAX_DP = 64f
    const val BURST_LIFE_MS = 400L
    const val BURST_R_DP = 3f

    // ── 트윙클 별 — sparkle stars(십자 2-rect, 회전+명멸) ────────────────────
    const val STAR_COLOR = 0xFFCDEEFF.toInt()
    const val STAR_COUNT = 8
    const val STAR_SIZE_MIN_DP = 4f
    const val STAR_SIZE_MAX_DP = 7f
    const val STAR_MAX_ALPHA = 0.85f
    /** 별 i 의 주기/시작 지연 — 참고 dur 2.2+0.32i s, begin 0.48i s. */
    const val STAR_PERIOD_BASE_MS = 2200L
    const val STAR_PERIOD_STEP_MS = 320L
    const val STAR_DELAY_STEP_MS = 480L

    // ── 부유 먼지 — .dust ────────────────────────────────────────────────────
    const val DUST_COLOR = 0xFFCDEEFF.toInt()
    const val DUST_COUNT = 16
    const val DUST_R_MIN_DP = 1f
    const val DUST_R_MAX_DP = 2.5f
    const val DUST_ALPHA_LO_MIN = 0.12f
    const val DUST_ALPHA_LO_MAX = 0.27f
    const val DUST_ALPHA_SPAN = 0.22f
    const val DUST_PERIOD_MIN_MS = 3500L
    const val DUST_PERIOD_MAX_MS = 7500L
    const val DUST_DRIFT_X_DP = 8f
    const val DUST_DRIFT_Y_DP = 12f

    // ── 배치 ─────────────────────────────────────────────────────────────────
    /** 부채꼴 반지름(dp) — 참고 R=180px 를 화면 여건에 맞게 150 으로. */
    const val FAN_RADIUS_DP = 150f
    const val FAN_SPAN_DEG = 140.0
    const val FAN_MIN_SPAN_DEG = 70.0
    /** 인접 칩 중심 최소 거리(dp) — 칩 폭 80 + 여유 16. */
    const val ORB_MIN_GAP_DP = 96f
    const val BOUNDS_MARGIN_DP = 10f

    // ── 펼침/수납 타이밍 ─────────────────────────────────────────────────────
    const val EXPAND_DURATION_MS = 460L
    const val EXPAND_STAGGER_MS = 60L
    const val SCRIM_FADE_MS = 320L
    const val COLLAPSE_DURATION_MS = 260L
    const val TAP_BOUNCE_MS = 420L
    const val OVERSHOOT_TENSION = 1.6f

    // ── 부유(bob) — 참고: 진폭 5~10px·주기 3.8~5.3s 랜덤, 지연 i×0.42s ────────
    const val BOB_AMP_MIN_DP = 5f
    const val BOB_AMP_MAX_DP = 10f
    const val BOB_PERIOD_MIN_MS = 3800L
    const val BOB_PERIOD_MAX_MS = 5300L
    const val BOB_DELAY_STEP_MS = 420L
}
