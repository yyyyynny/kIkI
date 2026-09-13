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
import android.widget.GridLayout
import android.widget.ImageButton
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
import com.langsense.app.util.ImeLocaleParser
import com.langsense.app.util.Prefs
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
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> syncToggles() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(28))
        }

        savedHint = TextView(this).apply {
            text = getString(R.string.settings_apply)
            setTextColor(getColor(R.color.ui_on_surface_muted))
            textSize = 12f
            setPadding(dp(2), 0, 0, dp(4))
        }
        content.addView(savedHint)

        // --- 지원 언어 ---
        content.addView(sectionCard(getString(R.string.settings_languages)).apply {
            addView(langSwitch(ImeLocaleParser.KO, R.string.settings_lang_ko))
            addView(langSwitch(ImeLocaleParser.EN, R.string.settings_lang_en))
            // [일본어 비활성화] 일본어 토글 주석(추후 재도입 위해 보존).
            // addView(langSwitch(ImeLocaleParser.JA, R.string.settings_lang_ja))
        })

        // --- 터치 키보드 제외 (추가 기능 2) ---
        content.addView(sectionCard(getString(R.string.settings_exclude_touch_kb)).apply {
            addView(descRow(getString(R.string.settings_exclude_touch_kb_desc)))
            addView(switchRow(getString(R.string.settings_exclude_touch_kb_enabled), prefs.excludeTouchKeyboard) {
                prefs.excludeTouchKeyboard = it; markSaved()
            })
        })

        // --- 전환 플래시 ---
        content.addView(sectionCard(getString(R.string.settings_flash)).apply {
            addView(boundSwitchRow(getString(R.string.settings_flash_enabled), { prefs.flashEnabled }) {
                prefs.flashEnabled = it; markSaved()
            })
            // 깜박임 속도 100~500ms
            addView(
                sliderRow(
                    label = getString(R.string.settings_flash_duration),
                    min = 100, max = 500, step = 50, value = prefs.flashDurationMs, suffix = "ms"
                ) { prefs.flashDurationMs = it; markSaved() }
            )
            // 깜박임 횟수 1~5
            addView(
                sliderRow(
                    label = getString(R.string.settings_flash_count),
                    min = 1, max = 5, step = 1, value = prefs.flashCount, suffix = "회"
                ) { prefs.flashCount = it; markSaved() }
            )
        })

        // --- 언어별 플래시 색상 ---
        content.addView(sectionCard(getString(R.string.settings_flash_colors)).apply {
            addView(colorEditor(ImeLocaleParser.KO, getString(R.string.settings_lang_ko)))
            addView(colorEditor(ImeLocaleParser.EN, getString(R.string.settings_lang_en)))
            // [일본어 비활성화] 일본어 색상 편집기 주석(추후 재도입 위해 보존).
            // addView(colorEditor(ImeLocaleParser.JA, getString(R.string.settings_lang_ja)))
        })

        // --- 상시 배지 ---
        content.addView(sectionCard(getString(R.string.settings_badge)).apply {
            addView(boundSwitchRow(getString(R.string.settings_badge_enabled), { prefs.badgeEnabled }) {
                prefs.badgeEnabled = it; markSaved()
            })
            // [5] 크기 3단계(소/중/대)
            addView(badgeSizeRow())
            // [5] 배경색/글씨색 — 플래시 색상과 동일한 공용 색 선택 컴포넌트 재사용
            addView(colorPickerRow(getString(R.string.settings_badge_bg_color), prefs.badgeBgColorHex) {
                prefs.badgeBgColorHex = it
            })
            addView(colorPickerRow(getString(R.string.settings_badge_text_color), prefs.badgeTextColorHex) {
                prefs.badgeTextColorHex = it
            })
            addView(descRow(getString(R.string.settings_badge_long_press_desc)))
        })

        // --- 플로팅 메뉴(배지 탭) ---
        content.addView(sectionCard(getString(R.string.settings_radial)).apply {
            addView(descRow(getString(R.string.settings_radial_reduce_motion_desc)))
            addView(switchRow(getString(R.string.settings_radial_reduce_motion), prefs.radialReduceMotion) {
                prefs.radialReduceMotion = it; markSaved()
            })
            // 메뉴 강조색 2종 — 배지 색상과 동일한 공용 색 선택 컴포넌트 재사용.
            addView(descRow(getString(R.string.settings_radial_colors_desc)))
            addView(colorPickerRow(getString(R.string.settings_radial_accent_color), prefs.radialAccentColorHex) {
                prefs.radialAccentColorHex = it
            })
            addView(colorPickerRow(getString(R.string.settings_radial_glow_color), prefs.radialGlowColorHex) {
                prefs.radialGlowColorHex = it
            })
        })

        // --- 배지 탭 동작 ---
        content.addView(sectionCard(getString(R.string.settings_badge_tap)).apply {
            addView(descRow(getString(R.string.settings_badge_tap_desc)))
            addView(badgeTapActionRow())
            addView(descRow(getString(R.string.settings_badge_tap_hide_warning)))
        })

        // --- 퀵메뉴 항목 순서 ---
        content.addView(sectionCard(getString(R.string.settings_quick_menu_order)).apply {
            addView(descRow(getString(R.string.settings_quick_menu_order_desc)))
            addView(quickMenuOrderSection())
        })

        // --- 포커스 없는 키 입력 경고 ---
        content.addView(sectionCard(getString(R.string.settings_nofocus)).apply {
            addView(descRow(getString(R.string.settings_nofocus_desc))) // [3] 기능 설명
            addView(switchRow(getString(R.string.settings_nofocus_enabled), prefs.noFocusEnabled) {
                prefs.noFocusEnabled = it; markSaved()
            })
            addView(
                sliderRow(
                    label = getString(R.string.settings_nofocus_threshold),
                    min = 1, max = 5, step = 1, value = prefs.noFocusThreshold, suffix = "회"
                ) { prefs.noFocusThreshold = it; markSaved() }
            )
        })

        // --- 한영타 교체 ---
        content.addView(sectionCard(getString(R.string.settings_replace)).apply {
            addView(boundSwitchRow(getString(R.string.settings_replace_enabled), { prefs.replaceEnabled }) {
                prefs.replaceEnabled = it; markSaved()
            })
            addView(
                sliderRow(
                    label = getString(R.string.settings_replace_confidence),
                    min = 50, max = 90, step = 5, value = prefs.replaceConfidence, suffix = "%"
                ) { prefs.replaceConfidence = it; markSaved() }
            )
            addView(descRow(getString(R.string.settings_replace_confidence_desc))) // [4] 신뢰도 설명
        })

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0
            ).also { it.weight = 1f }
        }

        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar())
            addView(scroll)
        }
        setContentView(screen)
    }

    /** 커스텀 톱바: 뒤로가기 + 화면 제목 (NoActionBar 테마 대응). */
    private fun topBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(20), dp(6))
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            contentDescription = getString(R.string.settings_back)
            background = rippleCircleBackground()
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 20f
            setTextColor(getColor(R.color.ui_on_surface))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f; it.marginStart = dp(8) }
        }
        bar.addView(back)
        bar.addView(title)
        return bar
    }

    /** 테마의 원형(borderless) 리플 배경 — 톱바 뒤로가기 버튼용. */
    private fun rippleCircleBackground(): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        return ContextCompat.getDrawable(this, tv.resourceId)
    }

    override fun onResume() {
        super.onResume()
        // 외부(래디얼 메뉴)에서 바뀐 토글 값을 현재 prefs 기준으로 다시 맞춘다(stale 표시 방지).
        syncToggles()
        prefs.register(prefsListener)
    }

    override fun onPause() {
        prefs.unregister(prefsListener)
        super.onPause()
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

    private fun markSaved() {
        savedHint.text = getString(R.string.settings_saved)
    }

    // ---------------------------------------------------------------------
    // UI 빌더
    // ---------------------------------------------------------------------

    /** 섹션 카드: 라운드 surface 컨테이너 + 섹션 제목. 항목들은 이 안에 addView 한다. */
    private fun sectionCard(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_card)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(12) }
        addView(TextView(this@SettingsActivity).apply {
            text = title
            textSize = 15f
            setTextColor(getColor(R.color.ui_accent))
            letterSpacing = 0.01f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })
    }

    /** 항목 아래 붙는 짧은 회색 설명 문구(기능/수치 해설). */
    private fun descRow(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        setTextColor(getColor(R.color.ui_on_surface_muted))
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
            setTextColor(getColor(R.color.ui_on_surface))
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
            setTextColor(getColor(R.color.ui_on_surface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.weight = 1f }
        })
        val valueLabel = TextView(this).apply {
            text = getString(R.string.slider_value_short_format, value, suffix)
            textSize = 14f
            setTextColor(getColor(R.color.ui_accent))
            setTypeface(typeface, Typeface.BOLD)
        }
        header.addView(valueLabel)

        val steps = (max - min) / step
        val seek = SeekBar(this).apply {
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

    /** 언어별 플래시 색상 편집기 — 공용 [colorPickerRow] 를 prefs.colorHex 에 연결. */
    private fun colorEditor(lang: String, langLabel: String): View =
        colorPickerRow(langLabel, prefs.colorHex(lang)) { hex -> prefs.setColorHex(lang, hex) }

    /**
     * 공용 색 선택 컴포넌트: 라벨 + 원형 미리보기 + #RRGGBB 입력 + 32색 원형 팔레트.
     * 플래시 색/배지 색 설정이 동일 코드를 공유한다(복붙 방지). 선택/입력 시 [onPicked] 에
     * 정규화된 "#RRGGBB" 를 넘기고 저장 안내를 표시한다.
     */
    private fun colorPickerRow(
        label: String,
        initialHex: String,
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
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = getString(R.string.settings_color_hex)
            layoutParams = LinearLayout.LayoutParams(dp(130), ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.marginStart = dp(14) }
        }
        applyPreview(preview, initialHex)

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
            applyPreview(preview, normalized)
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
            setTextColor(getColor(R.color.ui_on_surface))
            layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        headerRow.addView(preview)
        headerRow.addView(hexInput)
        container.addView(headerRow)

        // 32색 팔레트 (8 x 4, 원형 스와치)
        val palette = GridLayout(this).apply {
            columnCount = 8
            setPadding(0, dp(8), 0, 0)
        }
        PALETTE.forEach { hex ->
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
            setTextColor(getColor(R.color.ui_on_surface))
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

    private fun subsectionTitle(textRes: Int): TextView = TextView(this).apply {
        text = getString(textRes)
        textSize = 12.5f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(getColor(R.color.ui_on_surface_muted))
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
            setTextColor(getColor(R.color.ui_on_surface))
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
            setTextColor(getColor(R.color.ui_on_surface))
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

    private fun applyPreview(view: View, hex: String) {
        view.background = swatchDrawable(hex)
    }

    private fun swatchDrawable(hex: String): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(runCatching { Color.parseColor(hex) }.getOrDefault(Color.GRAY))
        setStroke(dp(1), getColor(R.color.ui_divider))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).roundToInt()

    companion object {
        /** 퀵메뉴 "선택됨" 행의 고정 높이(dp) — 드래그 자리교체 판정/애니메이션 오프셋 기준. */
        private const val ROW_HEIGHT_DP = 52

        /** 32색 팔레트. */
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
