package com.notiflistener.app.model

/**
 * Represents a parsed payment notification from a provider (DANA, BCA, BRI, etc.)
 */
data class PaymentNotification(
    val source: String,           // e.g. "DANA", "BCA", "BRI"
    val packageName: String,      // e.g. "id.dana"
    val amount: Long,             // nominal in Rupiah (e.g. 50123)
    val rawTitle: String,         // original notification title
    val rawText: String,          // original notification text
    val senderName: String?,      // nama pengirim jika tersedia
    val timestamp: Long,          // epoch millis
    val type: TransactionType     // INCOMING, OUTGOING, UNKNOWN
)

enum class TransactionType {
    INCOMING,   // Dana masuk
    OUTGOING,   // Dana keluar
    UNKNOWN
}
