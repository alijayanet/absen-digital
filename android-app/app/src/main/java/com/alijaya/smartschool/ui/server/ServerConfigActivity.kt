package com.alijaya.smartschool.ui.server

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.alijaya.smartschool.SmartSchoolApplication
import com.alijaya.smartschool.data.pref.SessionManager
import com.alijaya.smartschool.databinding.ActivityServerConfigBinding
import com.alijaya.smartschool.ui.main.MainActivity
import com.alijaya.smartschool.util.NetworkUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ServerConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerConfigBinding

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            handleQrCodeResult(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val session = SmartSchoolApplication.sessionManager

        // Setup App Mode (Default: Standalone untuk STB)
        val currentMode = session.getAppMode()
        if (currentMode == SessionManager.MODE_STANDALONE) {
            binding.rbModeStandalone.isChecked = true
            showStandaloneModeUI()
        } else {
            binding.rbModeClient.isChecked = true
            showClientModeUI()
        }

        binding.rgAppMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == binding.rbModeStandalone.id) {
                showStandaloneModeUI()
            } else {
                showClientModeUI()
            }
        }

        // Port Server
        binding.etServerPort.setText(session.getServerPort().toString())

        // Load existing configuration for Client Mode
        val savedUrl = session.getServerBaseUrl()
        if (savedUrl.isNotEmpty() && !savedUrl.contains("127.0.0.1")) {
            binding.etServerUrl.setText(savedUrl)
        } else {
            binding.etServerUrl.setText("http://192.168.1.100:3000/queue/display")
        }

        // Segarkan info IP lokal STB
        refreshIpInfo()

        binding.btnRefreshIp.setOnClickListener {
            refreshIpInfo()
            Toast.makeText(this, "Alamat IP lokal diperbarui", Toast.LENGTH_SHORT).show()
        }

        // Presets Client Mode
        binding.btnPresetProd.text = "Layar Display TV"
        binding.btnPresetProd.setOnClickListener {
            var url = binding.etServerUrl.text.toString().trim()
            if (url.isEmpty()) url = "http://192.168.1.100:3000"
            val uri = android.net.Uri.parse(if (url.startsWith("http")) url else "http://$url")
            val hostAndPort = "${uri.scheme}://${uri.authority}"
            binding.etServerUrl.setText("$hostAndPort/queue/display")
        }

        binding.btnPresetLocal.text = "Kiosk Gerbang"
        binding.btnPresetLocal.setOnClickListener {
            var url = binding.etServerUrl.text.toString().trim()
            if (url.isEmpty()) url = "http://192.168.1.100:3000"
            val uri = android.net.Uri.parse(if (url.startsWith("http")) url else "http://$url")
            val hostAndPort = "${uri.scheme}://${uri.authority}"
            binding.etServerUrl.setText("$hostAndPort/kiosk")
        }

        // Scan QR Code
        binding.btnScanQr.setOnClickListener {
            val options = ScanOptions().apply {
                setPrompt("Arahkan kamera ke QR Code konfigurasi server")
                setBeepEnabled(true)
                setOrientationLocked(true)
                setBarcodeImageEnabled(false)
            }
            qrScanLauncher.launch(options)
        }

        // Test Ping
        binding.btnTestPing.setOnClickListener {
            testServerConnection()
        }

        // Save & Connect
        binding.btnSaveConnect.setOnClickListener {
            saveAndApplyMode()
        }
    }

    private fun showStandaloneModeUI() {
        binding.cardStandaloneDetails.visibility = View.VISIBLE
        binding.cardClientDetails.visibility = View.GONE
        binding.tvBottomInfo.text = "Mode Server Mandiri akan langsung membuka Kiosk Absensi & Jam Sholat di TV."
    }

    private fun showClientModeUI() {
        binding.cardStandaloneDetails.visibility = View.GONE
        binding.cardClientDetails.visibility = View.VISIBLE
        binding.tvBottomInfo.text = "Mode Klien akan menghubungkan aplikasi ke URL server eksternal."
    }

    private fun refreshIpInfo() {
        val ip = NetworkUtils.getLocalIpAddress(this)
        val port = binding.etServerPort.text.toString().trim().toIntOrNull() ?: 3000
        val isConnected = NetworkUtils.isConnected(this)

        if (ip == "127.0.0.1" || !isConnected) {
            binding.tvRemoteWebUrl.text = "Belum Terkoneksi ke Router / Wi-Fi"
            binding.tvRemoteWebUrl.setTextColor(Color.parseColor("#EF4444"))
            binding.tvNetworkInfo.text = "⚠️ Hubungkan perangkat ke kabel LAN atau Wi-Fi untuk mengaktifkan web remote."
        } else {
            binding.tvRemoteWebUrl.text = "http://$ip:$port"
            binding.tvRemoteWebUrl.setTextColor(Color.parseColor("#0F172A"))
            binding.tvNetworkInfo.text = "✅ Jaringan aktif. Buka alamat di atas pada browser laptop/HP guru."
        }
    }

    private fun handleQrCodeResult(content: String) {
        val trimmed = content.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            binding.etServerUrl.setText(trimmed)
            Toast.makeText(this, "URL server terdeteksi dari QR Code", Toast.LENGTH_SHORT).show()
        } else {
            try {
                val json = JSONObject(trimmed)
                val url = json.optString("url", json.optString("server", ""))
                if (url.isNotEmpty()) {
                    binding.etServerUrl.setText(url)
                }
                Toast.makeText(this, "Konfigurasi server berhasil dimuat dari QR", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                binding.etServerUrl.setText(trimmed)
            }
        }
        testServerConnection()
    }

    private fun testServerConnection() {
        var url = binding.etServerUrl.text.toString().trim()
        if (url.isEmpty()) {
            binding.tilServerUrl.error = "URL server tidak boleh kosong"
            return
        }
        binding.tilServerUrl.error = null

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
            binding.etServerUrl.setText(url)
        }

        binding.tvPingStatus.visibility = View.VISIBLE
        binding.tvPingStatus.text = "Menguji koneksi ke server..."
        binding.tvPingStatus.setTextColor(Color.parseColor("#0F766E"))

        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            var message = ""
            var httpCode = 0

            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS)
                    .build()

                val testUrl = url.trimEnd('/')
                val request = Request.Builder()
                    .url(testUrl)
                    .header("User-Agent", "SmartSchool-Android-App/1.1.0")
                    .build()

                val response = client.newCall(request).execute()
                httpCode = response.code
                success = response.isSuccessful || httpCode in 200..399
                message = if (success) "Koneksi Berhasil (HTTP $httpCode)" else "Server Merespons Error (HTTP $httpCode)"
            } catch (e: Exception) {
                success = false
                message = "Gagal Terhubung: ${e.localizedMessage ?: "Timeout / Server offline"}"
            }

            withContext(Dispatchers.Main) {
                binding.tvPingStatus.visibility = View.VISIBLE
                if (success) {
                    binding.tvPingStatus.text = "✅ $message"
                    binding.tvPingStatus.setTextColor(Color.parseColor("#10B981"))
                } else {
                    binding.tvPingStatus.text = "❌ $message"
                    binding.tvPingStatus.setTextColor(Color.parseColor("#EF4444"))
                }
            }
        }
    }

    private fun saveAndApplyMode() {
        val session = SmartSchoolApplication.sessionManager

        if (binding.rbModeStandalone.isChecked) {
            // Standalone Mode
            val port = binding.etServerPort.text.toString().trim().toIntOrNull() ?: 3000
            session.setAppMode(SessionManager.MODE_STANDALONE)
            session.setServerPort(port)
            session.saveServerBaseUrl("http://127.0.0.1:$port")
            session.setFirstTimeSetup(false)

            // Mulai ulang server lokal terintegrasi pada port yang ditentukan
            SmartSchoolApplication.restartHttpServer(this, port)

            Toast.makeText(this, "Mode Server Mandiri diaktifkan", Toast.LENGTH_SHORT).show()
            goToMainActivity()
        } else {
            // Client Mode
            val url = binding.etServerUrl.text.toString().trim()
            if (url.isEmpty()) {
                binding.tilServerUrl.error = "URL server tidak boleh kosong"
                return
            }

            // Hentikan server lokal agar membebaskan port
            SmartSchoolApplication.stopHttpServer()

            session.setAppMode(SessionManager.MODE_CLIENT)
            session.saveServerBaseUrl(url)
            session.setFirstTimeSetup(false)

            Toast.makeText(this, "Mode Klien berhasil disimpan", Toast.LENGTH_SHORT).show()
            goToMainActivity()
        }
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
