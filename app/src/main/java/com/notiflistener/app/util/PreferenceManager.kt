package com.notiflistener.app.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

/**
 * Helper class untuk mengelola SharedPreferences.
 * Menyimpan konfigurasi webhook URL, API key, dan status listener.
 */
class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val appContext = context.applicationContext

    /** Webhook URL tujuan pengiriman data */
    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    /** API Key untuk autentikasi webhook (opsional) */
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    /** Status aktif/nonaktif listener */
    var isListenerEnabled: Boolean
        get() = prefs.getBoolean(KEY_LISTENER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LISTENER_ENABLED, value).apply()

    /** Apakah hanya mengirim notifikasi INCOMING (dana masuk) saja */
    var onlyIncoming: Boolean
        get() = prefs.getBoolean(KEY_ONLY_INCOMING, true)
        set(value) = prefs.edit().putBoolean(KEY_ONLY_INCOMING, value).apply()

    /** Auto retry webhook yang gagal */
    var autoRetry: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RETRY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RETRY, value).apply()

    /** Max retry count */
    var maxRetryCount: Int
        get() = prefs.getInt(KEY_MAX_RETRY, 3)
        set(value) = prefs.edit().putInt(KEY_MAX_RETRY, value).apply()

    /** Device ID unik (generated sekali saat pertama kali) */
    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                id = Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: java.util.UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    /** Apakah webhook URL sudah dikonfigurasi */
    val isWebhookConfigured: Boolean
        get() = webhookUrl.isNotBlank()

    /** Monitor DANA */
    var monitorDana: Boolean
        get() = prefs.getBoolean(KEY_MONITOR_DANA, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITOR_DANA, value).apply()

    /** Monitor BCA */
    var monitorBca: Boolean
        get() = prefs.getBoolean(KEY_MONITOR_BCA, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITOR_BCA, value).apply()

    /** Monitor BRI */
    var monitorBri: Boolean
        get() = prefs.getBoolean(KEY_MONITOR_BRI, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITOR_BRI, value).apply()

    /** Monitor BNI */
    var monitorBni: Boolean
        get() = prefs.getBoolean(KEY_MONITOR_BNI, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITOR_BNI, value).apply()

    /** Monitor Mandiri */
    var monitorMandiri: Boolean
        get() = prefs.getBoolean(KEY_MONITOR_MANDIRI, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITOR_MANDIRI, value).apply()

    /**
     * Mendapatkan daftar package names yang harus dimonitor.
     */
    fun getMonitoredPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        if (monitorDana) packages.add(PackageNames.DANA)
        if (monitorBca) {
            packages.add(PackageNames.BCA_MOBILE)
            packages.add(PackageNames.BCA_MYBCA)
        }
        if (monitorBri) packages.add(PackageNames.BRI_MOBILE)
        if (monitorBni) packages.add(PackageNames.BNI_MOBILE)
        if (monitorMandiri) packages.add(PackageNames.MANDIRI_ONLINE)
        return packages
    }

    companion object {
        private const val PREFS_NAME = "notiflistener_prefs"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_LISTENER_ENABLED = "listener_enabled"
        private const val KEY_ONLY_INCOMING = "only_incoming"
        private const val KEY_AUTO_RETRY = "auto_retry"
        private const val KEY_MAX_RETRY = "max_retry"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_MONITOR_DANA = "monitor_dana"
        private const val KEY_MONITOR_BCA = "monitor_bca"
        private const val KEY_MONITOR_BRI = "monitor_bri"
        private const val KEY_MONITOR_BNI = "monitor_bni"
        private const val KEY_MONITOR_MANDIRI = "monitor_mandiri"
    }
}

/**
 * Package names dari aplikasi-aplikasi yang didukung.
 */
object PackageNames {
    const val DANA = "id.dana"
    const val BCA_MOBILE = "com.bca.mobile"      // BCA Mobile (lama)
    const val BCA_MYBCA = "id.co.bca.mybca"       // myBCA (baru)
    const val BRI_MOBILE = "id.co.bri.brimo"      // BRImo
    const val BNI_MOBILE = "src.id.bni"            // BNI Mobile
    const val MANDIRI_ONLINE = "id.co.mandiri.android.livin" // Livin by Mandiri
}
