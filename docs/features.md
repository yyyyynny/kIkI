# LangSense — 기능 상세 명세

## Feature 1: 언어 전환 플래시

### 트리거 조건
- `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` 이벤트 수신
- 이벤트 수신 직후 `InputMethodManager.getCurrentInputMethodSubtype()` 호출
- 직전 locale과 현재 locale이 다를 경우 플래시 발동

### Samsung One UI 팝업 보조 감지 (fallback)
일부 기기에서 위 방식이 불안정할 경우 시스템 팝업 텍스트로 보조 감지.

| One UI 버전 | 팝업 텍스트 패턴 (한국어 전환) | 팝업 텍스트 패턴 (영어 전환) |
|---|---|---|
| One UI 3.x | `"한국어"` | `"English"` |
| One UI 5.x | `"한국어"` | `"English (US)"` |
| One UI 6.x | `"한국어"` 또는 `"Korean"` | `"English"` |
| One UI 7.x | `"한국어"` | `"English"` |
| One UI 8.x | 실기기 로그로 확인 필요 (TBD) |
| One UI 9.x | **미지원 (베타)** |

패키지명 필터: `packageName == "android"` 또는 `packageName.contains("samsung")` 인 이벤트만 처리

### 오버레이 스펙
```
WindowManager.LayoutParams:
  type    = TYPE_APPLICATION_OVERLAY
  flags   = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN
  width   = MATCH_PARENT
  height  = MATCH_PARENT
  format  = PixelFormat.TRANSLUCENT

애니메이션:
  나타남: 0ms (즉시)
  사라짐: 150~250ms 페이드 아웃
  총 표시 시간: 200ms
```

### 언어별 색상 코드
```kotlin
object LangColors {
    val KOREAN   = Color.parseColor("#CC2D2D")  // 짙은 빨강
    val ENGLISH  = Color.parseColor("#1A6EBD")  // 파랑
    val JAPANESE = Color.parseColor("#2D8C4E")  // 초록
    val DEFAULT  = Color.parseColor("#555555")  // 회색
    const val ALPHA = 0.85f
}
```

---

## Feature 2: 상시 언어 배지

### 표시 스펙
```
크기: 48dp × 24dp
배경: 반투명 검정 (#CC000000)
텍스트: 14sp Bold, 흰색
  한국어 → "한"
  English → "EN"
  日本語 → "日"
  기타 → locale 앞 2자리 대문자

위치 저장: SharedPreferences ("badge_x", "badge_y")
기본 위치: 우하단 (margin 16dp)
```

### 드래그 동작
- `OnTouchListener`로 ACTION_MOVE 감지
- 화면 경계 벗어나지 않도록 clamp 처리
- 드래그 종료 시 위치 자동 저장

---

## Feature 3: 포커스 없는 키 입력 경고

### 감지 로직
```kotlin
override fun onKeyEvent(event: KeyEvent): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false

    val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    if (focusedNode == null || !focusedNode.isEditable) {
        noFocusKeyCount++
        if (noFocusKeyCount >= 3) {
            overlayManager.showNoFocusWarning()
            noFocusKeyCount = 0
        }
    } else {
        noFocusKeyCount = 0
    }
    return false // 키 이벤트 소비 금지
}
```

---

## Feature 4: 한영타 감지 + "교체?" 인라인 버튼

### 개요
영어 자판 상태에서 한국어를 쳐서 생긴 "한영타"(예: `dlfjgrp` → `안드로`)를
드래그 선택하면 상단에 **"교체?"** 버튼을 띄워 탭 한 번에 올바른 한국어로 교체.

### 핵심 유틸: HangulConverter.kt

#### 두벌식(QWERTY → 한국어 자모) 매핑 테이블

