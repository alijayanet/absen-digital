package com.alijaya.smartschool.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.DashboardStats
import com.alijaya.smartschool.tv.service.ServerService
import com.alijaya.smartschool.tv.util.Qr
import kotlinx.coroutines.delay

@Composable
fun ServerInfoCard(modifier: Modifier = Modifier) {
    val st by ServerService.state.collectAsState()
    val url = "http://${st.ip ?: "0.0.0.0"}:${st.port}"
    val qr = remember(url) { Qr.bitmap(url, 360).asImageBitmap() }
    FocusCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(qr, null, Modifier.size(140.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(6.dp))
            Spacer(Modifier.width(20.dp))
            Column {
                Text("Web Remote (Guru & Admin)", color = Muted)
                Text(url, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        st.running -> "● Server aktif — buka dari HP/laptop di WiFi yang sama"
                        st.error != null -> "● Gagal: ${st.error}"
                        else -> "● Menyalakan server..."
                    },
                    color = if (st.running) Color(0xFF4ADE80) else Color(0xFFFBBF24)
                )
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val repo = App.instance.repo
    val stats by produceState<DashboardStats?>(null) {
        while (true) { value = repo.stats(); delay(5000) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader("Beranda")
        ServerInfoCard()
        val s = stats ?: return@Column
        Text("Kehadiran hari ini (${s.date})", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Hadir", "${s.hadir}", Color(0xFF4ADE80), Modifier.weight(1f))
            StatTile("Terlambat", "${s.terlambat}", Color(0xFFFBBF24), Modifier.weight(1f))
            StatTile("Izin", "${s.izin}", Color(0xFF38BDF8), Modifier.weight(1f))
            StatTile("Sakit", "${s.sakit}", Color(0xFFA78BFA), Modifier.weight(1f))
            StatTile("Alpa", "${s.alpa}", Color(0xFFF87171), Modifier.weight(1f))
            StatTile("Belum Scan", "${s.belumScan}", Muted, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Siswa Aktif", "${s.totalStudents}", Color.White, Modifier.weight(1f))
            StatTile("Kelas", "${s.totalClasses}", Color.White, Modifier.weight(1f))
            StatTile("Guru", "${s.totalTeachers}", Color.White, Modifier.weight(1f))
            StatTile("Izin Menunggu", "${s.pendingLeaves}", Color(0xFFFBBF24), Modifier.weight(1f))
            StatTile("Tagihan Belum Lunas", "${s.unpaidBills}", Color(0xFFF87171), Modifier.weight(1f))
            StatTile("Total Tabungan", rupiah(s.totalSavings), Teal, Modifier.weight(1.4f))
        }
    }
}
