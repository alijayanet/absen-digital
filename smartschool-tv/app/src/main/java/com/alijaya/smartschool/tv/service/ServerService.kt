package com.alijaya.smartschool.tv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.R
import com.alijaya.smartschool.tv.server.WebServer
import com.alijaya.smartschool.tv.ui.MainActivity
import com.alijaya.smartschool.tv.util.Net
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Foreground service: menjaga web server :8080 tetap hidup. */
class ServerService : Service() {

    private var server: WebServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var workerJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val app = App.instance
        if (server == null) {
            app.appScope.launch {
                val port = app.repo.setting("server_port").toIntOrNull() ?: DEFAULT_PORT
                try {
                    server = WebServer(applicationContext, app.repo, port).also { it.start() }
                    _state.value = ServerState(true, port, Net.localIp(), null)
                } catch (e: Exception) {
                    _state.value = ServerState(false, port, Net.localIp(), e.message)
                }
            }
        }
        if (workerJob == null || workerJob?.isCancelled == true) {
            workerJob = app.appScope.launch {
                startQueueAndScheduler(app)
            }
        }
        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartSchool:server").apply { acquire() }
        }
        return START_STICKY
    }

    private suspend fun startQueueAndScheduler(app: App) {
        var lastAutoRecapDate = ""
        while (true) {
            try {
                // 1. Proses antrian pesan WhatsApp
                val pending = app.repo.db.misc().nextPendingWa(5)
                if (pending.isNotEmpty()) {
                    val gateway = app.repo.getWhatsAppGateway()
                    pending.forEach { msg ->
                        app.repo.db.misc().updateWa(msg.copy(status = "sending", attempts = msg.attempts + 1))
                        val res = gateway.send(msg.phone, msg.message)
                        if (res.success) {
                            app.repo.db.misc().updateWa(msg.copy(status = "sent", sentAt = System.currentTimeMillis()))
                        } else {
                            val newAttempts = msg.attempts + 1
                            val newStatus = if (newAttempts >= 3) "failed" else "pending"
                            app.repo.db.misc().updateWa(msg.copy(status = newStatus, errorMessage = res.message, attempts = newAttempts))
                        }
                        kotlinx.coroutines.delay(1800) // Jeda throttling aman untuk anti-banned
                    }
                }

                // 2. Scheduler rekap otomatis ke wali kelas
                val today = com.alijaya.smartschool.tv.util.Clock.today()
                val nowTime = com.alijaya.smartschool.tv.util.Clock.nowTime().take(5) // HH:mm
                val targetRecapTime = app.repo.setting("jam_rekap_guru").take(5)

                if (targetRecapTime.isNotBlank() && nowTime >= targetRecapTime && lastAutoRecapDate != today) {
                    app.repo.sendTeacherRecap(today, null)
                    lastAutoRecapDate = today
                }
            } catch (_: Exception) {}

            kotlinx.coroutines.delay(10_000) // Periksa antrian tiap 10 detik
        }
    }

    override fun onDestroy() {
        workerJob?.cancel(); workerJob = null
        server?.stop(); server = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        _state.value = _state.value.copy(running = false)
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Server Smart School", NotificationManager.IMPORTANCE_LOW))
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = builder
            .setContentTitle("Smart School Server aktif")
            .setContentText("Web Remote: http://${Net.localIp() ?: "-"}:$DEFAULT_PORT")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, n)
    }

    data class ServerState(val running: Boolean = false, val port: Int = DEFAULT_PORT, val ip: String? = null, val error: String? = null)

    companion object {
        const val DEFAULT_PORT = 8080
        private const val CHANNEL = "server"
        private val _state = MutableStateFlow(ServerState())
        val state: StateFlow<ServerState> = _state

        fun start(ctx: Context) {
            val i = Intent(ctx, ServerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun restart(ctx: Context) {
            ctx.stopService(Intent(ctx, ServerService::class.java))
            start(ctx)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ServerService.start(context)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
