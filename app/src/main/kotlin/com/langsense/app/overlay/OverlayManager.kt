package com.langsense.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.langsense.app.R
import com.langsense.app.util.ImeLocaleParser
import com.langsense.app.util.Prefs
import kotlin.math.roundToInt

/**
 * 모든 오버레이(WindowManager) 추가/제거를 담당하는 단일 래퍼.
 * 메인 스레드에서만 윈도우를 조작한다.
 */
class OverlayManager(private val context: Context, private val prefs: Prefs) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var flashView: FlashOverlayView? = null
    // 같은 색 플래시가 아주 짧은 간격으로 다시 들어오면(잔존 중복 발화) 건너뛰는 렌더 단계 안전망.
    // 근본 원인은 ImeStateDetector(합치기/불응기/멱등)에서 처리하며, 이건 마지막 시각 방어일 뿐이다.
    private var lastFlashColorArgb = 0
    private var lastFlashAt = 0L
    private var badgeView: BadgeOverlayView? = null
    private var badgeParams: WindowManager.LayoutParams? = null
    private var chipView: ReplaceChipView? = null
    private var chipDismiss: Runnable? = null
    // 칩이 들고 있는 동안 소유하는 노드. 탭으로 소비되든, 타임아웃/교체로 버려지든 removeChip() 에서
    // 반드시 한 번 recycle() 한다(API 33 미만 노드 풀 누수 방지).
    private var pendingNode: AccessibilityNodeInfo? = null

    private var quickMenuView: QuickMenuOverlayView? = null
    /** 간편 메뉴 항목(앱 열기/설정/토글 등). 서비스가 [setQuickMenuItems] 로 주입. */
    private var quickMenuItems: List<QuickMenuItem> = emptyList()

    private val overlayType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    /**
     * 정리(removeAll) 후 도착하는 늦은 콜백이 오버레이를 다시 추가하지 못하게 막는 플래그 (Bug 4 감사).
     * 버그 1 수정으로 백그라운드 키 평가 스레드 → onWarn → onMain 경로가 생겨, 서비스 정리 직후
     * 큐에 남아 있던 post 가 실행되며 뷰를 재생성해 누수될 수 있다. removeAll 에서 동기로 set 하고
     * 모든 표시 진입점에서 확인한다. (재연결 시 서비스가 OverlayManager 를 새로 만들므로 상태 오염 없음)
     */
    @Volatile
    private var released = false

    /** 마지막 "언어 전환" 플래시 시각(uptime). 색과 무관한 에피소드 가드용(아래 showFlash 참조). */
    private var lastLangFlashAt = 0L

    // ---------------------------------------------------------------------
    // Feature 1 / 3: 플래시
    // ---------------------------------------------------------------------

    /** 언어 전환 플래시. 설정이 꺼져있거나 해당 언어가 비활성이면 무시. */
    fun showFlash(lang: String) = onMain {
        if (released) return@onMain
        if (!prefs.flashEnabled) return@onMain
        if (!prefs.isLangEnabled(lang)) return@onMain
        // (Bug 2 고질 중복 깜박임 최종 차단) 언어 전환 플래시는 "한 전환 에피소드당 1회"만 낸다.
        // 색과 무관하게 최소 간격(LANG_FLASH_MIN_INTERVAL_MS) 안의 재요청은 같은 전환에서 비롯된 중복
        // (다중 감지 신호·지연된 서브타입 캐시·stale 언어 재발동)으로 보고 건너뛴다. 사람이 한/영을 이보다
        // 빠르게 두 번 바꾸지 않으므로 정상 전환은 놓치지 않는다. (ImeStateDetector 의 합치기/불응기가
        // 1차 방어, 이 가드가 색을 가리지 않는 최종 방어 — 다른 색 2번째 깜박임까지 확실히 차단)
        val now = SystemClock.uptimeMillis()
        if (now - lastLangFlashAt < LANG_FLASH_MIN_INTERVAL_MS) return@onMain
        lastLangFlashAt = now
        flash(prefs.flashColorArgb(lang), ImeLocaleParser.displayName(lang))
    }

    /** 포커스 없는 키 입력 경고(동일한 플래시 방식, 회색 + 안내 텍스트). */
    fun showNoFocusWarning(message: String) = onMain {
        if (released) return@onMain
        flash(0xD9555555.toInt(), message)
    }

    private fun flash(colorArgb: Int, text: String) {
        // 렌더 단계 안전망: 동일 색 플래시가 FLASH_DEDUP_MS 안에 다시 오면 중복으로 보고 건너뛴다.
        // (다른 언어는 색이 달라 정상 표시됨. 사람은 같은 언어로 이만큼 빨리 두 번 전환할 수 없음.)
        val now = SystemClock.uptimeMillis()
        if (colorArgb == lastFlashColorArgb && now - lastFlashAt < FLASH_DEDUP_MS) return
        lastFlashColorArgb = colorArgb
        lastFlashAt = now

        removeFlash()
        val view = FlashOverlayView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        // [7] 기본 윈도우 enter/exit 애니메이션 제거 → 색이 좌→우로 채워지지 않고 전체 화면이
        // 한 프레임에 꽉 찬 상태로 나타난다. (페이드아웃은 뷰 알파 애니메이션이 따로 처리)
        params.windowAnimations = 0
        runCatching { wm.addView(view, params) }.onFailure { return }
        flashView = view
        // onEnd 시점에 다른 플래시로 교체되었을 수 있으므로 자기 자신일 때만 제거(오제거 방지).
        view.play(colorArgb, text, prefs.flashDurationMs, prefs.flashCount) { removeFlashIf(view) }
    }

    private fun removeFlash() {
        flashView?.let {
            it.cancel()
            runCatching { wm.removeView(it) }
        }
        flashView = null
    }

    /** 현재 표시 중인 플래시가 [view] 와 동일할 때만 제거한다. */
    private fun removeFlashIf(view: FlashOverlayView) {
        if (flashView === view) removeFlash()
    }

    // ---------------------------------------------------------------------
    // Feature 2: 배지
    // ---------------------------------------------------------------------

    fun showBadge(lang: String) = onMain {
        if (released) return@onMain
        if (!prefs.badgeEnabled) {
            hideBadgeInternal()
            return@onMain
        }
        val label = ImeLocaleParser.badgeLabel(lang)
        val existing = badgeView
        if (existing == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }

            val view = BadgeOverlayView(
                context, wm, params,
                onTap = { toggleQuickMenu() },
                onPositionSaved = { x, y -> prefs.setBadgePosition(x, y) }
            )
            applyBadgeStyle(view)
            view.setLanguage(label)

            // 저장 위치가 있으면 사용, 없으면 우하단 기본값
            if (prefs.badgeX >= 0 && prefs.badgeY >= 0) {
                params.x = prefs.badgeX
                params.y = prefs.badgeY
            } else {
                val (dx, dy) = BadgeOverlayView.defaultPosition(view)
                params.x = dx
                params.y = dy
            }
            runCatching { wm.addView(view, params) }.onFailure { return@onMain }
            badgeView = view
            badgeParams = params
        } else {
            // 재사용: 라벨 + 크기/색 스타일을 다시 적용(설정 변경 즉시 반영).
            applyBadgeStyle(existing)
            existing.setLanguage(label)
        }
    }

    /** 현재 설정(크기/배경색/글씨색)을 배지에 적용. */
    private fun applyBadgeStyle(view: BadgeOverlayView) =
        view.applyStyle(prefs.badgeSize, prefs.badgeBgColorArgb(), prefs.badgeTextColorArgb())

    fun updateBadge(lang: String) = showBadge(lang)

    fun hideBadge() = onMain { hideBadgeInternal() }

    private fun hideBadgeInternal() {
        hideQuickMenuInternal() // 배지가 사라지면 그 주위 메뉴도 함께 닫는다
        badgeView?.let { runCatching { wm.removeView(it) } }
        badgeView = null
        badgeParams = null
    }

    // ---------------------------------------------------------------------
    // 간편 메뉴(물방울) — 배지 탭으로 열림
    // ---------------------------------------------------------------------

    /** 서비스가 간편 메뉴 항목을 주입(앱 열기/설정/기능 토글 등). */
    fun setQuickMenuItems(items: List<QuickMenuItem>) {
        quickMenuItems = items
    }

    /** 배지 탭 시: 열려 있으면 (수납 애니메이션과 함께) 닫고, 닫혀 있으면 배지를 앵커로 메뉴를 연다. */
    fun toggleQuickMenu() = onMain {
        if (released) return@onMain
        quickMenuView?.let {
            badgeView?.pulse()
            it.requestCollapse() // 수납 애니메이션 후 onDismiss 콜백이 윈도우를 제거한다
            return@onMain
        }
        val bv = badgeView ?: return@onMain
        val bp = badgeParams ?: return@onMain
        if (quickMenuItems.isEmpty()) return@onMain
        bv.pulse()
        // 배지가 사라지는 일은 없지만(사라지면 hideQuickMenuInternal 이 메뉴도 함께 닫음) 방어적으로
        // 최초 좌표를 폴백으로 남겨둔다.
        val initialAnchorX = bp.x + bv.width / 2
        val initialAnchorY = bp.y + bv.height / 2

        val view = QuickMenuOverlayView(
            context,
            anchorProvider = {
                // 회전 등으로 다시 레이아웃될 때 배지의 "현재" 위치를 읽는다(Bug 6 — 고정 좌표로
                // 열면 회전 후 예전 배지 위치를 중심으로 부채꼴이 펼쳐지는 문제 방지).
                val curBv = badgeView
                val curBp = badgeParams
                if (curBv != null && curBp != null) {
                    (curBp.x + curBv.width / 2) to (curBp.y + curBv.height / 2)
                } else {
                    initialAnchorX to initialAnchorY
                }
            },
            items = quickMenuItems,
            reduceMotion = prefs.radialReduceMotion // 저사양 모드면 펼친 뒤 연속 애니메이션을 끈다
        ) {
            hideQuickMenu()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            // 터치는 받되(스크림/버튼) 키 포커스는 안 가져간다. 전체 화면 모달.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.windowAnimations = 0 // 등장 연출은 뷰 애니메이션으로 직접 처리
        runCatching { wm.addView(view, params) }.onFailure { return@onMain }
        quickMenuView = view
    }

    fun hideQuickMenu() = onMain { hideQuickMenuInternal() }

    private fun hideQuickMenuInternal() {
        quickMenuView?.let { runCatching { wm.removeView(it) } }
        quickMenuView = null
    }

    // ---------------------------------------------------------------------
    // Feature 4: "교체?" 칩
    // ---------------------------------------------------------------------

    /**
     * 한영타 교체 칩 표시.
     * @param node 선택이 일어난 편집 노드
     * @param fullText 노드 전체 텍스트
     * @param selStart 선택 시작
     * @param selEnd 선택 끝
     * @param converted 변환된 한국어
     */
    fun showReplaceChip(
        node: AccessibilityNodeInfo,
        fullText: String,
        selStart: Int,
        selEnd: Int,
        converted: String
    ) = onMain {
        if (released) { node.recycle(); return@onMain }
        removeChip() // 이전 칩이 있었다면 그 노드까지 함께 회수
        pendingNode = node
        val view = ReplaceChipView(context)
        view.bind(converted) {
            applyReplacement(node, fullText, selStart, selEnd, converted)
            removeChip()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight() + dp(8f)
        }
        runCatching { wm.addView(view, params) }.onFailure { return@onMain }
        chipView = view

        chipDismiss = Runnable { removeChip() }.also { handler.postDelayed(it, CHIP_TIMEOUT_MS) }
    }

    /**
     * 선택 영역을 변환 결과로 교체.
     *
     * 1차로 [AccessibilityNodeInfo.ACTION_SET_TEXT] 로 전체 텍스트를 통째로 교체한다.
     * 삼성 노트·일부 WebView/특수 에디터는 ACTION_SET_TEXT 를 무시하거나 false 를 반환하므로
     * (Bug 3) 클립보드 + 선택영역 재설정 + 붙여넣기로 fallback 한다.
     */
    private fun applyReplacement(
        node: AccessibilityNodeInfo,
        fullText: String,
        selStart: Int,
        selEnd: Int,
        converted: String
    ) {
        val s = selStart.coerceIn(0, fullText.length)
        val e = selEnd.coerceIn(s, fullText.length)
        val original = fullText.substring(s, e)

        // 노드가 갱신되었을 수 있으므로 최신 텍스트를 우선 사용(없으면 캡처된 fullText).
        val liveText = node.text?.toString() ?: fullText
        val (es, ee) = resolveReplaceRange(liveText, s, e, original) ?: return
        val newText = liveText.substring(0, es) + converted + liveText.substring(ee)

        if (trySetText(node, newText)) return
        pasteFallback(node, converted, es, ee)
    }

    /**
     * "교체?" 칩이 떠 있는 최대 2초 동안 사용자가 앞쪽 텍스트를 편집하면 (s..e) 오프셋이 실제 대상과
     * 어긋난다. 그 자리에 원래 선택했던 문자열([original])이 그대로 있을 때만 그 위치를 신뢰하고,
     * 아니면 문서 안에서 같은 문자열을 다시 찾는다(첫 매치). 그마저 못 찾으면 엉뚱한 곳을 덮어쓰지
     * 않도록 교체를 포기한다(null).
     */
    private fun resolveReplaceRange(liveText: String, s: Int, e: Int, original: String): Pair<Int, Int>? {
        if (e <= liveText.length && liveText.substring(s, e) == original) return s to e
        val idx = liveText.indexOf(original)
        if (idx < 0) return null
        return idx to (idx + original.length)
    }

    /** 1차: ACTION_SET_TEXT. editable 이 아니거나 false 반환 시 실패로 간주. */
    private fun trySetText(node: AccessibilityNodeInfo, newText: String): Boolean {
        if (!node.isEditable) return false
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
        }
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
    }

    /**
     * 2차(fallback): 클립보드에 변환 결과를 넣고, 교체 대상 영역을 선택한 뒤 붙여넣기.
     * 붙여넣기는 "현재 선택 영역을 대체"하므로 선택영역 재설정이 핵심이다.
     * 그래도 실패하면 클립보드에는 변환 결과가 남아 사용자가 직접 붙여넣을 수 있다.
     */
    private fun pasteFallback(node: AccessibilityNodeInfo, converted: String, s: Int, e: Int) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        runCatching { cm?.setPrimaryClip(ClipData.newPlainText("langsense", converted)) }

        // 포커스 확보 → 교체 대상 영역 선택 → 붙여넣기
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, s)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, e)
        }
        val selectionSet = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        }.getOrDefault(false)

        val pasted = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)

        if (!selectionSet || !pasted) {
            // 붙여넣기까지 실패: 변환 결과가 클립보드에 있음을 사용자에게 안내.
            handler.post {
                runCatching {
                    Toast.makeText(context, R.string.replace_copied_fallback, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun removeChip() {
        chipDismiss?.let { handler.removeCallbacks(it) }
        chipDismiss = null
        chipView?.let { runCatching { wm.removeView(it) } }
        chipView = null
        pendingNode?.let { runCatching { it.recycle() } }
        pendingNode = null
    }

    // ---------------------------------------------------------------------
    // 정리
    // ---------------------------------------------------------------------

    fun removeAll() {
        // 늦은 콜백이 이후 뷰를 다시 추가하지 못하도록 동기로 먼저 막는다(Bug 4 감사).
        released = true
        onMain {
            removeFlash()
            hideQuickMenuInternal()
            hideBadgeInternal()
            removeChip()
        }
    }

    // ---------------------------------------------------------------------
    // 헬퍼
    // ---------------------------------------------------------------------

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else handler.post(block)
    }

    private fun statusBarHeight(): Int {
        @Suppress("DiscouragedApi", "InternalInsetResource")
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24f)
    }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    companion object {
        const val CHIP_TIMEOUT_MS = 2000L

        /** 같은 색 플래시 중복 억제 창(ms). 근본 방어(ImeStateDetector) 뒤의 렌더 단계 마지막 안전망. */
        const val FLASH_DEDUP_MS = 350L

        /**
         * 언어 전환 플래시의 "전환 에피소드" 최소 간격(ms). 색과 무관하게 이 간격 안의 추가 플래시는
         * 같은 전환의 중복으로 보고 건너뛴다. ImeStateDetector 불응기(450ms)+합치기 창보다 넉넉히 크게
         * 잡아, 지연된 stale 언어가 다른 색으로 한 번 더 깜박이는 고질 현상까지 차단한다.
         */
        const val LANG_FLASH_MIN_INTERVAL_MS = 700L
    }
}
