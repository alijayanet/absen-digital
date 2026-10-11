package com.alijaya.smartschool.tv.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    suspend fun all(): List<Setting>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: Setting)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(settings: List<Setting>)
}

@Dao
interface ClassDao {
    @Query(
        """SELECT c.id, c.name, c.level,
           (SELECT COUNT(*) FROM students s WHERE s.classId = c.id AND s.isActive = 1) AS studentCount,
           (SELECT u.name FROM users u WHERE u.classId = c.id LIMIT 1) AS waliName
           FROM classes c ORDER BY c.level, c.name"""
    )
    fun observeRows(): Flow<List<ClassRow>>

    @Query(
        """SELECT c.id, c.name, c.level,
           (SELECT COUNT(*) FROM students s WHERE s.classId = c.id AND s.isActive = 1) AS studentCount,
           (SELECT u.name FROM users u WHERE u.classId = c.id LIMIT 1) AS waliName
           FROM classes c ORDER BY c.level, c.name"""
    )
    suspend fun rows(): List<ClassRow>

    @Query("SELECT * FROM classes ORDER BY level, name")
    suspend fun all(): List<SchoolClass>

    @Query("SELECT * FROM classes WHERE id = :id")
    suspend fun byId(id: Long): SchoolClass?

    @Insert suspend fun insert(c: SchoolClass): Long
    @Update suspend fun update(c: SchoolClass)

    @Query("DELETE FROM classes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM classes")
    suspend fun count(): Int
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun byUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun byId(id: Long): User?

    @Query(
        """SELECT u.id, u.username, u.name, u.phone, u.role, u.classId, c.name AS className
           FROM users u LEFT JOIN classes c ON c.id = u.classId ORDER BY u.role, u.name"""
    )
    fun observeRows(): Flow<List<UserRow>>

    @Query(
        """SELECT u.id, u.username, u.name, u.phone, u.role, u.classId, c.name AS className
           FROM users u LEFT JOIN classes c ON c.id = u.classId ORDER BY u.role, u.name"""
    )
    suspend fun rows(): List<UserRow>

    @Insert suspend fun insert(u: User): Long
    @Update suspend fun update(u: User)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE users SET classId = NULL WHERE classId = :classId")
    suspend fun clearClass(classId: Long)

    @Query("SELECT COUNT(*) FROM users WHERE role = 'guru'")
    suspend fun countTeachers(): Int
}

@Dao
interface StudentDao {
    @Query(
        """SELECT s.id, s.nis, s.nisn, s.rfidUid, s.qrToken, s.name, s.gender, s.classId, c.name AS className,
           s.photo, s.parentName, s.parentPhone, s.waStatus, s.savingsBalance, s.isActive
           FROM students s LEFT JOIN classes c ON c.id = s.classId
           WHERE (:classId IS NULL OR s.classId = :classId)
             AND (:q = '' OR s.name LIKE '%' || :q || '%' OR s.nis LIKE '%' || :q || '%')
           ORDER BY c.name, s.name"""
    )
    fun observeRows(classId: Long?, q: String): Flow<List<StudentRow>>

    @Query(
        """SELECT s.id, s.nis, s.nisn, s.rfidUid, s.qrToken, s.name, s.gender, s.classId, c.name AS className,
           s.photo, s.parentName, s.parentPhone, s.waStatus, s.savingsBalance, s.isActive
           FROM students s LEFT JOIN classes c ON c.id = s.classId
           WHERE (:classId IS NULL OR s.classId = :classId)
             AND (:q = '' OR s.name LIKE '%' || :q || '%' OR s.nis LIKE '%' || :q || '%')
           ORDER BY c.name, s.name"""
    )
    suspend fun rows(classId: Long?, q: String): List<StudentRow>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun byId(id: Long): Student?

    @Query("SELECT * FROM students WHERE isActive = 1 AND (rfidUid = :code OR qrToken = :code OR nis = :code) LIMIT 1")
    suspend fun byScanCode(code: String): Student?

    @Insert suspend fun insert(s: Student): Long
    @Insert suspend fun insertAll(s: List<Student>)
    @Update suspend fun update(s: Student)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE students SET classId = NULL WHERE classId = :classId")
    suspend fun clearClass(classId: Long)

    @Query("SELECT COUNT(*) FROM students WHERE isActive = 1")
    suspend fun countActive(): Int
}

@Dao
interface AttendanceDao {
    @Query("SELECT status, COUNT(*) AS total FROM attendances WHERE date = :date GROUP BY status")
    suspend fun countByStatus(date: String): List<StatusCount>

