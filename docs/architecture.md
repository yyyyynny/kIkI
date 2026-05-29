# LangSense — 아키텍처 문서

## 컴포넌트 흐름도

```
[블루투스 키보드 입력]
        │
        ▼
[Android 시스템]
  IME 서브타입 변경
  시스템 팝업 발화 (Samsung One UI)
        │
        ▼
[LangSenseAccessibilityService]
  ├── onAccessibilityEvent()
  │     TYPE_WINDOW_STATE_CHANGED    → ImeStateDetector
  │     TYPE_VIEW_TEXT_SELECTION_CHANGED → TextSelectionMonitor
  │
  └── onKeyEvent()
        포커스 확인 → KeyEventMonitor

[ImeStateDetector]
  InputMethodManager.getCurrentInputMethodSubtype()
  + Samsung 팝업 텍스트 fallback 파싱 (ImeLocaleParser)
        │
        ▼ 언어 변경 감지 시
[OverlayManager]
  ├── FlashOverlayView.show(lang)    → 전체화면 플래시
  └── BadgeOverlayView.update(lang)  → 상시 배지 갱신

[KeyEventMonitor]
  포커스 없는 키 3회 → FlashOverlayView.showNoFocus()

[TextSelectionMonitor]
  드래그 선택 감지
  → HangulConverter.detectEnglishToKorean() 신뢰도 판정
  → 신뢰도 ≥ 70% 시 ReplaceChipView.show(변환 미리보기)
  → 사용자 탭 → HangulConverter.convertEngToKor() → ACTION_SET_TEXT
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
// 이전 상태와 비교하여 locale 변경 시에만 플래시 발동
```

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
