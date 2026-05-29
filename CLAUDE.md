# LangSense — CLAUDE.md

> 이 파일은 Claude Code가 프로젝트를 이해하고 작업하기 위한 핵심 가이드입니다.
> 모든 작업은 이 파일의 명세와 `docs/` 하위 문서를 기준으로 수행하십시오.

---

## 프로젝트 개요

**앱 이름**: LangSense (패키지명: `com.langsense.app`)

블루투스 키보드와 Android 태블릿을 함께 사용할 때 발생하는 두 가지 문제를 해결하는 접근성 기반 유틸리티 앱.

**문제 1**: 한/영 전환이 됐는지 인식하지 못하고 그대로 입력함  
**문제 2**: 영어 자판 상태에서 한국어를 치면 "dlfjgrp" 같은 영타 문자가 입력됨 (한영타)

---

## 지원 범위

| 항목 | 값 |
|---|---|
| 최소 SDK | API 29 (Android 10) |
| 최대 SDK | API 35 (Android 15 / 16) |
| **제외** | API 36+ (Android 17 베타 — 명세 불안정, 추후 별도 대응) |
| 주 타겟 기기 | Samsung Galaxy Tab S6 Lite, Galaxy Tab S9 FE+ |
| One UI 버전 | One UI 3.x ~ **8.x (최대 8.5)** |
| **One UI 제외** | One UI 9.x (베타 테스트 중 — 추후 별도 대응) |

---

## 기술 스택

- **언어**: Kotlin 2.x
- **빌드**: Gradle + AGP 8.x
- **최소 의존성 원칙**: 외부 라이브러리 최소화 (AndroidX Core, Lifecycle만 허용)
- **핵심 Android 컴포넌트**:
  - `AccessibilityService` — IME 이벤트 감지 핵심
  - `WindowManager` — 오버레이 UI
  - `InputMethodManager` — 현재 IME 서브타입(언어) 읽기
  - `BroadcastReceiver` — IME 전체 전환 감지 보조

---

## 핵심 기능 명세

> 상세 스펙은 `docs/features.md` 참조

### Feature 1: 언어 전환 감지 플래시

IME 언어가 변경될 때 전체 화면에 1회 플래시 오버레이를 표시하여 사용자에게 인지시킴.

- 감지 방법: `AccessibilityService.onAccessibilityEvent()` — `TYPE_WINDOW_STATE_CHANGED` 이벤트 수신 후 `InputMethodManager.getCurrentInputMethodSubtype().locale` 변경 여부 확인
- 플래시 지속 시간: **150~250ms** (주변시 인지 최적 범위)
- 언어별 배경색:
  - 한국어 (`ko`) → `#CC2D2D` (불투명도 85%)
  - English (`en`) → `#1A6EBD` (불투명도 85%)
  - 日本語 (`ja`) → `#2D8C4E` (불투명도 85%)
  - 기타 → `#555555` (불투명도 85%)
- 중앙 텍스트: 언어명 표시 (`한국어` / `English` / `日本語` / locale 코드)
- One UI 삼성 시스템 팝업 텍스트 파싱 병행 적용 (기기별 대응)

### Feature 2: 상시 언어 표시 배지

화면 모서리에 현재 입력 언어를 항상 표시하는 소형 오버레이.

- 구현: `WindowManager` + `TYPE_APPLICATION_OVERLAY`
- 위치: 사용자 설정 가능 (기본값: 우하단)
- 크기: 최소화 원칙 (최대 48×24dp)
- 표시 내용: `한` / `EN` / `日`
- 드래그로 위치 변경 가능

### Feature 3: 포커스 없는 상태 키 입력 경고

입력 포커스가 없는 상태에서 키 입력이 3회 이상 감지되면 경고 오버레이 표시.

- 감지: `AccessibilityService.onKeyEvent()` + 현재 포커스된 노드 확인
- 표시: `선택되지 않음` 텍스트 오버레이 (Feature 1과 동일한 플래시 방식)
- 카운터는 포커스 획득 시 초기화

