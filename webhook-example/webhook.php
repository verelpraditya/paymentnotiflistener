<?php
/**
 * ============================================================
 * CONTOH WEBHOOK ENDPOINT - NotifListener Payment Receiver
 * ============================================================
 *
 * File ini adalah contoh endpoint webhook yang menerima data
 * notifikasi pembayaran dari aplikasi NotifListener Android.
 *
 * FLOW:
 * 1. Sistem kamu membuat QRIS dengan nominal + kode unik
 *    Contoh: Rp 50.123 (dimana 123 adalah kode unik)
 * 2. User scan QRIS dan bayar
 * 3. Notifikasi dari DANA/Bank masuk di HP Android
 * 4. Aplikasi NotifListener membaca notifikasi dan kirim ke webhook ini
 * 5. Webhook ini mencocokkan nominal dengan pending transaction
 * 6. Jika cocok, transaksi dianggap berhasil (PAID)
 *
 * CARA PAKAI:
 * 1. Letakkan file ini di server kamu (misal: https://example.com/webhook.php)
 * 2. Masukkan URL tersebut di aplikasi NotifListener Android
 * 3. Sesuaikan koneksi database dan logic sesuai kebutuhan
 *
 * KEAMANAN:
 * - Gunakan HTTPS
 * - Set API Key di aplikasi Android dan verifikasi di sini
 * - Validasi semua input
 */

// ============================================================
// KONFIGURASI
// ============================================================

// API Key untuk autentikasi (sama dengan yang di-set di aplikasi Android)
define('WEBHOOK_API_KEY', 'your-secret-api-key-here');

// Konfigurasi database (sesuaikan dengan database kamu)
define('DB_HOST', 'localhost');
define('DB_NAME', 'toko_online');
define('DB_USER', 'root');
define('DB_PASS', '');

// Timezone
date_default_timezone_set('Asia/Jakarta');

// ============================================================
// MAIN HANDLER
// ============================================================

// Hanya terima POST request
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['success' => false, 'message' => 'Method not allowed']);
    exit;
}

// Set response header
header('Content-Type: application/json');

// Baca body request
$rawBody = file_get_contents('php://input');
$data = json_decode($rawBody, true);

// Log request masuk (untuk debugging)
logWebhook($rawBody);

// ============================================================
// VALIDASI
// ============================================================

// 1. Validasi API Key (jika digunakan)
$apiKey = $_SERVER['HTTP_X_API_KEY'] ?? '';
if (WEBHOOK_API_KEY !== 'your-secret-api-key-here' && $apiKey !== WEBHOOK_API_KEY) {
    http_response_code(401);
    echo json_encode(['success' => false, 'message' => 'Unauthorized: Invalid API Key']);
    exit;
}

// 2. Validasi JSON body
if ($data === null) {
    http_response_code(400);
    echo json_encode(['success' => false, 'message' => 'Invalid JSON body']);
    exit;
}

// 3. Validasi required fields
$requiredFields = ['source', 'amount', 'raw_text', 'transaction_type', 'timestamp', 'device_id'];
foreach ($requiredFields as $field) {
    if (!isset($data[$field])) {
        http_response_code(400);
        echo json_encode(['success' => false, 'message' => "Missing field: $field"]);
        exit;
    }
}

// 4. Validasi amount (harus angka positif)
$amount = intval($data['amount']);
if ($amount <= 0) {
    http_response_code(400);
    echo json_encode(['success' => false, 'message' => 'Invalid amount']);
    exit;
}

// 5. Skip jika bukan transaksi masuk
if ($data['transaction_type'] !== 'INCOMING') {
    http_response_code(200);
    echo json_encode(['success' => true, 'message' => 'Skipped: not an incoming transaction']);
    exit;
}

// 6. Skip test webhook
if ($data['source'] === 'TEST') {
    http_response_code(200);
    echo json_encode(['success' => true, 'message' => 'Test webhook received successfully']);
    exit;
}

