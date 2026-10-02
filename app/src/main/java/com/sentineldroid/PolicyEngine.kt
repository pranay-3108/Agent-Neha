package com.sentineldroid

object PolicyEngine {
    private val destructive = setOf(
        "DISABLE_SECURITY_AGENT",
        "UNINSTALL_APP",
        "FACTORY_RESET",
        "CLEAR_SECURITY_DATA",
        "GRANT_PERMISSION",
        "MODIFY_SECURITY_POLICY"
    )

    private val allowed = setOf(
        "OBSERVE",
        "ALERT",
        "ISOLATE_NETWORK",
        "EMERGENCY_NETWORK_LOCKDOWN"
    )

    data class Decision(
        val allowed: Boolean,
        val requiresUser: Boolean,
        val reason: String
    )

    fun evaluate(action: String, packageName: String?): Decision {
        val normalized = action.trim().uppercase()
        if (normalized in destructive) {
            return Decision(false, true, "DESTRUCTIVE_OR_PRIVILEGED_ACTION_REQUIRES_EXPLICIT_AUTHORIZATION")
        }
        if (normalized !in allowed) {
            return Decision(false, false, "ACTION_NOT_IN_STATIC_ALLOWLIST")
        }
        if (normalized == "ISOLATE_NETWORK" && packageName.isNullOrBlank()) {
            return Decision(false, false, "TARGET_PACKAGE_REQUIRED")
        }
        if (normalized == "EMERGENCY_NETWORK_LOCKDOWN") {
            return Decision(true, true, "EXPLICIT_USER_CONFIRMATION_REQUIRED")
        }
        return Decision(true, true, "POLICY_ALLOWLIST")
    }
}
