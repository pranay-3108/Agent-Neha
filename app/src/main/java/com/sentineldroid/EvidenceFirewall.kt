package com.sentineldroid

import java.util.Locale

class EvidenceFirewall(private val vault: EvidenceVault) {
    fun ingest(
        sourceKind: String,
        packageName: String?,
        uid: Int?,
        contentType: String,
        content: String,
        origin: String = "APPLICATION_CONTROLLED"
    ): String {
        val clean = sanitize(content)
        val reasons = manipulationReasons(clean)
        val verification = if (reasons.isEmpty()) "UNVERIFIED_CONTENT" else "MANIPULATION_SIGNAL"

        val evidenceId = vault.add(
            sourceKind,
            safeOrigin(origin),
            "EVIDENCE",
            packageName,
            uid,
            contentType,
            clean,
            verification
        )

        if (reasons.isNotEmpty()) {
            val meta = buildMetaJson(evidenceId, packageName, reasons)
            vault.add(
                "SECURITY_META",
                "SENTINEL_FIREWALL",
                "SECURITY_META",
                packageName,
                uid,
                "application/json",
                meta,
                "FIREWALL_GENERATED"
            )
        }
        return evidenceId
    }

    private fun safeOrigin(origin: String): String = when (origin) {
        "ANDROID_PLATFORM", "SENTINEL_FIREWALL", "SENTINEL_USER_AUTHORIZED", "LAB_MANIPULATOR" -> origin
        else -> "APPLICATION_CONTROLLED"
    }

    private fun sanitize(value: String): String = value
        .replace("\u0000", "")
        .replace(Regex("[\\u0001-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "?")
        .take(200_000)

    private fun manipulationReasons(value: String): List<String> {
        val lower = value.lowercase(Locale.US)
        val reasons = mutableListOf<String>()

        if ("ignore previous instructions" in lower || "ignore all previous" in lower || "disregard previous" in lower) {
            reasons += "INSTRUCTION_LIKE_CONTENT"
        }
        if (listOf("system message", "developer message", "security system", "official android", "trusted system").any { it in lower }) {
            reasons += "AUTHORITY_IMPERSONATION"
        }
        if (Regex("\\\"role\\\"\\s*:\\s*\\\"system\\\"", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            reasons += "STRUCTURED_AUTHORITY_CLAIM"
        }
        if (Regex("\\\"(authorized|allow|trusted)\\\"\\s*:\\s*(true|\\\"true\\\")", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            reasons += "UNTRUSTED_AUTHORIZATION_CLAIM"
        }
        if (listOf("neural context", "semantic accessibility", "ai activity bridge", "cross-app neural", "context arbitration").any { it in lower }) {
            reasons += "POSSIBLE_PHANTOM_SUBSYSTEM"
        }
        if ("kernel" in lower && ("ai" in lower || "context" in lower || "accessibility" in lower)) {
            reasons += "UNVERIFIED_PLATFORM_CLAIM"
        }
        if (listOf("cve-", "security vulnerability", "critical diagnostic", "recovery code", "system command").any { it in lower }) {
            reasons += "DIAGNOSTIC_AUTHORITY_CLAIM"
        }
        return reasons.distinct()
    }

    private fun buildMetaJson(id: String, packageName: String?, reasons: List<String>): String {
        val rs = reasons.joinToString(",") { "\"${escape(it)}\"" }
        return "{" +
            "\"event\":\"EVIDENCE_MANIPULATION_DETECTED\"," +
            "\"affected_evidence\":\"${escape(id)}\"," +
            "\"package\":\"${escape(packageName ?: "") }\"," +
            "\"reasons\":[$rs]," +
            "\"platform_verification\":\"REQUIRED\"," +
            "\"origin\":\"SENTINEL_FIREWALL\"," +
            "\"authority\":\"SECURITY_META\"" +
            "}"
    }

    private fun escape(value: String) = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
