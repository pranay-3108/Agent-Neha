package com.sentineldroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object TelemetryHealth {
    data class State(
        val notifications: Boolean,
        val notificationAccess: Boolean,
        val accessibility: Boolean,
        val usageAccess: Boolean,
        val monitoring: Boolean,
        val unknowns: List<String>
    ) {
        fun blindSpots(): List<String> = buildList {
            if (!notifications) add("Security notifications are unavailable.")
            if (!notificationAccess) add("Notification listener telemetry is unavailable.")
            if (!accessibility) add("Accessibility telemetry is unavailable.")
            if (!usageAccess) add("Usage access telemetry is unavailable.")
            if (!monitoring) add("Continuous background monitoring is off.")
            addAll(unknowns)
        }
    }

    fun read(context: Context): State {
        val notifications = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val notificationAccess = NotificationAccessState.isEnabled(context)
        val accessibility = AccessibilityAccessState.isEnabled(context)
        val usage = UsageAccessState.isEnabled(context)
        val monitoring = context.getSharedPreferences("sentinel_runtime", Context.MODE_PRIVATE).getBoolean("background_monitoring", false)
        return State(notifications, notificationAccess, accessibility, usage, monitoring, emptyList())
    }
}

object NotificationAccessState {
    fun isEnabled(context: Context): Boolean {
        val raw = runCatching { Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") }.getOrNull().orEmpty()
        return raw.contains(context.packageName)
    }
}

object AccessibilityAccessState {
    fun isEnabled(context: Context): Boolean {
        val raw = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull().orEmpty()
        return raw.contains(context.packageName)
    }
}

object UsageAccessState {
    fun isEnabled(context: Context): Boolean {
        val appOps = context.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        return runCatching {
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}