```kotlin
object HangulConverter {

    // 소문자 → 한국어 자모 (두벌식 기준)
    private val ENG_TO_JAMO = mapOf(
        'q' to 'ㅂ', 'w' to 'ㅈ', 'e' to 'ㄷ', 'r' to 'ㄱ', 't' to 'ㅅ',
        'y' to 'ㅛ', 'u' to 'ㅕ', 'i' to 'ㅑ', 'o' to 'ㅐ', 'p' to 'ㅔ',
        'a' to 'ㅁ', 's' to 'ㄴ', 'd' to 'ㅇ', 'f' to 'ㄹ', 'g' to 'ㅎ',
        'h' to 'ㅗ', 'j' to 'ㅓ', 'k' to 'ㅏ', 'l' to 'ㅣ',
        'z' to 'ㅋ', 'x' to 'ㅌ', 'c' to 'ㅊ', 'v' to 'ㅍ',
        'b' to 'ㅠ', 'n' to 'ㅜ', 'm' to 'ㅡ'
    )

    // 대문자 → 쌍자음/쌍모음
    private val ENG_UPPER_TO_JAMO = mapOf(
        'Q' to 'ㅃ', 'W' to 'ㅉ', 'E' to 'ㄸ', 'R' to 'ㄲ', 'T' to 'ㅆ',
        'O' to 'ㅒ', 'P' to 'ㅖ'
    )

    // 초성 인덱스 (19개)
    private val CHOSUNG = listOf(
        'ㄱ','ㄲ','ㄳ','ㄴ','ㄵ','ㄶ','ㄷ','ㄸ','ㄹ',
        'ㄺ','ㄻ','ㄼ','ㄽ','ㄾ','ㄿ','ㅀ','ㅁ','ㅂ','ㅃ',
        'ㅄ','ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
    )

    // 중성 인덱스 (21개)
    private val JUNGSUNG = listOf(
        'ㅏ','ㅐ','ㅑ','ㅒ','ㅓ','ㅔ','ㅕ','ㅖ','ㅗ','ㅘ','ㅙ','ㅚ',
        'ㅛ','ㅜ','ㅝ','ㅞ','ㅟ','ㅠ','ㅡ','ㅢ','ㅣ'
    )

    // 종성 인덱스 (28개, 0 = 없음)
    private val JONGSUNG = listOf(
        '\u0000','ㄱ','ㄲ','ㄳ','ㄴ','ㄵ','ㄶ','ㄷ','ㄹ','ㄺ','ㄻ','ㄼ',
        'ㄽ','ㄾ','ㄿ','ㅀ','ㅁ','ㅂ','ㅄ','ㅅ','ㅆ','ㅇ','ㅈ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
    )

    /**
     * 신뢰도 판정: 입력 문자열이 한영타일 가능성 반환 (0.0 ~ 1.0)
     * 영문자를 자모 변환 후 유효한 한글 음절 조합 비율로 판정
     */
    fun detectEnglishToKorean(input: String): Float {
        val letters = input.filter { it.isLetter() }
        if (letters.isEmpty()) return 0f
        
        val convertible = letters.count { ENG_TO_JAMO.containsKey(it) || ENG_UPPER_TO_JAMO.containsKey(it) }
        return convertible.toFloat() / letters.length
    }

    /**
     * 영타 → 한글 변환 (두벌식 조합 알고리즘)
     * 공백, 숫자, 특수문자는 그대로 보존
     */
    fun convertEngToKor(input: String): String {
        val jamos = input.map { ch ->
            ENG_TO_JAMO[ch] ?: ENG_UPPER_TO_JAMO[ch] ?: ch
        }
        return assembleJamos(jamos)
    }

    /**
     * 자모 리스트를 완성형 한글로 조합
     * 한글 유니코드: 0xAC00 + (초성idx * 21 + 중성idx) * 28 + 종성idx
     */
    private fun assembleJamos(jamos: List<Char>): String {
        // 두벌식 조합 알고리즘 구현
        // 초성 + 중성 → 음절, 이후 자음 오면 종성 처리
        // 상세 구현은 표준 두벌식 오토마타 참고
        val sb = StringBuilder()
        var i = 0
        while (i < jamos.size) {
            val ch = jamos[i]
            val chIdx = CHOSUNG.indexOf(ch)
            if (chIdx >= 0 && i + 1 < jamos.size) {
                val next = jamos[i + 1]
                val jungIdx = JUNGSUNG.indexOf(next)
                if (jungIdx >= 0) {
                    // 초성 + 중성 조합
                    val syllable = 0xAC00 + (chIdx * 21 + jungIdx) * 28
                    // 종성 확인
                    if (i + 2 < jamos.size) {
                        val jongCh = jamos[i + 2]
                        val jongIdx = JONGSUNG.indexOf(jongCh)
                        if (jongIdx > 0 && (i + 3 >= jamos.size || JUNGSUNG.indexOf(jamos[i + 3]) < 0)) {
                            sb.append((syllable + jongIdx).toChar())
                            i += 3
                            continue
                        }
                    }
                    sb.append(syllable.toChar())
                    i += 2
                    continue
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }
}
```

