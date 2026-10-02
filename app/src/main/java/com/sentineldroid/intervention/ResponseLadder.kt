package com.sentineldroid.intervention

import android.content.Context
import com.sentineldroid.EvidenceVault

/**
 * The escalation ladder.
 *
 * Rung 0 = WATCH
 * Rung 1 = ALERT
 * Rung 2 = NETWORK_CONTAIN
 * Rung 3 = CAPABILITY_SHOT   ← only auto-fire rung
 * Rung 4 = TIME_BOXED_FREEZE
 * Rung 5 = SUSPEND (user-confirmed only)
 * Rung 6 = UNINSTALL (never automatic)
 */
object ResponseLadder {

    enum class Zone { GREEN, YELLOW, ORANGE, RED }

    data class Decision(
        val rung: Int,
        val action: String,
        val autoFire: Boolean,
        val reason: String,
        val expiresAt: Long?,
        val keystone: String? = null
    )

    fun evaluate(
        ctx: Context,
        pkg: String,
        zone: Zone,
        capabilities: Set<String>,
        category: String,
        installSource: String,
        signerKnown: Boolean
    ): Decision {

        val plan = InterventionRegistry.planner().plan(
            packageName = pkg,
            facts = capabilities,
            category = category,
            installSource = installSource,
            signerKnown = signerKnown
        )

        val autoEligible =
            zone == Zone.RED &&
            plan.hasTarget &&
            plan.userImpact == InterventionPlanner.Impact.LOW &&
            !installSource.equals("PLAY_STORE", ignoreCase = true) &&
            plan.confidence >= 0.7 &&
            ShotBudget.canFire(ctx, pkg) &&
            InterventionRegistry.executor().isCapable()

        if (autoEligible) {
            val expires = System.currentTimeMillis() + 15 * 60_000L
            val record = AutonomousDecisionRecord(
                packageName = pkg,
                rung = 3,
                action = "CAPABILITY_SHOT",
                triggerFacts = capabilities.toList().sorted(),
                policyDecision = "AUTO_FIRE_ALLOWED",
                authorizedBy = "AUTO_POLICY",
                expiresAt = expires
            )
            writeDecision(ctx, pkg, record)

            return Decision(
                rung = 3,
                action = "CAPABILITY_SHOT",
                autoFire = true,
                reason = "RED zone + keystone=${plan.keystoneCapability} + non-store + low impact",
                expiresAt = expires,
                keystone = plan.keystoneCapability
            )
        }

        if (zone == Zone.RED && plan.hasTarget) {
            return Decision(
                rung = 3,
                action = "CAPABILITY_SHOT",
                autoFire = false,
                reason = "RED zone, user confirmation required",
                expiresAt = null,
                keystone = plan.keystoneCapability
            )
        }

        if (zone == Zone.ORANGE || zone == Zone.RED) {
            return Decision(
                rung = 2,
                action = "NETWORK_CONTAIN",
                autoFire = false,
                reason = "elevated zone",
                expiresAt = null
            )
        }

        if (zone == Zone.YELLOW) {
            return Decision(
                rung = 1,
                action = "ALERT",
                autoFire = false,
                reason = "yellow zone",
                expiresAt = null
            )
        }

        return Decision(0, "WATCH", false, "green zone", null)
    }

    private fun writeDecision(ctx: Context, pkg: String, rec: AutonomousDecisionRecord) {
        try {
            EvidenceVault.get(ctx).add(
                sourceKind = "AUTONOMOUS_DECISION",
                origin = "RESPONSE_LADDER",
                authority = "SECURITY_META",
                packageName = pkg,
                uid = null,
                contentType = "application/json",
                content = rec.toJson(),
                verification = "SIGNED"
            )
        } catch (_: Throwable) { /* vault not ready */ }
    }
}