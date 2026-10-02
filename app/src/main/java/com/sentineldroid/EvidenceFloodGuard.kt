package com.sentineldroid

import android.content.Context

object EvidenceFloodGuard {
    private const val PREFS = "sentinel_flood"
    private const val LIMIT_PER_HOUR = 240

    fun allow(context: Context, packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "${packageName}_${now / 3_600_000L}"
        val count = prefs.getInt(key, 0)
        if (count >= LIMIT_PER_HOUR) {
            if (!prefs.getBoolean("flag_${key}", false)) {
                EvidenceVault.get(context).add(
                    "EVIDENCE_FLOOD_ATTEMPT",
                    "EVIDENCE_FIREWALL",
                    "SECURITY_META",
                    packageName,
                    null,
                    "text/plain",
                    "Per-package evidence rate limit exceeded: $LIMIT_PER_HOUR/hour",
                    "RATE_LIMITED"
                )
                prefs.edit().putBoolean("flag_${key}", true).apply()
            }
            return false
        }
        prefs.edit().putInt(key, count + 1).apply()
        return true
    }
}
