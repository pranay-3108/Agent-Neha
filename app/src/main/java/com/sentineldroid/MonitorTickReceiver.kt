package com.sentineldroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MonitorTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != MonitorScheduler.ACTION_TICK) return
        val enabled = context.getSharedPreferences("sentinel_runtime", Context.MODE_PRIVATE).getBoolean("background_monitoring", false)
        if (!enabled) return
        val pending = goAsync()
        Thread {
            try {
                val collector = PackageCollector(context)
                val packages = collector.scan(false)
                val boot = collector.bootReceiverPackages()
                val facts = AccessCollector(context).buildFacts(packages, boot).map { persistFact(context, it) }
                CapabilityMonitor.observeAndReconcile(
                    context = context,
                    facts = facts,
                    source = "TICK",
                    notifyUser = true
                )
                packages.forEach { pkg ->
                    BehavioralBaselineStore.add(context, BehavioralBaselineStore.Sample(System.currentTimeMillis(), 0L, 0L, 0.0, 0, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), emptySet()))
                }
                InstallSessionCollector(context).collect(EvidenceVault.get(context))
                IntentSurfaceCollector(context).collect(EvidenceVault.get(context))
                NetworkUsageCollector(context).collect(packages, EvidenceVault.get(context))
                PassiveNetworkObservation.collect(context)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun persistFact(context: Context, fact: CapabilityFact): CapabilityFact {
        val id = EvidenceVault.get(context).add("CAPABILITY_FACT", fact.source, "PLATFORM_FACT", fact.packageName.takeUnless { it == "<device>" }, null, "application/json", "{\"package\":\"${escape(fact.packageName)}\",\"capability\":\"${escape(fact.capability)}\",\"state\":\"${escape(fact.state)}\",\"source\":\"${escape(fact.source)}\",\"observed_at\":${fact.observedAt}}", "PLATFORM_DERIVED")
        return fact.copy(evidenceId = id)
    }
    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
