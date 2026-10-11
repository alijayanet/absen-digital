package com.alijaya.smartschool.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.SchoolClass
import com.alijaya.smartschool.tv.data.StudentRow
import com.alijaya.smartschool.tv.util.Qr
import kotlinx.coroutines.launch

@Composable
fun IdCardScreen() {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()

    var classes by remember { mutableStateOf<List<SchoolClass>>(emptyList()) }
    var selectedClassId by remember { mutableStateOf<Long?>(null) }
    var studentList by remember { mutableStateOf<List<StudentRow>>(emptyList()) }
    var selectedStudent by remember { mutableStateOf<StudentRow?>(null) }
    var schoolName by remember { mutableStateOf("Smart School") }
    var schoolAddress by remember { mutableStateOf("") }
    var waResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        classes = repo.db.classes().all()
        val s = repo.settings()
        schoolName = s["school_name"].orEmpty().ifBlank { "Smart School" }
        schoolAddress = s["school_address"].orEmpty()
    }

    LaunchedEffect(selectedClassId) {
        studentList = repo.db.students().rows(selectedClassId, "")
        if (selectedStudent == null || studentList.none { it.id == selectedStudent?.id }) {
            selectedStudent = studentList.firstOrNull()
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Kolom Kiri: Pemilih Siswa
        Column(Modifier.weight(1f)) {
            ScreenHeader("Daftar Siswa (${studentList.size})")
            Picker(
                "Filter Kelas",
                listOf<Pair<Long?, String>>(null to "Semua Kelas") + classes.map { it.id to it.name },
                selectedClassId,
                { selectedClassId = it },
                Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(studentList, key = { it.id }) { s ->
                    val isSel = s.id == selectedStudent?.id
                    FocusCard(Modifier.fillMaxWidth(), selected = isSel, onClick = { selectedStudent = s; waResult = null }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("${s.nis} • ${s.className ?: "-"}", fontSize = 13.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        }

        // Kolom Kanan: Pratinjau Kartu Pelajar CR80 & Aksi
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenHeader("Pratinjau Kartu Pelajar CR80") {
                val s = selectedStudent
                if (s != null) {
                    Button(onClick = {
                        scope.launch {
                            val ok = repo.sendIdCardWa(s.id)
                            waResult = if (ok) "Kartu dikirim ke WhatsApp orang tua (${s.parentPhone})" else "Nomor WhatsApp belum valid"
                        }
                    }) { Text("Kirim ke WA Orang Tua") }
                }
            }

            waResult?.let {
                Text(it, color = if (it.startsWith("Kartu")) Color(0xFF4ADE80) else Color(0xFFFBBF24), fontSize = 14.sp)
            }

            val student = selectedStudent
            if (student != null) {
                // Kartu Pelajar Desain Standar CR80 Rasio 85.6mm x 54mm
                val qrBmp = remember(student.qrToken) {
                    Qr.bitmap(student.qrToken, 240).asImageBitmap()
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(Color(0xFF0F766E), Color(0xFF134E4A), Color(0xFF042F2E))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(2.dp, Color(0xFF2DD4BF), RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        // Header Kartu
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎓", fontSize = 20.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(schoolName.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(schoolAddress, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                            }
                            Text("KARTU PELAJAR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF99F6E4))
                        }

                        // Isi Kartu: Foto, Identitas, QR Code
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(80.dp)
                                    .background(Panel, RoundedCornerShape(10.dp))
                                    .border(1.5.dp, Color(0xFF2DD4BF), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(student.name.take(1).uppercase(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(Modifier.weight(1f)) {
                                Text(student.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.height(4.dp))
                                Text("NIS: ${student.nis}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                                Text("Kelas: ${student.className ?: "-"}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                                Text("RFID: ${student.rfidUid ?: "-"}", fontSize = 13.sp, color = Color(0xFF99F6E4))
                            }

                            // QR Code
                            Image(
                                bitmap = qrBmp,
                                contentDescription = "QR Absen",
                                modifier = Modifier
                                    .size(84.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            )
                        }

                        // Footer Kartu
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gunakan untuk Presensi Mandiri di Gerbang", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                            Text(student.qrToken, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2DD4BF))
                        }
                    }
                }
            } else {
                FocusCard(Modifier.fillMaxWidth().height(260.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Pilih siswa untuk melihat kartu pelajar", color = Muted)
                    }
                }
            }
        }
    }
}
