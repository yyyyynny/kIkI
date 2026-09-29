package com.langsense.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.langsense.app.R
import com.langsense.app.util.ColorMath
import com.langsense.app.util.ImeLocaleParser
import com.langsense.app.util.Prefs
import com.langsense.app.util.SettingsSearch
import com.langsense.app.util.ThemeManager
import com.langsense.app.util.UiPalette
import com.langsense.app.util.themeColor
import kotlin.math.roundToInt

/**
 * 설정 화면 (프로그래매틱 UI, 성능 위주의 단순 구성).
 *
 * 모던 카드 레이아웃: 커스텀 톱바(뒤로가기) + 섹션별 라운드 카드([sectionCard]) + SwitchCompat 토글.
 * NoActionBar 테마(Theme.LangSense)를 쓰므로 supportActionBar 에 의존하지 않는다.
 *
 * - 지원 언어 스위치(기본 ON)
 * - 언어별 플래시 색상: #RRGGBB 직접 입력 + 32색 팔레트
 * - 깜박임 속도(100~500ms) / 횟수(1~5) 슬라이더
 * - 배지/포커스경고/한영타 토글 및 임계값
 *
 * 모든 변경은 즉시 SharedPreferences 에 반영되어 서비스에 적용된다(기능 표면은 리디자인 전과 1:1 동일).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var savedHint: TextView

    /**
     * 래디얼 메뉴 등 외부에서 바뀔 수 있는 토글(배지/플래시/한영타). 화면 재개 시 현재 값으로 다시
     * 동기화해 stale 표시를 막는다(이슈 4: 숨기기 후 설정 화면 토글이 안 꺼져 보이던 문제).
     */
    private val boundToggles = mutableListOf<Pair<CompoundButton, () -> Boolean>>()

    /** onResume 동기화 중 onCheckedChanged 가 prefs 를 되쓰지 않도록 막는 가드. */
    private var syncingToggles = false

    /**
     * 설정 화면이 떠 있는 동안에도 값이 밖에서 바뀔 수 있다 — 배지 오버레이는 이 화면 위에도
     * 떠 있어서, 보면서 배지를 탭해 래디얼 메뉴로 토글할 수 있기 때문. onResume 만으로는
     * (액티비티가 계속 포그라운드라 호출되지 않아) 스위치가 stale 로 남는다.
     */
    private val prefsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            syncToggles(); refreshDiagnosticResult(); refreshRailSummaries()
        }

    /** [diagnosticResultRow] 가 채우는, 최근 캡처된 전환 키를 보여주는 텍스트. */
    private lateinit var diagnosticResultText: TextView

    /**
     * 색 미리보기 원들을 다시 그리는 람다 모음([colorPickerRow] 가 등록).
     * 불투명도 슬라이더는 어느 색이 영향을 받는지 알 필요 없이 전부 한 번 갱신한다 — 최대 6개라
     * 비용이 무시할 수준이고, 슬라이더와 색 피커를 일일이 배선하지 않아도 되어 단순하다.
     */
    private val previewRefreshers = mutableListOf<() -> Unit>()


    /** 지금 열려 있는 그룹 id. */
    private var currentGroup: String = GROUP_FLASH

    /** 검색어(비면 검색 중 아님). */
    private var query: String = ""

    /** 태블릿 폭이면 목록+상세를 나란히(2단), 좁으면 목록→상세로 들어가는 1단. */
    private var twoPane: Boolean = false

    /** 테마 변경은 화면을 다시 만들어야 반영되므로, 지금 적용된 테마를 기억해 뒀다 비교한다. */
    private var appliedTheme: String = Prefs.THEME_SYSTEM

    /**
     * 펼쳐진 색 팔레트의 [colorPickerRow] key 집합 — 사용자 지정 테마 색은 고를 때마다 [recreate]
     * 가 화면을 통째로 새로 그리는데, 그때마다 팔레트가 접힌 채로 다시 시작하면 "펼치고 → 한 번
     * 고르면 접힘 → 다시 펼치고" 를 색마다 반복해야 해서 불편하다(사용자 제보, 2026-09). 이
     * 인스턴스 필드 자체는 recreate() 로 죽지만 [onSaveInstanceState]/복원으로 값이 넘어간다.
     */
    private val openPaletteKeys: MutableSet<String> = mutableSetOf()

    private lateinit var railRoot: View
    private lateinit var railList: LinearLayout
    private lateinit var detailScroll: ScrollView
    private lateinit var detailContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⚠️ setTheme 은 반드시 super.onCreate 보다 먼저 — windowBackground 는 super.onCreate 안에서
        // Window 가 붙을 때 확정되므로, 나중에 부르면 배경만 이전 테마로 남는다.
        prefs = Prefs(this)
        appliedTheme = ThemeManager.signature(this, prefs.uiTheme)
        ThemeManager.apply(this, prefs.uiTheme)
        super.onCreate(savedInstanceState)

        // 테마를 고르면 recreate() 가 돈다 — 복원하지 않으면 "화면 테마"에서 테마를 바꾸는 순간
        // 첫 그룹으로 튕겨 나간다(화면 회전도 같다). 검색 중이었다면 검색어도 함께 살린다.
        var restoredInDetail = false
        savedInstanceState?.let {
            currentGroup = it.getString(STATE_GROUP) ?: GROUP_FLASH
            query = it.getString(STATE_QUERY).orEmpty()
            restoredInDetail = it.getBoolean(STATE_IN_DETAIL, false)
            openPaletteKeys.addAll(it.getStringArrayList(STATE_OPEN_PALETTES).orEmpty())
        }

        twoPane = isWideEnoughForTwoPane()

        railList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        railRoot = buildRail()

        detailContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(DETAIL_PADDING_DP), dp(4), dp(DETAIL_PADDING_DP), dp(28))
        }
        // 넓은 화면에서 카드가 화면 끝까지 늘어나지 않게 상한을 두고 가운데로 모은다
        // (태블릿 가로 1338dp 에서 상세 영역이 1037dp 까지 벌어진다). 좁은 화면엔 영향 없음.
        detailContainer.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        detailScroll = ScrollView(this).apply {
            id = R.id.settings_detail_scroll   // 스크롤 위치를 테마 변경(recreate) 너머로 보존
            isVerticalScrollBarEnabled = false
            addView(detailContainer)
        }

        val body: ViewGroup = if (twoPane) {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(railRoot, LinearLayout.LayoutParams(dp(RAIL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT))
                addView(
                    View(this@SettingsActivity).apply {
                        setBackgroundColor(themeColor(R.attr.uiDivider))
                    },
                    LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT)
                )
                addView(
                    detailScroll,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).also { it.weight = 1f }
                )
            }
        } else {
            // 1단: 같은 자리에 겹쳐 두고 보이는 쪽만 바꾼다(목록 ↔ 상세).
            FrameLayout(this).apply {
                addView(railRoot)
                addView(detailScroll)
            }
        }

        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // 루트가 초기 포커스를 먼저 잡는다 — 아니면 첫 입력칸(2단은 검색칸, 1단 상세는 색 코드
            // 칸)이 포커스를 가져가, 태블릿(대화면)에서는 화면 키보드가 자동으로 떠 화면 절반을
            // 덮고, 외장 키보드로 친 글자는 색 코드 칸으로 들어갔다. 그룹을 바꿔 포커스된 입력칸이
            // 사라질 때도 포커스가 다음 입력칸이 아니라 이 루트로 돌아온다.
            isFocusableInTouchMode = true
            // 외장 키보드(비터치 모드)에서 포커스된 루트에 기본 하이라이트가 화면 전체로 칠해지지 않게.
            defaultFocusHighlightEnabled = false
            addView(topBar())
            addView(
                body,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).also { it.weight = 1f }
            )
        }
        setContentView(screen)
        screen.requestFocus()
        ThemeManager.applyWindow(this)

        capDetailWidth()
        renderRail()
        renderDetail()
        // 1단에서는 목록이 기본이지만, 상세를 보던 중 재생성(테마 변경·회전)됐으면 그 자리로 돌아간다.
        if (!twoPane) {
            if (restoredInDetail) {
                railRoot.visibility = View.GONE
                detailScroll.visibility = View.VISIBLE
            } else {
                showList()
            }
        }

        // 1단에서 상세를 보고 있으면 뒤로가기는 앱 종료가 아니라 목록으로.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!twoPane && detailScroll.visibility == View.VISIBLE) showList() else finish()
            }
        })
    }

    // ── 화면 크기 적응(폰 / 폴더블 / 태블릿 / 멀티윈도우) ────────────────────────

    /**
     * 목록과 상세를 나란히 놓을 만한 화면인가.
     *
     * 폭만 보면 **폰을 가로로 눕혔을 때도 2단이 뜬다**(예: 851×393dp) — 세로가 393dp뿐이라
     * 카드 두세 개면 끝나는 높이에 레일까지 끼는 꼴이라 오히려 답답하다. 그래서 높이도 함께 본다.
     *
     * 실제 기기 대입:
     *  - Galaxy Tab S6 Lite 세로 857×1338 / 가로 1338×857 → 둘 다 2단
     *  - Fold 펼침 약 725×870 → 2단 / **접힘 329×842 → 1단**
     *  - 일반 폰 393×851, 가로 851×393 → 둘 다 1단
     *  - 태블릿 분할화면(폭 절반) → 1단
     *
     * 접고 펴거나 분할화면 크기를 바꾸면 매니페스트에 `configChanges` 가 없어 액티비티가 재생성되고,
     * `onSaveInstanceState` 로 보던 그룹·검색어가 유지된 채 이 판정이 다시 돈다.
     */
    private fun isWideEnoughForTwoPane(): Boolean {
        val c = resources.configuration
        return c.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP && c.screenHeightDp >= TWO_PANE_MIN_HEIGHT_DP
    }

    /** 32색 팔레트의 열 수 — 들어가는 만큼만, 최소 4열 최대 8열. */
    private fun paletteColumns(): Int =
        (detailContentWidthDp() / SWATCH_CELL_DP).coerceIn(4, 8)

    /** 테마 카드 격자의 열 수 — 아주 좁으면(폴드 접힘 등) 1열로 떨어뜨려 글자가 뭉치지 않게 한다. */
    private fun themeColumns(): Int = if (detailContentWidthDp() < THEME_TWO_COLUMN_MIN_DP) 1 else 2

    /** 상세 카드 **안쪽**에서 실제로 쓸 수 있는 가로 폭(dp) — 팔레트·테마 격자의 열 수 기준. */
    private fun detailContentWidthDp(): Int {
        val screen = resources.configuration.screenWidthDp
        val detail = (if (twoPane) screen - RAIL_WIDTH_DP - 1 else screen)
            .coerceAtMost(contentMaxWidthDp())
        return (detail - DETAIL_PADDING_DP * 2 - CARD_PADDING_DP * 2).coerceAtLeast(160)
    }

    private fun contentMaxWidthDp(): Int =
        (resources.getDimensionPixelSize(R.dimen.content_max_width) / resources.displayMetrics.density)
            .toInt()

    /**
     * 상세 영역이 지나치게 넓어지지 않게 상한을 둔다 — 태블릿 가로(1338dp)에서는 상세만
     * 1037dp 라 카드 한 줄이 화면 끝까지 늘어나 읽기 불편하다. 상한보다 좁으면 그대로 둔다.
     */
    private fun capDetailWidth() {
        val maxWidth = resources.getDimensionPixelSize(R.dimen.content_max_width)
        val available = detailScroll.width.takeIf { it > 0 }
            ?: (resources.displayMetrics.widthPixels - if (twoPane) dp(RAIL_WIDTH_DP) else 0)
        if (available <= maxWidth) return
        detailContainer.layoutParams =
            (detailContainer.layoutParams as FrameLayout.LayoutParams).apply {
                width = maxWidth
                gravity = Gravity.CENTER_HORIZONTAL
            }
    }

    // ── 좌측 레일(검색 + 그룹 목록) ──────────────────────────────────────────────

    private fun buildRail(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(20))
        }
        column.addView(searchRow())
        column.addView(railList)
        column.addView(versionFooter())
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(column)
        }
    }

    /**
     * "26.9.19.04" 처럼 마지막 커밋 날짜 + 그날 몇 번째 커밋인지로 자동 계산된 버전(빌드 스크립트 참조). `BuildConfig` 를
     * 켜지 않고(빌드 산출물 증가 방지, [appVersionCode] 와 같은 이유) `PackageManager` 로 읽는다.
     */
    private fun versionFooter(): TextView = TextView(this).apply {
        val name = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull()
        text = getString(R.string.settings_version_format, name ?: "?")
        textSize = 11f
        setTextColor(themeColor(R.attr.uiOnSurfaceMuted))
        setPadding(dp(11), dp(10), dp(11), 0)
    }

    /** 설정 이름뿐 아니라 설명 문구까지 함께 찾는 검색칸. */
    private fun searchRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), themeColor(R.attr.uiDivider))
            }
            setPadding(dp(11), 0, dp(11), 0)
            minimumHeight = dp(44)
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = android.content.res.ColorStateList.valueOf(themeColor(R.attr.uiOnSurfaceMuted))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        })
        row.addView(EditText(this).apply {
            setText(query)
            hint = getString(R.string.settings_search_hint)
            textSize = 14f
            setTextColor(themeColor(R.attr.uiOnSurface))
            setHintTextColor(themeColor(R.attr.uiOnSurfaceMuted))
            tintForCustomPalette(this)
            setSingleLine()
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f; it.marginStart = dp(6) }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    query = s?.toString().orEmpty()
                    renderRail()
                }
            })
        })
        return row
    }

    /**
     * 그룹 줄의 상태 요약 TextView. 설정을 바꿀 때 레일 8줄을 통째로 다시 만들지 않고
     * 이 텍스트만 갱신한다 — 슬라이더 드래그 한 번에 최대 17회 호출되므로 저사양에서 차이가 크다.
     */
    private val railSummaries = mutableMapOf<String, TextView>()

    /** 설정 변경·화면 재개 시 그룹 요약만 현재 값으로 다시 쓴다(검색 중이면 비어 있어 no-op). */
    private fun refreshRailSummaries() {
        if (railSummaries.isEmpty()) return
        groupDefs().forEach { g -> railSummaries[g.id]?.text = g.summary() }
    }

    private fun renderRail() {
        railSummaries.clear()
        railList.removeAllViews()
        val q = query.trim()
        if (q.isEmpty()) {
            groupDefs().forEach { railList.addView(groupRow(it)) }
            return
        }
        val result = SettingsSearch.search(searchIndex(), q)
        if (result.hits.isEmpty()) {
            railList.addView(descRow(getString(R.string.settings_search_empty, q)))
            return
        }
        railList.addView(subsectionTitleText(getString(R.string.settings_search_result, result.hits.size)))
        // 보정 검색이면 무엇으로 찾았는지 먼저 알린다 — 조용히 다른 결과를 보여 주면 혼란스럽다.
        when (result.mode) {
            SettingsSearch.Mode.KEYBOARD_FIX ->
                railList.addView(descRow(getString(R.string.settings_search_fixed, q, result.corrected)))
            SettingsSearch.Mode.TYPO -> railList.addView(descRow(getString(R.string.settings_search_typo)))
            SettingsSearch.Mode.DIRECT -> Unit
        }
        val highlight = result.corrected ?: q
        result.hits.forEach { hit -> railList.addView(searchResultRow(hit, highlight)) }
    }

    /** 그룹 한 줄: 아이콘 + 이름 + 현재 상태 요약(+ 아직 안 본 새 기능이면 NEW). */
    private fun groupRow(g: GroupDef): View {
        val selected = twoPane && query.isBlank() && g.id == currentGroup
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(10), dp(11), dp(10))
            isClickable = true
            // 이 앱의 사용자는 물리 키보드를 쓴다 — 탭/방향키로도 닿아야 한다.
            isFocusable = true
            background = if (selected) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(themeColor(R.attr.uiAccentContainer))
                }
            } else {
                rippleBoundedBackground()
            }
            setOnClickListener { showGroup(g.id) }
        }
        val fg = if (selected) themeColor(R.attr.uiOnAccentContainer) else themeColor(R.attr.uiOnSurface)
        val sub = if (selected) themeColor(R.attr.uiOnAccentContainer) else themeColor(R.attr.uiOnSurfaceMuted)
        row.addView(ImageView(this).apply {
            setImageResource(g.iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(fg)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f; it.marginStart = dp(11) }
        }
        val titleLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleLine.addView(TextView(this).apply {
            text = getString(g.titleRes)
            textSize = 14.5f
            setTextColor(fg)
            if (selected) setTypeface(typeface, Typeface.BOLD)
        })
        if (showMarker(g.newMarker)) titleLine.addView(newChip())
        texts.addView(titleLine)
        texts.addView(TextView(this).apply {
            text = g.summary()
            textSize = 12f
            setTextColor(sub)
            railSummaries[g.id] = this
        })
        row.addView(texts)
        if (!selected) {
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_chevron)
                imageTintList = android.content.res.ColorStateList.valueOf(themeColor(R.attr.uiOnSurfaceMuted))
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
            })
        }
        return row
    }

    /** "NEW" 칩 — [Prefs.shouldShowMarker] 가 참일 때만 붙고, 저절로 사라진다. */
    private fun newChip(): View = TextView(this).apply {
        text = getString(R.string.settings_new_badge)
        textSize = 9.5f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(themeColor(R.attr.uiOnAccentContainer))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(5).toFloat()
            setColor(themeColor(R.attr.uiAccentContainer))
        }
        setPadding(dp(5), dp(1), dp(5), dp(1))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.marginStart = dp(6) }
    }

    /** 검색 결과 한 줄: 이름(일치 부분 강조) + 경로 + 태그로 걸렸으면 그 태그. 누르면 그 설정 줄로 간다. */
    private fun searchResultRow(hit: SettingsSearch.Hit, query: String): View {
        val item = hit.item
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(9), dp(11), dp(9))
            isClickable = true
            isFocusable = true
            background = rippleBoundedBackground()
            setOnClickListener { showGroup(item.group); revealAnchor(item.anchors) }
        }
        row.addView(TextView(this).apply {
            text = highlightTerms(item.name, query)
            textSize = 14f
            setTextColor(themeColor(R.attr.uiOnSurface))
        })
        row.addView(TextView(this).apply {
            text = item.path
            textSize = 11.5f
            setTextColor(themeColor(R.attr.uiOnSurfaceMuted))
        })
        hit.matchedTag?.let { tag ->
            row.addView(TextView(this).apply {
                text = getString(R.string.settings_search_tag, tag)
                textSize = 11f
                setTextColor(themeColor(R.attr.uiOnAccentContainer))
                background = UiDrawables.pill(this@SettingsActivity, themeColor(R.attr.uiAccentContainer))
                setPadding(dp(7), dp(1), dp(7), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(4) }
            })
        }
        return row
    }

    /** [text] 안에서 검색 단어와 글자 그대로 겹치는 부분을 강조색 굵게(띄어쓰기·대소문자 무시). */
    private fun highlightTerms(text: String, query: String): CharSequence {
        val out = android.text.SpannableString(text)
        val accent = themeColor(R.attr.uiAccent)
        query.split(Regex("\\s+")).map { SettingsSearch.normalize(it) }.filter { it.isNotEmpty() }.forEach { term ->
            // 원문 인덱스를 보존한 채 공백을 건너뛰며 비교한다.
            val lower = text.lowercase()
            var start = 0
            while (start < lower.length) {
                var i = start
                var k = 0
                while (i < lower.length && k < term.length) {
                    if (lower[i].isWhitespace()) { if (k == 0) break; i++; continue }
                    if (lower[i] != term[k]) break
                    i++; k++
                }
                if (k == term.length) {
                    out.setSpan(android.text.style.ForegroundColorSpan(accent), start, i, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, i, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    start = i
                } else {
                    start++
                }
            }
        }
        return out
    }

    /**
     * 검색 결과로 들어온 설정 줄을 찾아 보이게 스크롤하고 잠깐 강조한다. [anchors] 는 그 줄에 보이는
     * 문구 후보(앞에서부터 첫 번째로 찾은 것). 못 찾으면 그룹 맨 위에 그대로 둔다.
     */
    private fun revealAnchor(anchors: List<String>) {
        detailScroll.post {
            val target = anchors.firstNotNullOfOrNull { findTextView(detailContainer, it) } ?: return@post
            // 라벨 자체보다 그 설정 줄(라벨의 부모 줄)을 강조해야 스위치·슬라이더까지 한눈에 들어온다.
            val row = (target.parent as? View)?.takeIf { it !== detailContainer } ?: target
            var y = 0
            var v: View = row
            while (v !== detailContainer) {
                y += v.top
                v = v.parent as? View ?: break
            }
            detailScroll.smoothScrollTo(0, (y - dp(24)).coerceAtLeast(0))
            val flash = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(themeColor(R.attr.uiAccentContainer))
                alpha = 0
            }
            row.foreground = flash
            android.animation.ValueAnimator.ofInt(0, 200, 200, 0).apply {
                duration = 1400
                addUpdateListener { flash.alpha = it.animatedValue as Int; row.invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { row.foreground = null }
                })
                start()
            }
        }
    }

    /** [root] 아래에서 글자가 정확히 [text] 인 첫 TextView(보이는 것만). */
    private fun findTextView(root: View, text: String): TextView? {
        if (root.visibility != View.VISIBLE) return null
        if (root is TextView && root.text?.toString() == text) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) findTextView(root.getChildAt(i), text)?.let { return it }
        return null
    }

    // ── 우측 상세 ────────────────────────────────────────────────────────────────

    private fun showGroup(id: String) {
        currentGroup = id
        // 그룹을 한 번 열면 그 NEW 표시는 이 사용자에게서 사라진다.
        groupDefs().firstOrNull { it.id == id }?.newMarker?.let { prefs.markSeen(it) }
        renderRail()
        renderDetail()
        if (!twoPane) {
            railRoot.visibility = View.GONE
            detailScroll.visibility = View.VISIBLE
        }
        detailScroll.scrollTo(0, 0)
    }

    private fun showList() {
        railRoot.visibility = View.VISIBLE
        detailScroll.visibility = View.GONE
    }

    private fun renderDetail() {
        // ⚠️ 이 두 목록은 **지금 화면에 붙어 있는 뷰만** 담아야 한다. 그룹을 옮길 때마다 상세 뷰를
        // 새로 만들므로, 비우지 않으면 이미 떨어져 나간 뷰가 계속 쌓여 syncToggles/refreshPreviews 가
        // 죽은 뷰까지 훑고 참조를 붙들어 둔다(그룹을 오갈수록 무한히 증가).
        boundToggles.clear()
        previewRefreshers.clear()
        detailContainer.removeAllViews()
        val g = groupDefs().firstOrNull { it.id == currentGroup } ?: return
        detailContainer.addView(TextView(this).apply {
            text = getString(g.titleRes)
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(themeColor(R.attr.uiOnSurface))
            setPadding(dp(2), dp(6), 0, 0)
        })
        detailContainer.addView(descRow(getString(g.descRes)))
        movedNoticesFor(g.id).forEach { detailContainer.addView(movedRow(it)) }
        g.build(detailContainer)
    }

    /**
     * "이사 갔어요" 안내 — 원래 이 그룹 자리에 있던 설정이 어디로 갔는지 알려주고 눌러서 이동한다.
     * NEW 와 같은 규칙으로 저절로 만료된다([Prefs.shouldShowMarker]).
     */
    private fun movedRow(m: MovedNotice): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                // 점선 테두리 — 이건 설정이 아니라 이정표라는 뜻.
                setStroke(dp(1), themeColor(R.attr.uiDivider), dp(4).toFloat(), dp(3).toFloat())
            }
            setOnClickListener { prefs.markSeen(m.marker); showGroup(m.toGroup) }
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_moved)
            imageTintList = android.content.res.ColorStateList.valueOf(themeColor(R.attr.uiOnSurfaceMuted))
            layoutParams = LinearLayout.LayoutParams(dp(19), dp(19))
        })
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f; it.marginStart = dp(11) }
        }
        texts.addView(TextView(this).apply {
            text = getString(R.string.settings_moved_format, getString(m.whatRes), getString(m.whereRes))
            textSize = 13.5f
            setTextColor(themeColor(R.attr.uiOnSurface))
        })
        texts.addView(TextView(this).apply {
            text = getString(R.string.settings_moved_hint)
            textSize = 11.5f
            setTextColor(themeColor(R.attr.uiOnSurfaceMuted))
        })
        row.addView(texts)
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron)
            imageTintList = android.content.res.ColorStateList.valueOf(themeColor(R.attr.uiOnSurfaceMuted))
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
        })
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(12) }
        row.layoutParams = lp
        return row
    }

    private fun movedNoticesFor(group: String): List<MovedNotice> = MOVED_NOTICES
        .filter { it.fromGroup == group && showMarker(it.marker) }

    private fun showMarker(markerId: String?): Boolean {
        if (markerId == null) return false
        val since = Prefs.MARKER_SINCE[markerId] ?: return false
        return prefs.shouldShowMarker(markerId, since, appVersionCode())
    }

    /**
     * 현재 앱 versionCode. `BuildConfig` 는 이 프로젝트가 생성하지 않으므로(빌드 기능 미활성)
     * PackageManager 로 읽는다 — 이것 하나 때문에 BuildConfig 생성을 켜면 빌드 산출물만 늘어난다.
     * 조회 실패는 있을 수 없지만, 실패하면 마커를 보여주지 않는 쪽(0)이 안전하다.
     */
    private fun appVersionCode(): Int = runCatching {
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
    }.getOrDefault(0)

    // ── 그룹 정의 ────────────────────────────────────────────────────────────────

    private class GroupDef(
        val id: String,
        val titleRes: Int,
        val descRes: Int,
        val iconRes: Int,
        val newMarker: String?,
        val summary: () -> String,
        val build: (LinearLayout) -> Unit
    )

    private class MovedNotice(
        val marker: String,
        val fromGroup: String,
        val toGroup: String,
        val whatRes: Int,
        val whereRes: Int
    )

    private fun onOff(v: Boolean): String =
        getString(if (v) R.string.quick_state_on else R.string.quick_state_off)

    private fun groupDefs(): List<GroupDef> = listOf(
        GroupDef(
            GROUP_FLASH, R.string.settings_group_flash, R.string.settings_group_flash_desc,
            R.drawable.ic_grp_flash, Prefs.MARKER_FLASH_OPACITY,
            summary = {
                "${onOff(prefs.flashEnabled)} · ${prefs.flashDurationMs}ms · " +
                    "${prefs.flashCount}회 · ${prefs.flashOpacityPercent}%"
            }
        ) { c ->
            c.addView(sectionCard(getString(R.string.settings_flash)).apply {
                addView(boundSwitchRow(getString(R.string.settings_flash_enabled), { prefs.flashEnabled }) {
                    prefs.flashEnabled = it; markSaved(); refreshRailSummaries()
                })
                addView(
                    sliderRow(
                        label = getString(R.string.settings_flash_duration),
                        min = 100, max = 500, step = 50, value = prefs.flashDurationMs, suffix = "ms"
                    ) { prefs.flashDurationMs = it; markSaved(); refreshRailSummaries() }
                )
                addView(
                    sliderRow(
                        label = getString(R.string.settings_flash_count),
                        min = 1, max = 5, step = 1, value = prefs.flashCount, suffix = "회"
                    ) { prefs.flashCount = it; markSaved(); refreshRailSummaries() }
                )
                addView(
                    sliderRow(
                        label = getString(R.string.settings_flash_opacity),
                        min = Prefs.MIN_OPACITY_PCT, max = 100, step = 5,
                        value = prefs.flashOpacityPercent, suffix = "%"
                    ) { prefs.flashOpacityPercent = it; markSaved(); refreshPreviews(); refreshRailSummaries() }
                )
                addView(descRow(getString(R.string.settings_flash_opacity_desc)))
            })
            c.addView(sectionCard(getString(R.string.settings_languages)).apply {
                addView(descRow(getString(R.string.settings_languages_desc)))
                addView(langSwitch(ImeLocaleParser.KO, R.string.settings_lang_ko))
                addView(langSwitch(ImeLocaleParser.EN, R.string.settings_lang_en))
                // [일본어 비활성화] 일본어 토글 주석(추후 재도입 위해 보존).
                // addView(langSwitch(ImeLocaleParser.JA, R.string.settings_lang_ja))
            })
            c.addView(sectionCard(getString(R.string.settings_flash_colors)).apply {
                addView(colorEditor(ImeLocaleParser.KO, getString(R.string.settings_lang_ko)))
                addView(colorEditor(ImeLocaleParser.EN, getString(R.string.settings_lang_en)))
                // [일본어 비활성화] 일본어 색상 편집기 주석(추후 재도입 위해 보존).
                // addView(colorEditor(ImeLocaleParser.JA, getString(R.string.settings_lang_ja)))
            })
        },

        GroupDef(
            GROUP_BADGE, R.string.settings_group_badge, R.string.settings_group_badge_desc,
            R.drawable.ic_grp_badge, Prefs.MARKER_BADGE_OPACITY,
            summary = {
                val size = resources.getStringArray(R.array.badge_size_labels)
                    .getOrElse(prefs.badgeSize) { "" }
                "${onOff(prefs.badgeEnabled)} · $size · ${prefs.badgeBgOpacityPercent}%"
            }
        ) { c ->
            c.addView(sectionCard(getString(R.string.settings_badge)).apply {
                addView(boundSwitchRow(getString(R.string.settings_badge_enabled), { prefs.badgeEnabled }) {
                    prefs.badgeEnabled = it; markSaved(); refreshRailSummaries()
                })
                addView(badgeSizeRow())
                addView(
                    colorPickerRow(
                        getString(R.string.settings_badge_bg_color), prefs.badgeBgColorHex,
                        opacityPct = { prefs.badgeBgOpacityPercent }
                    ) { prefs.badgeBgColorHex = it }
                )
                addView(
                    sliderRow(
                        label = getString(R.string.settings_badge_bg_opacity),
                        min = Prefs.MIN_OPACITY_PCT, max = 100, step = 5,
                        value = prefs.badgeBgOpacityPercent, suffix = "%"
                    ) { prefs.badgeBgOpacityPercent = it; markSaved(); refreshPreviews(); refreshRailSummaries() }
                )
                // 글씨색은 불투명도 설정이 없다(항상 불투명) — 배경이 흐려도 글자는 읽혀야 하므로.
                addView(colorPickerRow(getString(R.string.settings_badge_text_color), prefs.badgeTextColorHex) {
                    prefs.badgeTextColorHex = it
                })
            })
            c.addView(sectionCard(getString(R.string.settings_badge_tap)).apply {
                addView(descRow(getString(R.string.settings_badge_tap_desc)))
                addView(badgeTapActionRow())
                addView(descRow(getString(R.string.settings_badge_tap_hide_warning)))
                addView(descRow(getString(R.string.settings_badge_long_press_desc)))
            })
        },

        GroupDef(
            GROUP_MENU, R.string.settings_group_menu, R.string.settings_group_menu_desc,
            R.drawable.ic_grp_menu, null,
            summary = {
                getString(R.string.settings_group_menu_summary, prefs.quickMenuOrder.size) +
                    " · " + onOff(prefs.radialReduceMotion)
            }
        ) { c ->
            c.addView(sectionCard(getString(R.string.settings_quick_menu_order)).apply {
                addView(descRow(getString(R.string.settings_quick_menu_order_desc)))
                addView(quickMenuOrderSection())
            })
            c.addView(sectionCard(getString(R.string.settings_radial)).apply {
                addView(descRow(getString(R.string.settings_radial_colors_desc)))
                addView(colorPickerRow(getString(R.string.settings_radial_accent_color), prefs.radialAccentColorHex) {
                    prefs.radialAccentColorHex = it
                })
                addView(colorPickerRow(getString(R.string.settings_radial_glow_color), prefs.radialGlowColorHex) {
                    prefs.radialGlowColorHex = it
                })
                addView(descRow(getString(R.string.settings_radial_reduce_motion_desc)))
                addView(switchRow(getString(R.string.settings_radial_reduce_motion), prefs.radialReduceMotion) {
                    prefs.radialReduceMotion = it; markSaved(); refreshRailSummaries()
                })
            })
        },

        GroupDef(
            GROUP_REPLACE, R.string.settings_group_replace, R.string.settings_group_replace_desc,
            R.drawable.ic_grp_replace, null,
            summary = {
                val n = prefs.typoExceptionWords.size
                "${onOff(prefs.replaceEnabled)} · ${prefs.replaceConfidence}%" + (if (n > 0) " · 예외 $n" else "")
            }
        ) { c ->
            c.addView(sectionCard(getString(R.string.settings_replace)).apply {
                addView(boundSwitchRow(getString(R.string.settings_replace_enabled), { prefs.replaceEnabled }) {
                    prefs.replaceEnabled = it; markSaved(); refreshRailSummaries()
                })
                addView(
                    sliderRow(
                        label = getString(R.string.settings_replace_confidence),
                        min = 50, max = 90, step = 5, value = prefs.replaceConfidence, suffix = "%"
                    ) { prefs.replaceConfidence = it; markSaved(); refreshRailSummaries() }
                )
                addView(descRow(getString(R.string.settings_replace_confidence_desc)))
            })
            c.addView(sectionCard(getString(R.string.settings_replace_exceptions)).apply {
                addView(descRow(getString(R.string.settings_replace_exceptions_desc)))
                addView(typoExceptionEditor())
            })
        },

        GroupDef(
            GROUP_NOFOCUS, R.string.settings_group_nofocus, R.string.settings_group_nofocus_desc,
            R.drawable.ic_grp_warn, null,
            summary = { "${onOff(prefs.noFocusEnabled)} · ${prefs.noFocusThreshold}회" }
        ) { c ->
            c.addView(sectionCard(getString(R.string.settings_nofocus)).apply {
                addView(descRow(getString(R.string.settings_nofocus_desc)))
                addView(switchRow(getString(R.string.settings_nofocus_enabled), prefs.noFocusEnabled) {
                    prefs.noFocusEnabled = it; markSaved(); refreshRailSummaries()
                })
                addView(
                    sliderRow(
                        label = getString(R.string.settings_nofocus_threshold),
                        min = 1, max = 5, step = 1, value = prefs.noFocusThreshold, suffix = "회"
                    ) { prefs.noFocusThreshold = it; markSaved(); refreshRailSummaries() }
                )
            })
        },

        GroupDef(
            GROUP_KEYBOARD, R.string.settings_group_keyboard, R.string.settings_group_keyboard_desc,
            R.drawable.ic_grp_keyboard, null,
            summary = {
                getString(R.string.settings_group_keyboard_summary,
                    onOff(prefs.excludeTouchKeyboard), onOff(prefs.keyboardConnectNotify))
            }
        ) { c ->
            c.addView(sectionCard(getString(R.string.settings_exclude_touch_kb)).apply {
                addView(descRow(getString(R.string.settings_exclude_touch_kb_desc)))
                addView(switchRow(getString(R.string.settings_exclude_touch_kb_enabled), prefs.excludeTouchKeyboard) {
                    prefs.excludeTouchKeyboard = it; markSaved(); refreshRailSummaries()
                })
            })
            c.addView(sectionCard(getString(R.string.settings_keyboard_connect_notify)).apply {
                addView(descRow(getString(R.string.settings_keyboard_connect_notify_desc)))
                addView(switchRow(getString(R.string.settings_keyboard_connect_notify), prefs.keyboardConnectNotify) {
                    prefs.keyboardConnectNotify = it; markSaved(); refreshRailSummaries()
                })
            })
        },

        GroupDef(
            GROUP_DIAG, R.string.settings_group_diag, R.string.settings_group_diag_desc,
            R.drawable.ic_grp_diag, null,
            summary = { onOff(prefs.diagnosticKeyLoggingEnabled) }
        ) { c ->
            c.addView(sectionCard(getString(R.string.settings_diagnostic)).apply {
                addView(descRow(getString(R.string.settings_diagnostic_desc)))
                addView(switchRow(getString(R.string.settings_diagnostic_enabled), prefs.diagnosticKeyLoggingEnabled) {
                    prefs.diagnosticKeyLoggingEnabled = it; markSaved(); refreshRailSummaries()
                })
                addView(diagnosticResultRow())
                // 하위 옵션 — 부모("전환 키 기록")와 같은 카드에 들여쓰기로 종속 관계를 보인다.
                addView(subsectionTitle(R.string.settings_diagnostic_sub_title))
                addView(descRow(getString(R.string.settings_diagnostic_pause_with_touch_kb_desc)))
                addView(
                    switchRow(
                        getString(R.string.settings_diagnostic_pause_with_touch_kb),
                        prefs.diagnosticPausedByTouchKeyboardExclude
                    ) { prefs.diagnosticPausedByTouchKeyboardExclude = it; markSaved() }
                )
            })
        },

        GroupDef(
            GROUP_THEME, R.string.settings_group_theme, R.string.settings_group_theme_desc,
            R.drawable.ic_grp_theme, Prefs.MARKER_THEME,
            summary = { getString(themeLabelRes(prefs.uiTheme)) }
        ) { c ->
            c.addView(sectionCard(getString(R.string.settings_group_theme)).apply {
                addView(themePicker())
            })
            if (prefs.uiTheme == Prefs.THEME_CUSTOM) c.addView(customThemeEditor())
            c.addView(sectionCard(getString(R.string.settings_theme_scope_title)).apply {
                addView(descRow(getString(R.string.settings_theme_scope_desc)))
            })
        }
    )

    private fun themeLabelRes(id: String): Int = when (id) {
        Prefs.THEME_LIGHT -> R.string.settings_theme_light
        Prefs.THEME_DARK -> R.string.settings_theme_dark
        Prefs.THEME_BEIGE -> R.string.settings_theme_beige
        Prefs.THEME_CYBER -> R.string.settings_theme_cyber
        Prefs.THEME_HIGH_CONTRAST -> R.string.settings_theme_high_contrast
        Prefs.THEME_CUSTOM -> R.string.settings_theme_custom
        else -> R.string.settings_theme_system
    }

    /**
     * 테마 카드(2열). 각 카드 왼쪽의 작은 화면 그림은 **그 테마의 실제 색**(바탕·카드·강조·글자)이라,
     * 고르기 전에 설명이 말하는 모습을 바로 볼 수 있다 — 예전엔 카드가 전부 지금 테마 색의 글자뿐이라
     * "종이 같은"·"네온" 같은 설명을 확인하려면 하나씩 눌러 봐야 했다. 고르면 즉시 [recreate].
     */
    private fun themePicker(): View {
        val grid = GridLayout(this).apply {
            columnCount = themeColumns()
            setPadding(0, dp(8), 0, dp(4))
        }
        Prefs.UI_THEME_IDS.forEach { id ->
            val selected = prefs.uiTheme == id
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(12), dp(10))
                isClickable = true
                isFocusable = true
                contentDescription = getString(themeLabelRes(id)) + ", " + getString(themeDescRes(id))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setStroke(
                        dp(if (selected) 2 else 1),
                        if (selected) themeColor(R.attr.uiAccent) else themeColor(R.attr.uiDivider)
                    )
                }
                setOnClickListener { selectTheme(id) }
            }
            card.addView(View(this).apply {
                background = themeThumbnail(UiPalette.forTheme(this@SettingsActivity, id))
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(34)).also { it.marginEnd = dp(10) }
            })
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.weight = 1f }
            }
            texts.addView(TextView(this).apply {
                text = getString(themeLabelRes(id))
                textSize = 14f
                setTextColor(themeColor(R.attr.uiOnSurface))
                if (selected) setTypeface(typeface, Typeface.BOLD)
            })
            texts.addView(TextView(this).apply {
                text = getString(themeDescRes(id))
                textSize = 11.5f
                setTextColor(themeColor(R.attr.uiOnSurfaceMuted))
                setPadding(0, dp(2), 0, 0)
            })
            card.addView(texts)
            card.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            grid.addView(card)
        }
        return grid
    }

    private fun selectTheme(id: String) {
        if (prefs.uiTheme == id) return
        // 사용자 지정을 처음 고르면 지금 보던 테마의 4색에서 시작한다 — 고르자마자 화면이 엉뚱한
        // 기본색으로 튀지 않고, "지금 테마에서 몇 군데만 바꾸기"가 자연스러운 출발점이 된다.
        if (id == Prefs.THEME_CUSTOM && !prefs.hasCustomThemeSeeds()) copyThemeToCustom(prefs.uiTheme)
        prefs.uiTheme = id
        markSaved()
        recreate()
    }

    /** [themeId] 테마의 배경·카드·글자·강조를 사용자 지정 4색으로 복사한다. */
    private fun copyThemeToCustom(themeId: String) {
        val p = UiPalette.forTheme(this, themeId)
        prefs.setCustomThemeSeed(Prefs.CUSTOM_BG, ColorMath.toHex(p.bg))
        prefs.setCustomThemeSeed(Prefs.CUSTOM_SURFACE, ColorMath.toHex(p.surface))
        prefs.setCustomThemeSeed(Prefs.CUSTOM_TEXT, ColorMath.toHex(p.onSurface))
        prefs.setCustomThemeSeed(Prefs.CUSTOM_ACCENT, ColorMath.toHex(p.accent))
    }

    /** 테마 미리보기 그림(46×34dp 고정 칸) — 바탕 위에 카드 한 장, 그 안에 강조색 막대와 글자 두 줄. */
    private fun themeThumbnail(p: UiPalette): android.graphics.drawable.Drawable {
        fun rect(color: Int, radius: Int, strokeColor: Int = 0) = GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(color)
            if (strokeColor != 0) setStroke(dp(1), strokeColor)
        }
        // 카드 테두리: 고대비/사이버펑크는 윤곽색, 나머지는 구분선 — 실제 화면과 같은 규칙.
        val cardStroke = if (ColorMath.alpha(p.outline) > 0) p.outline else p.divider
        val layers = arrayOf(
            rect(p.bg, 7, p.divider),
            rect(p.surface, 4, cardStroke),
            rect(p.accent, 2),
            rect(p.onSurface, 1),
            rect(p.onSurfaceMuted, 1),
        )
        return android.graphics.drawable.LayerDrawable(layers).apply {
            setLayerInset(1, dp(6), dp(6), dp(6), dp(5))
            setLayerInset(2, dp(10), dp(10), dp(22), dp(20))
            setLayerInset(3, dp(10), dp(17), dp(14), dp(14))
            setLayerInset(4, dp(10), dp(22), dp(20), dp(9))
        }
    }

    /**
     * 사용자 지정 테마 편집: 4색만 고르면 나머지 역할은 [UiPalette.derive] 가 기본 테마와 같은
     * 규칙으로 채운다. 색을 고를 때마다 [recreate] 해 화면 전체가 바로 그 팔레트로 바뀐다(가장
     * 확실한 미리보기). 스크롤 위치는 상세 ScrollView 의 고정 id 로 보존된다.
     */
    private fun customThemeEditor(): View = sectionCard(getString(R.string.settings_theme_custom_title)).apply {
        addView(descRow(getString(R.string.settings_theme_custom_help)))
        val slots = listOf(
            Triple(Prefs.CUSTOM_BG, R.string.settings_theme_custom_bg, THEME_BG_SWATCHES),
            Triple(Prefs.CUSTOM_SURFACE, R.string.settings_theme_custom_surface, THEME_BG_SWATCHES),
            Triple(Prefs.CUSTOM_TEXT, R.string.settings_theme_custom_text, THEME_TEXT_SWATCHES),
            Triple(Prefs.CUSTOM_ACCENT, R.string.settings_theme_custom_accent, PALETTE),
        )
        slots.forEach { (slot, labelRes, swatches) ->
            // key 를 줘서 recreate() 뒤에도 이 팔레트가 펼쳐진 채로 남게 한다(사용자 제보,
            // 2026-09) — 색 고를 때마다 recreate() 가 화면을 통째로 새로 그려 팔레트가 매번
            // 접힌 채로 시작하면, 여러 색을 잇달아 바꿔볼 때마다 다시 펼쳐야 해 불편했다.
            addView(
                colorPickerRow(
                    getString(labelRes), prefs.customThemeSeed(slot), swatches = swatches, key = "custom_$slot"
                ) { hex ->
                    if (hex.equals(prefs.customThemeSeed(slot), ignoreCase = true)) return@colorPickerRow
                    prefs.setCustomThemeSeed(slot, hex)
                    markSaved()
                    recreate()
                }
            )
        }
        // 가독성 보정 안내: 고른 글자/강조색이 배경 위에서 안 읽혀 자동으로 진하게(밝게) 했으면 알린다
        // — 조용히 바꾸면 "고른 색이 안 먹는다"로 보인다.
        val palette = prefs.customPalette()
        val adjusted = listOf(
            Triple(R.string.settings_theme_custom_surface, Prefs.CUSTOM_SURFACE, palette.surface),
            Triple(R.string.settings_theme_custom_text, Prefs.CUSTOM_TEXT, palette.onSurface),
            Triple(R.string.settings_theme_custom_accent, Prefs.CUSTOM_ACCENT, palette.accent),
        ).filter { (_, slot, effective) ->
            !ColorMath.toHex(effective).equals(prefs.customThemeSeed(slot), ignoreCase = true)
        }
        if (adjusted.isNotEmpty()) {
            addView(descRow(adjusted.joinToString("\n") { (labelRes, slot, effective) ->
                getString(
                    R.string.settings_theme_custom_adjusted,
                    getString(labelRes), prefs.customThemeSeed(slot), ColorMath.toHex(effective)
                )
            }))
        }
        // 출발점: 기본 테마의 4색을 그대로 가져와서 거기서부터 바꾼다.
        addView(TextView(this@SettingsActivity).apply {
            text = getString(R.string.settings_theme_custom_start_from)
            textSize = 13f
            setTextColor(themeColor(R.attr.uiOnSurface))
            setPadding(0, dp(12), 0, dp(6))
        })
        val starts = GridLayout(this@SettingsActivity).apply { columnCount = themeColumns() + 1 }
        Prefs.UI_THEME_IDS.filter { it != Prefs.THEME_SYSTEM && it != Prefs.THEME_CUSTOM }.forEach { id ->
            starts.addView(TextView(this@SettingsActivity).apply {
                text = getString(themeLabelRes(id))
                textSize = 13f
                gravity = Gravity.CENTER
                minHeight = dp(40)
                setTextColor(themeColor(R.attr.uiOnAccentContainer))
                background = UiDrawables.buttonTonal(this@SettingsActivity)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    copyThemeToCustom(id)
                    markSaved()
                    recreate()
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
            })
        }
        addView(starts)
    }

    /**
     * 사용자 지정 테마일 때 위젯 틴트를 팔레트로 맞춘다. 스위치·슬라이더·라디오·입력칸의 기본 색은
     * 스타일의 colorAccent 에서 오는데, 사용자가 고른 강조색은 스타일에 없다(런타임 팔레트뿐).
     * 기본 테마에서는 아무것도 하지 않는다 — 스타일이 이미 정확한 색을 준다.
     */
    private fun tintForCustomPalette(v: View) {
        val p = ThemeManager.customPalette ?: return
        fun states(on: Int, off: Int, state: Int = android.R.attr.state_checked) =
            android.content.res.ColorStateList(arrayOf(intArrayOf(state), intArrayOf()), intArrayOf(on, off))
        when (v) {
            is SwitchCompat -> {
                val thumbOff = if (p.isDark) ColorMath.mix(p.onSurfaceMuted, p.surface, 0.15) else ColorMath.mix(p.surface, p.onSurface, 0.06)
                v.thumbTintList = states(p.accent, thumbOff)
                v.trackTintList = states(ColorMath.withAlpha(p.accent, 0x4D), ColorMath.withAlpha(p.onSurface, 0x4D))
            }
            is SeekBar -> {
                v.progressTintList = android.content.res.ColorStateList.valueOf(p.accent)
                v.thumbTintList = android.content.res.ColorStateList.valueOf(p.accent)
                v.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(ColorMath.withAlpha(p.onSurface, 0x4D))
            }
            is RadioButton -> v.buttonTintList = states(p.accent, p.onSurfaceMuted)
            is EditText -> {
                v.backgroundTintList = states(p.accent, p.divider, android.R.attr.state_focused)
                v.highlightColor = ColorMath.withAlpha(p.accent, 0x55)
                v.textCursorDrawable?.setTint(p.accent)
            }
        }
    }

    private fun themeDescRes(id: String): Int = when (id) {
        Prefs.THEME_LIGHT -> R.string.settings_theme_light_desc
        Prefs.THEME_DARK -> R.string.settings_theme_dark_desc
        Prefs.THEME_BEIGE -> R.string.settings_theme_beige_desc
        Prefs.THEME_CYBER -> R.string.settings_theme_cyber_desc
        Prefs.THEME_HIGH_CONTRAST -> R.string.settings_theme_high_contrast_desc
        Prefs.THEME_CUSTOM -> R.string.settings_theme_custom_desc
        else -> R.string.settings_theme_system_desc
    }

    /** 검색 색인 캐시 — 글자를 칠 때마다 수십 개 항목을 새로 만들 이유가 없다(저사양 원칙). */
    private var searchIndexCache: List<SettingsSearch.Item>? = null

    private fun searchIndex(): List<SettingsSearch.Item> =
        searchIndexCache ?: buildSearchIndex().also { searchIndexCache = it }

    /**
     * 검색 색인 — 설정 화면의 모든 설정을 항목마다 **태그 여러 개**와 함께 둔다. 태그는 사용자가 떠올릴
     * 법한 다른 말(동의어·구어체·영어·증상 표현: "키보드가 자꾸 바뀜", "번쩍임")을 쉼표로 나열한다.
     * 이름·경로·태그 매칭과 초성·한영타·오타 보정은 [SettingsSearch] 가 한다.
     * 항목을 추가할 땐 **anchors 에 화면에 실제로 보이는 문구**(스위치·슬라이더 라벨이나 카드 제목)를
     * 넣어야 결과를 눌렀을 때 그 줄로 스크롤된다(못 찾으면 그룹 맨 위).
     */
    private fun buildSearchIndex(): List<SettingsSearch.Item> {
        fun s(res: Int) = getString(res)
        fun path(groupRes: Int, cardRes: Int? = null) =
            if (cardRes == null) s(groupRes) else "${s(groupRes)} › ${s(cardRes)}"
        fun item(name: String, path: String, group: String, tags: String, vararg anchors: String) =
            SettingsSearch.Item(
                name, path, group,
                tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                if (anchors.isEmpty()) listOf(name) else anchors.toList()
            )
        val flash = R.string.settings_group_flash
        val badge = R.string.settings_group_badge
        val menu = R.string.settings_group_menu
        val theme = R.string.settings_group_theme
        val quickActions = listOf(
            R.string.quick_app, R.string.quick_settings, R.string.quick_flash, R.string.quick_replace,
            R.string.quick_badge, R.string.quick_nofocus, R.string.quick_touchkb, R.string.quick_lowspec,
            R.string.quick_badge_size
        ).joinToString(",") { s(it) }
        return listOf(
            // ── 언어 전환 플래시 ──
            item(s(R.string.settings_flash_enabled), path(flash, R.string.settings_flash), GROUP_FLASH,
                "플래시, 번쩍, 번쩍임, 깜박임, 깜빡임, 화면 색, 전환 알림, 한영 전환 표시, 언어 바뀜, flash, 끄기, 켜기"),
            item(s(R.string.settings_flash_duration), path(flash, R.string.settings_flash), GROUP_FLASH,
                "속도, 시간, 길이, 빠르게, 느리게, 오래, 짧게, ms, 밀리초, duration"),
            item(s(R.string.settings_flash_count), path(flash, R.string.settings_flash), GROUP_FLASH,
                "횟수, 번, 몇 번, 여러 번, 두 번, 반복, count"),
            item(s(R.string.settings_flash_opacity), path(flash, R.string.settings_flash), GROUP_FLASH,
                "투명, 투명도, 불투명, 알파, 흐리게, 진하게, 연하게, 눈부심, 눈 아픔, opacity, alpha"),
            item(s(R.string.settings_languages), path(flash), GROUP_FLASH,
                "언어, 한국어, 영어, 한글, 영문, 언어별 켜기, 특정 언어만, language"),
            item(s(R.string.settings_flash_colors), path(flash), GROUP_FLASH,
                "색, 색상, 컬러, 빨강, 파랑, 한국어 색, 영어 색, 팔레트, 색상코드, hex, color"),
            // ── 상시 배지 ──
            item(s(R.string.settings_badge_enabled), path(badge, R.string.settings_badge), GROUP_BADGE,
                "배지, 뱃지, 표시, 아이콘, 한, EN, 떠 있는, 항상, 숨기기, 없애기, 다시 켜기, badge, 위치, 드래그, 옮기기"),
            item(s(R.string.settings_badge_size), path(badge, R.string.settings_badge), GROUP_BADGE,
                "크기, 사이즈, 작게, 크게, 소, 중, 대, size"),
            item(s(R.string.settings_badge_bg_color), path(badge, R.string.settings_badge), GROUP_BADGE,
                "배경, 배경색, 색, 색상, 컬러, 검정, 팔레트, 색상코드, hex, color"),
            item(s(R.string.settings_badge_bg_opacity), path(badge, R.string.settings_badge), GROUP_BADGE,
                "투명, 투명도, 불투명, 알파, 흐리게, 진하게, 비치게, opacity"),
            item(s(R.string.settings_badge_text_color), path(badge, R.string.settings_badge), GROUP_BADGE,
                "글씨, 글자, 글씨색, 글자색, 텍스트, 색, 컬러, 흰색, text color"),
            item(s(R.string.settings_badge_tap), path(badge), GROUP_BADGE,
                "탭, 누르기, 눌렀을 때, 터치, 클릭, 동작, 바로 실행, 단축, 메뉴 열기, $quickActions"),
            item(getString(R.string.settings_search_item_long_press), path(badge, R.string.settings_badge_tap), GROUP_BADGE,
                "길게, 길게 누르기, 롱프레스, 꾹, 입력기, 입력기 선택, 키보드 바꾸기, 언어 바꾸기, 서브타입, long press",
                s(R.string.settings_badge_tap)),
            // ── 빠른 메뉴 ──
            item(s(R.string.settings_quick_menu_order), path(menu), GROUP_MENU,
                "퀵메뉴, 빠른 메뉴, 래디얼, 원형 메뉴, 부채꼴, 항목, 순서, 드래그, 추가, 빼기, 삭제, 최대 5개, $quickActions"),
            item(s(R.string.settings_radial_accent_color), path(menu, R.string.settings_radial), GROUP_MENU,
                "강조, 강조색, 색, 컬러, 하늘색, 메뉴 색, 오브, 물방울, accent"),
            item(s(R.string.settings_radial_glow_color), path(menu, R.string.settings_radial), GROUP_MENU,
                "발광, 빛, 글로우, 후광, 선 색, 색, 컬러, 파랑, glow"),
            item(s(R.string.settings_radial_reduce_motion), path(menu, R.string.settings_radial), GROUP_MENU,
                "저사양, 움직임, 애니메이션, 버벅, 느림, 렉, 배터리, 가볍게, 모션, reduce motion"),
            // ── 한영타 교체 ──
            item(s(R.string.settings_replace_enabled), path(R.string.settings_group_replace), GROUP_REPLACE,
                "한영타, 영타, 오타, 교체, 변환, 교체 버튼, 자동 수정, dkssud, 안녕, 잘못 친, 영어로 쳐짐"),
            item(s(R.string.settings_replace_confidence), path(R.string.settings_group_replace), GROUP_REPLACE,
                "신뢰도, 정확도, 민감도, 임계값, 기준, 너무 자주 뜸, 안 뜸, 오탐, threshold, 캡스락, CapsLock, 대문자"),
            item(s(R.string.settings_replace_exceptions), path(R.string.settings_group_replace), GROUP_REPLACE,
                "예외, 예외 단어, 제외, 무시, 허용 목록, 화이트리스트, 바꾸지 않을 단어, 약어, 아이디, 교체 안 함, " +
                    "잘못 뜸, 자꾸 뜸, 오탐, 길게 누르기, exception, ignore, whitelist"),
            // ── 입력 경고 ──
            item(s(R.string.settings_nofocus_enabled), path(R.string.settings_group_nofocus), GROUP_NOFOCUS,
                "포커스, 경고, 선택되지 않음, 입력칸, 커서 없음, 허공에 입력, 글자 안 들어감, focus"),
            item(s(R.string.settings_nofocus_threshold), path(R.string.settings_group_nofocus), GROUP_NOFOCUS,
                "임계, 횟수, 몇 번, 민감도, 몇 글자"),
            // ── 키보드 ──
            item(s(R.string.settings_exclude_touch_kb_enabled), path(R.string.settings_group_keyboard, R.string.settings_exclude_touch_kb),
                GROUP_KEYBOARD, "터치 키보드, 화면 키보드, 가상 키보드, 소프트 키보드, 제외, 외장 키보드만, 블루투스 키보드만, 끄기"),
            item(s(R.string.settings_keyboard_connect_notify), path(R.string.settings_group_keyboard), GROUP_KEYBOARD,
                "블루투스, 연결, 끊김, 해제, 알림, 토스트, 배터리, 외장 키보드, bluetooth"),
            // ── 전환 원인 진단 ──
            item(s(R.string.settings_diagnostic_enabled), path(R.string.settings_group_diag), GROUP_DIAG,
                "진단, 원인, 범인, 단축키, 자동 전환, 저절로 바뀜, 멋대로 바뀜, 키보드가 자꾸 바뀜, 한영이 바뀜, 기록, 로그, 스페이스"),
            item(getString(R.string.settings_search_item_diag_history), path(R.string.settings_group_diag), GROUP_DIAG,
                "기록, 이력, 로그, 지우기, 삭제, 초기화, 최근 감지", s(R.string.settings_diagnostic_clear)),
            item(s(R.string.settings_diagnostic_pause_with_touch_kb), path(R.string.settings_group_diag), GROUP_DIAG,
                "일시정지, 멈춤, 터치 키보드, 화면 키보드, 하위 옵션"),
            // ── 화면 테마 ──
            item(s(theme), path(theme), GROUP_THEME,
                "테마, 모드, 색, 스킨, 배경색, 어둡게, 밝게, 야간, 밤, 눈 편한, theme"),
            item(s(R.string.settings_theme_system), path(theme), GROUP_THEME, "시스템, 기본, 자동, 기기 설정 따라, system"),
            item(s(R.string.settings_theme_light), path(theme), GROUP_THEME, "라이트, 밝게, 흰색, 화이트, light"),
            item(s(R.string.settings_theme_dark), path(theme), GROUP_THEME, "다크, 어둡게, 검정, 블랙, 야간, 다크모드, dark"),
            item(s(R.string.settings_theme_beige), path(theme), GROUP_THEME, "베이지, 종이, 미색, 따뜻한, 세피아, 눈 편한, beige"),
            item(s(R.string.settings_theme_cyber), path(theme), GROUP_THEME, "사이버펑크, 네온, 시안, 마젠타, 보라, cyberpunk"),
            item(s(R.string.settings_theme_high_contrast), path(theme), GROUP_THEME,
                "고대비, 대비, 잘 보이게, 저시력, 노랑, 선명, high contrast"),
            item(s(R.string.settings_theme_custom), path(theme), GROUP_THEME,
                "사용자 지정, 커스텀, 직접, 내 색, 나만의, 팔레트, custom"),
            item(s(R.string.settings_theme_custom_title), path(theme, R.string.settings_theme_custom), GROUP_THEME,
                "배경, 카드, 글자, 강조, 네 가지 색, 4색, 가독성 보정",
                s(R.string.settings_theme_custom_title), s(R.string.settings_theme_custom)),
            item(s(R.string.settings_theme_custom_start_from), path(theme, R.string.settings_theme_custom), GROUP_THEME,
                "다른 테마, 가져오기, 복사, 시작하기, 불러오기",
                s(R.string.settings_theme_custom_start_from), s(R.string.settings_theme_custom)),
            item(s(R.string.settings_theme_scope_title), path(theme), GROUP_THEME,
                "적용 범위, 오버레이, 플래시 색, 배지 색, 안 바뀜"),
        )
    }

    /** 커스텀 톱바: 뒤로가기 + 화면 제목 + 저장 안내(변경 피드백이 화면 밖으로 밀리지 않게 상단 고정). */
    private fun topBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(16), dp(6))
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            contentDescription = getString(R.string.settings_back)
            background = rippleCircleBackground()
            imageTintList = android.content.res.ColorStateList.valueOf(themeColor(R.attr.uiOnSurface))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener {
                if (!twoPane && detailScroll.visibility == View.VISIBLE) showList() else finish()
            }
        }
        val title = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 20f
            setTextColor(themeColor(R.attr.uiOnSurface))
            setTypeface(typeface, Typeface.BOLD)
            // 좁은 화면(폴드 접힘 329dp 등)에서 제목이 두 줄로 밀려 톱바가 뚱뚱해지지 않게.
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f; it.marginStart = dp(8) }
        }
        savedHint = TextView(this).apply {
            text = getString(R.string.settings_apply)
            setTextColor(themeColor(R.attr.uiOnSurfaceMuted))
            textSize = 11.5f
        }
        bar.addView(back)
        bar.addView(title)
        bar.addView(savedHint)
        return bar
    }

    /** 테마의 원형(borderless) 리플 배경 — 정사각형 아이콘 버튼용. */
    private fun rippleCircleBackground(): android.graphics.drawable.Drawable? =
        themeRipple(android.R.attr.selectableItemBackgroundBorderless)

    /**
     * 테마의 경계 있는(bounded) 리플 배경 — 가로로 긴 텍스트 버튼용. borderless 리플은 뷰
     * 크기와 무관하게 원형으로 퍼져 나가 폭이 넓은 라벨에 쓰면 터치 지점이 아니라 엉뚱한
     * 범위가 번지는 것처럼 보인다.
     */
    private fun rippleBoundedBackground(): android.graphics.drawable.Drawable? =
        themeRipple(android.R.attr.selectableItemBackground)

    private fun themeRipple(attr: Int): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return ContextCompat.getDrawable(this, tv.resourceId)
    }

    override fun onResume() {
        super.onResume()
        // 외부(래디얼 메뉴)에서 바뀐 토글 값을 현재 prefs 기준으로 다시 맞춘다(stale 표시 방지).
        // 배지 오버레이는 이 화면 위에도 떠 있어 보면서 메뉴로 토글할 수 있으므로 요약도 함께.
        syncToggles()
        refreshRailSummaries()
        // 서비스가 백그라운드에서 캡처했을 수 있는 최신 진단 결과를 반영.
        refreshDiagnosticResult()
        prefs.register(prefsListener)
    }

    override fun onPause() {
        prefs.unregister(prefsListener)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_GROUP, currentGroup)
        outState.putString(STATE_QUERY, query)
        outState.putBoolean(STATE_IN_DETAIL, !twoPane && detailScroll.visibility == View.VISIBLE)
        outState.putStringArrayList(STATE_OPEN_PALETTES, ArrayList(openPaletteKeys))
    }

    private fun syncToggles() {
        syncingToggles = true
        boundToggles.forEach { (toggle, get) -> toggle.isChecked = get() }
        syncingToggles = false
    }

    /**
     * 외부에서도 바뀔 수 있는 토글 행. 일반 [switchRow] 와 같지만 [boundToggles] 에 등록해
     * onResume 에서 현재 값으로 재동기화한다. 동기화 중에는 [syncingToggles] 가드로 되쓰기를 막는다.
     */
    private fun boundSwitchRow(label: String, get: () -> Boolean, set: (Boolean) -> Unit): SwitchCompat =
        switchRow(label, get()) { if (!syncingToggles) set(it) }
            .also { boundToggles.add(it to get) }

    /** 불투명도 슬라이더가 움직일 때 색 미리보기 원들을 현재 값으로 다시 그린다. */
    private fun refreshPreviews() {
        previewRefreshers.forEach { it() }
    }

    private fun markSaved() {
        savedHint.text = getString(R.string.settings_saved)
    }

    /**
     * 전환 원인 진단(추가 기능 3) 결과 행: 최근 캡처된 키 조합(또는 "없음" 안내) + 상대 시각,
     * 오른쪽에 지우기 버튼. 서비스가 백그라운드에서 [Prefs.lastSwitchTriggers] 를 갱신하므로
     * onResume/prefsListener 양쪽에서 [refreshDiagnosticResult] 로 다시 그린다.
     */
    private fun diagnosticResultRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        diagnosticResultText = TextView(this).apply {
            textSize = 13f
            setTextColor(themeColor(R.attr.uiOnSurface))
            setLineSpacing(dp(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f }
        }
        val clear = TextView(this).apply {
            text = getString(R.string.settings_diagnostic_clear)
            textSize = 13f
            setTextColor(themeColor(R.attr.uiAccent))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            isClickable = true
            isFocusable = true
            background = rippleBoundedBackground()
            setOnClickListener {
                prefs.clearLastSwitchTrigger()
                refreshDiagnosticResult()
            }
        }
        row.addView(diagnosticResultText)
        row.addView(clear)
        refreshDiagnosticResult()
        return row
    }

    /**
     * [Prefs.lastSwitchTriggers] 이력(최신이 맨 앞, 최대 10건)을 한 줄씩 보여준다. 각 건은 "캡처는
     * 됐는데 키를 못 찾았다"(keys=="")와 "이 키 조합이 눌려 있었다" 두 상태 중 하나이고, 이력 자체가
     * 비어 있으면(한 번도 캡처된 적 없음) 안내 문구 하나만 보여준다. 여러 줄을 나란히 보여주는
     * 이유: 전환이 반복될 때마다 매번 같은 키가 뜨면 그게 범인이라는 뜻이므로, 1건만 보이던 예전과
     * 달리 사람이 직접 패턴을 비교할 수 있다.
     */
    private fun refreshDiagnosticResult() {
        if (!::diagnosticResultText.isInitialized) return
        val history = prefs.lastSwitchTriggers
        diagnosticResultText.text = if (history.isEmpty()) {
            getString(R.string.settings_diagnostic_result_empty)
        } else {
            // 10건이 쌓여도 읽히게: 머리말 1줄 + 항목마다 "• 시각 — 키" 한 줄. "못 찾음"의 뜻풀이는
            // 항목마다 반복하지 않고 끝에 한 번만 붙인다(예전엔 긴 문장이 건마다 반복돼 문단이 됐다).
            buildString {
                append(getString(R.string.settings_diagnostic_result_header, Prefs.MAX_SWITCH_TRIGGER_HISTORY))
                history.forEach { t ->
                    append('\n')
                    append(
                        if (t.keys.isEmpty()) getString(R.string.settings_diagnostic_result_unknown, relativeTime(t.atMillis))
                        else getString(R.string.settings_diagnostic_result_label, relativeTime(t.atMillis), t.keys)
                    )
                }
                if (history.any { it.keys.isEmpty() }) {
                    append("\n\n")
                    append(getString(R.string.settings_diagnostic_result_unknown_note))
                }
            }
        }
    }

    private fun relativeTime(at: Long): CharSequence = android.text.format.DateUtils.getRelativeTimeSpanString(
        at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
    )

    // ---------------------------------------------------------------------
    // UI 빌더
    // ---------------------------------------------------------------------

    /** 섹션 카드: 라운드 surface 컨테이너 + 섹션 제목. 항목들은 이 안에 addView 한다. */
    private fun sectionCard(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = UiDrawables.card(this@SettingsActivity)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(12) }
        addView(TextView(this@SettingsActivity).apply {
            text = title
            textSize = 15f
            setTextColor(themeColor(R.attr.uiAccent))
            letterSpacing = 0.01f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })
    }

    /** 항목 아래 붙는 짧은 회색 설명 문구(기능/수치 해설). */
    private fun descRow(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        setTextColor(themeColor(R.attr.uiOnSurfaceMuted))
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun langSwitch(lang: String, labelRes: Int): SwitchCompat =
        switchRow(getString(labelRes), prefs.isLangEnabled(lang)) { checked ->
            prefs.setLangEnabled(lang, checked); markSaved()
        }

    /** 토글 행: 라벨 좌측 + 스위치 우측 (SwitchCompat 자체 텍스트 배치 활용). */
    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): SwitchCompat =
        SwitchCompat(this).apply {
            text = label
            textSize = 15f
            setTextColor(themeColor(R.attr.uiOnSurface))
            tintForCustomPalette(this)
            isChecked = initial
            minHeight = dp(44)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }

    /**
     * 정수 슬라이더 행. SeekBar 는 0..((max-min)/step) 범위로 매핑. 라벨 좌측 + 현재 값 우측 정렬.
     */
    private fun sliderRow(
        label: String,
        min: Int,
        max: Int,
        step: Int,
        value: Int,
        suffix: String,
        onChange: (Int) -> Unit
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(themeColor(R.attr.uiOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f }
        })
        val valueLabel = TextView(this).apply {
            text = getString(R.string.slider_value_short_format, value, suffix)
            textSize = 14f
            setTextColor(themeColor(R.attr.uiAccent))
            setTypeface(typeface, Typeface.BOLD)
        }
        header.addView(valueLabel)

        val steps = (max - min) / step
        val seek = SeekBar(this).apply {
            tintForCustomPalette(this)
            this.max = steps
            progress = ((value - min) / step).coerceIn(0, steps)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = min + p * step
                    valueLabel.text = getString(R.string.slider_value_short_format, v, suffix)
                    // 저장을 onStopTrackingTouch 에만 두면 터치 드래그로만 값이 바뀐다 —
                    // 키보드/D-pad/TalkBack 조작은 이 콜백만 오므로 화면 숫자만 바뀌고 저장이
                    // 안 됐다(접근성 앱의 설정 화면이 접근성 조작 불가였던 문제).
                    if (fromUser) onChange(v)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    onChange(min + (sb?.progress ?: 0) * step)
                }
            })
        }
        container.addView(header)
        container.addView(seek)
        return container
    }

    /**
     * 언어별 플래시 색상 편집기 — 공용 [colorPickerRow] 를 prefs.colorHex 에 연결.
     * ⚠️ [labelMinWidthDp] 로 라벨 폭을 고정한다 — "한국어"(3자)와 "영어"(2자)는 길이가 달라
     * 라벨을 wrap_content 그대로 두면 그 뒤의 색 미리보기 원·입력칸이 행마다 다른 x 위치에서
     * 시작해 두 행이 나란히 있을 때 어긋나 보였다(사용자 제보, 2026-09).
     */
    private fun colorEditor(lang: String, langLabel: String): View =
        colorPickerRow(
            langLabel, prefs.colorHex(lang), opacityPct = { prefs.flashOpacityPercent },
            labelMinWidthDp = LANGUAGE_LABEL_MIN_WIDTH_DP
        ) { hex -> prefs.setColorHex(lang, hex) }

    /**
     * 공용 색 선택 컴포넌트: 라벨 + 원형 미리보기 + #RRGGBB 입력 + 32색 원형 팔레트.
     * 플래시 색/배지 색 설정이 동일 코드를 공유한다(복붙 방지). 선택/입력 시 [onPicked] 에
     * 정규화된 "#RRGGBB" 를 넘기고 저장 안내를 표시한다.
     */
    private fun colorPickerRow(
        label: String,
        initialHex: String,
        /**
         * 이 색에 적용되는 불투명도(%)를 **호출 시점에** 읽는 람다. 기본 100 이라 불투명도 설정이
         * 없는 색(배지 글씨, 메뉴 강조/발광)은 기존과 동일하게 그려진다. 불투명도 슬라이더가
         * 움직이면 [previewRefreshers] 를 통해 여기를 다시 읽어 미리보기가 따라온다.
         */
        opacityPct: () -> Int = { 100 },
        /**
         * 0 이면 라벨 폭이 wrap_content(기본). 여러 [colorPickerRow] 가 같은 카드 안에 나란히
         * 쌓여 세로로 정렬돼 보여야 할 때(예: 한국어/영어 색상 두 행)만 지정한다 — 그 외 단독
         * 행에서는 라벨 길이만큼만 차지하는 게 자연스럽다.
         */
        labelMinWidthDp: Int = 0,
        /** 격자에 보일 견본 색 — 기본은 오버레이용 선명한 32색, 테마 배경·글자는 무채/옅은 색 목록. */
        swatches: List<String> = PALETTE,
        /**
         * 비 null 이면 이 팔레트의 펼침 상태를 [openPaletteKeys] 에 기억한다 — [recreate] 를 유발하는
         * 사용자 지정 테마 색에만 필요하다(그 외 색은 recreate() 가 없어 펼침 상태가 그냥 유지된다).
         */
        key: String? = null,
        onPicked: (String) -> Unit
    ): View {
        // 마지막으로 확정된 유효 색. 잘못된 입력으로 commit 이 거부될 때 입력칸을 되돌리는 기준.
        var currentHex = initialHex
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(4))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }
        val hexInput = EditText(this).apply {
            setText(initialHex)
            filters = arrayOf(InputFilter.LengthFilter(7))
            setSingleLine()
            textSize = 14f
            setTextColor(themeColor(R.attr.uiOnSurface))
            setHintTextColor(themeColor(R.attr.uiOnSurfaceMuted))
            tintForCustomPalette(this)
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = getString(R.string.settings_color_hex)
            // 고정 폭이 아니라 남는 공간을 받는다 — 좁은 화면에서도 팔레트 버튼과 겹치지 않는다.
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f; it.marginStart = dp(10) }
        }
        applyPreview(preview, initialHex, opacityPct())
        // 불투명도 슬라이더가 움직일 때 이 미리보기도 함께 다시 그리도록 등록.
        previewRefreshers.add { applyPreview(preview, currentHex, opacityPct()) }

        fun commit(raw: String) {
            // 파싱 실패 시 normalizeHex 는 기본 회색을 돌려준다 — 그대로 저장하면 입력 도중
            // 포커스를 잃었을 때(팔레트 스크롤 등) 사용자의 기존 색이 말없이 회색으로 날아간다.
            // 유효하지 않으면 저장하지 않고 현재 값으로 입력칸만 되돌린다.
            val normalized = Prefs.normalizeHexOrNull(raw)
            if (normalized == null) {
                hexInput.setText(currentHex)
                return
            }
            currentHex = normalized
            hexInput.setText(normalized)
            applyPreview(preview, normalized, opacityPct())
            onPicked(normalized)
            markSaved()
        }
        hexInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { commit(hexInput.text.toString()); true } else false
        }
        hexInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commit(hexInput.text.toString()) }

        headerRow.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(themeColor(R.attr.uiOnSurface))
            // 예전엔 64dp 고정이라 "배지 배경색"·"메뉴 강조색" 같은 6자 라벨이 잘렸다. 대신
            // labelMinWidthDp 로 필요한 곳(언어별 색상 두 행)만 최소 폭을 줘 정렬을 맞춘다.
            if (labelMinWidthDp > 0) minWidth = dp(labelMinWidthDp)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(10) }
        })
        headerRow.addView(preview)
        headerRow.addView(hexInput)
        container.addView(headerRow)

        // 32색 팔레트 (8 x 4, 원형 스와치) — 기본은 접어 둔다.
        //
        // 색은 한 번 정하면 거의 안 만지는 설정인데, 항상 펼쳐 두면 스와치 32개가 화면을 가득 채워
        // 자주 쓰는 스위치·슬라이더를 밀어낸다(예전 구조에선 팔레트 6벌이 전체 스크롤의 28%였다).
        // 미리보기 원 또는 색 이름을 누르면 열린다.
        // 열 수는 화면 폭에서 계산한다 — 8열 고정이면 8×(32+3+3)=304dp 가 필요해 폰(360dp)과
        // 폴드 접힌 화면(329dp)에서 카드 밖으로 넘쳐 오른쪽 색들이 잘린다.
        val startsOpen = key != null && openPaletteKeys.contains(key)
        val palette = GridLayout(this).apply {
            columnCount = paletteColumns()
            setPadding(0, dp(8), 0, 0)
            visibility = if (startsOpen) View.VISIBLE else View.GONE
        }
        val toggle = TextView(this).apply {
            text = getString(if (startsOpen) R.string.settings_color_palette_close else R.string.settings_color_palette_open)
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(themeColor(R.attr.uiAccent))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            isClickable = true
            isFocusable = true
            background = rippleBoundedBackground()
        }
        fun togglePalette() {
            val opening = palette.visibility != View.VISIBLE
            palette.visibility = if (opening) View.VISIBLE else View.GONE
            toggle.text = getString(
                if (opening) R.string.settings_color_palette_close else R.string.settings_color_palette_open
            )
            if (key != null) {
                if (opening) openPaletteKeys.add(key) else openPaletteKeys.remove(key)
            }
        }
        toggle.setOnClickListener { togglePalette() }
        preview.isClickable = true
        preview.setOnClickListener { togglePalette() }
        headerRow.addView(toggle)
        swatches.forEach { hex ->
            val swatch = View(this).apply {
                val lp = GridLayout.LayoutParams().apply {
                    width = dp(32); height = dp(32)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                layoutParams = lp
                background = swatchDrawable(hex)
                setOnClickListener { commit(hex) }
            }
            palette.addView(swatch)
        }
        container.addView(palette)
        return container
    }

    /** 배지 크기 3단계(소/중/대) 라디오 선택. */
    private fun badgeSizeRow(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.settings_badge_size)
            textSize = 14f
            setTextColor(themeColor(R.attr.uiOnSurface))
        })
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val labels = listOf(
            R.string.settings_badge_size_small,
            R.string.settings_badge_size_medium,
            R.string.settings_badge_size_large
        )
        val buttons = labels.map { res ->
            RadioButton(this).apply {
                id = View.generateViewId()
                setText(res)
                textSize = 14f
                setTextColor(themeColor(R.attr.uiOnSurface))
                tintForCustomPalette(this)
                setPadding(0, 0, dp(16), 0)
            }.also { group.addView(it) }
        }
        buttons.getOrNull(prefs.badgeSize)?.isChecked = true
        group.setOnCheckedChangeListener { _, checkedId ->
            val idx = buttons.indexOfFirst { it.id == checkedId }
            if (idx >= 0) { prefs.badgeSize = idx; markSaved() }
        }
        container.addView(group)
        return container
    }

    /**
     * 배지 탭 동작 10지선다: "메뉴 열기"(기본) + 퀵메뉴 액션 풀 9개 **전체**(퀵메뉴에 실제로 넣은
     * 것만이 아니라 항상 전체 노출) — 배지 탭은 메뉴와 독립된 기능이라 결합할 근거가 약하고,
     * 좁히면 "퀵메뉴에서 선택 해제 시 라디오가 사라짐" 동기화 로직이 새로 필요해진다.
     * [badgeSizeRow] 와 동일한 RadioGroup/RadioButton 패턴이되 인덱스 대신 [Prefs] 의 액션 id
     * 문자열로 매핑한다. 라벨은 [quickMenuActionLabelRes] 를 그대로 재사용해 중복 정의를 피한다.
     */
    private fun badgeTapActionRow(): View {
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val options = listOf(Prefs.BADGE_TAP_MENU to R.string.settings_badge_tap_menu) +
            Prefs.QUICK_MENU_ACTION_IDS.map { it to quickMenuActionLabelRes(it) }
        val buttons = options.map { (_, res) ->
            RadioButton(this).apply {
                id = View.generateViewId()
                setText(res)
                textSize = 14f
                setTextColor(themeColor(R.attr.uiOnSurface))
                tintForCustomPalette(this)
                minHeight = dp(40)
            }.also { group.addView(it) }
        }
        buttons.getOrNull(options.indexOfFirst { it.first == prefs.badgeTapAction })?.isChecked = true
        group.setOnCheckedChangeListener { _, checkedId ->
            val idx = buttons.indexOfFirst { it.id == checkedId }
            if (idx >= 0) { prefs.badgeTapAction = options[idx].first; markSaved() }
        }
        return group
    }

    /**
     * "선택됨"(순서 있음, 드래그로 재정렬) / "추가 가능"(스위치 없이 + 버튼만) 두 컨테이너.
     * [rebuildQuickMenuOrderRows] 가 매번 두 컨테이너를 전부 다시 그린다 — 항목이 최대 9개뿐이라
     * 전체 재생성 비용이 무시할 만하고, 순번 텍스트가 항상 정확히 갱신된다는 이점이 더 크다.
     */
    private lateinit var selectedContainer: LinearLayout
    private lateinit var addableContainer: LinearLayout

    private fun quickMenuOrderSection(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        selectedContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addableContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(subsectionTitle(R.string.settings_quick_menu_selected_title))
        container.addView(selectedContainer)
        container.addView(subsectionTitle(R.string.settings_quick_menu_addable_title))
        container.addView(addableContainer)
        rebuildQuickMenuOrderRows()
        return container
    }

    /** 문자열을 직접 받는 소제목(검색 결과 머리말처럼 개수가 섞이는 경우). */
    private fun subsectionTitleText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(themeColor(R.attr.uiOnSurfaceMuted))
        setPadding(dp(11), dp(10), 0, dp(4))
    }

    private fun subsectionTitle(textRes: Int): TextView = TextView(this).apply {
        text = getString(textRes)
        textSize = 12.5f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(themeColor(R.attr.uiOnSurfaceMuted))
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun quickMenuActionLabelRes(id: String): Int = when (id) {
        Prefs.ACTION_OPEN_APP -> R.string.quick_app
        Prefs.ACTION_OPEN_SETTINGS -> R.string.quick_settings
        Prefs.ACTION_TOGGLE_FLASH -> R.string.quick_flash
        Prefs.ACTION_TOGGLE_REPLACE -> R.string.quick_replace
        Prefs.ACTION_TOGGLE_NOFOCUS -> R.string.quick_nofocus
        Prefs.ACTION_TOGGLE_TOUCHKB -> R.string.quick_touchkb
        Prefs.ACTION_TOGGLE_LOWSPEC -> R.string.quick_lowspec
        Prefs.ACTION_CYCLE_BADGE_SIZE -> R.string.quick_badge_size
        else -> R.string.quick_badge // hide_badge 및 방어적 기본값
    }

    /** 선택된 항목은 순서대로 [selectedContainer]에, 나머지는 액션 풀 순서로 [addableContainer]에. */
    private fun rebuildQuickMenuOrderRows() {
        selectedContainer.removeAllViews()
        addableContainer.removeAllViews()
        val selected = prefs.quickMenuOrder
        selected.forEachIndexed { index, id ->
            selectedContainer.addView(selectedItemRow(id, index, selected.size))
        }
        Prefs.QUICK_MENU_ACTION_IDS.filter { it !in selected }.forEach { id ->
            addableContainer.addView(addableItemRow(id))
        }
    }

    /**
     * 한영타 예외 단어 편집기: 입력칸 + 추가 버튼, 아래에 단어마다 빼기 버튼. 저장은
     * [Prefs.addTypoException]/[Prefs.removeTypoException] — 서비스는 선택 때마다 prefs 를 읽으므로
     * 바로 반영된다. 퀵메뉴 편집과 같은 이유로 스위치가 아니라 클릭 리스너만 쓴다(프로그래매틱 되돌림 없음).
     */
    private fun typoExceptionEditor(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val list = LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.VERTICAL }
        fun rebuild() {
            list.removeAllViews()
            val words = prefs.typoExceptionWords.sorted()
            if (words.isEmpty()) {
                list.addView(TextView(this@SettingsActivity).apply {
                    text = getString(R.string.settings_replace_exceptions_empty)
                    textSize = 13f
                    setTextColor(themeColor(R.attr.uiOnSurfaceMuted))
                    setPadding(0, dp(6), 0, dp(6))
                })
            }
            for (w in words) {
                list.addView(LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(44)
                    addView(TextView(this@SettingsActivity).apply {
                        text = w
                        textSize = 14f
                        setTextColor(themeColor(R.attr.uiOnSurface))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                            .also { it.weight = 1f }
                    })
                    addView(ImageButton(this@SettingsActivity).apply {
                        setImageResource(R.drawable.ic_remove)
                        contentDescription = getString(R.string.settings_replace_exceptions_remove, w)
                        background = rippleCircleBackground()
                        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                        setOnClickListener {
                            prefs.removeTypoException(w)
                            markSaved(); refreshRailSummaries(); rebuild()
                        }
                    })
                })
            }
        }
        val input = EditText(this@SettingsActivity).apply {
            hint = getString(R.string.settings_replace_exceptions_hint)
            filters = arrayOf(InputFilter.LengthFilter(40))
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            textSize = 14f
            setTextColor(themeColor(R.attr.uiOnSurface))
            setHintTextColor(themeColor(R.attr.uiOnSurfaceMuted))
            tintForCustomPalette(this)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.weight = 1f }
        }
        fun commit() {
            val raw = input.text.toString()
            if (raw.isBlank()) return
            when {
                Prefs.normalizeTypoException(raw) == null -> toastMsg(getString(R.string.settings_replace_exceptions_invalid))
                prefs.addTypoException(raw) == null ->
                    toastMsg(getString(R.string.settings_replace_exceptions_full, Prefs.MAX_TYPO_EXCEPTIONS))
                else -> {
                    input.setText("")
                    markSaved(); refreshRailSummaries(); rebuild()
                }
            }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { commit(); true } else false
        }
        addView(LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(input)
            addView(ImageButton(this@SettingsActivity).apply {
                setImageResource(R.drawable.ic_add)
                contentDescription = getString(R.string.settings_replace_exceptions_add)
                background = rippleCircleBackground()
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).also { it.marginStart = dp(6) }
                setOnClickListener { commit() }
            })
        })
        addView(list)
        rebuild()
    }

    /** "선택됨" 행: 순번 + 라벨 + 빼기 버튼(마지막 1개면 비활성) + 드래그 손잡이. `tag` 에 id 보관. */
    private fun selectedItemRow(id: String, index: Int, total: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ROW_HEIGHT_DP)
            setPadding(0, dp(4), 0, dp(4))
            tag = id
        }
        row.addView(TextView(this).apply {
            text = "${index + 1}. ${getString(quickMenuActionLabelRes(id))}"
            textSize = 14f
            setTextColor(themeColor(R.attr.uiOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f }
        })
        val canRemove = total > 1
        row.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_remove)
            contentDescription = getString(R.string.settings_quick_menu_remove)
            background = rippleCircleBackground()
            isEnabled = canRemove
            alpha = if (canRemove) 1f else 0.3f
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener {
                // 마지막 1개일 땐 이 버튼 자체가 비활성이라 여기 도달하지 않는다 — 되돌림 로직이
                // 필요 없어(클릭 리스너는 스위치처럼 프로그래매틱 되돌림을 하지 않으므로 재귀 호출
                // 문제 자체가 없다) 이전 스위치 방식의 버그 클래스가 구조적으로 사라진다.
                val current = prefs.quickMenuOrder.toMutableList()
                current.remove(id)
                prefs.quickMenuOrder = current
                markSaved()
                rebuildQuickMenuOrderRows()
            }
        })
        row.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_drag_handle)
            contentDescription = getString(R.string.settings_quick_menu_drag_handle)
            background = rippleCircleBackground()
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).also { it.marginStart = dp(4) }
            attachDragTouch(this, row)
        })
        return row
    }

    /** "추가 가능" 행: 라벨 + 추가 버튼(5개 꽉 찼으면 토스트만, 리스트는 안 깨짐). */
    private fun addableItemRow(id: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(TextView(this).apply {
            text = getString(quickMenuActionLabelRes(id))
            textSize = 14f
            alpha = 0.75f
            setTextColor(themeColor(R.attr.uiOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f }
        })
        row.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_add)
            contentDescription = getString(R.string.settings_quick_menu_add)
            background = rippleCircleBackground()
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener {
                val current = prefs.quickMenuOrder.toMutableList()
                if (current.size >= Prefs.MAX_QUICK_MENU_ITEMS) {
                    toastMsg(getString(R.string.settings_quick_menu_limit_reached))
                    return@setOnClickListener
                }
                current.add(id)
                prefs.quickMenuOrder = current
                markSaved()
                rebuildQuickMenuOrderRows()
            }
        })
        return row
    }

    /**
     * 드래그 핸들 터치 처리(RecyclerView/ItemTouchHelper 없이 최소 의존성 원칙 유지 — [selectedContainer]
     * 는 최대 5행뿐이라 커스텀 구현의 검증 범위가 통제 가능하다). [row] 는 손가락을 그대로 따라가고
     * (`translationY`), 한 칸 이상 넘어가면 인접 행과 뷰 순서를 즉시 교체한 뒤 그 인접 행만 FLIP
     * 기법(반대 오프셋에서 0 으로 애니메이션)으로 부드럽게 자리를 넘겨준 것처럼 보이게 한다.
     */
    private fun attachDragTouch(handle: View, row: View) {
        var startRawY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 이 카드는 화면 전체 ScrollView 안에 있어, 안 막으면 세로 드래그가 스크롤로 먹힌다.
                    selectedContainer.parent?.requestDisallowInterceptTouchEvent(true)
                    startRawY = event.rawY
                    row.animate().scaleX(1.03f).scaleY(1.03f).alpha(0.95f).setDuration(100).start()
                    row.translationZ = dp(6).toFloat()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawY - startRawY
                    row.translationY = delta
                    val rowH = row.height.takeIf { it > 0 } ?: dp(ROW_HEIGHT_DP)
                    val index = selectedContainer.indexOfChild(row)
                    if (delta > rowH / 2f && index < selectedContainer.childCount - 1) {
                        swapWithNeighbor(row, selectedContainer.getChildAt(index + 1), neighborMovesUp = true)
                        startRawY += rowH
                    } else if (delta < -rowH / 2f && index > 0) {
                        swapWithNeighbor(row, selectedContainer.getChildAt(index - 1), neighborMovesUp = false)
                        startRawY -= rowH
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    selectedContainer.parent?.requestDisallowInterceptTouchEvent(false)
                    row.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                        .translationZ(0f).setDuration(150)
                        .withEndAction { commitSelectedOrder() }
                        .start()
                    true
                }
                else -> false
            }
        }
    }

    /** [neighbor] 를 [draggingRow] 가 있던 자리로 뷰 순서상 즉시 옮기고, FLIP 애니메이션으로 보정. */
    private fun swapWithNeighbor(draggingRow: View, neighbor: View, neighborMovesUp: Boolean) {
        val rowH = draggingRow.height.takeIf { it > 0 } ?: dp(ROW_HEIGHT_DP)
        val draggingIndex = selectedContainer.indexOfChild(draggingRow)
        selectedContainer.removeView(neighbor)
        selectedContainer.addView(neighbor, draggingIndex)
        // neighbor 는 방금 뷰 계층상 순간이동했으므로, 이전 화면 위치에서 지금 자리로 온 것처럼
        // 반대 오프셋에서 시작해 0으로 애니메이션한다(끊김 없는 자리 교대로 보임).
        neighbor.translationY = if (neighborMovesUp) rowH.toFloat() else -rowH.toFloat()
        neighbor.animate().translationY(0f).setDuration(150).start()
    }

    /** 드래그 종료(애니메이션이 0으로 완전히 정착한 뒤) 시 실제 뷰 순서를 prefs 에 반영. */
    private fun commitSelectedOrder() {
        val order = (0 until selectedContainer.childCount).map { selectedContainer.getChildAt(it).tag as String }
        prefs.quickMenuOrder = order
        markSaved()
        // 순번 텍스트("1. 2. 3...")를 정확히 다시 매긴다 — 이 시점엔 모든 translationY 가 0 이라
        // 재생성해도 시각적 점프가 없다.
        rebuildQuickMenuOrderRows()
    }

    private fun toastMsg(msg: String) {
        runCatching { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun applyPreview(view: View, hex: String, opacityPct: Int) {
        view.background = swatchDrawable(hex, opacityPct)
    }

    /**
     * 색 미리보기 원. [opacityPct] 가 100 미만이면 **밝음→어두움 그라데이션 바탕 위에** 반투명
     * 색을 얹어, 그 바탕이 비쳐 보이는 정도로 불투명도를 눈으로 확인할 수 있게 한다.
     * 카드 표면이 흰색이라 그냥 반투명 색만 칠하면 85%와 100%가 거의 구분되지 않는다.
     *
     * ⚠️ 테두리는 테마의 `uiDivider`(항상 옅은 회색)가 아니라 **이 색 자체의 밝기와 대비되는
     * 흑/백**([ColorMath.readableOn])을 쓴다(사용자 제보, 2026-09) — 사용자 지정 테마 편집기의
     * 배경/카드 견본([THEME_BG_SWATCHES], 흰색·검정 포함)이 옅은 회색 테두리로는 같은 톤의 카드
     * 배경과 구분이 안 돼 스와치가 통째로 안 보였다. 32색 팔레트([PALETTE])에도 흰색(#FFFFFF)·
     * 검정(#000000)이 있어 같은 문제가 있었다 — 색 자체와 대비시키면 어떤 배경 위에서도 보인다.
     */
    private fun swatchDrawable(hex: String, opacityPct: Int = 100): android.graphics.drawable.Drawable {
        val rgb = runCatching { Color.parseColor(hex) }.getOrDefault(Color.GRAY)
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                Color.argb(
                    Prefs.alphaFromPercent(opacityPct), Color.red(rgb), Color.green(rgb), Color.blue(rgb)
                )
            )
            setStroke(dp(1), ColorMath.readableOn(rgb))
        }
        if (opacityPct >= 100) return fill
        val base = GradientDrawable(
            GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFFFFFF.toInt(), 0xFF3A3F4A.toInt())
        ).apply { shape = GradientDrawable.OVAL }
        return android.graphics.drawable.LayerDrawable(arrayOf(base, fill))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).roundToInt()

    companion object {
        /** 퀵메뉴 "선택됨" 행의 고정 높이(dp) — 드래그 자리교체 판정/애니메이션 오프셋 기준. */
        private const val ROW_HEIGHT_DP = 52

        /**
         * 이 폭(dp) 이상이면 목록과 상세를 나란히 보여준다.
         * 주 타겟인 Galaxy Tab S6 Lite 는 세로에서도 약 857dp 라 2단으로 열린다 — 한 줄짜리 카드가
         * 세로로만 쌓이면 가로 공간을 통째로 버리면서 스크롤만 길어진다.
         */
        private const val TWO_PANE_MIN_WIDTH_DP = 720

        /** 2단에 필요한 최소 높이 — 폰을 눕혔을 때(높이 약 393dp) 2단이 뜨는 것을 막는다. */
        private const val TWO_PANE_MIN_HEIGHT_DP = 480

        /** 2단일 때 좌측 레일 폭. [detailContentWidthDp] 계산과 같은 값을 써야 한다. */
        private const val RAIL_WIDTH_DP = 300

        /** 상세 영역 좌우 패딩 / [sectionCard] 좌우 패딩 — 반응형 폭 계산의 입력. */
        private const val DETAIL_PADDING_DP = 18
        private const val CARD_PADDING_DP = 18

        /** 팔레트 스와치 한 칸이 차지하는 폭(32dp + 좌우 여백 3dp씩). */
        private const val SWATCH_CELL_DP = 38

        /** 테마 카드를 2열로 둘 수 있는 최소 카드 안쪽 폭. 그 아래면 1열. */
        private const val THEME_TWO_COLUMN_MIN_DP = 300

        /** [colorEditor] 의 라벨 최소 폭 — "한국어"(3자) 기준, "영어"(2자)와 나란히 정렬되게. */
        private const val LANGUAGE_LABEL_MIN_WIDTH_DP = 52

        // 재생성(테마 변경·회전) 너머로 살려야 하는 화면 상태.
        private const val STATE_GROUP = "state_group"
        private const val STATE_QUERY = "state_query"
        private const val STATE_IN_DETAIL = "state_in_detail"
        private const val STATE_OPEN_PALETTES = "state_open_palettes"

        // 설정 그룹 id — 기능 하나를 사용자가 찾는 이름 하나로 묶는 단위.
        private const val GROUP_FLASH = "flash"
        private const val GROUP_BADGE = "badge"
        private const val GROUP_MENU = "menu"
        private const val GROUP_REPLACE = "replace"
        private const val GROUP_NOFOCUS = "nofocus"
        private const val GROUP_KEYBOARD = "keyboard"
        private const val GROUP_DIAG = "diag"
        private const val GROUP_THEME = "theme"

        /**
         * "이사 갔어요" 안내 — 예전 배치에서 이 자리에 있던 설정이 어디로 갔는지.
         * 마커라서 [Prefs.shouldShowMarker] 규칙으로 저절로 만료된다(지우는 작업 불필요).
         */
        private val MOVED_NOTICES = listOf(
            MovedNotice(
                Prefs.MARKER_MOVED_DIAG_PAUSE, GROUP_KEYBOARD, GROUP_DIAG,
                R.string.settings_diagnostic_pause_with_touch_kb, R.string.settings_group_diag
            ),
            MovedNotice(
                Prefs.MARKER_MOVED_QUICK_MENU, GROUP_BADGE, GROUP_MENU,
                R.string.settings_quick_menu_order, R.string.settings_group_menu
            )
        )

        /** 32색 팔레트. */
        /** 테마 배경·카드 견본 — 옅은 무채/종이/파스텔 + 어두운 바탕(오버레이용 [PALETTE] 는 너무 선명하다). */
        private val THEME_BG_SWATCHES = listOf(
            "#FFFFFF", "#F4F6FA", "#F3EDE0", "#FBF8F1", "#EEF3EC", "#F1ECF7", "#FDF1F3", "#EAF4F8",
            "#E9E9E9", "#D9DEE7", "#1A1D23", "#0F1115", "#111827", "#110E1E", "#10231C", "#2A1E17",
            "#0B1B2B", "#000000"
        )

        /** 테마 글자 견본 — 짙은 잉크 계열 + 밝은 글자 계열. */
        private val THEME_TEXT_SWATCHES = listOf(
            "#111317", "#1A1C20", "#2B241C", "#1F2937", "#0F172A", "#3B2F2F",
            "#FFFFFF", "#ECEAF7", "#E6E8EC", "#F5F0E6", "#D1D5DB", "#FFE9A8"
        )

        private val PALETTE = listOf(
            "#000000", "#444444", "#888888", "#CCCCCC", "#FFFFFF",
            "#CC2D2D", "#E53935", "#FF6F61", "#FF1744",
            "#1A6EBD", "#1565C0", "#2196F3", "#00B0FF",
            "#2D8C4E", "#2E7D32", "#43A047", "#00C853",
            "#F9A825", "#FBC02D", "#FF9800", "#FF6D00",
            "#6A1B9A", "#8E24AA", "#AB47BC", "#D500F9",
            "#00838F", "#0097A7", "#26C6DA",
            "#5D4037", "#795548",
            "#AD1457", "#EC407A"
        )
    }
}