// ============================================================
// PROSES PEMBAYARAN
// ============================================================

try {
    $pdo = new PDO(
        "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4",
        DB_USER,
        DB_PASS,
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
    );

    /**
     * Cari transaksi PENDING yang nominal-nya cocok.
     *
     * Logika: Kamu membuat QRIS dengan nominal+kode_unik.
     * Misal: nominal = 50000, kode_unik = 123, total = 50123
     * Saat notifikasi masuk dengan amount = 50123, kita cari di tabel
     * pending_transactions yang total_amount = 50123.
     */
    $stmt = $pdo->prepare("
        SELECT id, order_id, amount, unique_code, total_amount, created_at
        FROM pending_transactions
        WHERE total_amount = :amount
          AND status = 'PENDING'
          AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        ORDER BY created_at DESC
        LIMIT 1
    ");

    $stmt->execute([':amount' => $amount]);
    $transaction = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($transaction) {
        // MATCH DITEMUKAN! Update status jadi PAID
        $updateStmt = $pdo->prepare("
            UPDATE pending_transactions
            SET status = 'PAID',
                paid_at = NOW(),
                payment_source = :source,
                payment_raw_text = :raw_text,
                webhook_received_at = NOW()
            WHERE id = :id AND status = 'PENDING'
        ");

        $updateStmt->execute([
            ':source' => $data['source'],
            ':raw_text' => substr($data['raw_text'], 0, 500),
            ':id' => $transaction['id']
        ]);

        $rowsAffected = $updateStmt->rowCount();

        if ($rowsAffected > 0) {
            // Sukses! Transaksi terverifikasi
            http_response_code(200);
            echo json_encode([
                'success' => true,
                'message' => 'Payment verified',
                'order_id' => $transaction['order_id'],
                'amount' => $amount,
                'matched' => true
            ]);

            // TODO: Kirim notifikasi ke user bahwa pembayaran berhasil
            // notifyPaymentSuccess($transaction['order_id']);

        } else {
            // Transaksi sudah diproses sebelumnya (race condition)
            http_response_code(200);
            echo json_encode([
                'success' => true,
                'message' => 'Transaction already processed',
                'order_id' => $transaction['order_id'],
                'matched' => false
            ]);
        }

    } else {
        // Tidak ada transaksi pending yang cocok
        // Simpan sebagai unmatched untuk review manual
        $logStmt = $pdo->prepare("
            INSERT INTO unmatched_payments (source, amount, raw_text, sender_name, device_id, received_at)
            VALUES (:source, :amount, :raw_text, :sender_name, :device_id, NOW())
        ");

        $logStmt->execute([
            ':source' => $data['source'],
            ':amount' => $amount,
            ':raw_text' => substr($data['raw_text'], 0, 500),
            ':sender_name' => $data['sender_name'] ?? null,
            ':device_id' => $data['device_id']
        ]);

        http_response_code(200);
        echo json_encode([
            'success' => true,
            'message' => 'No matching pending transaction found',
            'amount' => $amount,
            'matched' => false
        ]);
    }

} catch (PDOException $e) {
    // Log error (jangan expose detail ke response untuk keamanan)
    error_log("Webhook DB Error: " . $e->getMessage());

    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Internal server error'
    ]);
}

// ============================================================
// HELPER FUNCTIONS
// ============================================================

/**
 * Log webhook request ke file (untuk debugging).
 */
function logWebhook($rawBody) {
    $logDir = __DIR__ . '/logs';
    if (!is_dir($logDir)) {
        mkdir($logDir, 0755, true);
    }

    $logFile = $logDir . '/webhook_' . date('Y-m-d') . '.log';
    $logEntry = sprintf(
        "[%s] IP: %s | Body: %s\n",
        date('Y-m-d H:i:s'),
        $_SERVER['REMOTE_ADDR'] ?? 'unknown',
        $rawBody
    );

    file_put_contents($logFile, $logEntry, FILE_APPEND | LOCK_EX);
}
