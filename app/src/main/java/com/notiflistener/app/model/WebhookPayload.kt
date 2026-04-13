package com.notiflistener.app.model

/**
 * Webhook request payload that will be sent to the server.
 */
data class WebhookPayload(
    val source: String,          // "DANA", "BCA", "BRI"
    val amount: Long,            // nominal Rupiah
    val raw_text: String,        // raw notification text
    val sender_name: String?,    // nama pengirim
    val transaction_type: String,// "INCOMING", "OUTGOING"
    val timestamp: String,       // ISO 8601 format
    val device_id: String,       // unique device identifier
    val app_version: String      // app version name
)

/**
 * Webhook response from server
 */
data class WebhookResponse(
    val success: Boolean,
    val message: String?,
    val httpCode: Int
)
