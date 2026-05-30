package com.langsense.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.langsense.app.R
import kotlin.math.roundToInt

/**
 * "교체?" 미니 버튼 (Feature 4). 화면 상단에 떠서 탭 한 번으로 한영타를 교체.
 * 본체만 클릭 가능하며 2초 후 자동 소멸한다(소멸은 [OverlayManager] 가 관리).
 */
@SuppressLint("ViewConstructor")
class ReplaceChipView(context: Context) : TextView(context) {

    init {
        setTextColor(ContextCompat.getColor(context, R.color.chip_text))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setBackgroundResource(R.drawable.bg_chip)
        maxWidth = dp(280f)
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        isClickable = true
        isFocusable = false
        elevation = dp(4f).toFloat()
    }

    /** @param preview 변환 미리보기, @param onTap 탭 시 실행할 교체 액션 */
    fun bind(preview: String, onTap: () -> Unit) {
        text = context.getString(R.string.chip_replace_format, preview)
        setOnClickListener { onTap() }
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    ).roundToInt()
}
