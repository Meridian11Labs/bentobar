package com.meridian11.books.bentobar.items

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Notification icons' entry in Android's "Notification access" list, beside [MediaAccess] and under a name of its own,
 * so the user gives each its access by itself and Now playing's promise (no notifications at all) stays true. It asks
 * for every kind of notification, and Android does not bind it by itself: the item asks for it while it shows.
 *
 * Until the item is built this is only the entry: it posts nothing, and bound all the same it leaves at once.
 */
class NoticeAccess : NotificationListenerService() {
    override fun onListenerConnected() { runCatching { requestUnbind() } }

    companion object {
        fun component(context: Context) = ComponentName(context, NoticeAccess::class.java)

        /** Turned on in Android's Notification access list. Asks the system: not for every frame. */
        fun granted(context: Context): Boolean = runCatching {
            context.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(component(context)) == true
        }.getOrDefault(false)

        /** Android's page for this entry, or the whole list where a device has no such page. */
        fun openSettings(context: Context) {
            val own = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
            if (!Env.launch(own, quiet = true)) Env.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }
}
