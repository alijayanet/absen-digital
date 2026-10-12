package com.alijaya.smartschool.server.whatsapp

import android.util.Log
import com.alijaya.smartschool.server.db.SmartSchoolDatabase
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudWhatsAppClient {

    private const val TAG = "CloudWhatsAppClient"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun formatPhone(rawPhone: String): String {
        var digits = rawPhone.replace(Regex("\\D"), "")
        if (digits.startsWith("0")) {
            digits = "62" + digits.substring(1)
        } else if (digits.startsWith("8")) {
            digits = "62$digits"
        }
        return digits
    }

    /**
     * Kirim notifikasi presensi siswa ke nomor WhatsApp orang tua
     */
    fun sendAttendanceNotification(
        db: SmartSchoolDatabase,
        parentPhone: String,
        studentName: String,
        className: String,
        timeIn: String,
        status: String,
        lateMinutes: Int = 0,
        queueNumber: Int? = null,
        teacherName: String? = null
    ) {
        val enabled = db.getSetting("wa_enabled", "1")
        if (enabled != "1") {
            Log.d(TAG, "WhatsApp notification is disabled in settings.")
            return
        }

        val phone = formatPhone(parentPhone)
        if (phone.length < 9) {
            Log.w(TAG, "Parent phone number is invalid: $parentPhone")
            return
        }

        val schoolName = db.getSetting("school_name", "Musholla Al-Muhajirin")
        val statusText = if (status == "HADIR") "HADIR TEPAT WAKTU" else "TERLAMBAT ($lateMinutes Menit)"
        val entityType = db.getSetting("entity_type", "santri").lowercase()
        val (headerTitle, groupLabel, footerText) = when (entityType) {
            "murid" -> Triple(
                "NOTIFIKASI PRESENSI SISWA / MURID",
                "Kelas",
                "Terima kasih atas perhatian Bapak/Ibu orang tua / wali murid."
            )
            "karyawan" -> Triple(
                "NOTIFIKASI PRESENSI KARYAWAN",
                "Divisi",
                "Catatan kehadiran telah tersimpan di sistem presensi digital."
            )
            else -> Triple(
                "NOTIFIKASI PRESENSI SANTRI",
                "Kelas",
                "Terima kasih atas kerja sama Bapak/Ibu wali santri."
            )
        }

        val queueText = if (queueNumber != null && queueNumber > 0) {
            val tName = if (!teacherName.isNullOrBlank()) " ($teacherName)" else ""
            "\n📋 *Nomor Antrian Mengaji:* #$queueNumber$tName"
        } else {
            ""
        }

        val message = """
            📢 *$headerTitle*
            _${schoolName}_
            
            Telah tercatat presensi:
            👤 *Nama:* $studentName
            🏷️ *$groupLabel:* $className
            ⏰ *Waktu:* $timeIn WIB
            📊 *Status:* *$statusText*$queueText
            
            $footerText
            _Pesan otomatis dikirim oleh Sistem Presensi Mandiri._
        """.trimIndent()

        sendAsync(db, phone, message)
    }

    /**
     * Kirim pesan teks secara asinkron (background thread)
     */
    fun sendAsync(db: SmartSchoolDatabase, phone: String, message: String) {
        Thread {
            try {
                sendSync(db, phone, message)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending WhatsApp async: ${e.message}")
            }
        }.start()
    }

    /**
     * Kirim pesan teks secara sinkron
     */
    fun sendSync(db: SmartSchoolDatabase, rawPhone: String, message: String): Pair<Boolean, String> {
        val phone = formatPhone(rawPhone)
        if (phone.length < 9) return Pair(false, "Nomor WhatsApp tidak valid")

        val provider = db.getSetting("wa_provider", "fonnte")
        return if (provider == "wablas") {
            sendViaWablas(db, phone, message)
        } else {
            sendViaFonnte(db, phone, message)
        }
    }

    private fun sendViaFonnte(db: SmartSchoolDatabase, phone: String, message: String): Pair<Boolean, String> {
        val token = db.getSetting("wa_fonnte_token", "").trim()
        if (token.isEmpty()) {
            return Pair(false, "Token Fonnte belum diisi di Pengaturan WhatsApp")
        }

        val formBody = FormBody.Builder()
            .add("target", phone)
            .add("message", message)
            .add("countryCode", "62")
            .build()

        val request = Request.Builder()
            .url("https://api.fonnte.com/send")
            .header("Authorization", token)
            .post(formBody)
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val success = json.optBoolean("status", false)
            if (success) {
                Pair(true, "Pesan berhasil dikirim via Fonnte")
            } else {
                Pair(false, "Fonnte Error: " + json.optString("reason", json.optString("message", "Gagal")))
            }
        } catch (e: Exception) {
            Pair(false, "Koneksi Fonnte Gagal: ${e.message}")
        }
    }

    private fun sendViaWablas(db: SmartSchoolDatabase, phone: String, message: String): Pair<Boolean, String> {
        val token = db.getSetting("wa_wablas_token", "").trim()
        var domain = db.getSetting("wa_wablas_domain", "https://api.wablas.com").trim().trimEnd('/')
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "https://$domain"
        }

        if (token.isEmpty()) {
            return Pair(false, "Token Wablas belum diisi di Pengaturan WhatsApp")
        }

        val jsonPayload = JSONObject().apply {
            put("phone", phone)
            put("message", message)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$domain/api/send-message")
            .header("Authorization", token)
            .post(requestBody)
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val success = json.optBoolean("status", false)
            if (success) {
                Pair(true, "Pesan berhasil dikirim via Wablas")
            } else {
                Pair(false, "Wablas Error: " + json.optString("message", "Gagal"))
            }
        } catch (e: Exception) {
            Pair(false, "Koneksi Wablas Gagal: ${e.message}")
        }
    }
}
