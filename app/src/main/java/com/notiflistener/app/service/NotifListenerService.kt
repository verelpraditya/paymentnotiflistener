package com.notiflistener.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.notiflistener.app.MainActivity
import com.notiflistener.app.R
import com.notiflistener.app.data.AppDatabase
import com.notiflistener.app.data.TransactionLog
import com.notiflistener.app.model.PaymentNotification
import com.notiflistener.app.model.TransactionType
import com.notiflistener.app.parser.ParserRegistry
import com.notiflistener.app.util.PreferenceManager
import com.notiflistener.app.webhook.WebhookSender
import kotlinx.coroutines.*

/**
 * Service utama yang mendengarkan notifikasi dari aplikasi lain.
 *
 * Flow:
 * 1. Notifikasi masuk dari aplikasi (DANA, BCA, BRI, dll)
 * 2. Service menangkap notifikasi via onNotificationPosted()
 * 3. Filter berdasarkan package name yang dimonitor
 * 4. Parse notifikasi untuk mengekstrak informasi transaksi
 * 5. Simpan ke database lokal
 * 6. Kirim ke webhook server
 * 7. Update status pengiriman di database
 */
class NotifListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListenerService"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "notiflistener_service"
        private const val CHANNEL_NAME = "Payment Listener Service"

        /** Broadcast action untuk notify UI tentang transaksi baru */
        const val ACTION_NEW_TRANSACTION = "com.notiflistener.app.NEW_TRANSACTION"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
    }

    private lateinit var prefManager: PreferenceManager
    private lateinit var database: AppDatabase
    private lateinit var webhookSender: WebhookSender
    private val parserRegistry = ParserRegistry()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        prefManager = PreferenceManager(this)
        database = AppDatabase.getInstance(this)
        webhookSender = WebhookSender()

        startForegroundService()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        webhookSender.shutdown()
        super.onDestroy()
    }

    /**
     * Callback utama: dipanggil setiap kali ada notifikasi baru yang di-post.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Cek apakah listener aktif
        if (!prefManager.isListenerEnabled) return

        val packageName = sbn.packageName ?: return

        // Cek apakah package name ada di daftar monitor
        val monitoredPackages = prefManager.getMonitoredPackages()
        if (packageName !in monitoredPackages) return

        // Cek apakah parser tersedia
        if (!parserRegistry.isSupported(packageName)) return

        // Ambil konten notifikasi
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        // Preferensi: gunakan BIG_TEXT karena biasanya lebih lengkap
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        Log.d(TAG, "Notification from $packageName - Title: $title - Text: $text")

        // Parse notifikasi
        val parser = parserRegistry.findParser(packageName) ?: return
        val paymentNotification = parser.parse(
            packageName = packageName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        if (paymentNotification == null) {
            Log.d(TAG, "Notification from $packageName not relevant (could not parse)")
            return
        }

        // Filter hanya INCOMING jika opsi aktif
        if (prefManager.onlyIncoming && paymentNotification.type != TransactionType.INCOMING) {
            Log.d(TAG, "Skipping non-incoming transaction: ${paymentNotification.type}")
            return
        }

        Log.i(TAG, "Payment detected: ${paymentNotification.source} - ${paymentNotification.amount} - ${paymentNotification.type}")

        // Proses di background
        serviceScope.launch {
            processPaymentNotification(paymentNotification)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Tidak perlu action saat notifikasi dihapus
    }

    /**
     * Proses notifikasi pembayaran: simpan ke DB lalu kirim webhook.
     */
    private suspend fun processPaymentNotification(notification: PaymentNotification) {
        try {
            // 1. Simpan ke database
            val logEntry = TransactionLog(
                source = notification.source,
                packageName = notification.packageName,
                amount = notification.amount,
                transactionType = notification.type.name,
                rawTitle = notification.rawTitle,
                rawText = notification.rawText,
                senderName = notification.senderName,
                timestamp = notification.timestamp,
                webhookStatus = "PENDING"
            )

            val logId = database.transactionDao().insert(logEntry)
            Log.d(TAG, "Transaction saved to DB with id: $logId")

            // 2. Kirim webhook jika URL sudah dikonfigurasi
            if (prefManager.isWebhookConfigured) {
                sendWebhook(logId, notification)
            } else {
                Log.w(TAG, "Webhook URL not configured, skipping webhook send")
            }

            // 3. Broadcast ke UI untuk update
            val broadcastIntent = Intent(ACTION_NEW_TRANSACTION).apply {
                putExtra(EXTRA_TRANSACTION_ID, logId)
                setPackage(packageName) // Hanya ke aplikasi sendiri
            }
            sendBroadcast(broadcastIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing payment notification: ${e.message}", e)
        }
    }

    /**
     * Kirim data ke webhook server dan update status di DB.
     */
    private suspend fun sendWebhook(logId: Long, notification: PaymentNotification) {
        try {
            val response = webhookSender.send(
                webhookUrl = prefManager.webhookUrl,
                notification = notification,
                apiKey = prefManager.apiKey.ifBlank { null },
                deviceId = prefManager.deviceId,
                appVersion = getAppVersion()
            )

            val status = if (response.success) "SENT" else "FAILED"

            database.transactionDao().updateWebhookStatus(
                id = logId,
                status = status,
                httpCode = response.httpCode,
                response = response.message?.take(500), // Limit response length
                retryAt = System.currentTimeMillis()
            )

            if (response.success) {
                Log.i(TAG, "Webhook sent successfully for log $logId (HTTP ${response.httpCode})")
            } else {
                Log.w(TAG, "Webhook failed for log $logId: ${response.httpCode} - ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending webhook for log $logId: ${e.message}", e)
            database.transactionDao().updateWebhookStatus(
                id = logId,
                status = "FAILED",
                httpCode = -1,
                response = "Exception: ${e.message}",
                retryAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * Mulai foreground service dengan persistent notification.
     * Ini mencegah Android membunuh service di background.
     */
    private fun startForegroundService() {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotifListener Aktif")
            .setContentText("Mendengarkan notifikasi pembayaran...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground: ${e.message}", e)
        }
    }

    /**
     * Buat notification channel (wajib untuk Android 8.0+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification listener service berjalan di background"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Dapatkan versi aplikasi.
     */
    private fun getAppVersion(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
