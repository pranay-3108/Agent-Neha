package com.sentineldroid

import android.content.Context

object SelfDefense {
    fun audit(context: Context): List<String> {
        val issues = mutableListOf<String>()
        if (!SentinelNotifications.notificationsAllowed(context)) issues += "Notifications unavailable: alerts may not reach the user."
        if (!TelemetryHealth.read(context).monitoring) issues += "Continuous monitoring is off."
        if (!EvidenceVault.get(context).verifyChain()) issues += "Evidence vault integrity check failed."
        if (EvidenceVault.get(context).publicSigningKey().isBlank()) issues += "Evidence signing key is unavailable."
        EvidenceVault.get(context).add("SELF_DEFENSE_AUDIT", "SENTINEL_SELF_DEFENSE", "SECURITY_META", null, null, "application/json", org.json.JSONObject().put("issues", org.json.JSONArray(issues)).toString(), "SELF_AUDIT")
        return issues
    }
}