> ⚠️ `assembleJamos` 내부의 두벌식 오토마타 로직은 실제 구현 시
> 표준 두벌식 상태 머신을 기준으로 완전히 구현해야 합니다.
> 위 코드는 골격 참고용이며, Claude Code가 완전한 오토마타를 작성할 것.

### TextSelectionMonitor 감지 로직

```kotlin
// TYPE_VIEW_TEXT_SELECTION_CHANGED 이벤트 처리
fun onTextSelectionChanged(event: AccessibilityEvent) {
    val node = event.source ?: return
    val text = node.text?.toString() ?: return
    val selStart = node.textSelectionStart
    val selEnd = node.textSelectionEnd

    // 유효한 선택 범위인지 확인
    if (selStart < 0 || selEnd <= selStart) return
    val selectedText = text.substring(selStart, selEnd)
    if (selectedText.isBlank() || selectedText.length < 2) return

    // 한영타 신뢰도 판정
    val confidence = HangulConverter.detectEnglishToKorean(selectedText)
    if (confidence >= 0.70f) {
        val converted = HangulConverter.convertEngToKor(selectedText)
        overlayManager.showReplaceChip(
            originalNode = node,
            original = selectedText,
            converted = converted,
            selStart = selStart,
            selEnd = selEnd
        )
    }
}
```

### ReplaceChipView 스펙

```
위치: 화면 상단 고정 (status bar 바로 아래, 중앙 정렬)
크기: wrap_content × 32dp (최대 너비 280dp)
내용: "「{변환 미리보기}」 교체?"
  예: 「안드로이드」 교체?
배경: #1C1C1E (다크), 코너 반경 8dp
텍스트: 14sp, 흰색

WindowManager flags:
  TYPE_APPLICATION_OVERLAY
  FLAG_NOT_FOCUSABLE (터치 입력 방해 금지)
  단, 칩 본체는 클릭 가능해야 하므로 칩 외부만 FLAG_NOT_TOUCHABLE

자동 소멸: 2000ms 후 fadeOut + 제거
탭 시:
  1. AccessibilityNodeInfo.performAction(
       AccessibilityNodeInfo.ACTION_SET_TEXT,
       Bundle().apply { putCharSequence(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newFullText) }
     )
  2. 실패 시: 클립보드 복사 + Paste 액션 실행
  3. ReplaceChipView 즉시 소멸
```

---

## ImeLocaleParser 유틸리티

```kotlin
object ImeLocaleParser {

    fun parseLocale(subtype: InputMethodSubtype?): String {
        subtype ?: return "unknown"
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            subtype.languageTag.ifEmpty { subtype.locale }
        } else {
            subtype.locale
        }
        return when {
            locale.startsWith("ko") -> "ko"
            locale.startsWith("ja") -> "ja"
            locale.startsWith("en") -> "en"
            locale.startsWith("zh") -> "zh"
            else -> locale.take(2).lowercase()
        }
    }

    // Samsung One UI 시스템 팝업 텍스트 → 언어 추론 (fallback)
    fun parseFromSystemPopupText(text: String): String? {
        return when {
            text.contains("한국어", ignoreCase = false) -> "ko"
            text.contains("Korean", ignoreCase = true) -> "ko"
            text.contains("English", ignoreCase = true) -> "en"
            text.contains("日本語", ignoreCase = false) -> "ja"
            text.contains("Japanese", ignoreCase = true) -> "ja"
            text.contains("中文", ignoreCase = false) -> "zh"
            text.contains("Chinese", ignoreCase = true) -> "zh"
            else -> null
        }
    }
}
```

---

## 설정 항목 (SettingsActivity)

| 항목 | 기본값 | 타입 |
|---|---|---|
| 전환 플래시 활성화 | ON | Boolean |
| 플래시 지속 시간 | 200ms | Int (100~500 범위) |
| 상시 배지 표시 | ON | Boolean |
| 배지 위치 X | 우하단 | Int (px) |
| 배지 위치 Y | 우하단 | Int (px) |
| 포커스 없음 경고 | ON | Boolean |
| 포커스 없음 임계값 | 3회 | Int (1~5) |
| 한영타 교체 기능 | ON | Boolean |
| 한영타 신뢰도 임계값 | 70% | Int (50~90) |
