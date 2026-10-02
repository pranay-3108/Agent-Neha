package com.sentineldroid.labtarget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LabBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.getSharedPreferences("lab", Context.MODE_PRIVATE).edit().putLong("last_boot", System.currentTimeMillis()).apply()
        }
    }
}
