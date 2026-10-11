package com.alijaya.smartschool.tv.data

import androidx.room.withTransaction
import com.alijaya.smartschool.tv.util.Clock
import com.alijaya.smartschool.tv.util.Passwords
import com.alijaya.smartschool.tv.util.Tokens

class ValidationException(message: String) : Exception(message)

data class DashboardStats(
    val date: String,
    val totalStudents: Int,
    val totalClasses: Int,
    val totalTeachers: Int,
    val hadir: Int,
    val terlambat: Int,
    val izin: Int,
    val sakit: Int,
    val alpa: Int,
    val belumScan: Int,
    val pendingLeaves: Int,
    val unpaidBills: Int,
    val totalSavings: Long,
    val pendingWa: Int
)

/** Logika bisnis bersama untuk UI native dan Web API. */
class Repository(val db: AppDatabase) {

    /* ---------- Settings ---------- */
    suspend fun settings(): Map<String, String> =
        Seeder.DEFAULT_SETTINGS + db.settings().all().associate { it.key to it.value }

    suspend fun setting(key: String): String = db.settings().get(key) ?: Seeder.DEFAULT_SETTINGS[key].orEmpty()

    suspend fun saveSettings(values: Map<String, String>) =
        db.settings().putAll(values.filterKeys { it in Seeder.DEFAULT_SETTINGS }.map { Setting(it.key, it.value.trim()) })

    suspend fun todayPrayerSchedule(): com.alijaya.smartschool.tv.util.PrayerSchedule {
        val s = settings()
        val lat = s["prayer_lat"]?.toDoubleOrNull() ?: -6.2088
        val lng = s["prayer_lng"]?.toDoubleOrNull() ?: 106.8456
        val tz = s["prayer_tz"]?.toDoubleOrNull() ?: 7.0
        return com.alijaya.smartschool.tv.util.PrayerUtil.today(
            latitude = lat,
            longitude = lng,
            timezone = tz,
            ihtiyatMinutes = 2.0
        )
    }

    /* ---------- Auth ---------- */
    suspend fun login(username: String, password: String): User? {
        val u = db.users().byUsername(username.trim()) ?: return null
        return if (Passwords.verify(password, u.passwordHash)) u else null
    }

    /* ---------- Dashboard ---------- */
    suspend fun stats(): DashboardStats {
        val today = Clock.today()
        val c = db.attendances().countByStatus(today).associate { it.status to it.total }
        val total = db.students().countActive()
        return DashboardStats(
            date = today,
            totalStudents = total,
            totalClasses = db.classes().count(),
            totalTeachers = db.users().countTeachers(),
            hadir = c["HADIR"] ?: 0,
            terlambat = c["TERLAMBAT"] ?: 0,
            izin = c["IZIN"] ?: 0,
            sakit = c["SAKIT"] ?: 0,
            alpa = c["ALPA"] ?: 0,
            belumScan = (total - c.values.sum()).coerceAtLeast(0),
            pendingLeaves = db.misc().pendingLeaves(),
            unpaidBills = db.misc().unpaidBills(),
            totalSavings = db.misc().totalSavings(),
            pendingWa = db.misc().pendingWa()
        )
    }

    /* ---------- Classes ---------- */
    suspend fun saveClass(id: Long?, name: String, level: String): Long {
        if (name.isBlank()) throw ValidationException("Nama kelas wajib diisi")
        return if (id == null || id == 0L) db.classes().insert(SchoolClass(name = name.trim(), level = level.trim()))
        else { db.classes().update(SchoolClass(id, name.trim(), level.trim())); id }
    }

    suspend fun deleteClass(id: Long) = db.withTransaction {
        db.students().clearClass(id)
        db.users().clearClass(id)
        db.classes().delete(id)
    }

    /* ---------- Teachers / users ---------- */
    suspend fun saveUser(id: Long?, username: String, password: String?, name: String, phone: String, role: String, classId: Long?): Long {
        if (username.isBlank() || name.isBlank()) throw ValidationException("Username dan nama wajib diisi")
        if (role !in listOf(User.ROLE_ADMIN, User.ROLE_GURU)) throw ValidationException("Role tidak valid")
        val existing = db.users().byUsername(username.trim())
        if (existing != null && existing.id != id) throw ValidationException("Username sudah dipakai")
        return if (id == null || id == 0L) {
            if (password.isNullOrBlank()) throw ValidationException("Password wajib diisi")
            db.users().insert(User(username = username.trim(), passwordHash = Passwords.hash(password), name = name.trim(), phone = phone.trim(), role = role, classId = classId))
        } else {
            val old = db.users().byId(id) ?: throw ValidationException("User tidak ditemukan")
            db.users().update(
                old.copy(
                    username = username.trim(), name = name.trim(), phone = phone.trim(), role = role, classId = classId,
                    passwordHash = if (password.isNullOrBlank()) old.passwordHash else Passwords.hash(password)
                )
            )
            id
        }
    }

