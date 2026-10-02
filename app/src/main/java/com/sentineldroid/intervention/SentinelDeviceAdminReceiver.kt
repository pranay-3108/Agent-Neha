package com.sentineldroid.intervention

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.sentineldroid.EvidenceVault

/**
 * Identity holder for DevicePolicyManager. Does nothing on its own.
 * Only exists so DPM has a ComponentName to check against.
 */
class SentinelDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        record(context, "DEVICE_ADMIN_ENABLED")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        record(context, "DEVICE_ADMIN_DISABLED")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling this admin will remove Neha's ability to neutralize threats."
    }

    private fun record(ctx: Context, kind: String) {
        try {
            EvidenceVault.get(ctx).add(
                sourceKind = kind,
                origin = "SENTINEL_DEVICE_ADMIN_RECEIVER",
                authority = "PLATFORM_DERIVED",
                packageName = ctx.packageName,
                uid = null,
                contentType = "text/plain",
                content = kind,
                verification = "SIGNED"
            )
        } catch (_: Throwable) { /* vault not ready */ }
    }
}