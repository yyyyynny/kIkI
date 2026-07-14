package com.langsense.app.overlay

/**
 * 플로팅 래디얼 메뉴(추가 기능 1)의 모든 시각/치수/타이밍 상수 단일 진입점.
 *
 * 사용자 승인 원본 디자인은 `claude/intelligent-edison-kwQHu` 브랜치의 `design/orb_mockup.html`
 * **목업 v2(입체 비눗방울)** 다. 색상은 그 CSS 의 rgba 값을 그대로 ARGB 정수로 옮겼고(임의 변경
 * 금지), 치수는 dp(밀도 독립) 단위 Float, 타이밍은 ms(Long).
 *
 * 모션 원칙(불쾌한 골짜기 방지): 오브마다 제각각인 랜덤 주기/진폭 부유와 선 위를 흐르는 빛 점은
 * 유기체처럼 보여 제거했다. 부유는 전 오브 **동일 주기의 잔잔한 물결**(인덱스 기반 위상)만 남긴다.
 *
 * 라벨 텍스트(숨기기/앱/플래시/설정/한영타)는 strings.xml 의 quick_* 리소스를 서비스가 주입하므로
 * 여기서 중복 정의하지 않는다(레이블 임의 변경 금지 — 기존 리소스 그대로 사용).
 */
object RadialMenuStyle {

    // ── 색상 (orb_mockup.html rgba → ARGB, 임의 변경 금지) ─────────────────────
    /** 전체화면 스크림 — rgba(0,0,0,0.25). */
    const val SCRIM_COLOR = 0x40000000.toInt()

    /**
     * 비눗방울 구체 채움 — 목업 `.orb` background 의 radial-gradient 5-stop.
     * 중심이 좌상(36%, 30%)으로 치우친 오프셋 구체라 입체감이 난다.
     */
    const val ORB_SPHERE_0 = 0x8CE4FBFF.toInt() // rgba(228,251,255,0.55) @ 0%
    const val ORB_SPHERE_1 = 0x5787C8FF.toInt() // rgba(135,200,255,0.34) @ 28%
    const val ORB_SPHERE_2 = 0x3D3064CD.toInt() // rgba(48,100,205,0.24) @ 58%
    const val ORB_SPHERE_3 = 0x57163087.toInt() // rgba(22,48,135,0.34) @ 84%
    const val ORB_SPHERE_4 = 0x750C1C5C.toInt() // rgba(12,28,92,0.46) @ 100%

    /** 안쪽 밝은 링(비눗방울 막) — border: 2px rgba(200,248,255,0.92). */
    const val ORB_INNER_RING = 0xEBC8F8FF.toInt()

    /** 바깥 흐린 이중 링 — ::after border: 1.5px rgba(160,228,255,0.42), 오브 밖 +5dp. */
    const val ORB_OUTER_RING = 0x6BA0E4FF.toInt()

    /** 림 라이트(우하단 초승달) — .rim radial 70%/76%, rgba(195,242,255,0.55). */
    const val ORB_RIM_LIGHT = 0x8CC3F2FF.toInt()

    /**
     * 무지갯빛 sheen — .iris conic-gradient 를 SweepGradient 로 이식(가장자리 밴드, **정적**).
     * 목업의 opacity .55 를 각 색 알파에 미리 곱했다. 회전 애니메이션은 두지 않는다(차분함·배터리).
     */
    const val SHEEN_CYAN = 0x3878FFF0.toInt()  // rgba(120,255,240,0.40) × .55
    const val SHEEN_PINK = 0x30FF8CE6.toInt()  // rgba(255,140,230,0.34) × .55
    const val SHEEN_AMBER = 0x27FFDE8C.toInt() // rgba(255,222,140,0.28) × .55

    /** 상단 광택(스페큘러) — .spec rgba(255,255,255,0.92) 소프트 blob. */
    const val ORB_GLOSS = 0xCCFFFFFF.toInt()

    /** 외곽 다층 bloom — box-shadow rgba(125,210,255,0.50) / rgba(80,150,255,0.32). */
    const val ORB_GLOW_INNER = 0x807DD2FF.toInt()
    const val ORB_GLOW_OUTER = 0x525096FF.toInt()

    /** 라벨(방울 안 중앙) — 흰색 + 시안 글로우 text-shadow rgba(140,220,255,.95). */
    const val LABEL_COLOR = 0xFFFFFFFF.toInt()
    const val LABEL_GLOW = 0xF28CDCFF.toInt()

    /** 배지→오브 스포크 — 목업 SVG: glow #7fd2ff@0.5(3px) + crisp #d6f1ff@0.75(1px). */
    const val LINE_GLOW = 0x807FD2FF.toInt()
    const val LINE_CRISP = 0xBFD6F1FF.toInt()

