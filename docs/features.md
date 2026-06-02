# kIkI — 기능 상세 명세

> 앱 표시 이름은 **kIkI**. 패키지/클래스/리소스 id 등 식별자는 `com.langsense.app` / `LangSense…` 유지.
> 문서와 코드가 다르면 **코드가 진실**.

## Feature 1: 언어 전환 플래시

### 트리거 조건 (코드 기준: `ImeStateDetector`)
세 경로를 병행하여 한/영 전환을 즉시 감지한다.
1. **`BroadcastReceiver`** — `ACTION_INPUT_METHOD_CHANGED` (IME/서브타입 전체 전환)
2. **`ContentObserver`** — `Settings.Secure` 의 `selected_input_method_subtype` / `default_input_method`
   변경 (한/영 토글 시 시스템 설정이 즉시 바뀌므로 화면 변화 없이도 감지)
3. **`TYPE_WINDOW_STATE_CHANGED`** + Samsung One UI 팝업 텍스트 (백스톱 / 삼성 내부 토글)

각 경로는 `InputMethodManager.getCurrentInputMethodSubtype()` 의 locale 을 읽어 직전과 다르면 발동.

### ★ 2회 깜박임 방지 — 3중 방어
한 번의 전환이 위 경로에서 신호 다발(브로드캐스트 1 + 옵저버 2 + 윈도우 이벤트 n)을 만든다.
1. **신호 합치기(coalesce)**: 모든 신호를 **단일 예약(`recheckRunnable`)** 으로 합쳐 신호가 멎고
   `COALESCE_MS`(150ms) 뒤에 **한 번만** 서브타입(또는 팝업 힌트)을 읽어 발동.
2. **발동 후 불응기(`REFRACTORY_MS`=450ms)**: 발동 직후 들어오는 잔여 신호 + 지연된
   `currentInputMethodSubtype` 캐시가 "직전 언어"를 새 전환처럼 다시 발동(→ 다른 색 2번째 깜박임)하는
   것을 차단. 불응기 동안엔 재평가를 미뤘다가 캐시 안정 후 1회만 확인.
3. **`onServiceConnected` 멱등화**: 재연결 시 detector(Receiver/Observer)가 중복 등록되면 한 전환에
   여러 번 발동하므로, 재진입 시 이전 인스턴스를 `cleanup()` 후 재초기화.

따라서 깜박임 횟수 1 지정 시 **정확히 1회**만 깜박이고, 신호마다 두 번씩 읽던 중복 작업도 사라진다
(저사양 최적화). 전환당 `onLanguageChanged` 호출이 1회임은 `emitCount` + `Log.d` 로 실기기 확인 가능.

### Samsung One UI 팝업 보조 감지 (fallback)
경로 3에서 시스템 팝업 텍스트로 보조 감지(팝업 힌트는 서브타입보다 우선 — 삼성 내부 토글 대응).

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
  windowAnimations = 0   # ★ 윈도우 enter/exit 애니메이션 제거
                         #   → 색이 좌→우로 채워지지 않고 전체 화면이 한 프레임에 꽉 차게 나타남
  나타남: 즉시 (윈도우 애니메이션 없음)
  사라짐: 뷰 알파 페이드아웃 (1회 지속시간 = flash_duration_ms, 기본 200ms)
  깜박임 횟수: flash_count (기본 1, 1~5)
```

### 언어별 색상 코드 (기본값; 설정에서 #RRGGBB 로 변경 가능, ALPHA 0.85 적용)
- 한국어(`ko`) → `#CC2D2D`
- English(`en`) → `#1A6EBD`
- 기타 → `#555555`
- ~~日本語(`ja`) → `#2D8C4E`~~ — **[일본어 비활성화]** 코드 주석 처리(`Prefs.DEFAULT_JA` 상수는 보존)

---

## Feature 2: 상시 언어 배지

### 표시 스펙 (코드 기준: `BadgeOverlayView.applyStyle`)
```
크기 3단계 (badge_size, 기본 1=중):
  소(0): 12sp / 패딩 6·3dp / minWidth 32dp
  중(1): 14sp / 패딩 8·4dp / minWidth 40dp   ← 기본 = 기존 외형
  대(2): 18sp / 패딩 11·6dp / minWidth 52dp
배경: GradientDrawable, 코너 6dp, 색 = badge_bg_color(#RRGGBB) + BADGE_BG_ALPHA(0.8)
      → 기본값(#000000)이면 기존 #CC000000 과 100% 동일
텍스트: Bold, 색 = badge_text_color(#RRGGBB, 불투명), 기본 흰색
  한국어 → "한"
  English → "EN"
  기타 → locale 앞 2자리 대문자
  ~~日本語 → "日"~~  [일본어 비활성화]

위치 저장: SharedPreferences ("badge_x", "badge_y")
기본 위치: 우하단 (margin 16dp)
색 선택 UI: 플래시 색상과 동일한 공용 컴포넌트(32색 팔레트 + #RRGGBB 입력) 재사용
설정 변경 시: applyStyle 재호출로 즉시 반영
```

### 드래그 동작
- `OnTouchListener`로 ACTION_MOVE 감지
- 화면 경계 벗어나지 않도록 clamp 처리
- 드래그 종료 시 위치 자동 저장

---

## Feature 3: 포커스 없는 키 입력 경고

입력칸을 선택하지 않은 채 키보드를 치면 글자가 어디에도 안 들어간다. 이때만 경고를 띄운다.

