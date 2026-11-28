package com.titan.ide.os

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.services.TitanAccessibilityService
import com.example.services.TitanNotificationListenerService

data class DeviceTelemetrySnapshot(
    val batteryPercent: Int,
    val batteryTempC: Float,
    val isCharging: Boolean,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val storageFreeGb: Float,
    val storageTotalGb: Float,
    val networkLabel: String,
    val mediaVolumePercent: Int,
    val brightnessPercent: Int,
    val ringerModeLabel: String
)

data class TitanPermissionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val isGranted: Boolean,
    val isSpecialSettings: Boolean
)

/**
 * TitanSystemTelemetry — Real Hardware Diagnostics, OS Controllers & A-to-Z Permission Matrix
 * for J.A.R.V.I.S. Autonomous OS & F.R.I.D.A.Y. Command Center.
 */
object TitanSystemTelemetry {

    fun captureSnapshot(context: Context): DeviceTelemetrySnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val totalMb = (mi.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
        val availMb = (mi.availMem / (1024L * 1024L)).coerceAtLeast(0L)
        val usedMb = (totalMb - availMb).coerceAtLeast(0L)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = (stat.blockCountLong * stat.blockSizeLong) / (1024f * 1024f * 1024f)
        val freeGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024f * 1024f * 1024f)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val netCaps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val netLabel = when {
            netCaps == null -> "OFFLINE"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE DATA"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "ONLINE"
        }

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxVol = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
        val curVol = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val volPct = (curVol * 100) / maxVol
        val ringerLabel = when (audio?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            else -> "NORMAL"
        }

        val rawBright = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (_: Exception) {
            128
        }
        val brightPct = ((rawBright.coerceIn(0, 255) * 100) / 255)

