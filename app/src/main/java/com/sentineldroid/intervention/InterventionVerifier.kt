package com.sentineldroid.intervention

interface InterventionVerifier {
    suspend fun isCapabilityActive(packageName: String, capability: String): Boolean
    suspend fun waitForSettledState(packageName: String, capability: String): Boolean
}

class NoOpInterventionVerifier : InterventionVerifier {
    override suspend fun isCapabilityActive(p: String, c: String) = false
    override suspend fun waitForSettledState(p: String, c: String) = false
}

class RealInterventionVerifier(private val ctx: android.content.Context) : InterventionVerifier {
    override suspend fun isCapabilityActive(packageName: String, capability: String): Boolean {
        val pkg = com.sentineldroid.PackageCollector(ctx).scanPackage(packageName, false) ?: return false
        val facts = com.sentineldroid.AccessCollector(ctx).buildFacts(listOf(pkg), com.sentineldroid.PackageCollector(ctx).bootReceiverPackages())
        return facts.any { it.packageName == packageName && it.capability == capability && com.sentineldroid.CapabilityRiskClassifier.isActive(it.state) }
    }

    override suspend fun waitForSettledState(packageName: String, capability: String): Boolean {
        val deadline = System.currentTimeMillis() + 2_000L
        var last = false
        while (System.currentTimeMillis() < deadline) {
            last = isCapabilityActive(packageName, capability)
            if (last) return true
            Thread.sleep(200L)
        }
        return last
    }
}
