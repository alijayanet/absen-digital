package com.alijaya.smartschool.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.WaMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WhatsAppScreen() {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()

    var testPhone by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var queueList by remember { mutableStateOf<List<WaMessage>>(emptyList()) }
    var statusInfo by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            queueList = repo.db.misc().recentWaMessages(30)
            val pending = repo.db.misc().pendingWa()
            val prov = repo.setting("wa_provider")
            statusInfo = "Gateway: ${prov.uppercase()} • Antrian pending: $pending pesan"
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(5000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("WhatsApp Gateway & Antrian") {
            Button(onClick = {
                scope.launch {
                    val count = repo.broadcastAlpa()
                    testResult = "Broadcast ALPA berhasil dibuat: $count pesan masuk antrian."
                    refresh()
                }
            }) { Text("Broadcast ALPA Hari Ini") }

            Spacer(Modifier.width(8.dp))

            Button(onClick = {
                scope.launch {
                    val count = repo.sendTeacherRecap()
                    testResult = "Rekap dikirim ke $count wali kelas."
                    refresh()
                }
            }) { Text("Kirim Rekap Wali Kelas") }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(onClick = {
                scope.launch {
                    repo.retryWaQueue()
                    testResult = "Pesan gagal diatur ulang ke antrian."
                    refresh()
                }
            }) { Text("Retry Antrian Gagal") }
        }

        // Status Card & Test Send
        FocusCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(statusInfo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Teal)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Field(
                        "Nomor WA Penerima (contoh: 08123456789)",
                        testPhone,
                        { testPhone = it },
                        Modifier.weight(1f),
                        number = true
                    )
                    Button(onClick = {
                        val ph = testPhone.trim()
                        if (ph.isBlank()) {
                            testResult = "Masukkan nomor HP tujuan"
                            return@Button
                        }
                        scope.launch {
                            testResult = "Sedang mengirim..."
                            val res = repo.testWhatsApp(ph)
                            testResult = if (res.success) "Sukses: ${res.message}" else "Gagal: ${res.message}"
                            refresh()
                        }
                    }) { Text("Kirim Pesan Tes") }
                }
                testResult?.let {
                    Text(it, color = if (it.startsWith("Sukses")) Color(0xFF4ADE80) else Color(0xFFFBBF24))
                }
            }
        }

        // Tabel Antrian Terkini
        Text("Daftar Antrian & Riwayat Terkini (${queueList.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(Modifier.padding(vertical = 6.dp, horizontal = 12.dp)) {
            listOf("Tujuan" to 150, "Tipe" to 140, "Status" to 110, "Percobaan" to 110, "Pesan" to 340).forEach { (t, w) ->
                Cell(t, w, Muted)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(queueList, key = { it.id }) { q ->
                FocusCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell(q.phone, 150)
                        Cell(q.type, 140, Teal)
                        val stColor = when (q.status) {
                            "sent" -> Color(0xFF4ADE80)
                            "sending" -> Color(0xFF38BDF8)
                            "failed" -> Color(0xFFF87171)
                            else -> Color(0xFFFBBF24)
                        }
                        Cell(q.status.uppercase(), 110, stColor)
                        Cell("${q.attempts}x", 110, Muted)
                        Cell(q.message.replace("\n", " "), 340)
                    }
                }
            }
        }
    }
}
