package com.sentineldroid.intervention

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.sentineldroid.EvidenceVault
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Fires ONE reversible shot at ONE capability.
 * If Device Owner is not provisioned, refuses cleanly and lets the
 * caller fall back to guiding the user.
 */
class PrecisionIntervention(private val ctx: Context) : InterventionExecutor {

    private val dpm: DevicePolicyManager? =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private val admin = ComponentName(ctx, SentinelDeviceAdminReceiver::class.java)

    private val store: SharedPreferences =
        ctx.getSharedPreferences("sentinel_shots", Context.MODE_PRIVATE)

    override fun isCapable(): Boolean {
        val d = dpm ?: return false
        return d.isDeviceOwnerApp(ctx.packageName) || d.isProfileOwnerApp(ctx.packageName)
    }

    override suspend fun fire(request: InterventionExecutor.ShotRequest)
        : InterventionExecutor.ShotResult {

        val shotId = UUID.randomUUID().toString().replace("-", "").take(16)

        record(
            kind = "PRECISION_SHOT_INTENT",
            pkg = request.packageName,
            json = """{"shot_id":"$shotId","target":"${request.packageName}","capability":"${request.capability}","reason":"${request.reason.replace("\"","\\\"")}","authorized_by":"${request.authorizedBy}","expires_at":${request.expiresAt ?: 0}}"""
        )

        if (!isCapable()) {
            val r = InterventionExecutor.ShotResult(shotId, false, false, "NO_DEVICE_OWNER")
            recordResult(shotId, request.packageName, r)
            return r
        }

        val preState = capturePreState(request.packageName, request.capability)
        store.edit().putString(shotId, encodeShot(request, preState)).apply()

        val fired = when (request.capability.uppercase()) {
            "ACCESSIBILITY"         -> revokeAccessibility(request.packageName)
            "NOTIFICATION_LISTENER" -> revokeNotificationListener(request.packageName)
            "CAMERA"                -> revokePermission(request.packageName, "android.permission.CAMERA")
            "OVERLAY"               -> revokePermission(request.packageName, "android.permission.SYSTEM_ALERT_WINDOW")
            "MEDIA_PROJECTION"      -> suspendPackage(request.packageName)
            "FOREGROUND_SERVICE"    -> suspendPackage(request.packageName)
            "MICROPHONE"            -> revokePermission(request.packageName, "android.permission.RECORD_AUDIO")
            else                    -> false
        }

        delay(700)

        val verifier = InterventionRegistry.verifier()
        val stillActive = verifier.isCapabilityActive(request.packageName, request.capability)
        val verified = !stillActive

        val result = InterventionExecutor.ShotResult(
            shotId = shotId,
            fired = fired,
            verified = verified,
            failureReason = when {
                !fired    -> "EXECUTION_FAILED"
                !verified -> "STILL_ACTIVE_AFTER_SHOT"
                else      -> null
            }
        )

        recordResult(shotId, request.packageName, result)

        request.expiresAt?.let { expires ->
            AutoRevertScheduler.schedule(ctx, shotId, request.packageName, request.capability, expires)
        }

        return result
    }

    override suspend fun revert(shotId: String): InterventionExecutor.RevertResult {
        val encoded = store.getString(shotId, null)
            ?: return InterventionExecutor.RevertResult(false, "SHOT_RECORD_NOT_FOUND")

        val decoded = decodeShot(encoded)
        if (!isCapable()) {
            return InterventionExecutor.RevertResult(false, "NO_DEVICE_OWNER")
        }

        val reverted = when (decoded.capability.uppercase()) {
            "ACCESSIBILITY"         -> restoreAccessibility(decoded.packageName, decoded.preState)
            "NOTIFICATION_LISTENER" -> restoreNotificationListener(decoded.packageName, decoded.preState)
            "CAMERA"                -> restorePermission(decoded.packageName, "android.permission.CAMERA")
            "OVERLAY"               -> restorePermission(decoded.packageName, "android.permission.SYSTEM_ALERT_WINDOW")
            "MICROPHONE"            -> restorePermission(decoded.packageName, "android.permission.RECORD_AUDIO")
            "MEDIA_PROJECTION"      -> unsuspendPackage(decoded.packageName)
            "FOREGROUND_SERVICE"    -> unsuspendPackage(decoded.packageName)
            else                    -> false
        }

        record(
            kind = "PRECISION_SHOT_REVERTED",
            pkg = decoded.packageName,
            json = """{"shot_id":"$shotId","reverted":$reverted}"""
        )

        return InterventionExecutor.RevertResult(
            reverted = reverted,
            failureReason = if (reverted) null else "REVERT_FAILED"
        )
    }

