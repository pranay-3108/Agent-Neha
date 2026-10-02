package com.sentineldroid

import android.content.Context
import org.json.JSONObject

/**
 * Single capability observation/reconciliation gate.
 * Every runtime path must enter through observeAndReconcile().
 */
object CapabilityMonitor {
    private const val PREFS = "sentinel_capability_baselines"
    private const val KEY_PREFIX = "package_"
    private const val INITIALIZED = "initialized"

    private val important = setOf(
        "ACCESSIBILITY", "NOTIFICATION_LISTENER", "DEVICE_ADMIN", "DEVICE_OWNER", "PROFILE_OWNER", "USAGE_ACCESS",
        "OVERLAY_ACCESS", "INPUT_METHOD", "VPN_SERVICE", "FOREGROUND_SERVICE", "ALL_FILES_ACCESS", "BOOT_RECEIVER",
        "CAMERA_PERMISSION", "MICROPHONE_PERMISSION", "LOCATION_PERMISSION", "CONTACTS_PERMISSION", "CALL_LOG_PERMISSION",
        "SMS_PERMISSION", "PHONE_PERMISSION", "MEDIA_PERMISSION"
    )

    data class Change(
        val packageName: String,
        val appLabel: String,
        val changes: List<String>
    )

    data class ReconcileResult(
        val changes: List<Change>,
        val initialAudit: Boolean
    )

    /**
     * The only supported way to reconcile capability state.
     *
     * source examples: TICK, FOREGROUND, PACKAGE_ADD, PACKAGE_REPLACE, BOOT, COLD_START
     */
    @Synchronized
    fun observeAndReconcile(
        context: Context,
        facts: List<CapabilityFact>,
        source: String,
        notifyUser: Boolean = true
    ): ReconcileResult {
        val packages = facts
            .map { it.packageName }
            .filter { it != "<device>" }
            .toSet()

        if (packages.isEmpty()) {
            return ReconcileResult(emptyList(), isInitialized(context))
        }

        val packageRows = PackageCollector(context).scanPackageRows(packages)
        return reconcileInternal(context, packageRows, facts, source, notifyUser)
    }

    private fun reconcileInternal(
        context: Context,
        packages: List<PackageSnapshot>,
        facts: List<CapabilityFact>,
        source: String,
        notifyUser: Boolean
    ): ReconcileResult {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val initialized = prefs.getBoolean(INITIALIZED, false)
        val changes = mutableListOf<Change>()

        if (!initialized) {
            val now = System.currentTimeMillis()
            packages.forEach { pkg ->
                val currentFacts = factsFor(pkg.packageName, facts)
                val current = fingerprint(currentFacts)
                val zone = CapabilityRiskClassifier.classify(currentFacts)

                if (zone == RiskZone.ORANGE || zone == RiskZone.RED) {
                    EvidenceVault.get(context).add(
                        "COLD_START_AUDIT",
                        "CAPABILITY_MONITOR",
                        "SECURITY_META",
                        pkg.packageName,
                        pkg.uid,
                        "application/json",
                        JSONObject()
                            .put("zone", zone.name)
                            .put("capabilities", currentFacts.map { it.capability })
                            .put("source", source)
                            .put("observedAt", now)
                            .toString(),
                        "NEWLY_DISCOVERED"
                    )
                    WatchdogRegistry.enroll(
                        context,
                        WatchdogRegistry.Contract(
                            pkg.packageName,
                            pkg.signerSha256.joinToString(","),
                            now,
                            "COLD_START_DANGEROUS_COMBINATION",
                            zone
                        )
                    )
                }

                // Seal the baseline first. Cache is written only after the vault record succeeds.
                writeBaseline(context, pkg, current, now, "INITIAL")
                prefs.edit().putString(KEY_PREFIX + pkg.packageName, current).apply()
            }
            prefs.edit().putBoolean(INITIALIZED, true).apply()
            return ReconcileResult(emptyList(), true)
        }

        packages.forEach { pkg ->
            val currentFacts = factsFor(pkg.packageName, facts)
            val current = fingerprint(currentFacts)
            val previous = prefs.getString(KEY_PREFIX + pkg.packageName, null)

            if (previous == null) {
                writeBaseline(context, pkg, current, System.currentTimeMillis(), "NEW_PACKAGE")
                prefs.edit().putString(KEY_PREFIX + pkg.packageName, current).apply()

                if (notifyUser) {
                    SentinelNotifications.postNewApp(
                        context,
                        pkg.packageName,
                        pkg.appLabel,
                        activeCapabilities(currentFacts)
                    )
                }
                return@forEach
            }

            if (previous == current) return@forEach

            val difference = describeDifference(
                parseFingerprint(previous),
                parseFingerprint(current)
            )
            if (difference.isEmpty()) return@forEach

            if (!EvidenceFloodGuard.allow(context, pkg.packageName)) return@forEach

            val now = System.currentTimeMillis()
            val change = Change(pkg.packageName, pkg.appLabel, difference)
            changes += change

            // Security metadata/scar are sealed before the cache baseline is replaced.
            val changeId = EvidenceVault.get(context).add(
                "CAPABILITY_CHANGE",
                "CAPABILITY_MONITOR",
                "SECURITY_META",
                pkg.packageName,
                pkg.uid,
                "application/json",
                JSONObject()
                    .put("source", source)
                    .put("changes", difference)
                    .put("observedAt", now)
                    .toString(),
                "SEALED_BEFORE_BASELINE_UPDATE"
            )

            EvidenceVault.get(context).add(
                "CAPABILITY_SCAR",
                "CAPABILITY_MONITOR",
                "SECURITY_META",
                pkg.packageName,
                pkg.uid,
                "application/json",
                JSONObject()
                    .put("changes", difference)
                    .put("observedBy", source)
                    .put("observedAt", now)
                    .put("linkedChange", changeId)
                    .toString(),
                "IMMUTABLE_SCAR"
            )

            val zone = CapabilityRiskClassifier.classify(currentFacts)
            if (zone == RiskZone.ORANGE || zone == RiskZone.RED) {
                WatchdogRegistry.enroll(
                    context,
                    WatchdogRegistry.Contract(
                        pkg.packageName,
                        pkg.signerSha256.joinToString(","),
                        now,
                        "CAPABILITY_ESCALATION",
                        zone
                    )
                )
            }

            writeBaseline(context, pkg, current, now, source)
            prefs.edit().putString(KEY_PREFIX + pkg.packageName, current).apply()

            if (notifyUser) {
                SentinelNotifications.postCapabilityChange(
                    context,
                    pkg.packageName,
                    pkg.appLabel,
                    difference
                )
            }
        }

        return ReconcileResult(changes, false)
    }

