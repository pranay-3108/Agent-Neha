package com.sentineldroid

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.provider.Settings

class RuntimeCollector(private val context: Context) {
    fun collectGlobal(vault: EvidenceVault): Int {
        var count = 0
        val now = System.currentTimeMillis()
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val usageAccess = usageAccessGranted()
        vault.add(
            "SECURITY_STATE",
            "ANDROID_PLATFORM",
            "EVIDENCE",
            null,
            null,
            "application/json",
            "{\"usage_access_for_sentinel\":$usageAccess,\"adb\":${setting(Settings.Global.ADB_ENABLED)},\"developer_options\":${setting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)},\"timestamp\":$now}",
            "PLATFORM_DERIVED"
        )
        count++

        if (usage != null && usageAccess) {
            val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 15 * 60_000L, now)
            stats.take(100).filter { it.packageName != context.packageName }.forEach {
                vault.add(
                    "USAGE_SUMMARY",
                    "ANDROID_USAGE_STATS",
                    "EVIDENCE",
                    it.packageName,
                    null,
                    "application/json",
                    "{\"package\":\"${escape(it.packageName)}\",\"last_time_used\":${it.lastTimeUsed},\"total_foreground_ms\":${it.totalTimeInForeground}}",
                    "PLATFORM_DERIVED"
                )
                count++
            }
        }
        return count
    }

    private fun usageAccessGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        return runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    private fun setting(name: String): String = runCatching {
        Settings.Global.getInt(context.contentResolver, name).toString()
    }.getOrDefault("-1")

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
