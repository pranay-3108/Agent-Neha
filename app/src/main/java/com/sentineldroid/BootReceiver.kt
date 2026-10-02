package com.sentineldroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val vault = EvidenceVault.get(context)
        vault.add(
            sourceKind = "BOOT_EVENT",
            origin = "ANDROID_PLATFORM",
            authority = "EVIDENCE",
            packageName = context.packageName,
            uid = Process.myUid(),
            contentType = "text/plain",
            content = intent.action ?: "UNKNOWN_BOOT_ACTION",
            verification = "PLATFORM_DERIVED"
        )
        val prefs = context.getSharedPreferences("sentinel_runtime", Context.MODE_PRIVATE)
        if (prefs.getBoolean("background_monitoring", false)) {
            MonitorScheduler.schedule(context)
        }
    }
}
