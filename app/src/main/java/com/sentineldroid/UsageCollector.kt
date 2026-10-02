package com.sentineldroid

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class UsageCollector(private val context: Context) {
    fun collectAndStore(vault: EvidenceVault): Int {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return 0
        val now = System.currentTimeMillis()
        val start = now - 15 * 60_000L
        var newest = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_TS, 0L)
        var count = 0
        val events = manager.queryEvents(start, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp <= newest) continue
            val type = readableType(event.eventType) ?: continue
            if (event.packageName == null) continue

            val record = "eventType=$type\npackage=${event.packageName}\nclass=${event.className.orEmpty()}\ntimestamp=${event.timeStamp}"
            vault.add(
                "USAGE_EVENT",
                "ANDROID_PLATFORM",
                "EVIDENCE",
                event.packageName,
                null,
                "text/plain",
                record,
                "PLATFORM_DERIVED"
            )
            newest = maxOf(newest, event.timeStamp)
            count++
            if (count >= 200) break
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_TS, newest).apply()
        return count
    }

    private fun readableType(type: Int): String? = when (type) {
        UsageEvents.Event.ACTIVITY_RESUMED -> "ACTIVITY_RESUMED"
        UsageEvents.Event.ACTIVITY_PAUSED -> "ACTIVITY_PAUSED"
        UsageEvents.Event.ACTIVITY_STOPPED -> "ACTIVITY_STOPPED"
        UsageEvents.Event.FOREGROUND_SERVICE_START -> "FOREGROUND_SERVICE_START"
        UsageEvents.Event.FOREGROUND_SERVICE_STOP -> "FOREGROUND_SERVICE_STOP"
        UsageEvents.Event.DEVICE_STARTUP -> "DEVICE_STARTUP"
        UsageEvents.Event.DEVICE_SHUTDOWN -> "DEVICE_SHUTDOWN"
        UsageEvents.Event.SCREEN_INTERACTIVE -> "SCREEN_INTERACTIVE"
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> "SCREEN_NON_INTERACTIVE"
        else -> null
    }

    companion object {
        private const val PREFS = "sentinel_runtime"
        private const val LAST_TS = "usage_last_timestamp"
    }
}
