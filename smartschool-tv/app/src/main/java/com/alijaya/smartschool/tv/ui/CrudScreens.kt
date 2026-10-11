package com.alijaya.smartschool.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.ClassRow
import com.alijaya.smartschool.tv.data.Repository
import com.alijaya.smartschool.tv.data.SchoolClass
import com.alijaya.smartschool.tv.data.Student
import com.alijaya.smartschool.tv.data.StudentRow
import com.alijaya.smartschool.tv.data.User
import com.alijaya.smartschool.tv.data.UserRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun CoroutineScope.safe(onError: (String) -> Unit, block: suspend () -> Unit) =
    launch { try { block() } catch (e: Exception) { onError(e.message ?: "Gagal") } }

@Composable
private fun rememberClasses(): List<SchoolClass> {
    val repo = App.instance.repo
    return produceState(emptyList<SchoolClass>()) { value = repo.db.classes().all() }.value
}

@Composable
private fun ErrorText(msg: String?) { msg?.let { Text(it, color = Color(0xFFF87171)) } }

@Composable
private fun ConfirmDelete(what: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Hapus $what?") },
    text = { Text("Data yang dihapus tidak bisa dikembalikan.") },
    confirmButton = { DangerButton("Hapus", onConfirm) },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
)

/* =================== SISWA =================== */

@Composable
fun StudentsScreen(user: User) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    val classes = rememberClasses()
    val isAdmin = user.role == User.ROLE_ADMIN
    var classFilter by remember { mutableStateOf(if (isAdmin) null else user.classId) }
    var q by remember { mutableStateOf("") }
    val rows by remember(classFilter, q) { repo.db.students().observeRows(classFilter, q) }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<Student?>(null) }
    var deleting by remember { mutableStateOf<StudentRow?>(null) }

    Column {
        ScreenHeader("Data Siswa (${rows.size})") {
            Button(onClick = { editing = Student(nis = "", qrToken = "", name = "", classId = classFilter) }) { Text("+ Tambah Siswa") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Field("Cari nama / NIS", q, { q = it }, Modifier.width(320.dp))
            Picker("Kelas", listOf<Pair<Long?, String>>(null to "Semua") + classes.map { it.id to it.name }, classFilter, { classFilter = it }, Modifier.width(260.dp))
        }
        Row(Modifier.padding(vertical = 10.dp, horizontal = 14.dp)) {
            listOf("NIS" to 110, "Nama" to 240, "Kelas" to 110, "L/P" to 50, "Orang Tua" to 160, "No. WA" to 150, "Tabungan" to 120).forEach { (t, w) -> Cell(t, w, Muted) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = { it.id }) { s ->
                FocusCard(Modifier.fillMaxWidth(), onClick = {
                    scope.launch { editing = repo.db.students().byId(s.id) }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell(s.nis, 110); Cell(s.name, 240); Cell(s.className ?: "-", 110); Cell(s.gender, 50)
                        Cell(s.parentName, 160); Cell(s.parentPhone, 150); Cell(rupiah(s.savingsBalance), 120, Teal)
                        if (!s.isActive) Text("nonaktif", color = Color(0xFFF87171))
                        if (isAdmin) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)); TextButton(onClick = { deleting = s }) { Text("Hapus", color = Color(0xFFF87171)) } }
                    }
                }
            }
        }
    }

    editing?.let { st -> StudentDialog(st, classes, onDismiss = { editing = null }) { editing = null } }
    deleting?.let { d ->
        ConfirmDelete("siswa ${d.name}", { deleting = null }) { scope.launch { repo.deleteStudent(d.id); deleting = null } }
    }
}