    @Query("SELECT * FROM attendances WHERE studentId = :studentId AND date = :date LIMIT 1")
    suspend fun find(studentId: Long, date: String): Attendance?

    @Query(
        """SELECT a.id, a.studentId, s.nis, s.name, c.name AS className, a.date, a.timeIn, a.status, a.lateMinutes, a.notes, a.createdBy
           FROM attendances a
           JOIN students s ON s.id = a.studentId
           LEFT JOIN classes c ON c.id = s.classId
           WHERE a.date = :date
             AND (:classId IS NULL OR s.classId = :classId)
             AND (:status IS NULL OR a.status = :status)
             AND (:q = '' OR s.name LIKE '%' || :q || '%' OR s.nis LIKE '%' || :q || '%')
           ORDER BY a.timeIn DESC, s.name ASC"""
    )
    fun observeRows(date: String, classId: Long?, status: String?, q: String): Flow<List<AttendanceRow>>

    @Query(
        """SELECT a.id, a.studentId, s.nis, s.name, c.name AS className, a.date, a.timeIn, a.status, a.lateMinutes, a.notes, a.createdBy
           FROM attendances a
           JOIN students s ON s.id = a.studentId
           LEFT JOIN classes c ON c.id = s.classId
           WHERE a.date = :date
             AND (:classId IS NULL OR s.classId = :classId)
             AND (:status IS NULL OR a.status = :status)
             AND (:q = '' OR s.name LIKE '%' || :q || '%' OR s.nis LIKE '%' || :q || '%')
           ORDER BY a.timeIn DESC, s.name ASC"""
    )
    suspend fun rows(date: String, classId: Long?, status: String?, q: String): List<AttendanceRow>

    @Query(
        """SELECT a.id, a.studentId, s.nis, s.name, c.name AS className, a.date, a.timeIn, a.status, a.lateMinutes, a.notes, a.createdBy
           FROM attendances a
           JOIN students s ON s.id = a.studentId
           LEFT JOIN classes c ON c.id = s.classId
           WHERE a.date = :date
           ORDER BY a.timeIn DESC LIMIT :limit"""
    )
    suspend fun recent(date: String, limit: Int = 10): List<AttendanceRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: Attendance): Long

    @Query("DELETE FROM attendances WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM attendances WHERE studentId = :studentId")
    suspend fun deleteByStudent(studentId: Long)
}

@Dao
interface MiscDao {
    @Insert suspend fun insertCategories(list: List<FeeCategory>)
    @Insert suspend fun insertWa(msg: WaMessage): Long
    @Insert suspend fun insertLeave(l: LeaveRequest): Long
    @Insert suspend fun insertBill(b: StudentBill): Long
    @Insert suspend fun insertSaving(t: SavingTx): Long
    @Insert suspend fun insertAttendances(list: List<Attendance>)

    @Query(
        """SELECT l.id, l.studentId, s.nis, s.name AS studentName, c.name AS className,
                  l.type, l.startDate, l.endDate, l.reason, l.status, l.rejectionNote, l.createdAt
           FROM leave_requests l
           JOIN students s ON s.id = l.studentId
           LEFT JOIN classes c ON c.id = s.classId
           ORDER BY l.createdAt DESC"""
    )
    suspend fun leaveRows(): List<LeaveRow>

    @Query("SELECT * FROM leave_requests WHERE id = :id")
    suspend fun leaveById(id: Long): LeaveRequest?

    @Update suspend fun updateLeave(l: LeaveRequest)

    @Query("SELECT COUNT(*) FROM leave_requests WHERE status = 'PENDING'")
    suspend fun pendingLeaves(): Int

    @Query("SELECT COUNT(*) FROM student_bills WHERE status = 'UNPAID'")
    suspend fun unpaidBills(): Int

    @Query("SELECT COALESCE(SUM(savingsBalance),0) FROM students")
    suspend fun totalSavings(): Long

    @Query("SELECT COUNT(*) FROM wa_queue WHERE status = 'pending'")
    suspend fun pendingWa(): Int

