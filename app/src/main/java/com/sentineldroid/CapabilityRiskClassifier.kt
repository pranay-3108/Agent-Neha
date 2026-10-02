package com.sentineldroid

object CapabilityRiskClassifier {
    private val redSets = listOf(
        setOf("ACCESSIBILITY", "NOTIFICATION_LISTENER"),
        setOf("ACCESSIBILITY", "OVERLAY_ACCESS"),
        setOf("ACCESSIBILITY", "DEVICE_ADMIN"),
        setOf("ACCESSIBILITY", "VPN_SERVICE", "BOOT_RECEIVER"),
        setOf("NOTIFICATION_LISTENER", "OVERLAY_ACCESS", "INPUT_METHOD"),
        setOf("ACCESSIBILITY", "FOREGROUND_SERVICE", "BOOT_RECEIVER")
    )

    fun classify(facts: List<CapabilityFact>): RiskZone {
        val active = facts.filter { isActive(it.state) }.map { it.capability }.toSet()
        if (redSets.any { active.containsAll(it) }) return RiskZone.RED

        var score = 0
        if ("ACCESSIBILITY" in active) score += 2
        if ("NOTIFICATION_LISTENER" in active) score += 2
        if ("OVERLAY_ACCESS" in active) score += 1
        if ("DEVICE_ADMIN" in active || "DEVICE_OWNER" in active || "PROFILE_OWNER" in active) score += 2
        if ("VPN_SERVICE" in active) score += 1
        if ("BOOT_RECEIVER" in active) score += 1
        if ("FOREGROUND_SERVICE" in active) score += 1
        if (setOf("CAMERA_PERMISSION", "MICROPHONE_PERMISSION", "LOCATION_PERMISSION").count { it in active } >= 2) score += 1
        if (setOf("SMS_PERMISSION", "CALL_LOG_PERMISSION", "CONTACTS_PERMISSION").count { it in active } >= 2) score += 1

        return when {
            score >= 5 -> RiskZone.ORANGE
            score >= 2 -> RiskZone.YELLOW
            else -> RiskZone.GREEN
        }
    }

    fun isActive(state: String): Boolean = state in setOf("ENABLED", "GRANTED", "ACTIVE", "ALLOWED", "DECLARED")
}
