package com.sentineldroid.intervention

interface InterventionPlanner {
    fun plan(
        packageName: String,
        facts: Set<String>,
        category: String,
        installSource: String,
        signerKnown: Boolean
    ): InterventionPlan

    data class InterventionPlan(
        val hasTarget: Boolean,
        val keystoneCapability: String?,
        val brokenChains: List<String>,
        val userImpact: Impact,
        val confidence: Double
    )

    enum class Impact { LOW, MEDIUM, HIGH }
}

class NoOpInterventionPlanner : InterventionPlanner {
    override fun plan(
        packageName: String,
        facts: Set<String>,
        category: String,
        installSource: String,
        signerKnown: Boolean
    ) = InterventionPlanner.InterventionPlan(false, null, emptyList(), InterventionPlanner.Impact.HIGH, 0.0)
}
