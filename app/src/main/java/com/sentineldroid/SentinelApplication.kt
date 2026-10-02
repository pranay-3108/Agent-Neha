package com.sentineldroid

import android.app.Application
import com.sentineldroid.intervention.InterventionRegistry
import com.sentineldroid.intervention.RealInterventionVerifier

class SentinelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        InterventionRegistry.install(verifier = RealInterventionVerifier(this))
        SniperPlugIn.installIfPresent(this)
    }
}