### 감지 로직 (코드 기준: `KeyEventMonitor` + Service)
`onKeyEvent` 는 **항상 false**(이벤트 미소비). 다음 순서로 평가한다(앞 단계에서 걸러지면 비싼 조회 생략):
1. 기능 ON + `ACTION_DOWN` + 비반복 + **실제 문자 키**(modifier/단축키/기능키 제외) 게이트.
2. **입력 실착 확인(최우선·오발동 방지)**: 최근 `RECENT_INPUT_MS`(1.2s) 안에 편집 노드의
   `typeViewTextChanged`/`typeViewTextSelectionChanged` 가 있었으면(=글자가 실제 입력됨) 포커스
   있음으로 간주하고 카운터 초기화. 포커스가 정말 없으면 이런 이벤트가 없으므로 진짜 경고는 유지.
3. 포커스 조회(지연 평가): 1차 활성 윈도우 `findFocus(FOCUS_INPUT)?.isEditable`, 없을 때만
   2차 전체 윈도우 순회(일시적 null 방어).
4. 그래도 포커스 없으면 카운터 증가. 임계값(기본 3) 도달 시 경고, 재경고 쿨다운 `WARN_COOLDOWN_MS`(2.5s).

> 텍스트 '내용'은 절대 읽지 않는다(`source.isEditable` 여부만 확인). 카운터는 포커스 획득/입력 실착 시 초기화.

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
     * 신뢰도 판정: 입력 문자열이 한영타일 가능성 반환 (0.0 ~ 1.0)  [실제 구현 기준]
     *
     * 두 신호를 곱한다:
     *  - mapRatio   = 두벌식 자판에 매핑되는 영문자 비율
     *  - composeRatio = 변환 결과 중 "완성형 한글 음절" / (완성형 + 조합 실패해 남은 낱자모)
     * 실제 영어 단어는 모음/자음 배열이 한글 규칙과 어긋나 낱자모가 많이 남아 낮게 나온다.
     * (설정의 "신뢰도 임계값(%)" 인라인 설명이 이 로직을 사용자 언어로 풀어 쓴 것)
     */
    fun detectEnglishToKorean(input: String): Float {
        val letters = input.filter { it.isLetter() }
        if (letters.isEmpty()) return 0f
        val mappable = letters.count { engToJamo(it) != null }
        if (mappable == 0) return 0f
        val mapRatio = mappable.toFloat() / letters.length

        val converted = convertEngToKor(input)
        var syllables = 0; var looseJamo = 0
        for (ch in converted) when (ch.code) {
            in 0xAC00..0xD7A3 -> syllables++          // 완성형 음절
            in 0x3130..0x318F -> looseJamo++          // 남은 낱자모(호환 자모)
        }
        val units = syllables + looseJamo
        if (units == 0) return 0f
        return (syllables.toFloat() / units) * mapRatio
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

> ℹ️ 위 블록은 **개념 골격**이다. 실제 `HangulConverter.kt` 는 표준 두벌식 오토마타를 완전히 구현했다
> (복합 중성 `ㅘ/ㅚ/ㅢ`, 복합 종성 `ㄺ/ㄼ/ㅄ`, 연음·도깨비불, 역변환 `convertKorToEng` 포함).
> 안드로이드 의존성이 없는 순수 Kotlin 이라 JVM 단위 테스트로 검증된다(`HangulConverterTest`).

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
            // [일본어 비활성화] locale.startsWith("ja") -> "ja"  (주석 보존)
            locale.startsWith("en") -> "en"
            locale.startsWith("zh") -> "zh"
            else -> locale.take(2).lowercase()   // 일본어 IME 는 여기서 "ja"(기타 취급)로 흐름
        }
    }

    // Samsung One UI 시스템 팝업 텍스트 → 언어 추론 (fallback)
    fun parseFromSystemPopupText(text: String): String? {
        return when {
            text.contains("한국어") || text.contains("Korean", ignoreCase = true) -> "ko"
            text.contains("English", ignoreCase = true) -> "en"
            // [일본어 비활성화] text.contains("日本語")/Japanese -> "ja"  (주석 보존)
            text.contains("中文") || text.contains("Chinese", ignoreCase = true) -> "zh"
            else -> null
        }
    }

    // displayName/badgeLabel 의 JA 분기("日本語"/"日")도 동일하게 주석 비활성화.
}
```

---

## 설정 항목 (SettingsActivity)

| 항목 | 기본값 | 타입 / 범위 | 비고 |
|---|---|---|---|
| 지원 언어 토글(한국어/영어) | ON | Boolean | 일본어 항목은 [비활성화]로 주석 |
| 전환 플래시 활성화 | ON | Boolean | |
| 깜박임 속도(지속시간) | 200ms | Int 100~500 (step 50) | |
| 깜박임 횟수 | 1회 | Int 1~5 | coalesce 로 1회 지정 시 1회만 |
| 언어별 플래시 색상 | 코드별 기본값 | #RRGGBB | 공용 색 선택 컴포넌트 |
| 상시 배지 표시 | ON | Boolean | |
| **배지 크기** | 중 | 소/중/대 (라디오) | 신규 [5] |
| **배지 배경색** | #000000(+α0.8) | #RRGGBB | 신규 [5], 공용 컴포넌트 |
| **배지 글씨색** | #FFFFFF | #RRGGBB | 신규 [5], 공용 컴포넌트 |
| 배지 위치 X/Y | 우하단 | Int (px) | 드래그로 변경·저장 |
| 포커스 없음 경고 | ON | Boolean | **인라인 설명 추가 [3]** |
| 포커스 없음 임계값 | 3회 | Int 1~5 | |
| 한영타 교체 기능 | ON | Boolean | |
| 한영타 신뢰도 임계값 | 70% | Int 50~90 (step 5) | **인라인 설명 추가 [4]** |

> 색 선택 UI(32색 팔레트 + #RRGGBB 입력)는 `colorPickerRow` 공용 컴포넌트로 추출되어
> 플래시 색 설정과 배지 색 설정이 동일 코드를 공유한다(복붙 방지).
