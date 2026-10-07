package com.langsense.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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
 * 관문(2026-10): 세 단계를 마치기 전엔 설정으로 넘어갈 수 없고(배터리는 "나중에"로 넘길 수 있음), 마쳤으면 앱을 열 때 바로
 * 설정 화면으로 간다. 설정의 "설정 안내 다시 보기"로 열면([EXTRA_REVIEW]) 넘기지 않는다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * 이 화면에 실제로 적용한 테마([ThemeManager.signature] — 사용자 지정이면 4색 포함). 설정에서
     * 테마나 사용자 지정 색을 바꾸고 돌아오면 [onResume] 에서 이 값과 비교해 다시 그린다 — 팔레트
     * 변경은 시스템 구성 변경이 아니라 앱 내부 상태 변경이라 백스택에 멈춰 있던 이 화면은 저절로
     * 갱신되지 않는다.
     */
    private var appliedTheme: String = Prefs.THEME_SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⚠️ setTheme 은 super.onCreate 보다 먼저(ThemeManager.apply 문서 참조).
        val themeId = Prefs(this).uiTheme
        appliedTheme = ThemeManager.signature(this, themeId)
        ThemeManager.apply(this, themeId)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 레이아웃의 역할 태그(fg=/bg=)대로 색을 입힌다 — 사용자 지정 테마의 색은 스타일이 아니라
        // 런타임 팔레트에 있어 XML 의 ?attr 만으로는 닿지 않는다(UiDrawables 참조).
        UiDrawables.bindTags(binding.root)
        ThemeManager.applyWindow(this)
        capContentWidth()
        showVersion()

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

        binding.btnBatteryLater.setOnClickListener {
            Prefs(this).batteryStepSkipped = true
            refreshStatus()
        }

        binding.btnSettings.setOnClickListener { openSettings() }
    }

    private val reviewMode: Boolean get() = intent.getBooleanExtra(EXTRA_REVIEW, false)

    /** 다시 보기로 왔으면 뒤에 있는 설정 화면으로 돌아가고, 아니면 설정을 열고 이 화면은 닫는다(뒤로가기로 다시 오지 않게). */
    private fun openSettings() {
        if (!reviewMode) startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        // 설정에서 테마를 바꾸고 돌아온 경우: 이 화면은 아직 이전 팔레트라 다시 만든다.
        // (라이트/다크만 바뀐 경우는 AppCompat 이 이미 재생성해 주므로 여기서 값이 같아 통과한다.)
        if (appliedTheme != ThemeManager.signature(this, Prefs(this).uiTheme)) {
            recreate()
            return
        }
        if (refreshStatus() && !reviewMode) openSettings()
    }

    /**
     * 넓은 화면에서는 본문 폭을 [R.dimen.content_max_width] 로 묶고 가운데로 모은다.
     * 태블릿 가로(1338dp)에서 한 줄이 화면 끝까지 늘어나면 시선 이동이 커져 읽기 불편하다.
     * 폰·폴드 접힘처럼 그보다 좁은 화면에서는 조건에 걸리지 않아 `match_parent` 그대로다.
     */
    private fun capContentWidth() {
        val maxWidth = resources.getDimensionPixelSize(R.dimen.content_max_width)
        val screenWidth = resources.displayMetrics.widthPixels
        if (screenWidth <= maxWidth) return
        binding.contentColumn.layoutParams =
            (binding.contentColumn.layoutParams as FrameLayout.LayoutParams).apply {
                width = maxWidth
                gravity = Gravity.CENTER_HORIZONTAL
            }
    }

    /**
     * "26.9.19.04" 처럼 마지막 커밋 날짜 + 그날 몇 번째 커밋인지로 자동 계산되는 버전(app/build.gradle.kts 참조).
     * `BuildConfig` 를 켜지 않고(빌드 산출물 증가 방지) `PackageManager` 로 읽는다.
     */
    private fun showVersion() {
        val name = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull()
        binding.tvVersion.text = getString(R.string.settings_version_format, name ?: "?")
    }

    /** 상태 표시를 갱신하고, 설정으로 넘어가도 되는지(관문 통과) 돌려준다. */
    private fun refreshStatus(): Boolean {
        val overlayOk = PermissionHelper.canDrawOverlays(this)
        applyStatusPill(binding.tvOverlayStatus, overlayOk)

        val accOk = PermissionHelper.isAccessibilityServiceEnabled(this) ||
            PermissionHelper.isAccessibilityEnabledViaSecure(this)
        applyStatusPill(binding.tvAccStatus, accOk)

        val batteryOk = PermissionHelper.isIgnoringBatteryOptimizations(this)
        applyStatusPill(binding.tvBatteryStatus, batteryOk)

        val skipped = Prefs(this).batteryStepSkipped
        binding.btnBatteryLater.visibility = if (batteryOk || skipped) View.GONE else View.VISIBLE

        val allSet = overlayOk && accOk && (batteryOk || skipped)
        binding.tvSummary.text =
            getString(if (allSet) R.string.summary_all_set else R.string.summary_incomplete)
        tintPill(binding.tvSummary, allSet)
        binding.btnSettings.isEnabled = allSet
        binding.btnSettings.alpha = if (allSet) 1f else 0.4f
        binding.btnSettings.setText(if (allSet) R.string.btn_settings else R.string.btn_settings_locked)
        return allSet
    }

    /** 상태 필: 텍스트("완료됨"/"필요함") + 상태색 글씨/컨테이너 배경. */
    private fun applyStatusPill(tv: TextView, ok: Boolean) {
        tv.text = getString(if (ok) R.string.status_granted else R.string.status_needed)
        tintPill(tv, ok)
    }

    private fun tintPill(tv: TextView, ok: Boolean) {
        tv.setTextColor(themeColor(if (ok) R.attr.statusOk else R.attr.statusNeed))
        // backgroundTint 를 쓰면 테두리까지 채움색으로 덮여 고대비에서 필이 사라진다(bg_pill.xml).
        tv.background = UiDrawables.pill(
            this, themeColor(if (ok) R.attr.statusOkContainer else R.attr.statusNeedContainer)
        )
    }

    companion object {
        /** 설정의 "설정 안내 다시 보기"에서 열 때 — 관문을 통과해도 설정으로 자동으로 넘기지 않는다. */
        const val EXTRA_REVIEW = "review"
    }

    private fun startActivitySafely(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
}