@Composable
private fun StudentDialog(initial: Student, classes: List<SchoolClass>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(initial) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Tambah Siswa" else "Edit Siswa") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("NIS", s.nis, { s = s.copy(nis = it) }, Modifier.weight(1f))
                    Field("NISN", s.nisn, { s = s.copy(nisn = it) }, Modifier.weight(1f))
                }
                Field("Nama Lengkap", s.name, { s = s.copy(name = it) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Picker("Kelas", classes.map { it.id as Long? to it.name }, s.classId, { s = s.copy(classId = it) }, Modifier.weight(1f))
                    Picker("Jenis Kelamin", listOf("L" to "Laki-laki", "P" to "Perempuan"), s.gender, { s = s.copy(gender = it) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("UID Kartu RFID", s.rfidUid.orEmpty(), { s = s.copy(rfidUid = it) }, Modifier.weight(1f))
                    Field("Token QR (kosong = otomatis)", s.qrToken, { s = s.copy(qrToken = it) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Nama Orang Tua", s.parentName, { s = s.copy(parentName = it) }, Modifier.weight(1f))
                    Field("No. WA Orang Tua", s.parentPhone, { s = s.copy(parentPhone = it) }, Modifier.weight(1f), number = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktif", modifier = Modifier.padding(end = 12.dp))
                    Switch(checked = s.isActive, onCheckedChange = { s = s.copy(isActive = it) })
                }
                ErrorText(err)
            }
        },
        confirmButton = { Button(onClick = { scope.safe({ err = it }) { repo.saveStudent(s); onSaved() } }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/* =================== KELAS =================== */

@Composable
fun ClassesScreen(isAdmin: Boolean) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    val rows by remember { repo.db.classes().observeRows() }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<ClassRow?>(null) }
    var deleting by remember { mutableStateOf<ClassRow?>(null) }

    Column {
        ScreenHeader("Data Kelas (${rows.size})") {
            if (isAdmin) Button(onClick = { editing = ClassRow(0, "", "", 0, null) }) { Text("+ Tambah Kelas") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = { it.id }) { c ->
                FocusCard(Modifier.fillMaxWidth(), onClick = if (isAdmin) ({ editing = c }) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell(c.name, 200); Cell("Tingkat ${c.level}", 140, Muted); Cell("${c.studentCount} siswa", 140, Teal)
                        Cell("Wali: ${c.waliName ?: "-"}", 300, Muted)
                        if (isAdmin) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)); TextButton(onClick = { deleting = c }) { Text("Hapus", color = Color(0xFFF87171)) } }
                    }
                }
            }
        }
    }

    editing?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var level by remember(c) { mutableStateOf(c.level) }
        var err by remember(c) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.id == 0L) "Tambah Kelas" else "Edit Kelas") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Field("Nama Kelas", name, { name = it }); Field("Tingkat", level, { level = it }); ErrorText(err) } },
            confirmButton = { Button(onClick = { scope.safe({ err = it }) { repo.saveClass(c.id, name, level); editing = null } }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } }
        )
    }
    deleting?.let { d -> ConfirmDelete("kelas ${d.name}", { deleting = null }) { scope.launch { repo.deleteClass(d.id); deleting = null } } }
}

/* =================== GURU =================== */

@Composable
fun TeachersScreen(current: User) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    val classes = rememberClasses()
    val rows by remember { repo.db.users().observeRows() }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<UserRow?>(null) }
    var deleting by remember { mutableStateOf<UserRow?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    Column {
        ScreenHeader("Guru & Admin (${rows.size})") {
            Button(onClick = { editing = UserRow(0, "", "", "", User.ROLE_GURU, null, null) }) { Text("+ Tambah Guru") }
        }
        ErrorText(err)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = { it.id }) { u ->
                FocusCard(Modifier.fillMaxWidth(), onClick = { editing = u }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell(u.name, 240); Cell(u.username, 140, Muted); Cell(u.role.uppercase(), 90, if (u.role == User.ROLE_ADMIN) Color(0xFFFBBF24) else Teal)
                        Cell(u.phone, 160, Muted); Cell("Wali: ${u.className ?: "-"}", 200, Muted)
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        TextButton(onClick = { deleting = u }) { Text("Hapus", color = Color(0xFFF87171)) }
                    }
                }
            }
        }
    }

    editing?.let { u ->
        var username by remember(u) { mutableStateOf(u.username) }
        var name by remember(u) { mutableStateOf(u.name) }
        var phone by remember(u) { mutableStateOf(u.phone) }
        var password by remember(u) { mutableStateOf("") }
        var role by remember(u) { mutableStateOf(u.role) }
        var classId by remember(u) { mutableStateOf(u.classId) }
        var e by remember(u) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (u.id == 0L) "Tambah Guru" else "Edit Guru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Nama", name, { name = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Username", username, { username = it }, Modifier.weight(1f))
                        Field(if (u.id == 0L) "Password" else "Password baru (opsional)", password, { password = it }, Modifier.weight(1f), password = true)
                    }
                    Field("No. WA", phone, { phone = it }, number = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Picker("Role", listOf(User.ROLE_GURU to "Guru", User.ROLE_ADMIN to "Admin"), role, { role = it }, Modifier.weight(1f))
                        Picker("Wali Kelas", listOf<Pair<Long?, String>>(null to "-") + classes.map { it.id to it.name }, classId, { classId = it }, Modifier.weight(1f))
                    }
                    ErrorText(e)
                }
            },
            confirmButton = {
                Button(onClick = { scope.safe({ e = it }) { repo.saveUser(u.id, username, password, name, Repository.normalizePhone(phone), role, classId); editing = null } }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } }
        )
    }
    deleting?.let { d ->
        ConfirmDelete("akun ${d.name}", { deleting = null }) { scope.safe({ err = it; deleting = null }) { repo.deleteUser(d.id, current.id); deleting = null } }
    }
}

