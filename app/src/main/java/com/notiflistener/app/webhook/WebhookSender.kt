package com.notiflistener.app.webhook

import android.util.Log
import com.google.gson.Gson
import com.notiflistener.app.model.PaymentNotification
import com.notiflistener.app.model.WebhookPayload
import com.notiflistener.app.model.WebhookResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * HTTP Client untuk mengirim data notifikasi ke webhook server.
 * Menggunakan OkHttp3 dengan support retry dan error handling.
 */
class WebhookSender {

    companion object {
        private const val TAG = "WebhookSender"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Timeout untuk koneksi dan read */
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 15L
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = Gson()

    /**
     * Kirim notifikasi pembayaran ke webhook URL.
     *
     * @param webhookUrl URL tujuan webhook
     * @param notification Data notifikasi pembayaran
     * @param apiKey API key untuk autentikasi (opsional)
     * @param deviceId Device ID pengirim
     * @param appVersion Versi aplikasi
     * @return WebhookResponse dengan status pengiriman
     */
    suspend fun send(
        webhookUrl: String,
        notification: PaymentNotification,
        apiKey: String? = null,
        deviceId: String,
        appVersion: String
    ): WebhookResponse = withContext(Dispatchers.IO) {
        try {
            val payload = WebhookPayload(
                source = notification.source,
                amount = notification.amount,
                raw_text = notification.rawText,
                sender_name = notification.senderName,
                transaction_type = notification.type.name,
                timestamp = formatTimestamp(notification.timestamp),
                device_id = deviceId,
                app_version = appVersion
            )

            val jsonBody = gson.toJson(payload)
            Log.d(TAG, "Sending webhook to $webhookUrl: $jsonBody")

            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

            val requestBuilder = Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("User-Agent", "NotifListener-Android/$appVersion")

            // Tambahkan API key jika ada
            if (!apiKey.isNullOrBlank()) {
                requestBuilder.header("X-API-Key", apiKey)
            }

            val request = requestBuilder.build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Webhook response: ${response.code} - $responseBody")

            WebhookResponse(
                success = response.isSuccessful,
                message = responseBody,
                httpCode = response.code
            )
        } catch (e: IOException) {
            Log.e(TAG, "Webhook IO error: ${e.message}", e)
            WebhookResponse(
                success = false,
                message = "Network error: ${e.message}",
                httpCode = 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Webhook error: ${e.message}", e)
            WebhookResponse(
                success = false,
                message = "Error: ${e.message}",
                httpCode = -1
            )
        }
    }

    /**
     * Kirim raw payload (untuk retry dari database).
     */
    suspend fun sendRaw(
        webhookUrl: String,
        jsonPayload: String,
        apiKey: String? = null,
        appVersion: String
    ): WebhookResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)

            val requestBuilder = Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("User-Agent", "NotifListener-Android/$appVersion")

            if (!apiKey.isNullOrBlank()) {
                requestBuilder.header("X-API-Key", apiKey)
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            WebhookResponse(
                success = response.isSuccessful,
                message = responseBody,
                httpCode = response.code
            )
        } catch (e: IOException) {
            WebhookResponse(
                success = false,
                message = "Network error: ${e.message}",
                httpCode = 0
            )
        } catch (e: Exception) {
            WebhookResponse(
                success = false,
                message = "Error: ${e.message}",
                httpCode = -1
            )
        }
    }

    /**
     * Format timestamp ke ISO 8601 dengan timezone.
     */
    private fun formatTimestamp(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(epochMillis))
    }

    /**
     * Shutdown HTTP client (dipanggil saat service dihentikan).
     */
    fun shutdown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
