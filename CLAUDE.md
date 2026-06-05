# kIkI — CLAUDE.md

> 이 파일은 Claude Code가 프로젝트를 이해하고 작업하기 위한 핵심 가이드입니다.
> 모든 작업은 이 파일의 명세와 `docs/` 하위 문서를 기준으로 수행하십시오.
> ⚠️ **문서와 코드가 다르면 코드가 진실(source of truth)** 입니다.

---

## 프로젝트 개요

**앱 표시 이름**: kIkI (발음 "키키")
**패키지명 / applicationId**: `com.langsense.app` — *식별자라 불변. 표시 이름만 kIkI 로 변경*
**내부 코드 식별자**: 클래스 `LangSenseAccessibilityService`, 리소스 `Theme.LangSense` 등은 식별자라 유지(표시 텍스트만 교체)

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
- **최소 의존성 원칙**: 외부 라이브러리 최소화 (AndroidX Core, Lifecycle, DynamicAnimation 허용)
  - `androidx.dynamicanimation` 은 발광 오브 메뉴(Feature 5)의 물리 스프링 애니메이션 전용으로
    추가한 소형 공식 라이브러리. Compose 도입 없이 동급 연출을 얻기 위한 의도된 예외.
- **핵심 Android 컴포넌트**:
  - `AccessibilityService` — 키 이벤트/텍스트 선택/윈도우 이벤트 감지 핵심
  - `WindowManager` — 오버레이 UI
  - `InputMethodManager` — 현재 IME 서브타입(언어) 읽기
  - `BroadcastReceiver` (`ACTION_INPUT_METHOD_CHANGED`) + `ContentObserver`
    (`selected_input_method_subtype` / `default_input_method`) — **IME 언어 전환 즉시 감지(주 경로)**
  - `TYPE_WINDOW_STATE_CHANGED` + Samsung 팝업 텍스트 — 백스톱/삼성 내부 토글 보조 경로

---

## 핵심 기능 명세

> 상세 스펙은 `docs/features.md` 참조

### Feature 1: 언어 전환 감지 플래시

IME 언어가 변경될 때 전체 화면에 플래시 오버레이를 표시하여 사용자에게 인지시킴.

- **감지 방법(코드 기준)**: `ImeStateDetector` 가 세 경로를 병행한다.
  1. `BroadcastReceiver` — `ACTION_INPUT_METHOD_CHANGED`
  2. `ContentObserver` — `selected_input_method_subtype` / `default_input_method` 변경
     (한/영 토글 시 시스템 설정이 즉시 바뀌므로 화면 변화 없이도 감지)
  3. `TYPE_WINDOW_STATE_CHANGED` + Samsung One UI 팝업 텍스트(백스톱 / 삼성 내부 토글)
- **중복 발화 합치기(debounce/coalesce)**: 한 번의 전환이 위 경로에서 여러 신호를 만들기 때문에,
  모든 신호를 단일 예약으로 합쳐 마지막 신호 후 `COALESCE_MS`(150ms) 시점에 **한 번만** 발동.
  덕분에 깜박임 횟수 1 지정 시 정확히 1회만 깜박인다(이전엔 신호 중복으로 2회 이상 깜박임).
- 플래시 지속 시간: **100~500ms 설정 가능(기본 200ms)**, 깜박임 횟수 1~5(기본 1)
- 오버레이 추가 시 **윈도우 enter/exit 애니메이션 제거**(`windowAnimations=0`)로 색이 좌→우로
  채워지지 않고 전체 화면이 한 프레임에 꽉 차게 나타남(페이드아웃은 뷰 알파 애니메이션).
