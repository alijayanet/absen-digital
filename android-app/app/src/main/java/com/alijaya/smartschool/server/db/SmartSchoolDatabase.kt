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
        const val DATABASE_VERSION = 4

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

        // 6. Daily Queues Table (Antrian Mengaji Meja Ustadz)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS daily_queues (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL,
                class_id INTEGER,
                teacher_id INTEGER,
                queue_number INTEGER NOT NULL,
                date TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'WAITING',
                call_count INTEGER DEFAULT 0,
                called_at TEXT,
                completed_at TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """)

        // Index untuk performa query cepat
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_rfid ON students(rfid_uid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_nis ON students(nis)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attendances_date ON attendances(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_queues_lookup ON daily_queues(date, status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_queues_student ON daily_queues(student_id, date)")

        // Seed Default Settings
        val defaultSettings = mapOf(
            "school_name" to "MUSHOLLA AL MUHAJIRIN",
            "school_tagline" to "Sistem Presensi & Antrian Mengaji Digital",
            "school_address" to "Jl. Raya Pesantren No. 1",
            "school_phone" to "08123456789",
            "school_principal_name" to "Ust. H. Abdullah, Lc.",
            "school_logo" to "",
            "jam_masuk" to "07:00",
            "toleransi_telat" to "07:15",
            "jam_pulang" to "14:00",
            "queue_enabled" to "1",
            "queue_calling_prefix" to "Nomor antrian",
            "wa_provider" to "fonnte",
            "wa_fonnte_token" to "",
            "wa_wablas_token" to "",
            "wa_wablas_domain" to "https://api.wablas.com",
            "entity_type" to "santri",
            "wa_enabled" to "1",
            "telegram_bot_token" to "",
            "telegram_chat_id" to "",
            "telegram_enabled" to "0",
            "running_text" to "Selamat Datang di MUSHOLLA AL MUHAJIRIN • Santri wajib melakukan presensi kartu RFID sebelum mengaji • Mohon tertib, tenang, dan jaga kebersihan",
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

        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS daily_queues (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id INTEGER NOT NULL,
                    class_id INTEGER,
                    teacher_id INTEGER,
                    queue_number INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'WAITING',
                    call_count INTEGER DEFAULT 0,
                    called_at TEXT,
                    completed_at TEXT,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_queues_lookup ON daily_queues(date, status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_queues_student ON daily_queues(student_id, date)")
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

        // Migrasi & Inisialisasi Setting Baru (Telegram dsb) jika belum ada di database lama
        try {
            val defaultSettings = mapOf(
                "telegram_bot_token" to "",
                "telegram_chat_id" to "",
                "telegram_enabled" to "0",
                "running_text" to "Selamat Datang di MUSHOLLA AL MUHAJIRIN • Santri wajib melakukan presensi kartu RFID sebelum mengaji • Mohon tertib, tenang, dan jaga kebersihan"
            )
            for ((k, v) in defaultSettings) {
                val cv = ContentValues().apply {
                    put("key", k)
                    put("value", v)
                }
                db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
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
        if (clean.isEmpty()) return null

        // 1. Coba pencarian tepat berdasarkan RFID UID, NIS, atau QR Code Token
        val exactQuery = """
            SELECT s.id, s.nis, s.name, s.gender, s.pob, s.dob, s.class_id, c.name as class_name, 
                   s.rfid_uid, s.parent_name, s.parent_phone, s.photo,
                   t.name as teacher_name
            FROM students s
            LEFT JOIN classes c ON s.class_id = c.id
            LEFT JOIN teachers t ON c.teacher_id = t.id
            WHERE s.rfid_uid = ? OR s.nis = ? OR s.qr_code_token = ?
            LIMIT 1
        """
        val cursorExact = readableDatabase.rawQuery(exactQuery, arrayOf(clean, clean, clean))
        cursorExact.use {
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

        // 2. Fallback pencarian fleksibel berdasarkan NAMA santri (Case-Insensitive Exact atau Prefix Match)
        val nameQuery = """
            SELECT s.id, s.nis, s.name, s.gender, s.pob, s.dob, s.class_id, c.name as class_name, 
                   s.rfid_uid, s.parent_name, s.parent_phone, s.photo,
                   t.name as teacher_name
            FROM students s
            LEFT JOIN classes c ON s.class_id = c.id
            LEFT JOIN teachers t ON c.teacher_id = t.id
            WHERE LOWER(TRIM(s.name)) = LOWER(?) 
               OR LOWER(s.name) LIKE LOWER(? || '%')
               OR LOWER(s.name) LIKE LOWER('%' || ? || '%')
            ORDER BY 
               CASE 
                   WHEN LOWER(TRIM(s.name)) = LOWER(?) THEN 1
                   WHEN LOWER(s.name) LIKE LOWER(? || '%') THEN 2
                   ELSE 3
               END
            LIMIT 1
        """
        val cursorName = readableDatabase.rawQuery(nameQuery, arrayOf(clean, clean, clean, clean, clean))
        cursorName.use {
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

    // =========================================================================
    // --- ANTRIAN MENGAJI MEJA USTADZ (MULTI-MEJA STANDALONE) ---
    // =========================================================================

    /**
     * Dapatkan nomor antrian santri jika sudah terdaftar hari ini
     */
    fun getQueueByStudent(studentId: Int, date: String): JSONObject? {
        val query = """
            SELECT q.id, q.student_id, q.class_id, q.teacher_id, q.queue_number, q.date, q.status,
                   c.name as class_name, s.name as student_name, s.gender as student_gender,
                   t.name as teacher_name
            FROM daily_queues q
            LEFT JOIN classes c ON q.class_id = c.id
            JOIN students s ON q.student_id = s.id
            LEFT JOIN teachers t ON q.teacher_id = t.id
            WHERE q.student_id = ? AND q.date = ?
            LIMIT 1
        """
        val cursor = readableDatabase.rawQuery(query, arrayOf(studentId.toString(), date))
        cursor.use {
            if (it.moveToFirst()) {
                val tName = it.getString(10) ?: it.getString(7) ?: "Ustadz"
                return JSONObject().apply {
                    put("id", it.getInt(0))
                    put("student_id", it.getInt(1))
                    put("class_id", if (it.isNull(2)) 0 else it.getInt(2))
                    put("teacher_id", if (it.isNull(3)) 0 else it.getInt(3))
                    put("queue_number", it.getInt(4))
                    put("date", it.getString(5))
                    put("status", it.getString(6))
                    put("class_name", it.getString(7) ?: "")
                    put("student_name", it.getString(8) ?: "")
                    put("student_gender", it.getString(9) ?: "L")
                    put("teacher_name", tName)
                    put("desk_name", "Meja $tName")
                }
            }
        }
        return null
    }

    /**
     * Daftarkan nomor antrian untuk santri saat presensi datang (Kiosk / manual)
     */
    fun assignQueueNumber(studentId: Int, classId: Int, dateStr: String? = null, teacherId: Int? = null): JSONObject? {
        val date = dateStr ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (studentId <= 0) return null

        // 1. Cek apakah sudah ada antrian hari ini
        val existing = getQueueByStudent(studentId, date)
        if (existing != null) return existing

        var targetTeacherId = teacherId
        var targetClassId = classId

        // Cari relasi kelas / guru jika belum disediakan
        if (targetTeacherId == null || targetTeacherId <= 0) {
            val studentCursor = readableDatabase.rawQuery(
                "SELECT s.class_id, c.teacher_id FROM students s LEFT JOIN classes c ON s.class_id = c.id WHERE s.id = ?",
                arrayOf(studentId.toString())
            )
            studentCursor.use {
                if (it.moveToFirst()) {
                    if (targetClassId <= 0) targetClassId = it.getInt(0)
                    if (!it.isNull(1)) targetTeacherId = it.getInt(1)
                }
            }
        }

        // Cari nomor antrian berikutnya
        var nextNumber = 1
        val numQuery = if (targetTeacherId != null && targetTeacherId!! > 0) {
            """
                SELECT COALESCE(MAX(queue_number), 0) + 1 
                FROM daily_queues 
                WHERE (teacher_id = ? OR (teacher_id IS NULL AND class_id = ?)) AND date = ?
            """
        } else {
            """
                SELECT COALESCE(MAX(queue_number), 0) + 1 
                FROM daily_queues 
                WHERE class_id = ? AND date = ?
            """
        }

        val args = if (targetTeacherId != null && targetTeacherId!! > 0) {
            arrayOf(targetTeacherId.toString(), targetClassId.toString(), date)
        } else {
            arrayOf(targetClassId.toString(), date)
        }

        val numCursor = readableDatabase.rawQuery(numQuery, args)
        numCursor.use {
            if (it.moveToFirst()) {
                nextNumber = it.getInt(0)
            }
        }

        val cv = ContentValues().apply {
            put("student_id", studentId)
            put("class_id", if (targetClassId > 0) targetClassId else 1)
            if (targetTeacherId != null && targetTeacherId!! > 0) {
                put("teacher_id", targetTeacherId)
            } else {
                putNull("teacher_id")
            }
            put("date", date)
            put("queue_number", nextNumber)
            put("status", "WAITING")
            put("call_count", 0)
        }

        val insertedId = writableDatabase.insert("daily_queues", null, cv)
        if (insertedId > 0) {
            return getQueueByStudent(studentId, date)
        }
        return null
    }

    /**
     * Data Panel Panggilan Meja Ustadz (Desk Data)
     */
    fun getDeskData(deskId: Int, dateStr: String? = null): JSONObject {
        val date = dateStr ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Coba cari dari guru atau kelas
        var teacherName = ""
        var className = ""
        var targetTeacherId: Int? = null
        var targetClassId = deskId

        val tCursor = readableDatabase.rawQuery(
            "SELECT t.id, t.name, c.id, c.name FROM teachers t LEFT JOIN classes c ON c.teacher_id = t.id WHERE t.id = ?",
            arrayOf(deskId.toString())
        )
        var isTeacherFound = false
        tCursor.use {
            if (it.moveToFirst()) {
                isTeacherFound = true
                targetTeacherId = it.getInt(0)
                teacherName = it.getString(1) ?: "Ustadz"
                if (!it.isNull(2)) {
                    targetClassId = it.getInt(2)
                    className = it.getString(3) ?: ""
                }
            }
        }

        if (!isTeacherFound) {
            val cCursor = readableDatabase.rawQuery(
                "SELECT c.id, c.name, t.id, t.name FROM classes c LEFT JOIN teachers t ON c.teacher_id = t.id WHERE c.id = ?",
                arrayOf(deskId.toString())
            )
            cCursor.use {
                if (it.moveToFirst()) {
                    targetClassId = it.getInt(0)
                    className = it.getString(1) ?: "Kelas"
                    if (!it.isNull(2)) {
                        targetTeacherId = it.getInt(2)
                        teacherName = it.getString(3) ?: ""
                    }
                }
            }
        }

        val classInfo = JSONObject().apply {
            put("id", targetClassId)
            put("name", className.ifEmpty { teacherName })
            put("teacher_id", targetTeacherId ?: 0)
            put("teacher_name", teacherName.ifEmpty { "Ustadz Pengajar" })
        }

        // Santri yang sedang dipanggil / mengaji (CALLING / SERVING)
        var currentObj: JSONObject? = null
        val currentQuery = """
            SELECT q.id, q.queue_number, q.call_count, q.called_at, s.id as student_id, s.name as student_name,
                   s.nis, s.gender as student_gender, s.photo, s.parent_phone
            FROM daily_queues q
            JOIN students s ON q.student_id = s.id
            WHERE ((q.teacher_id = ? AND ? > 0) OR (q.teacher_id IS NULL AND q.class_id = ?))
              AND q.date = ?
              AND q.status IN ('CALLING', 'SERVING')
            ORDER BY q.called_at DESC
            LIMIT 1
        """
        val tIdVal = targetTeacherId ?: 0
        val currentCursor = readableDatabase.rawQuery(currentQuery, arrayOf(tIdVal.toString(), tIdVal.toString(), targetClassId.toString(), date))
        currentCursor.use {
            if (it.moveToFirst()) {
                currentObj = JSONObject().apply {
                    put("id", it.getInt(0))
                    put("queue_number", it.getInt(1))
                    put("call_count", it.getInt(2))
                    put("called_at", it.getString(3) ?: "")
                    put("student_id", it.getInt(4))
                    put("student_name", it.getString(5))
                    put("nis", it.getString(6) ?: "-")
                    put("student_gender", it.getString(7) ?: "L")
                    put("photo", it.getString(8) ?: "")
                    put("parent_phone", it.getString(9) ?: "")
                }
            }
        }

        // Waiting list
        val waitingArr = JSONArray()
        val waitingQuery = """
            SELECT q.id, q.queue_number, s.id as student_id, s.name as student_name, s.nis, s.photo
            FROM daily_queues q
            JOIN students s ON q.student_id = s.id
            WHERE ((q.teacher_id = ? AND ? > 0) OR (q.teacher_id IS NULL AND q.class_id = ?))
              AND q.date = ?
              AND q.status = 'WAITING'
            ORDER BY q.queue_number ASC
        """
        val waitingCursor = readableDatabase.rawQuery(waitingQuery, arrayOf(tIdVal.toString(), tIdVal.toString(), targetClassId.toString(), date))
        waitingCursor.use {
            while (it.moveToNext()) {
                waitingArr.put(JSONObject().apply {
                    put("id", it.getInt(0))
                    put("queue_number", it.getInt(1))
                    put("student_id", it.getInt(2))
                    put("student_name", it.getString(3))
                    put("nis", it.getString(4) ?: "-")
                    put("photo", it.getString(5) ?: "")
                })
            }
        }

        // Skipped list
        val skippedArr = JSONArray()
        val skippedQuery = """
            SELECT q.id, q.queue_number, s.id as student_id, s.name as student_name, s.nis, s.photo
            FROM daily_queues q
            JOIN students s ON q.student_id = s.id
            WHERE ((q.teacher_id = ? AND ? > 0) OR (q.teacher_id IS NULL AND q.class_id = ?))
              AND q.date = ?
              AND q.status = 'SKIPPED'
            ORDER BY q.queue_number ASC
        """
        val skippedCursor = readableDatabase.rawQuery(skippedQuery, arrayOf(tIdVal.toString(), tIdVal.toString(), targetClassId.toString(), date))
        skippedCursor.use {
            while (it.moveToNext()) {
                skippedArr.put(JSONObject().apply {
                    put("id", it.getInt(0))
                    put("queue_number", it.getInt(1))
                    put("student_id", it.getInt(2))
                    put("student_name", it.getString(3))
                    put("nis", it.getString(4) ?: "-")
                    put("photo", it.getString(5) ?: "")
                })
            }
        }

        // Completed list
        val completedArr = JSONArray()
        val completedQuery = """
            SELECT q.id, q.queue_number, s.id as student_id, s.name as student_name, s.nis, s.photo
            FROM daily_queues q
            JOIN students s ON q.student_id = s.id
            WHERE ((q.teacher_id = ? AND ? > 0) OR (q.teacher_id IS NULL AND q.class_id = ?))
              AND q.date = ?
              AND q.status = 'COMPLETED'
            ORDER BY q.completed_at DESC
            LIMIT 15
        """
        val completedCursor = readableDatabase.rawQuery(completedQuery, arrayOf(tIdVal.toString(), tIdVal.toString(), targetClassId.toString(), date))
        completedCursor.use {
            while (it.moveToNext()) {
                completedArr.put(JSONObject().apply {
                    put("id", it.getInt(0))
                    put("queue_number", it.getInt(1))
                    put("student_id", it.getInt(2))
                    put("student_name", it.getString(3))
                    put("nis", it.getString(4) ?: "-")
                    put("photo", it.getString(5) ?: "")
                })
            }
        }

        // Stats
        val statsQuery = """
            SELECT 
                COUNT(*) as total,
                COALESCE(SUM(CASE WHEN status = 'WAITING' THEN 1 ELSE 0 END), 0) as waiting,
                COALESCE(SUM(CASE WHEN status IN ('CALLING', 'SERVING') THEN 1 ELSE 0 END), 0) as serving,
                COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) as completed,
                COALESCE(SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END), 0) as skipped
            FROM daily_queues
            WHERE ((teacher_id = ? AND ? > 0) OR (teacher_id IS NULL AND class_id = ?))
              AND date = ?
        """
        var statsObj = JSONObject()
        val statsCursor = readableDatabase.rawQuery(statsQuery, arrayOf(tIdVal.toString(), tIdVal.toString(), targetClassId.toString(), date))
        statsCursor.use {
            if (it.moveToFirst()) {
                statsObj = JSONObject().apply {
                    put("total", it.getInt(0))
                    put("waiting", it.getInt(1))
                    put("serving", it.getInt(2))
                    put("completed", it.getInt(3))
                    put("skipped", it.getInt(4))
                }
            }
        }

        return JSONObject().apply {
            put("classInfo", classInfo)
            put("current", currentObj ?: JSONObject.NULL)
            put("waitingList", waitingArr)
            put("skippedList", skippedArr)
            put("completedList", completedArr)
            put("stats", statsObj)
        }
    }

    /**
     * Panggil antrian berikutnya di meja
     */
    fun callNextQueue(deskId: Int, dateStr: String? = null): JSONObject {
        val date = dateStr ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timeNow = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        var targetTeacherId: Int? = null
        var targetClassId = deskId

        val tCursor = readableDatabase.rawQuery("SELECT id FROM teachers WHERE id = ?", arrayOf(deskId.toString()))
        val isTeacher = tCursor.use { it.moveToFirst() }
        if (isTeacher) {
            targetTeacherId = deskId
            val cCursor = readableDatabase.rawQuery("SELECT id FROM classes WHERE teacher_id = ?", arrayOf(deskId.toString()))
            cCursor.use { if (it.moveToFirst()) targetClassId = it.getInt(0) }
        } else {
            val cCursor = readableDatabase.rawQuery("SELECT teacher_id FROM classes WHERE id = ?", arrayOf(deskId.toString()))
            cCursor.use {
                if (it.moveToFirst() && !it.isNull(0)) targetTeacherId = it.getInt(0)
            }
        }

        val tIdVal = targetTeacherId ?: 0

        // Selesaikan santri yang sedang dipanggil saat ini
        val finishSql = """
            UPDATE daily_queues 
            SET status = 'COMPLETED', completed_at = ? 
            WHERE ((teacher_id = ? AND ? > 0) OR (teacher_id IS NULL AND class_id = ?))
              AND date = ? 
              AND status IN ('CALLING', 'SERVING')
        """
        writableDatabase.execSQL(finishSql, arrayOf(timeNow, tIdVal.toString(), tIdVal.toString(), targetClassId.toString(), date))

        // Cari antrian urutan teratas yang WAITING
        val nextSql = """
            SELECT id, queue_number, student_id 
            FROM daily_queues 
            WHERE ((teacher_id = ? AND ? > 0) OR (teacher_id IS NULL AND class_id = ?))
              AND date = ? 
              AND status = 'WAITING'
            ORDER BY queue_number ASC
            LIMIT 1
        """
        var nextId = 0
        val nextCursor = readableDatabase.rawQuery(nextSql, arrayOf(tIdVal.toString(), tIdVal.toString(), targetClassId.toString(), date))
        nextCursor.use {
            if (it.moveToFirst()) nextId = it.getInt(0)
        }

        if (nextId <= 0) {
            return JSONObject().apply {
                put("success", false)
                put("message", "Semua antrian santri yang menunggu sudah dipanggil.")
            }
        }

        // Update status santri menjadi CALLING
        writableDatabase.execSQL(
            "UPDATE daily_queues SET status = 'CALLING', called_at = ?, call_count = 1 WHERE id = ?",
            arrayOf(timeNow, nextId.toString())
        )

        val detailQuery = """
            SELECT q.id, q.queue_number, q.call_count, q.called_at,
                   s.name as student_name, s.gender as student_gender,
                   COALESCE(t.name, c.name, 'Ustadz') as teacher_name,
                   c.name as class_name
            FROM daily_queues q
            JOIN students s ON q.student_id = s.id
            LEFT JOIN teachers t ON q.teacher_id = t.id
            LEFT JOIN classes c ON q.class_id = c.id
            WHERE q.id = ?
        """
        val detailCursor = readableDatabase.rawQuery(detailQuery, arrayOf(nextId.toString()))
        var calledObj = JSONObject()
        detailCursor.use {
            if (it.moveToFirst()) {
                val qNum = it.getInt(1)
                val sName = it.getString(4)
                val tName = it.getString(6) ?: "Ustadz"
                calledObj = JSONObject().apply {
                    put("id", it.getInt(0))
                    put("queue_number", qNum)
                    put("call_count", it.getInt(2))
                    put("called_at", it.getString(3) ?: "")
                    put("student_name", sName)
                    put("student_gender", it.getString(5) ?: "L")
                    put("teacher_name", tName)
                    put("class_name", it.getString(7) ?: "")
                    put("speechSentence", "Nomor antrian $qNum, $sName, silakan menuju $tName.")
                }
            }
        }

        return JSONObject().apply {
            put("success", true)
            put("message", "Memanggil ${calledObj.optString("student_name")} (Nomor ${calledObj.optInt("queue_number")})")
            put("queue", calledObj)
        }
    }

    /**
     * Panggil ulang (Recall)
     */
    fun recallQueue(queueId: Int): JSONObject {
        val timeNow = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        writableDatabase.execSQL(
            "UPDATE daily_queues SET status = 'CALLING', called_at = ?, call_count = COALESCE(call_count, 0) + 1 WHERE id = ?",
            arrayOf(timeNow, queueId.toString())
        )

        val detailQuery = """
            SELECT q.id, q.queue_number, q.call_count, q.called_at,
                   s.name as student_name, s.gender as student_gender,
                   COALESCE(t.name, c.name, 'Ustadz') as teacher_name,
                   c.name as class_name
            FROM daily_queues q
            JOIN students s ON q.student_id = s.id
            LEFT JOIN teachers t ON q.teacher_id = t.id
            LEFT JOIN classes c ON q.class_id = c.id
            WHERE q.id = ?
        """
        val detailCursor = readableDatabase.rawQuery(detailQuery, arrayOf(queueId.toString()))
        var calledObj = JSONObject()
        detailCursor.use {
            if (it.moveToFirst()) {
                val qNum = it.getInt(1)
                val sName = it.getString(4)
                val tName = it.getString(6) ?: "Ustadz"
                calledObj = JSONObject().apply {
                    put("id", it.getInt(0))
                    put("queue_number", qNum)
                    put("call_count", it.getInt(2))
                    put("called_at", it.getString(3) ?: "")
                    put("student_name", sName)
                    put("student_gender", it.getString(5) ?: "L")
                    put("teacher_name", tName)
                    put("class_name", it.getString(7) ?: "")
                    put("speechSentence", "Panggilan ulang. Nomor antrian $qNum, $sName, silakan menuju $tName.")
                }
            }
        }

        return JSONObject().apply {
            put("success", true)
            put("called", calledObj)
        }
    }

    /**
     * Lewati antrian (Skip)
     */
    fun skipQueue(queueId: Int): JSONObject {
        val count = writableDatabase.delete("daily_queues", "id = ?", arrayOf(queueId.toString()))
        val cv = ContentValues().apply {
            put("status", "SKIPPED")
            putNull("called_at")
        }
        val updated = writableDatabase.update("daily_queues", cv, "id = ?", arrayOf(queueId.toString()))
        return JSONObject().apply {
            put("success", updated > 0)
            put("message", if (updated > 0) "Santri berhasil dilewati sementara." else "Data antrian tidak ditemukan")
        }
    }

    /**
     * Selesaikan antrian (Finish)
     */
    fun finishQueue(queueId: Int): JSONObject {
        val timeNow = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val cv = ContentValues().apply {
            put("status", "COMPLETED")
            put("completed_at", timeNow)
        }
        val updated = writableDatabase.update("daily_queues", cv, "id = ?", arrayOf(queueId.toString()))
        return JSONObject().apply {
            put("success", updated > 0)
            put("message", if (updated > 0) "Santri telah selesai mengaji." else "Data antrian tidak ditemukan")
        }
    }

    /**
     * Data Display TV Kiosk Lengkap (/api/display-data)
     * Langsung disuplai ke UI TV Native Android & Web Remote Display
     */
    fun getDisplayData(): JSONObject {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val schoolName = getSetting("school_name", "MUSHOLLA AL MUHAJIRIN")
        val schoolTagline = getSetting("school_tagline", "Sistem Presensi & Antrian Mengaji Digital")
        val schoolLogo = getSetting("school_logo", "")
        val prefix = getSetting("queue_calling_prefix", "Nomor antrian")

        // 1. Ambil seluruh meja (berdasarkan guru atau kelas)
        val teachers = JSONArray()
        val tQuery = """
            SELECT t.id, t.name, c.id as class_id, c.name as class_name
            FROM teachers t
            LEFT JOIN classes c ON c.teacher_id = t.id
            WHERE t.is_active = 1
            ORDER BY t.name ASC
        """
        val tCursor = readableDatabase.rawQuery(tQuery, null)
        val tablesArr = JSONArray()

        tCursor.use {
            var deskNum = 1
            while (it.moveToNext()) {
                val tId = it.getInt(0)
                val tName = it.getString(1)
                val cId = if (it.isNull(2)) tId else it.getInt(2)
                val cName = it.getString(3) ?: tName

                val deskData = getDeskData(tId, today)
                val current = deskData.optJSONObject("current")
                val waitingList = deskData.optJSONArray("waitingList") ?: JSONArray()
                val stats = deskData.optJSONObject("stats") ?: JSONObject()

                // Batasi waiting list di display TV 4 teratas
                val displayWaiting = JSONArray()
                for (w in 0 until minOf(4, waitingList.length())) {
                    displayWaiting.put(waitingList.getJSONObject(w))
                }

                val tableObj = JSONObject().apply {
                    put("deskNumber", deskNum++)
                    put("tableId", tId)
                    put("classId", cId)
                    put("teacherId", tId)
                    put("tableName", "Meja $tName")
                    put("className", tName)
                    put("teacherName", tName)
                    put("current", current ?: JSONObject.NULL)
                    put("nextWaiting", displayWaiting)
                    put("stats", stats)
                }
                tablesArr.put(tableObj)
            }
        }

        // Jika belum ada guru, fallback ke daftar kelas
        if (tablesArr.length() == 0) {
            val cQuery = "SELECT id, name FROM classes ORDER BY name ASC"
            val cCursor = readableDatabase.rawQuery(cQuery, null)
            cCursor.use {
                var deskNum = 1
                while (it.moveToNext()) {
                    val cId = it.getInt(0)
                    val cName = it.getString(1)
                    val deskData = getDeskData(cId, today)
                    val current = deskData.optJSONObject("current")
                    val waitingList = deskData.optJSONArray("waitingList") ?: JSONArray()
                    val stats = deskData.optJSONObject("stats") ?: JSONObject()

                    val displayWaiting = JSONArray()
                    for (w in 0 until minOf(4, waitingList.length())) {
                        displayWaiting.put(waitingList.getJSONObject(w))
                    }

                    val tableObj = JSONObject().apply {
                        put("deskNumber", deskNum++)
                        put("classId", cId)
                        put("tableName", "Meja $cName")
                        put("className", cName)
                        put("teacherName", "Ustadz Pengajar")
                        put("current", current ?: JSONObject.NULL)
                        put("nextWaiting", displayWaiting)
                        put("stats", stats)
                    }
                    tablesArr.put(tableObj)
                }
            }
        }

        // 2. Ambil lastCall (Panggilan aktif paling baru)
        var lastCallObj: JSONObject? = null
        val lastCallQuery = """
            SELECT q.id, q.queue_number, q.call_count, q.called_at,
                   s.name as student_name, s.gender as student_gender,
                   COALESCE(t.name, c.name, 'Ustadz') as teacher_name,
                   c.name as class_name
            FROM daily_queues q
            JOIN students s ON q.student_id = s.id
            LEFT JOIN teachers t ON q.teacher_id = t.id
            LEFT JOIN classes c ON q.class_id = c.id
            WHERE q.date = ? AND q.status = 'CALLING' AND q.called_at IS NOT NULL
            ORDER BY q.called_at DESC
            LIMIT 1
        """
        val lastCursor = readableDatabase.rawQuery(lastCallQuery, arrayOf(today))
        lastCursor.use {
            if (it.moveToFirst()) {
                val qNum = it.getInt(1)
                val sName = it.getString(4)
                val tName = it.getString(6) ?: "Ustadz"
                lastCallObj = JSONObject().apply {
                    put("id", it.getInt(0))
                    put("queueNumber", qNum)
                    put("queue_number", qNum)
                    put("callCount", it.getInt(2))
                    put("call_count", it.getInt(2))
                    put("calledAt", it.getString(3) ?: "")
                    put("called_at", it.getString(3) ?: "")
                    put("studentName", sName)
                    put("student_name", sName)
                    put("student_gender", it.getString(5) ?: "L")
                    put("className", tName)
                    put("teacherName", tName)
                    put("speechSentence", "$prefix $qNum, $sName, silakan menuju $tName.")
                }
            }
        }

        // 3. Running Text Marquee
        val defaultAnnouncement = "Selamat Datang di $schoolName   •••   Santri wajib melakukan presensi kartu RFID sebelum mengaji   •••   Mohon tertib, tenang, dan jaga kebersihan"
        val announcement = getSetting("running_text", defaultAnnouncement).ifBlank { defaultAnnouncement }

        // 4. Jadwal Sholat Kemenag Falakiyah Offline
        val lat = getSetting("prayer_latitude", "-6.3263").toDoubleOrNull() ?: -6.3263
        val lng = getSetting("prayer_longitude", "108.3200").toDoubleOrNull() ?: 108.3200
        val tz = getSetting("prayer_timezone", "7.0").toDoubleOrNull() ?: 7.0
        val iht = getSetting("prayer_ihtiyat", "2").toIntOrNull() ?: 2
        val prayerObj = com.alijaya.smartschool.server.prayer.PrayerCalculator.calculateTodayPrayer(lat, lng, tz, iht)

        return JSONObject().apply {
            put("success", true)
            put("date", today)
            put("schoolName", schoolName)
            put("school_name", schoolName)
            put("schoolTagline", schoolTagline)
            put("school_tagline", schoolTagline)
            put("schoolLogo", schoolLogo)
            put("school_logo", schoolLogo)
            put("announcement", announcement)
            put("tables", tablesArr)
            put("lastCall", lastCallObj ?: JSONObject.NULL)
            put("prayer", prayerObj)
        }
    }
}