    suspend fun deleteUser(id: Long, currentUserId: Long) {
        if (id == currentUserId) throw ValidationException("Tidak bisa menghapus akun sendiri")
        db.users().delete(id)
    }

    /* ---------- Students ---------- */
    suspend fun saveStudent(s: Student): Long {
        if (s.name.isBlank() || s.nis.isBlank()) throw ValidationException("NIS dan nama wajib diisi")
        val clean = s.copy(
            name = s.name.trim(), nis = s.nis.trim(), nisn = s.nisn.trim(),
            rfidUid = s.rfidUid?.trim()?.ifBlank { null },
            parentPhone = normalizePhone(s.parentPhone),
            qrToken = s.qrToken.ifBlank { "QR-" + Tokens.random(8) }
        )
        return if (clean.id == 0L) db.students().insert(clean)
        else {
            val old = db.students().byId(clean.id) ?: throw ValidationException("Siswa tidak ditemukan")
            db.students().update(clean.copy(savingsBalance = old.savingsBalance, createdAt = old.createdAt))
            clean.id
        }
    }

    suspend fun deleteStudent(id: Long) = db.withTransaction {
        db.attendances().deleteByStudent(id)
        db.students().delete(id)
    }

    /* ---------- Attendance ---------- */
    data class ScanResult(
        val success: Boolean,
        val message: String,
        val student: Student? = null,
        val attendance: Attendance? = null,
        val className: String? = null,
        val isDuplicate: Boolean = false,
        val queue: DailyQueue? = null
    )

    suspend fun processScan(code: String): ScanResult {
        val trimmed = code.trim()
        if (trimmed.isBlank()) return ScanResult(false, "Kode scan kosong")
        val s = db.students().byScanCode(trimmed) ?: return ScanResult(false, "Siswa tidak terdaftar ($trimmed)")
        val today = Clock.today()
        val nowTime = Clock.nowTime()

        // Daftarkan antrian mengaji santri otomatis jika belum ada
        val currentQueue = try { registerQueue(s.id) } catch (_: Exception) { db.misc().queueByStudent(s.id, today) }

        val existing = db.attendances().find(s.id, today)
        if (existing != null) {
            val cls = s.classId?.let { db.classes().byId(it)?.name }
            return ScanResult(
                success = true,
                message = "${s.name} sudah absen (${existing.status} jam ${existing.timeIn})",
                student = s,
                attendance = existing,
                className = cls,
                isDuplicate = true,
                queue = currentQueue
            )
        }

        val jamMasukStr = setting("jam_masuk").ifBlank { "07:00" }
        val toleransi = setting("toleransi_menit").toIntOrNull() ?: 0

        var lateMinutes = 0
        var status = "HADIR"

        try {
            val partsMasuk = jamMasukStr.split(":")
            val partsNow = nowTime.split(":")
            val targetMinutes = partsMasuk[0].toInt() * 60 + partsMasuk[1].toInt() + toleransi
            val nowMinutes = partsNow[0].toInt() * 60 + partsNow[1].toInt()

            if (nowMinutes > targetMinutes) {
                status = "TERLAMBAT"
                lateMinutes = nowMinutes - targetMinutes
            }
        } catch (_: Exception) {}

        val att = Attendance(
            studentId = s.id,
            date = today,
            timeIn = nowTime,
            status = status,
            lateMinutes = lateMinutes,
            notes = if (lateMinutes > 0) "Terlambat $lateMinutes menit" else "",
            createdBy = "kiosk"
        )
        val id = db.attendances().upsert(att)
        val cls = s.classId?.let { db.classes().byId(it)?.name }

        // Buat antrian notifikasi WA jika aktif
        if (setting("wa_notif_scan") == "1" && s.parentPhone.isNotBlank() && setting("wa_provider") != "none") {
            val school = setting("school_name")
            val statusLabel = if (status == "TERLAMBAT") "Terlambat ($lateMinutes menit)" else "Tepat Waktu"
            val text = "Assalamu'alaikum wr. wb.\n\n" +
                    "Diberitahukan bahwa ananda *${s.name}* (${cls ?: "-"})\n" +
                    "telah hadir di *$school* pada:\n" +
                    "Tanggal: $today\n" +
                    "Pukul: $nowTime WIB\n" +
                    "Status: *$statusLabel*\n\n" +
                    "Terima kasih."
            db.misc().insertWa(WaMessage(studentId = s.id, type = "scan_in", phone = s.parentPhone, message = text))
        }

        return ScanResult(
            success = true,
            message = if (status == "TERLAMBAT") "TERLAMBAT ($lateMinutes menit)" else "HADIR TEPAT WAKTU",
            student = s,
            attendance = att.copy(id = id),
            className = cls,
            isDuplicate = false,
            queue = currentQueue
        )
    }

