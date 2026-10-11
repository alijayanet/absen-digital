package com.alijaya.smartschool.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.LeaveRow
import com.alijaya.smartschool.tv.data.SchoolClass
import com.alijaya.smartschool.tv.data.StudentRow
import com.alijaya.smartschool.tv.data.User
import com.alijaya.smartschool.tv.util.Clock
import kotlinx.coroutines.launch

@Composable
fun LeavesScreen(user: User) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    var leaves by remember { mutableStateOf<List<LeaveRow>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch { leaves = repo.db.misc().leaveRows() }
    }

    LaunchedEffect(Unit) { refresh() }

    Column {
        ScreenHeader("Pengajuan Izin & Sakit (${leaves.size})") {
            Button(onClick = { showAddDialog = true }) { Text("+ Ajukan Izin") }
        }

        Row(Modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
            listOf(
                "Siswa" to 220,
                "Kelas" to 100,
                "Tipe" to 90,
                "Mulai" to 110,
                "Selesai" to 110,
                "Alasan" to 240,
                "Status" to 110
            ).forEach { (t, w) -> Cell(t, w, Muted) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(leaves, key = { it.id }) { l ->
                FocusCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell("${l.nis} - ${l.studentName}", 220)
                        Cell(l.className ?: "-", 100)
                        Cell(l.type, 90, if (l.type == "SAKIT") Color(0xFFA78BFA) else Color(0xFF38BDF8))
                        Cell(l.startDate, 110)
                        Cell(l.endDate, 110)
                        Cell(l.reason, 240)
                        val stColor = when (l.status) {
                            "APPROVED" -> Color(0xFF4ADE80)
                            "REJECTED" -> Color(0xFFF87171)
                            else -> Color(0xFFFBBF24)
                        }
                        Cell(l.status, 110, stColor)
                        if (l.status == "PENDING") {
                            Spacer(Modifier.weight(1f))
                            Button(onClick = {
                                scope.launch {
                                    repo.approveLeave(l.id, user.id, true, null)
                                    refresh()
                                }
                            }) { Text("Setujui") }
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            TextButton(onClick = {
                                scope.launch {
                                    repo.approveLeave(l.id, user.id, false, "Ditolak oleh admin/guru")
                                    refresh()
                                }
                            }) { Text("Tolak", color = Color(0xFFF87171)) }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddLeaveDialog(
            onDismiss = { showAddDialog = false },
            onSaved = {
                showAddDialog = false
                refresh()
            }
        )
    }
}

@Composable
fun AddLeaveDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()

    var classes by remember { mutableStateOf<List<SchoolClass>>(emptyList()) }
    var selectedClassId by remember { mutableStateOf<Long?>(null) }
    var studentList by remember { mutableStateOf<List<StudentRow>>(emptyList()) }
    var selectedStudentId by remember { mutableStateOf<Long?>(null) }

    var type by remember { mutableStateOf("IZIN") }
    var startDate by remember { mutableStateOf(Clock.today()) }
    var endDate by remember { mutableStateOf(Clock.today()) }
    var reason by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        classes = repo.db.classes().all()
        selectedClassId = classes.firstOrNull()?.id
    }

    LaunchedEffect(selectedClassId) {
        studentList = repo.db.students().rows(selectedClassId, "")
        selectedStudentId = studentList.firstOrNull()?.id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input Permohonan Izin / Sakit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker("Pilih Kelas", classes.map { it.id to it.name }, selectedClassId, { selectedClassId = it }, Modifier.fillMaxWidth())
                Picker("Pilih Siswa", studentList.map { it.id to "${it.nis} - ${it.name}" }, selectedStudentId, { selectedStudentId = it }, Modifier.fillMaxWidth())
                Picker("Tipe", listOf("IZIN" to "Izin", "SAKIT" to "Sakit"), type, { type = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Tanggal Mulai", startDate, { startDate = it }, Modifier.weight(1f))
                    Field("Tanggal Selesai", endDate, { endDate = it }, Modifier.weight(1f))
                }
                Field("Alasan Izin / Sakit", reason, { reason = it })
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
                        repo.saveLeave(sId, type, startDate, endDate, reason)
                        onSaved()
                    } catch (e: Exception) {
                        err = e.message ?: "Gagal simpan izin"
                    }
                }
            }) { Text("Ajukan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
