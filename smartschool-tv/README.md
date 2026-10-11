# Smart School TV (Android STB Edition)

Aplikasi Standalone Manajemen Sekolah & Absensi Siswa yang berjalan langsung di atas perangkat **Android TV / Set-Top Box (STB)**.  
**100% Offline-First**, tanpa memerlukan server Node.js terpisah atau database cloud eksternal.

---

## 🌟 Fitur Utama

1. **Dual Interface**:
   - **Native TV UI (Compose for TV)**: Desain landscape 1080p, navigasi remote D-pad, responsif, dan ramah TV.
   - **Embedded Web Remote (HTTP Ktor port 8080)**: Guru dan admin dapat membuka browser HP/laptop pada jaringan Wi-Fi/LAN yang sama (`http://<IP-STB>:8080`) untuk input data, cetak kartu, dan rekapitulasi.
2. **Kiosk Absensi Cerdas**:
   - Dukungan scan kartu RFID (USB keyboard wedge) dan QR code scanner kamera/USB.
   - Dilengkapi suara beep interaktif dan Text-to-Speech (TTS) Bahasa Indonesia ("Terima kasih, nama siswa, hadir").
3. **Rekap Absensi & Izin/Sakit**:
   - Rekap absensi harian dengan filter tanggal, kelas, dan status (Hadir, Terlambat, Izin, Sakit, Alpa).
   - Input izin/sakit manual dengan approval alur wali kelas.
   - Ekspor rekapitulasi ke format CSV/Excel.
4. **WhatsApp Gateway & Antrian Pesan Otomatis**:
   - Terintegrasi dengan provider Fonnte & Wablas (dapat diganti di Pengaturan).
   - Pengiriman otomatis real-time ke orang tua saat siswa tap kartu absensi.
   - Pengiriman ringkasan absensi harian otomatis ke nomor WhatsApp wali kelas setiap jam yang ditentukan.
   - Broadcast manual ALPA ke orang tua siswa.
5. **Cetak Kartu Pelajar Digital (Standar CR80)**:
   - Layout kartu pelajar rasio CR80 (85.6mm x 53.98mm).
   - Generator QR code offline (ZXing).
   - Tombol kirim kartu pelajar langsung via WhatsApp ke nomor orang tua.
6. **Keuangan SPP & Dynamic QRIS**:
   - Generator tagihan SPP bulanan otomatis dengan penambahan 3 digit kode unik transfer.
   - **Offline Dynamic QRIS EMVCo**: Mengubah QRIS statis sekolah menjadi QRIS dinamis ber-nominal otomatis dengan kalkulasi checksum CRC16 CCITT.
   - Webhook penerimaan transfer bank lokal (`/api/webhook/payment`).
7. **Buku Tabungan Santri / Siswa**:
   - Pencatatan transaksi setor dan tarik tunai tabungan.
   - Validasi saldo real-time.
   - Pengiriman bukti transaksi tabungan otomatis ke WhatsApp orang tua.
8. **Antrian Setoran Al-Qur'an & Display TV + Jadwal Sholat**:
   - Nomor antrian setoran tahfidz harian dengan fitur Panggil, Panggil Ulang, Selesai, dan Lewati.
   - Suara TTS otomatis panggilan antrian santri ke meja pengampu.
   - Hisab perhitungan jadwal waktu sholat standar Kementerian Agama RI (offline falak astronomi matahari).
9. **Backup & Restore Database SQLite**:
   - Unduh database lengkap (`smartschool.db`) langsung melalui web remote untuk backup berkala di flashdisk/PC.
   - Fitur restore database instan.
   - Tombol "Reset Data Demo" untuk kebutuhan pengujian.

---

## 🔑 Akun Demo Default

- **Admin**:
  - Username: `admin`
  - Password: `admin123`
- **Wali Kelas**:
  - Username: `guru1` s/d `guru7`
  - Password: `guru123`

---

## 🚀 Panduan Instalasi ke STB Android

1. Aktifkan **Developer Options** dan **USB Debugging** pada STB Android TV Anda.
2. Sambungkan STB ke PC via kabel USB atau jaringan Wi-Fi (ADB over network):
   ```bash
   adb connect <IP-STB>:5555
   ```
3. Install file APK hasil kompilasi:
   ```bash
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
4. Buka aplikasi **Smart School** di layar TV Anda.
5. Perhatikan alamat IP yang tampil di halaman Beranda TV (contoh: `http://192.168.1.50:8080`).
6. Buka alamat tersebut melalui browser HP atau laptop Anda untuk mulai mengelola sekolah secara nirkabel!

---

## 🛠️ Spesifikasi Teknologi

- **Platform**: Android TV / Leanback (minSdk 24 / Android 7.0 Nougat s/d Android 15+)
- **Bahasa**: Kotlin 2.2.10
- **UI**: Jetpack Compose + Material3 (TV D-pad friendly)
- **Database**: Android Jetpack Room 2.7.0 (SQLite WAL mode)
- **Embedded Web Server**: Ktor 2.3.12 CIO engine
- **QR Engine**: ZXing Embedded 3.5.0
- **Audio & Speech**: Android SoundPool & Android Text-To-Speech (Indonesian locale)