    suspend fun saveManualAttendance(studentId: Long, date: String, status: String, notes: String, byUser: String) {
        val s = db.students().byId(studentId) ?: throw ValidationException("Siswa tidak ditemukan")
        val validStatus = listOf("HADIR", "TERLAMBAT", "IZIN", "SAKIT", "ALPA")
        if (status !in validStatus) throw ValidationException("Status tidak valid")
        val att = Attendance(
            studentId = s.id,
            date = date.ifBlank { Clock.today() },
            timeIn = if (status in listOf("HADIR", "TERLAMBAT")) Clock.nowTime() else null,
            status = status,
            lateMinutes = 0,
            notes = notes.trim(),
            createdBy = byUser
        )
        db.attendances().upsert(att)
    }

    suspend fun deleteAttendance(id: Long) = db.attendances().delete(id)

    /* ---------- Leaves ---------- */
    suspend fun saveLeave(studentId: Long, type: String, startDate: String, endDate: String, reason: String): Long {
        if (type !in listOf("SAKIT", "IZIN")) throw ValidationException("Tipe izin tidak valid")
        if (startDate.isBlank()) throw ValidationException("Tanggal awal wajib diisi")
        val req = LeaveRequest(
            studentId = studentId,
            type = type,
            startDate = startDate.trim(),
            endDate = endDate.ifBlank { startDate }.trim(),
            reason = reason.trim(),
            status = "PENDING"
        )
        return db.misc().insertLeave(req)
    }

    suspend fun approveLeave(id: Long, reviewerId: Long, isApprove: Boolean, note: String?) = db.withTransaction {
        val l = db.misc().leaveById(id) ?: throw ValidationException("Pengajuan izin tidak ditemukan")
        val status = if (isApprove) "APPROVED" else "REJECTED"
        db.misc().updateLeave(l.copy(status = status, reviewedBy = reviewerId, rejectionNote = note?.trim()))

        if (isApprove) {
            // otomatis buat rekaman kehadiran dengan status SAKIT/IZIN
            db.attendances().upsert(
                Attendance(
                    studentId = l.studentId,
                    date = l.startDate,
                    timeIn = null,
                    status = l.type,
                    notes = l.reason,
                    createdBy = "leave_approval"
                )
            )
        }
    }

    suspend fun exportAttendanceCsv(date: String, classId: Long?): String {
        val rows = db.attendances().rows(date, classId, null, "")
        val sb = StringBuilder()
        sb.append("ID,NIS,Nama,Kelas,Tanggal,Jam Masuk,Status,Terlambat (menit),Catatan,Dicatat Oleh\n")
        rows.forEach { r ->
            sb.append("${r.id},\"${r.nis}\",\"${r.name}\",\"${r.className ?: "-"}\",${r.date},${r.timeIn ?: "-"},${r.status},${r.lateMinutes},\"${r.notes}\",\"${r.createdBy}\"\n")
        }
        return sb.toString()
    }

    /* ---------- WhatsApp ---------- */
    suspend fun getWhatsAppGateway(): com.alijaya.smartschool.tv.util.WhatsAppGateway {
        return com.alijaya.smartschool.tv.util.WhatsAppGateway(
            provider = setting("wa_provider"),
            token = setting("wa_token"),
            wablasDomain = setting("wa_wablas_domain"),
            secretKey = setting("wa_secret_key")
        )
    }

    suspend fun testWhatsApp(targetPhone: String): com.alijaya.smartschool.tv.util.WhatsAppGateway.SendResult {
        val client = getWhatsAppGateway()
        val school = setting("school_name")
        val msg = "Tes notifikasi WhatsApp dari *$school*.\nSistem Smart School TV berhasil terhubung."
        return client.send(targetPhone, msg)
    }

    suspend fun retryWaQueue() {
        db.misc().retryFailedWa()
    }

