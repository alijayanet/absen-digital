# Absen Digital - Sistem Presensi & Antrian Mengaji Santri Terpadu

[![GitHub Repository](https://img.shields.io/badge/GitHub-alijayanet%2Fabsen--digital-181717?style=for-the-badge&logo=github)](https://github.com/alijayanet/absen-digital)
[![Node.js](https://img.shields.io/badge/Node.js-v18%20%7C%20v20+-339933?style=for-the-badge&logo=node.js&logoColor=white)](https://nodejs.org/)
[![Express.js](https://img.shields.io/badge/Express.js-v4.21-000000?style=for-the-badge&logo=express&logoColor=white)](https://expressjs.com/)
[![SQLite WAL](https://img.shields.io/badge/SQLite-WAL%20Mode-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://www.sqlite.org/)
[![WhatsApp Baileys](https://img.shields.io/badge/WhatsApp-Baileys%20v7-25D366?style=for-the-badge&logo=whatsapp&logoColor=white)](https://github.com/WhiskeySockets/Baileys)
[![Android TV](https://img.shields.io/badge/Android%20TV-Native%20App-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#-aplikasi-native-android-tv--stb)
[![Bluetooth Printer](https://img.shields.io/badge/Bluetooth%20Printer-ESC%2FPOS%2058mm-blue?style=for-the-badge&logo=bluetooth&logoColor=white)](#-fitur-printer-bluetooth-thermal-58mm)
[![Developer](https://img.shields.io/badge/Pengembang-ALIJAYA--NET-0284c7?style=for-the-badge&logo=github&logoColor=white)](https://github.com/alijayanet)
[![WhatsApp Support](https://img.shields.io/badge/WhatsApp-081947215703-25D366?style=for-the-badge&logo=whatsapp&logoColor=white)](https://wa.me/6281947215703)

Sistem Informasi Manajemen Sekolah & Majlis Ta'lim Modern (*All-in-One Digital Attendance & Quran Queue Management System*). Menggabungkan **Presensi Digital IoT (Kartu RFID & Barcode/QR Code)**, **Manajemen Antrian Mengaji Santri Per Ustadz/Guru**, **Display TV Digital & Kiosk Real-Time**, **Panggilan Suara Natural Human TTS Berbahasa Indonesia**, **Cetak Struk Antrian Otomatis via Printer Bluetooth Thermal**, **Notifikasi WhatsApp Gateway 2-Arah**, serta **Website Profil & CMS Lembaga**.

---

## 📑 Daftar Isi

- [Fitur Utama Sistem](#-fitur-utama-sistem)
- [Arsitektur Sistem](#-arsitektur-sistem)
- [Prasyarat Sistem (Prerequisites)](#-prasyarat-sistem-prerequisites)
- [Panduan Instalasi Server (Node.js)](#-panduan-instalasi-server-nodejs)
  - [1. Clone Repositori](#1-clone-repositori)
  - [2. Install Dependensi](#2-install-dependensi)
  - [3. Konfigurasi File Environment (.env)](#3-konfigurasi-file-environment-env)
  - [4. Inisialisasi Database Awal](#4-inisialisasi-database-awal)
  - [5. Menjalankan Aplikasi](#5-menjalankan-aplikasi)
- [Aplikasi Native Android TV / STB](#-aplikasi-native-android-tv--stb)
  - [Fitur Aplikasi Native TV](#fitur-aplikasi-native-tv)
  - [Cara Menghubungkan TV ke Server](#cara-menghubungkan-tv-ke-server)
  - [Cara Setting Printer Bluetooth Thermal](#cara-setting-printer-bluetooth-thermal)
- [Panduan Menghubungkan WhatsApp Gateway](#-panduan-menghubungkan-whatsapp-gateway)
- [Akun Pengguna Bawaan (Default Credentials)](#-akun-pengguna-bawaan-default-credentials)
- [Akses URL Cepat](#-akses-url-cepat)
- [Struktur Direktori Proyek](#-struktur-direktori-proyek)
- [Pengembang & Bantuan Teknis](#-pengembang--bantuan-teknis)

---

## 🌟 Fitur Utama Sistem

### 1. Presensi Digital Santri/Siswa (RFID & QR Code)
- **Single Tap Fast Scanning**: Cukup dekatkan kartu RFID ke scanner USB tanpa perlu klik apapun di layar.
- **Kiosk Web & Native TV Client**: Dapat diakses via web browser (`/kiosk`) maupun lewat aplikasi Android TV native.
- **Deteksi Anti-Double Scan**: Mencegah tap berulang kali pada hari yang sama.
- **Foto Santri & Pop-up Status**: Menampilkan nama santri, NIS, kelas, foto profil, dan status kehadiran secara instan.

### 2. Antrian Mengaji Berbasis Ustadz/Guru
- **Pemberian Nomor Antrian Otomatis**: Saat santri melakukan presensi masuk, sistem otomatis menerbitkan nomor antrian mengaji sesuai ustadz/guru pengampu santri tersebut.
- **Meja Mengaji Real-Time**: Tampilan antrian per meja ustadz di layar TV (nomor yang sedang mengaji, daftar tunggu, dan statistik harian).
- **Dashboard Panggilan Guru (`/queue/desk`)**: Panel khusus untuk ustadz/guru memanggil santri berikutnya (*Panggil*, *Panggil Ulang*, *Lewati*, *Selesai*).

### 3. Voice TTS Natural Human Indonesia & Airport Chime
- **Dual TTS Engine**: Menggunakan suara manusia natural berbahasa Indonesia (Google Stream + MD5 disk cache) dengan fallback ke engine TTS lokal STB/Android TV.
- **Harmonic Airport Ding-Dong Chime**: Sintesis nada bel dua nada (D5 587.33 Hz $\to$ A4 440 Hz AudioTrack PCM 16-bit) sebelum santri dipanggil.
- **Penyebutan Lengkap**: Menyebutkan nomor antrian, nama santri, serta ustadz/guru yang dituju:
  > *"Nomor antrian 05, Ahmad Fauzi, silakan menuju Ustadz Abdullah."*
- **Toggle ON/OFF TTS**: Suara panggilan dapat dimatikan di pengaturan TV jika digunakan di instansi sekolah formal yang memerlukan ketenangan.

### 4. Printer Bluetooth Thermal (58mm ESC/POS)
- **Cetak Nomor Antrian Otomatis**: Saat kartu RFID di-tap, printer thermal mencetak tiket antrian fisik untuk santri.
- **Nomor Antrian Jumbo**: Angka nomor antrian dicetak tebal dan ekstra besar (Double Width + Double Height) di tengah struk.
- **Header Dinamis Mengikuti Server**: Nama lembaga/sekolah dan slogan pada struk otomatis mengikuti server yang sedang aktif.
- **Fitur Opsional (Toggle ON/OFF)**: Dapat diaktifkan atau dinonaktifkan kapan saja melalui menu pengaturan aplikasi TV.

### 5. WhatsApp Gateway Interaktif (Baileys v7)
- **Notifikasi Kehadiran Otomatis**: Pesan WhatsApp terkirim langsung ke orang tua santri saat presensi berhasil tercatat.
- **Bot Informasi Otomatis**: Wali santri dapat mengecek riwayat kehadiran dengan mengirim pesan ke WhatsApp resmi lembaga.

### 6. Zero Heavy Database (SQLite WAL Mode)
- Menggunakan database **SQLite** dengan mode **WAL (Write-Ahead Logging)** yang super cepat, ringan, tahan crash, dan mudah dibackup (cukup salin 1 file database).

---

## 🛠️ Prasyarat Sistem (Prerequisites)

Sebelum melakukan instalasi, pastikan server/komputer Anda sudah terpasang:
- **Node.js**: Versi `v18.x`, `v20.x`, atau yang lebih baru ([Download Node.js](https://nodejs.org/))
- **NPM**: Bawaan dari instalasi Node.js
- **Git**: Untuk mendownload source code ([Download Git](https://git-scm.com/))
- **PM2** (Opsional, untuk production server): `npm install -g pm2`

Untuk perangkat client (opsional):
- **Android TV / TV Box (STB Android 6.0+)**
- **USB RFID Reader (125KHz / 13.56MHz Keyboard Wedge)**
- **Printer Bluetooth Thermal 58mm (ESC/POS)**

---

## 💻 Panduan Instalasi Server (Node.js)

### 1. Clone Repositori
Buka terminal / PowerShell dan clone repositori ini:
```bash
git clone https://github.com/alijayanet/absen-digital.git
cd absen-digital
```

### 2. Install Dependensi
Jalankan perintah instalasi paket Node.js:
```bash
npm install
```

### 3. Konfigurasi File Environment (.env)
Salin contoh konfigurasi ke file `.env`:
- **Windows (PowerShell / CMD)**:
  ```powershell
  copy env-example.txt .env
  ```
- **Linux / macOS**:
  ```bash
  cp env-example.txt .env
  ```

Buka dan sesuaikan isi file `.env` jika diperlukan:
```env
# Port server web
PORT=3000

# Kunci rahasia enkripsi session
SESSION_SECRET=absensi-digital-secret-key-2026-xyz

# Mode lingkungan (development / production)
NODE_ENV=development

# Lokasi database SQLite
DB_PATH=./data/database.sqlite

# Folder autentikasi WhatsApp Baileys
AUTH_FOLDER=./data/baileys_auth

# Folder unggahan foto dan berkas
UPLOAD_FOLDER=./data/uploads
```

### 4. Inisialisasi Database Awal
Jalankan migrasi dan seeding data default:
```bash
npm run seed
```
> Perintah ini akan membuat struktur tabel database dan akun pengguna administrator bawaan.

### 5. Menjalankan Aplikasi

- **Mode Development (Auto Reload)**:
  ```bash
  npm run dev
  ```
- **Mode Standard**:
  ```bash
  npm start
  ```
- **Mode Production (Background Service via PM2)**:
  ```bash
  npm run pm2:start
  ```
  Untuk memantau log server:
  ```bash
  npm run pm2:logs
  ```

Buka browser Anda dan akses:
👉 **http://localhost:3000** atau alamat IP server LAN Anda.

---

## 📺 Aplikasi Native Android TV / STB

Sistem ini dilengkapi aplikasi Android native (`SmartSchool-TV-Native-RFID-Client.apk`) yang dirancang khusus untuk layar TV Digital / STB tanpa touchscreen, dioperasikan penuh menggunakan **Remote TV** dan **USB RFID Reader**.

### Fitur Aplikasi Native TV:
1. **Layar Tampilan 24/7 Kiosk**: Layar dijaga tetap aktif tanpa sleep (*Keep Screen On*).
2. **Papan Antrian Meja Ustadz**: Menampilkan nomor antrian santri yang sedang mengaji dan daftar tunggu berdampingan.
3. **Panggilan Suara Santri**: Suara TTS natural Indonesia dan bel ding-dong bandara otomatis saat ustadz menekan tombol panggil.
4. **Cetak Tiket Antrian**: Otomatis cetak nomor antrian santri dengan printer bluetooth thermal.

### Cara Menghubungkan TV ke Server:
1. Pasang file APK ke Android TV / STB.
2. Buka aplikasi di TV.
3. Tekan tombol **MENU** atau **SETTINGS** pada Remote TV (atau klik ikon gear di sudut atas).
4. Masukkan URL server Anda (contoh: `http://192.168.1.100:3000` atau domain online `https://almuhajirin.ujunggebang.online`).
5. Klik **Tes Koneksi**, kemudian klik **Simpan & Terapkan**.

### Cara Setting Printer Bluetooth Thermal:
1. Hidupkan printer Bluetooth Thermal Anda.
2. Buka menu **Pengaturan TV $\to$ Remot & Aksesori $\to$ Tambahkan Aksesori**, lalu lakukan *Pairing* ke printer Anda (PIN bawaan biasanya `0000` atau `1234`).
3. Di dalam aplikasi TV, buka menu pengaturan (tombol Remote TV).
4. Aktifkan saklar **Printer Bluetooth Thermal (58mm)**.
5. Pilih nama printer Anda dari daftar dropdown (atau masukkan MAC address printer secara manual).
6. Tekan tombol **Tes Cetak** untuk menguji printer.
7. Klik **Simpan & Terapkan**.

---

## 📱 Panduan Menghubungkan WhatsApp Gateway

1. Login ke halaman Dashboard Admin (`/dashboard/login`).
2. Masuk ke menu **WhatsApp Gateway** (`/dashboard/whatsapp`).
3. Klik tombol **Hubungkan WhatsApp**.
4. Pindai kode QR yang muncul menggunakan aplikasi WhatsApp di ponsel Anda (**Perangkat Tertaut $\to$ Tautkan Perangkat**).
5. Setelah status berubah menjadi **Tersambung (Online)**, seluruh notifikasi kehadiran santri akan terkirim secara otomatis ke nomor WhatsApp orang tua.

---

## 🔑 Akun Pengguna Bawaan (Default Credentials)

Setelah menjalankan `npm run seed`, Anda dapat login menggunakan akun:

| Role / Jabatan | Username | Password | Wewenang |
| :--- | :--- | :--- | :--- |
| **Administrator Utama** | `admin` | `admin123` | Akses penuh seluruh sistem, CMS profil, siswa, dan guru |
| **Ustadz / Guru Pengampu** | `guru1` | `guru123` | Panel antrian meja mengaji (`/queue/desk`) & absensi |
| **Operator / Petugas** | `operator` | `operator123` | Manajemen presensi & operasional harian |

> ⚠️ **PENTING**: Segera ganti password bawaan setelah login pertama kali di menu Pengaturan Akun.

---

## 🌐 Akses URL Cepat

| Halaman | URL | Keterangan |
| :--- | :--- | :--- |
| **Website Profil Lembaga** | `/` | Profil sekolah/majlis ta'lim & cek mandiri santri |
| **Layar Kiosk Presensi Web** | `/kiosk` | Halaman scanner RFID/QR di browser |
| **Display Antrian TV Web** | `/queue/display` | Tampilan papan antrian meja mengaji santri |
| **Panel Panggilan Ustadz** | `/queue/desk` | Panel kontrol guru/ustadz untuk memanggil antrian |
| **Dashboard Admin** | `/dashboard` | Pusat kendali data santri, guru, jadwal, dan laporan |
| **API Data Display TV** | `/api/display-data` | Endpoint JSON sinkronisasi display native TV |
| **API Scan Presensi** | `/api/attendance/scan` | Endpoint POST penerima data kartu RFID |

---

## 📁 Struktur Direktori Proyek

```text
absen-digital/
├── android-app/             # Source code aplikasi native Android TV (Kotlin)
│   ├── app/
│   │   ├── src/main/java/   # Logika native: RFID listener, TTS, Bluetooth Printer
│   │   └── src/main/res/    # Layout XML TV, dialog settings, assets
├── data/                    # Database SQLite WAL & file uploads
│   ├── database.sqlite      # File database utama
│   └── uploads/             # Foto santri, pengajar, logo lembaga
├── public/                  # Asset web frontend statis (CSS, JS, Fonts, Icons)
│   ├── css/
│   └── js/
├── src/                     # Backend Node.js Express
│   ├── config/              # Konfigurasi aplikasi
│   ├── database/            # Skema tabel SQLite & seed data
│   ├── middleware/          # Autentikasi sesi & hak akses role
│   ├── routes/              # Routing URL Express
│   │   ├── attendance.js    # Presensi RFID & scanner
│   │   ├── queue.js         # Sistem antrian mengaji
│   │   ├── displayApi.js    # Sinkronisasi data display TV
│   │   └── dashboard.js     # Manajemen CMS & database
│   ├── services/            # Service logic: WhatsApp, TTS, Antrian
│   └── views/               # Template tampilan EJS
├── ecosystem.config.js      # Konfigurasi deployment PM2
├── env-example.txt          # Template file environment
├── package.json             # Manifest paket Node.js
└── README.md                # Dokumentasi sistem
```

---

## 👨‍💻 Pengembang & Bantuan Teknis

Dikembangkan dan dipelihara secara resmi oleh:
* **Pengembang**: **ALIJAYA-NET**
* **Repositori GitHub**: [alijayanet/absen-digital](https://github.com/alijayanet/absen-digital)
* **Kontak WhatsApp**: [081947215703](https://wa.me/6281947215703)
* **Website**: [alijaya.net](https://alijaya.net)

Jika Anda memiliki pertanyaan seputar instalasi server, implementasi Android TV, atau konfigurasi perangkat keras RFID dan printer bluetooth, jangan ragu untuk menghubungi kontak di atas!