    private fun writeBaseline(
        context: Context,
        pkg: PackageSnapshot,
        fingerprint: String,
        now: Long,
        source: String
    ) {
        EvidenceVault.get(context).add(
            "CAPABILITY_BASELINE",
            "CAPABILITY_MONITOR",
            "SECURITY_META",
            pkg.packageName,
            pkg.uid,
            "application/json",
            JSONObject()
                .put("package", pkg.packageName)
                .put("fingerprint", fingerprint)
                .put("source", source)
                .put("observedAt", now)
                .toString(),
            "SIGNED_BASELINE"
        )
    }

    private fun isInitialized(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(INITIALIZED, false)

    private fun factsFor(packageName: String, facts: List<CapabilityFact>): List<CapabilityFact> =
        facts.filter { it.packageName == packageName && it.capability in important }
            .sortedBy { it.capability }

    private fun activeCapabilities(facts: List<CapabilityFact>): List<String> =
        facts.filter { CapabilityRiskClassifier.isActive(it.state) }
            .map { friendly(it.capability) }

    private fun fingerprint(facts: List<CapabilityFact>): String =
        facts.joinToString("|") { "${it.capability}=${it.state}" }

    private fun parseFingerprint(value: String): Map<String, String> =
        if (value.isBlank()) {
            emptyMap()
        } else {
            value.split('|').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null
                else part.substring(0, index) to part.substring(index + 1)
            }.toMap()
        }

    private fun describeDifference(
        old: Map<String, String>,
        new: Map<String, String>
    ): List<String> =
        (old.keys + new.keys).toSortedSet().mapNotNull { key ->
            val before = old[key]
            val after = new[key]
            if (before == after) {
                null
            } else {
                when {
                    before == null -> "${friendly(key)} ${after?.lowercase()}"
                    after == null -> "${friendly(key)} removed"
                    else -> "${friendly(key)} ${before.lowercase()} → ${after.lowercase()}"
                }
            }
        }

    private fun friendly(value: String): String = when (value) {
        "ACCESSIBILITY" -> "Accessibility"
        "NOTIFICATION_LISTENER" -> "Notification access"
        "DEVICE_ADMIN" -> "Device admin"
        "DEVICE_OWNER" -> "Device owner"
        "PROFILE_OWNER" -> "Profile owner"
        "USAGE_ACCESS" -> "Usage access"
        "OVERLAY_ACCESS" -> "Overlay access"
        "INPUT_METHOD" -> "Input method"
        "VPN_SERVICE" -> "VPN service"
        "FOREGROUND_SERVICE" -> "Foreground service"
        "ALL_FILES_ACCESS" -> "All files access"
        "BOOT_RECEIVER" -> "Boot persistence"
        "CAMERA_PERMISSION" -> "Camera"
        "MICROPHONE_PERMISSION" -> "Microphone"
        "LOCATION_PERMISSION" -> "Location"
        "CONTACTS_PERMISSION" -> "Contacts"
        "CALL_LOG_PERMISSION" -> "Call log"
        "SMS_PERMISSION" -> "SMS"
        "PHONE_PERMISSION" -> "Phone"
        "MEDIA_PERMISSION" -> "Media"
        else -> value
    }
}