    suspend fun broadcastAlpa(date: String = Clock.today()): Int {
        val school = setting("school_name")
        val rows = db.attendances().rows(date, null, "ALPA", "")
        var count = 0
        rows.forEach { r ->
            val s = db.students().byId(r.studentId)
            if (s != null && s.parentPhone.isNotBlank()) {
                val msg = "Assalamu'alaikum wr. wb.\n\n" +
                        "Diberitahukan bahwa ananda *${s.name}* (Kelas ${r.className ?: "-"})\n" +
                        "pada hari ini tanggal *$date* tercatat *ALPA (Tidak Hadir Tanpa Keterangan)* di *$school*.\n\n" +
                        "Mohon konfirmasi jika ada kendala. Terima kasih."
                db.misc().insertWa(WaMessage(studentId = s.id, type = "alpa_broadcast", phone = s.parentPhone, message = msg))
                count++
            }
        }
        return count
    }

    suspend fun sendTeacherRecap(date: String = Clock.today(), classId: Long? = null): Int {
        val school = setting("school_name")
        val classes = if (classId != null) {
            val c = db.classes().byId(classId)
            if (c != null) listOf(c) else emptyList()
        } else {
            db.classes().all()
        }

        var sentCount = 0
        classes.forEach { c ->
            val teachers = db.users().rows().filter { it.classId == c.id }
            if (teachers.isNotEmpty()) {
                val attendances = db.attendances().rows(date, c.id, null, "")
                val hadir = attendances.count { it.status == "HADIR" }
                val terlambat = attendances.count { it.status == "TERLAMBAT" }
                val izin = attendances.count { it.status == "IZIN" }
                val sakit = attendances.count { it.status == "SAKIT" }
                val alpa = attendances.count { it.status == "ALPA" }
                val totalMurid = db.students().rows(c.id, "").size
                val belum = (totalMurid - attendances.size).coerceAtLeast(0)

                val msg = "📊 *Rekap Absensi ${c.name}*\n" +
                        "Sekolah: *$school*\n" +
                        "Tanggal: $date\n\n" +
                        "• Hadir: $hadir\n" +
                        "• Terlambat: $terlambat\n" +
                        "• Izin: $izin\n" +
                        "• Sakit: $sakit\n" +
                        "• Alpa: $alpa\n" +
                        "• Belum Absen: $belum\n" +
                        "• Total Siswa: $totalMurid\n\n" +
                        "Semoga kegiatan belajar mengajar berjalan lancar."

                teachers.forEach { t ->
                    if (t.phone.isNotBlank()) {
                        db.misc().insertWa(WaMessage(studentId = null, type = "teacher_recap", phone = t.phone, message = msg))
                        sentCount++
                    }
                }
            }
        }
        return sentCount
    }

    suspend fun sendIdCardWa(studentId: Long): Boolean {
        val s = db.students().byId(studentId) ?: return false
        if (s.parentPhone.isBlank()) return false
        val school = setting("school_name")
        val cls = s.classId?.let { db.classes().byId(it)?.name } ?: "-"
        val msg = "Assalamu'alaikum wr. wb.\n\n" +
                "Berikut informasi Kartu Pelajar Digital ananda:\n" +
                "Nama: *${s.name}*\n" +
                "NIS: *${s.nis}*\n" +
                "Kelas: *$cls*\n" +
                "Token QR Absen: *${s.qrToken}*\n" +
                "RFID UID: *${s.rfidUid ?: "-"}*\n" +
                "Sekolah: *$school*\n\n" +
                "Kartu ini digunakan untuk presensi mandiri di gerbang sekolah. Terima kasih."
        db.misc().insertWa(WaMessage(studentId = s.id, type = "id_card", phone = s.parentPhone, message = msg))
        return true
    }

    /* ---------- Finance & QRIS ---------- */
    suspend fun generateMonthlyBills(period: String, categoryId: Long): Int {
        val cat = db.misc().categories().firstOrNull { it.id == categoryId } ?: throw ValidationException("Kategori tidak ditemukan")
        val students = db.students().rows(null, "")
        var count = 0
        students.forEachIndexed { i, s ->
            val unique = 1 + (i % 999)
            val code = "INV-$period-${cat.code}-${s.nis}"
            val existing = db.misc().billRows(null, null, code).firstOrNull { it.billCode == code }
            if (existing == null) {
                db.misc().insertBill(
                    StudentBill(
                        billCode = code,
                        studentId = s.id,
                        categoryId = cat.id,
                        title = "${cat.name} $period",
                        period = period,
                        baseAmount = cat.defaultAmount,
                        uniqueCode = unique,
                        totalAmount = cat.defaultAmount + unique,
                        status = "UNPAID",
                        dueDate = "$period-10"
                    )
                )
                count++
            }
        }
        return count
    }

