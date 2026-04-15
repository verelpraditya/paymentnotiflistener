package com.notiflistener.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.notiflistener.app.data.AppDatabase
import com.notiflistener.app.data.TransactionLog
import com.notiflistener.app.databinding.ActivityMainBinding
import com.notiflistener.app.model.PaymentNotification
import com.notiflistener.app.model.TransactionType
import com.notiflistener.app.service.ListenerRecovery
import com.notiflistener.app.service.NotifListenerService
import com.notiflistener.app.service.WebhookRetryWorker
import com.notiflistener.app.util.PreferenceManager
import com.notiflistener.app.webhook.WebhookSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity utama aplikasi.
 *
 * Fitur UI:
 * 1. Status service (aktif/nonaktif) + tombol untuk ke Notification Access Settings
 * 2. Konfigurasi webhook URL & API key
 * 3. Toggle provider yang dimonitor (DANA, BCA, BRI, dll)
 * 4. Log transaksi 50 terbaru
 * 5. Peringatan battery optimization
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefManager: PreferenceManager
    private lateinit var database: AppDatabase
    private lateinit var logAdapter: TransactionLogAdapter

    /**
     * BroadcastReceiver untuk menerima notifikasi transaksi baru dari service.
     */
    private val transactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Refresh akan terjadi otomatis via Flow, tapi kita bisa trigger UI update di sini
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PreferenceManager(this)
        database = AppDatabase.getInstance(this)

        setupUI()
        loadSavedSettings()
        observeTransactionLogs()
        updateStats()
    }

    override fun onResume() {
        super.onResume()
        if (isNotificationListenerEnabled()) {
            ListenerRecovery.requestRebindIfAllowed(this, "activity resume health check")
        }
        updateServiceStatus()
        updateBatteryOptimizationWarning()

        // Register broadcast receiver
        val filter = IntentFilter(NotifListenerService.ACTION_NEW_TRANSACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(transactionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(transactionReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(transactionReceiver)
        } catch (_: Exception) {}
    }

    /**
     * Setup semua UI components dan event listeners.
     */
    private fun setupUI() {
        // RecyclerView
        logAdapter = TransactionLogAdapter()
        binding.rvTransactionLog.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
        }

        // Button: Toggle Notification Access Permission
        binding.btnTogglePermission.setOnClickListener {
            openNotificationAccessSettings()
        }

        // Button: Save Webhook Config
        binding.btnSaveWebhook.setOnClickListener {
            saveWebhookConfig()
        }

        // Button: Test Webhook
        binding.btnTestWebhook.setOnClickListener {
            testWebhook()
        }

        // Button: Clear Log
        binding.btnClearLog.setOnClickListener {
            showClearLogDialog()
        }

        // Button: Battery Optimization
        binding.btnBatteryOptimization.setOnClickListener {
            requestDisableBatteryOptimization()
        }

        // Switch: Only Incoming
        binding.switchOnlyIncoming.setOnCheckedChangeListener { _, isChecked ->
            prefManager.onlyIncoming = isChecked
        }

        // Switch: Provider Toggles
        binding.switchDana.setOnCheckedChangeListener { _, isChecked ->
            prefManager.monitorDana = isChecked
        }
        binding.switchBca.setOnCheckedChangeListener { _, isChecked ->
            prefManager.monitorBca = isChecked
        }
        binding.switchBri.setOnCheckedChangeListener { _, isChecked ->
            prefManager.monitorBri = isChecked
        }
        binding.switchBni.setOnCheckedChangeListener { _, isChecked ->
            prefManager.monitorBni = isChecked
        }
        binding.switchMandiri.setOnCheckedChangeListener { _, isChecked ->
            prefManager.monitorMandiri = isChecked
        }
    }

    /**
     * Load saved settings ke UI.
     */
    private fun loadSavedSettings() {
        binding.etWebhookUrl.setText(prefManager.webhookUrl)
        binding.etApiKey.setText(prefManager.apiKey)
        binding.switchOnlyIncoming.isChecked = prefManager.onlyIncoming
        binding.switchDana.isChecked = prefManager.monitorDana
        binding.switchBca.isChecked = prefManager.monitorBca
        binding.switchBri.isChecked = prefManager.monitorBri
        binding.switchBni.isChecked = prefManager.monitorBni
        binding.switchMandiri.isChecked = prefManager.monitorMandiri
    }

    /**
     * Cek apakah NotificationListenerService aktif dan update UI.
     */
    private fun updateServiceStatus() {
        val isEnabled = isNotificationListenerEnabled()

        if (!isEnabled) {
            binding.tvServiceStatus.text = getString(R.string.status_no_permission)
            binding.viewStatusIndicator.setBackgroundResource(R.drawable.circle_red)
            binding.btnTogglePermission.text = getString(R.string.btn_enable)
        } else if (prefManager.isListenerConnected) {
            binding.tvServiceStatus.text = getString(R.string.status_active)
            binding.viewStatusIndicator.setBackgroundResource(R.drawable.circle_green)
            binding.btnTogglePermission.text = getString(R.string.btn_disable)
        } else {
            binding.tvServiceStatus.text = getString(R.string.status_reconnecting)
            binding.viewStatusIndicator.setBackgroundResource(R.drawable.circle_red)
            binding.btnTogglePermission.text = getString(R.string.btn_disable)
        }
    }

    /**
     * Cek apakah app memiliki notification listener permission.
     */
    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, NotifListenerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(componentName.flattenToString()) == true
    }

    /**
     * Buka halaman Notification Access di Settings.
     */
    private fun openNotificationAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak bisa membuka Settings", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Simpan konfigurasi webhook.
     */
    private fun saveWebhookConfig() {
        val url = binding.etWebhookUrl.text.toString().trim()
        val apiKey = binding.etApiKey.text.toString().trim()

        if (url.isBlank()) {
            Snackbar.make(binding.root, R.string.msg_webhook_url_empty, Snackbar.LENGTH_SHORT).show()
            return
        }

        if (!isValidUrl(url)) {
            Snackbar.make(binding.root, R.string.msg_webhook_url_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }

        prefManager.webhookUrl = url
        prefManager.apiKey = apiKey

        // Schedule retry worker jika belum
        WebhookRetryWorker.schedule(this)

        Snackbar.make(binding.root, R.string.msg_webhook_saved, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Validasi URL.
     */
    private fun isValidUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches() &&
                (url.startsWith("http://") || url.startsWith("https://"))
    }

    /**
     * Kirim test webhook untuk memverifikasi koneksi.
     */
    private fun testWebhook() {
        val url = binding.etWebhookUrl.text.toString().trim()
        val apiKey = binding.etApiKey.text.toString().trim()

        if (url.isBlank() || !isValidUrl(url)) {
            Snackbar.make(binding.root, R.string.msg_webhook_url_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }

        Snackbar.make(binding.root, R.string.msg_test_sending, Snackbar.LENGTH_SHORT).show()
        binding.btnTestWebhook.isEnabled = false

        lifecycleScope.launch {
            val sender = WebhookSender()

            try {
                val testNotification = PaymentNotification(
                    source = "TEST",
                    packageName = "com.notiflistener.app.test",
                    amount = 1000,
                    rawTitle = "Test Notification",
                    rawText = "Ini adalah test webhook dari NotifListener",
                    senderName = "TEST_USER",
                    timestamp = System.currentTimeMillis(),
                    type = TransactionType.INCOMING
                )

                val response = sender.send(
                    webhookUrl = url,
                    notification = testNotification,
                    apiKey = apiKey.ifBlank { null },
                    deviceId = prefManager.deviceId,
                    appVersion = getAppVersion()
                )

                withContext(Dispatchers.Main) {
                    if (!isDestroyed) {
                        binding.btnTestWebhook.isEnabled = true

                        if (response.success) {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.msg_test_success, response.httpCode),
                                Snackbar.LENGTH_LONG
                            ).show()
                        } else {
                            val msg = "HTTP ${response.httpCode}: ${response.message?.take(100)}"
                            Snackbar.make(
                                binding.root,
                                getString(R.string.msg_test_failed, msg),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isDestroyed) {
                        binding.btnTestWebhook.isEnabled = true
                        Snackbar.make(
                            binding.root,
                            getString(R.string.msg_test_exception, e.message ?: "unknown"),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            } finally {
                sender.shutdown()
            }
        }
    }

    /**
     * Observe log transaksi dari database dan tampilkan di RecyclerView.
     */
    private fun observeTransactionLogs() {
        lifecycleScope.launch {
            database.transactionDao().getRecentLogs(50).collectLatest { logs ->
                logAdapter.submitList(logs)

                // Show/hide empty state
                binding.tvEmptyLog.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
                binding.rvTransactionLog.visibility = if (logs.isEmpty()) View.GONE else View.VISIBLE

                // Update stats
                updateStats()
            }
        }
    }

    /**
     * Update statistik di UI.
     */
    private fun updateStats() {
        lifecycleScope.launch {
            val total = withContext(Dispatchers.IO) { database.transactionDao().getCount() }
            val sent = withContext(Dispatchers.IO) { database.transactionDao().getCountByStatus("SENT") }
            val failed = withContext(Dispatchers.IO) { database.transactionDao().getCountByStatus("FAILED") }

            binding.tvStatTotal.text = "Total: $total"
            binding.tvStatSent.text = "Sent: $sent"
            binding.tvStatFailed.text = "Failed: $failed"
        }
    }

    /**
     * Tampilkan dialog konfirmasi hapus log.
     */
    private fun showClearLogDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_clear_title)
            .setMessage(R.string.dialog_clear_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        database.transactionDao().deleteAll()
                    }
                    Snackbar.make(binding.root, R.string.msg_log_cleared, Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    /**
     * Update tampilan peringatan battery optimization.
     */
    private fun updateBatteryOptimizationWarning() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
        binding.cardBatteryWarning.visibility = if (isIgnoring) View.GONE else View.VISIBLE
    }

    /**
     * Minta user untuk menonaktifkan battery optimization.
     */
    private fun requestDisableBatteryOptimization() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback ke halaman battery optimization
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Tidak bisa membuka Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Dapatkan versi aplikasi.
     */
    private fun getAppVersion(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
