package com.langsense.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import com.langsense.app.service.LangSenseAccessibilityService

/**
 * 권한/활성화 상태 점검 및 시스템 설정 화면 이동을 담당.
 * (권한 요청 자체는 시스템 설정으로 이동시키는 방식 — 오버레이/접근성은 런타임 다이얼로그 불가)
 */
object PermissionHelper {

    /** SYSTEM_ALERT_WINDOW(다른 앱 위에 표시) 권한 보유 여부. */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 접근성 서비스 활성화 여부. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val target = LangSenseAccessibilityService::class.java.name
        return enabled.any { info ->
            val id = info.id ?: return@any false
            id.contains(context.packageName) && id.contains(target.substringAfterLast('.'))
        }
    }

    /** Settings.Secure 기반 보조 점검(일부 기기에서 enabledServices 직접 확인). */
    fun isAccessibilityEnabledViaSecure(context: Context): Boolean {
        val expected = "${context.packageName}/${LangSenseAccessibilityService::class.java.name}"
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 배터리 최적화 예외 여부. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