    suspend fun payBillCash(billId: Long, receivedByUser: String): Boolean = db.withTransaction {
        val b = db.misc().billById(billId) ?: return@withTransaction false
        if (b.status == "PAID") return@withTransaction false
        val now = System.currentTimeMillis()
        db.misc().updateBill(b.copy(status = "PAID", paidAt = now))

        val receiptNo = "RCP-" + com.alijaya.smartschool.tv.util.Clock.today().replace("-", "") + "-${b.id}"
        db.misc().insertReceipt(
            PaymentReceipt(
                receiptNo = receiptNo,
                billId = b.id,
                amountPaid = b.totalAmount,
                method = "CASH",
                receivedBy = receivedByUser
            )
        )

        // Kirim WA tanda lunas
        val s = db.students().byId(b.studentId)
        if (s != null && s.parentPhone.isNotBlank()) {
            val school = setting("school_name")
            val msg = "Alhamdulillah, pembayaran telah diterima.\n\n" +
                    "Kwitansi: *$receiptNo*\n" +
                    "Siswa: *${s.name}*\n" +
                    "Tagihan: *${b.title}*\n" +
                    "Total Dibayar: *Rp ${b.totalAmount}*\n" +
                    "Metode: *Tunai (Kasir/Admin)*\n" +
                    "Sekolah: *$school*\n\n" +
                    "Terima kasih atas kerja samanya."
            db.misc().insertWa(WaMessage(studentId = s.id, type = "payment_receipt", phone = s.parentPhone, message = msg))
        }
        true
    }

    suspend fun getDynamicQris(billId: Long): String {
        val b = db.misc().billById(billId) ?: throw ValidationException("Tagihan tidak ditemukan")
        val staticQris = setting("qris_static")
        if (staticQris.isBlank()) throw ValidationException("QRIS statis sekolah belum diatur di menu Pengaturan")
        return com.alijaya.smartschool.tv.util.QrisUtil.convertStaticToDynamic(staticQris, b.totalAmount)
    }

    suspend fun processAutoPaymentNotification(source: String, text: String, amount: Long): Boolean = db.withTransaction {
        val matchedBill = db.misc().billByAmount(amount)
        if (matchedBill != null) {
            val now = System.currentTimeMillis()
            db.misc().updateBill(matchedBill.copy(status = "PAID", paidAt = now))

            val receiptNo = "AUTO-" + com.alijaya.smartschool.tv.util.Clock.today().replace("-", "") + "-${matchedBill.id}"
            db.misc().insertReceipt(
                PaymentReceipt(
                    receiptNo = receiptNo,
                    billId = matchedBill.id,
                    amountPaid = matchedBill.totalAmount,
                    method = "QRIS",
                    channel = source,
                    receivedBy = "auto_listener"
                )
            )

            db.misc().insertPaymentLog(
                PaymentLog(
                    source = source,
                    text = text,
                    amount = amount,
                    matchedBillId = matchedBill.id,
                    status = "MATCHED_PAID"
                )
            )

            // Kirim notifikasi WA
            val s = db.students().byId(matchedBill.studentId)
            if (s != null && s.parentPhone.isNotBlank()) {
                val school = setting("school_name")
                val msg = "Pembayaran Otomatis Berhasil Terverifikasi ✅\n\n" +
                        "No. Bukti: *$receiptNo*\n" +
                        "Siswa: *${s.name}*\n" +
                        "Tagihan: *${matchedBill.title}*\n" +
                        "Nominal: *Rp ${matchedBill.totalAmount}*\n" +
                        "Metode: *QRIS / Transfer Otomatis*\n" +
                        "Sekolah: *$school*\n\n" +
                        "Tagihan otomatis dinyatakan LUNAS."
                db.misc().insertWa(WaMessage(studentId = s.id, type = "payment_receipt", phone = s.parentPhone, message = msg))
            }
            true
        } else {
            db.misc().insertPaymentLog(
                PaymentLog(
                    source = source,
                    text = text,
                    amount = amount,
                    matchedBillId = null,
                    status = "UNMATCHED"
                )
            )
            false
        }
    }

