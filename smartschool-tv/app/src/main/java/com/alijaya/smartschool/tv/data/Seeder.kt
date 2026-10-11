package com.alijaya.smartschool.tv.data

import androidx.room.withTransaction
import com.alijaya.smartschool.tv.util.Clock
import com.alijaya.smartschool.tv.util.Passwords
import com.alijaya.smartschool.tv.util.Tokens
import java.time.LocalDate
import kotlin.random.Random

/** Data dummy untuk demo. Bisa dipanggil ulang lewat "Reset Data Demo". */
object Seeder {

    val DEFAULT_SETTINGS = mapOf(
        "school_name" to "SD Islam Smart School",
        "school_address" to "Jl. Pendidikan No. 1, Jakarta",
        "school_phone" to "021-0000000",
        "principal_name" to "Drs. H. Ahmad Fauzi, M.Pd",
        "jam_masuk" to "07:00",
        "toleransi_menit" to "0",
        "jam_pulang" to "13:00",
        "jam_rekap_guru" to "09:00",
        "wa_provider" to "fonnte",      // fonnte|wablas|none
        "wa_token" to "",
        "wa_wablas_domain" to "https://solo.wablas.com",
        "wa_secret_key" to "",
        "wa_notif_scan" to "1",
        "prayer_lat" to "-6.2088",
        "prayer_lng" to "106.8456",
        "prayer_tz" to "7",
        "qris_static" to "",
        "server_port" to "8080"
    )

    private val boys = listOf("Ahmad", "Muhammad", "Rizki", "Fajar", "Bima", "Dimas", "Hafiz", "Ilham", "Yusuf", "Zaki", "Arkan", "Farhan")
    private val girls = listOf("Aisyah", "Fatimah", "Nadia", "Salsabila", "Zahra", "Khansa", "Putri", "Naura", "Alya", "Hana", "Syifa", "Azkia")
    private val lastNames = listOf("Pratama", "Ramadhan", "Hidayat", "Saputra", "Nugroho", "Lestari", "Maulana", "Kurniawan", "Wulandari", "Rahman", "Permata", "Fauzan")
    private val parents = listOf("Bapak Hasan", "Ibu Siti", "Bapak Agus", "Ibu Rina", "Bapak Joko", "Ibu Dewi", "Bapak Andi", "Ibu Yuli")

    suspend fun seedIfEmpty(db: AppDatabase) {
        if (db.users().byUsername("admin") == null) seed(db)
    }

