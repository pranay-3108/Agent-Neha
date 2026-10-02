package com.sentineldroid

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var cycle = 0

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            cycle++
            val currentCycle = cycle
            Thread { runOneCycle(currentCycle) }.start()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        handler.removeCallbacks(loop)
        handler.post(loop)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(loop)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runOneCycle(cycleNumber: Int) {
        val vault = EvidenceVault.get(this)
        UsageCollector(this).collectAndStore(vault)
        if (cycleNumber % 5 != 0) return

        val collector = PackageCollector(this)
        val packages = collector.scan(includeApkHash = false)
        val boot = collector.bootReceiverPackages()
        val rawFacts = AccessCollector(this).buildFacts(packages, boot)
        val facts = rawFacts.map { fact ->
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
            fact.copy(evidenceId = id)
        }

        CapabilityMonitor.observeAndReconcile(
            context = this,
            facts = facts,
            source = "FOREGROUND",
            notifyUser = true
        )
        PassiveNetworkObservation.collect(this)
        SelfDefense.audit(this)
        IntentSurfaceCollector(this).collect(vault)
        NetworkUsageCollector(this).collect(packages, vault)
        DeviceStateCollector(this).collect(vault)
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    private fun startForegroundCompat() {
        val notification = SentinelNotifications.buildMonitoringNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(2002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2002, notification)
        }
    }
}