    /* ---------- Tabungan Siswa (Savings) ---------- */
    suspend fun depositSavings(studentId: Long, amount: Long, note: String, teacherId: Long?): SavingTx {
        if (amount <= 0) throw ValidationException("Nominal setoran harus lebih dari 0")
        val s = db.students().byId(studentId) ?: throw ValidationException("Siswa tidak ditemukan")
        val today = Clock.today()
        val newBalance = s.savingsBalance + amount

        val tx = SavingTx(
            studentId = studentId,
            teacherId = teacherId,
            classId = s.classId,
            type = "SETOR",
            amount = amount,
            balanceAfter = newBalance,
            note = note.trim(),
            transactionDate = today
        )
        val id = db.misc().insertSaving(tx)
        db.misc().updateStudentSavingsBalance(studentId, amount)

        // Kirim WhatsApp bukti setoran ke orang tua
        if (s.parentPhone.isNotBlank()) {
            val school = setting("school_name")
            val msg = "Bukti Setoran Tabungan 💵\n\n" +
                    "Tanggal: *$today*\n" +
                    "Siswa: *${s.name}*\n" +
                    "Jumlah Setor: *Rp $amount*\n" +
                    "Saldo Akhir: *Rp $newBalance*\n" +
                    "Catatan: *${note.ifBlank { "-" }}*\n" +
                    "Sekolah: *$school*\n\n" +
                    "Terima kasih telah menabung."
            db.misc().insertWa(WaMessage(studentId = s.id, type = "savings_receipt", phone = s.parentPhone, message = msg))
        }

        return tx.copy(id = id)
    }

    suspend fun withdrawSavings(studentId: Long, amount: Long, note: String, teacherId: Long?): SavingTx {
        if (amount <= 0) throw ValidationException("Nominal penarikan harus lebih dari 0")
        val s = db.students().byId(studentId) ?: throw ValidationException("Siswa tidak ditemukan")
        if (s.savingsBalance < amount) throw ValidationException("Saldo tidak mencukupi (Saldo saat ini: Rp ${s.savingsBalance})")
        val today = Clock.today()
        val newBalance = s.savingsBalance - amount

        val tx = SavingTx(
            studentId = studentId,
            teacherId = teacherId,
            classId = s.classId,
            type = "TARIK",
            amount = amount,
            balanceAfter = newBalance,
            note = note.trim(),
            transactionDate = today
        )
        val id = db.misc().insertSaving(tx)
        db.misc().updateStudentSavingsBalance(studentId, -amount)

        // Kirim WhatsApp bukti penarikan ke orang tua
        if (s.parentPhone.isNotBlank()) {
            val school = setting("school_name")
            val msg = "Bukti Penarikan Tabungan 📤\n\n" +
                    "Tanggal: *$today*\n" +
                    "Siswa: *${s.name}*\n" +
                    "Jumlah Tarik: *Rp $amount*\n" +
                    "Sisa Saldo: *Rp $newBalance*\n" +
                    "Keperluan: *${note.ifBlank { "-" }}*\n" +
                    "Sekolah: *$school*"
            db.misc().insertWa(WaMessage(studentId = s.id, type = "savings_receipt", phone = s.parentPhone, message = msg))
        }

        return tx.copy(id = id)
    }

    /* ---------- Antrian Setoran Tahfidz (Queue) ---------- */
    suspend fun registerQueue(studentId: Long): DailyQueue {
        val s = db.students().byId(studentId) ?: throw ValidationException("Siswa tidak ditemukan")
        val today = Clock.today()
        val existing = db.misc().queueByStudent(studentId, today)
        if (existing != null) return existing

        val nextNum = db.misc().maxQueueNumber(today) + 1
        val q = DailyQueue(
            studentId = studentId,
            classId = s.classId,
            date = today,
            queueNumber = nextNum,
            status = "WAITING"
        )
        val id = db.misc().insertQueue(q)
        return q.copy(id = id)
    }

    suspend fun callNextQueue(): QueueRow? {
        val today = Clock.today()
        val list = db.misc().queueRows(today)
        // Cari antrian pertama berstatus WAITING
        val target = list.firstOrNull { it.status == "WAITING" } ?: return null
        val entity = db.misc().queueById(target.id) ?: return null
        val updated = entity.copy(
            status = "CALLING",
            callCount = entity.callCount + 1,
            updatedAt = System.currentTimeMillis()
        )
        db.misc().updateQueue(updated)
        return db.misc().currentCallingQueue(today)
    }

    suspend fun recallQueue(queueId: Long): QueueRow? {
        val today = Clock.today()
        val q = db.misc().queueById(queueId) ?: return null
        val updated = q.copy(
            status = "CALLING",
            callCount = q.callCount + 1,
            updatedAt = System.currentTimeMillis()
        )
        db.misc().updateQueue(updated)
        return db.misc().currentCallingQueue(today)
    }

