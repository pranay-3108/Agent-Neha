package com.sentineldroid

import android.content.Context
import kotlin.math.roundToInt

object TrustScore {
    data class Result(
        val packageName: String,
        val score: Int,
        val zone: RiskZone,
        val reasons: List<String>
    )

    fun calculate(context: Context, pkg: PackageSnapshot, facts: List<CapabilityFact>): Result {
        var score = 100.0
        val reasons = mutableListOf<String>()
        val packageFacts = facts.filter { it.packageName == pkg.packageName }
        val zone = CapabilityRiskClassifier.classify(packageFacts)

        when (zone) {
            RiskZone.GREEN -> Unit
            RiskZone.YELLOW -> { score -= 12; reasons += "Capability combination is in YELLOW." }
            RiskZone.ORANGE -> { score -= 28; reasons += "Capability combination is in ORANGE." }
            RiskZone.RED -> { score -= 48; reasons += "Capability combination matches a RED signature." }
        }

        val source = InstallSourceClassifier.classify(pkg)
        if (source != InstallSourceClassifier.Source.PLAY_STORE) {
            val penalty = ((source.multiplier - 1.0) * 14.0).coerceAtLeast(0.0)
            score -= penalty
            reasons += "Install source: ${source.name}."
        }

        if (pkg.debuggable) { score -= 8; reasons += "Debuggable build." }
        if (pkg.testOnly) { score -= 8; reasons += "Test-only package." }
        if (!pkg.enabled) { score += 3; reasons += "Package is disabled." }

        val manipulationFlags = context.getSharedPreferences("sentinel_trust", Context.MODE_PRIVATE)
            .getInt("flags_${pkg.packageName}", 0)
        if (manipulationFlags > 0) {
            score -= (manipulationFlags * 8).coerceAtMost(24)
            reasons += "Evidence firewall flags present."
        }

        return Result(pkg.packageName, score.roundToInt().coerceIn(0, 100), zone, reasons)
    }
}
