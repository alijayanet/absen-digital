package com.alijaya.smartschool.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    /**
     * Mendapatkan alamat IP lokal STB/Android TV (Wi-Fi atau Ethernet LAN).
     * Sangat berguna untuk STB yang sering menggunakan kabel LAN (eth0).
     */
    fun getLocalIpAddress(context: Context): String {
        // 1. Coba baca dari NetworkInterface (meliputi Ethernet eth0 dan Wi-Fi wlan0)
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Prioritaskan eth0 (Kabel LAN) jika ada, lalu wlan0 (Wi-Fi)
            val sortedInterfaces = interfaces.sortedBy { if (it.name.startsWith("eth")) 0 else 1 }

            for (intf in sortedInterfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress
                        if (!hostAddress.isNullOrEmpty() && hostAddress != "127.0.0.1") {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback baca via WifiManager jika metode di atas kosong
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            if (wifiInfo != null && wifiInfo.ipAddress != 0) {
                @Suppress("DEPRECATION")
                return Formatter.formatIpAddress(wifiInfo.ipAddress)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "127.0.0.1"
    }

    /**
     * Cek apakah perangkat terhubung ke jaringan (Wi-Fi atau Ethernet LAN)
     */
    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }
}
