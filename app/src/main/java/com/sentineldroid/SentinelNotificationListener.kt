package com.sentineldroid

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SentinelNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        EvidenceFirewall(EvidenceVault(this)).ingest(
            "NOTIFICATION", sbn.packageName, sbn.uid, "text/plain", "title=$title\ntext=$text"
        )
    }
}
