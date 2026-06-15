package com.langsense.app.overlay

/**
 * 플로팅 래디얼 메뉴(추가 기능 1)의 모든 시각/치수/타이밍 상수 단일 진입점.
 *
 * 첨부된 radial-menu.html 의 고정값(색상·크기·반지름·각도·애니메이션 타이밍)을 코드 전역에서
 * 일관되게 참조하도록 한곳에 모아 변수화했다(하드코딩 금지 요건). 색상은 HTML 의 rgba 값을
 * 그대로 ARGB 정수로 옮겼고(임의 변경 금지), 치수는 dp(밀도 독립) 단위 Float, 타이밍은 ms(Long).
 *
 * 라벨 텍스트(숨기기/앱/플래시/설정/한영타)는 strings.xml 의 quick_* 리소스를 서비스가 주입하므로
 * 여기서 중복 정의하지 않는다(레이블 임의 변경 금지 — 기존 리소스 그대로 사용).
 */
object RadialMenuStyle {

    // ── 색상 (HTML rgba → ARGB, 임의 변경 금지) ────────────────────────────────
    /** 전체화면 스크림 — rgba(0,0,0,0.25). */
    const val SCRIM_COLOR = 0x40000000.toInt()

    /**
     * 오브(유리구슬) 채움. HTML 은 backdrop-filter(프로스트 글래스)라 Canvas 로 직접 재현이 불가해,
     * 같은 파란 계열의 위(밝음)→아래(기본 rgba(20,60,180,0.12)) 세로 그라데이션으로 유리 질감을 근사한다.
     */
    const val ORB_FILL_TOP = 0x384678D2.toInt()    // 윗부분(밝은 유리) 근사
    const val ORB_FILL_BOTTOM = 0x1F143CB4.toInt() // rgba(20,60,180,0.12) 원본 기본색

    /** 오브 테두리(시안 림) — rgba(180,240,255,0.55). */
    const val ORB_RIM = 0x8CB4F0FF.toInt()

    /** 오브 외곽 글로우(가까운/먼) — rgba(120,200,255,0.70) / rgba(80,150,255,0.40). */
    const val ORB_GLOW_INNER = 0xB278C8FF.toInt()
    const val ORB_GLOW_OUTER = 0x665096FF.toInt()

    /** 상단 광택(스페큘러) — rgba(255,255,255,0.72) 중심 → 투명. */
    const val ORB_GLOSS = 0xB7FFFFFF.toInt()

    /** 안쪽 얇은 링 — rgba(127,210,255,0.20). */
    const val ORB_INNER_RING = 0x337FD2FF.toInt()

    /** 라벨 글자색/글로우 — #cdeeff / rgba(127,210,255,0.95). */
    const val LABEL_COLOR = 0xFFCDEEFF.toInt()
    const val LABEL_GLOW = 0xF27FD2FF.toInt()

    /** 별자리(constellation) 선 — glow:#4da8ff@0.30, crisp:#cdeeff@0.55. */
    const val LINE_GLOW = 0x4C4DA8FF.toInt()
    const val LINE_CRISP = 0x8CCDEEFF.toInt()

    /** 선 위를 흐르는 빛 점(travel dot) — HTML .travel-dot fill #cdeeff. */
    const val TRAVEL_DOT_COLOR = 0xFFCDEEFF.toInt()
    const val TRAVEL_DOT_GLOW = 0x807FD2FF.toInt()

    // ── 치수 (dp; 밀도 변환은 사용처에서) ──────────────────────────────────────
    /**
     * 오브 유리구슬 크기(dp). 피드백: 네모(둥근 사각형)가 "큼직한 네모 칸" 같아 지저분 →
     * **정원(가로=세로)** 으로 그려 깔끔한 유리구슬로 바꾸고 크기도 더 줄였다(라벨은 오브 아래).
     */
    const val ORB_W_DP = 46f
    const val ORB_H_DP = 46f

    /** 라벨이 들어갈 오브 아래 영역(dp). (HTML 은 라벨이 오브 아래) */
    const val LABEL_AREA_DP = 20f

    /** 부채꼴 이상적 반지름(dp). 공간 충분 시 그대로 사용(RadialFanLayout 의 desiredRadius). */
    const val FAN_RADIUS_DP = 116f

