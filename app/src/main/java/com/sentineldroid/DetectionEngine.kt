package com.sentineldroid

object DetectionEngine {
    data class Assessment(
        val packageName: String,
        val labels: List<String>,
        val facts: List<CapabilityFact>,
        val limitations: List<String>
    )

    fun assess(pkg: PackageSnapshot, facts: List<CapabilityFact>): Assessment {
        val own = facts.filter { it.packageName == pkg.packageName }
        val labels = CapabilityGraph.correlationFlags(pkg, own)
        val limitations = mutableListOf<String>()
        if (pkg.services.isNotEmpty()) limitations += "Declared services are visible; actual runtime lifetime is observed separately."
        if (pkg.receivers.isNotEmpty()) limitations += "Declared receivers are visible; dynamically registered receivers are not fully enumerable by a normal app."
        limitations += "Private sandbox files of other apps are not accessible in standard app-only mode."
        limitations += "No claim of plaintext HTTPS inspection is made from network statistics alone."
        return Assessment(pkg.packageName, labels, own, limitations)
    }
}
