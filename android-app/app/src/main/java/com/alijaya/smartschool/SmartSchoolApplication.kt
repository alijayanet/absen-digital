package com.alijaya.smartschool

import android.app.Application
import android.content.Context
import android.util.Log
import com.alijaya.smartschool.data.pref.SessionManager
import com.alijaya.smartschool.server.KioskHttpServer
import fi.iki.elonen.NanoHTTPD

class SmartSchoolApplication : Application() {

    companion object {
        private const val TAG = "SmartSchoolApp"

        lateinit var sessionManager: SessionManager
            private set

        @Volatile
        var httpServer: KioskHttpServer? = null
            private set

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
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(applicationContext)

        // Otomatis jalankan server mandiri lokal di STB jika mode Standalone
        if (sessionManager.isStandaloneMode()) {
            startHttpServer(this, sessionManager.getServerPort())
        }
    }
}
