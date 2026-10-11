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
    private var isCallBannerActive = false
    private val callBannerHandler = Handler(Looper.getMainLooper())
    private val hideCallBannerRunnable = Runnable {
        binding.layoutActiveCallBanner.visibility = View.GONE
        isCallBannerActive = false
    }

    // Hardware Keyboard Wedge (USB RFID Reader) Buffer
    private val rfidBuffer = StringBuilder()
    private var lastKeyTimestamp = 0L
    private val SCAN_TIMEOUT_MS = 600L
    private val bufferResetHandler = Handler(Looper.getMainLooper())
    private val resetBufferRunnable = Runnable { rfidBuffer.clear() }

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

        audioHelper = BeepTtsHelper(this)

        setupRecyclerView()
        setupListeners()
        startClock()
        startDisplayPolling()
        checkAndRequestBluetoothPermissions()

        // Aktifkan Marquee Running Text di baris bawah
        binding.tvMarqueeAnnouncement.isSelected = true
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
        pollingJob?.cancel()
        clockJob?.cancel()
        bufferResetHandler.removeCallbacks(resetBufferRunnable)
        resultDismissHandler.removeCallbacksAndMessages(null)
        callBannerHandler.removeCallbacksAndMessages(null)
        audioHelper.release()
    }

    private fun setupRecyclerView() {
        adapter = QueueTableAdapter()
        binding.rvQueueTables.layoutManager = GridLayoutManager(this, 2)
        binding.rvQueueTables.adapter = adapter
    }

    private fun updateTableLayoutManager(tableCount: Int) {
        val spanCount = when {
            tableCount <= 1 -> 1
            tableCount in 2..4 -> tableCount // 2, 3, atau 4 meja tampil berdampingan penuh
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

            // Shortcut Remote TV: Tekan MENU atau SETTINGS untuk membuka pengaturan server
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                showServerSettingsDialog()
                return true
            }

            // Enter menandakan akhir dari transmisi kartu RFID reader USB
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                val cardCode = rfidBuffer.toString().trim()
                rfidBuffer.clear()
                bufferResetHandler.removeCallbacks(resetBufferRunnable)

                if (cardCode.length >= 3) {
                    processAttendanceScan(cardCode)
                    return true // Telah ditangani, jangan teruskan event enter
                }
            } else {
                val unicodeChar = event.unicodeChar
                if (unicodeChar > 0 && (Character.isLetterOrDigit(unicodeChar) || unicodeChar == '-'.code)) {
                    rfidBuffer.append(unicodeChar.toChar())
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
                    renderScanResult(scanResult, cardCode, baseUrl)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    renderScanError("Gagal terhubung: ${e.message ?: "Koneksi terputus"}", cardCode)
                }
            }
        }
    }

    private fun renderScanResult(result: ScanResponse, cardCode: String, baseUrl: String) {
        val session = SmartSchoolApplication.sessionManager

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
            if (!student.photo.isNullOrBlank()) {
                val photoUrl = if (student.photo.startsWith("http")) student.photo else "$baseUrl${student.photo}"
                ImageLoader.load(photoUrl, binding.ivStudentPhoto, R.drawable.ic_user_avatar, circleCrop = true)
            } else {
                binding.ivStudentPhoto.setImageResource(R.drawable.ic_user_avatar)
            }

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

            if (!student.photo.isNullOrBlank()) {
                val photoUrl = if (student.photo.startsWith("http")) student.photo else "$baseUrl${student.photo}"
                ImageLoader.load(photoUrl, binding.ivStudentPhoto, R.drawable.ic_user_avatar, circleCrop = true)
            } else {
                binding.ivStudentPhoto.setImageResource(R.drawable.ic_user_avatar)
            }

            val isMale = isStudentMale(student.gender, student.className, student.name)
            audioHelper.playBeepWarning()
            if (session.isTtsEnabled()) {
                audioHelper.speak("Santri ${student.name} sudah melakukan presensi hari ini.", isMale = isMale)
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

            audioHelper.playBeepWarning()
            if (session.isTtsEnabled()) {
                audioHelper.speak("Kartu tidak terdaftar.")
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
        // Status Server
        val host = try {
            val uri = android.net.Uri.parse(baseUrl)
            uri.host ?: baseUrl
        } catch (_: Exception) {
            baseUrl
        }
        binding.tvConnectionStatus.text = "🟢 Online ($host)"
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

        // Update Jadwal Sholat
        val prayerSchedule = data.prayer?.schedule
        if (prayerSchedule != null) {
            val prayerStr = "Subuh: ${prayerSchedule.subuh ?: "-"}  •  Dzuhur: ${prayerSchedule.dzuhur ?: "-"}  •  Ashar: ${prayerSchedule.ashar ?: "-"}  •  Maghrib: ${prayerSchedule.maghrib ?: "-"}  •  Isya: ${prayerSchedule.isya ?: "-"}"
            binding.tvPrayerSchedule.text = prayerStr

            val nextInfo = data.prayer.nextInfo
            if (nextInfo?.next != null && !nextInfo.formattedCountdown.isNullOrBlank()) {
                binding.tvNextPrayerCountdown.text = "Menuju ${nextInfo.next.label ?: ""}: ${nextInfo.formattedCountdown}"
                binding.tvNextPrayerCountdown.visibility = View.VISIBLE
            } else {
                binding.tvNextPrayerCountdown.visibility = View.GONE
            }
        }

        // Update Meja Mengaji
        val tables = data.tables
        if (!tables.isNullOrEmpty()) {
            binding.tvEmptyTables.visibility = View.GONE
            binding.rvQueueTables.visibility = View.VISIBLE
            binding.tvTotalTablesCount.text = "${tables.size} Meja Terdaftar"

            // Tampilkan card meja secara berdampingan (1, 2, 3, 4 meja side-by-side)
            updateTableLayoutManager(tables.size)

            adapter.submitList(tables)
        } else {
            binding.tvEmptyTables.visibility = View.VISIBLE
            binding.rvQueueTables.visibility = View.GONE
            binding.tvEmptyTables.text = "Belum ada meja antrian mengaji yang aktif hari ini"
            binding.tvTotalTablesCount.text = "0 Meja"
        }

        // Cek Panggilan Suara Terakhir (lastCall)
        val lastCall = data.lastCall
        if (lastCall != null && lastCall.queueNumber > 0) {
            val uniqueKey = "${lastCall.id}_${lastCall.queueNumber}_${lastCall.callCount}_${lastCall.calledAt}"
            if (uniqueKey != lastCallUniqueKey) {
                lastCallUniqueKey = uniqueKey
                triggerQueueCall(lastCall)
            }
        }
    }

    // =========================================================================
    // 5. TRIGGER PANGGILAN SUARA (AUDIO CHIME + NATIVE TTS) & BANNER PANGGILAN
    // =========================================================================
    private fun triggerQueueCall(lastCall: com.alijaya.smartschool.data.model.QueueCallItem) {
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

            dialog.dismiss()
            hideSoftKeyboard()

            Toast.makeText(this, "Pengaturan berhasil disimpan!", Toast.LENGTH_SHORT).show()

            // Segera refresh polling display data
            startDisplayPolling()
        }

        // Inisialisasi status Switch TTS Voice
        dialogBinding.switchTts.isChecked = session.isTtsEnabled()

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
}
