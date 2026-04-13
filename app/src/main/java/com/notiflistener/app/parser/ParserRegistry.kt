package com.notiflistener.app.parser

/**
 * Registry untuk semua notification parsers.
 * Mengelola dan mendistribusikan notifikasi ke parser yang sesuai.
 */
class ParserRegistry {

    private val parsers: List<NotificationParser> = listOf(
        DanaParser(),
        BankParser()
    )

    /**
     * Cari parser yang sesuai untuk package name tertentu.
     *
     * @param packageName Package name aplikasi sumber notifikasi
     * @return Parser yang sesuai, atau null jika tidak ada
     */
    fun findParser(packageName: String): NotificationParser? {
        return parsers.find { it.supports(packageName) }
    }

    /**
     * Cek apakah package name didukung oleh salah satu parser.
     */
    fun isSupported(packageName: String): Boolean {
        return parsers.any { it.supports(packageName) }
    }

    /**
     * Mendapatkan semua package names yang didukung.
     */
    fun getAllSupportedPackages(): Set<String> {
        return parsers.flatMap { it.supportedPackages }.toSet()
    }
}
