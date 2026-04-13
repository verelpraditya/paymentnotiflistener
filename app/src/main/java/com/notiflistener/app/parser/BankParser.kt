package com.notiflistener.app.parser

import com.notiflistener.app.model.PaymentNotification
import com.notiflistener.app.model.TransactionType
import com.notiflistener.app.util.AmountExtractor
import com.notiflistener.app.util.PackageNames

/**
 * Parser untuk notifikasi dari aplikasi Mobile Banking (BCA, BRI, BNI, Mandiri).
 *
 * Contoh notifikasi yang dimonitor:
 *
 * BCA Mobile:
 * - Title: "myBCA"
 *   Text: "Transfer masuk Rp 50.123,00 dari JOHN DOE ke rek 1234567890"
 *
 * BRI (BRImo):
 * - Title: "BRImo"
 *   Text: "Dana masuk sebesar Rp50.123 ke rekening 1234567890 dari JOHN DOE"
 *
 * BNI Mobile:
 * - Title: "BNI Mobile Banking"
 *   Text: "Anda menerima transfer sebesar Rp 50.123 dari rek 987654321 a.n. JOHN DOE"
 *
 * Mandiri (Livin):
 * - Title: "Livin' by Mandiri"
 *   Text: "Dana masuk Rp 50.123,00 dari JOHN DOE ke rek 1234567890"
 */
class BankParser : NotificationParser {

    override val providerName: String = "BANK"

    override val supportedPackages: Set<String> = setOf(
        PackageNames.BCA_MOBILE,
        PackageNames.BCA_MYBCA,
        PackageNames.BRI_MOBILE,
        PackageNames.BNI_MOBILE,
        PackageNames.MANDIRI_ONLINE
    )

    /** Mapping package name ke nama bank */
    private val bankNames = mapOf(
        PackageNames.BCA_MOBILE to "BCA",
        PackageNames.BCA_MYBCA to "BCA",
        PackageNames.BRI_MOBILE to "BRI",
        PackageNames.BNI_MOBILE to "BNI",
        PackageNames.MANDIRI_ONLINE to "MANDIRI"
    )

    /**
     * Keywords yang mengindikasikan dana MASUK.
     */
    private val incomingKeywords = listOf(
        "transfer masuk",
        "dana masuk",
        "menerima transfer",
        "menerima dana",
        "terima transfer",
        "uang masuk",
        "saldo masuk",
        "kredit",
        "cr ",
        "received",
        "incoming transfer",
        "anda menerima"
    )

    /**
     * Keywords yang mengindikasikan dana KELUAR.
     */
    private val outgoingKeywords = listOf(
        "transfer keluar",
        "transfer ke",
        "pembayaran",
        "pembelian",
        "debit",
        "db ",
        "berhasil transfer",
        "penarikan",
        "tarik tunai"
    )

    /**
     * Keywords yang harus di-skip (bukan transaksi keuangan).
     */
    private val skipKeywords = listOf(
        "otp",
        "kode verifikasi",
        "promo",
        "login",
        "sandi",
        "password",
        "iklan",
        "penawaran",
        "undian"
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

        // Ekstrak nominal
        val amount = AmountExtractor.extract(text)
            ?: AmountExtractor.extract(title)
            ?: return null

        // Nama bank berdasarkan package name
        val bankName = bankNames[packageName] ?: "BANK"

        // Coba ekstrak nama pengirim
        val senderName = extractSenderName(text)

        return PaymentNotification(
            source = bankName,
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
     * Coba ekstrak nama pengirim dari teks notifikasi bank.
     * Pattern umum: "dari NAMA" atau "a.n. NAMA" atau "dari rek xxx a.n. NAMA"
     */
    private fun extractSenderName(text: String): String? {
        // Pattern 1: "a.n. NAMA PENGIRIM"
        val anPattern = Regex("""a\.?n\.?\s+(.+?)(?:\s*$|\s*\.|ke\s)""", RegexOption.IGNORE_CASE)
        val anMatch = anPattern.find(text)
        if (anMatch != null) {
            return anMatch.groupValues[1].trim()
        }

        // Pattern 2: "dari NAMA PENGIRIM ke" atau "dari NAMA PENGIRIM"
        val dariPattern = Regex("""dari\s+(?:rek(?:ening)?\s*\d+\s*)?(?:a\.?n\.?\s*)?(.+?)(?:\s*ke\s|\s*$|\s*\.)""", RegexOption.IGNORE_CASE)
        val dariMatch = dariPattern.find(text)
        if (dariMatch != null) {
            val name = dariMatch.groupValues[1].trim()
            // Pastikan bukan nomor rekening saja
            if (!name.matches(Regex("""^\d+$"""))) {
                return name
            }
        }

        return null
    }
}
