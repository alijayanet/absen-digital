package com.alijaya.smartschool.data.model

import com.google.gson.annotations.SerializedName

data class DisplayResponse(
    val success: Boolean,
    val date: String? = null,
    val schoolName: String? = null,
    @SerializedName("school_name") val schoolNameAlt: String? = null,
    val schoolLogo: String? = null,
    @SerializedName("school_logo") val schoolLogoAlt: String? = null,
    val schoolTagline: String? = null,
    @SerializedName("school_tagline") val schoolTaglineAlt: String? = null,
    val announcement: String? = null,
    val tables: List<QueueTable>? = null,
    val lastCall: QueueCallItem? = null,
    val prayer: PrayerData? = null
)

data class QueueTable(
    val classId: Int? = null,
    val className: String? = null,
    val teacherId: Int? = null,
    val teacherName: String? = null,
    val tableName: String? = null,
    val current: QueueCurrentItem? = null,
    val nextWaiting: List<QueueWaitingItem>? = null,
    val stats: QueueStats? = null
)

data class QueueCurrentItem(
    @SerializedName("queue_number") val queueNumber: Int,
    @SerializedName("student_name") val studentName: String? = null,
    val nis: String? = null,
    val photo: String? = null
)

data class QueueWaitingItem(
    @SerializedName("queue_number") val queueNumber: Int,
    @SerializedName("student_name") val studentName: String? = null,
    val nis: String? = null
)

data class QueueStats(
    val total: Int = 0,
    val waiting: Int = 0,
    val serving: Int = 0,
    val completed: Int = 0,
    val skipped: Int = 0
)

data class QueueCallItem(
    val id: Int? = null,
    @SerializedName("queue_number") val queueNumber: Int,
    @SerializedName("call_count") val callCount: Int = 1,
    @SerializedName("called_at") val calledAt: String? = null,
    @SerializedName("student_name") val studentName: String? = null,
    @SerializedName("class_name") val className: String? = null,
    @SerializedName("teacher_name") val teacherName: String? = null,
    @SerializedName("teacher_id") val teacherId: Int? = null,
    val gender: String? = null,
    val speechSentence: String? = null
)

data class PrayerData(
    val schedule: PrayerSchedule? = null,
    val nextInfo: PrayerNextInfo? = null
)

data class PrayerSchedule(
    val date: String? = null,
    val imsak: String? = null,
    val subuh: String? = null,
    val terbit: String? = null,
    val dzuhur: String? = null,
    val ashar: String? = null,
    val maghrib: String? = null,
    val isya: String? = null,
    val timezoneName: String? = null
)

data class PrayerNextInfo(
    val next: PrayerTimeItem? = null,
    val active: PrayerTimeItem? = null,
    val formattedCountdown: String? = null
)

data class PrayerTimeItem(
    val key: String? = null,
    val label: String? = null,
    val time: String? = null
)

// Absensi Scan Models
data class ScanResponse(
    val success: Boolean,
    val code: String? = null,
    val message: String? = null,
    val student: StudentScanInfo? = null,
    val attendance: AttendanceInfo? = null,
    val queue: QueueAssignedInfo? = null
)

data class StudentScanInfo(
    val id: Int? = null,
    val name: String? = null,
    val nis: String? = null,
    val gender: String? = null,
    @SerializedName("class_name") val className: String? = null,
    @SerializedName("teacher_name") val teacherName: String? = null,
    val photo: String? = null
)

data class AttendanceInfo(
    val status: String? = null,
    @SerializedName("time_in") val timeIn: String? = null
)

data class QueueAssignedInfo(
    @SerializedName("queue_number") val queueNumber: Int? = null,
    @SerializedName("class_name") val className: String? = null,
    @SerializedName("teacher_name") val teacherName: String? = null
)
