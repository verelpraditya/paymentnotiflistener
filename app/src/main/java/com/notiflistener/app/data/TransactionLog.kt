package com.notiflistener.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity untuk menyimpan log transaksi yang terdeteksi dari notifikasi.
 */
@Entity(tableName = "transaction_logs")
data class TransactionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Sumber notifikasi: DANA, BCA, BRI, dll */
    val source: String,

    /** Package name aplikasi sumber */
    val packageName: String,

    /** Nominal transaksi dalam Rupiah */
    val amount: Long,

    /** Tipe transaksi: INCOMING / OUTGOING / UNKNOWN */
    val transactionType: String,

    /** Judul notifikasi asli */
    val rawTitle: String,

    /** Teks notifikasi asli */
    val rawText: String,

    /** Nama pengirim (jika tersedia) */
    val senderName: String? = null,

    /** Timestamp notifikasi diterima (epoch millis) */
    val timestamp: Long,

    /** Status pengiriman webhook: PENDING, SENT, FAILED */
    val webhookStatus: String = "PENDING",

    /** HTTP response code dari webhook */
    val webhookHttpCode: Int? = null,

    /** Response body dari webhook */
    val webhookResponse: String? = null,

    /** Jumlah retry yang sudah dilakukan */
    val retryCount: Int = 0,

    /** Timestamp terakhir kali dicoba kirim webhook */
    val lastRetryAt: Long? = null
)
