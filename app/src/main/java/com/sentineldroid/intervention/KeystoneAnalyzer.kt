package com.sentineldroid.intervention

/**
 * Deterministic. No AI. No network.
 * Finds the single capability whose removal collapses the most
 * critical attack chains with the least user impact.
 */
class KeystoneAnalyzer : InterventionPlanner {

    private data class Pattern(
        val name: String,
        val requires: Set<String>,
        val keystone: String,
        val severity: Int   // 3 = critical, 2 = high, 1 = medium
    )

    private val patterns = listOf(
        Pattern("SCREEN_READ_AND_EXFIL",   setOf("ACCESSIBILITY","NETWORK"),                       "ACCESSIBILITY",       3),
        Pattern("CREDENTIAL_THEFT",        setOf("ACCESSIBILITY","OVERLAY"),                       "ACCESSIBILITY",       3),
        Pattern("SURVEILLANCE",            setOf("CAMERA","ACCESSIBILITY","NETWORK"),              "ACCESSIBILITY",       3),
        Pattern("INPUT_CAPTURE",           setOf("ACCESSIBILITY","INPUT_METHOD"),                  "ACCESSIBILITY",       3),
        Pattern("DEVICE_MANAGEMENT_ABUSE", setOf("DEVICE_ADMIN","ACCESSIBILITY"),                  "ACCESSIBILITY",       3),
        Pattern("SCREEN_RECORD_AND_EXFIL", setOf("MEDIA_PROJECTION","NETWORK"),                    "MEDIA_PROJECTION",    2),
        Pattern("CLICKJACKING",            setOf("OVERLAY","ACCESSIBILITY"),                       "OVERLAY",             2),
        Pattern("PERSISTENCE_C2",          setOf("BOOT_RECEIVER","FOREGROUND_SERVICE","NETWORK"),  "FOREGROUND_SERVICE",  2),
        Pattern("NOTIFICATION_THEFT",      setOf("NOTIFICATION_LISTENER","NETWORK"),               "NOTIFICATION_LISTENER",2),
        Pattern("VPN_INTERCEPTION",        setOf("VPN_SERVICE","ACCESSIBILITY"),                   "ACCESSIBILITY",       2)
    )

    override fun plan(
        packageName: String,
        facts: Set<String>,
        category: String,
        installSource: String,
        signerKnown: Boolean
    ): InterventionPlanner.InterventionPlan {

        val have = facts.map { it.uppercase() }.toSet()
        val active = patterns.filter { have.containsAll(it.requires) }

        if (active.isEmpty()) {
            return InterventionPlanner.InterventionPlan(
                hasTarget = false,
                keystoneCapability = null,
                brokenChains = emptyList(),
                userImpact = InterventionPlanner.Impact.HIGH,
                confidence = 0.0
            )
        }

        val byKeystone = active.groupBy { it.keystone }
        val best = byKeystone.maxByOrNull { (_, chains) ->
            chains.size * 10 + chains.maxOf { it.severity }
        }!!

        val keystone = best.key
        val broken = best.value.map { it.name }
        val totalSeverity = best.value.maxOf { it.severity }
        val impact = estimateImpact(keystone, category)

        var confidence = 0.5
        if (installSource.contains("SIDELOAD", ignoreCase = true)) confidence += 0.2
        if (!signerKnown) confidence += 0.15
        confidence += (totalSeverity - 1) * 0.05
        confidence = confidence.coerceIn(0.0, 1.0)

        return InterventionPlanner.InterventionPlan(
            hasTarget = true,
            keystoneCapability = keystone,
            brokenChains = broken,
            userImpact = impact,
            confidence = confidence
        )
    }

    private fun estimateImpact(capability: String, category: String): InterventionPlanner.Impact {
        val cat = category.lowercase()
        return when (capability) {
            "ACCESSIBILITY"         -> if (cat.contains("accessib") || cat.contains("automation"))
                                          InterventionPlanner.Impact.MEDIUM
                                       else InterventionPlanner.Impact.LOW
            "MEDIA_PROJECTION"      -> if (cat.contains("record") || cat.contains("stream"))
                                          InterventionPlanner.Impact.HIGH
                                       else InterventionPlanner.Impact.LOW
            "CAMERA"                -> if (cat.contains("camera") || cat.contains("photo"))
                                          InterventionPlanner.Impact.HIGH
                                       else InterventionPlanner.Impact.MEDIUM
            "NOTIFICATION_LISTENER" -> InterventionPlanner.Impact.LOW
            "OVERLAY"               -> InterventionPlanner.Impact.LOW
            "FOREGROUND_SERVICE"    -> InterventionPlanner.Impact.MEDIUM
            else                    -> InterventionPlanner.Impact.MEDIUM
        }
    }
}