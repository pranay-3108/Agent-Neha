package com.sentineldroid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object WatchdogRegistry {
    private const val PREFS = "sentinel_watchdogs"
    private const val KEY = "contracts"

    data class Contract(
        val packageName: String,
        val signerSha256: String,
        val enteredAt: Long,
        val triggerReason: String,
        val triggerZone: RiskZone,
        val permanent: Boolean = true
    )

    fun enroll(context: Context, contract: Contract) {
        val current = all(context).filterNot { it.packageName == contract.packageName && it.signerSha256 == contract.signerSha256 }
        save(context, current + contract)
        EvidenceVault.get(context).add(
            "WATCHDOG_ENROLLED",
            "WATCHDOG_REGISTRY",
            "SECURITY_META",
            contract.packageName,
            null,
            "application/json",
            toJson(contract),
            "WATCHDOG_PERMANENT"
        )
    }

    fun isWatched(context: Context, packageName: String): Boolean = all(context).any { it.packageName == packageName }

    fun contracts(context: Context): List<Contract> = all(context)

    fun markUninstalled(context: Context, pkg: PackageSnapshot) {
        val contract = all(context).firstOrNull { it.packageName == pkg.packageName }
        if (contract != null) {
            EvidenceVault.get(context).add(
                "SHADOW_RECORD",
                "WATCHDOG_REGISTRY",
                "SECURITY_META",
                pkg.packageName,
                pkg.uid,
                "application/json",
                JSONObject().apply {
                    put("packageName", pkg.packageName)
                    put("signerSha256", pkg.signerSha256.joinToString(","))
                    put("zone", contract.triggerZone.name)
                    put("uninstalledAt", System.currentTimeMillis())
                }.toString(),
                "UNINSTALL_SHADOW"
            )
        }
    }

    private fun all(context: Context): List<Contract> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Contract(
                    o.getString("packageName"),
                    o.getString("signerSha256"),
                    o.getLong("enteredAt"),
                    o.getString("triggerReason"),
                    RiskZone.valueOf(o.getString("triggerZone")),
                    o.optBoolean("permanent", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, contracts: List<Contract>) {
        val arr = JSONArray()
        contracts.forEach { arr.put(JSONObject(toJson(it))) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun toJson(c: Contract): String = JSONObject().apply {
        put("packageName", c.packageName)
        put("signerSha256", c.signerSha256)
        put("enteredAt", c.enteredAt)
        put("triggerReason", c.triggerReason)
        put("triggerZone", c.triggerZone.name)
        put("permanent", c.permanent)
    }.toString()
}
