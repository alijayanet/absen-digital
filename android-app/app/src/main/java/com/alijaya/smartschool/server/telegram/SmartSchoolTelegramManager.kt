package com.alijaya.smartschool.server.telegram

import android.content.Context
import android.util.Log
import com.alijaya.smartschool.server.db.SmartSchoolDatabase
import com.alijaya.smartschool.util.BeepTtsHelper
import com.alijaya.smartschool.util.NetworkUtils
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * SmartSchoolTelegramManager
 * Mengimplementasikan Telegram Bot Long-Polling interaktif murni di Android.
 * Mengontrol pemanggilan antrian mengaji multi-meja, pengumuman suara TTS TV,
 * pergantian teks berjalan (running text), dan pengecekan rekap presensi hari ini.
 */
class SmartSchoolTelegramManager(
    private val context: Context,
    private val database: SmartSchoolDatabase,
    private val audioHelper: BeepTtsHelper,
    private val scope: CoroutineScope
) {

    private val TAG = "SmartSchoolTelegram"
    private val BASE_URL = "https://api.telegram.org/bot"

    private var pollingJob: Job? = null
    private var lastUpdateId = 0L

    var botToken: String = ""
        private set
    var chatId: String = ""
        private set
    var isEnabled: Boolean = false
        private set

    // Conversational state tracking per chat
    private val pendingRunningText = ConcurrentHashMap<Long, Boolean>() // chatId -> true
    private val pendingBroadcastTts = ConcurrentHashMap<Long, Boolean>() // chatId -> true
    private val pendingSchoolName = ConcurrentHashMap<Long, Boolean>() // chatId -> true
    private val pendingSchoolTagline = ConcurrentHashMap<Long, Boolean>() // chatId -> true

    init {
        scope.launch(Dispatchers.IO) {
            reloadConfig()
        }
    }

    suspend fun reloadConfig() = withContext(Dispatchers.IO) {
        try {
            botToken = database.getSetting("telegram_bot_token", "").trim()
            chatId = database.getSetting("telegram_chat_id", "").trim()
            val enabledStr = database.getSetting("telegram_enabled", "0").trim()
            isEnabled = enabledStr.equals("true", ignoreCase = true) || enabledStr == "1"

            Log.i(TAG, "Reloading Telegram Config: enabled=$isEnabled, hasToken=${botToken.isNotBlank()}")

            if (isEnabled && botToken.isNotBlank()) {
                startPolling()
            } else {
                stopPolling()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error reloading Telegram config: ${t.message}", t)
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        if (botToken.isBlank()) return

        pollingJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Memulai Telegram Bot Long-Polling SmartSchool...")
            deleteWebhook(botToken)

            while (isActive) {
                try {
                    val updatesJson = getUpdates(botToken, lastUpdateId + 1, timeoutSeconds = 15)
                    if (!updatesJson.isNullOrBlank()) {
                        val root = JSONObject(updatesJson)
                        if (root.optBoolean("ok", false)) {
                            val results = root.optJSONArray("result") ?: JSONArray()
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val updateId = item.getLong("update_id")
                                if (updateId > lastUpdateId) {
                                    lastUpdateId = updateId
                                }

                                if (item.has("message")) {
                                    val msg = item.getJSONObject("message")
                                    handleIncomingMessage(msg)
                                } else if (item.has("callback_query")) {
                                    val cb = item.getJSONObject("callback_query")
                                    handleCallbackQuery(cb)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Polling exception: ${e.message}")
                    delay(3000)
                }
                delay(1000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        Log.i(TAG, "Telegram Bot Polling dihentikan.")
    }

    fun isPollingActive(): Boolean = pollingJob?.isActive == true

    private suspend fun handleIncomingMessage(msg: JSONObject) = withContext(Dispatchers.IO) {
        try {
            val fromChat = msg.getJSONObject("chat")
            val targetChatId = fromChat.getLong("id")
            val text = msg.optString("text", "").trim()

            if (text.isBlank()) return@withContext
            val cmd = text.lowercase()

            // Handle BATAL/CANCEL dari aksi percakapan tertunda
            if (cmd == "batal" || cmd == "/batal" || cmd == "cancel") {
                pendingRunningText.remove(targetChatId)
                pendingBroadcastTts.remove(targetChatId)
                pendingSchoolName.remove(targetChatId)
                pendingSchoolTagline.remove(targetChatId)
                sendMessage(botToken, targetChatId, "↩️ Tindakan dibatalkan.", replyMarkupJson = buildMainMenuKeyboard())
                return@withContext
            }

            // Conversational step 1: Pending Running Text
            if (pendingRunningText.remove(targetChatId) == true) {
                database.setSetting("running_text", text)
                val reply = """
                    ✅ *Teks Berjalan (Running Text) Berhasil Diperbarui!*
                    ━━━━━━━━━━━━━━━━━━━━
                    📝 Teks baru:
                    "$text"
                    
                    📺 *Teks sekarang langsung berjalan di layar TV Kiosk.*
                """.trimIndent()
                sendMessage(botToken, targetChatId, reply, replyMarkupJson = buildMainMenuKeyboard())
                return@withContext
            }

            // Conversational step 2: Pending Broadcast TTS TV
            if (pendingBroadcastTts.remove(targetChatId) == true) {
                audioHelper.playChimeAndSpeak(text, isMale = true)
                val reply = """
                    🔊 *Pengumuman Suara Sedang Diputar di TV:*
                    ━━━━━━━━━━━━━━━━━━━━
                    📢 "$text"
                """.trimIndent()
                sendMessage(botToken, targetChatId, reply, replyMarkupJson = buildMainMenuKeyboard())
                return@withContext
            }

            // Conversational step 3: Pending Ganti Nama Sekolah / Lembaga
            if (pendingSchoolName.remove(targetChatId) == true) {
                database.setSetting("school_name", text)
                val reply = """
                    ✅ *Nama Lembaga / Sekolah Berhasil Diperbarui!*
                    ━━━━━━━━━━━━━━━━━━━━
                    🏫 Nama Baru: *$text*
                    
                    📺 *Nama lembaga telah langsung berubah di Layar TV Kiosk & Web Remote.*
                """.trimIndent()
                sendMessage(botToken, targetChatId, reply, replyMarkupJson = buildMainMenuKeyboard())
                return@withContext
            }

            // Conversational step 4: Pending Ganti Tagline Instansi
            if (pendingSchoolTagline.remove(targetChatId) == true) {
                database.setSetting("school_tagline", text)
                val reply = """
                    ✅ *Tagline Lembaga Berhasil Diperbarui!*
                    ━━━━━━━━━━━━━━━━━━━━
                    📌 Tagline Baru: *$text*
                """.trimIndent()
                sendMessage(botToken, targetChatId, reply, replyMarkupJson = buildMainMenuKeyboard())
                return@withContext
            }

            when {
                // Menu Utama
                cmd == "/start" || cmd == "/menu" || cmd == "menu" -> {
                    sendMainMenu(targetChatId)
                }

                // Submenu Meja Antrian
                cmd == "/meja" || cmd == "/antrian" || cmd == "antrian" -> {
                    sendMejaList(targetChatId)
                }

                // Submenu Rekap Presensi
                cmd == "/rekap" || cmd == "/presensi" || cmd == "rekap" -> {
                    sendDailyAttendanceReport(targetChatId)
                }

                // Info Status TV & Server
                cmd == "/status" || cmd == "status" -> {
                    sendStatusInfo(targetChatId)
                }

                // Submenu / Profil Sekolah
                cmd == "/profil" || cmd == "/sekolah" || cmd == "sekolah" || cmd == "/profile" -> {
                    sendSchoolProfileMenu(targetChatId)
                }

                // Ganti nama sekolah command: /setnama <nama baru>
                cmd.startsWith("/setnama") || cmd.startsWith("/namasekolah") -> {
                    val newName = if (cmd.startsWith("/setnama")) {
                        text.removePrefix("/setnama").trim()
                    } else {
                        text.removePrefix("/namasekolah").trim()
                    }

                    if (newName.isBlank()) {
                        pendingSchoolName[targetChatId] = true
                        val curName = database.getSetting("school_name", "-")
                        val prompt = """
                            🏫 *GANTI NAMA LEMBAGA / SEKOLAH*
                            ━━━━━━━━━━━━━━━━━━━━
                            📌 *Nama saat ini:* *$curName*
                            
                            Silakan ketik nama lembaga/sekolah yang baru:
                            _Contoh:_ `TPQ & MADIN AL-IKHLAS`
                            
                            _(Ketik 'batal' untuk membatalkan)_
                        """.trimIndent()
                        sendMessage(botToken, targetChatId, prompt)
                        return@withContext
                    }

                    database.setSetting("school_name", newName)
                    val reply = """
                        ✅ *Nama Lembaga / Sekolah Berhasil Diperbarui!*
                        ━━━━━━━━━━━━━━━━━━━━
                        🏫 Nama Baru: *$newName*
                        
                        📺 *Nama lembaga telah langsung berubah di Layar TV Kiosk & Web Remote.*
                    """.trimIndent()
                    sendMessage(botToken, targetChatId, reply, replyMarkupJson = buildMainMenuKeyboard())
                }

                // Ganti tagline sekolah command: /settagline <tagline baru>
                cmd.startsWith("/settagline") || cmd.startsWith("/tagline") -> {
                    val newTagline = if (cmd.startsWith("/settagline")) {
                        text.removePrefix("/settagline").trim()
                    } else {
                        text.removePrefix("/tagline").trim()
                    }

                    if (newTagline.isBlank()) {
                        pendingSchoolTagline[targetChatId] = true
                        val curTagline = database.getSetting("school_tagline", "-")
                        val prompt = """
                            📌 *GANTI TAGLINE LEMBAGA*
                            ━━━━━━━━━━━━━━━━━━━━
                            📌 *Tagline saat ini:* _${curTagline}_
                            
                            Silakan ketik slogan/tagline instansi yang baru:
                            _Contoh:_ `Membentuk Generasi Qur'ani, Cerdas dan Berakhlak Mulia`
                            
                            _(Ketik 'batal' untuk membatalkan)_
                        """.trimIndent()
                        sendMessage(botToken, targetChatId, prompt)
                        return@withContext
                    }

                    database.setSetting("school_tagline", newTagline)
                    val reply = """
                        ✅ *Tagline Lembaga Berhasil Diperbarui!*
                        ━━━━━━━━━━━━━━━━━━━━
                        📌 Tagline Baru: *$newTagline*
                    """.trimIndent()
                    sendMessage(botToken, targetChatId, reply, replyMarkupJson = buildMainMenuKeyboard())
                }

                // Running text command langsung: /runningtext <teks>
                cmd.startsWith("/runningtext") || cmd.startsWith("/teks") -> {
                    val newText = if (cmd.startsWith("/runningtext")) {
                        text.removePrefix("/runningtext").trim()
                    } else {
                        text.removePrefix("/teks").trim()
                    }

                    if (newText.isBlank()) {
                        pendingRunningText[targetChatId] = true
                        sendMessage(botToken, targetChatId, "📝 Silakan ketik *teks pengumuman berjalan* yang baru untuk layar TV Kiosk:\n_(Ketik 'batal' untuk membatalkan)_")
                        return@withContext
                    }

                    database.setSetting("running_text", newText)
                    val reply = """
                        ✅ *Teks Berjalan TV Berhasil Diperbarui!*
                        ━━━━━━━━━━━━━━━━━━━━
                        📝 Teks baru:
                        "$newText"
                    """.trimIndent()
                    sendMessage(botToken, targetChatId, reply, replyMarkupJson = buildMainMenuKeyboard())
                }

                // Broadcast TTS command langsung: /tts <kalimat> atau /suara <kalimat>
                cmd.startsWith("/tts") || cmd.startsWith("/suara") -> {
                    val speechText = if (cmd.startsWith("/tts")) {
                        text.removePrefix("/tts").trim()
                    } else {
                        text.removePrefix("/suara").trim()
                    }

                    if (speechText.isBlank()) {
                        pendingBroadcastTts[targetChatId] = true
                        sendMessage(botToken, targetChatId, "🎙️ Silakan ketik *kalimat pengumuman* yang ingin disuarakan ke speaker TV:\n_(Ketik 'batal' untuk membatalkan)_")
                        return@withContext
                    }

                    audioHelper.playChimeAndSpeak(speechText, isMale = true)
                    sendMessage(botToken, targetChatId, "🔊 *Pengumuman Suara Berhasil Dikirim ke TV:*\n\"$speechText\"", replyMarkupJson = buildMainMenuKeyboard())
                }

                // Default Help
                else -> {
                    sendMainMenu(targetChatId)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error handling incoming message: ${t.message}", t)
        }
    }

    private suspend fun handleCallbackQuery(cb: JSONObject) = withContext(Dispatchers.IO) {
        try {
            val queryId = cb.getString("id")
            val fromUser = cb.getJSONObject("from")
            val targetChatId = fromUser.getLong("id")
            val callbackData = cb.optString("data", "")

            answerCallbackQuery(botToken, queryId)

            when {
                callbackData == "menu_main" -> {
                    sendMainMenu(targetChatId)
                }

                callbackData == "menu_meja" -> {
                    sendMejaList(targetChatId)
                }

                callbackData.startsWith("desk_detail_") -> {
                    val deskId = callbackData.removePrefix("desk_detail_").toIntOrNull() ?: 1
                    sendDeskDetail(targetChatId, deskId)
                }

                // Aksi Meja: Panggil Berikutnya
                callbackData.startsWith("call_next_") -> {
                    val deskId = callbackData.removePrefix("call_next_").toIntOrNull() ?: 1
                    executeCallNext(targetChatId, deskId)
                }

                // Aksi Meja: Panggil Ulang (Recall)
                callbackData.startsWith("call_recall_") -> {
                    val parts = callbackData.removePrefix("call_recall_").split("_")
                    val queueId = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val deskId = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    executeRecall(targetChatId, queueId, deskId)
                }

                // Aksi Meja: Lewati (Skip)
                callbackData.startsWith("call_skip_") -> {
                    val parts = callbackData.removePrefix("call_skip_").split("_")
                    val queueId = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val deskId = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    executeSkip(targetChatId, queueId, deskId)
                }

                // Aksi Meja: Selesai (Finish)
                callbackData.startsWith("call_finish_") -> {
                    val parts = callbackData.removePrefix("call_finish_").split("_")
                    val queueId = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val deskId = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    executeFinish(targetChatId, queueId, deskId)
                }

                callbackData == "menu_rekap" -> {
                    sendDailyAttendanceReport(targetChatId)
                }

                callbackData == "menu_runningtext" -> {
                    pendingRunningText[targetChatId] = true
                    val currentText = database.getSetting("running_text", "-")
                    val prompt = """
                        📝 *UBAH TEKS BERJALAN (RUNNING TEXT)*
                        ━━━━━━━━━━━━━━━━━━━━
                        📌 *Teks saat ini:*
                        "$currentText"
                        
                        Silakan ketik teks pengumuman baru untuk layar TV Kiosk:
                        _(Ketik 'batal' untuk membatalkan)_
                    """.trimIndent()
                    sendMessage(botToken, targetChatId, prompt)
                }

                callbackData == "menu_tts" -> {
                    pendingBroadcastTts[targetChatId] = true
                    val prompt = """
                        🎙️ *PENGUMUMAN SUARA (TTS SPEAKER TV)*
                        ━━━━━━━━━━━━━━━━━━━━
                        Silakan ketik kalimat pengumuman yang ingin disuarakan di speaker TV:
                        _Contoh:_ `Perhatian santri putri kelas Iqro 2 harap berkumpul di aula depan.`
                        
                        _(Ketik 'batal' untuk membatalkan)_
                    """.trimIndent()
                    sendMessage(botToken, targetChatId, prompt)
                }

                callbackData == "cmd_chime" -> {
                    audioHelper.playChime()
                    sendMessage(botToken, targetChatId, "🔔 Nada ding-dong / airport chime dibunyikan di speaker TV!")
                }

                callbackData == "cmd_status" -> {
                    sendStatusInfo(targetChatId)
                }

                callbackData == "menu_profile" -> {
                    sendSchoolProfileMenu(targetChatId)
                }

                callbackData == "toggle_queue_per_teacher" -> {
                    val currentVal = database.getSetting("queue_per_teacher", "1")
                    val newVal = if (currentVal == "1") "0" else "1"
                    database.setSetting("queue_per_teacher", newVal)
                    val modeName = if (newVal == "1") "✅ Terpisah Per Meja / Ustadz (Santri Ust A nomor 1, Ust B nomor 1)" else "🌐 Digabungkan Global (1, 2, 3, ...)"
                    sendMessage(botToken, targetChatId, "🔢 *Pengaturan Berhasil Diubah!*\nMode Nomor Antrian sekarang: *$modeName*")
                    sendSchoolProfileMenu(targetChatId)
                }

                callbackData == "set_school_name" -> {
                    pendingSchoolName[targetChatId] = true
                    val curName = database.getSetting("school_name", "-")
                    val prompt = """
                        🏫 *GANTI NAMA LEMBAGA / SEKOLAH*
                        ━━━━━━━━━━━━━━━━━━━━
                        📌 *Nama saat ini:* *$curName*
                        
                        Silakan ketik nama lembaga/sekolah yang baru:
                        _Contoh:_ `TPQ & MADIN AL-IKHLAS`
                        
                        _(Ketik 'batal' untuk membatalkan)_
                    """.trimIndent()
                    sendMessage(botToken, targetChatId, prompt)
                }

                callbackData == "set_school_tagline" -> {
                    pendingSchoolTagline[targetChatId] = true
                    val curTagline = database.getSetting("school_tagline", "-")
                    val prompt = """
                        📌 *GANTI TAGLINE LEMBAGA*
                        ━━━━━━━━━━━━━━━━━━━━
                        📌 *Tagline saat ini:* _${curTagline}_
                        
                        Silakan ketik slogan/tagline instansi yang baru:
                        _Contoh:_ `Membentuk Generasi Qur'ani, Cerdas dan Berakhlak Mulia`
                        
                        _(Ketik 'batal' untuk membatalkan)_
                    """.trimIndent()
                    sendMessage(botToken, targetChatId, prompt)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error handling callback query: ${t.message}", t)
        }
    }

    private suspend fun sendMainMenu(chatId: Long) {
        val schoolName = database.getSetting("school_name", "SMART SCHOOL STANDALONE")
        val todayStats = database.getTodayStats()
        val totalSantri = todayStats.optInt("total_students", 0)
        val hadir = todayStats.optInt("hadir", 0)
        val terlambat = todayStats.optInt("terlambat", 0)
        val totalAbsen = todayStats.optInt("total_absen", 0)

        val text = """
            🕌 *PANEL KONTROL TELEGRAM*
            *${schoolName.uppercase()}*
            ━━━━━━━━━━━━━━━━━━━━
            👥 Total Santri: *$totalSantri Santri*
            ✅ Hadir Hari Ini: *$totalAbsen Santri* (Hadir: $hadir, Telat: $terlambat)
            
            Silakan pilih menu interaktif di bawah ini untuk memanggil antrian mengaji atau mengontrol TV Kiosk:
        """.trimIndent()

        sendMessage(botToken, chatId, text, replyMarkupJson = buildMainMenuKeyboard())
    }

    private fun buildMainMenuKeyboard(): String {
        val rows = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "📢 Panggil Antrian Meja"); put("callback_data", "menu_meja") })
                put(JSONObject().apply { put("text", "📊 Rekap Presensi"); put("callback_data", "menu_rekap") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "📝 Teks Berjalan TV"); put("callback_data", "menu_runningtext") })
                put(JSONObject().apply { put("text", "🎙️ Pengumuman Suara"); put("callback_data", "menu_tts") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "🏫 Profil Lembaga / TV"); put("callback_data", "menu_profile") })
                put(JSONObject().apply { put("text", "🔔 Bunyikan Bel TV"); put("callback_data", "cmd_chime") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "ℹ️ Status Server TV"); put("callback_data", "cmd_status") })
            })
        }
        return JSONObject().apply { put("inline_keyboard", rows) }.toString()
    }

    private suspend fun sendSchoolProfileMenu(chatId: Long) {
        val schoolName = database.getSetting("school_name", "MUSHOLLA AL MUHAJIRIN")
        val schoolTagline = database.getSetting("school_tagline", "Sistem Presensi & Antrian Mengaji Digital")
        val entityType = database.getSetting("entity_type", "santri")
        val principal = database.getSetting("school_principal_name", "-")
        val queuePerTeacher = database.getSetting("queue_per_teacher", "1") == "1"
        val queueModeStr = if (queuePerTeacher) "✅ Pisah Per Meja (Mulai #01 per Ustadz)" else "🌐 Gabungan Global (1, 2, 3, ...)"

        val msg = """
            🏫 *PROFIL LEMBAGA & PENGATURAN*
            ━━━━━━━━━━━━━━━━━━━━
            🏛️ *Nama Lembaga:*
            *$schoolName*
            
            📌 *Slogan / Tagline:*
            _${schoolTagline}_
            
            👤 *Pimpinan:* $principal
            🕌 *Mode Istilah:* ${entityType.uppercase()}
            🔢 *Mode Nomor Antrian:* $queueModeStr
            ━━━━━━━━━━━━━━━━━━━━
            Silakan pilih pengaturan yang ingin Anda ubah:
        """.trimIndent()

        val toggleLabel = if (queuePerTeacher) "🔄 Ubah ke Mode: Gabungan Global" else "🔄 Ubah ke Mode: Pisah Per Meja"

        val rows = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "✏️ Ubah Nama Lembaga"); put("callback_data", "set_school_name") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "✏️ Ubah Slogan / Tagline"); put("callback_data", "set_school_tagline") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", toggleLabel); put("callback_data", "toggle_queue_per_teacher") })
            })
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "🏠 Menu Utama"); put("callback_data", "menu_main") })
            })
        }

        sendMessage(botToken, chatId, msg, replyMarkupJson = JSONObject().apply { put("inline_keyboard", rows) }.toString())
    }

    private suspend fun sendMejaList(chatId: Long) {
        val displayData = database.getDisplayData()
        val tables = displayData.optJSONArray("tables") ?: JSONArray()

        if (tables.length() == 0) {
            val msg = "⚠️ Belum ada meja ustadz yang aktif atau terdaftar di sistem."
            sendMessage(botToken, chatId, msg, replyMarkupJson = buildMainMenuKeyboard())
            return
        }

        val rows = JSONArray()
        val sb = StringBuilder()
        sb.append("📋 *PILIH MEJA USTADZ UNTUK MEMANGGIL:*\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")

        for (i in 0 until tables.length()) {
            val t = tables.getJSONObject(i)
            val deskNum = t.optInt("deskNumber", i + 1)
            val deskId = t.optInt("tableId", t.optInt("classId", i + 1))
            val deskName = t.optString("tableName", "Meja $deskNum")
            val current = t.optJSONObject("current")
            val nextWaiting = t.optJSONArray("nextWaiting") ?: JSONArray()
            val stats = t.optJSONObject("stats") ?: JSONObject()
            val waitingCount = stats.optInt("waiting", nextWaiting.length())

            val curStudentName = if (current != null) current.optString("student_name", "Sedang mengaji") else "Standby (Kosong)"
            val curIcon = if (current != null) "🟢" else "⚪"

            sb.append("*$deskNum. $deskName*\n")
            sb.append("   $curIcon Santri: $curStudentName\n")
            sb.append("   ⏳ Antrian Menunggu: *$waitingCount santri*\n\n")

            rows.put(JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "👉 Buka $deskName ($waitingCount menunggu)")
                    put("callback_data", "desk_detail_$deskId")
                })
            })
        }

        rows.put(JSONArray().apply {
            put(JSONObject().apply { put("text", "🏠 Menu Utama"); put("callback_data", "menu_main") })
        })

        sendMessage(botToken, chatId, sb.toString(), replyMarkupJson = JSONObject().apply { put("inline_keyboard", rows) }.toString())
    }

    private suspend fun sendDeskDetail(chatId: Long, deskId: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val deskData = database.getDeskData(deskId, today)
        val classInfo = deskData.optJSONObject("classInfo") ?: JSONObject()
        val deskName = classInfo.optString("name", "Meja $deskId")
        val teacherName = classInfo.optString("teacher_name", "Ustadz")
        val current = deskData.optJSONObject("current")
        val waitingList = deskData.optJSONArray("waitingList") ?: JSONArray()
        val stats = deskData.optJSONObject("stats") ?: JSONObject()

        val curQueueId = current?.optInt("id", 0) ?: 0
        val curQueueNum = current?.optInt("queue_number", 0) ?: 0
        val curStudentName = current?.optString("student_name", "-") ?: "-"
        val curCalledAt = current?.optString("called_at", "-") ?: "-"
        val curCallCount = current?.optInt("call_count", 1) ?: 1

        val totalWaiting = stats.optInt("waiting", waitingList.length())
        val totalCompleted = stats.optInt("completed", 0)

        val sb = StringBuilder()
        sb.append("🕌 *KONTROL MEJA: $deskName*\n")
        sb.append("👤 Pengajar: *$teacherName*\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")

        if (current != null && curQueueId > 0) {
            sb.append("🟢 *SEDANG MENGAJI / DIPANGGIL:*\n")
            sb.append("   • Nomor: *#$curQueueNum*\n")
            sb.append("   • Nama: *$curStudentName*\n")
            sb.append("   • Waktu Panggil: `$curCalledAt` (Dipanggil $curCallCount kali)\n\n")
        } else {
            sb.append("⚪ *STATUS: STANDBY (MEJA KOSONG)*\n")
            sb.append("   Belum ada santri yang sedang mengaji di meja ini.\n\n")
        }

        sb.append("📊 *Statistik Meja Hari Ini:*\n")
        sb.append("⏳ Antrian Menunggu: *$totalWaiting santri*\n")
        sb.append("✅ Selesai Mengaji: *$totalCompleted santri*\n\n")

        if (waitingList.length() > 0) {
            sb.append("📋 *3 Antrian Teratas Menunggu:*\n")
            for (w in 0 until minOf(3, waitingList.length())) {
                val item = waitingList.getJSONObject(w)
                sb.append("   ${w + 1}. *#${item.optInt("queue_number")}* - ${item.optString("student_name")}\n")
            }
            if (waitingList.length() > 3) {
                sb.append("   _...dan ${waitingList.length() - 3} santri lainnya_\n")
            }
        } else {
            sb.append("ℹ️ _Tidak ada antrian santri menunggu di meja ini._\n")
        }

        val rows = JSONArray()

        // Baris 1: Tombol Aksi Utama
        val actionRow1 = JSONArray()
        actionRow1.put(JSONObject().apply {
            put("text", "📢 Panggil Berikutnya")
            put("callback_data", "call_next_$deskId")
        })
        if (current != null && curQueueId > 0) {
            actionRow1.put(JSONObject().apply {
                put("text", "🔁 Panggil Ulang")
                put("callback_data", "call_recall_${curQueueId}_$deskId")
            })
        }
        rows.put(actionRow1)

        // Baris 2: Tombol Selesai & Lewati jika ada yang sedang dipanggil
        if (current != null && curQueueId > 0) {
            val actionRow2 = JSONArray()
            actionRow2.put(JSONObject().apply {
                put("text", "✅ Selesai")
                put("callback_data", "call_finish_${curQueueId}_$deskId")
            })
            actionRow2.put(JSONObject().apply {
                put("text", "⏭️ Lewati")
                put("callback_data", "call_skip_${curQueueId}_$deskId")
            })
            rows.put(actionRow2)
        }

        // Baris 3: Navigasi
        val navRow = JSONArray().apply {
            put(JSONObject().apply { put("text", "🔄 Refresh"); put("callback_data", "desk_detail_$deskId") })
            put(JSONObject().apply { put("text", "🔙 Pilih Meja Lain"); put("callback_data", "menu_meja") })
        }
        rows.put(navRow)

        sendMessage(botToken, chatId, sb.toString(), replyMarkupJson = JSONObject().apply { put("inline_keyboard", rows) }.toString())
    }

    private suspend fun executeCallNext(chatId: Long, deskId: Int) {
        val result = database.callNextQueue(deskId)
        val success = result.optBoolean("success", false)
        val msg = result.optString("message", "")

        if (success) {
            val q = result.optJSONObject("queue")
            val sentence = q?.optString("speechSentence", "") ?: ""
            val qNum = q?.optInt("queue_number", 0) ?: 0
            val sName = q?.optString("student_name", "Santri") ?: "Santri"
            val tName = q?.optString("teacher_name", "Ustadz") ?: "Ustadz"

            // Delegasikan pemutaran suara Chime + TTS & banner visual ke layar TV utama
            if (com.alijaya.smartschool.SmartSchoolApplication.onQueueChangedListener != null) {
                com.alijaya.smartschool.SmartSchoolApplication.notifyQueueChanged()
            } else if (sentence.isNotBlank()) {
                // Fallback hanya jika MainActivity sedang tidak aktif
                audioHelper.playChimeAndSpeak(sentence, isMale = true)
            }

            val confirmMsg = """
                📢 *PANGGILAN BERIKUTNYA DIPROSES!*
                ━━━━━━━━━━━━━━━━━━━━
                🔢 Nomor Antrian: *#$qNum*
                👤 Santri: *$sName*
                🕌 Menuju: *$tName*
                
                🔊 Suara panggilan telah disuarakan di speaker TV.
            """.trimIndent()
            sendMessage(botToken, chatId, confirmMsg)
        } else {
            sendMessage(botToken, chatId, "ℹ️ $msg")
        }

        sendDeskDetail(chatId, deskId)
    }

    private suspend fun executeRecall(chatId: Long, queueId: Int, deskId: Int) {
        val result = database.recallQueue(queueId)
        val success = result.optBoolean("success", false)

        if (success) {
            val called = result.optJSONObject("called")
            val sentence = called?.optString("speechSentence", "") ?: ""
            val sName = called?.optString("student_name", "Santri") ?: "Santri"
            val qNum = called?.optInt("queue_number", 0) ?: 0

            // Delegasikan pemutaran suara Chime + TTS & banner visual ke layar TV utama
            if (com.alijaya.smartschool.SmartSchoolApplication.onQueueChangedListener != null) {
                com.alijaya.smartschool.SmartSchoolApplication.notifyQueueChanged()
            } else if (sentence.isNotBlank()) {
                // Fallback hanya jika MainActivity sedang tidak aktif
                audioHelper.playChimeAndSpeak(sentence, isMale = true)
            }

            sendMessage(botToken, chatId, "🔁 *Panggilan Ulang Disuarakan!*\nSantri: *$sName* (#$qNum)")
        } else {
            sendMessage(botToken, chatId, "❌ Gagal memanggil ulang antrian.")
        }

        sendDeskDetail(chatId, deskId)
    }

    private suspend fun executeSkip(chatId: Long, queueId: Int, deskId: Int) {
        val result = database.skipQueue(queueId)
        val success = result.optBoolean("success", false)
        val msg = result.optString("message", "Santri dilewati.")

        if (success) {
            com.alijaya.smartschool.SmartSchoolApplication.notifyQueueChanged()
            sendMessage(botToken, chatId, "⏭️ $msg")
        } else {
            sendMessage(botToken, chatId, "❌ $msg")
        }

        sendDeskDetail(chatId, deskId)
    }

    private suspend fun executeFinish(chatId: Long, queueId: Int, deskId: Int) {
        val result = database.finishQueue(queueId)
        val success = result.optBoolean("success", false)
        val msg = result.optString("message", "Santri telah selesai.")

        if (success) {
            com.alijaya.smartschool.SmartSchoolApplication.notifyQueueChanged()
            sendMessage(botToken, chatId, "✅ $msg")
        } else {
            sendMessage(botToken, chatId, "❌ $msg")
        }

        sendDeskDetail(chatId, deskId)
    }

    private suspend fun sendDailyAttendanceReport(chatId: Long) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dateHuman = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())
        val stats = database.getTodayStats()

        val totalStudents = stats.optInt("total_students", 0)
        val hadir = stats.optInt("hadir", 0)
        val terlambat = stats.optInt("terlambat", 0)
        val totalAbsen = stats.optInt("total_absen", 0)
        val belumAbsen = stats.optInt("belum_absen", 0)

        // Ambil 5 scan terakhir
        val recentScans = database.getRecentScans(5)

        val sb = StringBuilder()
        sb.append("📊 *REKAP PRESENSI HARI INI*\n")
        sb.append("🗓️ $dateHuman\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")
        sb.append("👥 Total Santri Terdaftar: *$totalStudents Orang*\n")
        sb.append("🟢 Hadir Tepat Waktu: *$hadir Santri*\n")
        sb.append("🟡 Hadir Terlambat: *$terlambat Santri*\n")
        sb.append("📈 Total Sudah Presensi: *$totalAbsen Santri*\n")
        sb.append("⚪ Belum Presensi: *$belumAbsen Santri*\n\n")

        if (recentScans.length() > 0) {
            sb.append("🕒 *5 Presensi Terakhir:* \n")
            for (i in 0 until recentScans.length()) {
                val sc = recentScans.getJSONObject(i)
                val statusIcon = if (sc.optString("status") == "HADIR") "🟢" else "🟡"
                sb.append("$statusIcon `${sc.optString("time_in")}` - *${sc.optString("name")}* (${sc.optString("class_name")})\n")
            }
        }

        val rows = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "📢 Panggil Antrian"); put("callback_data", "menu_meja") })
                put(JSONObject().apply { put("text", "🏠 Menu Utama"); put("callback_data", "menu_main") })
            })
        }

        sendMessage(botToken, chatId, sb.toString(), replyMarkupJson = JSONObject().apply { put("inline_keyboard", rows) }.toString())
    }

    private suspend fun sendStatusInfo(chatId: Long) {
        val ip = NetworkUtils.getLocalIpAddress(context)
        val schoolName = database.getSetting("school_name", "SMART SCHOOL STANDALONE")
        val audioMode = database.getSetting("tts_voice_mode", "ONLINE_NATURAL")
        val audioLabel = if (audioMode == "ONLINE_NATURAL") "Suara Alami Manusia (Online)" else "TTS Offline Native STB"

        val msg = """
            📺 *STATUS SERVER TV & KIOSK*
            ━━━━━━━━━━━━━━━━━━━━
            🕌 Lembaga: *$schoolName*
            🌐 IP URL LAN: `http://$ip:8080`
            🎙️ Mesin Suara: *$audioLabel*
            🔔 Nada Chime: *Aktif (Harmonic PCM Airport)*
            🤖 Bot Telegram: *Online & Long-Polling Aktif*
            ━━━━━━━━━━━━━━━━━━━━
            _Buka URL di atas via browser HP/Laptop untuk membuka Web Remote Admin._
        """.trimIndent()

        val rows = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONObject().apply { put("text", "🔔 Tes Bel TV"); put("callback_data", "cmd_chime") })
                put(JSONObject().apply { put("text", "🏠 Menu Utama"); put("callback_data", "menu_main") })
            })
        }

        sendMessage(botToken, chatId, msg, replyMarkupJson = JSONObject().apply { put("inline_keyboard", rows) }.toString())
    }

    // --- HTTP Telegram API Helper Methods ---

    suspend fun testTelegramMessage(token: String, targetChatIdStr: String): Result<String> = withContext(Dispatchers.IO) {
        val targetChatId = targetChatIdStr.toLongOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Format Chat ID tidak valid (harus berupa angka)"))

        val schoolName = database.getSetting("school_name", "Smart School Standalone")
        val ok = sendMessage(
            token,
            targetChatId,
            "🔔 *Tes Notifikasi Bot Telegram*\n\n✅ Koneksi bot Telegram ke Android Smart School *$schoolName* berhasil terhubung!\nKetik /start atau klik tombol menu untuk membuka panel kontrol interaktif.",
            replyMarkupJson = buildMainMenuKeyboard()
        )

        if (ok) {
            Result.success("Pesan uji coba berhasil dikirim ke Telegram!")
        } else {
            Result.failure(Exception("Gagal mengirim ke Telegram. Pastikan Bot Token & Chat ID benar, serta Anda sudah menekan tombol Start pada bot Telegram."))
        }
    }

    suspend fun sendMessage(
        token: String,
        targetChatId: Long,
        text: String,
        replyMarkupJson: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL$token/sendMessage")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val payload = JSONObject().apply {
                put("chat_id", targetChatId)
                put("text", text)
                put("parse_mode", "Markdown")
                if (!replyMarkupJson.isNullOrBlank()) {
                    put("reply_markup", JSONObject(replyMarkupJson))
                }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            if (conn.responseCode != 200) {
                // Retry plain jika formatting markdown gagal
                return@withContext sendPlainMessage(token, targetChatId, text, replyMarkupJson)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error: ${e.message}")
            false
        }
    }

    private suspend fun sendPlainMessage(
        token: String,
        targetChatId: Long,
        text: String,
        replyMarkupJson: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanText = text.replace("*", "").replace("`", "").replace("_", "")
            val url = URL("$BASE_URL$token/sendMessage")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            val payload = JSONObject().apply {
                put("chat_id", targetChatId)
                put("text", cleanText)
                if (!replyMarkupJson.isNullOrBlank()) {
                    put("reply_markup", JSONObject(replyMarkupJson))
                }
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun answerCallbackQuery(token: String, callbackQueryId: String, text: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL$token/answerCallbackQuery")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
            }
            val payload = JSONObject().apply {
                put("callback_query_id", callbackQueryId)
                if (!text.isNullOrBlank()) put("text", text)
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun deleteWebhook(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL$token/deleteWebhook?drop_pending_updates=false")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun getUpdates(token: String, offset: Long, timeoutSeconds: Int = 15): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL$token/getUpdates?offset=$offset&timeout=$timeoutSeconds")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = (timeoutSeconds + 15) * 1000
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
