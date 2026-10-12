package com.alijaya.smartschool

import android.app.Application
import android.content.Context
import android.util.Log
import com.alijaya.smartschool.data.pref.SessionManager
import com.alijaya.smartschool.server.KioskHttpServer
import com.alijaya.smartschool.server.db.SmartSchoolDatabase
import com.alijaya.smartschool.server.telegram.SmartSchoolTelegramManager
import com.alijaya.smartschool.util.BeepTtsHelper
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmartSchoolApplication : Application() {

    companion object {
        private const val TAG = "SmartSchoolApp"

        lateinit var sessionManager: SessionManager
            private set

        @Volatile
        var httpServer: KioskHttpServer? = null
            private set

        @Volatile
        var telegramManager: SmartSchoolTelegramManager? = null
            private set

        @Volatile
        var globalAudioHelper: BeepTtsHelper? = null
            private set

        @Volatile
        var onQueueChangedListener: (() -> Unit)? = null

        fun notifyQueueChanged() {
            try {
                onQueueChangedListener?.invoke()
            } catch (e: Exception) {
                Log.w(TAG, "Error invoking onQueueChangedListener: ${e.message}")
            }
        }

        private val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        fun startHttpServer(context: Context, port: Int = 8080) {
            try {
                if (httpServer == null || !httpServer!!.isAlive) {
                    httpServer?.stop()
                    val server = KioskHttpServer(context.applicationContext, port)
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    httpServer = server
                    Log.i(TAG, "KioskHttpServer running on 0.0.0.0:$port")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start KioskHttpServer: ${e.message}", e)
            }
        }

        fun stopHttpServer() {
            try {
                httpServer?.stop()
                httpServer = null
                Log.i(TAG, "KioskHttpServer stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping KioskHttpServer: ${e.message}", e)
            }
        }

        fun restartHttpServer(context: Context, port: Int) {
            stopHttpServer()
            startHttpServer(context, port)
        }

        fun initTelegramManager(context: Context) {
            try {
                if (globalAudioHelper == null) {
                    globalAudioHelper = BeepTtsHelper(context.applicationContext)
                }
                val db = SmartSchoolDatabase.getInstance(context.applicationContext)
                if (telegramManager == null) {
                    telegramManager = SmartSchoolTelegramManager(
                        context.applicationContext,
                        db,
                        globalAudioHelper!!,
                        appScope
                    )
                } else {
                    appScope.launch(Dispatchers.IO) {
                        telegramManager?.reloadConfig()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init TelegramManager: ${e.message}", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(applicationContext)

        // Otomatis jalankan server mandiri lokal di STB jika mode Standalone
        if (sessionManager.isStandaloneMode()) {
            startHttpServer(this, sessionManager.getServerPort())
        }

        // Inisialisasi Bot Telegram Long-Polling
        initTelegramManager(this)
    }
}

