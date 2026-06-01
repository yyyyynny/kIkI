package com.langsense.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.langsense.app.R
import com.langsense.app.util.ImeLocaleParser
import com.langsense.app.util.Prefs
import kotlin.math.roundToInt

/**
 * 설정 화면 (프로그래매틱 UI, 성능 위주의 단순 구성).
 *
 * - 지원 언어 체크박스(기본 3개 ON)
 * - 언어별 플래시 색상: #RRGGBB 직접 입력 + 32색 팔레트
 * - 깜박임 속도(100~500ms) / 횟수(1~5) 슬라이더
 * - 배지/포커스경고/한영타 토글 및 임계값
 *
 * 모든 변경은 즉시 SharedPreferences 에 반영되어 서비스에 적용된다.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var savedHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }

        savedHint = TextView(this).apply {
            text = getString(R.string.settings_apply)
            setTextColor(0xFF888888.toInt())
            textSize = 12f
        }
        root.addView(savedHint)

        // --- 지원 언어 ---
        root.addView(sectionHeader(getString(R.string.settings_languages)))
        root.addView(langCheckbox(ImeLocaleParser.KO, R.string.settings_lang_ko))
        root.addView(langCheckbox(ImeLocaleParser.EN, R.string.settings_lang_en))
        // [일본어 비활성화] 일본어 체크박스 주석(추후 재도입 위해 보존).
        // root.addView(langCheckbox(ImeLocaleParser.JA, R.string.settings_lang_ja))

        // --- 전환 플래시 ---
        root.addView(sectionHeader(getString(R.string.settings_flash)))
        root.addView(switchRow(getString(R.string.settings_flash_enabled), prefs.flashEnabled) {
            prefs.flashEnabled = it; markSaved()
        })
        // 깜박임 속도 100~500ms
        root.addView(
            sliderRow(
                label = getString(R.string.settings_flash_duration),
                min = 100, max = 500, step = 50, value = prefs.flashDurationMs, suffix = "ms"
            ) { prefs.flashDurationMs = it; markSaved() }
        )
        // 깜박임 횟수 1~5
        root.addView(
            sliderRow(
                label = getString(R.string.settings_flash_count),
                min = 1, max = 5, step = 1, value = prefs.flashCount, suffix = "회"
            ) { prefs.flashCount = it; markSaved() }
        )

        // --- 언어별 플래시 색상 ---
        root.addView(sectionHeader(getString(R.string.settings_flash_colors)))
        root.addView(colorEditor(ImeLocaleParser.KO, getString(R.string.settings_lang_ko)))
        root.addView(colorEditor(ImeLocaleParser.EN, getString(R.string.settings_lang_en)))
        // [일본어 비활성화] 일본어 색상 편집기 주석(추후 재도입 위해 보존).
        // root.addView(colorEditor(ImeLocaleParser.JA, getString(R.string.settings_lang_ja)))

        // --- 상시 배지 ---
        root.addView(sectionHeader(getString(R.string.settings_badge)))
        root.addView(switchRow(getString(R.string.settings_badge_enabled), prefs.badgeEnabled) {
            prefs.badgeEnabled = it; markSaved()
        })
        // [5] 크기 3단계(소/중/대)
        root.addView(badgeSizeRow())
        // [5] 배경색/글씨색 — 플래시 색상과 동일한 공용 색 선택 컴포넌트 재사용
        root.addView(colorPickerRow(getString(R.string.settings_badge_bg_color), prefs.badgeBgColorHex) {
            prefs.badgeBgColorHex = it
        })
        root.addView(colorPickerRow(getString(R.string.settings_badge_text_color), prefs.badgeTextColorHex) {
            prefs.badgeTextColorHex = it
        })

        // --- 포커스 없는 키 입력 경고 ---
        root.addView(sectionHeader(getString(R.string.settings_nofocus)))
        root.addView(descRow(getString(R.string.settings_nofocus_desc))) // [3] 기능 설명
        root.addView(switchRow(getString(R.string.settings_nofocus_enabled), prefs.noFocusEnabled) {
            prefs.noFocusEnabled = it; markSaved()
        })
        root.addView(
            sliderRow(
                label = getString(R.string.settings_nofocus_threshold),
                min = 1, max = 5, step = 1, value = prefs.noFocusThreshold, suffix = "회"
            ) { prefs.noFocusThreshold = it; markSaved() }
        )

        // --- 한영타 교체 ---
        root.addView(sectionHeader(getString(R.string.settings_replace)))
        root.addView(switchRow(getString(R.string.settings_replace_enabled), prefs.replaceEnabled) {
            prefs.replaceEnabled = it; markSaved()
        })
        root.addView(
            sliderRow(
                label = getString(R.string.settings_replace_confidence),
                min = 50, max = 90, step = 5, value = prefs.replaceConfidence, suffix = "%"
            ) { prefs.replaceConfidence = it; markSaved() }
        )
        root.addView(descRow(getString(R.string.settings_replace_confidence_desc))) // [4] 신뢰도 설명

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun markSaved() {
        savedHint.text = getString(R.string.settings_saved)
    }

    // ---------------------------------------------------------------------
    // UI 빌더
    // ---------------------------------------------------------------------

    private fun sectionHeader(title: String): TextView = TextView(this).apply {
        text = title
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    /** 항목 아래 붙는 짧은 회색 설명 문구(기능/수치 해설). */
    private fun descRow(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(0xFF888888.toInt())
        setPadding(0, dp(2), 0, dp(4))
    }

    private fun langCheckbox(lang: String, labelRes: Int): CheckBox = CheckBox(this).apply {
        setText(labelRes)
        isChecked = prefs.isLangEnabled(lang)
        setOnCheckedChangeListener { _, checked ->
            prefs.setLangEnabled(lang, checked); markSaved()
        }
    }

    private fun switchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): CheckBox =
        CheckBox(this).apply {
            text = label
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }

    /**
     * 정수 슬라이더 행. SeekBar 는 0..((max-min)/step) 범위로 매핑.
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
        val valueLabel = TextView(this).apply {
            text = "$label: $value$suffix"
            textSize = 14f
        }
        val steps = (max - min) / step
        val seek = SeekBar(this).apply {
            this.max = steps
            progress = ((value - min) / step).coerceIn(0, steps)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = min + p * step
                    valueLabel.text = "$label: $v$suffix"
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    onChange(min + (sb?.progress ?: 0) * step)
                }
            })
        }
        container.addView(valueLabel)
        container.addView(seek)
        return container
    }

    /** 언어별 플래시 색상 편집기 — 공용 [colorPickerRow] 를 prefs.colorHex 에 연결. */
    private fun colorEditor(lang: String, langLabel: String): View =
        colorPickerRow(langLabel, prefs.colorHex(lang)) { hex -> prefs.setColorHex(lang, hex) }

    /**
     * 공용 색 선택 컴포넌트: 라벨 + 미리보기 + #RRGGBB 입력 + 32색 팔레트.
     * 플래시 색/배지 색 설정이 동일 코드를 공유한다(복붙 방지). 선택/입력 시 [onPicked] 에
     * 정규화된 "#RRGGBB" 를 넘기고 저장 안내를 표시한다.
     */
    private fun colorPickerRow(
        label: String,
        initialHex: String,
        onPicked: (String) -> Unit
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(4))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }
        val hexInput = EditText(this).apply {
            setText(initialHex)
            filters = arrayOf(InputFilter.LengthFilter(7))
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = getString(R.string.settings_color_hex)
            layoutParams = LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.marginStart = dp(12) }
        }
        applyPreview(preview, initialHex)

        fun commit(raw: String) {
            val normalized = Prefs.normalizeHex(raw)
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
            layoutParams = LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        headerRow.addView(preview)
        headerRow.addView(hexInput)
        container.addView(headerRow)

        // 32색 팔레트 (8 x 4)
        val palette = GridLayout(this).apply {
            columnCount = 8
            setPadding(0, dp(6), 0, 0)
        }
        PALETTE.forEach { hex ->
            val swatch = View(this).apply {
                val lp = GridLayout.LayoutParams().apply {
                    width = dp(30); height = dp(30)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
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

    private fun applyPreview(view: View, hex: String) {
        view.background = swatchDrawable(hex)
    }

    private fun swatchDrawable(hex: String): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(4).toFloat()
        setColor(runCatching { Color.parseColor(hex) }.getOrDefault(Color.GRAY))
        setStroke(dp(1), 0x55FFFFFF)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).roundToInt()

    companion object {
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
