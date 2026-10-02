package com.sentineldroid.labtarget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.net.Socket

class LabForegroundService : Service() {
    private var socket: Socket? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Lab foreground service", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("System Monitor Lab")
            .setContentText("Synthetic lab foreground service is active")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(20, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(20, notification)
        Thread {
            runCatching {
                socket = Socket("10.0.2.2", 8765)
                socket?.getOutputStream()?.use { it.write("LAB_HEARTBEAT\n".toByteArray()) }
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { socket?.close(); socket = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object { private const val CHANNEL = "lab_service" }
}
