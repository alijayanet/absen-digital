package com.alijaya.smartschool.tv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Setting::class, SchoolClass::class, User::class, Student::class, Attendance::class,
        WaMessage::class, LeaveRequest::class, FeeCategory::class, StudentBill::class,
        PaymentReceipt::class, PaymentLog::class, SavingTx::class, DailyQueue::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settings(): SettingDao
    abstract fun classes(): ClassDao
    abstract fun users(): UserDao
    abstract fun students(): StudentDao
    abstract fun attendances(): AttendanceDao
    abstract fun misc(): MiscDao

    companion object {
        const val NAME = "smartschool.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