    /** 부채꼴 펼침 각도(도). HTML 140°(110°~250°). 모서리에서 화면에 넣기 위해 최소 각도까지 좁힘. */
    const val FAN_SPAN_DEG = 140.0
    const val FAN_MIN_SPAN_DEG = 70.0

    /** 인접 오브 중심 거리 최소값(dp) — 겹침 방지(오브 지름 46 + 여유 8). */
    const val ORB_MIN_GAP_DP = 54f

    /** 화면 가장자리 여백(dp). */
    const val BOUNDS_MARGIN_DP = 10f

    /** 오브 테두리/링/선 굵기(dp). */
    const val ORB_RIM_WIDTH_DP = 1.5f
    const val ORB_INNER_RING_WIDTH_DP = 1f
    const val ORB_CORNER_DP = 14f // (현재 오브는 정원으로 그려 미사용 — 추후 모양 변경 대비 보존)
    const val LINE_GLOW_WIDTH_DP = 3.5f
    const val LINE_CRISP_WIDTH_DP = 1.2f

    /** 외곽 글로우가 오브 밖으로 번지는 정도(dp). */
    const val ORB_GLOW_SPREAD_DP = 8f

    /** 라벨 글자 크기(sp). HTML 12px. */
    const val LABEL_TEXT_SP = 11f

    /** 빛 점 반지름(dp). */
    const val TRAVEL_DOT_RADIUS_DP = 2.6f

    // ── 애니메이션 타이밍(ms) ────────────────────────────────────────────────
    /** 오브 펼침 1개 지속(HTML 0.46s, overshoot). */
    const val EXPAND_DURATION_MS = 460L

    /** 오브별 펼침 시작 지연(스태거, HTML 60ms). */
    const val EXPAND_STAGGER_MS = 60L

    /** 스크림/별자리 페이드(HTML 0.32s). */
    const val SCRIM_FADE_MS = 320L

    /** 오브 수납(collapse) 지속 — 펼침보다 조금 빠르게. */
    const val COLLAPSE_DURATION_MS = 260L

    /** 오브 탭 바운스(HTML 0.42s). */
    const val TAP_BOUNCE_MS = 420L

    /** OvershootInterpolator 장력(HTML cubic-bezier(0.34,1.56,0.64,1) 의 튕김 근사). */
    const val OVERSHOOT_TENSION = 1.6f

    // ── 휴지(idle) 상태 앰비언트 모션 — 메뉴가 열려 있는 동안만 동작(배터리 영향 한정) ──────────
    /**
     * 부유(bob) 기준 한 주기(ms). 오브마다 [BOB_PERIOD_JITTER] 범위로 살짝 달라져(랜덤) 모두
     * 똑같이 움직이는 "하드코딩 느낌"을 없앤다. 절대 시간 기반이라 반복 경계에서 튀지 않는다.
     */
    const val AMBIENT_PERIOD_MS = 3000L

    /**
     * 부유 시작 시 진폭을 0→최대로 부드럽게 끌어올리는 시간(ms). 펼침이 끝나자마자 갑자기
     * 큰 속도로 흔들려 부자연스럽던 문제(피드백)를 없애기 위한 ease-in 엔벌로프 길이.
     */
    const val AMBIENT_RAMP_MS = 900L

    /** 오브 부유(bob) 진폭(dp) — 위아래로 살짝 떠다님(HTML bob -5~-10px). */
    const val BOB_AMP_DP = 5f

    /** 부유 주기/진폭 랜덤 변동 폭(±비율). 오브마다 미세하게 달라 자연스러운 물결이 된다. */
    const val BOB_PERIOD_JITTER = 0.28f
    const val BOB_AMP_JITTER = 0.22f

    /** 빛 점이 스포크를 한 번 흐르는 데 걸리는 기준 시간(ms) — 길수록 천천히 흐름. */
    const val TRAVEL_DOT_PERIOD_MS = 1500L

    /** 빛 점 속도 랜덤 변동 폭(±비율) — 스포크마다 흐름 속도가 약간씩 다르게. */
    const val TRAVEL_DOT_SPEED_JITTER = 0.25f
}