        return DeviceTelemetrySnapshot(
            batteryPercent = batteryPct,
            batteryTempC = tempTenths / 10f,
            isCharging = isCharging,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            storageFreeGb = freeGb,
            storageTotalGb = totalGb,
            networkLabel = netLabel,
            mediaVolumePercent = volPct,
            brightnessPercent = brightPct,
            ringerModeLabel = ringerLabel
        )
    }

    fun formatTelemetryForAi(context: Context): String {
        val s = captureSnapshot(context)
        val chg = if (s.isCharging) "Charging" else "Discharging"
        return "Battery: ${s.batteryPercent}% ($chg, ${String.format("%.1f", s.batteryTempC)}C) | " +
            "RAM: ${s.ramUsedMb}/${s.ramTotalMb} MB | " +
            "Storage Free: ${String.format("%.1f", s.storageFreeGb)}/${String.format("%.1f", s.storageTotalGb)} GB | " +
            "Net: ${s.networkLabel} | Vol: ${s.mediaVolumePercent}% | Bright: ${s.brightnessPercent}% | Ringer: ${s.ringerModeLabel}"
    }

    fun setBrightnessPercent(context: Context, percent: Int): String {
        if (!Settings.System.canWrite(context)) {
            return "WRITE_SETTINGS permission required"
        }
        return try {
            val clamped = percent.coerceIn(5, 100)
            val value = (clamped * 255) / 100
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            "Brightness set to $clamped%"
        } catch (e: Exception) {
            "Brightness error: ${e.message}"
        }
    }

    fun toggleDoNotDisturb(context: Context, enable: Boolean): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return "NotificationManager unavailable"
        if (!nm.isNotificationPolicyAccessGranted) {
            return "DND Access permission required"
        }
        return try {
            val filter = if (enable) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            nm.setInterruptionFilter(filter)
            if (enable) "Do Not Disturb enabled" else "Do Not Disturb disabled"
        } catch (e: Exception) {
            "DND error: ${e.message}"
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun auditAllPermissions(context: Context): List<TitanPermissionItem> {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val batteryOk = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        val overlayOk = Settings.canDrawOverlays(context)
        val writeSettingsOk = Settings.System.canWrite(context)
        val dndOk = nm?.isNotificationPolicyAccessGranted == true
        val allFilesOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val installOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

        fun rt(perm: String) = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

        return listOf(
            TitanPermissionItem(
                id = "accessibility",
                title = "Accessibility Service",
                subtitle = "Hands-free screen reading, tapping, typing & app control",
                category = "CORE OS",
                isGranted = TitanAccessibilityService.isServiceRunning(),
                isSpecialSettings = true
            ),
            TitanPermissionItem(
                id = "overlay",
                title = "Display Over Other Apps",
                subtitle = "Floating HUD orb & background app launching",
                category = "CORE OS",
                isGranted = overlayOk,
                isSpecialSettings = true
            ),
            TitanPermissionItem(
                id = "notifications",
                title = "Notification Access",
                subtitle = "Read WhatsApp/SMS/OTP & direct quick-reply without opening apps",
                category = "CORE OS",
                isGranted = TitanNotificationListenerService.isPermissionGranted(context),
                isSpecialSettings = true
            ),
            TitanPermissionItem(
                id = "battery",
                title = "Unrestricted Battery",
                subtitle = "Keep Titan active in background without Samsung killing it",
                category = "CORE OS",
                isGranted = batteryOk,
                isSpecialSettings = true
            ),
            TitanPermissionItem(
                id = "write_settings",
                title = "Modify System Settings",
                subtitle = "Adjust screen brightness, timeout & rotation",
                category = "SYSTEM",
                isGranted = writeSettingsOk,
                isSpecialSettings = true
            ),
            TitanPermissionItem(
                id = "dnd",
                title = "Do Not Disturb Access",
                subtitle = "Toggle silent, vibrate & focus modes",
                category = "SYSTEM",
                isGranted = dndOk,
                isSpecialSettings = true
            ),
            TitanPermissionItem(
                id = "all_files",
                title = "All Files Access",
                subtitle = "Read, search & share files across Downloads and storage",
                category = "SYSTEM",
                isGranted = allFilesOk,
                isSpecialSettings = true
            ),
            TitanPermissionItem(
                id = "usage",
                title = "Usage Access",
                subtitle = "Detect foreground running app for smart context",
                category = "SYSTEM",
                isGranted = hasUsageAccess(context),
                isSpecialSettings = true
            ),
            TitanPermissionItem(
                id = "install_apk",
                title = "Install Unknown Apps",
                subtitle = "Seamless 1-tap OTA self-updates",
                category = "SYSTEM",
                isGranted = installOk,
                isSpecialSettings = true
            ),
            TitanPermissionItem(
                id = "mic",
                title = "Microphone",
                subtitle = "Hands-free voice commands & live call",
                category = "RUNTIME",
                isGranted = rt(Manifest.permission.RECORD_AUDIO),
                isSpecialSettings = false
            ),
            TitanPermissionItem(
                id = "phone_contacts",
                title = "Phone & Contacts",
                subtitle = "Place calls & look up contact numbers by name",
                category = "RUNTIME",
                isGranted = rt(Manifest.permission.CALL_PHONE) && rt(Manifest.permission.READ_CONTACTS),
                isSpecialSettings = false
            ),
            TitanPermissionItem(
                id = "sms",
                title = "SMS Read & Send",
                subtitle = "Read OTPs & send text messages hands-free",
                category = "RUNTIME",
                isGranted = rt(Manifest.permission.SEND_SMS),
                isSpecialSettings = false
            ),
            TitanPermissionItem(
                id = "camera",
                title = "Camera & Flashlight",
                subtitle = "Toggle torch & visual capture",
                category = "RUNTIME",
                isGranted = rt(Manifest.permission.CAMERA),
                isSpecialSettings = false
            )
        )
    }

    fun openPermissionSettings(context: Context, permissionId: String) {
        val pkgUri = Uri.parse("package:${context.packageName}")
        val intent = when (permissionId) {
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            "notifications" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            "battery" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            "write_settings" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
            "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "all_files" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
            }
            "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "install_apk" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        }
    }

    fun openAppInfoForRestrictedSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {}
    }
}
