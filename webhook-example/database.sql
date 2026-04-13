-- ============================================================
-- DATABASE SCHEMA untuk Webhook Payment System
-- ============================================================
-- Jalankan SQL ini di database MySQL/MariaDB kamu.
-- Sesuaikan nama database dan struktur sesuai kebutuhan.
-- ============================================================

-- Tabel pending transactions (transaksi yang menunggu pembayaran)
CREATE TABLE IF NOT EXISTS `pending_transactions` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `order_id` VARCHAR(50) NOT NULL UNIQUE COMMENT 'ID order dari toko online',
    `amount` BIGINT UNSIGNED NOT NULL COMMENT 'Nominal asli (tanpa kode unik)',
    `unique_code` INT UNSIGNED NOT NULL COMMENT 'Kode unik (1-999)',
    `total_amount` BIGINT UNSIGNED NOT NULL COMMENT 'Nominal + kode unik (yang dibayar user)',
    `status` ENUM('PENDING', 'PAID', 'EXPIRED', 'CANCELLED') DEFAULT 'PENDING',
    `customer_name` VARCHAR(100) DEFAULT NULL,
    `customer_email` VARCHAR(100) DEFAULT NULL,
    `customer_phone` VARCHAR(20) DEFAULT NULL,
    `payment_source` VARCHAR(20) DEFAULT NULL COMMENT 'DANA, BCA, BRI, dll (diisi oleh webhook)',
    `payment_raw_text` TEXT DEFAULT NULL COMMENT 'Teks notifikasi asli (diisi oleh webhook)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `paid_at` TIMESTAMP NULL DEFAULT NULL,
    `expired_at` TIMESTAMP NULL DEFAULT NULL,
    `webhook_received_at` TIMESTAMP NULL DEFAULT NULL,

    INDEX `idx_total_amount_status` (`total_amount`, `status`),
    INDEX `idx_status_created` (`status`, `created_at`),
    INDEX `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabel unmatched payments (pembayaran yang tidak cocok dengan transaksi pending)
CREATE TABLE IF NOT EXISTS `unmatched_payments` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `source` VARCHAR(20) NOT NULL COMMENT 'DANA, BCA, BRI, dll',
    `amount` BIGINT UNSIGNED NOT NULL,
    `raw_text` TEXT DEFAULT NULL,
    `sender_name` VARCHAR(100) DEFAULT NULL,
    `device_id` VARCHAR(100) DEFAULT NULL,
    `received_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `resolved` BOOLEAN DEFAULT FALSE COMMENT 'Sudah di-review/resolve manual',
    `notes` TEXT DEFAULT NULL,

    INDEX `idx_amount` (`amount`),
    INDEX `idx_received` (`received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- CONTOH DATA
-- ============================================================

-- Contoh: Buat transaksi pending senilai Rp 50.000 + kode unik 123 = Rp 50.123
INSERT INTO `pending_transactions` (`order_id`, `amount`, `unique_code`, `total_amount`, `customer_name`)
VALUES ('ORD-20260412-001', 50000, 123, 50123, 'John Doe');

-- Contoh: Buat transaksi pending senilai Rp 100.000 + kode unik 456 = Rp 100.456
INSERT INTO `pending_transactions` (`order_id`, `amount`, `unique_code`, `total_amount`, `customer_name`)
VALUES ('ORD-20260412-002', 100000, 456, 100456, 'Jane Doe');

-- ============================================================
-- CONTOH QUERY HELPER
-- ============================================================

-- Lihat semua transaksi pending
-- SELECT * FROM pending_transactions WHERE status = 'PENDING' ORDER BY created_at DESC;

-- Lihat transaksi yang sudah dibayar hari ini
-- SELECT * FROM pending_transactions WHERE status = 'PAID' AND DATE(paid_at) = CURDATE();

-- Auto-expire transaksi yang lebih dari 24 jam
-- UPDATE pending_transactions SET status = 'EXPIRED' WHERE status = 'PENDING' AND created_at < DATE_SUB(NOW(), INTERVAL 24 HOUR);

-- Lihat pembayaran yang tidak cocok (perlu review manual)
-- SELECT * FROM unmatched_payments WHERE resolved = FALSE ORDER BY received_at DESC;