    private fun revokePermission(pkg: String, permission: String): Boolean = try {
        dpm!!.setPermissionGrantState(
            admin, pkg, permission,
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        ) == DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
    } catch (_: Throwable) { false }

    private fun restorePermission(pkg: String, permission: String): Boolean = try {
        dpm!!.setPermissionGrantState(
            admin, pkg, permission,
            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        ) == DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
    } catch (_: Throwable) { false }

    private fun revokeAccessibility(pkg: String): Boolean = try {
        val current = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val filtered = current.split(':').filterNot { it.startsWith("$pkg/") }.joinToString(":")
        dpm!!.setSecureSetting(
            admin, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, filtered
        )
        true
    } catch (_: Throwable) { false }

    private fun restoreAccessibility(pkg: String, preState: String): Boolean = try {
        if (preState.isBlank()) return false
        dpm!!.setSecureSetting(
            admin, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, preState
        )
        true
    } catch (_: Throwable) { false }

    private fun revokeNotificationListener(pkg: String): Boolean = try {
        val current = Settings.Secure.getString(
            ctx.contentResolver, "enabled_notification_listeners"
        ).orEmpty()
        val filtered = current.split(':').filterNot { it.startsWith("$pkg/") }.joinToString(":")
        dpm!!.setSecureSetting(admin, "enabled_notification_listeners", filtered)
        true
    } catch (_: Throwable) { false }

    private fun restoreNotificationListener(pkg: String, preState: String): Boolean = try {
        if (preState.isBlank()) return false
        dpm!!.setSecureSetting(admin, "enabled_notification_listeners", preState)
        true
    } catch (_: Throwable) { false }

    private fun suspendPackage(pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dpm!!.setPackagesSuspended(admin, arrayOf(pkg), true) != null
        } else false
    } catch (_: Throwable) { false }

    private fun unsuspendPackage(pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dpm!!.setPackagesSuspended(admin, arrayOf(pkg), false) != null
        } else false
    } catch (_: Throwable) { false }

    private fun capturePreState(pkg: String, capability: String): String = when (capability.uppercase()) {
        "ACCESSIBILITY" -> Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        "NOTIFICATION_LISTENER" -> Settings.Secure.getString(
            ctx.contentResolver, "enabled_notification_listeners"
        ).orEmpty()
        else -> ""
    }

    private data class ShotRecord(
        val packageName: String,
        val capability: String,
        val preState: String,
        val firedAt: Long
    )

    private fun encodeShot(req: InterventionExecutor.ShotRequest, preState: String): String =
        listOf(req.packageName, req.capability, preState, System.currentTimeMillis().toString())
            .joinToString("|")

    private fun decodeShot(encoded: String): ShotRecord {
        val parts = encoded.split("|")
        return ShotRecord(
            packageName = parts.getOrElse(0) { "" },
            capability = parts.getOrElse(1) { "" },
            preState = parts.getOrElse(2) { "" },
            firedAt = parts.getOrElse(3) { "0" }.toLongOrNull() ?: 0L
        )
    }

    private fun record(kind: String, pkg: String, json: String) {
        try {
            EvidenceVault.get(ctx).add(
                sourceKind = kind,
                origin = "PRECISION_INTERVENTION",
                authority = "SECURITY_META",
                packageName = pkg,
                uid = null,
                contentType = "application/json",
                content = json,
                verification = "SIGNED"
            )
        } catch (_: Throwable) { /* vault not ready */ }
    }

    private fun recordResult(shotId: String, pkg: String, r: InterventionExecutor.ShotResult) {
        record(
            kind = "PRECISION_SHOT_RESULT",
            pkg = pkg,
            json = """{"shot_id":"$shotId","fired":${r.fired},"verified":${r.verified},"failure_reason":${r.failureReason?.let { "\"$it\"" } ?: "null"}}"""
        )
    }
}