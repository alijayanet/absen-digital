package com.alijaya.smartschool.ui.main

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.alijaya.smartschool.R
import com.alijaya.smartschool.SmartSchoolApplication
import com.alijaya.smartschool.data.model.DisplayResponse
import com.alijaya.smartschool.data.model.ScanResponse
import com.alijaya.smartschool.databinding.ActivityMainBinding
import com.alijaya.smartschool.databinding.DialogServerSettingsBinding
import com.alijaya.smartschool.printer.BluetoothPrinterDevice
import com.alijaya.smartschool.printer.BluetoothThermalPrinter
import com.alijaya.smartschool.printer.QueueTicket
import com.alijaya.smartschool.util.BeepTtsHelper
import com.alijaya.smartschool.util.ImageLoader
import android.widget.ArrayAdapter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: QueueTableAdapter
    private lateinit var audioHelper: BeepTtsHelper
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Polling & Background Jobs
    private var pollingJob: Job? = null
    private var clockJob: Job? = null

    // Server Branding & Header info
    private var activeSchoolName: String = "SMART SCHOOL & MAJLIS TA'LIM"
    private var activeSchoolTagline: String = "Sistem Presensi & Antrian Mengaji"

    // Track last audio call to avoid repeating
    private var lastCallUniqueKey: String = ""
    private var lastQueueCallTimestamp = 0L
    private var isCallBannerActive = false
    private val callBannerHandler = Handler(Looper.getMainLooper())
    private val hideCallBannerRunnable = Runnable {
        binding.layoutActiveCallBanner.visibility = View.GONE
        isCallBannerActive = false
    }

    // Daftar Meja Antrian Aktif & Kontrol Remote TV Fisik
    private var activeTables: List<com.alijaya.smartschool.data.model.QueueTable> = emptyList()
    private var lastRemoteCallTimestamp = 0L
    private val remoteFeedbackHandler = Handler(Looper.getMainLooper())
    private val hideRemoteFeedbackRunnable = Runnable {
        binding.tvRemoteFeedbackToast.visibility = View.GONE
    }

    // Prayer Time Alert Popup State & Countdown (20 Detik)
    private var isPrayerAlertActive = false
    private var prayerAlertSecondsRemaining = 20
    private var lastTriggeredPrayerKey = ""
    private var activePrayerSchedule: com.alijaya.smartschool.data.model.PrayerSchedule? = null
    private val prayerAlertHandler = Handler(Looper.getMainLooper())
    private val updatePrayerAlertRunnable = object : Runnable {
        override fun run() {
            prayerAlertSecondsRemaining--
            if (prayerAlertSecondsRemaining <= 0) {
                hidePrayerAlertPopup()
            } else {
                binding.tvPrayerAlertCountdown.text = "Kembali ke layar antrian dalam $prayerAlertSecondsRemaining detik... (Tekan OK / BACK di Remote untuk menutup)"
                prayerAlertHandler.postDelayed(this, 1000L)
            }
        }
    }

    // Track external scans from Web Kiosk
    private var lastHandledAttendanceId = -1
    private var isFirstDisplayLoad = true
    private var lastLocalScanTimestamp = 0L

    // Hardware Keyboard Wedge (USB RFID Reader) Buffer & Debounce
    private val rfidBuffer = StringBuilder()
    private var lastKeyTimestamp = 0L
    private val SCAN_TIMEOUT_MS = 600L
    private val bufferResetHandler = Handler(Looper.getMainLooper())
    private val resetBufferRunnable = Runnable { rfidBuffer.clear() }
    private var isScanProcessing = false
    private var lastScannedCardCode = ""
    private var lastCardScanTimestamp = 0L

    // RFID Result Dismissal
    private val resultDismissHandler = Handler(Looper.getMainLooper())
    private var dismissSecondsRemaining = 6
    private val updateDismissTimerRunnable = object : Runnable {
        override fun run() {
            dismissSecondsRemaining--
            if (dismissSecondsRemaining <= 0) {
                restoreRfidStandby()
            } else {
                binding.tvDismissTimer.text = "Kembali ke standby dalam $dismissSecondsRemaining detik..."
                resultDismissHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Mencegah layar STB / Android TV sleep / mati (24/7 Kiosk)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioHelper = SmartSchoolApplication.getAudioHelper(this)

        // Daftarkan listener agar panggilan antrian dari Telegram & Web langsung direspon instan oleh TV
        SmartSchoolApplication.onQueueChangedListener = {
            lifecycleScope.launch(Dispatchers.Main) {
                fetchDisplayData()
            }
        }

        setupRecyclerView()
        setupListeners()
        startClock()
        startDisplayPolling()
        checkAndRequestBluetoothPermissions()

        // Aktifkan Marquee Running Text di baris bawah
        binding.tvMarqueeAnnouncement.isSelected = true

        // Sinkronisasi status TTS dari database lokal SQLite (Default AKTIF jika belum diatur)
        try {
            val session = SmartSchoolApplication.sessionManager
            val standaloneDb = com.alijaya.smartschool.server.db.SmartSchoolDatabase.getInstance(this)
            val dbTts = standaloneDb.getSetting("tts_enabled", "1")
            session.setTtsEnabled(dbTts != "0")
            val dbPrayerTts = standaloneDb.getSetting("prayer_tts_enabled", "0")
            session.setPrayerTtsEnabled(dbPrayerTts == "1" || dbPrayerTts.equals("true", ignoreCase = true))
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        hideSoftKeyboard()
        if (pollingJob == null || pollingJob?.isActive == false) {
            startDisplayPolling()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SmartSchoolApplication.onQueueChangedListener = null
        pollingJob?.cancel()
        clockJob?.cancel()
        bufferResetHandler.removeCallbacks(resetBufferRunnable)
        resultDismissHandler.removeCallbacksAndMessages(null)
        callBannerHandler.removeCallbacksAndMessages(null)
        remoteFeedbackHandler.removeCallbacksAndMessages(null)
        prayerAlertHandler.removeCallbacksAndMessages(null)
    }

    private fun setupRecyclerView() {
        adapter = QueueTableAdapter()
        binding.rvQueueTables.layoutManager = GridLayoutManager(this, 2)
        binding.rvQueueTables.adapter = adapter
    }

    private fun updateTableLayoutManager(tableCount: Int) {
        // Pada layar TV (Landscape 16:9), split 68% lebar cocok untuk 2 kolom (jika 2 atau 4 meja)
        // atau 3 kolom (jika 3, 5, atau 6 meja) agar card meja tidak terjepit
        val spanCount = when {
            tableCount <= 1 -> 1
            tableCount in 2..2 -> 2
            tableCount == 3 -> 3
            tableCount == 4 -> 2 // 4 meja tampil 2x2 (sangat rapi, lapang, dan proporsional)
            tableCount in 5..6 -> 3
            else -> 4
        }
        val currentLayoutManager = binding.rvQueueTables.layoutManager as? GridLayoutManager
        if (currentLayoutManager == null || currentLayoutManager.spanCount != spanCount) {
            binding.rvQueueTables.layoutManager = GridLayoutManager(this, spanCount)
        }
    }

    private fun setupListeners() {
        binding.btnSettings.setOnClickListener {
            showServerSettingsDialog()
        }
        binding.tvInfoBadge.setOnClickListener {
            showServerSettingsDialog()
        }
    }

    private fun checkAndRequestBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
            val needed = permissions.filter {
                androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                androidx.core.app.ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
            }
        }
    }

    private fun hideSoftKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentFocusView = currentFocus ?: binding.root
            imm?.hideSoftInputFromWindow(currentFocusView.windowToken, 0)
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 1. JAM DIGITAL & TANGGAL REAL-TIME (WIB)
    // =========================================================================
    private fun startClock() {
        val timeFormat = SimpleDateFormat("HH:mm:ss 'WIB'", Locale("id", "ID"))
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))

        clockJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                val now = Date()
                binding.tvLiveClock.text = timeFormat.format(now)
                binding.tvLiveDate.text = dateFormat.format(now)
                checkPrayerTimeTrigger(now)
                delay(1000L)
            }
        }
    }

    // =========================================================================
    // 2. HARDWARE KEY EVENT LISTENER (USB RFID READER - ZERO KEYBOARD POPUP)
    // =========================================================================
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastKeyTimestamp > SCAN_TIMEOUT_MS) {
                rfidBuffer.clear()
            }
            lastKeyTimestamp = now

            // Selalu paksa sembunyikan keyboard virtual jika mencoba muncul
            hideSoftKeyboard()

            // Reset buffer jika scanner terputus di tengah jalan
            bufferResetHandler.removeCallbacks(resetBufferRunnable)
            bufferResetHandler.postDelayed(resetBufferRunnable, SCAN_TIMEOUT_MS)

            val keyCode = event.keyCode

            // Jika popup pengingat sholat sedang aktif, tombol remote TV menutup popup secara instan
            if (isPrayerAlertActive) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    hidePrayerAlertPopup()
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    hidePrayerAlertPopup()
                }
            }

            // Shortcut Remote TV: Tekan MENU atau SETTINGS untuk membuka pengaturan server
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                showServerSettingsDialog()
                return true
            }

            // Remote TV D-Pad Direct Call untuk Memanggil Antrian Meja Mengaji
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    triggerRemoteDeskCall(0) // Meja 1
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    triggerRemoteDeskCall(1) // Meja 2
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    triggerRemoteDeskCall(2) // Meja 3
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    triggerRemoteDeskCall(3) // Meja 4
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    // Tombol OK / Tengah Remote TV: Buka Pengaturan Server / IP:8080
                    showServerSettingsDialog()
                    return true
                }
            }

            // Enter menandakan akhir dari transmisi kartu RFID reader USB atau tombol OK/Enter Remote TV
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                val cardCode = rfidBuffer.toString().trim()
                rfidBuffer.clear()
                bufferResetHandler.removeCallbacks(resetBufferRunnable)

                if (cardCode.length >= 3) {
                    val currentTime = System.currentTimeMillis()
                    // Debounce hardware scanner: jika sedang memproses atau kartu sama ditap dalam 2.5 detik
                    if (isScanProcessing) {
                        return true
                    }
                    if (cardCode == lastScannedCardCode && (currentTime - lastCardScanTimestamp) < 2500L) {
                        return true
                    }
                    lastScannedCardCode = cardCode
                    lastCardScanTimestamp = currentTime
                    processAttendanceScan(cardCode)
                    return true // Telah ditangani, jangan teruskan event enter
                } else {
                    // Tombol OK / Enter remote TV ditekan (bukan transmisi kartu RFID)
                    showServerSettingsDialog()
                    return true
                }
            } else {
                val unicodeChar = event.unicodeChar
                val charFromKey = if (unicodeChar > 0) {
                    unicodeChar.toChar()
                } else {
                    when (keyCode) {
                        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> '0'
                        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> '1'
                        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> '2'
                        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> '3'
                        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> '4'
                        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> '5'
                        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> '6'
                        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> '7'
                        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> '8'
                        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> '9'
                        KeyEvent.KEYCODE_A -> 'A'
                        KeyEvent.KEYCODE_B -> 'B'
                        KeyEvent.KEYCODE_C -> 'C'
                        KeyEvent.KEYCODE_D -> 'D'
                        KeyEvent.KEYCODE_E -> 'E'
                        KeyEvent.KEYCODE_F -> 'F'
                        else -> null
                    }
                }

                if (charFromKey != null && (Character.isLetterOrDigit(charFromKey) || charFromKey == '-')) {
                    rfidBuffer.append(charFromKey)
                    return true // Telah disimpan di buffer, jangan ubah fokus UI
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // =========================================================================
    // 3. PROSES ABSENSI SCAN DARI KARTU RFID (HTTP POST /api/attendance/scan)
    // =========================================================================
    private fun processAttendanceScan(cardCode: String) {
        val session = SmartSchoolApplication.sessionManager
        val baseUrl = session.getServerBaseUrl().trimEnd('/')
        val url = "$baseUrl/api/attendance/scan"

        isScanProcessing = true

        // Tampilkan indikasi proses
        binding.layoutRfidStandby.visibility = View.GONE
        binding.layoutRfidResult.visibility = View.VISIBLE
        binding.tvScanResultBadge.text = "⏳ MEMPROSES KARTU..."
        binding.tvScanResultBadge.setBackgroundResource(R.drawable.bg_badge_warning)
        binding.tvScanResultBadge.setTextColor(Color.parseColor("#FBBF24"))
        binding.tvStudentName.text = "Membaca data kartu..."
        binding.tvStudentClassNis.text = "Kode Kartu: $cardCode"
        binding.tvScanTimeStatus.text = ""
        binding.layoutQueueAssigned.visibility = View.GONE
        binding.ivStudentPhoto.setImageResource(R.drawable.ic_user_avatar)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonBody = gson.toJson(mapOf("code" to cardCode))
                val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", "SmartSchool-NativeTV/1.1.0")
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""

                val scanResult: ScanResponse = try {
                    gson.fromJson(responseStr, ScanResponse::class.java)
                } catch (_: Exception) {
                    ScanResponse(success = false, message = "Format respons server tidak valid")
                }

                withContext(Dispatchers.Main) {
                    isScanProcessing = false
                    renderScanResult(scanResult, cardCode, baseUrl)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isScanProcessing = false
                    renderScanError("Gagal terhubung: ${e.message ?: "Koneksi terputus"}", cardCode)
                }
            }
        }
    }

    private fun renderScanResult(result: ScanResponse, cardCode: String, baseUrl: String) {
        val session = SmartSchoolApplication.sessionManager

        // Catat timestamp scan lokal dan ID presensi agar display-data polling tidak memproses ulang (mencegah double TTS)
        lastLocalScanTimestamp = System.currentTimeMillis()
        if (result.attendance?.id != null && result.attendance.id > 0) {
            lastHandledAttendanceId = maxOf(lastHandledAttendanceId, result.attendance.id)
        }

        // Reset dismiss countdown
        resultDismissHandler.removeCallbacksAndMessages(null)
        dismissSecondsRemaining = 6

        if (result.success && result.student != null) {
            val student = result.student
            val attendance = result.attendance
            val queue = result.queue

            // 1. Badge Sukses
            val statusText = attendance?.status ?: "HADIR"
            binding.tvScanResultBadge.text = "✅ PRESENSI BERHASIL ($statusText)"
            binding.tvScanResultBadge.setBackgroundResource(R.drawable.bg_badge_success)
            binding.tvScanResultBadge.setTextColor(Color.parseColor("#34D399"))

            // 2. Info Santri
            binding.tvStudentName.text = student.name ?: "Santri"
            binding.tvStudentClassNis.text = "NIS: ${student.nis ?: "-"}  •  ${student.className ?: "Kelas Santri"}"
            binding.tvScanTimeStatus.text = "Waktu: ${attendance?.timeIn ?: "-"} WIB  •  Status: $statusText"

            // 3. Foto Santri
            renderStudentPhoto(student.photo, baseUrl)

            // 4. Nomor Antrian Mengaji
            if (queue?.queueNumber != null) {
                binding.layoutQueueAssigned.visibility = View.VISIBLE
                binding.tvAssignedQueueNumber.text = "NOMOR ${String.format("%02d", queue.queueNumber)}"
                val targetTeacher = queue.teacherName ?: student.teacherName
                val queueSubText = if (!targetTeacher.isNullOrBlank()) "Pengajar: $targetTeacher" else "Kelas: ${queue.className ?: student.className ?: ""}"
                binding.tvAssignedQueueClass.text = queueSubText
            } else {
                binding.layoutQueueAssigned.visibility = View.GONE
            }

            // 5. Audio Sambutan & Panggilan TTS (Menyebutkan Nama Ustadz/Guru & Nomor Antrian)
            val isMale = isStudentMale(student.gender, student.className, student.name)
            val targetTeacher = queue?.teacherName ?: student.teacherName
            val speech = if (queue?.queueNumber != null) {
                if (!targetTeacher.isNullOrBlank()) {
                    "Selamat datang ${student.name}. Nomor antrian Anda ${queue.queueNumber}, pengajar $targetTeacher."
                } else {
                    "Selamat datang ${student.name}. Nomor antrian Anda ${queue.queueNumber}."
                }
            } else {
                "Selamat datang ${student.name}. Presensi berhasil tercatat."
            }

            if (session.isTtsEnabled()) {
                audioHelper.playBeepAndSpeak(speech, isMale = isMale)
            } else {
                audioHelper.playBeepSuccess()
            }

            // 6. Otomatis Cetak Struk Antrian jika Printer Bluetooth Aktif
            if (queue?.queueNumber != null) {
                printQueueTicketIfEnabled(
                    queueNumber = queue.queueNumber,
                    studentName = student.name ?: "Santri",
                    nis = student.nis ?: "-",
                    className = queue.className ?: student.className ?: "",
                    teacherName = queue.teacherName ?: student.teacherName ?: "",
                    timeIn = attendance?.timeIn ?: ""
                )
            }

        } else if (result.code == "ALREADY_ATTENDED" && result.student != null) {
            val student = result.student
            val attendance = result.attendance
            val queue = result.queue

            binding.tvScanResultBadge.text = "⚠️ SUDAH PRESENSI HARI INI"
            binding.tvScanResultBadge.setBackgroundResource(R.drawable.bg_badge_warning)
            binding.tvScanResultBadge.setTextColor(Color.parseColor("#FBBF24"))

            binding.tvStudentName.text = student.name ?: "Santri"
            binding.tvStudentClassNis.text = "NIS: ${student.nis ?: "-"}  •  ${student.className ?: "Kelas Santri"}"
            binding.tvScanTimeStatus.text = "Tercatat Masuk: ${attendance?.timeIn ?: "-"} WIB"

            if (queue?.queueNumber != null) {
                binding.layoutQueueAssigned.visibility = View.VISIBLE
                binding.tvAssignedQueueNumber.text = "NOMOR ${String.format("%02d", queue.queueNumber)}"
                val targetTeacher = queue.teacherName ?: student.teacherName
                val queueSubText = if (!targetTeacher.isNullOrBlank()) "Pengajar: $targetTeacher" else "Nomor Antrian Mengaji Hari Ini"
                binding.tvAssignedQueueClass.text = queueSubText

                // Cetak ulang struk antrian jika sebelumnya belum tercetak
                printQueueTicketIfEnabled(
                    queueNumber = queue.queueNumber,
                    studentName = student.name ?: "Santri",
                    nis = student.nis ?: "-",
                    className = queue.className ?: student.className ?: "",
                    teacherName = targetTeacher ?: "",
                    timeIn = attendance?.timeIn ?: ""
                )
            } else {
                binding.layoutQueueAssigned.visibility = View.GONE
            }

            renderStudentPhoto(student.photo, baseUrl)

            val isMale = isStudentMale(student.gender, student.className, student.name)
            val targetTeacher = queue?.teacherName ?: student.teacherName
            val alreadySpeech = if (queue?.queueNumber != null && queue.queueNumber > 0) {
                if (!targetTeacher.isNullOrBlank()) {
                    "Santri ${student.name} sudah absen hari ini. Nomor antrian Anda ${queue.queueNumber}, pengajar $targetTeacher."
                } else {
                    "Santri ${student.name} sudah absen hari ini. Nomor antrian Anda ${queue.queueNumber}."
                }
            } else {
                "Santri ${student.name} sudah melakukan presensi hari ini."
            }

            if (session.isTtsEnabled()) {
                audioHelper.playBeepAndSpeak(alreadySpeech, isMale = isMale, isWarning = false)
            } else {
                audioHelper.playBeepWarning()
            }

        } else {
            // Not Found / Error
            binding.tvScanResultBadge.text = "❌ KARTU TIDAK TERDAFTAR"
            binding.tvScanResultBadge.setBackgroundResource(R.drawable.bg_badge_danger)
            binding.tvScanResultBadge.setTextColor(Color.parseColor("#F87171"))

            binding.tvStudentName.text = "Kode Kartu: $cardCode"
            binding.tvStudentClassNis.text = result.message ?: "Kartu belum didaftarkan di sistem"
            binding.tvScanTimeStatus.text = "Silakan hubungi Ustadz / Admin Sekolah"
            binding.layoutQueueAssigned.visibility = View.GONE
            binding.ivStudentPhoto.setImageResource(R.drawable.ic_user_avatar)

            if (session.isTtsEnabled()) {
                audioHelper.playBeepAndSpeak("Kartu tidak terdaftar di sistem.", isMale = true, isWarning = true)
            } else {
                audioHelper.playBeepWarning()
            }
        }

        // Mulai countdown otomatis kembali ke standby
        binding.tvDismissTimer.text = "Kembali ke standby dalam $dismissSecondsRemaining detik..."
        resultDismissHandler.postDelayed(updateDismissTimerRunnable, 1000L)
    }

    private fun renderScanError(errorMessage: String, cardCode: String) {
        resultDismissHandler.removeCallbacksAndMessages(null)
        dismissSecondsRemaining = 5

        binding.tvScanResultBadge.text = "❌ GAGAL MENGHUBUNGI SERVER"
        binding.tvScanResultBadge.setBackgroundResource(R.drawable.bg_badge_danger)
        binding.tvScanResultBadge.setTextColor(Color.parseColor("#F87171"))

        binding.tvStudentName.text = "Kesalahan Jaringan"
        binding.tvStudentClassNis.text = errorMessage
        binding.tvScanTimeStatus.text = "Kartu: $cardCode"
        binding.layoutQueueAssigned.visibility = View.GONE

        audioHelper.playBeepWarning()

        binding.tvDismissTimer.text = "Kembali ke standby dalam $dismissSecondsRemaining detik..."
        resultDismissHandler.postDelayed(updateDismissTimerRunnable, 1000L)
    }

    private fun restoreRfidStandby() {
        resultDismissHandler.removeCallbacksAndMessages(null)
        binding.layoutRfidResult.visibility = View.GONE
        binding.layoutRfidStandby.visibility = View.VISIBLE
    }

    // =========================================================================
    // 4. PERIODIC DISPLAY DATA POLLING (GET /api/display-data)
    // =========================================================================
    private fun startDisplayPolling() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                fetchDisplayData()
                delay(2500L) // Polling interval 2.5 detik
            }
        }
    }

    private suspend fun fetchDisplayData() {
        val session = SmartSchoolApplication.sessionManager
        val baseUrl = session.getServerBaseUrl().trimEnd('/')
        val url = "$baseUrl/api/display-data"

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "SmartSchool-NativeTV/1.1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val data = gson.fromJson(bodyStr, DisplayResponse::class.java)

                withContext(Dispatchers.Main) {
                    updateDisplayUI(data, baseUrl)
                }
            } else {
                withContext(Dispatchers.Main) {
                    binding.tvConnectionStatus.text = "⚠️ HTTP ${response.code}"
                    binding.tvConnectionStatus.setBackgroundResource(R.drawable.bg_badge_warning)
                    binding.tvConnectionStatus.setTextColor(Color.parseColor("#FBBF24"))
                }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                binding.tvConnectionStatus.text = "🔴 Terputus"
                binding.tvConnectionStatus.setBackgroundResource(R.drawable.bg_badge_danger)
                binding.tvConnectionStatus.setTextColor(Color.parseColor("#F87171"))
            }
        }
    }

    private fun updateDisplayUI(data: DisplayResponse, baseUrl: String) {
        // Status Server & URL Akses Web Remote
        val localIp = com.alijaya.smartschool.util.NetworkUtils.getLocalIpAddress(this)
        val serverDisplayUrl = if (localIp != "127.0.0.1") "http://$localIp:8080" else baseUrl
        binding.tvConnectionStatus.text = "🟢 Server: $serverDisplayUrl"
        binding.tvConnectionStatus.setBackgroundResource(R.drawable.bg_badge_success)
        binding.tvConnectionStatus.setTextColor(Color.parseColor("#34D399"))

        // Update Nama Sekolah, Tagline, dan Logo dari CMS Server
        val schoolName = data.schoolName ?: data.schoolNameAlt
        if (!schoolName.isNullOrBlank()) {
            activeSchoolName = schoolName
            binding.tvHeaderTitle.text = schoolName.uppercase()
        }

        val schoolTagline = data.schoolTagline ?: data.schoolTaglineAlt
        if (!schoolTagline.isNullOrBlank()) {
            activeSchoolTagline = schoolTagline
            binding.tvHeaderSubtitle.text = schoolTagline
        }

        val schoolLogo = data.schoolLogo ?: data.schoolLogoAlt
        if (!schoolLogo.isNullOrBlank()) {
            val logoUrl = if (schoolLogo.startsWith("http")) schoolLogo else "$baseUrl$schoolLogo"
            ImageLoader.load(logoUrl, binding.ivSchoolLogo, R.mipmap.ic_launcher, circleCrop = false)
        }

        val newAnnouncement = data.announcement?.trim()
        if (!newAnnouncement.isNullOrBlank()) {
            if (binding.tvMarqueeAnnouncement.text.toString() != newAnnouncement) {
                binding.tvMarqueeAnnouncement.text = newAnnouncement
                binding.tvMarqueeAnnouncement.isSelected = true
            }
        }

        // Sinkronisasi Pengaturan TTS Suara dari Server Database
        val session = SmartSchoolApplication.sessionManager
        if (!data.ttsEnabled.isNullOrBlank()) {
            val isTts = data.ttsEnabled != "0"
            session.setTtsEnabled(isTts)
        }
        if (!data.prayerTtsEnabled.isNullOrBlank()) {
            val isPrayerTts = data.prayerTtsEnabled == "1" || data.prayerTtsEnabled.equals("true", ignoreCase = true)
            session.setPrayerTtsEnabled(isPrayerTts)
        }

        // Update Jadwal Sholat Cards (6 Card Digital Signage TV)
        updatePrayerCardsUI(data.prayer)

        // Deteksi Presensi Eksternal Baru (dari Web Kiosk / Browser Remote)
        val latest = data.latestScan
        if (latest != null && latest.id > 0) {
            if (isFirstDisplayLoad) {
                lastHandledAttendanceId = latest.id
                isFirstDisplayLoad = false
            } else if (latest.id > lastHandledAttendanceId) {
                lastHandledAttendanceId = latest.id
                val timeSinceLocalScan = System.currentTimeMillis() - lastLocalScanTimestamp
                // Hanya trigger popup & TTS eksternal jika BUKAN berasal dari scan lokal yang baru selesai (< 4 detik)
                if (timeSinceLocalScan > 4000L) {
                    renderExternalScanResult(latest, baseUrl)
                }
            }
        }

        // Update Feed Presensi Terkini Hari Ini
        updateRecentAttendancesUI(data.recentAttendances)

        // Update Meja Mengaji
        val tables = data.tables
        activeTables = tables ?: emptyList()
        if (!tables.isNullOrEmpty()) {
            binding.tvEmptyTables.visibility = View.GONE
            binding.rvQueueTables.visibility = View.VISIBLE
            binding.tvTotalTablesCount.text = "${tables.size} Meja Terdaftar"

            // Sesuaikan label hint remote TV sesuai jumlah meja yang aktif
            binding.tvRemoteControlHint.text = when (tables.size) {
                1 -> "🎮 Remote: [◀] Meja 1  •  [OK] Pengaturan IP"
                2 -> "🎮 Remote: [◀] Meja 1  •  [▶] Meja 2  •  [OK] Pengaturan IP"
                3 -> "🎮 Remote: [◀] Meja 1  •  [▶] Meja 2  •  [▲] Meja 3  •  [OK] Pengaturan IP"
                else -> "🎮 Remote: [◀] Meja 1  •  [▶] Meja 2  •  [▲] Meja 3  •  [▼] Meja 4  •  [OK] Pengaturan IP"
            }
            binding.tvRemoteControlHint.visibility = View.VISIBLE

            // Tampilkan card meja secara berdampingan (1, 2, 3, 4 meja side-by-side)
            updateTableLayoutManager(tables.size)

            adapter.submitList(tables)
        } else {
            binding.tvEmptyTables.visibility = View.VISIBLE
            binding.rvQueueTables.visibility = View.GONE
            binding.tvRemoteControlHint.visibility = View.GONE
            binding.tvEmptyTables.text = "Belum ada meja antrian mengaji yang aktif hari ini"
            binding.tvTotalTablesCount.text = "0 Meja"
        }

        // Cek Panggilan Suara Terakhir (lastCall)
        val lastCall = data.lastCall
        if (lastCall != null && lastCall.queueNumber > 0) {
            val uniqueKey = "${lastCall.id}_${lastCall.queueNumber}_${lastCall.callCount}_${lastCall.calledAt}"
            if (isFirstDisplayLoad) {
                // Pada pemuatan pertama TV baru menyala, simpan status panggilan yang ada
                // agar tidak membunyikan panggilan lama / basi dari sesi sebelumnya
                lastCallUniqueKey = uniqueKey
            } else if (uniqueKey != lastCallUniqueKey) {
                lastCallUniqueKey = uniqueKey
                triggerQueueCall(lastCall)
            }
        }

        // Tandai initial load selesai
        if (isFirstDisplayLoad) {
            isFirstDisplayLoad = false
        }
    }

    // =========================================================================
    // 5. TRIGGER PANGGILAN SUARA (AUDIO CHIME + NATIVE TTS) & BANNER PANGGILAN
    // =========================================================================
    private fun triggerQueueCall(lastCall: com.alijaya.smartschool.data.model.QueueCallItem) {
        val now = System.currentTimeMillis()
        // Debounce: cegah pemanggilan berulang dalam rentang 3.5 detik
        if (now - lastQueueCallTimestamp < 3500L) {
            return
        }
        lastQueueCallTimestamp = now

        val qNum = String.format("%02d", lastCall.queueNumber)
        val studentName = lastCall.studentName ?: "Santri"
        val targetTeacher = lastCall.teacherName ?: lastCall.className ?: "Ustadz"

        // 1. Tampilkan banner panggilan
        binding.tvCallingBannerText.text = "📢 PANGGILAN: NOMOR #$qNum ($studentName) ➔ SILAKAN MENUJU ${targetTeacher.uppercase()}"
        binding.tvCallingCountBadge.text = "Panggilan Ke-${lastCall.callCount}"
        binding.layoutActiveCallBanner.visibility = View.VISIBLE
        isCallBannerActive = true

        callBannerHandler.removeCallbacks(hideCallBannerRunnable)
        callBannerHandler.postDelayed(hideCallBannerRunnable, 12000L) // Sembunyikan setelah 12 detik

        // 2. Bunyikan Bel Chime & Panggil Santri via Text-To-Speech (Suara Pria vs Wanita)
        val session = SmartSchoolApplication.sessionManager
        if (session.isTtsEnabled()) {
            val isMale = isStudentMale(lastCall.gender, lastCall.className, lastCall.studentName)
            val speechText = lastCall.speechSentence ?: "Nomor antrian $qNum, $studentName, silakan menuju $targetTeacher"
            audioHelper.playChimeAndSpeak(speechText, isMale = isMale)
        }
    }

    /**
     * Deteksi apakah santri adalah Laki-laki atau Perempuan
     * 1. Dari field gender ('L' vs 'P')
     * 2. Dari nama kelas (SANTRI LAKI LAKI vs SANTRI PEREMPUAN, PUTRA vs PUTRI, BANIN vs BANAT)
     * 3. Dari nama santri perempuan Indonesia yang umum
     */
    private fun isStudentMale(gender: String?, className: String?, studentName: String?): Boolean {
        if (!gender.isNullOrBlank()) {
            val g = gender.trim().uppercase(Locale.ROOT)
            if (g == "L" || g == "LAKI" || g == "LAKI-LAKI" || g == "MALE" || g == "M") return true
            if (g == "P" || g == "PEREMPUAN" || g == "FEMALE" || g == "F") return false
        }

        if (!className.isNullOrBlank()) {
            val c = className.uppercase(Locale.ROOT)
            if (c.contains("LAKI") || c.contains("PUTRA") || c.contains("BANIN") || c.contains("IKHWAN")) return true
            if (c.contains("PEREMPUAN") || c.contains("PUTRI") || c.contains("BANAT") || c.contains("AKHWAT")) return false
        }

        if (!studentName.isNullOrBlank()) {
            val n = studentName.uppercase(Locale.ROOT)
            val femaleKeywords = listOf(
                "SITI", "NURUL", "AISYAH", "FATIMAH", "PUTRI", "ZAHRA",
                "AULIA", "ANISA", "DEWI", "LESTARI", "RAHMA", "SALMA", "HANIFAH"
            )
            for (k in femaleKeywords) {
                if (n.contains(k)) return false
            }
        }

        return true
    }

    // =========================================================================
    // 6. DIALOG KONFIGURASI URL SERVER (DAPAT DIAKSES VIA REMOTE / KLIK GEAR)
    // =========================================================================
    private fun showServerSettingsDialog() {
        val session = SmartSchoolApplication.sessionManager
        val dialogBinding = DialogServerSettingsBinding.inflate(LayoutInflater.from(this))

        val currentUrl = session.getServerBaseUrl()
        dialogBinding.etServerUrl.setText(currentUrl)

        // Inisialisasi Teks Berjalan (Running Text)
        val standaloneDb = com.alijaya.smartschool.server.db.SmartSchoolDatabase.getInstance(this)
        val currentRunningText = standaloneDb.getSetting("running_text", binding.tvMarqueeAnnouncement.text.toString())
            .ifBlank { binding.tvMarqueeAnnouncement.text.toString() }
        dialogBinding.etRunningText.setText(currentRunningText)

        // Inisialisasi Kordinat Jadwal Sholat
        dialogBinding.etPrayerLat.setText(standaloneDb.getSetting("prayer_latitude", "-6.3263"))
        dialogBinding.etPrayerLng.setText(standaloneDb.getSetting("prayer_longitude", "108.3200"))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnPresetDefault.setOnClickListener {
            dialogBinding.etServerUrl.setText(com.alijaya.smartschool.data.pref.SessionManager.DEFAULT_SERVER_URL)
        }

        dialogBinding.btnTestPing.setOnClickListener {
            val inputUrl = dialogBinding.etServerUrl.text.toString().trim()
            if (inputUrl.isEmpty()) {
                dialogBinding.tilServerUrl.error = "URL tidak boleh kosong"
                return@setOnClickListener
            }
            dialogBinding.tilServerUrl.error = null
            val cleanUrl = session.cleanUrl(inputUrl)

            dialogBinding.tvPingStatus.visibility = View.VISIBLE
            dialogBinding.tvPingStatus.text = "Menguji koneksi ke $cleanUrl..."
            dialogBinding.tvPingStatus.setTextColor(Color.parseColor("#38BDF8"))

            lifecycleScope.launch(Dispatchers.IO) {
                var isOk = false
                var msg = ""
                try {
                    val req = Request.Builder()
                        .url("$cleanUrl/api/display-data")
                        .get()
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    isOk = resp.isSuccessful
                    msg = if (isOk) "🟢 Terhubung Berhasil (HTTP ${resp.code})" else "🔴 Gagal (HTTP ${resp.code})"
                } catch (e: Exception) {
                    msg = "🔴 Gagal koneksi: ${e.message}"
                }

                withContext(Dispatchers.Main) {
                    dialogBinding.tvPingStatus.text = msg
                    dialogBinding.tvPingStatus.setTextColor(
                        if (isOk) Color.parseColor("#34D399") else Color.parseColor("#F87171")
                    )
                }
            }
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
            hideSoftKeyboard()
        }

        // =====================================================================
        // KONFIGURASI PRINTER BLUETOOTH THERMAL (58MM)
        // =====================================================================
        var pairedPrinters = BluetoothThermalPrinter.getPairedDevices(this)
        var selectedPrinterMac = session.getPrinterMac()

        fun refreshPrinterSpinner() {
            pairedPrinters = BluetoothThermalPrinter.getPairedDevices(this@MainActivity)
            if (pairedPrinters.isEmpty()) {
                val emptyAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("⚠️ Belum ada printer bluetooth yang di-pair di TV/STB"))
                dialogBinding.spinnerPrinter.adapter = emptyAdapter
            } else {
                val names = pairedPrinters.map { "${it.name} (${it.address})" }
                val spinnerAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
                dialogBinding.spinnerPrinter.adapter = spinnerAdapter

                // Pilih yang tersimpan jika ada
                val selectedIndex = pairedPrinters.indexOfFirst { it.address == selectedPrinterMac }
                if (selectedIndex >= 0) {
                    dialogBinding.spinnerPrinter.setSelection(selectedIndex)
                }
            }
        }

        // Inisialisasi input manual MAC jika ada yang tersimpan
        dialogBinding.etPrinterMac.setText(selectedPrinterMac)

        dialogBinding.switchPrinter.isChecked = session.isPrinterEnabled()
        dialogBinding.layoutPrinterOptions.visibility = if (dialogBinding.switchPrinter.isChecked) View.VISIBLE else View.GONE
        refreshPrinterSpinner()

        dialogBinding.spinnerPrinter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in pairedPrinters.indices) {
                    val p = pairedPrinters[position]
                    dialogBinding.etPrinterMac.setText(p.address)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        dialogBinding.switchPrinter.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.layoutPrinterOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                refreshPrinterSpinner()
            }
        }

        dialogBinding.btnRefreshPrinters.setOnClickListener {
            refreshPrinterSpinner()
            Toast.makeText(this@MainActivity, "Daftar printer bluetooth diperbarui", Toast.LENGTH_SHORT).show()
        }

        dialogBinding.btnTestPrint.setOnClickListener {
            val manualMac = dialogBinding.etPrinterMac.text.toString().trim()
            val targetMac = if (manualMac.isNotBlank()) {
                manualMac
            } else {
                val selectedIdx = dialogBinding.spinnerPrinter.selectedItemPosition
                if (selectedIdx in pairedPrinters.indices) pairedPrinters[selectedIdx].address else ""
            }

            if (targetMac.isBlank()) {
                dialogBinding.tvPrinterStatus.visibility = View.VISIBLE
                dialogBinding.tvPrinterStatus.text = "⚠️ Pilih printer atau masukkan MAC Address printer"
                dialogBinding.tvPrinterStatus.setTextColor(Color.parseColor("#FBBF24"))
                return@setOnClickListener
            }

            dialogBinding.tvPrinterStatus.visibility = View.VISIBLE
            dialogBinding.tvPrinterStatus.text = "⏳ Mengirim data tes cetak ke $targetMac..."
            dialogBinding.tvPrinterStatus.setTextColor(Color.parseColor("#38BDF8"))

            lifecycleScope.launch(Dispatchers.IO) {
                val testBytes = BluetoothThermalPrinter.buildTestReceiptBytes(activeSchoolName)
                val res = BluetoothThermalPrinter.printBytes(targetMac, testBytes)
                withContext(Dispatchers.Main) {
                    if (res.isSuccess) {
                        dialogBinding.tvPrinterStatus.text = "✅ ${res.getOrNull()}"
                        dialogBinding.tvPrinterStatus.setTextColor(Color.parseColor("#34D399"))
                    } else {
                        dialogBinding.tvPrinterStatus.text = "❌ ${res.exceptionOrNull()?.message}"
                        dialogBinding.tvPrinterStatus.setTextColor(Color.parseColor("#F87171"))
                    }
                }
            }
        }

        dialogBinding.btnSave.setOnClickListener {
            val inputUrl = dialogBinding.etServerUrl.text.toString().trim()
            if (inputUrl.isEmpty()) {
                dialogBinding.tilServerUrl.error = "URL tidak boleh kosong"
                return@setOnClickListener
            }
            session.saveServerBaseUrl(inputUrl)

            // Simpan Pengaturan Printer
            val isPrinterOn = dialogBinding.switchPrinter.isChecked
            session.setPrinterEnabled(isPrinterOn)

            val manualMac = dialogBinding.etPrinterMac.text.toString().trim()
            if (manualMac.isNotBlank()) {
                val matchedDevice = pairedPrinters.find { it.address.equals(manualMac, ignoreCase = true) }
                val devName = matchedDevice?.name ?: "Printer Thermal"
                session.savePrinter(manualMac, devName)
            } else if (pairedPrinters.isNotEmpty()) {
                val selectedIdx = dialogBinding.spinnerPrinter.selectedItemPosition
                if (selectedIdx in pairedPrinters.indices) {
                    val p = pairedPrinters[selectedIdx]
                    session.savePrinter(p.address, p.name)
                }
            }

            // Simpan Pengaturan TTS Voice
            val isTtsOn = dialogBinding.switchTts.isChecked
            session.setTtsEnabled(isTtsOn)
            standaloneDb.setSetting("tts_enabled", if (isTtsOn) "1" else "0")

            // Simpan Pengaturan TTS Waktu Sholat Opsional
            val isPrayerTtsOn = dialogBinding.switchPrayerTts.isChecked
            session.setPrayerTtsEnabled(isPrayerTtsOn)
            standaloneDb.setSetting("prayer_tts_enabled", if (isPrayerTtsOn) "1" else "0")

            // Simpan Running Text
            val newRunningText = dialogBinding.etRunningText.text.toString().trim()
            if (newRunningText.isNotBlank()) {
                standaloneDb.setSetting("running_text", newRunningText)
                binding.tvMarqueeAnnouncement.text = newRunningText
                binding.tvMarqueeAnnouncement.isSelected = true
            }

            // Simpan Kordinat Jadwal Sholat
            val newLat = dialogBinding.etPrayerLat.text.toString().trim()
            val newLng = dialogBinding.etPrayerLng.text.toString().trim()
            if (newLat.isNotBlank()) standaloneDb.setSetting("prayer_latitude", newLat)
            if (newLng.isNotBlank()) standaloneDb.setSetting("prayer_longitude", newLng)

            dialog.dismiss()
            hideSoftKeyboard()

            Toast.makeText(this, "Pengaturan berhasil disimpan!", Toast.LENGTH_SHORT).show()

            // Segera refresh polling display data
            startDisplayPolling()
        }

        // Tampilkan IP Web Admin Remote Port 8080
        val localIp = com.alijaya.smartschool.util.NetworkUtils.getLocalIpAddress(this)
        val adminUrl = if (localIp != "127.0.0.1") "http://$localIp:8080/settings.html" else "http://127.0.0.1:8080/settings.html"
        dialogBinding.tvWebAdminIpUrl.text = "🌐 Web Admin Remote: $adminUrl"

        // Inisialisasi status Switch TTS Voice & Pengingat Sholat
        dialogBinding.switchTts.isChecked = session.isTtsEnabled()
        dialogBinding.switchPrayerTts.isChecked = session.isPrayerTtsEnabled()

        // Tombol Tes Suara Bel & TTS
        dialogBinding.btnTestAudio.setOnClickListener {
            Toast.makeText(this, "Memutar bel dan suara TTS...", Toast.LENGTH_SHORT).show()
            audioHelper.playDingDong()
            audioHelper.speak("Tes audio sistem absensi dan antrian santri berhasil. Pengeras suara berfungsi normal.")
        }

        dialog.show()
    }

    /**
     * Otomatis cetak tiket antrian santri jika fitur printer diaktifkan
     */
    private fun printQueueTicketIfEnabled(
        queueNumber: Int,
        studentName: String,
        nis: String,
        className: String,
        teacherName: String,
        timeIn: String
    ) {
        val session = SmartSchoolApplication.sessionManager
        if (!session.isPrinterEnabled()) return

        val printerMac = session.getPrinterMac()
        if (printerMac.isBlank()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ticket = QueueTicket(
                    schoolName = activeSchoolName,
                    schoolTagline = activeSchoolTagline,
                    queueNumber = queueNumber,
                    studentName = studentName,
                    nis = nis,
                    teacherName = teacherName,
                    className = className,
                    timeIn = timeIn
                )
                val bytes = BluetoothThermalPrinter.buildQueueTicketBytes(ticket)
                BluetoothThermalPrinter.printBytes(printerMac, bytes)
            } catch (_: Exception) {
                // Jangan ganggu proses aplikasi jika printer bluetooth mati/terputus
            }
        }
    }

    // =========================================================================
    // 6. UPDATE 6 PRAYER CARDS & LIVE COUNTDOWN (NATIVE DIGITAL SIGNAGE TV)
    // =========================================================================
    private fun updatePrayerCardsUI(prayer: com.alijaya.smartschool.data.model.PrayerData?) {
        activePrayerSchedule = prayer?.schedule
        val s = prayer?.schedule
        if (s != null) {
            binding.tvSubuhTime.text = s.subuh ?: "--:--"
            binding.tvSyuruqTime.text = s.terbit ?: "--:--"
            binding.tvDzuhurTime.text = s.dzuhur ?: "--:--"
            binding.tvAsharTime.text = s.ashar ?: "--:--"
            binding.tvMaghribTime.text = s.maghrib ?: "--:--"
            binding.tvIsyaTime.text = s.isya ?: "--:--"
        }

        val nextInfo = prayer?.nextInfo
        val activeKey = nextInfo?.next?.key?.lowercase(Locale.ROOT) ?: ""

        // Reset style kartu ke normal
        binding.cardPrayerSubuh.setBackgroundResource(R.drawable.bg_prayer_card_normal)
        binding.cardPrayerSyuruq.setBackgroundResource(R.drawable.bg_prayer_card_normal)
        binding.cardPrayerDzuhur.setBackgroundResource(R.drawable.bg_prayer_card_normal)
        binding.cardPrayerAshar.setBackgroundResource(R.drawable.bg_prayer_card_normal)
        binding.cardPrayerMaghrib.setBackgroundResource(R.drawable.bg_prayer_card_normal)
        binding.cardPrayerIsya.setBackgroundResource(R.drawable.bg_prayer_card_normal)

        // Highlight kartu sholat berikutnya
        when (activeKey) {
            "subuh" -> binding.cardPrayerSubuh.setBackgroundResource(R.drawable.bg_prayer_card_active)
            "syuruq", "terbit" -> binding.cardPrayerSyuruq.setBackgroundResource(R.drawable.bg_prayer_card_active)
            "dzuhur" -> binding.cardPrayerDzuhur.setBackgroundResource(R.drawable.bg_prayer_card_active)
            "ashar" -> binding.cardPrayerAshar.setBackgroundResource(R.drawable.bg_prayer_card_active)
            "maghrib" -> binding.cardPrayerMaghrib.setBackgroundResource(R.drawable.bg_prayer_card_active)
            "isya" -> binding.cardPrayerIsya.setBackgroundResource(R.drawable.bg_prayer_card_active)
        }

        if (nextInfo?.next != null && !nextInfo.formattedCountdown.isNullOrBlank()) {
            binding.tvNextPrayerCountdown.text = "Menuju ${nextInfo.next.label ?: ""}: ${nextInfo.formattedCountdown}"
            binding.tvNextPrayerCountdown.visibility = View.VISIBLE
        } else {
            binding.tvNextPrayerCountdown.visibility = View.GONE
        }
    }

    /**
     * Memuat foto santri dengan dukungan penuh:
     * 1. Data URL Base64 (data:image/jpeg;base64,...)
     * 2. Raw Base64 string dari SQLite
     * 3. Path file lokal di storage perangkat
     * 4. Path URL HTTP / HTTPS dari server Node.js atau Kiosk Server
     */
    private fun renderStudentPhoto(rawPhoto: String?, baseUrl: String) {
        if (rawPhoto.isNullOrBlank()) {
            binding.ivStudentPhoto.setImageResource(R.drawable.ic_user_avatar)
            return
        }
        val photoStr = rawPhoto.trim()
        val target = when {
            photoStr.startsWith("data:image", ignoreCase = true) || photoStr.contains(";base64,") -> {
                photoStr
            }
            photoStr.startsWith("http://", ignoreCase = true) || photoStr.startsWith("https://", ignoreCase = true) -> {
                photoStr
            }
            photoStr.startsWith("/") -> {
                val cleanBase = baseUrl.trimEnd('/')
                "$cleanBase$photoStr"
            }
            photoStr.length > 200 && !photoStr.contains(" ") -> {
                photoStr
            }
            else -> {
                val cleanBase = baseUrl.trimEnd('/')
                "$cleanBase/uploads/photos/$photoStr"
            }
        }
        ImageLoader.load(target, binding.ivStudentPhoto, R.drawable.ic_user_avatar, circleCrop = true)
    }

    // =========================================================================
    // 7. SINKRONISASI PRESENSI EKSTERNAL DARI WEB KIOSK / REMOTE BROWSER
    // =========================================================================
    private fun renderExternalScanResult(scan: com.alijaya.smartschool.data.model.RecentAttendanceItem, baseUrl: String) {
        val session = SmartSchoolApplication.sessionManager

        resultDismissHandler.removeCallbacksAndMessages(null)
        dismissSecondsRemaining = 6

        binding.layoutRfidStandby.visibility = View.GONE
        binding.layoutRfidResult.visibility = View.VISIBLE

        val statusText = scan.status ?: "HADIR"
        val isLate = statusText == "TERLAMBAT"
        binding.tvScanResultBadge.text = "✅ PRESENSI BERHASIL ($statusText)"
        binding.tvScanResultBadge.setBackgroundResource(if (isLate) R.drawable.bg_badge_warning else R.drawable.bg_badge_success)
        binding.tvScanResultBadge.setTextColor(Color.parseColor(if (isLate) "#FBBF24" else "#34D399"))

        binding.tvStudentName.text = scan.name ?: "Santri"
        binding.tvStudentClassNis.text = "NIS: ${scan.nis ?: "-"}  •  ${scan.className ?: "Kelas Santri"}"
        binding.tvScanTimeStatus.text = "Waktu: ${scan.timeIn ?: "-"} WIB  •  Status: $statusText"

        // Foto Santri (Mendukung Base64, Local File, dan HTTP URL)
        renderStudentPhoto(scan.photo, baseUrl)

        if (scan.queueNumber != null && scan.queueNumber > 0) {
            binding.layoutQueueAssigned.visibility = View.VISIBLE
            binding.tvAssignedQueueNumber.text = "NOMOR ${String.format("%02d", scan.queueNumber)}"
            val teacherText = if (!scan.teacherName.isNullOrBlank()) "Pengajar: ${scan.teacherName}" else "Nomor Antrian Mengaji"
            binding.tvAssignedQueueClass.text = teacherText
        } else {
            binding.layoutQueueAssigned.visibility = View.GONE
        }

        val speech = if (scan.queueNumber != null && scan.queueNumber > 0) {
            val tName = scan.teacherName ?: "Ustadz"
            "Selamat datang ${scan.name}. Nomor antrian Anda ${scan.queueNumber}, pengajar $tName."
        } else {
            "Selamat datang ${scan.name}. Presensi berhasil tercatat."
        }

        if (session.isTtsEnabled()) {
            audioHelper.playBeepAndSpeak(speech, isMale = true)
        } else {
            audioHelper.playBeepSuccess()
        }

        if (scan.queueNumber != null && scan.queueNumber > 0) {
            printQueueTicketIfEnabled(
                queueNumber = scan.queueNumber,
                studentName = scan.name ?: "Santri",
                nis = scan.nis ?: "-",
                className = scan.className ?: "",
                teacherName = scan.teacherName ?: "",
                timeIn = scan.timeIn ?: ""
            )
        }

        binding.tvDismissTimer.text = "Kembali ke standby dalam $dismissSecondsRemaining detik..."
        resultDismissHandler.postDelayed(updateDismissTimerRunnable, 1000L)
    }

    // =========================================================================
    // 8. UPDATE FEED PRESENSI TERKINI HARI INI DI KOLOM KIRI STANDBY
    // =========================================================================
    private fun updateRecentAttendancesUI(recentList: List<com.alijaya.smartschool.data.model.RecentAttendanceItem>?) {
        val container = binding.layoutRecentItems
        val countBadge = binding.tvRecentCountBadge
        val emptyText = binding.tvEmptyRecentScans

        if (recentList.isNullOrEmpty()) {
            countBadge.text = "0 Hadir"
            emptyText.visibility = View.VISIBLE
            for (i in container.childCount - 1 downTo 0) {
                val child = container.getChildAt(i)
                if (child.id != R.id.tvEmptyRecentScans) {
                    container.removeViewAt(i)
                }
            }
            return
        }

        countBadge.text = "${recentList.size} Hadir"
        emptyText.visibility = View.GONE

        // Bersihkan item lama selain empty placeholder
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child.id != R.id.tvEmptyRecentScans) {
                container.removeViewAt(i)
            }
        }

        val displayLimit = minOf(4, recentList.size)
        for (i in 0 until displayLimit) {
            val item = recentList[i]
            val itemView = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(10.dpToPx(), 6.dpToPx(), 10.dpToPx(), 6.dpToPx())
                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 3.dpToPx(), 0, 3.dpToPx())
                }
                layoutParams = params
                setBackgroundColor(Color.parseColor("#0F172A"))
            }

            // Avatar initial
            val tvAvatar = android.widget.TextView(this).apply {
                val p = android.widget.LinearLayout.LayoutParams(28.dpToPx(), 28.dpToPx())
                layoutParams = p
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(Color.parseColor("#065F46"))
                setTextColor(Color.parseColor("#34D399"))
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                text = item.name?.take(1) ?: "?"
            }
            itemView.addView(tvAvatar)

            // Info Santri
            val infoLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val p = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 8.dpToPx()
                }
                layoutParams = p
            }

            val tvName = android.widget.TextView(this).apply {
                text = item.name ?: "-"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val tvClass = android.widget.TextView(this).apply {
                val qText = if (item.queueNumber != null && item.queueNumber > 0) " • Antrian #${item.queueNumber}" else ""
                text = "${item.className ?: "-"}$qText"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 9.5f
                maxLines = 1
            }
            infoLayout.addView(tvName)
            infoLayout.addView(tvClass)
            itemView.addView(infoLayout)

            // Jam & Status
            val statusLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.END
            }
            val tvTime = android.widget.TextView(this).apply {
                text = item.timeIn ?: ""
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 9.5f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            val tvStatusBadge = android.widget.TextView(this).apply {
                val isLate = item.status == "TERLAMBAT"
                text = item.status ?: "HADIR"
                setTextColor(if (isLate) Color.parseColor("#FBBF24") else Color.parseColor("#34D399"))
                textSize = 8.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            statusLayout.addView(tvTime)
            statusLayout.addView(tvStatusBadge)
            itemView.addView(statusLayout)

            container.addView(itemView)
        }
    }

    // =========================================================================
    // 9. REMOTE TV DIRECT CALL HANDLER (1-CLICK D-PAD CALLING MEJA MENGAJI)
    // =========================================================================
    private fun showRemoteFeedbackToast(message: String, isSuccess: Boolean) {
        remoteFeedbackHandler.removeCallbacks(hideRemoteFeedbackRunnable)
        binding.tvRemoteFeedbackToast.text = message
        val bgRes = if (isSuccess) R.drawable.bg_badge_primary else R.drawable.bg_badge_warning
        val textColor = if (isSuccess) Color.parseColor("#38BDF8") else Color.parseColor("#FBBF24")
        binding.tvRemoteFeedbackToast.setBackgroundResource(bgRes)
        binding.tvRemoteFeedbackToast.setTextColor(textColor)
        binding.tvRemoteFeedbackToast.visibility = View.VISIBLE
        remoteFeedbackHandler.postDelayed(hideRemoteFeedbackRunnable, 4000L)
    }

    private fun triggerRemoteAutoCall() {
        if (activeTables.isEmpty()) {
            showRemoteFeedbackToast("⚠️ Belum ada meja mengaji yang aktif saat ini.", isSuccess = false)
            return
        }
        // Cari meja pertama yang antriannya sedang menunggu (waiting > 0)
        val targetIndex = activeTables.indexOfFirst { table ->
            (table.stats?.waiting ?: 0) > 0 || !table.nextWaiting.isNullOrEmpty()
        }
        if (targetIndex >= 0) {
            triggerRemoteDeskCall(targetIndex)
        } else {
            // Default panggil Meja 1 jika semua meja berstatus kosong
            triggerRemoteDeskCall(0)
        }
    }

    private fun triggerRemoteDeskCall(tableIndex: Int) {
        val now = System.currentTimeMillis()
        if (now - lastRemoteCallTimestamp < 1800L) {
            return // Debounce tombol remote TV agar tidak tertekan ganda tanpa sengaja
        }
        lastRemoteCallTimestamp = now

        if (activeTables.isEmpty()) {
            showRemoteFeedbackToast("⚠️ Belum ada meja antrian mengaji yang aktif saat ini.", isSuccess = false)
            return
        }

        // Validasi ketersediaan meja sesuai nomor index
        if (tableIndex >= activeTables.size) {
            showRemoteFeedbackToast("⚠️ Meja ${tableIndex + 1} belum terdaftar (Hanya ada ${activeTables.size} meja aktif).", isSuccess = false)
            return
        }

        val targetTable = activeTables[tableIndex]
        val deskId = targetTable.teacherId ?: targetTable.classId ?: (tableIndex + 1)
        val deskName = targetTable.tableName ?: if (!targetTable.teacherName.isNullOrBlank()) "Meja ${targetTable.teacherName}" else "Meja ${tableIndex + 1}"

        showRemoteFeedbackToast("📢 [Remote TV] Memanggil antrian santri untuk $deskName...", isSuccess = true)

        lifecycleScope.launch(Dispatchers.IO) {
            val session = SmartSchoolApplication.sessionManager
            val baseUrl = session.getServerBaseUrl().trimEnd('/')
            val isStandalone = session.isStandaloneMode() || baseUrl.contains("127.0.0.1") || baseUrl.contains("localhost")

            if (isStandalone) {
                // 1. Mode Standalone: Eksekusi LANGSUNG ke database SQLite lokal (TIDAK kirim HTTP loopback agar tidak double call)
                try {
                    val db = com.alijaya.smartschool.server.db.SmartSchoolDatabase.getInstance(this@MainActivity)
                    val result = db.callNextQueue(deskId)
                    val isSuccess = result.optBoolean("success", false)
                    val serverMsg = result.optString("message", "")

                    withContext(Dispatchers.Main) {
                        if (isSuccess) {
                            showRemoteFeedbackToast("📢 Berhasil memanggil antrian $deskName", isSuccess = true)
                            SmartSchoolApplication.notifyQueueChanged()
                            fetchDisplayData()
                        } else {
                            val msg = if (serverMsg.isNotBlank()) serverMsg else "Semua antrian santri di $deskName sudah dipanggil."
                            showRemoteFeedbackToast("ℹ️ $msg", isSuccess = false)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showRemoteFeedbackToast("⚠️ Gagal memanggil antrian: ${e.message}", isSuccess = false)
                    }
                }
            } else {
                // 2. Mode Client-Server: Kirim HTTP POST ke server Node.js eksternal
                try {
                    val jsonPayload = org.json.JSONObject().apply {
                        put("action", "NEXT")
                        put("classId", deskId)
                        put("deskId", deskId)
                    }.toString()

                    val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

                    val primaryRequest = Request.Builder()
                        .url("$baseUrl/api/queue/remote-call")
                        .post(requestBody)
                        .header("User-Agent", "SmartSchool-NativeTV/1.1.0")
                        .build()

                    val response = try {
                        httpClient.newCall(primaryRequest).execute()
                    } catch (_: Exception) {
                        val fallbackRequest = Request.Builder()
                            .url("$baseUrl/api/queue/action")
                            .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .header("User-Agent", "SmartSchool-NativeTV/1.1.0")
                            .build()
                        httpClient.newCall(fallbackRequest).execute()
                    }

                    val bodyStr = response.body?.string() ?: ""
                    val jsonRes = try { org.json.JSONObject(bodyStr) } catch (_: Exception) { org.json.JSONObject() }
                    val isSuccess = jsonRes.optBoolean("success", false)
                    val serverMsg = jsonRes.optString("message", "")

                    withContext(Dispatchers.Main) {
                        if (isSuccess) {
                            showRemoteFeedbackToast("📢 Berhasil memanggil antrian $deskName", isSuccess = true)
                            fetchDisplayData()
                        } else {
                            val msg = if (serverMsg.isNotBlank()) serverMsg else "Semua antrian santri di $deskName sudah dipanggil."
                            showRemoteFeedbackToast("ℹ️ $msg", isSuccess = false)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showRemoteFeedbackToast("⚠️ Gagal memanggil antrian: ${e.message ?: "Koneksi terputus"}", isSuccess = false)
                    }
                }
            }
        }
    }

    // =========================================================================
    // 10. POPUP OVERLAY WAKTU SHOLAT TIBA & HIMBAUAN (20 DETIK)
    // =========================================================================
    private fun checkPrayerTimeTrigger(now: Date) {
        val schedule = activePrayerSchedule ?: return
        val timeNow = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)

        val prayerList = listOf(
            Triple("Subuh", schedule.subuh, "subuh"),
            Triple("Dzuhur", schedule.dzuhur, "dzuhur"),
            Triple("Ashar", schedule.ashar, "ashar"),
            Triple("Maghrib", schedule.maghrib, "maghrib"),
            Triple("Isya", schedule.isya, "isya")
        )

        for ((name, pTime, key) in prayerList) {
            if (!pTime.isNullOrBlank() && pTime == timeNow) {
                val triggerKey = "${todayStr}_${key}"
                if (lastTriggeredPrayerKey != triggerKey) {
                    lastTriggeredPrayerKey = triggerKey
                    showPrayerAlertPopup(name, pTime)
                }
                break
            }
        }
    }

    private fun showPrayerAlertPopup(prayerName: String, prayerTime: String) {
        isPrayerAlertActive = true
        prayerAlertSecondsRemaining = 20
        prayerAlertHandler.removeCallbacksAndMessages(null)

        binding.tvPrayerAlertTitle.text = "WAKTU SHOLAT ${prayerName.uppercase()} TELAH TIBA"
        binding.tvPrayerAlertTime.text = "Pukul $prayerTime WIB"
        binding.tvPrayerAlertCountdown.text = "Kembali ke layar antrian dalam $prayerAlertSecondsRemaining detik... (Tekan OK / BACK di Remote untuk menutup)"
        binding.layoutPrayerAlertOverlay.visibility = View.VISIBLE

        // Klik layar overlay untuk menutup instan
        binding.layoutPrayerAlertOverlay.setOnClickListener {
            hidePrayerAlertPopup()
        }

        // Suara Notifikasi Waktu Sholat (Opsional berdasarkan SessionManager)
        val session = SmartSchoolApplication.sessionManager
        if (session.isPrayerTtsEnabled()) {
            // Jika opsi TTS suara pengingat sholat diaktifkan
            val speech = "Waktu sholat $prayerName telah tiba. Mari mengambil air wudhu dan bersiap melaksanakan sholat berjamaah."
            audioHelper.playChimeAndSpeak(speech, isMale = true)
        } else {
            // Jika TTS sholat dinonaktifkan: hanya bunyikan nada chime lembut tanpa suara bicara (atau senyap jika TTS global nonaktif)
            if (session.isTtsEnabled()) {
                audioHelper.playChime()
            }
        }

        // Mulai countdown otomatis 20 detik
        prayerAlertHandler.postDelayed(updatePrayerAlertRunnable, 1000L)
    }

    private fun hidePrayerAlertPopup() {
        prayerAlertHandler.removeCallbacksAndMessages(null)
        isPrayerAlertActive = false
        binding.layoutPrayerAlertOverlay.visibility = View.GONE
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
