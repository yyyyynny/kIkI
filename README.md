# kIkI

> 앱 표시 이름은 **kIkI**(발음 "키키"). 패키지/클래스 등 식별자는 `com.langsense.app` /
> `LangSenseAccessibilityService` 그대로 유지(식별자라 불변). 문서와 코드가 다르면 **코드가 진실**.

블루투스 키보드 + Android 태블릿 사용 시 한/영 전환 인지 문제와 "한영타"(영어 자판으로 친 한국어, 예: `dlfjgrp` → `이렇게`) 문제를 해결하는 접근성 기반 유틸리티 앱.

> 프로젝트 명세는 [`CLAUDE.md`](CLAUDE.md), 기능 상세는 [`docs/features.md`](docs/features.md), 아키텍처는 [`docs/architecture.md`](docs/architecture.md) 참고.

## 기능

| # | 기능 | 핵심 구현체 |
|---|------|------------|
| 1 | 언어 전환 감지 플래시(중복 발화 합쳐 1회만) | `ImeStateDetector` + `FlashOverlayView` |
| 2 | 상시 언어 배지(드래그 이동 + 크기·색 커스텀) | `BadgeOverlayView` |
| 3 | 포커스 없는 키 입력 경고 | `KeyEventMonitor` |
| 4 | 한영타 감지 + "교체?" 인라인 교체 | `HangulConverter` + `TextSelectionMonitor` + `ReplaceChipView` |
| 5 | 배지 탭 → 물방울 간편 메뉴(앱/설정/기능 토글) | `QuickMenuOverlayView` + `WaterDropView` |

- **언어 전환 감지**: `BroadcastReceiver`(`ACTION_INPUT_METHOD_CHANGED`) + `ContentObserver`
  (`selected_input_method_subtype`) + 윈도우 팝업 텍스트를 병행하고, 한 전환에서 쏟아지는 여러 신호를
  합쳐(coalesce, 150ms) **전환당 정확히 1회**만 플래시한다(깜박임 횟수 1 지정 시 1회).
- **언어 지원**: 한국어/영어 + 기타. **일본어는 비활성화**(코드 삭제 없이 주석 처리, 추후 재도입 대비).
- `HangulConverter` 는 두벌식 입력 오토마타를 완전 구현한다(초/중/종성 조합, 복합 중성 `ㅘ/ㅚ/ㅢ`, 복합 종성 `ㄺ/ㄼ/ㅄ`, 연음/도깨비불 처리). 영↔한 양방향 변환과 한영타 신뢰도 판정을 제공하며, 안드로이드 의존성이 없는 순수 Kotlin 이라 JVM 단위 테스트가 가능하다.

## 빌드 (CLI, Android Studio 불필요)

요구사항:
- JDK 17 이상
- Android SDK (Platform 35, Build-Tools) — `sdkmanager "platforms;android-35" "build-tools;35.0.0"`
- `local.properties` 에 SDK 경로 지정 또는 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 환경변수 설정

```bash
# SDK 경로 지정 (예시)
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 디버그 APK 빌드
./gradlew assembleDebug
# 결과물: app/build/outputs/apk/debug/app-debug.apk

# HangulConverter 단위 테스트
./gradlew testDebugUnitTest
```

Gradle Wrapper(8.11.1)가 포함되어 있어 별도 Gradle 설치는 필요 없다. AGP 8.7.3 / Kotlin 2.0.21 사용.

> ⚠️ AGP·AndroidX·Android SDK 는 Google Maven(`maven.google.com`)에서 받는다. 네트워크가 이를 차단하는 환경에서는 빌드가 플러그인 해석 단계에서 실패하므로, 해당 호스트 접근이 가능한 환경에서 빌드해야 한다.

## 설치 후 사용

1. 앱 실행 → **다른 앱 위에 표시** 권한 허용
2. **접근성 서비스** 에서 **kIkI**(목록 표기: "kIkI 언어 감지") 활성화
3. (권장) **배터리 최적화 예외** 등록
4. 앱 내 **설정** 에서 조정 (변경 즉시 적용):
   - 지원 언어(한국어/영어) 토글
   - 플래시 색상(#RRGGBB·32색 팔레트)·속도(100~500ms)·횟수(1~5)
   - 상시 배지: 표시 ON/OFF, **크기(소/중/대)**, **배경색/글씨색**
   - 포커스 없는 키 입력 경고·한영타 신뢰도(각 인라인 설명 포함)

## 지원 범위

- minSdk 29 (Android 10) ~ targetSdk 35 (Android 15)
- Samsung One UI 3.x ~ 8.x (One UI 9.x 및 API 36+ 는 미지원)

## 개인정보 / 보안

키 입력 내용·화면 내용을 수집·저장·전송하지 않는다. 모든 처리는 온디바이스(로컬)에서만 수행되며, `onKeyEvent()` 는 이벤트를 절대 소비하지 않는다(항상 `false` 반환).