### Feature 4: 한영타 감지 + 인라인 교체

드래그로 선택한 텍스트가 "영타로 입력된 한국어"(한영타) 패턴일 때,
선택 영역 근처에 작은 **"교체?"** 버튼을 띄워 탭 한 번으로 올바른 한국어로 교체.

**동작 흐름**:
1. `TYPE_VIEW_TEXT_SELECTION_CHANGED` 이벤트 감지
2. 선택된 텍스트 추출
3. `HangulConverter.detectEnglishToKorean(text)` 호출 — 한영타 신뢰도 판정
4. 신뢰도 ≥ 70% 이면 화면 상단에 `"교체?"` 미니 오버레이 버튼 표시
5. 사용자가 "교체?" 탭 → `HangulConverter.convertEngToKor(text)` 실행
6. `AccessibilityNodeInfo.performAction(ACTION_SET_TEXT, bundle)` 으로 교체
   - 실패 시 fallback: 클립보드에 복사 후 붙여넣기
7. 버튼 **2초 후 자동 소멸** (시스템 텍스트 선택 팝업 방해 최소화)

**한영타 판정 기준** (`HangulConverter`):
- 입력 텍스트의 각 영문자를 두벌식 자모로 변환 후 한글 조합 시도
- 조합 성공한 음절 비율 ≥ 70% → 한영타로 판정
- 실제 영어 단어일 경우 조합 실패율이 높아 오탐 방지

**"교체?" 버튼 스펙**:
- 위치: 화면 상단 고정 (시스템 텍스트 팝업과 겹침 최소화)
- 크기: 64×32dp, 배경 `#222222`, 텍스트 흰색 14sp
- 터치 영역 방해 금지: `FLAG_NOT_FOCUSABLE` 적용, 탭 후 즉시 소멸

**⚠️ 핵심 구현체**: `HangulConverter.kt`
두벌식(QWERTY) ↔ 한국어 변환 로직. 상세 스펙은 `docs/features.md` 참조.

---

## 권한 목록

```xml
<!-- 필수 -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Android 14+ 필수 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

---

## 아키텍처 개요

```
LangSenseAccessibilityService (AccessibilityService)
  ├── ImeStateDetector         — IME 서브타입 변경 감지
  ├── KeyEventMonitor          — 포커스 없는 키 입력 카운트
  └── TextSelectionMonitor     — 드래그 선택 + 한영타 판정

OverlayManager (WindowManager 래퍼)
  ├── FlashOverlayView         — 전체화면 플래시 (Feature 1, 3)
  ├── BadgeOverlayView         — 상시 언어 배지 (Feature 2)
  └── ReplaceChipView          — "교체?" 미니 버튼 (Feature 4)

util/
  ├── HangulConverter          — 두벌식↔QWERTY 변환 + 한영타 신뢰도 판정
  ├── ImeLocaleParser          — locale 파싱 + One UI 팝업 패턴 매핑
  └── PermissionHelper         — 권한 안내 흐름

SettingsActivity
  └── 배지 위치, 플래시 색상 커스텀, ON/OFF 토글
