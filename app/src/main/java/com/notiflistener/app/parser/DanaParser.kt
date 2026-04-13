package com.notiflistener.app.parser

import com.notiflistener.app.model.PaymentNotification
import com.notiflistener.app.model.TransactionType
import com.notiflistener.app.util.AmountExtractor
import com.notiflistener.app.util.PackageNames

/**
 * Parser untuk notifikasi dari aplikasi DANA.
 *
 * Contoh notifikasi DANA yang dimonitor:
 * - Title: "Pembayaran Diterima"
 *   Text: "Kamu menerima Rp50.123 dari JOHN DOE"
 *
 * - Title: "Dana Masuk"
 *   Text: "Kamu menerima transfer sebesar Rp50.123 dari 081234567890"
 *
 * - Title: "Pembayaran QRIS Diterima"
 *   Text: "Pembayaran QRIS sebesar Rp50.123 dari JOHN DOE telah diterima"
 */
class DanaParser : NotificationParser {

    override val providerName: String = "DANA"

    override val supportedPackages: Set<String> = setOf(PackageNames.DANA)

    /**
     * Keywords yang mengindikasikan dana MASUK.
     */
    private val incomingKeywords = listOf(
        "menerima",
        "diterima",
        "dana masuk",
        "pembayaran diterima",
        "transfer masuk",
        "qris diterima",
        "received",
        "payment received"
    )

    /**
     * Keywords yang mengindikasikan dana KELUAR.
     */
    private val outgoingKeywords = listOf(
        "membayar",
        "transfer ke",
        "pembayaran berhasil",
        "bayar",
        "kirim",
        "sent",
        "paid"
    )

    /**
     * Keywords yang harus di-skip (bukan transaksi).
     */
    private val skipKeywords = listOf(
        "promo",
        "cashback",
        "voucher",
        "diskon",
        "iklan",
        "update",
        "verifikasi",
        "otp"
    )

    override fun parse(
        packageName: String,
        title: String,
        text: String,
        timestamp: Long
    ): PaymentNotification? {
        val combinedText = "$title $text".lowercase()

        // Skip notifikasi yang bukan transaksi
        if (skipKeywords.any { combinedText.contains(it) }) {
            return null
        }

        val type = detectTransactionType(title, text)

        // Hanya proses jika kita bisa mendeteksi tipe transaksi
        if (type == TransactionType.UNKNOWN) {
            return null
        }

        // Ekstrak nominal - coba dari text dulu, lalu title
        val amount = AmountExtractor.extract(text)
            ?: AmountExtractor.extract(title)
            ?: return null // Tidak ada nominal = skip

        // Coba ekstrak nama pengirim
        val senderName = extractSenderName(text)

        return PaymentNotification(
            source = providerName,
            packageName = packageName,
            amount = amount,
            rawTitle = title,
            rawText = text,
            senderName = senderName,
            timestamp = timestamp,
            type = type
        )
    }

    override fun detectTransactionType(title: String, text: String): TransactionType {
        val combined = "$title $text".lowercase()

        if (incomingKeywords.any { combined.contains(it) }) {
            return TransactionType.INCOMING
        }
        if (outgoingKeywords.any { combined.contains(it) }) {
            return TransactionType.OUTGOING
        }
        return TransactionType.UNKNOWN
    }

    /**
     * Coba ekstrak nama pengirim dari teks notifikasi DANA.
     * Pattern: "dari NAMA_PENGIRIM" atau "dari 08xxxx"
     */
    private fun extractSenderName(text: String): String? {
        // Pattern: "dari <nama/nomor>"
        val dariPattern = Regex("""dari\s+(.+?)(?:\s*$|\s*\.|telah)""", RegexOption.IGNORE_CASE)
        val match = dariPattern.find(text)
        return match?.groupValues?.get(1)?.trim()
    }
}
