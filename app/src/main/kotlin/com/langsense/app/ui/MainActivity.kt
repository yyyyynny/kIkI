package com.langsense.app.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.langsense.app.R
import com.langsense.app.databinding.ActivityMainBinding
import com.langsense.app.util.PermissionHelper
import com.langsense.app.util.Prefs
import com.langsense.app.util.ThemeManager
import com.langsense.app.util.themeColor

/**
 * 온보딩 화면.
 * 3단계 권한/활성화 흐름을 안내하고, onResume 마다 상태를 갱신한다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * 이 화면에 실제로 적용한 테마. 설정에서 테마를 바꾸고 돌아오면 [onResume] 에서 이 값과
     * 비교해 다시 그린다 — 팔레트 변경은 시스템 구성 변경이 아니라 앱 내부 상태 변경이라
     * 백스택에 멈춰 있던 이 화면은 저절로 갱신되지 않는다.
     */
    private var appliedTheme: String = Prefs.THEME_SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⚠️ setTheme 은 super.onCreate 보다 먼저(ThemeManager.apply 문서 참조).
        appliedTheme = Prefs(this).uiTheme
        ThemeManager.apply(this, appliedTheme)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOverlay.setOnClickListener {
            if (PermissionHelper.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.status_granted, Toast.LENGTH_SHORT).show()
            } else {
                startActivitySafely(PermissionHelper.overlaySettingsIntent(this))
            }
        }

        binding.btnAccessibility.setOnClickListener {
            startActivitySafely(PermissionHelper.accessibilitySettingsIntent())
        }

        binding.btnBattery.setOnClickListener {
            startActivitySafely(PermissionHelper.batteryOptimizationSettingsIntent())
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 설정에서 테마를 바꾸고 돌아온 경우: 이 화면은 아직 이전 팔레트라 다시 만든다.
        // (라이트/다크만 바뀐 경우는 AppCompat 이 이미 재생성해 주므로 여기서 값이 같아 통과한다.)
        if (appliedTheme != Prefs(this).uiTheme) {
            recreate()
            return
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        val overlayOk = PermissionHelper.canDrawOverlays(this)
        applyStatusPill(binding.tvOverlayStatus, overlayOk)

        val accOk = PermissionHelper.isAccessibilityServiceEnabled(this) ||
            PermissionHelper.isAccessibilityEnabledViaSecure(this)
        applyStatusPill(binding.tvAccStatus, accOk)

        val batteryOk = PermissionHelper.isIgnoringBatteryOptimizations(this)
        applyStatusPill(binding.tvBatteryStatus, batteryOk)

        // 상단 요약 배너: 오버레이·접근성 두 필수 권한이 모두 켜지면 "완료". 배터리는 권장이라 제외.
        val allSet = overlayOk && accOk
        binding.tvSummary.text =
            getString(if (allSet) R.string.summary_all_set else R.string.summary_incomplete)
        tintPill(binding.tvSummary, allSet)
    }

    /** 상태 필: 텍스트("완료됨"/"필요함") + 상태색 글씨/컨테이너 배경. */
    private fun applyStatusPill(tv: TextView, ok: Boolean) {
        tv.text = getString(if (ok) R.string.status_granted else R.string.status_needed)
        tintPill(tv, ok)
    }

    private fun tintPill(tv: TextView, ok: Boolean) {
        tv.setTextColor(themeColor(if (ok) R.attr.statusOk else R.attr.statusNeed))
        tv.backgroundTintList = ColorStateList.valueOf(
            themeColor(if (ok) R.attr.statusOkContainer else R.attr.statusNeedContainer)
        )
    }

    private fun startActivitySafely(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
}
