package com.sentineldroid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ForensicSnapshot {
    fun build(context: Context, pkg: PackageSnapshot, facts: List<CapabilityFact>): String {
        val trust = TrustScore.calculate(context, pkg, facts)
        val baseline = BehavioralBaselineStore.baseline(context, pkg.packageName)
        val watchdog = WatchdogRegistry.contracts(context).filter { it.packageName == pkg.packageName }
        val evidence = EvidenceVault.get(context).latest(500).filter { it["package"] == pkg.packageName }.take(160)
        val out = JSONObject()
            .put("schema", "neha-forensic-snapshot-v0.6")
            .put("generatedAt", System.currentTimeMillis())
            .put("manifest", JSONObject(SnapshotJson.build(pkg)))
            .put("trustScore", trust.score)
            .put("riskZone", trust.zone.name)
            .put("trustReasons", JSONArray(trust.reasons))
            .put("watchdogContracts", JSONArray(watchdog.map { JSONObject().put("enteredAt", it.enteredAt).put("reason", it.triggerReason).put("zone", it.triggerZone.name) }))
            .put("vaultPublicKey", EvidenceVault.get(context).publicSigningKey())
            .put("vaultIntegrity", EvidenceVault.get(context).verifyChain())
            .put("unknowns", JSONArray(TelemetryHealth.read(context).blindSpots()))
        if (baseline != null) {
            out.put("behavior", JSONObject()
                .put("builtDays", baseline.builtDays)
                .put("confidence", baseline.confidence)
                .put("rxMean", baseline.rx.mean)
                .put("txMean", baseline.tx.mean)
                .put("foregroundMean", baseline.foregroundMinutes.mean)
                .put("accessibilityMean", baseline.accessibilityEvents.mean))
        }
        out.put("facts", JSONArray(facts.filter { it.packageName == pkg.packageName }.map {
            JSONObject().put("capability", it.capability).put("state", it.state).put("source", it.source).put("observedAt", it.observedAt)
        }))
        out.put("recentEvidence", JSONArray(evidence.map {
            JSONObject().put("id", it["id"]).put("source", it["source"]).put("origin", it["origin"]).put("authority", it["authority"]).put("verification", it["verification"]).put("contentSha256", it["content_sha256"])
        }))
        val payload = out.toString(2)
        val id = EvidenceVault.get(context).add("FORENSIC_SNAPSHOT", "FORENSIC_EXPORT", "SECURITY_META", pkg.packageName, pkg.uid, "application/json", payload, "SIGNED_IN_VAULT")
        return JSONObject(payload).put("snapshotRecordId", id).toString(2)
    }
}
