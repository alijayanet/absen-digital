package com.alijaya.smartschool.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BluetoothPrinterDevice(
    val name: String,
    val address: String,
    val isPaired: Boolean = true
)

data class QueueTicket(
    val schoolName: String = "MAJLIS TA'LIM & PENGAJIAN",
    val schoolTagline: String = "",
    val queueNumber: Int,
    val studentName: String,
    val nis: String = "-",
    val teacherName: String = "",
    val className: String = "",
    val timeIn: String = "",
    val dateText: String = "",
    val note: String = "Harap tertib menunggu giliran mengaji."
)

object BluetoothThermalPrinter {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Commands
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_DOUBLE_HEIGHT_ON = byteArrayOf(0x1B, 0x21, 0x10)
    private val ESC_DOUBLE_WIDTH_ON = byteArrayOf(0x1B, 0x21, 0x20)
    private val ESC_DOUBLE_WIDTH_HEIGHT_ON = byteArrayOf(0x1B, 0x21, 0x30) // Karakter Jumbo
    private val ESC_NORMAL = byteArrayOf(0x1B, 0x21, 0x00)

    @SuppressLint("MissingPermission")
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) {
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(context: Context): List<BluetoothPrinterDevice> {
        val list = mutableListOf<BluetoothPrinterDevice>()
        try {
            val bluetoothAdapter = getBluetoothAdapter(context)
            if (bluetoothAdapter != null) {
                // Pastikan adapter aktif atau coba ambil daftar bonded devices
                val bonded = try { bluetoothAdapter.bondedDevices } catch (_: SecurityException) { null }
                if (bonded != null && bonded.isNotEmpty()) {
                    for (device in bonded) {
                        val name = try { device.name } catch (_: Exception) { null } ?: "Printer Bluetooth"
                        list.add(BluetoothPrinterDevice(name = name, address = device.address, isPaired = true))
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        return list
    }

    fun sanitizeForPrinter(text: String): String {
        return text
            .replace("•", "-")
            .replace("–", "-")
            .replace("—", "-")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("‘", "'")
            .replace("’", "'")
    }

    /**
     * Membangun byte data cetak Struk Nomor Antrian Santri (58mm / 32 kolom karakter)
     * Nomor antrian dicetak dengan font BESAR / JUMBO tebal di tengah struk.
     */
    fun buildQueueTicketBytes(ticket: QueueTicket, paperWidthChars: Int = 32): ByteArray {
        val out = mutableListOf<Byte>()

        fun append(bytes: ByteArray) { for (b in bytes) out.add(b) }
        fun appendText(text: String) {
            val clean = sanitizeForPrinter(text)
            for (b in clean.toByteArray(Charsets.US_ASCII)) out.add(b)
        }
        fun appendLine(text: String = "") { appendText(text + "\n") }
        fun appendDashes() { appendLine("-".repeat(paperWidthChars)) }
        fun appendDoubleDashes() { appendLine("=".repeat(paperWidthChars)) }

        fun appendKeyValue(key: String, value: String) {
            val cleanKey = sanitizeForPrinter(key)
            val cleanValue = sanitizeForPrinter(value)
            val spaceNeeded = paperWidthChars - cleanKey.length - cleanValue.length
            if (spaceNeeded > 0) {
                appendLine(cleanKey + " ".repeat(spaceNeeded) + cleanValue)
            } else {
                appendLine(cleanKey)
                appendLine(" ".repeat(maxOf(0, paperWidthChars - cleanValue.length)) + cleanValue)
            }
        }

        // Initialize Printer
        append(byteArrayOf(0x1B, 0x40))
        append(ESC_NORMAL)

        // 1. Header: Nama Sekolah / Majlis Ta'lim (dari server)
        append(ESC_ALIGN_CENTER)
        append(ESC_BOLD_ON)
        append(ESC_DOUBLE_HEIGHT_ON)
        appendLine(ticket.schoolName.uppercase())
        append(ESC_NORMAL)
        append(ESC_BOLD_OFF)

        if (ticket.schoolTagline.isNotBlank()) {
            appendLine(ticket.schoolTagline)
        }
        appendDoubleDashes()

        // 2. Info Tanggal & Waktu
        val now = Date()
        val defaultDateStr = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).format(now)
        val defaultTimeStr = SimpleDateFormat("HH:mm:ss 'WIB'", Locale("id", "ID")).format(now)
        val tgl = if (ticket.dateText.isNotBlank()) ticket.dateText else defaultDateStr
        val jam = if (ticket.timeIn.isNotBlank()) ticket.timeIn else defaultTimeStr

        append(ESC_ALIGN_LEFT)
        appendKeyValue("Tgl: $tgl", "Jam: $jam")
        appendDashes()

        // 3. NOMOR ANTRIAN JUMBO / EXTRA BESAR
        append(ESC_ALIGN_CENTER)
        append(ESC_BOLD_ON)
        appendLine("NOMOR ANTRIAN MENGAJI")
        append(ESC_NORMAL)
        appendLine()

        val numStr = String.format("%02d", ticket.queueNumber)
        // Karakter Ekstra Besar (Double Width + Double Height)
        append(ESC_BOLD_ON)
        append(ESC_DOUBLE_WIDTH_HEIGHT_ON)
        appendLine("[  $numStr  ]")
        append(ESC_NORMAL)
        append(ESC_BOLD_OFF)
        appendLine()

        // 4. Data Santri & Pengajar
        append(ESC_ALIGN_LEFT)
        appendDashes()
        append(ESC_BOLD_ON)
        appendKeyValue("Santri", ticket.studentName.uppercase())
        append(ESC_BOLD_OFF)

        if (ticket.nis.isNotBlank() && ticket.nis != "-") {
            appendKeyValue("NIS", ticket.nis)
        }

        if (ticket.className.isNotBlank()) {
            appendKeyValue("Kelas", ticket.className)
        }

        if (ticket.teacherName.isNotBlank()) {
            append(ESC_BOLD_ON)
            appendKeyValue("Pengajar", ticket.teacherName)
            append(ESC_BOLD_OFF)
        }
        appendDashes()

        // 5. Footer & Catatan
        append(ESC_ALIGN_CENTER)
        if (ticket.note.isNotBlank()) {
            appendLine(ticket.note)
        }
        appendLine("Simpan struk ini hingga dipanggil.")
        appendDoubleDashes()

        // Feed Kertas
        for (i in 0 until 4) {
            append(byteArrayOf(0x0D, 0x0A))
        }

        return out.toByteArray()
    }

    /**
     * Struk Test Cetak Printer
     */
    fun buildTestReceiptBytes(schoolName: String = "SMART SCHOOL & MAJLIS TA'LIM"): ByteArray {
        val testTicket = QueueTicket(
            schoolName = schoolName,
            schoolTagline = "Sistem Presensi & Antrian Mengaji",
            queueNumber = 1,
            studentName = "SANTRI UJI COBA",
            nis = "12345",
            teacherName = "USTADZ PEMBINA",
            className = "KELAS UJI COBA",
            note = "Printer Bluetooth Terhubung Sempurna!"
        )
        return buildQueueTicketBytes(testTicket)
    }

    /**
     * Mengirim byte array ke printer Bluetooth thermal via SPP Socket
     */
    @SuppressLint("MissingPermission")
    suspend fun printBytes(
        deviceAddress: String,
        data: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        if (deviceAddress.isBlank()) {
            return@withContext Result.failure(Exception("Alamat MAC printer belum dipilih."))
        }

        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        val cleanAddress = deviceAddress.trim().uppercase(Locale.ROOT)
        if (!BluetoothAdapter.checkBluetoothAddress(cleanAddress)) {
            return@withContext Result.failure(Exception("Format MAC Address '$deviceAddress' tidak valid. Contoh format: 66:22:BB:11:33:AA"))
        }

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth tidak didukung pada perangkat ini"))

            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(cleanAddress)
            try { bluetoothAdapter.cancelDiscovery() } catch (_: Throwable) { }

            // 4-Tier Socket Connection (Mendukung segala varian STB / Android TV dan jenis printer)
            socket = try {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).apply { connect() }
            } catch (_: Exception) {
                try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID).apply { connect() }
                } catch (_: Exception) {
                    try {
                        val method = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                        (method.invoke(device, 1) as BluetoothSocket).apply { connect() }
                    } catch (_: Exception) {
                        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        (method.invoke(device, 1) as BluetoothSocket).apply { connect() }
                    }
                }
            }

            kotlinx.coroutines.delay(100)
            outputStream = socket.outputStream

            val chunkSize = 256
            var offset = 0
            while (offset < data.size) {
                val len = minOf(chunkSize, data.size - offset)
                outputStream.write(data, offset, len)
                outputStream.flush()
                offset += len
                kotlinx.coroutines.delay(40)
            }
            kotlinx.coroutines.delay(1500)

            val deviceName = try { device.name } catch (_: Exception) { null } ?: deviceAddress
            Result.success("Struk berhasil dicetak ke $deviceName")
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mencetak: ${e.localizedMessage}"))
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (_: Exception) { }
        }
    }
}
