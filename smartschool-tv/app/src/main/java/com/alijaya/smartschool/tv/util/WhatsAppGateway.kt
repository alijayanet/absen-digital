package com.alijaya.smartschool.tv.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WhatsAppGateway(
    private val provider: String,
    private val token: String,
    private val wablasDomain: String = "https://solo.wablas.com",
    private val secretKey: String = ""
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class SendResult(val success: Boolean, val message: String)

    suspend fun send(targetPhone: String, message: String): SendResult = withContext(Dispatchers.IO) {
        val phone = targetPhone.replace("+", "").replace("-", "").replace(" ", "").trim()
        if (phone.isBlank()) return@withContext SendResult(false, "Nomor telepon kosong")
        if (token.isBlank()) return@withContext SendResult(false, "Token API WhatsApp belum diatur")

        when (provider.lowercase()) {
            "fonnte" -> sendFonnte(phone, message)
            "wablas" -> sendWablas(phone, message)
            else -> SendResult(false, "Gateway WhatsApp dinonaktifkan")
        }
    }

    private fun sendFonnte(phone: String, message: String): SendResult {
        return try {
            val form = FormBody.Builder()
                .add("target", phone)
                .add("message", message)
                .build()

            val req = Request.Builder()
                .url("https://api.fonnte.com/send")
                .header("Authorization", token)
                .post(form)
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val json = JSONObject(body)
                val status = json.optBoolean("status", true)
                if (status) {
                    SendResult(true, "Terkirim via Fonnte")
                } else {
                    val reason = json.optString("reason", "Fonnte error")
                    SendResult(false, reason)
                }
            } else {
                SendResult(false, "HTTP ${resp.code}: $body")
            }
        } catch (e: Exception) {
            SendResult(false, e.message ?: "Koneksi ke Fonnte gagal")
        }
    }

    private fun sendWablas(phone: String, message: String): SendResult {
        return try {
            val domain = if (wablasDomain.isBlank()) "https://solo.wablas.com" else wablasDomain.removeSuffix("/")
            val url = "$domain/api/send-message"

            val jsonBody = JSONObject().apply {
                put("phone", phone)
                put("message", message)
                if (secretKey.isNotBlank()) put("secret", secretKey)
            }

            val req = Request.Builder()
                .url(url)
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val json = JSONObject(body)
                val status = json.optBoolean("status", true)
                if (status) {
                    SendResult(true, "Terkirim via Wablas")
                } else {
                    val msg = json.optString("message", "Wablas error")
                    SendResult(false, msg)
                }
            } else {
                SendResult(false, "HTTP ${resp.code}: $body")
            }
        } catch (e: Exception) {
            SendResult(false, e.message ?: "Koneksi ke Wablas gagal")
        }
    }
}