```

---

## 구현 작업 순서 (Phase)

### Phase 1 — 뼈대 및 권한 흐름
- [ ] Gradle 프로젝트 초기화 (minSdk 29, targetSdk 35)
- [ ] `AndroidManifest.xml` 권한 및 서비스 선언
- [ ] 온보딩 화면: `SYSTEM_ALERT_WINDOW` 권한 → 접근성 서비스 활성화 안내
- [ ] `LangSenseAccessibilityService` 빈 뼈대 등록 확인

### Phase 2 — 핵심 감지 로직
- [ ] `ImeStateDetector`: `onAccessibilityEvent` TYPE_WINDOW_STATE_CHANGED 수신
- [ ] `InputMethodManager.getCurrentInputMethodSubtype()` locale 파싱
- [ ] Samsung One UI 시스템 팝업 텍스트 파싱 보조 로직 (기기별 fallback)
- [ ] 언어 상태 변경 이벤트 내부 브로드캐스트

### Phase 3 — 오버레이 UI
- [ ] `FlashOverlayView`: 전체화면 색상 오버레이 + 언어명 텍스트 + 150~250ms 애니메이션
- [ ] `BadgeOverlayView`: 드래그 가능한 상시 배지
- [ ] Feature 3 포커스 없는 키 입력 경고 오버레이

### Phase 4 — Feature 4 (한영타 교체)
- [ ] `HangulConverter`: 두벌식 QWERTY→한글 자모 매핑 테이블 + 음절 조합 알고리즘
- [ ] `HangulConverter.detectEnglishToKorean()`: 신뢰도 판정 로직
- [ ] `TextSelectionMonitor`: 드래그 선택 감지 + HangulConverter 호출
- [ ] `ReplaceChipView`: "교체?" 미니 버튼 오버레이 (2초 자동 소멸)
- [ ] `AccessibilityNodeInfo.performAction(ACTION_SET_TEXT)` 교체 실행

### Phase 5 — 설정 및 안정화
- [ ] `SettingsActivity`: 배지 위치, 기능별 ON/OFF
- [ ] API 29~35 각 버전별 동작 검증 코드 주석
- [ ] Samsung One UI 3.x / 5.x / 6.x / 7.x / 8.x 팝업 텍스트 패턴 매핑
- [ ] 배터리 최적화 예외 설정 유도 화면

---

## 주의 사항 및 알려진 제약

1. **Samsung One UI 팝업 텍스트**: One UI 버전마다 IME 전환 시스템 팝업의 텍스트가 다를 수 있음. `ImeStateDetector`에 버전별 패턴 매핑 테이블 유지 필요.
2. **Android 14 Foreground Service**: `foregroundServiceType` 미지정 시 크래시 발생. `specialUse` 타입으로 선언할 것.
3. **TYPE_APPLICATION_OVERLAY**: API 26+ 필수. API 29 이상만 지원하므로 별도 분기 불필요.
4. **ACTION_SET_TEXT 호환성**: 일부 앱(WebView 기반, 특수 에디터)에서 동작 안 할 수 있음. 클립보드 fallback 필수 구현.
5. **Accessibility 오용 방지**: 이 서비스는 키로깅, 화면 내용 수집, 외부 전송을 절대 수행하지 않음. 모든 처리는 로컬 온디바이스.
6. **One UI 9.x 미지원**: 베타 상태로 AccessibilityEvent 동작 변경 가능성 있음. 별도 분기 작성 금지, 추후 별도 대응.

---

## 파일 구조 (목표)

```
langsense/
├── CLAUDE.md                  ← 이 파일
├── docs/
│   ├── features.md            ← 기능 상세 명세 + HangulConverter 스펙
│   └── architecture.md        ← 아키텍처 다이어그램
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/langsense/app/
│   │   │   ├── service/
│   │   │   │   ├── LangSenseAccessibilityService.kt
│   │   │   │   ├── ImeStateDetector.kt
│   │   │   │   ├── KeyEventMonitor.kt
│   │   │   │   └── TextSelectionMonitor.kt
│   │   │   ├── overlay/
│   │   │   │   ├── OverlayManager.kt
│   │   │   │   ├── FlashOverlayView.kt
│   │   │   │   ├── BadgeOverlayView.kt
│   │   │   │   └── ReplaceChipView.kt
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── SettingsActivity.kt
│   │   │   └── util/
│   │   │       ├── HangulConverter.kt    ← 두벌식↔QWERTY 변환 핵심
│   │   │       ├── ImeLocaleParser.kt
│   │   │       └── PermissionHelper.kt
│   │   └── res/
│   │       ├── layout/
│   │       ├── values/
│   │       └── xml/
│   │           └── accessibility_service_config.xml
└── build.gradle.kts
```
