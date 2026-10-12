package com.alijaya.smartschool.server

import android.content.Context
import android.util.Log
import com.alijaya.smartschool.server.db.SmartSchoolDatabase
import com.alijaya.smartschool.server.whatsapp.CloudWhatsAppClient
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KioskHttpServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD("0.0.0.0", port) {

    companion object {
        private const val TAG = "KioskHttpServer"
        private const val MIME_JSON = "application/json"
        private const val MIME_HTML = "text/html"
        private const val MIME_JS = "application/javascript"
        private const val MIME_CSS = "text/css"
    }

    private val db = SmartSchoolDatabase.getInstance(context)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "Request: $method $uri")

        // Tangani preflight OPTIONS CORS
        if (method == Method.OPTIONS) {
            val response = newFixedLengthResponse(Response.Status.OK, MIME_JSON, "{}")
            addCorsHeaders(response)
            return response
        }

        try {
            // Routing API REST & Halaman Web
            val response = when {
                // 0. API Autentikasi Admin
                uri == "/api/login" && method == Method.POST -> handleAdminLogin(session)
                uri == "/api/auth/check" && method == Method.GET -> handleAuthCheck()

                // 1. API Absensi Scan Kiosk & Laporan
                uri == "/api/attendance/scan" && method == Method.POST -> handleAttendanceScan(session)
                uri == "/api/attendance/recent" && method == Method.GET -> handleRecentAttendances()
                uri == "/api/attendances/report" && method == Method.GET -> handleAttendanceReport(session)
                uri == "/api/attendances/export/csv" && method == Method.GET -> handleAttendanceExportCsv(session)

                // 2. API Statistik Dashboard & Antrian Display TV
                uri == "/api/dashboard/stats" && method == Method.GET -> handleDashboardStats()
                (uri == "/api/display-data" || uri == "/api/queue/display-data") && method == Method.GET -> handleGetDisplayData()
                uri == "/api/queue/desk-data" && method == Method.GET -> handleGetQueueDeskData(session)
                uri == "/api/queue/action" && method == Method.POST -> handleQueueAction(session)

                // 3. API Data Santri / Siswa (CRUD + Import/Export)
                uri == "/api/students" && method == Method.GET -> handleGetStudents()
                uri == "/api/students" && method == Method.POST -> handleSaveStudent(session)
                uri.startsWith("/api/students/delete") && method == Method.POST -> handleDeleteStudent(session)
                uri == "/api/students/export/csv" && method == Method.GET -> handleStudentsExportCsv()
                uri == "/api/students/template/csv" && method == Method.GET -> handleStudentsTemplateCsv()
                uri == "/api/students/import" && method == Method.POST -> handleStudentsImport(session)

                // 4. API Data Kelas
                uri == "/api/classes" && method == Method.GET -> handleGetClasses()
                uri == "/api/classes" && method == Method.POST -> handleSaveClass(session)
                uri.startsWith("/api/classes/delete") && method == Method.POST -> handleDeleteClass(session)

                // 5. API Data Guru / Wali Kelas
                uri == "/api/teachers" && method == Method.GET -> handleGetTeachers()
                uri == "/api/teachers" && method == Method.POST -> handleSaveTeacher(session)
                uri.startsWith("/api/teachers/delete") && method == Method.POST -> handleDeleteTeacher(session)

                // 6. API Pengaturan, WhatsApp & Telegram Bot
                uri == "/api/settings" && method == Method.GET -> handleGetSettings()
                uri == "/api/settings" && method == Method.POST -> handleSaveSettings(session)
                uri == "/api/whatsapp/test" && method == Method.POST -> handleTestWhatsApp(session)
                uri == "/api/telegram/test" && method == Method.POST -> handleTestTelegram(session)

                // 7. Backup Database SQLite
                uri == "/api/backup" && method == Method.GET -> handleBackupDatabase()

                // 8. Halaman Web (HTML)
                uri == "/login" -> serveAssetHtml("login.html")
                uri == "/kiosk" -> serveAssetHtml("kiosk.html")
                uri == "/queue/display" || uri == "/display" -> serveAssetHtml("display.html")
                uri == "/queue/desk" || uri == "/dashboard/queue/desk" -> serveAssetHtml("desk.html")
                uri == "/" || uri == "/dashboard" -> serveAssetHtml("dashboard.html")
                uri == "/students" || uri == "/dashboard/students" -> serveAssetHtml("students.html")
                uri == "/classes" || uri == "/dashboard/classes" -> serveAssetHtml("classes.html")
                uri == "/teachers" || uri == "/dashboard/teachers" -> serveAssetHtml("teachers.html")
                uri == "/attendances" || uri == "/dashboard/attendances" -> serveAssetHtml("attendances.html")
                uri == "/idcard" || uri == "/dashboard/idcard" -> serveAssetHtml("idcard.html")
                uri == "/whatsapp" || uri == "/dashboard/whatsapp" -> serveAssetHtml("whatsapp.html")
                uri == "/settings" || uri == "/dashboard/settings" -> serveAssetHtml("settings.html")

                // 9. Static Assets (JS / CSS)
                uri.startsWith("/static/") || uri.startsWith("/public/") || uri.startsWith("/js/") || uri.startsWith("/css/") -> serveStaticFile(uri)

                else -> serveAssetHtml("dashboard.html")
            }

            addCorsHeaders(response)
            return response

        } catch (e: Exception) {
            Log.e(TAG, "Error handling request $uri: ${e.message}", e)
            val errJson = JSONObject().apply {
                put("success", false)
                put("message", "Internal Server Error: ${e.message}")
            }
            val res = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, errJson.toString())
            addCorsHeaders(res)
            return res
        }
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
    }

    // --- API Handlers ---

    private fun parseRequestBody(session: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val postData = files["postData"] ?: ""
        return if (postData.trim().startsWith("{")) {
            JSONObject(postData)
        } else {
            val json = JSONObject()
            for ((k, v) in session.parameters) {
                if (v.isNotEmpty()) json.put(k, v[0])
            }
            json
        }
    }

    private fun handleAttendanceScan(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val code = body.optString("code", "").trim()

        if (code.isEmpty()) {
            val res = JSONObject().apply {
                put("success", false)
                put("code", "EMPTY_CODE")
                put("message", "Kode kartu atau QR kosong")
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, res.toString())
        }

        val student = db.findStudentByCode(code)
        if (student == null) {
            val res = JSONObject().apply {
                put("success", false)
                put("code", "NOT_FOUND")
                put("message", "Santri tidak terdaftar di sistem")
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
        }

        val studentId = student.getInt("id")
        val studentName = student.getString("name")
        val classId = student.optInt("class_id", 0)
        val className = student.optString("class_name", "-")
        val parentPhone = student.optString("parent_phone", "")
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Cek fitur antrian mengaji apakah diaktifkan
        val isQueueEnabled = db.getSetting("queue_enabled", "1") == "1"
        var studentQueue: JSONObject? = null
        if (isQueueEnabled) {
            studentQueue = db.assignQueueNumber(studentId, classId, todayStr)
        }

        // Cek apakah sudah absen hari ini
        val existingAttendance = db.getTodayAttendance(studentId)
        if (existingAttendance != null) {
            if (studentQueue == null && isQueueEnabled) {
                studentQueue = db.getQueueByStudent(studentId, todayStr)
            }
            val res = JSONObject().apply {
                put("success", false)
                put("code", "ALREADY_ATTENDED")
                put("status", existingAttendance.getString("status"))
                put("timeIn", existingAttendance.getString("time_in"))
                put("student", student)
                put("attendance", existingAttendance)
                if (studentQueue != null) put("queue", studentQueue)
                put("message", "Santri atas nama $studentName sudah melakukan presensi hari ini.")
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
        }

        // Tentukan Hadir atau Terlambat berdasarkan batas jam toleransi
        val timeNow = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val tolerance = db.getSetting("toleransi_telat", "07:15")

        val timeNowMinutes = timeToMinutes(timeNow)
        val toleranceMinutes = timeToMinutes(tolerance)

        val isLate = timeNowMinutes > toleranceMinutes
        val lateMinutes = if (isLate) (timeNowMinutes - toleranceMinutes) else 0
        val status = if (isLate) "TERLAMBAT" else "HADIR"
        val timeInFull = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        // Catat ke Database SQLite
        db.recordAttendance(studentId, status, lateMinutes)

        // Kirim Notifikasi WhatsApp ke Wali Santri via Cloud Gateway (Fonnte / Wablas)
        if (parentPhone.isNotEmpty()) {
            val qNum = studentQueue?.optInt("queue_number", 0)
            val tName = studentQueue?.optString("teacher_name", "")
            CloudWhatsAppClient.sendAttendanceNotification(
                db = db,
                parentPhone = parentPhone,
                studentName = studentName,
                className = className,
                timeIn = timeInFull,
                status = status,
                lateMinutes = lateMinutes,
                queueNumber = if (qNum != null && qNum > 0) qNum else null,
                teacherName = if (!tName.isNullOrBlank()) tName else null
            )
        }

        val attendanceObj = JSONObject().apply {
            put("status", status)
            put("time_in", timeInFull)
            put("late_minutes", lateMinutes)
        }

        val res = JSONObject().apply {
            put("success", true)
            put("code", "SUCCESS")
            put("status", status)
            put("lateMinutes", lateMinutes)
            put("timeIn", timeInFull)
            put("student", student)
            put("attendance", attendanceObj)
            if (studentQueue != null) put("queue", studentQueue)
            put("message", if (status == "HADIR") "Presensi berhasil. Hadir tepat waktu!" else "Presensi berhasil tercatat (Terlambat $lateMinutes menit).")
        }

        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
    }

    private fun handleGetDisplayData(): Response {
        val data = db.getDisplayData()
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, data.toString())
    }

    private fun handleGetQueueDeskData(session: IHTTPSession): Response {
        val params = session.parameters
        val classIdParam = params["classId"]?.getOrNull(0)?.toIntOrNull() ?: 1
        val deskData = db.getDeskData(classIdParam)
        val res = JSONObject().apply {
            put("success", true)
            put("deskData", deskData)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
    }

    private fun handleQueueAction(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val action = body.optString("action", "").uppercase()
        val classId = body.optInt("classId", 1)
        val queueId = body.optInt("queueId", 0)

        val result = when (action) {
            "NEXT" -> db.callNextQueue(classId)
            "RECALL" -> db.recallQueue(queueId)
            "SKIP" -> db.skipQueue(queueId)
            "FINISH" -> db.finishQueue(queueId)
            else -> JSONObject().apply {
                put("success", false)
                put("message", "Aksi antrian tidak valid: $action")
            }
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString())
    }

    private fun timeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            return h * 60 + m
        }
        return 0
    }

    private fun handleRecentAttendances(): Response {
        val arr = db.getRecentScans(10)
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, arr.toString())
    }

    private fun handleAttendanceReport(session: IHTTPSession): Response {
        val params = session.parameters
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val startDate = params["startDate"]?.getOrNull(0) ?: today
        val endDate = params["endDate"]?.getOrNull(0) ?: today
        val classId = params["classId"]?.getOrNull(0)?.toIntOrNull() ?: 0
        val status = params["status"]?.getOrNull(0) ?: ""

        val arr = db.getAttendanceReport(startDate, endDate, classId, status)
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, arr.toString())
    }

    private fun handleAttendanceExportCsv(session: IHTTPSession): Response {
        val params = session.parameters
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val startDate = params["startDate"]?.getOrNull(0) ?: today
        val endDate = params["endDate"]?.getOrNull(0) ?: today
        val classId = params["classId"]?.getOrNull(0)?.toIntOrNull() ?: 0
        val status = params["status"]?.getOrNull(0) ?: ""

        val arr = db.getAttendanceReport(startDate, endDate, classId, status)

        val csv = StringBuilder()
        csv.append("\uFEFF") // UTF-8 BOM untuk Microsoft Excel
        csv.append("No;Tanggal;Waktu Masuk;NIS;Nama Santri;Kelas;Status;Keterlambatan (Menit);No WA Wali;Catatan\n")

        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            csv.append("${i + 1};")
            csv.append("\"${item.optString("date")}\";")
            csv.append("\"${item.optString("time_in")}\";")
            csv.append("\"${item.optString("nis")}\";")
            csv.append("\"${item.optString("name").replace("\"", "\"\"")}\";")
            csv.append("\"${item.optString("class_name").replace("\"", "\"\"")}\";")
            csv.append("\"${item.optString("status")}\";")
            csv.append("${item.optInt("late_minutes", 0)};")
            csv.append("\"${item.optString("parent_phone")}\";")
            csv.append("\"${item.optString("notes").replace("\"", "\"\"")}\"\n")
        }

        val bytes = csv.toString().toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(bytes)
        val res = newFixedLengthResponse(Response.Status.OK, "text/csv; charset=utf-8", stream, bytes.size.toLong())
        res.addHeader("Content-Disposition", "attachment; filename=\"Laporan_Presensi_${startDate}_sd_${endDate}.csv\"")
        return res
    }

    private fun handleDashboardStats(): Response {
        val stats = db.getTodayStats()
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, stats.toString())
    }

    private fun handleGetStudents(): Response {
        val arr = db.getStudents()
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, arr.toString())
    }

    private fun handleSaveStudent(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val id = body.optInt("id", 0).let { if (it > 0) it else null }
        val nis = body.optString("nis", "").trim()
        val name = body.optString("name", "").trim()
        val classId = body.optInt("class_id", 1)
        val rfidUid = body.optString("rfid_uid", "").trim()
        val parentPhone = body.optString("parent_phone", "").trim()
        val parentName = body.optString("parent_name", "").trim()
        val gender = body.optString("gender", "L").trim()
        val photo = body.optString("photo", "").trim()
        val pob = body.optString("pob", "").trim()
        val dob = body.optString("dob", "").trim()

        if (nis.isEmpty() || name.isEmpty()) {
            val err = JSONObject().apply {
                put("success", false)
                put("message", "NIS dan Nama wajib diisi")
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, err.toString())
        }

        try {
            val resultId = db.addOrUpdateStudent(id, nis, name, classId, rfidUid, parentPhone, parentName, gender, photo, pob, dob)
            val res = JSONObject().apply {
                put("success", resultId >= 0)
                put("message", if (resultId >= 0) "Data berhasil disimpan" else "Gagal menyimpan data")
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
        } catch (e: Exception) {
            val err = JSONObject().apply {
                put("success", false)
                put("message", "Gagal menyimpan data: ${e.message}")
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, err.toString())
        }
    }

    private fun handleDeleteStudent(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val id = body.optInt("id", 0)
        val deleted = db.deleteStudent(id)
        val res = JSONObject().apply {
            put("success", deleted > 0)
            put("message", if (deleted > 0) "Santri berhasil dihapus" else "Santri tidak ditemukan")
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
    }

    private fun handleStudentsExportCsv(): Response {
        val arr = db.getStudents()
        val csv = StringBuilder()
        csv.append("\uFEFF") // UTF-8 BOM
        csv.append("NIS;Nama;Tempat Lahir;Tanggal Lahir;Kelas;Wali Kelas;Jenis Kelamin;RFID UID;Nama Wali;No WA Wali\n")

        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            csv.append("\"${s.optString("nis")}\";")
            csv.append("\"${s.optString("name").replace("\"", "\"\"")}\";")
            csv.append("\"${s.optString("pob").replace("\"", "\"\"")}\";")
            csv.append("\"${s.optString("dob")}\";")
            csv.append("\"${s.optString("class_name").replace("\"", "\"\"")}\";")
            csv.append("\"${s.optString("teacher_name").replace("\"", "\"\"")}\";")
            csv.append("\"${s.optString("gender", "L")}\";")
            csv.append("\"${s.optString("rfid_uid")}\";")
            csv.append("\"${s.optString("parent_name").replace("\"", "\"\"")}\";")
            csv.append("\"${s.optString("parent_phone")}\"\n")
        }

        val bytes = csv.toString().toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(bytes)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val res = newFixedLengthResponse(Response.Status.OK, "text/csv; charset=utf-8", stream, bytes.size.toLong())
        res.addHeader("Content-Disposition", "attachment; filename=\"Data_Santri_${today}.csv\"")
        return res
    }

    private fun handleStudentsTemplateCsv(): Response {
        val csv = StringBuilder()
        csv.append("\uFEFF")
        csv.append("NIS;Nama;Tempat Lahir;Tanggal Lahir;Kelas;Jenis Kelamin;RFID UID;Nama Wali;No WA Wali\n")
        csv.append("1001;Ahmad Fauzi;Jakarta;2015-05-15;Iqro 1;L;1001;Bpk. Slamet;081234567890\n")
        csv.append("1002;Siti Nurhaliza;Bandung;2015-08-20;Iqro 1;P;1002;Ibu Maryam;081298765432\n")

        val bytes = csv.toString().toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(bytes)
        val res = newFixedLengthResponse(Response.Status.OK, "text/csv; charset=utf-8", stream, bytes.size.toLong())
        res.addHeader("Content-Disposition", "attachment; filename=\"Template_Import_Santri.csv\"")
        return res
    }

    private fun handleStudentsImport(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val studentsArr = body.optJSONArray("students")
        if (studentsArr == null || studentsArr.length() == 0) {
            val err = JSONObject().apply {
                put("success", false)
                put("message", "Data santri yang diimpor tidak ditemukan atau format tidak sesuai.")
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, err.toString())
        }

        val result = db.importStudents(studentsArr)
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString())
    }

    // --- Handlers Data Kelas ---
    private fun handleGetClasses(): Response {
        val arr = db.getClasses()
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, arr.toString())
    }

    private fun handleSaveClass(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val id = body.optInt("id", 0).let { if (it > 0) it else null }
        val name = body.optString("name", "").trim()
        val level = body.optString("level", "Santri").trim()
        val teacherId = body.optInt("teacher_id", 0).let { if (it > 0) it else null }

        if (name.isEmpty()) {
            val err = JSONObject().apply {
                put("success", false)
                put("message", "Nama kelas tidak boleh kosong")
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, err.toString())
        }

        try {
            val resId = db.addOrUpdateClass(id, name, level, teacherId)
            val res = JSONObject().apply {
                put("success", resId >= 0)
                put("message", if (resId >= 0) "Data kelas berhasil disimpan" else "Gagal menyimpan kelas")
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
        } catch (e: Exception) {
            val err = JSONObject().apply {
                put("success", false)
                put("message", "Gagal menyimpan kelas: ${e.message}")
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, err.toString())
        }
    }

    private fun handleDeleteClass(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val id = body.optInt("id", 0)
        val res = db.deleteClass(id)
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
    }

    // --- Handlers Data Guru / Wali Kelas ---
    private fun handleGetTeachers(): Response {
        val arr = db.getTeachers()
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, arr.toString())
    }

    private fun handleSaveTeacher(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val id = body.optInt("id", 0).let { if (it > 0) it else null }
        val nip = body.optString("nip", "").trim()
        val name = body.optString("name", "").trim()
        val gender = body.optString("gender", "L").trim()
        val phone = body.optString("phone", "").trim()

        if (name.isEmpty()) {
            val err = JSONObject().apply {
                put("success", false)
                put("message", "Nama guru tidak boleh kosong")
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, err.toString())
        }

        try {
            val resId = db.addOrUpdateTeacher(id, nip, name, gender, phone)
            val res = JSONObject().apply {
                put("success", resId >= 0)
                put("message", if (resId >= 0) "Data guru berhasil disimpan" else "Gagal menyimpan guru")
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
        } catch (e: Exception) {
            val err = JSONObject().apply {
                put("success", false)
                put("message", "Gagal menyimpan guru: ${e.message}")
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, err.toString())
        }
    }

    private fun handleDeleteTeacher(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val id = body.optInt("id", 0)
        val res = db.deleteTeacher(id)
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
    }

    // --- Handlers Pengaturan & WhatsApp ---
    private fun handleGetSettings(): Response {
        val json = db.getAllSettings()
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString())
    }

    private fun handleSaveSettings(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val keys = body.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            db.setSetting(k, body.getString(k))
        }

        // Reload Telegram config jika token atau status berubah
        try {
            com.alijaya.smartschool.SmartSchoolApplication.initTelegramManager(context)
        } catch (_: Exception) {}

        val res = JSONObject().apply {
            put("success", true)
            put("message", "Pengaturan berhasil disimpan")
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
    }

    private fun handleTestTelegram(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val token = body.optString("bot_token", "").trim().ifBlank {
            db.getSetting("telegram_bot_token", "").trim()
        }
        val chatId = body.optString("chat_id", "").trim().ifBlank {
            db.getSetting("telegram_chat_id", "").trim()
        }

        if (token.isBlank() || chatId.isBlank()) {
            val err = JSONObject().apply {
                put("success", false)
                put("message", "Bot Token dan Chat ID wajib diisi terlebih dahulu")
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, err.toString())
        }

        val tm = com.alijaya.smartschool.SmartSchoolApplication.telegramManager
        if (tm == null) {
            com.alijaya.smartschool.SmartSchoolApplication.initTelegramManager(context)
        }

        val activeTm = com.alijaya.smartschool.SmartSchoolApplication.telegramManager
        if (activeTm == null) {
            val err = JSONObject().apply {
                put("success", false)
                put("message", "Telegram Manager belum terinisialisasi di sistem Android")
            }
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, err.toString())
        }

        return runBlocking(Dispatchers.IO) {
            val testResult = activeTm.testTelegramMessage(token, chatId)
            val res = JSONObject().apply {
                if (testResult.isSuccess) {
                    put("success", true)
                    put("message", testResult.getOrNull() ?: "Pesan berhasil terkirim ke Telegram!")
                } else {
                    put("success", false)
                    put("message", testResult.exceptionOrNull()?.message ?: "Gagal mengirim ke Telegram")
                }
            }
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
        }
    }

    private fun handleTestWhatsApp(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val phone = body.optString("phone", "").trim()
        val message = body.optString("message", "Halo! Pesan uji coba dari Sistem Presensi Smart School.").trim()

        val (success, msg) = CloudWhatsAppClient.sendSync(db, phone, message)
        val res = JSONObject().apply {
            put("success", success)
            put("message", msg)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
    }

    // --- Handlers Autentikasi Admin ---
    private fun handleAdminLogin(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val username = body.optString("username", "").trim()
        val password = body.optString("password", "").trim()

        val expectedUser = db.getSetting("admin_username").takeIf { it.isNotEmpty() } ?: "admin"
        val expectedPass = db.getSetting("admin_password").takeIf { it.isNotEmpty() } ?: "admin"

        val isValid = (username == expectedUser && password == expectedPass)
        val res = JSONObject().apply {
            put("success", isValid)
            if (isValid) {
                put("token", "admin_session_" + System.currentTimeMillis())
                put("message", "Login berhasil")
            } else {
                put("message", "Username atau kata sandi tidak cocok!")
            }
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
    }

    private fun handleAuthCheck(): Response {
        val expectedUser = db.getSetting("admin_username").takeIf { it.isNotEmpty() } ?: "admin"
        val res = JSONObject().apply {
            put("success", true)
            put("username", expectedUser)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, res.toString())
    }

    private fun handleBackupDatabase(): Response {
        val dbFile = context.getDatabasePath(SmartSchoolDatabase.DATABASE_NAME)
        return if (dbFile.exists()) {
            val stream = FileInputStream(dbFile)
            val res = newChunkedResponse(Response.Status.OK, "application/octet-stream", stream)
            res.addHeader("Content-Disposition", "attachment; filename=\"smartschool_backup.db\"")
            res
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{\"error\": \"Database not found\"}")
        }
    }

    // --- Static File Serving ---
    private fun serveAssetHtml(filename: String): Response {
        return try {
            val stream = context.assets.open("www/$filename")
            newChunkedResponse(Response.Status.OK, MIME_HTML, stream)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "<h1>404 File Not Found</h1><p>$filename tidak ditemukan di assets/www</p>")
        }
    }

    private fun serveStaticFile(uri: String): Response {
        val cleanPath = uri.removePrefix("/static/").removePrefix("/public/").trimStart('/')
        return try {
            val stream = context.assets.open("www/$cleanPath")
            val mime = when {
                cleanPath.endsWith(".js") -> MIME_JS
                cleanPath.endsWith(".css") -> MIME_CSS
                cleanPath.endsWith(".png") -> "image/png"
                cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") -> "image/jpeg"
                cleanPath.endsWith(".svg") -> "image/svg+xml"
                else -> "application/octet-stream"
            }
            newChunkedResponse(Response.Status.OK, mime, stream)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{\"error\": \"File static not found\"}")
        }
    }
}
