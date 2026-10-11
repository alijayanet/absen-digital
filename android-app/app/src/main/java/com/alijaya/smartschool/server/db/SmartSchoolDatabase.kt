package com.alijaya.smartschool.server.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmartSchoolDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "smartschool.db"
        const val DATABASE_VERSION = 2

        @Volatile
        private var instance: SmartSchoolDatabase? = null

        fun getInstance(context: Context): SmartSchoolDatabase {
            return instance ?: synchronized(this) {
                instance ?: SmartSchoolDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Settings Table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        """)

        // 2. Teachers / Wali Kelas Table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS teachers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nip TEXT UNIQUE,
                name TEXT NOT NULL,
                gender TEXT DEFAULT 'L',
                phone TEXT,
                is_active INTEGER DEFAULT 1
            )
        """)

        // 3. Classes Table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS classes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                level TEXT,
                teacher_id INTEGER
            )
        """)

        // 4. Students Table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS students (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nis TEXT UNIQUE NOT NULL,
                rfid_uid TEXT,
                qr_code_token TEXT,
                name TEXT NOT NULL,
                gender TEXT DEFAULT 'L',
                pob TEXT,
                dob TEXT,
                class_id INTEGER,
                parent_name TEXT,
                parent_phone TEXT,
                photo TEXT,
                is_active INTEGER DEFAULT 1
            )
        """)

        // 5. Attendances Table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS attendances (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                time_in TEXT NOT NULL,
                status TEXT NOT NULL,
                late_minutes INTEGER DEFAULT 0,
                notes TEXT
            )
        """)

        // Index untuk performa query cepat
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_rfid ON students(rfid_uid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_nis ON students(nis)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attendances_date ON attendances(date)")

        // Seed Default Settings
        val defaultSettings = mapOf(
            "school_name" to "MUSHOLLA AL MUHAJIRIN",
            "school_address" to "Jl. Raya Pesantren No. 1",
            "school_phone" to "08123456789",
            "school_principal_name" to "Ust. H. Abdullah, Lc.",
            "school_logo" to "",
            "jam_masuk" to "07:00",
            "toleransi_telat" to "07:15",
            "jam_pulang" to "14:00",
            "wa_provider" to "fonnte",
            "wa_fonnte_token" to "",
            "wa_wablas_token" to "",
            "wa_wablas_domain" to "https://api.wablas.com",
            "entity_type" to "santri",
            "wa_enabled" to "1",
            "admin_username" to "admin",
            "admin_password" to "admin"
        )
        for ((k, v) in defaultSettings) {
            val cv = ContentValues().apply {
                put("key", k)
                put("value", v)
            }
            db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }

        // Seed Sample Teachers
        val sampleTeachers = listOf(
            Triple("G001", "Ust. H. Abdullah, Lc.", "08123456789"),
            Triple("G002", "Ust. Ahmad Dahlan, S.Pd.I", "08129876543"),
            Triple("G003", "Ustdz. Fatimah Az-Zahra, S.Ag", "08131122334")
        )
        for ((nip, name, phone) in sampleTeachers) {
            val cv = ContentValues().apply {
                put("nip", nip)
                put("name", name)
                put("gender", if (name.startsWith("Ustdz")) "P" else "L")
                put("phone", phone)
                put("is_active", 1)
            }
            db.insertWithOnConflict("teachers", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }

        // Seed Sample Classes
        val sampleClasses = listOf(
            Triple("Iqro 1", "Santri", 1),
            Triple("Iqro 2", "Santri", 2),
            Triple("Al-Qur'an 1", "Santri", 3),
            Triple("Al-Qur'an 2", "Santri", 1)
        )
        for ((name, level, teacherId) in sampleClasses) {
            val cv = ContentValues().apply {
                put("name", name)
                put("level", level)
                put("teacher_id", teacherId)
            }
            db.insertWithOnConflict("classes", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }

        // Seed Sample Students with Tempat & Tanggal Lahir
        val sampleStudents = listOf(
            Triple("1001", "Ahmad Fauzi", "1"),
            Triple("1002", "Siti Nurhaliza", "1"),
            Triple("1003", "Muhammad Rizky", "2"),
            Triple("1004", "Fatimah Az-Zahra", "3")
        )
        for ((nis, name, classId) in sampleStudents) {
            val cv = ContentValues().apply {
                put("nis", nis)
                put("name", name)
                put("class_id", classId.toInt())
                put("gender", if (name.contains("Siti") || name.contains("Fatimah")) "P" else "L")
                put("pob", "Jakarta")
                put("dob", "2015-05-15")
                put("rfid_uid", nis)
                put("qr_code_token", "QR-$nis")
                put("parent_name", "Wali dari $name")
                put("parent_phone", "")
            }
            db.insertWithOnConflict("students", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        applyMigrations(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        applyMigrations(db)
    }

    private fun applyMigrations(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE students ADD COLUMN pob TEXT;")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE students ADD COLUMN dob TEXT;")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE classes ADD COLUMN teacher_id INTEGER;")
        } catch (_: Exception) {}
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS teachers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nip TEXT UNIQUE,
                    name TEXT NOT NULL,
                    gender TEXT DEFAULT 'L',
                    phone TEXT,
                    is_active INTEGER DEFAULT 1
                )
            """)
        } catch (_: Exception) {}

        // Pastikan tabel teachers memiliki data awal jika kosong
        try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM teachers", null)
            var count = 0
            cursor.use { if (it.moveToFirst()) count = it.getInt(0) }
            if (count == 0) {
                val sampleTeachers = listOf(
                    Triple("G001", "Ust. H. Abdullah, Lc.", "08123456789"),
                    Triple("G002", "Ust. Ahmad Dahlan, S.Pd.I", "08129876543"),
                    Triple("G003", "Ustdz. Fatimah Az-Zahra, S.Ag", "08131122334")
                )
                for ((nip, name, phone) in sampleTeachers) {
                    val cv = ContentValues().apply {
                        put("nip", nip)
                        put("name", name)
                        put("gender", if (name.startsWith("Ustdz")) "P" else "L")
                        put("phone", phone)
                        put("is_active", 1)
                    }
                    db.insertWithOnConflict("teachers", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
        } catch (_: Exception) {}
    }

    // --- Settings Helpers ---
    fun getSetting(key: String, default: String = ""): String {
        val cursor = readableDatabase.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(key))
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0) ?: default
            }
        }
        return default
    }

    fun setSetting(key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllSettings(): JSONObject {
        val json = JSONObject()
        val cursor = readableDatabase.rawQuery("SELECT key, value FROM settings", null)
        cursor.use {
            while (it.moveToNext()) {
                json.put(it.getString(0), it.getString(1) ?: "")
            }
        }
        return json
    }

    // --- Teachers / Wali Kelas Helpers ---
    fun getTeachers(): JSONArray {
        val arr = JSONArray()
        val query = """
            SELECT t.id, t.nip, t.name, t.gender, t.phone, t.is_active,
                   (SELECT COUNT(*) FROM classes c WHERE c.teacher_id = t.id) as class_count,
                   (SELECT GROUP_CONCAT(c.name, ', ') FROM classes c WHERE c.teacher_id = t.id) as assigned_classes
            FROM teachers t
            ORDER BY t.name ASC
        """
        val cursor = readableDatabase.rawQuery(query, null)
        cursor.use {
            while (it.moveToNext()) {
                val obj = JSONObject().apply {
                    put("id", it.getInt(0))
                    put("nip", it.getString(1) ?: "")
                    put("name", it.getString(2))
                    put("gender", it.getString(3) ?: "L")
                    put("phone", it.getString(4) ?: "")
                    put("is_active", it.getInt(5))
                    put("class_count", it.getInt(6))
                    put("assigned_classes", it.getString(7) ?: "-")
                }
                arr.put(obj)
            }
        }
        return arr
    }

    fun addOrUpdateTeacher(
        id: Int?,
        nip: String,
        name: String,
        gender: String = "L",
        phone: String = ""
    ): Long {
        val cv = ContentValues().apply {
            if (nip.isNotEmpty()) {
                put("nip", nip)
            } else {
                putNull("nip")
            }
            put("name", name)
            put("gender", gender)
            put("phone", phone)
            put("is_active", 1)
        }
        return if (id != null && id > 0) {
            writableDatabase.update("teachers", cv, "id = ?", arrayOf(id.toString())).toLong()
        } else {
            writableDatabase.insertWithOnConflict("teachers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deleteTeacher(id: Int): JSONObject {
        // Cek relasi ke classes: set teacher_id null
        val cv = ContentValues().apply { putNull("teacher_id") }
        writableDatabase.update("classes", cv, "teacher_id = ?", arrayOf(id.toString()))

        val deleted = writableDatabase.delete("teachers", "id = ?", arrayOf(id.toString()))
        return JSONObject().apply {
            put("success", deleted > 0)
            put("message", if (deleted > 0) "Data guru berhasil dihapus" else "Guru tidak ditemukan")
        }
    }

    // --- Classes Helpers ---
    fun getClasses(): JSONArray {
        val arr = JSONArray()
        val query = """
            SELECT c.id, c.name, c.level, c.teacher_id, t.name as teacher_name, t.phone as teacher_phone,
                   (SELECT COUNT(*) FROM students s WHERE s.class_id = c.id) as student_count
            FROM classes c
            LEFT JOIN teachers t ON c.teacher_id = t.id
            ORDER BY c.name ASC
        """
        val cursor = readableDatabase.rawQuery(query, null)
        cursor.use {
            while (it.moveToNext()) {
                val obj = JSONObject().apply {
                    put("id", it.getInt(0))
                    put("name", it.getString(1))
                    put("level", it.getString(2) ?: "Santri")
                    put("teacher_id", if (it.isNull(3)) 0 else it.getInt(3))
                    put("teacher_name", it.getString(4) ?: "-")
                    put("teacher_phone", it.getString(5) ?: "")
                    put("student_count", it.getInt(6))
                }
                arr.put(obj)
            }
        }
        return arr
    }

    fun addOrUpdateClass(id: Int?, name: String, level: String = "Santri", teacherId: Int? = null): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("level", level)
            if (teacherId != null && teacherId > 0) {
                put("teacher_id", teacherId)
            } else {
                putNull("teacher_id")
            }
        }
        return if (id != null && id > 0) {
            writableDatabase.update("classes", cv, "id = ?", arrayOf(id.toString())).toLong()
        } else {
            writableDatabase.insertWithOnConflict("classes", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun addClass(name: String, level: String = "Santri"): Long {
        return addOrUpdateClass(null, name, level, null)
    }

    fun deleteClass(id: Int): JSONObject {
        // Cek santri yang masih terdaftar di kelas ini
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM students WHERE class_id = ?", arrayOf(id.toString()))
        var studentCount = 0
        cursor.use { if (it.moveToFirst()) studentCount = it.getInt(0) }

        if (studentCount > 0) {
            return JSONObject().apply {
                put("success", false)
                put("message", "Kelas tidak dapat dihapus karena masih ada $studentCount santri di dalamnya. Silakan pindahkan kelas santri terlebih dahulu.")
            }
        }

        val deleted = writableDatabase.delete("classes", "id = ?", arrayOf(id.toString()))
        return JSONObject().apply {
            put("success", deleted > 0)
            put("message", if (deleted > 0) "Kelas berhasil dihapus" else "Kelas tidak ditemukan")
        }
    }

    // --- Students Helpers ---
    fun getStudents(): JSONArray {
        val arr = JSONArray()
        val query = """
            SELECT s.id, s.nis, s.name, s.gender, s.pob, s.dob, s.class_id, c.name as class_name, 
                   s.rfid_uid, s.parent_name, s.parent_phone, s.photo, s.is_active,
                   t.name as teacher_name
            FROM students s
            LEFT JOIN classes c ON s.class_id = c.id
            LEFT JOIN teachers t ON c.teacher_id = t.id
            ORDER BY s.name ASC
        """
        val cursor = readableDatabase.rawQuery(query, null)
        cursor.use {
            while (it.moveToNext()) {
                val obj = JSONObject().apply {
                    put("id", it.getInt(0))
                    put("nis", it.getString(1))
                    put("name", it.getString(2))
                    put("gender", it.getString(3) ?: "L")
                    put("pob", it.getString(4) ?: "")
                    put("dob", it.getString(5) ?: "")
                    put("class_id", it.getInt(6))
                    put("class_name", it.getString(7) ?: "-")
                    put("rfid_uid", it.getString(8) ?: "")
                    put("parent_name", it.getString(9) ?: "")
                    put("parent_phone", it.getString(10) ?: "")
                    put("photo", it.getString(11) ?: "")
                    put("is_active", it.getInt(12))
                    put("teacher_name", it.getString(13) ?: "-")
                }
                arr.put(obj)
            }
        }
        return arr
    }

    fun findStudentByCode(code: String): JSONObject? {
        val clean = code.trim()
        val query = """
            SELECT s.id, s.nis, s.name, s.gender, s.pob, s.dob, s.class_id, c.name as class_name, 
                   s.rfid_uid, s.parent_name, s.parent_phone, s.photo,
                   t.name as teacher_name
            FROM students s
            LEFT JOIN classes c ON s.class_id = c.id
            LEFT JOIN teachers t ON c.teacher_id = t.id
            WHERE s.rfid_uid = ? OR s.nis = ? OR s.qr_code_token = ?
            LIMIT 1
        """
        val cursor = readableDatabase.rawQuery(query, arrayOf(clean, clean, clean))
        cursor.use {
            if (it.moveToFirst()) {
                return JSONObject().apply {
                    put("id", it.getInt(0))
                    put("nis", it.getString(1))
                    put("name", it.getString(2))
                    put("gender", it.getString(3) ?: "L")
                    put("pob", it.getString(4) ?: "")
                    put("dob", it.getString(5) ?: "")
                    put("class_id", it.getInt(6))
                    put("class_name", it.getString(7) ?: "-")
                    put("rfid_uid", it.getString(8) ?: "")
                    put("parent_name", it.getString(9) ?: "")
                    put("parent_phone", it.getString(10) ?: "")
                    put("photo", it.getString(11) ?: "")
                    put("teacher_name", it.getString(12) ?: "-")
                }
            }
        }
        return null
    }

    fun addOrUpdateStudent(
        id: Int?,
        nis: String,
        name: String,
        classId: Int,
        rfidUid: String,
        parentPhone: String,
        parentName: String = "",
        gender: String = "L",
        photo: String = "",
        pob: String = "",
        dob: String = ""
    ): Long {
        val cv = ContentValues().apply {
            put("nis", nis)
            put("name", name)
            put("class_id", classId)
            put("rfid_uid", rfidUid.ifEmpty { nis })
            put("qr_code_token", "QR-$nis")
            put("parent_phone", parentPhone)
            put("parent_name", parentName)
            put("gender", gender)
            put("pob", pob)
            put("dob", dob)
            put("photo", photo)
        }

        return if (id != null && id > 0) {
            writableDatabase.update("students", cv, "id = ?", arrayOf(id.toString())).toLong()
        } else {
            writableDatabase.insertWithOnConflict("students", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deleteStudent(id: Int): Int {
        return writableDatabase.delete("students", "id = ?", arrayOf(id.toString()))
    }

    // --- Attendances Helpers ---
    fun getTodayAttendance(studentId: Int): JSONObject? {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cursor = readableDatabase.rawQuery(
            "SELECT id, status, time_in, late_minutes FROM attendances WHERE student_id = ? AND date = ? LIMIT 1",
            arrayOf(studentId.toString(), today)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return JSONObject().apply {
                    put("id", it.getInt(0))
                    put("status", it.getString(1))
                    put("time_in", it.getString(2))
                    put("late_minutes", it.getInt(3))
                }
            }
        }
        return null
    }

    fun recordAttendance(studentId: Int, status: String, lateMinutes: Int, notes: String = ""): Long {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timeNow = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val cv = ContentValues().apply {
            put("student_id", studentId)
            put("date", today)
            put("time_in", timeNow)
            put("status", status)
            put("late_minutes", lateMinutes)
            put("notes", notes)
        }
        return writableDatabase.insert("attendances", null, cv)
    }

    fun getRecentScans(limit: Int = 5): JSONArray {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val arr = JSONArray()
        val query = """
            SELECT a.id, a.time_in, a.status, a.late_minutes, s.name, c.name as class_name, s.nis, s.photo
            FROM attendances a
            JOIN students s ON a.student_id = s.id
            LEFT JOIN classes c ON s.class_id = c.id
            WHERE a.date = ?
            ORDER BY a.id DESC
            LIMIT ?
        """
        val cursor = readableDatabase.rawQuery(query, arrayOf(today, limit.toString()))
        cursor.use {
            while (it.moveToNext()) {
                val obj = JSONObject().apply {
                    put("id", it.getInt(0))
                    put("time_in", it.getString(1))
                    put("status", it.getString(2))
                    put("late_minutes", it.getInt(3))
                    put("name", it.getString(4))
                    put("class_name", it.getString(5) ?: "-")
                    put("nis", it.getString(6))
                    put("photo", it.getString(7) ?: "")
                }
                arr.put(obj)
            }
        }
        return arr
    }

    fun getTodayStats(): JSONObject {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        var totalStudents = 0
        var totalClasses = 0
        var totalTeachers = 0
        var hadir = 0
        var terlambat = 0

        // Total santri aktif
        val c1 = readableDatabase.rawQuery("SELECT COUNT(*) FROM students WHERE is_active = 1", null)
        c1.use { if (it.moveToFirst()) totalStudents = it.getInt(0) }

        // Total Kelas
        val cClass = readableDatabase.rawQuery("SELECT COUNT(*) FROM classes", null)
        cClass.use { if (it.moveToFirst()) totalClasses = it.getInt(0) }

        // Total Guru
        val cTeacher = readableDatabase.rawQuery("SELECT COUNT(*) FROM teachers WHERE is_active = 1", null)
        cTeacher.use { if (it.moveToFirst()) totalTeachers = it.getInt(0) }

        // Hadir & Terlambat
        val c2 = readableDatabase.rawQuery(
            "SELECT status, COUNT(*) FROM attendances WHERE date = ? GROUP BY status",
            arrayOf(today)
        )
        c2.use {
            while (it.moveToNext()) {
                val st = it.getString(0)
                val cnt = it.getInt(1)
                if (st == "HADIR") hadir = cnt
                if (st == "TERLAMBAT") terlambat = cnt
            }
        }

        return JSONObject().apply {
            put("date", today)
            put("total_students", totalStudents)
            put("total_classes", totalClasses)
            put("total_teachers", totalTeachers)
            put("hadir", hadir)
            put("terlambat", terlambat)
            put("total_absen", hadir + terlambat)
            put("belum_absen", (totalStudents - (hadir + terlambat)).coerceAtLeast(0))
        }
    }

    // --- Laporan Presensi Lengkap (Filter Tanggal, Kelas, Status) ---
    fun getAttendanceReport(
        startDate: String,
        endDate: String,
        classId: Int = 0,
        statusFilter: String = ""
    ): JSONArray {
        val arr = JSONArray()
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        conditions.add("a.date >= ?")
        args.add(startDate)

        conditions.add("a.date <= ?")
        args.add(endDate)

        if (classId > 0) {
            conditions.add("s.class_id = ?")
            args.add(classId.toString())
        }

        if (statusFilter.isNotEmpty() && statusFilter != "ALL") {
            conditions.add("a.status = ?")
            args.add(statusFilter)
        }

        val whereClause = if (conditions.isNotEmpty()) "WHERE " + conditions.joinToString(" AND ") else ""
        val query = """
            SELECT a.id, a.date, a.time_in, a.status, a.late_minutes, s.nis, s.name, c.name as class_name, s.parent_phone, a.notes
            FROM attendances a
            JOIN students s ON a.student_id = s.id
            LEFT JOIN classes c ON s.class_id = c.id
            $whereClause
            ORDER BY a.date DESC, a.time_in DESC
        """

        val cursor = readableDatabase.rawQuery(query, args.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                val obj = JSONObject().apply {
                    put("id", it.getInt(0))
                    put("date", it.getString(1))
                    put("time_in", it.getString(2))
                    put("status", it.getString(3))
                    put("late_minutes", it.getInt(4))
                    put("nis", it.getString(5))
                    put("name", it.getString(6))
                    put("class_name", it.getString(7) ?: "-")
                    put("parent_phone", it.getString(8) ?: "")
                    put("notes", it.getString(9) ?: "")
                }
                arr.put(obj)
            }
        }
        return arr
    }

    // --- Pencarian / Pembuatan Kelas Otomatis saat Import ---
    fun getOrCreateClass(className: String): Int {
        val cleanName = className.trim()
        if (cleanName.isEmpty()) return 1

        val cursor = readableDatabase.rawQuery(
            "SELECT id FROM classes WHERE LOWER(name) = LOWER(?) LIMIT 1",
            arrayOf(cleanName)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }

        val cv = ContentValues().apply {
            put("name", cleanName)
            put("level", "Santri")
        }
        return writableDatabase.insert("classes", null, cv).toInt()
    }

    // --- Import Santri Massal dari JSON/CSV ---
    fun importStudents(students: JSONArray): JSONObject {
        var inserted = 0
        var updated = 0
        var failed = 0

        val db = writableDatabase
        db.beginTransaction()
        try {
            for (i in 0 until students.length()) {
                val s = students.getJSONObject(i)
                val nis = s.optString("nis", "").trim()
                val name = s.optString("name", "").trim()

                if (nis.isEmpty() || name.isEmpty()) {
                    failed++
                    continue
                }

                val className = s.optString("class_name", "").trim()
                val classId = if (className.isNotEmpty()) {
                    getOrCreateClass(className)
                } else {
                    s.optInt("class_id", 1)
                }

                val rfidUid = s.optString("rfid_uid", "").trim().ifEmpty { nis }
                val gender = s.optString("gender", "L").trim().uppercase().take(1).ifEmpty { "L" }
                val pob = s.optString("pob", "").trim()
                val dob = s.optString("dob", "").trim()
                val parentName = s.optString("parent_name", "").trim()
                val parentPhone = s.optString("parent_phone", "").trim()

                // Cek apakah NIS sudah ada
                val checkCursor = readableDatabase.rawQuery(
                    "SELECT id FROM students WHERE nis = ? LIMIT 1",
                    arrayOf(nis)
                )
                val existingId = checkCursor.use {
                    if (it.moveToFirst()) it.getInt(0) else null
                }

                val cv = ContentValues().apply {
                    put("nis", nis)
                    put("name", name)
                    put("class_id", classId)
                    put("rfid_uid", rfidUid)
                    put("gender", gender)
                    put("pob", pob)
                    put("dob", dob)
                    put("parent_name", parentName)
                    put("parent_phone", parentPhone)
                    put("qr_code_token", "QR-$nis")
                    put("is_active", 1)
                }

                if (existingId != null) {
                    db.update("students", cv, "id = ?", arrayOf(existingId.toString()))
                    updated++
                } else {
                    db.insert("students", null, cv)
                    inserted++
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
            return JSONObject().apply {
                put("success", false)
                put("message", "Gagal mengimpor data: ${e.message}")
            }
        } finally {
            db.endTransaction()
        }

        return JSONObject().apply {
            put("success", true)
            put("total", students.length())
            put("inserted", inserted)
            put("updated", updated)
            put("failed", failed)
            put("message", "Berhasil mengimpor $inserted santri baru dan memperbarui $updated santri.")
        }
    }
}