    suspend fun serveQueue(queueId: Long) {
        val q = db.misc().queueById(queueId) ?: return
        db.misc().updateQueue(q.copy(status = "SERVING", updatedAt = System.currentTimeMillis()))
    }

    suspend fun completeQueue(queueId: Long) {
        val q = db.misc().queueById(queueId) ?: return
        db.misc().updateQueue(q.copy(status = "COMPLETED", updatedAt = System.currentTimeMillis()))
    }

    suspend fun skipQueue(queueId: Long) {
        val q = db.misc().queueById(queueId) ?: return
        db.misc().updateQueue(q.copy(status = "SKIPPED", updatedAt = System.currentTimeMillis()))
    }

    suspend fun resetDemo() = Seeder.seed(db)

    suspend fun todayPrayerFullData(): Map<String, Any?> {
        val s = settings()
        val lat = (s["prayer_latitude"] ?: s["prayer_lat"])?.toDoubleOrNull() ?: -6.2088
        val lng = (s["prayer_longitude"] ?: s["prayer_lng"])?.toDoubleOrNull() ?: 106.8456
        val tz = (s["prayer_timezone"] ?: s["prayer_tz"])?.toDoubleOrNull() ?: 7.0
        val cityName = s["prayer_city_name"]?.ifBlank { null } ?: "Kab. Indramayu / Cirebon"
        val ihtiyat = (s["prayer_ihtiyat"]?.toDoubleOrNull()) ?: 2.0
        val enabled = s["prayer_enabled"] ?: "1"
        val alertEnabled = s["prayer_alert_enabled"] ?: "1"
        val iqomahDuration = s["prayer_iqomah_duration"] ?: "10"

        val schedule = com.alijaya.smartschool.tv.util.PrayerUtil.today(
            latitude = lat,
            longitude = lng,
            timezone = tz,
            ihtiyatMinutes = ihtiyat
        )

        val tzName = if (tz >= 9.0) "WIT" else if (tz >= 8.0) "WITA" else "WIB"

        val cal = java.util.Calendar.getInstance()
        val nowSec = cal.get(java.util.Calendar.HOUR_OF_DAY) * 3600 +
                     cal.get(java.util.Calendar.MINUTE) * 60 +
                     cal.get(java.util.Calendar.SECOND)

        fun parseSec(t: String): Int {
            val parts = t.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return h * 3600 + m * 60
        }

        val prayerList = listOf(
            "subuh" to ("Subuh" to parseSec(schedule.subuh)),
            "terbit" to ("Syuruq" to parseSec(schedule.terbit)),
            "dzuhur" to ("Dzuhur" to parseSec(schedule.dzuhur)),
            "ashar" to ("Ashar" to parseSec(schedule.ashar)),
            "maghrib" to ("Maghrib" to parseSec(schedule.maghrib)),
            "isya" to ("Isya" to parseSec(schedule.isya))
        )

        val nextEntry = prayerList.firstOrNull { it.second.second > nowSec } ?: prayerList.first()
        var remainingSeconds = nextEntry.second.second - nowSec
        if (remainingSeconds < 0) remainingSeconds += 86400

        val remH = remainingSeconds / 3600
        val remM = (remainingSeconds % 3600) / 60
        val remS = remainingSeconds % 60
        val formattedCountdown = "%02d:%02d:%02d".format(remH, remM, remS)

        val nextInfo = mapOf(
            "next" to mapOf(
                "key" to nextEntry.first,
                "label" to nextEntry.second.first
            ),
            "remainingSeconds" to remainingSeconds,
            "formattedCountdown" to formattedCountdown
        )

        val localTime = mapOf(
            "hour" to cal.get(java.util.Calendar.HOUR_OF_DAY),
            "minute" to cal.get(java.util.Calendar.MINUTE),
            "second" to cal.get(java.util.Calendar.SECOND)
        )

        return mapOf(
            "settings" to mapOf(
                "prayer_enabled" to enabled,
                "prayer_city_name" to cityName,
                "prayer_alert_enabled" to alertEnabled,
                "prayer_iqomah_duration" to iqomahDuration,
                "prayer_latitude" to lat.toString(),
                "prayer_longitude" to lng.toString(),
                "prayer_timezone" to tz.toInt().toString()
            ),
            "schedule" to mapOf(
                "date" to schedule.date,
                "imsak" to schedule.imsak,
                "subuh" to schedule.subuh,
                "terbit" to schedule.terbit,
                "dzuhur" to schedule.dzuhur,
                "ashar" to schedule.ashar,
                "maghrib" to schedule.maghrib,
                "isya" to schedule.isya,
                "timezone" to tz.toInt(),
                "timezoneName" to tzName
            ),
            "nextInfo" to nextInfo,
            "localTime" to localTime
        )
    }

