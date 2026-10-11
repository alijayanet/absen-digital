package com.alijaya.smartschool.data.pref

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "SmartSchoolPrefs"
        private const val KEY_SERVER_BASE_URL = "server_base_url"
        private const val KEY_FIRST_TIME = "is_first_time"
        private const val KEY_GATEWAY_SECRET = "gateway_secret"
        private const val KEY_GATEWAY_ENABLED = "gateway_enabled"
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_PRINTER_ENABLED = "printer_enabled"
        private const val KEY_PRINTER_MAC = "printer_mac"
        private const val KEY_PRINTER_NAME = "printer_name"
        private const val KEY_TTS_ENABLED = "tts_enabled"

        const val MODE_STANDALONE = "standalone" // STB All-in-One: Server mandiri di STB
        const val MODE_CLIENT = "client"         // Client mode: Hubungkan ke server Node.js eksternal
        const val DEFAULT_PORT = 3000
        const val DEFAULT_SERVER_URL = "https://almuhajirin.ujunggebang.online"
    }

    fun getAppMode(): String {
        return prefs.getString(KEY_APP_MODE, MODE_CLIENT) ?: MODE_CLIENT
    }

    fun setAppMode(mode: String) {
        prefs.edit().putString(KEY_APP_MODE, mode).apply()
    }

    fun isStandaloneMode(): Boolean {
        return getAppMode() == MODE_STANDALONE
    }

    fun getServerPort(): Int {
        return prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
    }

    fun setServerPort(port: Int) {
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply()
    }

    fun getServerBaseUrl(): String {
        if (isStandaloneMode()) {
            return "http://127.0.0.1:${getServerPort()}"
        }
        val saved = prefs.getString(KEY_SERVER_BASE_URL, "") ?: ""
        return if (saved.isEmpty()) DEFAULT_SERVER_URL else cleanUrl(saved)
    }

    fun getKioskUrl(): String {
        return "${getServerBaseUrl()}/kiosk"
    }

    fun cleanUrl(url: String): String {
        var clean = url.trim()
        if (clean.isNotEmpty() && !clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        clean = clean.trimEnd('/')
        val suffixes = listOf("/queue/display", "/display", "/kiosk", "/login", "/dashboard")
        for (s in suffixes) {
            if (clean.endsWith(s, ignoreCase = true)) {
                clean = clean.substring(0, clean.length - s.length).trimEnd('/')
            }
        }
        return if (clean.isEmpty()) DEFAULT_SERVER_URL else clean
    }

    fun saveServerBaseUrl(url: String) {
        val clean = cleanUrl(url)
        prefs.edit()
            .putString(KEY_SERVER_BASE_URL, clean)
            .putBoolean(KEY_FIRST_TIME, false)
            .apply()
    }

    fun isFirstTimeSetup(): Boolean {
        return prefs.getBoolean(KEY_FIRST_TIME, false)
    }

    fun setFirstTimeSetup(isFirstTime: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_TIME, isFirstTime).apply()
    }

    fun getGatewaySecret(): String {
        return prefs.getString(KEY_GATEWAY_SECRET, "") ?: ""
    }

    fun saveGatewaySecret(secret: String) {
        prefs.edit().putString(KEY_GATEWAY_SECRET, secret.trim()).apply()
    }

    fun isGatewayEnabled(): Boolean {
        return prefs.getBoolean(KEY_GATEWAY_ENABLED, true)
    }

    fun setGatewayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GATEWAY_ENABLED, enabled).apply()
    }

    fun isPrinterEnabled(): Boolean {
        return prefs.getBoolean(KEY_PRINTER_ENABLED, false)
    }

    fun setPrinterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRINTER_ENABLED, enabled).apply()
    }

    fun getPrinterMac(): String {
        return prefs.getString(KEY_PRINTER_MAC, "") ?: ""
    }

    fun getPrinterName(): String {
        return prefs.getString(KEY_PRINTER_NAME, "") ?: ""
    }

    fun savePrinter(mac: String, name: String) {
        prefs.edit()
            .putString(KEY_PRINTER_MAC, mac.trim())
            .putString(KEY_PRINTER_NAME, name.trim())
            .apply()
    }

    fun isTtsEnabled(): Boolean {
        return prefs.getBoolean(KEY_TTS_ENABLED, true) // Default AKTIF (true)
    }

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
    }
}