    suspend fun seed(db: AppDatabase) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        db.withTransaction { seedData(db) }
    }

    private suspend fun seedData(db: AppDatabase) {
        val rnd = Random(42)

        db.settings().putAll(DEFAULT_SETTINGS.map { Setting(it.key, it.value) })

        val classIds = listOf(
            "1A" to "1", "1B" to "1", "2A" to "2", "3A" to "3", "4A" to "4", "5A" to "5", "6A" to "6"
        ).map { (n, l) -> db.classes().insert(SchoolClass(name = "Kelas $n", level = l)) }

        db.users().insert(
            User(username = "admin", passwordHash = Passwords.hash("admin123"), name = "Administrator", phone = "6281200000000", role = User.ROLE_ADMIN)
        )
        val teacherNames = listOf("Ustadz Abdullah", "Ustadzah Maryam", "Bapak Hendra", "Ibu Ratna", "Ustadz Salman", "Ibu Kartika", "Bapak Wahyu")
        val teacherIds = teacherNames.mapIndexed { i, n ->
            db.users().insert(
                User(
                    username = "guru${i + 1}", passwordHash = Passwords.hash("guru123"), name = n,
                    phone = "62813000000${10 + i}", role = User.ROLE_GURU, classId = classIds[i]
                )
            )
        }

        // Siswa: 8 per kelas
        val students = mutableListOf<Student>()
        var seq = 1
        classIds.forEach { cid ->
            repeat(8) {
                val male = rnd.nextBoolean()
                val first = if (male) boys.random(rnd) else girls.random(rnd)
                val nis = "2026" + seq.toString().padStart(4, '0')
                students += Student(
                    nis = nis,
                    nisn = "00" + (10000000 + seq),
                    rfidUid = (1000000000L + seq * 7919L).toString(),
                    qrToken = "QR-" + Tokens.random(8),
                    name = "$first ${lastNames.random(rnd)}",
                    gender = if (male) "L" else "P",
                    classId = cid,
                    parentName = parents.random(rnd),
                    parentPhone = "62857" + (10000000 + rnd.nextInt(89999999)),
                    waStatus = listOf("unverified", "intro_sent", "confirmed").random(rnd)
                )
                seq++
            }
        }
        db.students().insertAll(students)
        val all = db.students().rows(null, "")

        // Absensi 7 hari terakhir (kecuali hari ini, biar kiosk bisa dicoba)
        val today = LocalDate.now(Clock.zone)
        val att = mutableListOf<Attendance>()
        for (d in 1..7) {
            val date = today.minusDays(d.toLong())
            if (date.dayOfWeek.value >= 6) continue
            all.forEach { s ->
                val r = rnd.nextInt(100)
                val (status, late) = when {
                    r < 78 -> "HADIR" to 0
                    r < 88 -> "TERLAMBAT" to (1 + rnd.nextInt(30))
                    r < 93 -> "IZIN" to 0
                    r < 97 -> "SAKIT" to 0
                    else -> "ALPA" to 0
                }
                val time = when (status) {
                    "HADIR" -> "06:%02d:%02d".format(30 + rnd.nextInt(29), rnd.nextInt(60))
                    "TERLAMBAT" -> "07:%02d:%02d".format(late, rnd.nextInt(60))
                    else -> null
                }
                att += Attendance(studentId = s.id, date = date.toString(), timeIn = time, status = status, lateMinutes = late, createdBy = if (time != null) "kiosk" else "admin")
            }
        }
        db.misc().insertAttendances(att)

        // Kategori biaya + tagihan bulan ini
        db.misc().insertCategories(
            listOf(
                FeeCategory(code = "SPP", name = "SPP Bulanan", defaultAmount = 150_000, frequency = "MONTHLY"),
                FeeCategory(code = "INFAQ", name = "Infaq Jumat", defaultAmount = 5_000, frequency = "WEEKLY"),
                FeeCategory(code = "BUKU", name = "Buku Paket", defaultAmount = 250_000, frequency = "ONCE")
            )
        )
        val period = today.toString().substring(0, 7)
        all.forEachIndexed { i, s ->
            val unique = 1 + (i % 999)
            db.misc().insertBill(
                StudentBill(
                    billCode = "INV-$period-${s.nis}", studentId = s.id, categoryId = 1, title = "SPP $period",
                    period = period, baseAmount = 150_000, uniqueCode = unique, totalAmount = 150_000L + unique,
                    status = if (i % 3 == 0) "PAID" else "UNPAID", dueDate = "$period-10",
                    paidAt = if (i % 3 == 0) System.currentTimeMillis() else null
                )
            )
        }

        // Tabungan
        all.take(20).forEachIndexed { i, s ->
            val amount = (2 + i % 5) * 10_000L
            db.misc().insertSaving(
                SavingTx(studentId = s.id, teacherId = teacherIds.getOrNull(classIds.indexOf(s.classId)), classId = s.classId, type = "SETOR", amount = amount, balanceAfter = amount, transactionDate = today.minusDays(2).toString())
            )
            db.students().byId(s.id)?.let { db.students().update(it.copy(savingsBalance = amount)) }
        }

        // Izin pending
        all.take(3).forEach { s ->
            db.misc().insertLeave(LeaveRequest(studentId = s.id, type = "SAKIT", startDate = today.toString(), endDate = today.plusDays(1).toString(), reason = "Demam, istirahat di rumah"))
        }
    }
}