    suspend fun getDisplayData(): Map<String, Any?> {
        val today = Clock.today()
        val classes = db.classes().rows()
        val allQueues = db.misc().queueRows(today)
        val callingQueue = db.misc().currentCallingQueue(today)

        val tables = if (classes.isNotEmpty()) {
            classes.map { cls ->
                val classQueues = allQueues.filter { it.className == cls.name }
                val current = classQueues.firstOrNull { it.status in listOf("CALLING", "SERVING") }
                val nextWaiting = classQueues.filter { it.status == "WAITING" }.take(6).map {
                    mapOf(
                        "queue_number" to it.queueNumber,
                        "student_name" to it.studentName,
                        "nis" to it.nis
                    )
                }
                val stats = mapOf(
                    "total" to classQueues.size,
                    "waiting" to classQueues.count { it.status == "WAITING" },
                    "serving" to classQueues.count { it.status in listOf("CALLING", "SERVING") },
                    "completed" to classQueues.count { it.status == "COMPLETED" },
                    "skipped" to classQueues.count { it.status == "SKIPPED" }
                )
                mapOf(
                    "classId" to cls.id,
                    "className" to cls.name,
                    "teacherName" to (cls.waliName ?: "Ustadz / Wali Kelas"),
                    "current" to current?.let {
                        mapOf(
                            "id" to it.id,
                            "queue_number" to it.queueNumber,
                            "student_name" to it.studentName,
                            "nis" to it.nis,
                            "status" to it.status,
                            "call_count" to it.callCount
                        )
                    },
                    "nextWaiting" to nextWaiting,
                    "stats" to stats
                )
            }
        } else {
            listOf(
                mapOf(
                    "classId" to 1L,
                    "className" to "Meja Utama TPQ",
                    "teacherName" to "Ustadz Penguji",
                    "current" to callingQueue?.let {
                        mapOf(
                            "id" to it.id,
                            "queue_number" to it.queueNumber,
                            "student_name" to it.studentName,
                            "nis" to it.nis,
                            "status" to it.status,
                            "call_count" to it.callCount
                        )
                    },
                    "nextWaiting" to allQueues.filter { it.status == "WAITING" }.take(6).map {
                        mapOf(
                            "queue_number" to it.queueNumber,
                            "student_name" to it.studentName,
                            "nis" to it.nis
                        )
                    },
                    "stats" to mapOf(
                        "total" to allQueues.size,
                        "waiting" to allQueues.count { it.status == "WAITING" },
                        "serving" to allQueues.count { it.status in listOf("CALLING", "SERVING") },
                        "completed" to allQueues.count { it.status == "COMPLETED" },
                        "skipped" to allQueues.count { it.status == "SKIPPED" }
                    )
                )
            )
        }

        val prefix = setting("queue_calling_prefix").ifBlank { "Nomor antrian" }
        val lastCall = callingQueue?.let { cq ->
            mapOf(
                "queueId" to cq.id,
                "queueNumber" to cq.queueNumber,
                "queue_number" to cq.queueNumber,
                "callCount" to cq.callCount,
                "call_count" to cq.callCount,
                "calledAt" to cq.updatedAt.toString(),
                "called_at" to cq.updatedAt.toString(),
                "studentName" to cq.studentName,
                "student_name" to cq.studentName,
                "className" to (cq.className ?: "Meja"),
                "class_name" to (cq.className ?: "Meja"),
                "teacherName" to "Ustadz",
                "teacher_name" to "Ustadz",
                "speechSentence" to "$prefix ${cq.queueNumber}, ${cq.studentName}, silakan menuju meja ${cq.className ?: "penguji"}."
            )
        }

        val prayerFull = todayPrayerFullData()

        return mapOf(
            "success" to true,
            "date" to today,
            "tables" to tables,
            "lastCall" to lastCall,
            "prayer" to prayerFull,
            "settings" to mapOf(
                "school_name" to setting("school_name").ifBlank { "Smart School" },
                "school_logo" to setting("school_logo"),
                "queue_calling_prefix" to prefix,
                "prayer_city_name" to setting("prayer_city_name").ifBlank { "Kab. Indramayu / Cirebon" }
            )
        )
    }


    companion object {
        fun normalizePhone(raw: String): String {
            val d = raw.filter { it.isDigit() }
            return when {
                d.startsWith("0") -> "62" + d.drop(1)
                d.startsWith("8") -> "62$d"
                else -> d
            }
        }
    }
}

