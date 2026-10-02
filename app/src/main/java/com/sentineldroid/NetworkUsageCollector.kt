package com.sentineldroid

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager

class NetworkUsageCollector(private val context: Context) {
    fun collect(packages: List<PackageSnapshot>, vault: EvidenceVault): Int {
        if (!usageAccessAvailable()) return 0
        val manager = context.getSystemService(NetworkStatsManager::class.java) ?: return 0
        val end = System.currentTimeMillis()
        val start = end - 15 * 60_000L
        var count = 0

        packages.forEach { pkg ->
            val wifi = query(manager, ConnectivityManager.TYPE_WIFI, start, end, pkg.uid)
            val mobile = query(manager, ConnectivityManager.TYPE_MOBILE, start, end, pkg.uid)
            if (wifi.rx + wifi.tx > 0) {
                vault.add(
                    "NETWORK_USAGE",
                    "ANDROID_NETWORK_STATS",
                    "EVIDENCE",
                    pkg.packageName,
                    pkg.uid,
                    "application/json",
                    "{\"transport\":\"WIFI\",\"rx_bytes\":${wifi.rx},\"tx_bytes\":${wifi.tx},\"window_start\":$start,\"window_end\":$end}",
                    "PLATFORM_DERIVED"
                )
                count++
            }
            if (mobile.rx + mobile.tx > 0) {
                vault.add(
                    "NETWORK_USAGE",
                    "ANDROID_NETWORK_STATS",
                    "EVIDENCE",
                    pkg.packageName,
                    pkg.uid,
                    "application/json",
                    "{\"transport\":\"MOBILE\",\"rx_bytes\":${mobile.rx},\"tx_bytes\":${mobile.tx},\"window_start\":$start,\"window_end\":$end}",
                    "PLATFORM_DERIVED"
                )
                count++
            }
        }
        return count
    }

    private data class Totals(val rx: Long, val tx: Long)

    private fun query(manager: NetworkStatsManager, type: Int, start: Long, end: Long, uid: Int): Totals {
        var rx = 0L
        var tx = 0L
        runCatching {
            @Suppress("DEPRECATION")
            manager.queryDetailsForUid(type, null, start, end, uid).use { stats ->
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    rx += bucket.rxBytes
                    tx += bucket.txBytes
                }
            }
        }
        return Totals(rx, tx)
    }

    private fun usageAccessAvailable(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        return runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}
