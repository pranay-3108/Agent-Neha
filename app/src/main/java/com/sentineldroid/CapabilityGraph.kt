package com.sentineldroid

object CapabilityGraph {
    fun summarize(pkg: PackageSnapshot, facts: List<CapabilityFact>): String {
        val own = facts.filter { it.packageName == pkg.packageName }
        val lines = mutableListOf<String>()
        lines += pkg.packageName
        lines += "  Identity: UID=${pkg.uid}, signer=${pkg.signerSha256.firstOrNull() ?: "unknown"}"
        lines += "  Launcher: ${if (pkg.launcherVisible) "visible" else "not exposed"}"
        lines += "  Enabled: ${pkg.enabled}"
        lines += "  Static APK signals: splits=${pkg.splitApkCount}, dex=${pkg.dexFileCount}, native_libs=${pkg.nativeLibFileCount}"
        if (pkg.services.isNotEmpty()) lines += "  Services: ${pkg.services.size}"
        declaredServiceCapabilities(pkg.services).forEach { lines += "    - declared service capability: $it" }
        if (pkg.receivers.isNotEmpty()) lines += "  Receivers: ${pkg.receivers.size}"
        if (pkg.providers.isNotEmpty()) lines += "  Providers: ${pkg.providers.size}"
        if (pkg.activities.isNotEmpty()) lines += "  Activities: ${pkg.activities.size}"
        if (own.isNotEmpty()) {
            lines += "  Platform capabilities:"
            own.forEach { lines += "    - ${it.capability}: ${it.state}" }
        }
        if (pkg.debuggable) lines += "  Build flag: DEBUGGABLE"
        if (pkg.testOnly) lines += "  Build flag: TEST_ONLY"
        if (pkg.systemApp) lines += "  Build flag: SYSTEM_APP"
        if (pkg.updatedSystemApp) lines += "  Build flag: UPDATED_SYSTEM_APP"
        return lines.joinToString("\n")
    }

    fun correlationFlags(pkg: PackageSnapshot, facts: List<CapabilityFact>): List<String> {
        val own = facts.filter { it.packageName == pkg.packageName }.map { it.capability to it.state }.toSet()
        val out = mutableListOf<String>()
        val declared = declaredServiceCapabilities(pkg.services)
        if ("ACCESSIBILITY" to "ENABLED" in own && declared.isNotEmpty()) out += "ACCESSIBILITY_PLUS_SERVICE"
        if ("ACCESSIBILITY" to "ENABLED" in own && "NOTIFICATION_LISTENER" to "ENABLED" in own) out += "UI_AND_NOTIFICATION_ACCESS"
        if ("BOOT_RECEIVER" to "DECLARED" in own) out += "BOOT_ENTRY_POINT"
        if ("CAMERA_PERMISSION" to "GRANTED" in own && "MICROPHONE_PERMISSION" to "GRANTED" in own) out += "CAMERA_AND_MIC_GRANTED"
        if ("DEVICE_OWNER" to "ACTIVE" in own) out += "DEVICE_OWNER_MANAGEMENT"
        if (declared.contains("VPN")) out += "VPN_SERVICE_DECLARED"
        if (declared.contains("INPUT_METHOD")) out += "INPUT_METHOD_DECLARED"
        if (declared.contains("QUICK_SETTINGS_TILE")) out += "QUICK_SETTINGS_TILE_DECLARED"
        if (declared.contains("AUTOFILL")) out += "AUTOFILL_DECLARED"
        if (declared.contains("WALLPAPER")) out += "WALLPAPER_DECLARED"
        if (declared.contains("CALL_SCREENING")) out += "CALL_SCREENING_DECLARED"
        if (declared.contains("IN_CALL_SERVICE")) out += "IN_CALL_SERVICE_DECLARED"
        if (declared.contains("DREAM")) out += "DREAM_DECLARED"
        if (declared.contains("CREDENTIAL_PROVIDER")) out += "CREDENTIAL_PROVIDER_DECLARED"
        return out.distinct()
    }

    private fun declaredServiceCapabilities(services: List<String>): Set<String> {
        val output = mutableSetOf<String>()
        services.forEach { service ->
            when {
                "BIND_ACCESSIBILITY_SERVICE" in service -> output += "ACCESSIBILITY"
                "BIND_NOTIFICATION_LISTENER_SERVICE" in service -> output += "NOTIFICATION_LISTENER"
                "BIND_VPN_SERVICE" in service -> output += "VPN"
                "BIND_INPUT_METHOD" in service -> output += "INPUT_METHOD"
                "BIND_QUICK_SETTINGS_TILE" in service -> output += "QUICK_SETTINGS_TILE"
                "BIND_AUTOFILL_SERVICE" in service -> output += "AUTOFILL"
                "BIND_WALLPAPER" in service -> output += "WALLPAPER"
                "BIND_SCREENING_SERVICE" in service -> output += "CALL_SCREENING"
                "BIND_INCALL_SERVICE" in service -> output += "IN_CALL_SERVICE"
                "BIND_DREAM_SERVICE" in service -> output += "DREAM"
                "BIND_CREDENTIAL_PROVIDER_SERVICE" in service -> output += "CREDENTIAL_PROVIDER"
            }
        }
        return output
    }
}
