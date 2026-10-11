package com.alijaya.smartschool.tv.server

import android.content.Context
import com.alijaya.smartschool.tv.data.Repository
import com.alijaya.smartschool.tv.data.Student
import com.alijaya.smartschool.tv.data.User
import com.alijaya.smartschool.tv.data.ValidationException
import com.alijaya.smartschool.tv.util.Tokens
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.concurrent.ConcurrentHashMap

data class Session(val userId: Long, val role: String, val name: String, val classId: Long?)

class AuthException(val status: HttpStatusCode, message: String) : Exception(message)

/** Web server lokal (port 8080) untuk akses "Web Remote" guru/admin. */
class WebServer(private val context: Context, private val repo: Repository, private val port: Int) {

    private var engine: ApplicationEngine? = null
    private val sessions = ConcurrentHashMap<String, Session>()

    val isRunning get() = engine != null

    fun start() {
        if (engine != null) return
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") { module() }.also { it.start(wait = false) }
    }

    fun stop() {
        engine?.stop(500, 1500)
        engine = null
    }

    private fun Application.module() {
        install(ContentNegotiation) { gson { serializeNulls() } }
        install(StatusPages) {
            exception<AuthException> { call, e -> call.respond(e.status, mapOf("error" to e.message)) }
            exception<ValidationException> { call, e -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message)) }
            exception<Throwable> { call, e -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Server error"))) }
        }
        routing {
            get("/") { call.respondRedirect("/app/display.html") }
            get("/remote") { call.respondRedirect("/app/index.html") }
            get("/display") { call.respondRedirect("/app/display.html") }
            get("/kiosk") { call.respondRedirect("/app/display.html") }
            route("/api") { api() }
            get("/app/{path...}") { serveAsset(call) }
        }
    }

    /* ---------- helpers ---------- */

    private fun ApplicationCall.session(): Session? = request.cookies["sid"]?.let { sessions[it] }

    private fun ApplicationCall.requireAuth(): Session =
        session() ?: throw AuthException(HttpStatusCode.Unauthorized, "Silakan login")

    private fun ApplicationCall.requireAdmin(): Session =
        requireAuth().also { if (it.role != User.ROLE_ADMIN) throw AuthException(HttpStatusCode.Forbidden, "Khusus admin") }

    private fun ApplicationCall.idParam(): Long =
        parameters["id"]?.toLongOrNull() ?: throw ValidationException("ID tidak valid")

    private suspend fun serveAsset(call: ApplicationCall) {
        val path = call.parameters.getAll("path")?.joinToString("/").orEmpty().ifBlank { "index.html" }
        if (path.contains("..")) return call.respond(HttpStatusCode.BadRequest)
        val bytes = try {
            context.assets.open("web/$path").use { it.readBytes() }
        } catch (_: Exception) {
            return call.respond(HttpStatusCode.NotFound, "Not found")
        }
        call.respondBytes(bytes, contentTypeFor(path))
    }

    private fun contentTypeFor(path: String): ContentType = when (path.substringAfterLast('.').lowercase()) {
        "html" -> ContentType.Text.Html
        "css" -> ContentType.Text.CSS
        "js" -> ContentType.Application.JavaScript
        "json" -> ContentType.Application.Json
        "png" -> ContentType.Image.PNG
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "svg" -> ContentType.Image.SVG
        else -> ContentType.Application.OctetStream
    }

    /* ---------- API ---------- */

    data class LoginReq(val username: String = "", val password: String = "")
    data class ClassReq(val name: String = "", val level: String = "")
    data class UserReq(
        val username: String = "", val password: String? = null, val name: String = "",
        val phone: String = "", val role: String = User.ROLE_GURU, val classId: Long? = null
    )
    data class StudentReq(
        val nis: String = "", val nisn: String = "", val rfidUid: String? = null, val qrToken: String = "",
        val name: String = "", val gender: String = "L", val classId: Long? = null,
        val parentName: String = "", val parentPhone: String = "", val isActive: Boolean = true
    )

    private fun Route.api() {
        post("/auth/login") {
            val req = call.receive<LoginReq>()
            val u = repo.login(req.username, req.password)
                ?: throw AuthException(HttpStatusCode.Unauthorized, "Username atau password salah")
            val sid = Tokens.random(32)
            sessions[sid] = Session(u.id, u.role, u.name, u.classId)
            call.response.cookies.append(Cookie("sid", sid, path = "/", httpOnly = true, maxAge = 7 * 24 * 3600))
            call.respond(mapOf("ok" to true, "user" to sessions[sid]))
        }
        post("/auth/logout") {
            call.request.cookies["sid"]?.let { sessions.remove(it) }
            call.respond(mapOf("ok" to true))
        }
        get("/auth/me") { call.respond(call.requireAuth()) }

        get("/dashboard/stats") { call.requireAuth(); call.respond(repo.stats()) }

        get("/settings/public") {
            val s = repo.settings()
            call.respond(mapOf("school_name" to s["school_name"], "school_address" to s["school_address"]))
        }

        // Kelas
        get("/classes") { call.requireAuth(); call.respond(repo.db.classes().rows()) }
        post("/classes") {
            call.requireAdmin(); val r = call.receive<ClassReq>()
            call.respond(mapOf("id" to repo.saveClass(null, r.name, r.level)))
        }
        put("/classes/{id}") {
            call.requireAdmin(); val r = call.receive<ClassReq>()
            call.respond(mapOf("id" to repo.saveClass(call.idParam(), r.name, r.level)))
        }
        delete("/classes/{id}") { call.requireAdmin(); repo.deleteClass(call.idParam()); call.respond(mapOf("ok" to true)) }

        // Guru / user
        get("/teachers") { call.requireAdmin(); call.respond(repo.db.users().rows()) }
        post("/teachers") {
            call.requireAdmin(); val r = call.receive<UserReq>()
            call.respond(mapOf("id" to repo.saveUser(null, r.username, r.password, r.name, r.phone, r.role, r.classId)))
        }
        put("/teachers/{id}") {
            call.requireAdmin(); val r = call.receive<UserReq>()
            call.respond(mapOf("id" to repo.saveUser(call.idParam(), r.username, r.password, r.name, r.phone, r.role, r.classId)))
        }
        delete("/teachers/{id}") {
            val s = call.requireAdmin(); repo.deleteUser(call.idParam(), s.userId); call.respond(mapOf("ok" to true))
        }

        // Siswa
        get("/students") {
            val s = call.requireAuth()
            var classId = call.request.queryParameters["classId"]?.toLongOrNull()
            if (s.role == User.ROLE_GURU && s.classId != null && classId == null) classId = s.classId
            call.respond(repo.db.students().rows(classId, call.request.queryParameters["q"].orEmpty()))
        }
        post("/students") {
            call.requireAuth(); call.respond(mapOf("id" to repo.saveStudent(call.receive<StudentReq>().toStudent(0))))
        }
        put("/students/{id}") {
            call.requireAuth(); call.respond(mapOf("id" to repo.saveStudent(call.receive<StudentReq>().toStudent(call.idParam()))))
        }
        delete("/students/{id}") { call.requireAdmin(); repo.deleteStudent(call.idParam()); call.respond(mapOf("ok" to true)) }

        // Absensi
        get("/attendances") {
            val s = call.requireAuth()
            val date = call.request.queryParameters["date"] ?: com.alijaya.smartschool.tv.util.Clock.today()
            var classId = call.request.queryParameters["classId"]?.toLongOrNull()
            if (s.role == User.ROLE_GURU && s.classId != null && classId == null) classId = s.classId
            val status = call.request.queryParameters["status"]?.ifBlank { null }
            val q = call.request.queryParameters["q"].orEmpty()
            call.respond(repo.db.attendances().rows(date, classId, status, q))
        }
        get("/attendances/recent") {
            val date = call.request.queryParameters["date"] ?: com.alijaya.smartschool.tv.util.Clock.today()
            call.respond(repo.db.attendances().recent(date, 15))
        }
        post("/attendance/scan") {
            val req = call.receive<ScanReq>()
            val res = repo.processScan(req.code)
            call.respond(mapOf(
                "success" to res.success,
                "code" to if (!res.success) "ERROR" else if (res.isDuplicate) "ALREADY_ATTENDED" else "SUCCESS",
                "status" to (res.attendance?.status ?: "HADIR"),
                "message" to res.message,
                "lateMinutes" to (res.attendance?.lateMinutes ?: 0),
                "timeIn" to (res.attendance?.timeIn ?: com.alijaya.smartschool.tv.util.Clock.nowTime()),
                "student" to res.student?.let { s ->
                    mapOf(
                        "id" to s.id,
                        "name" to s.name,
                        "nis" to s.nis,
                        "class_name" to (res.className ?: "-"),
                        "photo" to s.photo
                    )
                },
                "queue" to res.queue?.let { q ->
                    mapOf(
                        "queue_number" to q.queueNumber,
                        "class_name" to (res.className ?: "-")
                    )
                },
                "attendance" to res.attendance,
                "className" to res.className,
                "isDuplicate" to res.isDuplicate
            ))
        }
        post("/attendances/manual") {
            val s = call.requireAuth()
            val req = call.receive<ManualAttReq>()
            repo.saveManualAttendance(req.studentId, req.date, req.status, req.notes, s.name)
            call.respond(mapOf("ok" to true))
        }
        delete("/attendances/{id}") {
            call.requireAuth()
            repo.deleteAttendance(call.idParam())
            call.respond(mapOf("ok" to true))
        }
        get("/attendances/export/csv") {
            call.requireAuth()
            val date = call.request.queryParameters["date"] ?: com.alijaya.smartschool.tv.util.Clock.today()
            val classId = call.request.queryParameters["classId"]?.toLongOrNull()
            val csv = repo.exportAttendanceCsv(date, classId)
            call.response.headers.append("Content-Disposition", "attachment; filename=\"absensi-$date.csv\"")
            call.respondBytes(csv.toByteArray(Charsets.UTF_8), ContentType.Text.CSV)
        }

        // Izin / Sakit
        get("/leaves") {
            call.requireAuth()
            call.respond(repo.db.misc().leaveRows())
        }
        post("/leaves") {
            call.requireAuth()
            val req = call.receive<LeaveReq>()
            val id = repo.saveLeave(req.studentId, req.type, req.startDate, req.endDate, req.reason)
            call.respond(mapOf("id" to id))
        }
        post("/leaves/{id}/approve") {
            val s = call.requireAuth()
            repo.approveLeave(call.idParam(), s.userId, true, null)
            call.respond(mapOf("ok" to true))
        }
        post("/leaves/{id}/reject") {
            val s = call.requireAuth()
            val req = call.receive<Map<String, String>>()
            repo.approveLeave(call.idParam(), s.userId, false, req["note"])
            call.respond(mapOf("ok" to true))
        }

        // WhatsApp Gateway
        get("/whatsapp/queue") {
            call.requireAdmin()
            call.respond(repo.db.misc().recentWaMessages(50))
        }
        post("/whatsapp/test") {
            call.requireAdmin()
            val req = call.receive<Map<String, String>>()
            val phone = req["phone"].orEmpty()
            val res = repo.testWhatsApp(phone)
            call.respond(res)
        }
        post("/whatsapp/retry") {
            call.requireAdmin()
            repo.retryWaQueue()
            call.respond(mapOf("ok" to true))
        }
        post("/whatsapp/broadcast-alpa") {
            call.requireAdmin()
            val date = call.request.queryParameters["date"] ?: com.alijaya.smartschool.tv.util.Clock.today()
            val count = repo.broadcastAlpa(date)
            call.respond(mapOf("ok" to true, "count" to count))
        }
        post("/whatsapp/send-recap") {
            call.requireAdmin()
            val date = call.request.queryParameters["date"] ?: com.alijaya.smartschool.tv.util.Clock.today()
            val classId = call.request.queryParameters["classId"]?.toLongOrNull()
            val count = repo.sendTeacherRecap(date, classId)
            call.respond(mapOf("ok" to true, "count" to count))
        }

        // Kartu Pelajar
        get("/students/{id}/idcard-data") {
            call.requireAuth()
            val s = repo.db.students().byId(call.idParam()) ?: throw ValidationException("Siswa tidak ditemukan")
            val cls = s.classId?.let { repo.db.classes().byId(it)?.name } ?: "-"
            val school = repo.settings()
            call.respond(mapOf(
                "student" to s,
                "className" to cls,
                "school_name" to school["school_name"],
                "school_address" to school["school_address"],
                "principal_name" to school["principal_name"]
            ))
        }
        post("/students/{id}/send-idcard-wa") {
            call.requireAuth()
            val ok = repo.sendIdCardWa(call.idParam())
            call.respond(mapOf("ok" to ok))
        }

        // Keuangan & Tagihan SPP
        get("/finance/categories") {
            call.requireAuth()
            call.respond(repo.db.misc().categories())
        }
        get("/finance/bills") {
            call.requireAuth()
            val status = call.request.queryParameters["status"]?.ifBlank { null }
            val classId = call.request.queryParameters["classId"]?.toLongOrNull()
            val q = call.request.queryParameters["q"].orEmpty()
            call.respond(repo.db.misc().billRows(status, classId, q))
        }
        post("/finance/bills/generate-monthly") {
            call.requireAdmin()
            val req = call.receive<Map<String, Any>>()
            val period = req["period"]?.toString().orEmpty()
            val categoryId = req["categoryId"]?.toString()?.toLongOrNull() ?: 1L
            val count = repo.generateMonthlyBills(period, categoryId)
            call.respond(mapOf("ok" to true, "count" to count))
        }
        post("/finance/bills/{id}/pay-cash") {
            val s = call.requireAuth()
            val ok = repo.payBillCash(call.idParam(), s.name)
            call.respond(mapOf("ok" to ok))
        }
        get("/finance/bills/{id}/qris") {
            call.requireAuth()
            val qris = repo.getDynamicQris(call.idParam())
            call.respond(mapOf("qris" to qris))
        }
        post("/finance/qris/decode-image") {
            call.requireAdmin()
            val req = call.receive<Map<String, String>>()
            val base64 = req["image"] ?: throw ValidationException("Data gambar tidak ditemukan")
            val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw ValidationException("Format gambar tidak dikenali")
            val payload = com.alijaya.smartschool.tv.util.Qr.decode(bitmap)
                ?: throw ValidationException("Gagal membaca barcode QRIS dari gambar. Pastikan gambar jelas dan terang.")
            if (!payload.contains("000201")) throw ValidationException("QR Code terdeteksi, namun bukan standar format QRIS EMVCo.")
            // Otomatis simpan ke setting
            repo.saveSettings(mapOf("qris_static" to payload))
            call.respond(mapOf("ok" to true, "payload" to payload))
        }
        post("/finance/qris/generate-custom") {
            call.requireAuth()
            val req = call.receive<Map<String, Any>>()
            val amount = req["amount"]?.toString()?.toLongOrNull() ?: throw ValidationException("Nominal tidak valid")
            val staticQris = req["staticQris"]?.toString()?.ifBlank { null } ?: repo.setting("qris_static")
            if (staticQris.isBlank()) throw ValidationException("QRIS statis belum diatur. Silakan upload gambar QRIS di Pengaturan terlebih dahulu.")
            val dynamicQris = com.alijaya.smartschool.tv.util.QrisUtil.convertStaticToDynamic(staticQris, amount)
            call.respond(mapOf("ok" to true, "qris" to dynamicQris, "amount" to amount))
        }
        post("/webhook/payment") {
            // Webhook untuk listener notifikasi bank lokal
            val req = call.receive<Map<String, Any>>()
            val source = req["source"]?.toString().orEmpty()
            val text = req["text"]?.toString().orEmpty()
            val amount = req["amount"]?.toString()?.toLongOrNull() ?: 0L
            val matched = repo.processAutoPaymentNotification(source, text, amount)
            call.respond(mapOf("matched" to matched))
        }

        // Tabungan Siswa
        get("/savings/history") {
            call.requireAuth()
            val studentId = call.request.queryParameters["studentId"]?.toLongOrNull()
            val classId = call.request.queryParameters["classId"]?.toLongOrNull()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            call.respond(repo.db.misc().savingRows(studentId, classId, limit))
        }
        post("/savings/deposit") {
            val user = call.requireAuth()
            val req = call.receive<Map<String, Any>>()
            val studentId = req["studentId"]?.toString()?.toLongOrNull() ?: throw ValidationException("studentId harus diisi")
            val amount = req["amount"]?.toString()?.toLongOrNull() ?: throw ValidationException("amount harus diisi")
            val note = req["note"]?.toString().orEmpty()
            val tx = repo.depositSavings(studentId, amount, note, user.userId)
            call.respond(mapOf("ok" to true, "tx" to tx))
        }
        post("/savings/withdraw") {
            val user = call.requireAuth()
            val req = call.receive<Map<String, Any>>()
            val studentId = req["studentId"]?.toString()?.toLongOrNull() ?: throw ValidationException("studentId harus diisi")
            val amount = req["amount"]?.toString()?.toLongOrNull() ?: throw ValidationException("amount harus diisi")
            val note = req["note"]?.toString().orEmpty()
            val tx = repo.withdrawSavings(studentId, amount, note, user.userId)
            call.respond(mapOf("ok" to true, "tx" to tx))
        }

        // Antrian Setoran Tahfidz / Quran
        get("/queue/today") {
            val today = com.alijaya.smartschool.tv.util.Clock.today()
            val list = repo.db.misc().queueRows(today)
            val calling = repo.db.misc().currentCallingQueue(today)
            call.respond(mapOf("date" to today, "list" to list, "calling" to calling))
        }
        get("/queue/display-data") {
            call.respond(repo.getDisplayData())
        }
        get("/display-data") {
            call.respond(repo.getDisplayData())
        }
        post("/queue/register") {
            val req = call.receive<Map<String, Any>>()
            val studentId = req["studentId"]?.toString()?.toLongOrNull() ?: throw ValidationException("studentId harus diisi")
            val q = repo.registerQueue(studentId)
            call.respond(mapOf("ok" to true, "queue" to q))
        }
        post("/queue/call-next") {
            call.requireAuth()
            val called = repo.callNextQueue()
            call.respond(mapOf("ok" to true, "called" to called))
        }
        post("/queue/{id}/recall") {
            call.requireAuth()
            val called = repo.recallQueue(call.idParam())
            call.respond(mapOf("ok" to true, "called" to called))
        }
        post("/queue/{id}/serve") {
            call.requireAuth()
            repo.serveQueue(call.idParam())
            call.respond(mapOf("ok" to true))
        }
        post("/queue/{id}/complete") {
            call.requireAuth()
            repo.completeQueue(call.idParam())
            call.respond(mapOf("ok" to true))
        }
        post("/queue/{id}/skip") {
            call.requireAuth()
            repo.skipQueue(call.idParam())
            call.respond(mapOf("ok" to true))
        }

        // Backup & Restore Database SQLite
        get("/backup/download") {
            call.requireAdmin()
            val dbFile = this@WebServer.context.getDatabasePath(com.alijaya.smartschool.tv.data.AppDatabase.NAME)
            if (!dbFile.exists()) throw ValidationException("Database tidak ditemukan")
            val bytes = dbFile.readBytes()
            call.response.header(
                io.ktor.http.HttpHeaders.ContentDisposition,
                io.ktor.http.ContentDisposition.Attachment.withParameter(
                    io.ktor.http.ContentDisposition.Parameters.FileName,
                    "smartschool_backup_${com.alijaya.smartschool.tv.util.Clock.today()}.db"
                ).toString()
            )
            call.respondBytes(bytes, ContentType.Application.OctetStream)
        }
        post("/backup/restore") {
            call.requireAdmin()
            val req = call.receive<Map<String, String>>()
            val base64 = req["data"] ?: throw ValidationException("Data backup base64 tidak ditemukan")
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            if (bytes.isEmpty()) throw ValidationException("File backup kosong atau korup")
            val dbFile = this@WebServer.context.getDatabasePath(com.alijaya.smartschool.tv.data.AppDatabase.NAME)
            dbFile.parentFile?.mkdirs()
            dbFile.writeBytes(bytes)
            sessions.clear()
            call.respond(mapOf("ok" to true, "message" to "Database berhasil direstore. Silakan restart aplikasi."))
        }

        // Pengaturan
        get("/settings") { call.requireAdmin(); call.respond(repo.settings()) }
        post("/settings") {
            call.requireAdmin()
            @Suppress("UNCHECKED_CAST")
            val body = call.receive<Map<String, Any?>>().mapValues { it.value?.toString().orEmpty() }
            repo.saveSettings(body); call.respond(mapOf("ok" to true))
        }
        post("/demo/reset") { call.requireAdmin(); repo.resetDemo(); sessions.clear(); call.respond(mapOf("ok" to true)) }
    }

    data class ScanReq(val code: String = "")
    data class ManualAttReq(val studentId: Long = 0, val date: String = "", val status: String = "HADIR", val notes: String = "")
    data class LeaveReq(val studentId: Long = 0, val type: String = "IZIN", val startDate: String = "", val endDate: String = "", val reason: String = "")

    private fun StudentReq.toStudent(id: Long) = Student(
        id = id, nis = nis, nisn = nisn, rfidUid = rfidUid, qrToken = qrToken, name = name, gender = gender,
        classId = classId, parentName = parentName, parentPhone = parentPhone, isActive = isActive
    )
}
