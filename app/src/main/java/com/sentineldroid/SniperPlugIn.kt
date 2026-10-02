package com.sentineldroid

import android.content.Context
import com.sentineldroid.intervention.InterventionRegistry
import com.sentineldroid.intervention.KeystoneAnalyzer
import com.sentineldroid.intervention.PrecisionIntervention
import com.sentineldroid.intervention.RealInterventionVerifier

/**
 * Base build: SNIPER_ENABLED=false → no-op.
 * Sniper build: SNIPER_ENABLED=true → installs planner + executor.
 *   The verifier is real in both builds (observation only).
 */
object SniperPlugIn {
    fun installIfPresent(ctx: Context) {
        // Always install the real verifier — it has no teeth.
        InterventionRegistry.install(
            verifier = RealInterventionVerifier(ctx)
        )

        // Only install the teeth when the sniper variant is built.
        if (!BuildConfig.SNIPER_ENABLED) return

        InterventionRegistry.install(
            planner = KeystoneAnalyzer(),
            executor = PrecisionIntervention(ctx)
        )
    }
}