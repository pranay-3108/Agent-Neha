package com.sentineldroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return

        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val event = when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> if (replacing) "PACKAGE_ADDED_REPLACING" else "PACKAGE_ADDED"
            Intent.ACTION_PACKAGE_REPLACED -> "PACKAGE_REPLACED"
            Intent.ACTION_PACKAGE_REMOVED -> if (replacing) "PACKAGE_REMOVED_REPLACING" else "PACKAGE_REMOVED"
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> "PACKAGE_FULLY_REMOVED"
            else -> return
        }

        val pending = goAsync()
        Thread {
            try {
                val vault = EvidenceVault.get(context)
                vault.add(
                    "PACKAGE_CHANGE",
                    "ANDROID_PACKAGE_MANAGER",
                    "EVIDENCE",
                    packageName,
                    null,
                    "application/json",
                    "{\"event\":\"$event\",\"package\":\"${escape(packageName)}\",\"replacing\":$replacing}",
                    "PLATFORM_DERIVED"
                )

                if (event == "PACKAGE_FULLY_REMOVED") {
                    // Removal is retained as a shadow record when a watched app disappears.
                    WatchdogRegistry.contracts(context).firstOrNull { it.packageName == packageName }?.let { contract ->
                        EvidenceVault.get(context).add("SHADOW_RECORD", "PACKAGE_CHANGE", "SECURITY_META", packageName, null, "application/json", org.json.JSONObject().put("packageName", contract.packageName).put("signerSha256", contract.signerSha256).put("uninstalledAt", System.currentTimeMillis()).toString(), "UNINSTALL_SHADOW")
                    }
                }
                if (event == "PACKAGE_ADDED" || event == "PACKAGE_REPLACED") {
                    PackageCollector(context).scanPackage(packageName, true)?.let { snapshot ->
                        vault.add(
                            "PACKAGE_SNAPSHOT",
                            "ANDROID_PACKAGE_MANAGER",
                            "EVIDENCE",
                            snapshot.packageName,
                            snapshot.uid,
                            "application/json",
                            SnapshotJson.build(snapshot),
                            "PLATFORM_DERIVED"
                        )

                        val boot = PackageCollector(context).bootReceiverPackages()
                        val rawFacts = AccessCollector(context).buildFacts(listOf(snapshot), boot)
                        val facts = rawFacts.map { persistFact(vault, it) }
                        val source = when {
                            intent.action == Intent.ACTION_PACKAGE_REPLACED -> "PACKAGE_REPLACE"
                            intent.action == Intent.ACTION_PACKAGE_ADDED && replacing -> "PACKAGE_REPLACE"
                            else -> "PACKAGE_ADD"
                        }
                        CapabilityMonitor.observeAndReconcile(
                            context = context,
                            facts = facts,
                            source = source,
                            notifyUser = true
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun persistFact(vault: EvidenceVault, fact: CapabilityFact): CapabilityFact {
        val id = vault.add(
            "CAPABILITY_FACT",
            fact.source,
            "PLATFORM_FACT",
            fact.packageName.takeUnless { it == "<device>" },
            null,
            "application/json",
            "{\"package\":\"${escape(fact.packageName)}\",\"capability\":\"${escape(fact.capability)}\",\"state\":\"${escape(fact.state)}\",\"source\":\"${escape(fact.source)}\",\"observed_at\":${fact.observedAt}}",
            "PLATFORM_DERIVED"
        )
        return fact.copy(evidenceId = id)
    }

    private fun escape(value: String) = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}

object SnapshotJson {
    fun build(pkg: PackageSnapshot): String {
        fun arr(values: List<String>) = values.joinToString(",") { "\"${escape(it)}\"" }
        return "{" +
            "\"package\":\"${escape(pkg.packageName)}\"," +
            "\"uid\":${pkg.uid}," +
            "\"label\":\"${escape(pkg.appLabel)}\"," +
            "\"version\":\"${escape(pkg.versionName)}\"," +
            "\"version_code\":${pkg.versionCode}," +
            "\"apk_sha256\":\"${escape(pkg.apkSha256 ?: "unknown")}\"," +
            "\"signers\":[${arr(pkg.signerSha256)}]," +
            "\"enabled\":${pkg.enabled}," +
            "\"launcher_visible\":${pkg.launcherVisible}," +
            "\"debuggable\":${pkg.debuggable}," +
            "\"system_app\":${pkg.systemApp}," +
            "\"updated_system_app\":${pkg.updatedSystemApp}," +
            "\"test_only\":${pkg.testOnly}," +
            "\"direct_boot_aware\":${pkg.directBootAware}," +
            "\"process\":\"${escape(pkg.processName.orEmpty())}\"," +
            "\"data_dir\":\"${escape(pkg.dataDir.orEmpty())}\"," +
            "\"native_library_dir\":\"${escape(pkg.nativeLibraryDir.orEmpty())}\"," +
            "\"split_apk_count\":${pkg.splitApkCount}," +
            "\"dex_file_count\":${pkg.dexFileCount}," +
            "\"native_lib_file_count\":${pkg.nativeLibFileCount}," +
            "\"requested_permissions\":[${arr(pkg.requestedPermissions)}]," +
            "\"granted_permissions\":[${arr(pkg.grantedPermissions)}]," +
            "\"installer\":\"${escape(pkg.installerPackage.orEmpty())}\"," +
            "\"initiating_package\":\"${escape(pkg.initiatingPackage.orEmpty())}\"," +
            "\"originating_package\":\"${escape(pkg.originatingPackage.orEmpty())}\"," +
            "\"permissions\":[${arr(pkg.permissions)}]," +
            "\"services\":[${arr(pkg.services)}]," +
            "\"receivers\":[${arr(pkg.receivers)}]," +
            "\"providers\":[${arr(pkg.providers)}]," +
            "\"activities\":[${arr(pkg.activities)}]" +
            "}"
    }

    private fun escape(value: String) = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