- 언어별 배경색(불투명도 85%, 설정에서 #RRGGBB 로 변경 가능):
  - 한국어 (`ko`) → `#CC2D2D`
  - English (`en`) → `#1A6EBD`
  - 기타 → `#555555`
  - ~~日本語 (`ja`) → `#2D8C4E`~~ — **[일본어 비활성화]** 코드 주석 처리(아래 "주의 사항" 참조)
- 중앙 텍스트: 언어명 표시 (`한국어` / `English` / locale 코드)

### Feature 2: 상시 언어 표시 배지

화면 모서리에 현재 입력 언어를 항상 표시하는 소형 오버레이.

- 구현: `WindowManager` + `TYPE_APPLICATION_OVERLAY`
- 위치: 드래그로 변경, 자동 저장 (기본값: 우하단)
- **커스터마이즈(설정에서)**:
  - 표시 ON/OFF
  - 크기 3단계: 소 / **중(기본=기존 외형)** / 대
  - 배경색·글씨색: 플래시와 동일한 공용 색 선택 UI(32색 팔레트 + #RRGGBB 입력) 재사용.
    배경은 `BADGE_BG_ALPHA`(0.8) 적용 → 기본값이면 기존 `#CC000000` 과 동일, 둥근 모서리 6dp.
- 표시 내용: `한` / `EN` (기타 언어는 locale 앞 2자리 대문자) — ~~`日`~~ [일본어 비활성화]

### Feature 3: 포커스 없는 상태 키 입력 경고

입력 포커스가 없는 상태에서 문자 키 입력이 임계값(기본 3회) 이상 감지되면 경고 오버레이 표시.

- 감지: `AccessibilityService.onKeyEvent()` + 포커스 노드 확인
- **오발동 방지(최우선)**: 글자가 실제 입력칸에 들어가면 편집 노드가 `typeViewTextChanged`/
  `typeViewTextSelectionChanged` 이벤트를 낸다. 최근(`RECENT_INPUT_MS`=1.2s) 그런 "입력 실착"이
  있었으면 포커스 조회 결과와 무관하게 포커스 있음으로 간주해 경고를 억제(포커스가 정말 없으면
  그 이벤트가 없으므로 진짜 경고는 유지). → "입력은 되는데 경고가 뜨는" 오발동 해결.
- **저사양 최적화**: 포커스 조회는 비싸므로 **기능 ON + ACTION_DOWN + 비반복 + 실제 문자 키 +
  최근 입력 실착 없음** 게이트를 통과한 뒤에만 지연 평가. 1차는 활성 윈도우만 보는 저비용 조회,
  없을 때만 2차로 전체 윈도우 순회(일시적 null 방어). 모디파이어/반복/단축키/기능 OFF 에선 노드 트리를 안 건드림.
- 표시: `선택되지 않음` 텍스트 오버레이 (Feature 1과 동일한 플래시 방식, 재경고 쿨다운 2.5s)
- 카운터는 포커스 획득 시 초기화. `onKeyEvent` 는 항상 `false` 반환(이벤트 절대 미소비)
- 텍스트 '내용'은 읽지 않음(`source.isEditable` 여부만 확인)

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

**"교체?" 버튼 스펙(코드 기준)**:
- 위치: 화면 상단 중앙 고정 (시스템 텍스트 팝업과 겹침 최소화)
- 외형: 배경 `#1C1C1E`(코너 8dp), 텍스트 흰색 14sp, 최대 너비 280dp, 형식 `「{변환미리보기}」 교체?`
- 터치 영역 방해 금지: `FLAG_NOT_FOCUSABLE` 적용, 탭 후 즉시 소멸 / 미탭 시 2초 후 자동 소멸

**⚠️ 핵심 구현체**: `HangulConverter.kt`
두벌식(QWERTY) ↔ 한국어 변환 로직. 상세 스펙은 `docs/features.md` 참조.

### Feature 5: 발광 오브 간편 메뉴 (배지 탭)

상시 배지를 **탭**(드래그 아님)하면 배지 주위로 **발광하는 원형 오브 버튼**들이 부채꼴로 솟아오르는
간편 메뉴를 띄운다. 빈 곳/항목 탭 시 닫힘. (원신 푸리나 스킬 연출의 빛나는 파랑-보라 구체 스타일)

- 구현: `QuickMenuOverlayView`(전체화면 반투명 스크림) + `OrbView`(Canvas 로 그린 발광 오브 —
  `RadialGradient` 파랑→보라 구체 + `BlurMaskFilter` 외곽 글로우/부드러운 그림자 + 상단 specular
  하이라이트, 라벨 중앙. 별도 리소스 불필요).
- 애니메이션(모두 `SpringAnimation` 물리 기반, `androidx.dynamicanimation`):
  - 등장: 아래에서 위로 스프링 바운스(스태거).
  - 드래그: 손가락을 탄성 있게 추종하고, 놓으면 제자리로 스프링 복귀.
  - 탭: 살짝 눌리는 scale down 피드백.
  - 소멸: 페이드아웃 + 축소.
- 글로우 성능: `Prefs.orbGlowEnabled`(설정 토글, 기본 ON). 저사양 기기에서 끄면 `BlurMaskFilter`
  없이 가벼운 링 글로우로 대체(라이트 모드)되어 성능 저하가 없다.
- 배치: 앵커(배지 중심)에서 **화면 중앙 방향**으로 약 150° 부채꼴, 화면 밖으로 안 나가게 clamp.
- 항목(서비스가 주입): **앱 열기 / 설정 / 플래시 토글 / 한영타 토글 / 배지 숨기기**.
  토글은 탭 시점에 `Prefs` 를 읽어 현재 상태를 뒤집고 토스트로 새 상태(켜짐/꺼짐)를 안내.
- 배지가 사라지거나 서비스 정리 시 메뉴도 함께 제거.

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
LangSenseAccessibilityService (AccessibilityService)   # 클래스명은 식별자(불변)
  ├── ImeStateDetector         — IME 언어 전환 감지(Broadcast+ContentObserver+윈도우팝업, 중복 합치기)
  ├── KeyEventMonitor          — 포커스 없는 문자키 입력 카운트(게이트 후 지연 포커스 조회)
  └── TextSelectionMonitor     — 드래그 선택 + 한영타 판정

OverlayManager (WindowManager 래퍼)
  ├── FlashOverlayView         — 전체화면 플래시 (Feature 1, 3) / windowAnimations=0
  ├── BadgeOverlayView         — 상시 언어 배지 (Feature 2) / 크기·색 applyStyle / 탭→간편 메뉴
  ├── ReplaceChipView          — "교체?" 미니 버튼 (Feature 4)
  └── QuickMenuOverlayView     — 발광 오브 간편 메뉴 (Feature 5) / OrbView 항목(SpringAnimation)

util/
  ├── HangulConverter          — 두벌식↔QWERTY 변환 + 한영타 신뢰도 판정 (순수 Kotlin)
  ├── ImeLocaleParser          — locale 파싱 + One UI 팝업 패턴 매핑 (ja 분기는 주석 비활성화)
  ├── Prefs                    — SharedPreferences 래퍼(모든 설정 단일 진입점, 변경 즉시 적용)
  └── PermissionHelper         — 권한 안내 흐름

SettingsActivity
  └── 지원 언어 토글, 플래시 색/속도/횟수, 배지(ON·크기·배경색·글씨색),
      포커스 경고/한영타(설명 문구 포함) — 공용 colorPickerRow 로 색 UI 공유
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
5. **Accessibility 오용 방지**: 이 서비스는 키로깅, 화면 내용 수집, 외부 전송을 절대 수행하지 않음. 모든 처리는 로컬 온디바이스. `typeViewTextChanged` 도 구독하지만 텍스트 '내용'은 읽지 않고 `source.isEditable` 여부만 본다(포커스 경고 오발동 방지용). 디버그 로그는 언어 코드만 출력.
6. **One UI 9.x 미지원**: 베타 상태로 AccessibilityEvent 동작 변경 가능성 있음. 별도 분기 작성 금지, 추후 별도 대응.
7. **[일본어 비활성화]**: 일본어는 발음 입력 후 한자 변환 단계가 많아 한/영 감지 실효가 낮아 기능을 껐다. **삭제하지 않고 전부 주석 처리**(ImeLocaleParser 의 ja·日本語·日 감지, displayName/badgeLabel, Prefs 의 JA 분기/상수, 설정 체크박스·색상, strings/colors)하여 추후 재도입을 쉽게 함. 주석만 처리했으므로 일본어 IME 입력 시 전용 처리는 사라지고 **기타 언어와 동일한 일반 경로(회색 `#555555`, 라벨 "JA")** 로 흐른다(완전 억제는 아님).
8. **IME 전환 2회 깜박임 — 3중 방어**: 한 전환이 Broadcast/Observer/윈도우 이벤트에서 여러 신호를 만들어 2회 이상 깜박이던 문제를 다음으로 해결.
   - (a) **신호 합치기**: 모든 신호를 단일 예약으로 합쳐 `COALESCE_MS`(150ms) 후 1회만 평가.
   - (b) **발동 후 불응기**(`REFRACTORY_MS`=450ms): 잔여 신호 + 지연된 `currentInputMethodSubtype` 캐시가 "직전 언어"를 다시 발동(다른 색 2번째 깜박임)하는 것을 차단.
   - (c) **`onServiceConnected` 멱등화**: 재연결 시 중복으로 BroadcastReceiver/ContentObserver 가 등록돼 detector 가 누적되면 한 전환에 여러 번 발동하므로, 재진입 시 `cleanup()` 후 재초기화.
   - (d) **렌더 단계 안전망**(`OverlayManager.FLASH_DEDUP_MS`=350ms): 같은 색 플래시가 아주 짧은 간격으로 다시 오면 건너뜀. 위 (a)~(c) 가 근본 방어이고 이건 마지막 시각 방어(원인 진단용 `emitCount` 로그는 그대로 유지).
   `onLanguageChanged` 는 전환당 1회만 호출되도록 했고 `emitCount`+`Log.d` 로 실기기 검증 가능(렌더 안전망이 가려도 로그로 실제 트리거 수 확인 가능). 키 입력 시 전체 윈도우 순회는 게이트 통과 후로 지연.
9. **앱 표시 이름 = kIkI**: 사용자에게 보이는 문구만 kIkI. 패키지/applicationId(`com.langsense.app`), 클래스명(`LangSenseAccessibilityService`), 리소스 id(`Theme.LangSense`), Gradle `rootProject.name` 등 **식별자는 절대 변경 금지**.

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
│   │   │       ├── HangulConverter.kt    ← 두벌식↔QWERTY 변환 핵심 (순수 Kotlin)
│   │   │       ├── ImeLocaleParser.kt    ← locale/팝업 파싱 (ja 분기 주석 비활성화)
│   │   │       ├── Prefs.kt              ← 설정 단일 진입점(SharedPreferences)
│   │   │       └── PermissionHelper.kt
│   │   └── res/
│   │       ├── layout/
│   │       ├── values/
│   │       └── xml/
│   │           └── accessibility_service_config.xml
└── build.gradle.kts
```
