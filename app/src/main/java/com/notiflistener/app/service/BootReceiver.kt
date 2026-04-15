package com.notiflistener.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver untuk me-restart service setelah device reboot.
 * Memastikan NotifListenerService tetap berjalan setelah restart device.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d(TAG, "System event received (${intent.action}), requesting listener rebind")
            ListenerRecovery.requestRebindIfAllowed(context.applicationContext, intent.action ?: "system event")
        }
    }
}
