package com.sentineldroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.graphics.drawable.Icon
import android.os.ParcelFileDescriptor
import android.content.pm.ServiceInfo

class BlockPackageVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        val target = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
        val global = intent?.getBooleanExtra(EXTRA_GLOBAL_LOCKDOWN, false) == true
        if (!global && target.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat(if (global) "Emergency data protection" else "Network isolation: $target")
        stopVpn()

        vpnInterface = runCatching {
            Builder()
                .setSession(if (global) "Agent Neha emergency data protection" else "Agent Neha - isolate $target")
                .setBlocking(true)
                .addAddress("10.231.0.2", 32)
                .addAddress("fd10:231::2", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .also { builder ->
                    if (global) builder.addDisallowedApplication(packageName)
                    else builder.addAllowedApplication(target!!)
                }
                .establish()
        }.getOrNull()

        EvidenceVault.get(this).add(
            sourceKind = "CONTAINMENT",
            origin = "SENTINEL_USER_AUTHORIZED",
            authority = "POLICY",
            packageName = target ?: "<all-other-apps>",
            uid = null,
            contentType = "text/plain",
            content = if (vpnInterface != null) {
                if (global) "Emergency network lockdown active; SentinelDroid excluded from VPN." else "Network isolation active for target package."
            } else {
                "VPN containment failed to establish."
            },
            verification = if (vpnInterface != null) "CONTAINMENT_ACTIVE" else "CONTAINMENT_FAILED"
        )
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
    }

    private fun startForegroundCompat(text: String) {
        createChannel()
        val stopIntent = Intent(this, BlockPackageVpnService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 501, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sentinel)
            .setContentTitle("Agent Neha containment")
            .setContentText(text)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_sentinel), "Stop", stopPending).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Agent Neha containment", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_GLOBAL_LOCKDOWN = "global_lockdown"
        const val ACTION_STOP = "com.sentineldroid.STOP_CONTAINMENT"
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sentinel_containment"
    }
}
