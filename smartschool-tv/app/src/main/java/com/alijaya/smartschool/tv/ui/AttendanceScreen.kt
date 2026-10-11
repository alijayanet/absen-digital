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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.AttendanceRow
import com.alijaya.smartschool.tv.data.SchoolClass
import com.alijaya.smartschool.tv.data.StudentRow
import com.alijaya.smartschool.tv.data.User
import com.alijaya.smartschool.tv.util.Clock
import kotlinx.coroutines.launch

@Composable
fun AttendanceScreen(user: User) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    val isAdmin = user.role == User.ROLE_ADMIN

    var date by remember { mutableStateOf(Clock.today()) }
    var classFilter by remember { mutableStateOf(if (isAdmin) null else user.classId) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }

    val classes = produceState(emptyList<SchoolClass>()) { value = repo.db.classes().all() }.value
    val rows by remember(date, classFilter, statusFilter, q) {
        repo.db.attendances().observeRows(date, classFilter, statusFilter, q)
    }.collectAsState(emptyList())

    var showManualDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AttendanceRow?>(null) }

    Column {
        ScreenHeader("Rekap Kehadiran (${rows.size})") {
            Button(onClick = { showManualDialog = true }) { Text("+ Absen Manual") }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Field("Tanggal (yyyy-MM-dd)", date, { date = it }, Modifier.width(170.dp))
            Field("Cari nama/NIS", q, { q = it }, Modifier.width(200.dp))
            Picker(
                "Kelas",
                listOf<Pair<Long?, String>>(null to "Semua") + classes.map { it.id to it.name },
                classFilter,
                { classFilter = it },
                Modifier.width(190.dp)
            )
            Picker(
                "Status",
                listOf<Pair<String?, String>>(
                    null to "Semua",
                    "HADIR" to "HADIR",
                    "TERLAMBAT" to "TERLAMBAT",
                    "IZIN" to "IZIN",
                    "SAKIT" to "SAKIT",
                    "ALPA" to "ALPA"
                ),
                statusFilter,
                { statusFilter = it },
                Modifier.width(170.dp)
            )
        }

        Row(Modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
            listOf(
                "NIS" to 110,
                "Nama" to 220,
                "Kelas" to 100,
                "Status" to 110,
                "Jam Masuk" to 110,
                "Terlambat" to 110,
                "Catatan" to 180,
                "Dicatat" to 100
            ).forEach { (t, w) -> Cell(t, w, Muted) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = { it.id }) { a ->
                FocusCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell(a.nis, 110)
                        Cell(a.name, 220)
                        Cell(a.className ?: "-", 100)
                        val stColor = when (a.status) {
                            "HADIR" -> Color(0xFF4ADE80)
                            "TERLAMBAT" -> Color(0xFFFBBF24)
                            "IZIN" -> Color(0xFF38BDF8)
                            "SAKIT" -> Color(0xFFA78BFA)
                            else -> Color(0xFFF87171)
                        }
                        Cell(a.status, 110, stColor)
                        Cell(a.timeIn ?: "-", 110)
                        Cell(if (a.lateMinutes > 0) "${a.lateMinutes} mnt" else "-", 110)
                        Cell(a.notes.ifBlank { "-" }, 180, Muted)
                        Cell(a.createdBy, 100, Muted)
                        if (isAdmin) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { deleting = a }) { Text("Hapus", color = Color(0xFFF87171)) }
                        }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        ManualAttendanceDialog(
            classes = classes,
            date = date,
            userName = user.name,
            onDismiss = { showManualDialog = false },
            onSaved = { showManualDialog = false }
        )
    }

    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus rekaman absensi?") },
            text = { Text("Hapus data absen ${d.name} pada tanggal ${d.date}?") },
            confirmButton = {
                DangerButton("Hapus") {
                    scope.launch {
                        repo.deleteAttendance(d.id)
                        deleting = null
                    }
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } }
        )
    }
}

@Composable
fun ManualAttendanceDialog(
    classes: List<SchoolClass>,
    date: String,
    userName: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()

    var studentList by remember { mutableStateOf<List<StudentRow>>(emptyList()) }
    var selectedStudentId by remember { mutableStateOf<Long?>(null) }
    var selectedClassId by remember { mutableStateOf<Long?>(classes.firstOrNull()?.id) }
    var status by remember { mutableStateOf("HADIR") }
    var notes by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    val statusOpts = listOf(
        "HADIR" to "Hadir",
        "TERLAMBAT" to "Terlambat",
        "IZIN" to "Izin",
        "SAKIT" to "Sakit",
        "ALPA" to "Alpa"
    )

    androidx.compose.runtime.LaunchedEffect(selectedClassId) {
        studentList = repo.db.students().rows(selectedClassId, "")
        if (studentList.isNotEmpty()) {
            selectedStudentId = studentList.first().id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input Absensi Manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    "Pilih Kelas",
                    classes.map { it.id to it.name },
                    selectedClassId,
                    { selectedClassId = it },
                    Modifier.fillMaxWidth()
                )
                Picker(
                    "Pilih Siswa",
                    studentList.map { it.id to "${it.nis} - ${it.name}" },
                    selectedStudentId,
                    { selectedStudentId = it },
                    Modifier.fillMaxWidth()
                )
                Picker("Status Kehadiran", statusOpts, status, { status = it }, Modifier.fillMaxWidth())
                Field("Catatan (opsional)", notes, { notes = it })
                err?.let { Text(it, color = Color(0xFFF87171)) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val sId = selectedStudentId
                if (sId == null) {
                    err = "Pilih siswa terlebih dahulu"
                    return@Button
                }
                scope.launch {
                    try {
                        repo.saveManualAttendance(sId, date, status, notes, userName)
                        onSaved()
                    } catch (e: Exception) {
                        err = e.message ?: "Gagal simpan"
                    }
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
