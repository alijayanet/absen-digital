package com.alijaya.smartschool.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.SavingRow
import com.alijaya.smartschool.tv.data.SchoolClass
import com.alijaya.smartschool.tv.data.StudentRow
import com.alijaya.smartschool.tv.data.User
import kotlinx.coroutines.launch

@Composable
fun SavingsScreen(user: User) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()

    var classes by remember { mutableStateOf<List<SchoolClass>>(emptyList()) }
    var selectedClassId by remember { mutableStateOf<Long?>(null) }
    var students by remember { mutableStateOf<List<StudentRow>>(emptyList()) }
    var selectedStudent by remember { mutableStateOf<StudentRow?>(null) }
    var history by remember { mutableStateOf<List<SavingRow>>(emptyList()) }

    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var txAmount by remember { mutableStateOf("") }
    var txNote by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun refreshStudents() {
        scope.launch {
            students = repo.db.students().rows(selectedClassId, "")
            if (selectedStudent != null) {
                selectedStudent = students.firstOrNull { it.id == selectedStudent?.id }
            }
        }
    }

    fun refreshHistory() {
        scope.launch {
            history = repo.db.misc().savingRows(selectedStudent?.id, selectedClassId)
        }
    }

    LaunchedEffect(Unit) {
        classes = repo.db.classes().all()
        refreshStudents()
        refreshHistory()
    }

    LaunchedEffect(selectedClassId) {
        refreshStudents()
        refreshHistory()
    }

    LaunchedEffect(selectedStudent) {
        refreshHistory()
    }

    Column {
        ScreenHeader("Tabungan Siswa")

        // Filter Bar & Student Selector
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FocusCard(selected = selectedClassId == null, onClick = { selectedClassId = null; selectedStudent = null }) {
                Text("Semua Kelas", fontSize = 13.sp)
            }
            classes.forEach { c ->
                FocusCard(selected = selectedClassId == c.id, onClick = { selectedClassId = c.id; selectedStudent = null }) {
                    Text(c.name, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            if (selectedStudent != null) {
                Button(onClick = {
                    txAmount = ""
                    txNote = ""
                    err = null
                    showDepositDialog = true
                }) {
                    Text("+ Setor Tunai")
                }

                OutlinedButton(onClick = {
                    txAmount = ""
                    txNote = ""
                    err = null
                    showWithdrawDialog = true
                }) {
                    Text("- Tarik Tunai")
                }
            }
        }

        msg?.let {
            Text(it, color = Teal, modifier = Modifier.padding(bottom = 8.dp), fontWeight = FontWeight.Bold)
        }

        // 2 Columns: Student List with Saldo (Left) & Transaction History (Right)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Student List
            FocusCard(Modifier.weight(1.1f)) {
                Column {
                    Text(
                        "Daftar Siswa & Saldo (${students.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(Modifier.height(480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(students) { s ->
                            val isSel = selectedStudent?.id == s.id
                            FocusCard(
                                Modifier.fillMaxWidth(),
                                selected = isSel,
                                onClick = { selectedStudent = s }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(s.name, fontWeight = FontWeight.Bold)
                                        Text("${s.nis} • ${s.className ?: "-"}", color = Muted, fontSize = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "Rp ${s.savingsBalance}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (s.savingsBalance > 0) Teal else Color.White
                                        )
                                        Text("Saldo", color = Muted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // History Column
            FocusCard(Modifier.weight(1.4f)) {
                Column {
                    val subtitle = if (selectedStudent != null) {
                        "Riwayat Transaksi: ${selectedStudent?.name}"
                    } else {
                        "Riwayat Transaksi Terbaru"
                    }
                    Text(subtitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))

                    if (history.isEmpty()) {
                        Box(Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Belum ada riwayat transaksi", color = Muted)
                        }
                    } else {
                        LazyColumn(Modifier.height(480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(history) { tx ->
                                val isDeposit = tx.type == "SETOR"
                                FocusCard(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    Modifier.background(
                                                        if (isDeposit) Color(0xFF065F46) else Color(0xFF7F1D1D),
                                                        RoundedCornerShape(4.dp)
                                                    ).padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        tx.type,
                                                        fontSize = 11.sp,
                                                        color = if (isDeposit) Color(0xFF6EE7B7) else Color(0xFFFCA5A5)
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Text(tx.studentName, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                "${tx.transactionDate} • ${tx.note.ifBlank { "-" }} • Petugas: ${tx.teacherName ?: "Admin"}",
                                                color = Muted,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "${if (isDeposit) "+" else "-"} Rp ${tx.amount}",
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDeposit) Teal else Color(0xFFF87171)
                                            )
                                            Text("Sisa: Rp ${tx.balanceAfter}", color = Muted, fontSize = 11.sp)
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

    // Modal Setor Tunai
    if (showDepositDialog && selectedStudent != null) {
        val s = selectedStudent!!
        AlertDialog(
            onDismissRequest = { showDepositDialog = false },
            title = { Text("Setor Tabungan Siswa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Siswa: ${s.name} (${s.className ?: "-"})")
                    Text("Saldo Saat Ini: Rp ${s.savingsBalance}", color = Teal, fontWeight = FontWeight.Bold)
                    Field("Nominal Setoran (Rp)", txAmount, { txAmount = it })
                    Field("Catatan (Opsional)", txNote, { txNote = it })
                    err?.let { Text(it, color = Color(0xFFF87171), fontSize = 13.sp) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = txAmount.toLongOrNull() ?: 0L
                    if (amt <= 0) {
                        err = "Nominal setoran tidak valid"
                        return@Button
                    }
                    scope.launch {
                        try {
                            repo.depositSavings(s.id, amt, txNote, user.id)
                            msg = "Setoran Rp $amt untuk ${s.name} berhasil disimpan & WA dikirim"
                            showDepositDialog = false
                            refreshStudents()
                            refreshHistory()
                        } catch (e: Exception) {
                            err = e.message
                        }
                    }
                }) { Text("Konfirmasi Setor") }
            },
            dismissButton = {
                TextButton(onClick = { showDepositDialog = false }) { Text("Batal") }
            }
        )
    }

    // Modal Tarik Tunai
    if (showWithdrawDialog && selectedStudent != null) {
        val s = selectedStudent!!
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title = { Text("Tarik Saldo Tabungan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Siswa: ${s.name} (${s.className ?: "-"})")
                    Text("Saldo Tersedia: Rp ${s.savingsBalance}", color = Teal, fontWeight = FontWeight.Bold)
                    Field("Nominal Penarikan (Rp)", txAmount, { txAmount = it })
                    Field("Keperluan / Catatan", txNote, { txNote = it })
                    err?.let { Text(it, color = Color(0xFFF87171), fontSize = 13.sp) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = txAmount.toLongOrNull() ?: 0L
                    if (amt <= 0) {
                        err = "Nominal penarikan tidak valid"
                        return@Button
                    }
                    if (amt > s.savingsBalance) {
                        err = "Saldo tidak mencukupi (Maks Rp ${s.savingsBalance})"
                        return@Button
                    }
                    scope.launch {
                        try {
                            repo.withdrawSavings(s.id, amt, txNote, user.id)
                            msg = "Penarikan Rp $amt untuk ${s.name} berhasil & WA dikirim"
                            showWithdrawDialog = false
                            refreshStudents()
                            refreshHistory()
                        } catch (e: Exception) {
                            err = e.message
                        }
                    }
                }) { Text("Konfirmasi Tarik") }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = false }) { Text("Batal") }
            }
        )
    }
}
