package com.sentineldroid

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object SentinelNotifications {
    private const val ALERT_CHANNEL = "sentinel_alerts"
    private const val MONITOR_CHANNEL = "sentinel_monitoring"
    private const val ALERT_ID_BASE = 7000

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL,
                "Security alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Agent Neha security alerts for application capability and security-state changes"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                MONITOR_CHANNEL,
                "Background monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when continuous Agent Neha monitoring is enabled"
            }
        )
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() != false
    }

    fun postNewApp(
        context: Context,
        packageName: String,
        appLabel: String,
        capabilities: List<String>
    ) {
        val title = "Agent Neha: New app detected"
        val body = if (capabilities.isEmpty()) {
            "$appLabel was installed. No sensitive capabilities were observed yet."
        } else {
            "$appLabel was installed with: ${capabilities.take(4).joinToString(", ")}."
        }
        postAlert(context, packageName, title, body, packageName.hashCode())
    }

    fun postCapabilityChange(
        context: Context,
        packageName: String,
        appLabel: String,
        changes: List<String>
    ) {
        if (changes.isEmpty()) return
        val body = "$appLabel changed: ${changes.take(5).joinToString(", ")}. Tap to review."
        postAlert(context, packageName, "Agent Neha: Security capability changed", body, packageName.hashCode() xor 0x22)
    }

    private fun postAlert(
        context: Context,
        packageName: String,
        title: String,
        body: String,
        idPart: Int
    ) {
        if (!notificationsAllowed(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_REVIEW_PACKAGE, packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            8100 xor idPart,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = context.getSharedPreferences("sentinel_alerts", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_title", title)
            .putString("last_body", body)
            .putLong("last_time", System.currentTimeMillis())
            .putInt("pending_count", prefs.getInt("pending_count", 0) + 1)
            .apply()

        val notification = android.app.Notification.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_sentinel)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(android.app.Notification.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(android.app.Notification.CATEGORY_STATUS)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(
            ALERT_ID_BASE + (idPart and 0x7ff),
            notification
        )
    }

    fun pendingCount(context: Context): Int =
        context.getSharedPreferences("sentinel_alerts", Context.MODE_PRIVATE).getInt("pending_count", 0)

    fun lastAlert(context: Context): String {
        val prefs = context.getSharedPreferences("sentinel_alerts", Context.MODE_PRIVATE)
        val title = prefs.getString("last_title", null).orEmpty()
        val body = prefs.getString("last_body", null).orEmpty()
        return listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")
    }

    fun postTieredAlert(context: Context, packageName: String, tier: String, title: String, body: String) {
        val suffix = when (tier) {
            "critical" -> "CRITICAL"
            "alert" -> "ALERT"
            else -> "INFO"
        }
        postAlert(context, packageName, "$title [$suffix]", body, packageName.hashCode() xor tier.hashCode())
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences("sentinel_alerts", Context.MODE_PRIVATE).edit()
            .putInt("pending_count", 0)
            .apply()
    }

    fun buildMonitoringNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            8101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return android.app.Notification.Builder(context, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_sentinel)
            .setContentTitle("Agent Neha monitoring")
            .setContentText("Watching for security-relevant app changes")
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(android.app.Notification.CATEGORY_SERVICE)
            .setPriority(android.app.Notification.PRIORITY_LOW)
            .build()
    }
}