    @Query("SELECT * FROM wa_queue WHERE status = 'pending' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun nextPendingWa(limit: Int = 10): List<WaMessage>

    @Query("SELECT * FROM wa_queue ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentWaMessages(limit: Int = 30): List<WaMessage>

    @Update suspend fun updateWa(msg: WaMessage)

    @Query("UPDATE wa_queue SET status = 'pending', attempts = 0 WHERE status = 'failed'")
    suspend fun retryFailedWa()

    @Query("DELETE FROM wa_queue WHERE status = 'sent' AND createdAt < :beforeTime")
    suspend fun cleanOldWa(beforeTime: Long)

    @Query("SELECT * FROM fee_categories WHERE isActive = 1")
    suspend fun categories(): List<FeeCategory>

    @Query(
        """SELECT b.id, b.billCode, b.studentId, s.nis, s.name AS studentName, c.name AS className,
                  b.title, b.period, b.baseAmount, b.uniqueCode, b.totalAmount, b.status, b.dueDate, b.paidAt,
                  s.parentPhone
           FROM student_bills b
           JOIN students s ON s.id = b.studentId
           LEFT JOIN classes c ON c.id = s.classId
           WHERE (:status IS NULL OR b.status = :status)
             AND (:classId IS NULL OR s.classId = :classId)
             AND (:q = '' OR s.name LIKE '%' || :q || '%' OR b.billCode LIKE '%' || :q || '%')
           ORDER BY b.createdAt DESC"""
    )
    suspend fun billRows(status: String?, classId: Long?, q: String): List<BillRow>

    @Query("SELECT * FROM student_bills WHERE id = :id")
    suspend fun billById(id: Long): StudentBill?

    @Query("SELECT * FROM student_bills WHERE totalAmount = :amount AND status = 'UNPAID' LIMIT 1")
    suspend fun billByAmount(amount: Long): StudentBill?

    @Update suspend fun updateBill(b: StudentBill)

    @Insert suspend fun insertReceipt(r: PaymentReceipt): Long

    @Insert suspend fun insertPaymentLog(l: PaymentLog): Long

    @Query("SELECT * FROM payment_logs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun paymentLogs(limit: Int = 30): List<PaymentLog>

    /* ---------- Tabungan Siswa (Savings) ---------- */
    @Query(
        """SELECT s.id, s.studentId, st.nis, st.name AS studentName, c.name AS className,
                  s.type, s.amount, s.balanceAfter, s.note, s.transactionDate, u.name AS teacherName
           FROM student_savings s
           JOIN students st ON st.id = s.studentId
           LEFT JOIN classes c ON c.id = st.classId
           LEFT JOIN users u ON u.id = s.teacherId
           WHERE (:studentId IS NULL OR s.studentId = :studentId)
             AND (:classId IS NULL OR st.classId = :classId)
           ORDER BY s.createdAt DESC LIMIT :limit"""
    )
    suspend fun savingRows(studentId: Long? = null, classId: Long? = null, limit: Int = 100): List<SavingRow>

    @Query("UPDATE students SET savingsBalance = savingsBalance + :delta WHERE id = :studentId")
    suspend fun updateStudentSavingsBalance(studentId: Long, delta: Long)

    /* ---------- Antrian Setoran / Tahfidz (Queue) ---------- */
    @Query("SELECT * FROM daily_queues WHERE studentId = :studentId AND date = :date LIMIT 1")
    suspend fun queueByStudent(studentId: Long, date: String): DailyQueue?

    @Query("SELECT * FROM daily_queues WHERE id = :id")
    suspend fun queueById(id: Long): DailyQueue?

    @Query("SELECT COALESCE(MAX(queueNumber), 0) FROM daily_queues WHERE date = :date")
    suspend fun maxQueueNumber(date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(q: DailyQueue): Long

    @Update suspend fun updateQueue(q: DailyQueue)

    @Query(
        """SELECT q.id, q.studentId, s.nis, s.name AS studentName, c.name AS className,
                  q.queueNumber, q.status, q.callCount, q.updatedAt
           FROM daily_queues q
           JOIN students s ON s.id = q.studentId
           LEFT JOIN classes c ON c.id = q.classId
           WHERE q.date = :date
           ORDER BY 
             CASE q.status 
               WHEN 'CALLING' THEN 1 
               WHEN 'SERVING' THEN 2 
               WHEN 'WAITING' THEN 3 
               WHEN 'SKIPPED' THEN 4 
               ELSE 5 
             END,
             q.queueNumber ASC"""
    )
    suspend fun queueRows(date: String): List<QueueRow>

    @Query(
        """SELECT q.id, q.studentId, s.nis, s.name AS studentName, c.name AS className,
                  q.queueNumber, q.status, q.callCount, q.updatedAt
           FROM daily_queues q
           JOIN students s ON s.id = q.studentId
           LEFT JOIN classes c ON c.id = q.classId
           WHERE q.date = :date AND q.status = 'CALLING'
           ORDER BY q.updatedAt DESC LIMIT 1"""
    )
    suspend fun currentCallingQueue(date: String): QueueRow?

    @Query("UPDATE daily_queues SET status = 'WAITING' WHERE date = :date AND status = 'CALLING'")
    suspend fun resetCallingToWaiting(date: String)
}

