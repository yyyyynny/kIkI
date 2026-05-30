package com.langsense.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.langsense.app.R
import com.langsense.app.databinding.ActivityMainBinding
import com.langsense.app.util.PermissionHelper

/**
 * 온보딩 화면.
 * 3단계 권한/활성화 흐름을 안내하고, onResume 마다 상태를 갱신한다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
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
        refreshStatus()
    }

    private fun refreshStatus() {
        val granted = getString(R.string.status_granted)
        val needed = getString(R.string.status_needed)
        val green = 0xFF2D8C4E.toInt()
        val red = 0xFFCC2D2D.toInt()

        val overlayOk = PermissionHelper.canDrawOverlays(this)
        binding.tvOverlayStatus.text = if (overlayOk) granted else needed
        binding.tvOverlayStatus.setTextColor(if (overlayOk) green else red)

        val accOk = PermissionHelper.isAccessibilityServiceEnabled(this) ||
            PermissionHelper.isAccessibilityEnabledViaSecure(this)
        binding.tvAccStatus.text = if (accOk) granted else needed
        binding.tvAccStatus.setTextColor(if (accOk) green else red)

        val batteryOk = PermissionHelper.isIgnoringBatteryOptimizations(this)
        binding.tvBatteryStatus.text = if (batteryOk) granted else needed
        binding.tvBatteryStatus.setTextColor(if (batteryOk) green else red)
    }

    private fun startActivitySafely(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
}
