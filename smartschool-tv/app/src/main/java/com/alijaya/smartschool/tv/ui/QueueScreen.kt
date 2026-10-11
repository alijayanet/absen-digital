package com.alijaya.smartschool.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.QueueRow
import com.alijaya.smartschool.tv.data.SchoolClass
import com.alijaya.smartschool.tv.data.StudentRow
import com.alijaya.smartschool.tv.data.User
import com.alijaya.smartschool.tv.util.BeepHelper
import com.alijaya.smartschool.tv.util.Clock
import com.alijaya.smartschool.tv.util.PrayerSchedule
import com.alijaya.smartschool.tv.util.PrayerUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(user: User) {
    val context = LocalContext.current
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    val isTeacher = user.role == User.ROLE_GURU || user.role == User.ROLE_ADMIN

    val beep = remember { BeepHelper(context) }
    DisposableEffect(Unit) {
        onDispose { beep.release() }
    }

    var currentTime by remember { mutableStateOf(Clock.nowTime()) }
    var prayer by remember { mutableStateOf<PrayerSchedule>(PrayerUtil.today()) }
    var queueList by remember { mutableStateOf<List<QueueRow>>(emptyList()) }
    var callingItem by remember { mutableStateOf<QueueRow?>(null) }
    var lastSpokenQueueId by remember { mutableStateOf<Long?>(null) }

    // State untuk Meja Guru (Pendaftaran & Panggil)
    var classes by remember { mutableStateOf<List<SchoolClass>>(emptyList()) }
    var selectedClassId by remember { mutableStateOf<Long?>(null) }
    var students by remember { mutableStateOf<List<StudentRow>>(emptyList()) }
    var selectedStudentId by remember { mutableStateOf<Long?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun refreshData() {
        scope.launch {
            val today = Clock.today()
            queueList = repo.db.misc().queueRows(today)
            callingItem = repo.db.misc().currentCallingQueue(today)

            // TTS Panggilan Otomatis saat ada antrian berstatus CALLING baru
            val c = callingItem
            if (c != null && c.id != lastSpokenQueueId) {
                lastSpokenQueueId = c.id
                beep.speak("Nomor antrian ${c.queueNumber}, santri ${c.studentName}, silakan maju ke meja setoran")
            }
        }
    }

    // Polling antrian & jam setiap 2 detik
    LaunchedEffect(Unit) {
        classes = repo.db.classes().all()
        while (true) {
            currentTime = Clock.nowTime()
            prayer = repo.todayPrayerSchedule()
            refreshData()
            delay(2000)
        }
    }

    LaunchedEffect(selectedClassId) {
        students = repo.db.students().rows(selectedClassId, "")
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Antrian Setoran Al-Qur'an & Display TV")

        // Top Banner: Jadwal Sholat Kemenag & Waktu Saat Ini
        FocusCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mosque, null, tint = Teal, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Waktu Sholat Hari Ini (Hisab Kemenag)", fontWeight = FontWeight.Bold)
                        Text(
                            "Subuh ${prayer.subuh} • Dzuhur ${prayer.dzuhur} • Ashar ${prayer.ashar} • Maghrib ${prayer.maghrib} • Isya ${prayer.isya}",
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.background(Color(0xFF0F766E), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Menuju: ${prayer.nextPrayerName} (${prayer.nextPrayerTime})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(currentTime, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Teal)
                }
            }
        }

        msg?.let {
            Text(it, color = Teal, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        }

        // Layout Utama: Display Panggilan TV (Kiri) & Meja Guru / Daftar Antrian (Kanan)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Kolom Kiri: Display Panggilan TV Utama (Landscape Big Screen)
            FocusCard(Modifier.weight(1.2f)) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("SEDANG DIPANGGIL", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))

                    val active = callingItem
                    if (active != null) {
                        Box(
                            Modifier.size(160.dp).background(Color(0xFF042F2E), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "%03d".format(active.queueNumber),
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Bold,
                                color = Teal
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(active.studentName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Kelas: ${active.className ?: "-"} • NIS: ${active.nis}",
                            color = Muted,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    beep.speak("Panggilan ulang. Nomor antrian ${active.queueNumber}, santri ${active.studentName}")
                                    repo.recallQueue(active.id)
                                    refreshData()
                                }
                            }) {
                                Icon(Icons.Default.Campaign, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Panggil Ulang")
                            }

                            Button(onClick = {
                                scope.launch {
                                    repo.completeQueue(active.id)
                                    refreshData()
                                }
                            }) { Text("Selesai") }

                            OutlinedButton(onClick = {
                                scope.launch {
                                    repo.skipQueue(active.id)
                                    refreshData()
                                }
                            }) { Text("Lewati") }
                        }
                    } else {
                        Box(
                            Modifier.size(160.dp).background(PanelHi, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("--", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Muted)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Belum Ada Santri Dipanggil", fontSize = 20.sp, color = Muted)
                        Spacer(Modifier.height(14.dp))

                        if (isTeacher) {
                            Button(onClick = {
                                scope.launch {
                                    val called = repo.callNextQueue()
                                    if (called == null) msg = "Tidak ada santri yang sedang menunggu antrian"
                                    refreshData()
                                }
                            }) {
                                Text("Panggil Antrian Berikutnya ▶")
                            }
                        }
                    }
                }
            }

            // Kolom Kanan: Meja Pengampu & Daftar Antrian Hari Ini
            FocusCard(Modifier.weight(1.3f)) {
                Column {
                    if (isTeacher) {
                        Text("Daftarkan Santri ke Antrian", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            classes.take(4).forEach { c ->
                                FocusCard(
                                    selected = selectedClassId == c.id,
                                    onClick = { selectedClassId = c.id; selectedStudentId = null }
                                ) {
                                    Text(c.name, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Picker(
                                label = "Pilih Santri",
                                options = students.map { it.id to "${it.name} (${it.nis})" },
                                selected = selectedStudentId ?: 0L,
                                onSelect = { selectedStudentId = it },
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                val sid = selectedStudentId
                                if (sid == null || sid <= 0L) {
                                    msg = "Pilih santri terlebih dahulu"
                                    return@Button
                                }
                                scope.launch {
                                    val q = repo.registerQueue(sid)
                                    msg = "Santri masuk antrian nomor ${q.queueNumber}"
                                    refreshData()
                                }
                            }) {
                                Text("+ Ambil Nomor")
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Text("Daftar Antrian Hari Ini (${queueList.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))

                    if (queueList.isEmpty()) {
                        Box(Modifier.height(180.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Belum ada antrian yang terdaftar hari ini", color = Muted)
                        }
                    } else {
                        LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(queueList) { q ->
                                val statusColor = when (q.status) {
                                    "CALLING" -> Color(0xFFF59E0B)
                                    "SERVING" -> Teal
                                    "COMPLETED" -> Color(0xFF10B981)
                                    "SKIPPED" -> Color(0xFFEF4444)
                                    else -> Color.White
                                }
                                FocusCard(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "#%02d".format(q.queueNumber),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = Teal
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(q.studentName, fontWeight = FontWeight.Bold)
                                                Text(q.className ?: "-", color = Muted, fontSize = 12.sp)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(q.status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            if (isTeacher && (q.status == "WAITING" || q.status == "SKIPPED")) {
                                                Spacer(Modifier.width(8.dp))
                                                OutlinedButton(onClick = {
                                                    scope.launch {
                                                        repo.recallQueue(q.id)
                                                        refreshData()
                                                    }
                                                }) { Text("Panggil", fontSize = 11.sp) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
