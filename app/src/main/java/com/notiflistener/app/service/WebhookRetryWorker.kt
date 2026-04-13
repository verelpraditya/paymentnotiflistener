package com.notiflistener.app.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.notiflistener.app.data.AppDatabase
import com.notiflistener.app.model.PaymentNotification
import com.notiflistener.app.model.TransactionType
import com.notiflistener.app.util.PreferenceManager
import com.notiflistener.app.webhook.WebhookSender
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker untuk retry pengiriman webhook yang gagal.
 * Berjalan secara periodik untuk mencoba kirim ulang webhook yang FAILED/PENDING.
 */
class WebhookRetryWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "WebhookRetryWorker"
        private const val WORK_NAME = "webhook_retry_work"

        /**
         * Schedule periodic retry worker.
         * Jalan setiap 15 menit (minimum interval untuk WorkManager).
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WebhookRetryWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )

            Log.d(TAG, "Webhook retry worker scheduled")
        }

        /**
         * Cancel scheduled worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Webhook retry worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        val prefManager = PreferenceManager(applicationContext)
        val database = AppDatabase.getInstance(applicationContext)
        val webhookSender = WebhookSender()

        if (!prefManager.isWebhookConfigured) {
            Log.d(TAG, "Webhook not configured, skipping retry")
            return Result.success()
        }

        if (!prefManager.autoRetry) {
            Log.d(TAG, "Auto retry disabled, skipping")
            return Result.success()
        }

        try {
            // Retry FAILED webhooks
            val failedLogs = database.transactionDao().getFailedWebhooks(prefManager.maxRetryCount)
            // Retry PENDING webhooks
            val pendingLogs = database.transactionDao().getPendingWebhooks()

            val allToRetry = (pendingLogs + failedLogs).distinctBy { it.id }
            Log.d(TAG, "Found ${allToRetry.size} webhooks to retry")

            for (log in allToRetry) {
                try {
                    val notification = PaymentNotification(
                        source = log.source,
                        packageName = log.packageName,
                        amount = log.amount,
                        rawTitle = log.rawTitle,
                        rawText = log.rawText,
                        senderName = log.senderName,
                        timestamp = log.timestamp,
                        type = TransactionType.valueOf(log.transactionType)
                    )

                    val response = webhookSender.send(
                        webhookUrl = prefManager.webhookUrl,
                        notification = notification,
                        apiKey = prefManager.apiKey.ifBlank { null },
                        deviceId = prefManager.deviceId,
                        appVersion = getAppVersion()
                    )

                    val status = if (response.success) "SENT" else "FAILED"
                    database.transactionDao().updateWebhookStatus(
                        id = log.id,
                        status = status,
                        httpCode = response.httpCode,
                        response = response.message?.take(500),
                        retryAt = System.currentTimeMillis()
                    )

                    Log.d(TAG, "Retry log ${log.id}: $status")
                } catch (e: Exception) {
                    Log.e(TAG, "Error retrying log ${log.id}: ${e.message}", e)
                }
            }

            // Cleanup log lama (> 30 hari)
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            database.transactionDao().deleteOldLogs(thirtyDaysAgo)

            webhookSender.shutdown()
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Worker error: ${e.message}", e)
            webhookSender.shutdown()
            return Result.retry()
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = applicationContext.packageManager.getPackageInfo(
                applicationContext.packageName, 0
            )
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
