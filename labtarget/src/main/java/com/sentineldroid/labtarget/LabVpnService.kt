package com.sentineldroid.labtarget

import android.content.Intent
import android.net.VpnService
import android.os.IBinder

class LabVpnService : VpnService() {
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
