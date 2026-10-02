package com.sentineldroid.intervention

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RevertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val shotId = intent.getStringExtra("shotId") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                InterventionRegistry.executor().revert(shotId)
            } finally {
                pending.finish()
            }
        }
    }
}