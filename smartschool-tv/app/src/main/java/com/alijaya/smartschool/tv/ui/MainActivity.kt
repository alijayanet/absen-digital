package com.alijaya.smartschool.tv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijaya.smartschool.tv.App
import com.alijaya.smartschool.tv.data.User
import com.alijaya.smartschool.tv.service.ServerService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Layar TV selalu menyala (Digital Signage / Kiosk Always-ON, tidak akan sleep/mati otomatis)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.decorView.isSoundEffectsEnabled = false
        ServerService.start(this)
        setContent { AppTheme { Root() } }
    }
}

enum class Menu(val title: String, val icon: ImageVector, val adminOnly: Boolean = false) {
    HOME("Beranda", Icons.Default.Home),
    KIOSK("Kiosk Absen", Icons.Default.QrCodeScanner),
    ATTENDANCES("Rekap Absen", Icons.Default.Assignment),
    LEAVES("Izin / Sakit", Icons.Default.EventNote),
    IDCARD("Kartu Pelajar", Icons.Default.Badge),
    FINANCE("Keuangan SPP", Icons.Default.Payments),
    SAVINGS("Tabungan", Icons.Default.AccountBalanceWallet),
    QUEUE("Antrian Qur'an", Icons.Default.ConfirmationNumber),
    STUDENTS("Siswa", Icons.Default.People),
    CLASSES("Kelas", Icons.Default.Class),
    TEACHERS("Guru", Icons.Default.Person, adminOnly = true),
    WHATSAPP("WhatsApp", Icons.AutoMirrored.Filled.Send, adminOnly = true),
    SETTINGS("Pengaturan", Icons.Default.Settings, adminOnly = true)
}

@Composable
fun Root() {
    var loggedInUser by remember { mutableStateOf<User?>(null) }
    var showLoginDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        val u = loggedInUser
        if (u != null) {
            // Jika admin/guru sudah login, tampilkan Shell TV lengkap
            Shell(u) { loggedInUser = null }
        } else {
            // Mode Default Standalone: Layar Kiosk / Digital Signage Langsung!
            KioskScreen(onOpenAdmin = { showLoginDialog = true })

            // Dialog Login Bersih (Tanpa keterangan akun demo) jika staf/admin butuh akses Shell TV
            if (showLoginDialog) {
                AdminLoginDialog(
                    onDismiss = { showLoginDialog = false },
                    onSuccess = {
                        loggedInUser = it
                        showLoginDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun AdminLoginDialog(onDismiss: () -> Unit, onSuccess: (User) -> Unit) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Masuk Menu Admin / Guru", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Masukkan kredensial untuk membuka navigasi internal TV.", color = Muted, fontSize = 13.sp)
                Field("Username", username, { username = it })
                Field("Password", password, { password = it }, password = true)
                error?.let { Text(it, color = Color(0xFFF87171), fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val u = repo.login(username, password)
                    if (u == null) error = "Username atau password salah" else onSuccess(u)
                }
            }) { Text("Masuk") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun Shell(user: User, onLogout: () -> Unit) {
    var menu by remember { mutableStateOf(Menu.HOME) }
    val isAdmin = user.role == User.ROLE_ADMIN
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(220.dp).fillMaxHeight().background(Panel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Smart School", color = Teal, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
            Text(user.name, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(12.dp))
            Menu.entries.filter { isAdmin || !it.adminOnly }.forEach { m ->
                NavItem(m.title, m.icon, menu == m) { menu = m }
            }
            Spacer(Modifier.weight(1f))
            NavItem("Keluar", Icons.AutoMirrored.Filled.Logout, false, onLogout)
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            when (menu) {
                Menu.HOME -> HomeScreen()
                Menu.KIOSK -> KioskScreen()
                Menu.ATTENDANCES -> AttendanceScreen(user)
                Menu.LEAVES -> LeavesScreen(user)
                Menu.IDCARD -> IdCardScreen()
                Menu.FINANCE -> FinanceScreen(user)
                Menu.SAVINGS -> SavingsScreen(user)
                Menu.QUEUE -> QueueScreen(user)
                Menu.STUDENTS -> StudentsScreen(user)
                Menu.CLASSES -> ClassesScreen(isAdmin)
                Menu.TEACHERS -> TeachersScreen(user)
                Menu.WHATSAPP -> WhatsAppScreen()
                Menu.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun NavItem(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth(), selected = selected, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) Teal else Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 16.sp)
        }
    }
}