/* =================== PENGATURAN =================== */

@Composable
fun SettingsScreen() {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { values = repo.settings() }
    fun v(k: String) = values[k].orEmpty()
    fun set(k: String, x: String) { values = values + (k to x) }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Pengaturan") {
            Button(onClick = { scope.launch { repo.saveSettings(values); msg = "Pengaturan tersimpan" } }) { Text("Simpan") }
        }
        msg?.let { Text(it, color = Teal) }
        Text("Profil Sekolah", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Nama Sekolah", v("school_name"), { set("school_name", it) }, Modifier.weight(1f))
            Field("Kepala Sekolah", v("principal_name"), { set("principal_name", it) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Alamat", v("school_address"), { set("school_address", it) }, Modifier.weight(2f))
            Field("Telepon", v("school_phone"), { set("school_phone", it) }, Modifier.weight(1f))
        }
        Text("Jam Sekolah", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Jam Masuk (HH:mm)", v("jam_masuk"), { set("jam_masuk", it) }, Modifier.weight(1f))
            Field("Toleransi (menit)", v("toleransi_menit"), { set("toleransi_menit", it) }, Modifier.weight(1f), number = true)
            Field("Jam Pulang", v("jam_pulang"), { set("jam_pulang", it) }, Modifier.weight(1f))
            Field("Jam Rekap ke Wali Kelas", v("jam_rekap_guru"), { set("jam_rekap_guru", it) }, Modifier.weight(1f))
        }
        Text("Notifikasi WhatsApp", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker("Gateway", listOf("fonnte" to "Fonnte", "wablas" to "Wablas", "none" to "Nonaktif"), v("wa_provider"), { set("wa_provider", it) }, Modifier.weight(1f))
            Field("Token API", v("wa_token"), { set("wa_token", it) }, Modifier.weight(2f), password = true)
        }
        if (v("wa_provider") == "wablas") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Domain Wablas", v("wa_wablas_domain"), { set("wa_wablas_domain", it) }, Modifier.weight(1f))
            Field("Secret Key (opsional)", v("wa_secret_key"), { set("wa_secret_key", it) }, Modifier.weight(1f), password = true)
        }
        Text("Server", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field("Port Web Remote", v("server_port"), { set("server_port", it) }, Modifier.width(220.dp), number = true)
            OutlinedButton(onClick = {
                scope.launch { repo.saveSettings(values); com.alijaya.smartschool.tv.service.ServerService.restart(App.instance); msg = "Server direstart" }
            }) { Text("Simpan & Restart Server") }
        }
        Text("Data Demo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        DangerButton("Reset Data Demo (hapus semua data)") { confirmReset = true }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset semua data?") },
        text = { Text("Semua data akan dihapus dan diganti data dummy. Login: admin/admin123.") },
        confirmButton = { DangerButton("Reset") { scope.launch { repo.resetDemo(); values = repo.settings(); confirmReset = false; msg = "Data demo dibuat ulang" } } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal") } }
    )
}
