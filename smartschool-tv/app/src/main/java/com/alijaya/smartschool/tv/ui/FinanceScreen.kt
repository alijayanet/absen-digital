package com.alijaya.smartschool.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.BillRow
import com.alijaya.smartschool.tv.data.SchoolClass
import com.alijaya.smartschool.tv.data.User
import com.alijaya.smartschool.tv.util.Qr
import kotlinx.coroutines.launch

@Composable
fun FinanceScreen(user: User) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    val isAdmin = user.role == User.ROLE_ADMIN

    var classes by remember { mutableStateOf<List<SchoolClass>>(emptyList()) }
    var selectedClassId by remember { mutableStateOf<Long?>(null) }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var bills by remember { mutableStateOf<List<BillRow>>(emptyList()) }

    var selectedQrisBill by remember { mutableStateOf<BillRow?>(null) }
    var dynamicQrisCode by remember { mutableStateOf<String?>(null) }
    var qrisError by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            bills = repo.db.misc().billRows(selectedStatus, selectedClassId, q)
        }
    }

    LaunchedEffect(Unit) {
        classes = repo.db.classes().all()
        refresh()
    }

    LaunchedEffect(selectedStatus, selectedClassId, q) {
        refresh()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Keuangan & Tagihan SPP (${bills.size})") {
            if (isAdmin) {
                Button(onClick = {
                    scope.launch {
                        val currentMonth = com.alijaya.smartschool.tv.util.Clock.today().substring(0, 7)
                        val count = repo.generateMonthlyBills(currentMonth, 1L)
                        msg = "Berhasil membuat $count tagihan SPP untuk periode $currentMonth."
                        refresh()
                    }
                }) { Text("Generate SPP Bulan Ini") }
            }
        }

        msg?.let { Text(it, color = Teal, fontSize = 14.sp) }

        // Filter Bar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Picker(
                "Filter Status",
                listOf<Pair<String?, String>>(null to "Semua", "UNPAID" to "BELUM LUNAS", "PAID" to "LUNAS"),
                selectedStatus,
                { selectedStatus = it },
                Modifier.width(180.dp)
            )
            Picker(
                "Filter Kelas",
                listOf<Pair<Long?, String>>(null to "Semua Kelas") + classes.map { it.id to it.name },
                selectedClassId,
                { selectedClassId = it },
                Modifier.width(180.dp)
            )
            Field("Cari siswa/kode", q, { q = it }, Modifier.weight(1f))
        }

        // Header Tabel
        Row(Modifier.padding(vertical = 6.dp, horizontal = 12.dp)) {
            listOf(
                "Kode" to 140,
                "Siswa" to 220,
                "Kelas" to 90,
                "Tagihan" to 160,
                "Nominal" to 130,
                "Status" to 110,
                "Aksi" to 220
            ).forEach { (t, w) -> Cell(t, w, Muted) }
        }

        // List Tagihan
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(bills, key = { it.id }) { b ->
                FocusCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell(b.billCode, 140)
                        Cell(b.studentName, 220)
                        Cell(b.className ?: "-", 90)
                        Cell(b.title, 160)
                        Cell(rupiah(b.totalAmount), 130, Teal)
                        val isPaid = b.status == "PAID"
                        Cell(if (isPaid) "LUNAS" else "BELUM", 110, if (isPaid) Color(0xFF4ADE80) else Color(0xFFF87171))

                        Row(Modifier.width(220.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isPaid) {
                                Button(onClick = {
                                    scope.launch {
                                        repo.payBillCash(b.id, user.name)
                                        refresh()
                                    }
                                }) { Text("Bayar Tunai") }

                                OutlinedButton(onClick = {
                                    selectedQrisBill = b
                                    qrisError = null
                                    dynamicQrisCode = null
                                    scope.launch {
                                        try {
                                            dynamicQrisCode = repo.getDynamicQris(b.id)
                                        } catch (e: Exception) {
                                            qrisError = e.message ?: "Gagal memproses QRIS"
                                        }
                                    }
                                }) { Text("QRIS") }
                            } else {
                                Text("Terbayar", color = Color(0xFF4ADE80), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog QRIS Popup
    selectedQrisBill?.let { bill ->
        AlertDialog(
            onDismissRequest = { selectedQrisBill = null },
            title = { Text("QRIS Dinamis Tagihan") },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(bill.studentName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(bill.title, color = Muted)
                    Text(rupiah(bill.totalAmount), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Teal)

                    val err = qrisError
                    val qris = dynamicQrisCode
                    if (err != null) {
                        Text(err, color = Color(0xFFF87171), modifier = Modifier.padding(16.dp))
                    } else if (qris != null) {
                        val qrBmp = remember(qris) { Qr.bitmap(qris, 300).asImageBitmap() }
                        Image(
                            bitmap = qrBmp,
                            contentDescription = "QRIS Dinamis",
                            modifier = Modifier
                                .size(240.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        )
                        Text(
                            "Scan melalui BCA, GoPay, OVO, Dana, ShopeePay, atau mobile banking apapun",
                            fontSize = 11.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text("Menyiapkan QRIS dinamis...", color = Muted, modifier = Modifier.padding(16.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedQrisBill = null }) { Text("Tutup") }
            }
        )
    }
}
