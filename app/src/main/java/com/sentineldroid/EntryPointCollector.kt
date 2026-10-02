package com.sentineldroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

class EntryPointCollector(private val context: Context) {
    fun collect(packages: List<PackageSnapshot>, vault: EvidenceVault): Int {
        val pm = context.packageManager
        val map = mutableMapOf<String, MutableSet<String>>()
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_FULLY_REMOVED
        )

        actions.forEach { action ->
            val intent = Intent(action)
            if (action.startsWith("android.intent.action.PACKAGE_")) {
                intent.data = Uri.parse("package:com.sentineldroid.probe")
            }
            runCatching {
                @Suppress("DEPRECATION")
                pm.queryBroadcastReceivers(intent, android.content.pm.PackageManager.MATCH_ALL)
            }.getOrDefault(emptyList()).forEach { resolve ->
                val pkg = resolve.activityInfo?.packageName ?: return@forEach
                map.getOrPut(pkg) { mutableSetOf() } += action
            }
        }

        var count = 0
        map.entries.sortedBy { it.key }.take(500).forEach { (pkg, triggers) ->
            vault.add(
                "RECEIVER_ENTRYPOINTS",
                "ANDROID_PACKAGE_MANAGER",
                "EVIDENCE",
                pkg,
                null,
                "application/json",
                "{\"package\":\"${escape(pkg)}\",\"triggers\":[${triggers.sorted().joinToString(",") { "\"${escape(it)}\"" }}],\"build_sdk\":${Build.VERSION.SDK_INT}}",
                "PLATFORM_DERIVED"
            )
            count++
        }
        return count
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
