package com.sentineldroid

import android.content.Context

/**
 * Runs a complete SentinelDroid scan for the user-facing agent.
 *
 * The scan stays deterministic: collectors gather facts, the vault stores
 * evidence, and the capability monitor reconciles state. Neha only explains
 * the result after the scan finishes.
 */
class NehaScanController(private val context: Context) {

    interface Callback {
        fun onStep(text: String)
        fun onFinished(result: Result)
        fun onFailed(error: Throwable)
    }

    data class Result(
        val appCount: Int,
        val factCount: Int,
        val evidenceCount: Int,
        val green: Int,
        val yellow: Int,
        val orange: Int,
        val red: Int,
        val pendingAlerts: Int,
        val telemetry: TelemetryHealth.State
    )

    fun start(callback: Callback) {
        Thread {
            runCatching {
                runScan(callback)
            }.onSuccess {
                callback.onFinished(it)
            }.onFailure {
                callback.onFailed(it)
            }
        }.start()
    }

    private fun runScan(callback: Callback): Result {
        val vault = EvidenceVault.get(context)
        val collector = PackageCollector(context)

        callback.onStep("Checking device security")
        val packages = collector.scan(includeApkHash = true)

        callback.onStep("Scanning installed apps")
        val bootPackages = collector.bootReceiverPackages()
        val rawFacts = AccessCollector(context).buildFacts(packages, bootPackages)
        val facts = saveFacts(vault, rawFacts)

        callback.onStep("Sealing package evidence")
        packages.forEach { pkg ->
            vault.add(
                "PACKAGE_SNAPSHOT",
                "ANDROID_PACKAGE_MANAGER",
                "EVIDENCE",
                pkg.packageName,
                pkg.uid,
                "application/json",
                SnapshotJson.build(pkg),
                "PLATFORM_DERIVED"
            )
        }

        callback.onStep("Checking entry points and installs")
        EntryPointCollector(context).collect(packages, vault)
        InstallSessionCollector(context).collect(vault)
        IntentSurfaceCollector(context).collect(vault)

        callback.onStep("Checking usage and network activity")
        UsageCollector(context).collectAndStore(vault)
        NetworkUsageCollector(context).collect(packages, vault)
        RuntimeCollector(context).collectGlobal(vault)
        DeviceStateCollector(context).collect(vault)

        callback.onStep("Reconciling capability changes")
        CapabilityMonitor.observeAndReconcile(
            context = context,
            facts = facts,
            source = "NEHA_SCAN",
            notifyUser = true
        )

        val counts = countZones(packages, facts)
        val pending = context
            .getSharedPreferences("sentinel_alerts", Context.MODE_PRIVATE)
            .getInt("pending_count", 0)
        val telemetry = TelemetryHealth.read(context)

        context.getSharedPreferences("sentinel_scan", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_scan_at", System.currentTimeMillis())
            .putInt("last_app_count", packages.size)
            .apply()

        return Result(
            appCount = packages.size,
            factCount = facts.size,
            evidenceCount = vault.count(),
            green = counts[RiskZone.GREEN] ?: 0,
            yellow = counts[RiskZone.YELLOW] ?: 0,
            orange = counts[RiskZone.ORANGE] ?: 0,
            red = counts[RiskZone.RED] ?: 0,
            pendingAlerts = pending,
            telemetry = telemetry
        )
    }

    private fun saveFacts(vault: EvidenceVault, rawFacts: List<CapabilityFact>): List<CapabilityFact> {
        return rawFacts.map { fact ->
            val body = """{"package":"${esc(fact.packageName)}","capability":"${esc(fact.capability)}","state":"${esc(fact.state)}","source":"${esc(fact.source)}","observed_at":${fact.observedAt}}"""
            val evidenceId = vault.add(
                "CAPABILITY_FACT",
                fact.source,
                "PLATFORM_FACT",
                fact.packageName.takeUnless { it == "<device>" },
                null,
                "application/json",
                body,
                "PLATFORM_DERIVED"
            )
            fact.copy(evidenceId = evidenceId)
        }
    }

    private fun countZones(packages: List<PackageSnapshot>, facts: List<CapabilityFact>): Map<RiskZone, Int> {
        val result = mutableMapOf(
            RiskZone.GREEN to 0,
            RiskZone.YELLOW to 0,
            RiskZone.ORANGE to 0,
            RiskZone.RED to 0
        )

        for (pkg in packages) {
            val zone = CapabilityRiskClassifier.classify(
                facts.filter { it.packageName == pkg.packageName }
            )
            result[zone] = (result[zone] ?: 0) + 1
        }

        return result
    }

    private fun esc(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
