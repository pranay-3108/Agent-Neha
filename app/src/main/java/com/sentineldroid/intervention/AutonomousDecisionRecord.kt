package com.sentineldroid.intervention

/**
 * Signed record of an autonomous decision. Written BEFORE the shot.
 * Makes the killer accountable to the evidence, not after the fact.
 */
data class AutonomousDecisionRecord(
    val packageName: String,
    val rung: Int,
    val action: String,
    val triggerFacts: List<String>,
    val policyDecision: String,
    val authorizedBy: String,     // "AUTO_POLICY" | "USER"
    val expiresAt: Long?
) {
    fun toJson(): String {
        val factsJson = triggerFacts.joinToString(",", "[", "]") { "\"${it.replace("\"","\\\"")}\"" }
        val exp = expiresAt?.toString() ?: "null"
        return """{"package":"$packageName","rung":$rung,"action":"$action","trigger_facts":$factsJson,"policy_decision":"$policyDecision","authorized_by":"$authorizedBy","expires_at":$exp}"""
    }
}