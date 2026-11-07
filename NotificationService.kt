package com.slate.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val map = HashMap<String, Int>()
        try {
            for (n in activeNotifications ?: emptyArray()) {
                if (n.isOngoing || n.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
                map[n.packageName] = (map[n.packageName] ?: 0) + 1
            }
        } catch (_: Exception) { }
        NotificationState.update(map)
    }

    override fun onListenerDisconnected() {
        NotificationState.update(emptyMap())
    }
}
