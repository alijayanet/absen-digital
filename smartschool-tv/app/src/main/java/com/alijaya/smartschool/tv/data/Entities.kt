package com.alijaya.smartschool.tv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/* Skema mengikuti src/database/schema.sql versi Node (tanpa CMS/galeri). */

@Entity(tableName = "settings")
data class Setting(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "classes")
data class SchoolClass(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val level: String = ""
)

@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val name: String,
    val phone: String = "",
    val role: String = ROLE_GURU,
    val classId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_GURU = "guru"
    }
}

@Entity(
    tableName = "students",
    indices = [Index("classId"), Index(value = ["rfidUid"]), Index(value = ["qrToken"], unique = true), Index("nis")]
)
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nis: String,
    val nisn: String = "",
    val rfidUid: String? = null,
    val qrToken: String,
    val name: String,
    val gender: String = "L",
    val classId: Long? = null,
    val photo: String? = null,
    val parentName: String = "",
    val parentPhone: String = "",
    val waStatus: String = "unverified",
    val savingsBalance: Long = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendances",
    indices = [Index(value = ["studentId", "date"], unique = true), Index("date")]
)
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val date: String,          // yyyy-MM-dd
    val timeIn: String? = null, // HH:mm:ss
    val status: String,        // HADIR|TERLAMBAT|IZIN|SAKIT|ALPA
    val lateMinutes: Int = 0,
    val notes: String = "",
    val createdBy: String = "kiosk",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "wa_queue", indices = [Index("status")])
data class WaMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long? = null,
    val type: String,
    val phone: String,
    val message: String,
    val status: String = "pending", // pending|sending|sent|failed
    val attempts: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null
)

@Entity(tableName = "leave_requests", indices = [Index("studentId"), Index("status")])
data class LeaveRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val type: String,         // SAKIT|IZIN
    val startDate: String,
    val endDate: String,
    val reason: String = "",
    val attachment: String? = null,
    val status: String = "PENDING", // PENDING|APPROVED|REJECTED
    val reviewedBy: Long? = null,
    val rejectionNote: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "fee_categories", indices = [Index(value = ["code"], unique = true)])
data class FeeCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val defaultAmount: Long,
    val frequency: String = "MONTHLY", // MONTHLY|WEEKLY|ONCE
    val isActive: Boolean = true
)

@Entity(
    tableName = "student_bills",
    indices = [Index(value = ["billCode"], unique = true), Index("studentId"), Index("status"), Index("totalAmount")]
)
data class StudentBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billCode: String,
    val studentId: Long,
    val categoryId: Long?,
    val title: String,
    val period: String = "",
    val baseAmount: Long,
    val uniqueCode: Int = 0,
    val totalAmount: Long,
    val status: String = "UNPAID", // UNPAID|PAID|CANCELLED
    val dueDate: String? = null,
    val paidAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "payment_receipts", indices = [Index(value = ["receiptNo"], unique = true), Index("billId")])
data class PaymentReceipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNo: String,
    val billId: Long,
    val amountPaid: Long,
    val method: String, // QRIS|CASH|TRANSFER
    val channel: String = "",
    val receivedBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "payment_logs")
data class PaymentLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val text: String,
    val amount: Long? = null,
    val matchedBillId: Long? = null,
    val status: String = "UNMATCHED",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "student_savings", indices = [Index("studentId"), Index("classId")])
data class SavingTx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val teacherId: Long?,
    val classId: Long?,
    val type: String, // SETOR|TARIK
    val amount: Long,
    val balanceAfter: Long,
    val note: String = "",
    val transactionDate: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "daily_queues",
    indices = [Index(value = ["studentId", "date"], unique = true), Index("date")]
)
data class DailyQueue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val classId: Long?,
    val date: String,
    val queueNumber: Int,
    val status: String = "WAITING", // WAITING|CALLING|SERVING|COMPLETED|SKIPPED
    val callCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

/* ---------- Projections ---------- */

data class StudentRow(
    val id: Long,
    val nis: String,
    val nisn: String,
    val rfidUid: String?,
    val qrToken: String,
    val name: String,
    val gender: String,
    val classId: Long?,
    val className: String?,
    val photo: String?,
    val parentName: String,
    val parentPhone: String,
    val waStatus: String,
    val savingsBalance: Long,
    val isActive: Boolean
)

data class ClassRow(
    val id: Long,
    val name: String,
    val level: String,
    val studentCount: Int,
    val waliName: String?
)

data class UserRow(
    val id: Long,
    val username: String,
    val name: String,
    val phone: String,
    val role: String,
    val classId: Long?,
    val className: String?
)

data class StatusCount(val status: String, val total: Int)

data class AttendanceRow(
    val id: Long,
    val studentId: Long,
    val nis: String,
    val name: String,
    val className: String?,
    val date: String,
    val timeIn: String?,
    val status: String,
    val lateMinutes: Int,
    val notes: String,
    val createdBy: String
)

data class LeaveRow(
    val id: Long,
    val studentId: Long,
    val nis: String,
    val studentName: String,
    val className: String?,
    val type: String,
    val startDate: String,
    val endDate: String,
    val reason: String,
    val status: String,
    val rejectionNote: String?,
    val createdAt: Long
)

data class BillRow(
    val id: Long,
    val billCode: String,
    val studentId: Long,
    val nis: String,
    val studentName: String,
    val className: String?,
    val title: String,
    val period: String,
    val baseAmount: Long,
    val uniqueCode: Int,
    val totalAmount: Long,
    val status: String,
    val dueDate: String?
)

data class SavingRow(
    val id: Long,
    val studentId: Long,
    val nis: String,
    val studentName: String,
    val className: String?,
    val type: String,
    val amount: Long,
    val balanceAfter: Long,
    val note: String,
    val transactionDate: String,
    val teacherName: String?
)

data class QueueRow(
    val id: Long,
    val studentId: Long,
    val nis: String,
    val studentName: String,
    val className: String?,
    val queueNumber: Int,
    val status: String,
    val callCount: Int,
    val updatedAt: Long
)


