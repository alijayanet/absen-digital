package com.alijaya.smartschool.tv

import android.app.Application
import com.alijaya.smartschool.tv.data.AppDatabase
import com.alijaya.smartschool.tv.data.Repository
import com.alijaya.smartschool.tv.data.Seeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var repo: Repository
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = AppDatabase.build(this)
        repo = Repository(db)
        appScope.launch { Seeder.seedIfEmpty(db) }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
