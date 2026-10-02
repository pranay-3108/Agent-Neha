package com.sentineldroid

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager

class AccessCollector(private val context: Context) {
    fun buildFacts(packages: List<PackageSnapshot>, bootPackages: Set<String>): List<CapabilityFact> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<CapabilityFact>()
        val accessibility = enabledAccessibilityPackages()
        val notification = enabledNotificationPackages()
        val inputMethods = enabledInputMethodPackages()
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admins = dpm.activeAdmins.orEmpty().map(ComponentName::getPackageName).toSet()
        val deviceOwner = packages.firstOrNull { pkg -> runCatching { dpm.isDeviceOwnerApp(pkg.packageName) }.getOrDefault(false) }?.packageName

        packages.forEach { pkg ->
            if (pkg.packageName in accessibility) result += fact(pkg, "ACCESSIBILITY", "ENABLED", now)
            if (pkg.packageName in notification) result += fact(pkg, "NOTIFICATION_LISTENER", "ENABLED", now)
            if (pkg.packageName in inputMethods) result += fact(pkg, "INPUT_METHOD", "ENABLED", now)
            if (pkg.packageName in admins) result += fact(pkg, "DEVICE_ADMIN", "ACTIVE", now)
            if (pkg.packageName == deviceOwner) result += fact(pkg, "DEVICE_OWNER", "ACTIVE", now)
            if (runCatching { dpm.isProfileOwnerApp(pkg.packageName) }.getOrDefault(false)) result += fact(pkg, "PROFILE_OWNER", "ACTIVE", now)
            if (usageAccessGranted(pkg.packageName, pkg.uid)) result += fact(pkg, "USAGE_ACCESS", "ENABLED", now)
            if (pkg.packageName in bootPackages) result += fact(pkg, "BOOT_RECEIVER", "DECLARED", now)
            if (pkg.permissions.any { it.startsWith("android.permission.ACCESS_FINE_LOCATION=GRANTED") || it.startsWith("android.permission.ACCESS_COARSE_LOCATION=GRANTED") }) {
                result += fact(pkg, "LOCATION_PERMISSION", "GRANTED", now)
            }
            if (pkg.permissions.any { it.startsWith("android.permission.CAMERA=GRANTED") }) {
                result += fact(pkg, "CAMERA_PERMISSION", "GRANTED", now)
            }
            if (pkg.permissions.any { it.startsWith("android.permission.RECORD_AUDIO=GRANTED") }) {
                result += fact(pkg, "MICROPHONE_PERMISSION", "GRANTED", now)
            }
            if (pkg.permissions.any { it.startsWith("android.permission.READ_CONTACTS=GRANTED") }) {
                result += fact(pkg, "CONTACTS_PERMISSION", "GRANTED", now)
            }
            if (pkg.permissions.any { it.startsWith("android.permission.READ_CALL_LOG=GRANTED") || it.startsWith("android.permission.WRITE_CALL_LOG=GRANTED") }) {
                result += fact(pkg, "CALL_LOG_PERMISSION", "GRANTED", now)
            }
            if (pkg.permissions.any { it.startsWith("android.permission.READ_SMS=GRANTED") || it.startsWith("android.permission.RECEIVE_SMS=GRANTED") || it.startsWith("android.permission.SEND_SMS=GRANTED") }) {
                result += fact(pkg, "SMS_PERMISSION", "GRANTED", now)
            }
            if (pkg.permissions.any { it.startsWith("android.permission.READ_PHONE_STATE=GRANTED") || it.startsWith("android.permission.CALL_PHONE=GRANTED") || it.startsWith("android.permission.READ_PHONE_NUMBERS=GRANTED") }) {
                result += fact(pkg, "PHONE_PERMISSION", "GRANTED", now)
            }
            if (pkg.permissions.any {
                    it.startsWith("android.permission.READ_MEDIA_IMAGES=GRANTED") ||
                    it.startsWith("android.permission.READ_MEDIA_VIDEO=GRANTED") ||
                    it.startsWith("android.permission.READ_MEDIA_AUDIO=GRANTED")
                }) {
                result += fact(pkg, "MEDIA_PERMISSION", "GRANTED", now)
            }
            if (overlayAllowed(pkg.packageName, pkg.uid)) {
                result += fact(pkg, "OVERLAY_ACCESS", "ALLOWED", now)
            } else if (pkg.permissions.any { it.startsWith("android.permission.SYSTEM_ALERT_WINDOW=") }) {
                result += fact(pkg, "OVERLAY_ACCESS", "DECLARED", now)
            }
            if (pkg.permissions.any { it.startsWith("android.permission.MANAGE_EXTERNAL_STORAGE=") }) {
                result += fact(pkg, "ALL_FILES_ACCESS", "DECLARED_FOR_MANIFEST", now)
            }
        }

        result += CapabilityFact("<device>", "VPN", if (vpnActive()) "ACTIVE" else "INACTIVE", "CONNECTIVITY_SERVICE", now)
        result += CapabilityFact("<device>", "ADB_DEBUGGING", adbState(), "ANDROID_SETTINGS", now)
        result += CapabilityFact("<device>", "DEVELOPER_OPTIONS", developerOptionsState(), "ANDROID_SETTINGS", now)
        result += CapabilityFact("<device>", "VERIFIED_BOOT", verifiedBootState(), "ANDROID_SYSTEM_PROPERTIES", now)
        return result
    }

    private fun fact(pkg: PackageSnapshot, capability: String, state: String, now: Long) =
        CapabilityFact(pkg.packageName, capability, state, "ANDROID_PLATFORM_STATE", now)

    private fun enabledAccessibilityPackages(): Set<String> {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return emptySet()
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }.toSet()
    }

    private fun enabledNotificationPackages(): Set<String> {
        val raw = runCatching { Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(":").mapNotNull { runCatching { ComponentName.unflattenFromString(it)?.packageName }.getOrNull() }.toSet()
    }


    private fun enabledInputMethodPackages(): Set<String> {
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return emptySet()
        return manager.enabledInputMethodList.map { it.packageName }.toSet()
    }

    private fun overlayAllowed(packageName: String, uid: Int): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        return runCatching {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    private fun usageAccessGranted(packageName: String, uid: Int): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        return runCatching {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    private fun vpnActive(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    private fun adbState(): String = when (runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED) }.getOrDefault(-1)) {
        1 -> "ENABLED"
        0 -> "DISABLED"
        else -> "UNKNOWN"
    }

    private fun developerOptionsState(): String = when (runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) }.getOrDefault(-1)) {
        1 -> "ENABLED"
        0 -> "DISABLED"
        else -> "UNKNOWN"
    }

    private fun verifiedBootState(): String = runCatching {
        SystemPropertiesProxy.get("ro.boot.verifiedbootstate")
    }.getOrDefault("UNOBSERVED")
}

private object SystemPropertiesProxy {
    fun get(key: String): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            clazz.getMethod("get", String::class.java).invoke(null, key) as String
        }.getOrDefault("UNOBSERVED")
    }
}
