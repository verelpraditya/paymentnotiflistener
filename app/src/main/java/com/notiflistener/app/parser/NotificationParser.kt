package com.notiflistener.app.parser

import com.notiflistener.app.model.PaymentNotification
import com.notiflistener.app.model.TransactionType

/**
 * Interface untuk parser notifikasi dari berbagai provider.
 */
interface NotificationParser {

    /**
     * Nama provider (e.g., "DANA", "BCA").
     */
    val providerName: String

    /**
     * Daftar package names yang ditangani parser ini.
     */
    val supportedPackages: Set<String>

    /**
     * Cek apakah parser ini mendukung package name tertentu.
     */
    fun supports(packageName: String): Boolean {
        return packageName in supportedPackages
    }

    /**
     * Parse notifikasi menjadi PaymentNotification.
     *
     * @param packageName Package name aplikasi sumber
     * @param title Judul notifikasi
     * @param text Teks notifikasi (bisa dari EXTRA_TEXT atau EXTRA_BIG_TEXT)
     * @param timestamp Waktu notifikasi diterima
     * @return PaymentNotification jika berhasil di-parse, null jika tidak relevan
     */
    fun parse(
        packageName: String,
        title: String,
        text: String,
        timestamp: Long
    ): PaymentNotification?

    /**
     * Deteksi tipe transaksi dari teks notifikasi.
     */
    fun detectTransactionType(title: String, text: String): TransactionType
}
