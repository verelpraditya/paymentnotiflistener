package com.notiflistener.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object untuk transaction_logs table.
 */
@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: TransactionLog): Long

    @Update
    suspend fun update(log: TransactionLog)

    /**
     * Ambil semua log transaksi, terbaru di atas (untuk UI).
     */
    @Query("SELECT * FROM transaction_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<TransactionLog>>

    /**
     * Ambil N log terbaru (untuk UI).
     */
    @Query("SELECT * FROM transaction_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<TransactionLog>>

    /**
     * Ambil log yang gagal kirim webhook dan belum melebihi max retry.
     */
    @Query("SELECT * FROM transaction_logs WHERE webhookStatus = 'FAILED' AND retryCount < :maxRetry ORDER BY timestamp ASC")
    suspend fun getFailedWebhooks(maxRetry: Int = 3): List<TransactionLog>

    /**
     * Ambil log yang masih PENDING.
     */
    @Query("SELECT * FROM transaction_logs WHERE webhookStatus = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingWebhooks(): List<TransactionLog>

    /**
     * Update status webhook setelah pengiriman.
     */
    @Query("UPDATE transaction_logs SET webhookStatus = :status, webhookHttpCode = :httpCode, webhookResponse = :response, retryCount = retryCount + 1, lastRetryAt = :retryAt WHERE id = :id")
    suspend fun updateWebhookStatus(
        id: Long,
        status: String,
        httpCode: Int?,
        response: String?,
        retryAt: Long
    )

    /**
     * Hapus log yang lebih tua dari N hari.
     */
    @Query("DELETE FROM transaction_logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldLogs(beforeTimestamp: Long)

    /**
     * Hitung total log.
     */
    @Query("SELECT COUNT(*) FROM transaction_logs")
    suspend fun getCount(): Int

    /**
     * Hitung log berdasarkan status webhook.
     */
    @Query("SELECT COUNT(*) FROM transaction_logs WHERE webhookStatus = :status")
    suspend fun getCountByStatus(status: String): Int

    /**
     * Hapus semua log.
     */
    @Query("DELETE FROM transaction_logs")
    suspend fun deleteAll()
}