    /** 앵커(배지) 스파클 — 목업 SVG 의 원형 헤일로 + 4꼭지 별(정적, 펼침 진행도에 알파 연동). */
    const val SPARKLE_HALO_CORE = 0xE6FFFFFF.toInt() // #ffffff 중심
    const val SPARKLE_HALO_MID = 0x66CDEEFF.toInt()  // #cdeeff 중간
    const val SPARKLE_STAR = 0xF2FFFFFF.toInt()      // 별 fill #fff @ .95

    // ── 치수 (dp; 밀도 변환은 사용처에서) ──────────────────────────────────────
    /** 비눗방울 지름(dp). 라벨이 방울 **안**에 들어가므로(목업) 46 → 56 으로 키움. */
    const val ORB_DIAMETER_DP = 56f

    /** 부채꼴 이상적 반지름(dp). 오브가 커진 만큼 116 → 124. */
    const val FAN_RADIUS_DP = 124f

    /** 부채꼴 펼침 각도(도). 모서리에서 화면에 넣기 위해 최소 각도까지 좁힘. */
    const val FAN_SPAN_DEG = 140.0
    const val FAN_MIN_SPAN_DEG = 70.0

    /** 인접 오브 중심 거리 최소값(dp) — 겹침 방지(오브 지름 56 + 여유 8). */
    const val ORB_MIN_GAP_DP = 64f

    /** 화면 가장자리 여백(dp). */
    const val BOUNDS_MARGIN_DP = 10f

    /** 링/선 굵기(dp). */
    const val ORB_INNER_RING_WIDTH_DP = 2f
    const val ORB_OUTER_RING_WIDTH_DP = 1.5f
    /** 바깥 이중 링이 오브 가장자리에서 떨어진 거리(dp) — 목업 inset:-7px 비율 근사. */
    const val ORB_OUTER_RING_OFFSET_DP = 5f
    const val LINE_GLOW_WIDTH_DP = 3f
    const val LINE_CRISP_WIDTH_DP = 1f

    /** 외곽 bloom 이 오브 밖으로 번지는 정도(dp). 바깥 링(+5dp+1.5dp)도 이 여백 안에 담긴다. */
    const val ORB_GLOW_SPREAD_DP = 10f

    /** 라벨 글자 크기(sp). 목업 110px 방울에 16px 글자 비율을 56dp 에 맞춰 환산. */
    const val LABEL_TEXT_SP = 12.5f

    /** 앵커 스파클 크기(dp): 헤일로 반지름 / 별 팔 길이 / 별 팔 반폭. */
    const val SPARKLE_HALO_RADIUS_DP = 24f
    const val SPARKLE_ARM_DP = 15f
    const val SPARKLE_ARM_HALF_WIDTH_DP = 2.6f

    // ── 애니메이션 타이밍(ms) ────────────────────────────────────────────────
    /** 오브 펼침 1개 지속(0.46s, overshoot). */
    const val EXPAND_DURATION_MS = 460L

    /** 오브별 펼침 시작 지연(스태거, 60ms). */
    const val EXPAND_STAGGER_MS = 60L

    /** 스크림/스포크 페이드(0.32s). */
    const val SCRIM_FADE_MS = 320L

    /** 오브 수납(collapse) 지속 — 펼침보다 조금 빠르게. */
    const val COLLAPSE_DURATION_MS = 260L

    /** 오브 탭 바운스(0.42s). */
    const val TAP_BOUNCE_MS = 420L

    /** OvershootInterpolator 장력(cubic-bezier(.2,.8,.3,1.35) 의 튕김 근사). */
    const val OVERSHOOT_TENSION = 1.6f

    // ── 휴지(idle) 부유 — 메뉴가 열려 있는 동안만 동작(배터리 영향 한정) ──────────
    /**
     * 부유(bob) 주기(ms) — 목업 bob 4.4s. **전 오브 동일 주기**의 잔잔한 물결만 남긴다.
     * (과거의 오브별 랜덤 주기/진폭은 유기체처럼 보여 불쾌한 골짜기를 유발 → 제거)
     */
    const val AMBIENT_PERIOD_MS = 4400L

    /** 부유 시작 시 진폭을 0→최대로 부드럽게 끌어올리는 ease-in 엔벌로프 길이(ms). */
    const val AMBIENT_RAMP_MS = 900L

    /** 부유 진폭(dp) — 목업 ±8px 를 절반 수준으로 낮춰 더 차분하게. */
    const val BOB_AMP_DP = 4f

    /** 오브 간 부유 위상 오프셋(주기 비율). 인덱스 × 이 값 — 결정적이고 잔잔한 물결. */
    const val BOB_PHASE_STEP = 0.12f
}
