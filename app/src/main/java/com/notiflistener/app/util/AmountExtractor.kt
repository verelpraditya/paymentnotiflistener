package com.notiflistener.app.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Utility untuk mengekstrak nominal (amount) dari teks notifikasi.
 *
 * Mendukung berbagai format penulisan nominal Rupiah:
 * - Rp50.000
 * - Rp 50.000
 * - Rp50,000
 * - Rp 50.000,00
 * - Rp50000
 * - IDR 50.000
 * - 50.000
 * - Rp. 50.000
 */
object AmountExtractor {

    /**
     * Regex patterns untuk menangkap nominal Rupiah dari teks.
     * Diurutkan dari yang paling spesifik ke yang paling umum.
     */
    private val AMOUNT_PATTERNS = listOf(
        // Rp/IDR diikuti nominal dengan titik/koma sebagai separator
        // Contoh: Rp50.123, Rp 50.123,00, Rp. 50.000, IDR 50.000
        Regex("""(?:Rp\.?|IDR)\s*([\d]+(?:[.,]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),

        // Nominal diikuti "rupiah"
        // Contoh: 50.000 rupiah, 50000 Rupiah
        Regex("""([\d]+(?:[.,]\d{3})*(?:[.,]\d{1,2})?)\s*(?:rupiah)""", RegexOption.IGNORE_CASE),

        // Nominal dengan prefix "sebesar" atau "senilai"
        // Contoh: sebesar 50.000, senilai Rp50.000
        Regex("""(?:sebesar|senilai)\s*(?:Rp\.?|IDR)?\s*([\d]+(?:[.,]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
    )

    /**
     * Mengekstrak nominal dari teks notifikasi.
     *
     * @param text Teks notifikasi
     * @return Nominal dalam Long (tanpa desimal), atau null jika tidak ditemukan
     */
    fun extract(text: String): Long? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val rawAmount = match.groupValues[1]
                val amount = parseAmount(rawAmount)
                if (amount != null && amount > 0) {
                    return amount
                }
            }
        }
        return null
    }

    /**
     * Mengekstrak semua nominal yang ditemukan dalam teks.
     *
     * @param text Teks notifikasi
     * @return List nominal yang ditemukan
     */
    fun extractAll(text: String): List<Long> {
        val amounts = mutableListOf<Long>()
        for (pattern in AMOUNT_PATTERNS) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                val rawAmount = match.groupValues[1]
                val amount = parseAmount(rawAmount)
                if (amount != null && amount > 0) {
                    amounts.add(amount)
                }
            }
        }
        return amounts.distinct()
    }

    /**
     * Parse string nominal ke Long.
     * Menangani berbagai format separator (titik, koma).
     *
     * "50.123" -> 50123
     * "50,123" -> 50123
     * "50.123,00" -> 50123
     * "50.000.000" -> 50000000
     * "50000" -> 50000
     */
    private fun parseAmount(raw: String): Long? {
        if (raw.isBlank()) return null

        var cleaned = raw.trim()

        // Jika ada koma diikuti tepat 2 digit di akhir, itu desimal -> buang
        // Contoh: 50.000,00 -> 50.000 -> 50000
        cleaned = cleaned.replace(Regex("""[,.](\d{2})$"""), "")

        // Hapus semua titik dan koma (separator ribuan)
        cleaned = cleaned.replace(".", "").replace(",", "")

        return try {
            cleaned.toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Format nominal ke string Rupiah yang readable.
     * Contoh: 50123 -> "Rp 50.123"
     */
    fun formatRupiah(amount: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
        return "Rp ${formatter.format(amount)}"
    }
}
