# kIkI — 아키텍처 문서

> 앱 표시 이름은 **kIkI**. 클래스명 `LangSenseAccessibilityService` 등 식별자는 유지(코드가 진실).

## 컴포넌트 흐름도

```
[블루투스 키보드 입력]
        │
        ▼
[Android 시스템]  IME 서브타입/설정 변경, 시스템 팝업 발화(Samsung One UI)
        │
        ▼
[LangSenseAccessibilityService]            # 클래스명은 식별자(불변)
  ├── onAccessibilityEvent()
  │     TYPE_WINDOW_STATE_CHANGED        → ImeStateDetector (백스톱/삼성 팝업)
  │     TYPE_VIEW_TEXT_SELECTION_CHANGED → TextSelectionMonitor
  │
  └── onKeyEvent()                         # 문자 키 게이트 통과 후에만 포커스 조회(지연 평가)
        1차 활성 윈도우 → 없으면 2차 전체 윈도우 → KeyEventMonitor

[ImeStateDetector]  ── 언어 전환 감지(세 경로 병행) ──
  ① BroadcastReceiver  ACTION_INPUT_METHOD_CHANGED
  ② ContentObserver    selected_input_method_subtype / default_input_method
  ③ 윈도우 이벤트 + Samsung 팝업 텍스트(ImeLocaleParser)
        │  모든 신호 → requestRecheck() 로 합침(coalesce)
        ▼  COALESCE_MS(150ms) 후 단 1회 emitIfChanged → onLanguageChanged
[OverlayManager]
  ├── FlashOverlayView.show(lang)    → 전체화면 플래시 (windowAnimations=0)
  └── BadgeOverlayView.update(lang)  → 상시 배지 갱신(크기/색 applyStyle)

[KeyEventMonitor]
  포커스 없는 문자 키 N회(기본 3) → OverlayManager.showNoFocusWarning() (쿨다운 2.5s)

[TextSelectionMonitor]
  드래그 선택 감지
  → HangulConverter.detectEnglishToKorean() 신뢰도 판정(mapRatio × composeRatio)
  → 신뢰도 ≥ 임계값(기본 70%) 시 ReplaceChipView.show(변환 미리보기)
  → 사용자 탭 → HangulConverter.convertEngToKor() → ACTION_SET_TEXT (실패 시 클립보드 fallback)
```

---

## 서비스 생명주기

```
앱 설치
  └── MainActivity (온보딩)
        ├── SYSTEM_ALERT_WINDOW 권한 요청
        └── 접근성 서비스 활성화 안내

접근성 서비스 활성화
  └── LangSenseAccessibilityService.onServiceConnected()
        ├── OverlayManager 초기화
        ├── BadgeOverlayView 표시 (항상)
        └── ImeStateDetector 초기화 (현재 언어 캐시)

백그라운드 상시 실행
  접근성 서비스 = 시스템이 관리
  → 배터리 최적화 예외 등록 권장 안내만 제공

접근성 서비스 비활성화
  └── onUnbind()
        └── OverlayManager.removeAll()
```

---

## 상태 관리

```kotlin
data class ImeState(
    val locale: String,
    val subtypeId: Int,
    val timestamp: Long
)
// emitIfChanged: 이전 lastState.locale 과 다를 때만 발동(중복 제거 1차).
// requestRecheck: 여러 감지 신호를 COALESCE_MS 동안 합쳐 전환당 1회만 emit(중복 제거 2차).
// → 깜박임 횟수 1 지정 시 정확히 1회. emitCount + Log.d 로 실기기 검증 가능.
```

> **언어 지원**: 한국어/영어 + 기타. **일본어는 비활성화**(ImeLocaleParser/Prefs/Settings 의 ja 분기를
> 삭제 없이 주석 처리). 일본어 IME 는 전용 처리 없이 기타 언어와 같은 일반 경로(회색 `#555555`)로 흐른다.

---

## Android / One UI 버전별 호환성

| API / One UI | 이슈 | 대응 |
|---|---|---|
| API 29~30 | 특이사항 없음 | — |
| API 31~33 | 특이사항 없음 | — |
| API 34 | foregroundServiceType 필수 (접근성 서비스는 해당 없음) | — |
| API 35 | predictive back gesture 영향 없음 | — |
| One UI 3.x~7.x | 팝업 텍스트 패턴 확인 완료 | ImeLocaleParser 매핑 |
| One UI 8.x | 실기기 로그 확인 필요 (TBD) | TBD |
| One UI 9.x | **미지원 (베타)** | 별도 분기 작성 금지 |

---

## 보안 원칙

- 키스트로크 내용 수집 금지
- 선택된 텍스트는 로컬 한영타 판정에만 사용, 외부 전송 없음
- `onKeyEvent()`는 항상 `false` 반환 (이벤트 소비 금지)
- 접근성 이벤트 필터는 필요한 타입만 등록

```xml
<!-- accessibility_service_config.xml -->
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged|typeViewTextSelectionChanged"
    android:accessibilityFeedbackType="feedbackVisual"
    android:accessibilityFlags="flagRetrieveInteractiveWindows|flagRequestFilterKeyEvents"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="50" />
```
