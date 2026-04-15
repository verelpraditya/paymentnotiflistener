package com.notiflistener.app.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Helper untuk memulihkan koneksi NotificationListenerService saat OEM/system
 * memutus listener setelah app di-swipe atau process di-kill.
 */
object ListenerRecovery {

    private const val TAG = "ListenerRecovery"

    fun isNotificationAccessGranted(context: Context): Boolean {
        val componentName = ComponentName(context, NotifListenerService::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        return enabledListeners.contains(componentName.flattenToString())
    }

    fun requestRebindIfAllowed(context: Context, reason: String) {
        if (!isNotificationAccessGranted(context)) {
            Log.d(TAG, "Skip rebind ($reason): notification access not granted")
            return
        }

        val componentName = ComponentName(context, NotifListenerService::class.java)

        try {
            NotificationListenerService.requestRebind(componentName)
            Log.i(TAG, "Requested listener rebind: $reason")
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind failed, toggling component: ${e.message}", e)

            try {
                val packageManager = context.packageManager
                packageManager.setComponentEnabledSetting(
                    componentName,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                packageManager.setComponentEnabledSetting(
                    componentName,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )

                NotificationListenerService.requestRebind(componentName)
                Log.i(TAG, "Listener component toggled and rebind requested: $reason")
            } catch (toggleException: Exception) {
                Log.e(TAG, "Failed to recover listener: ${toggleException.message}", toggleException)
            }
        }
    }
}
