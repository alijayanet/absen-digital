# -*- coding: utf-8 -*-
"""
scripts/generate_presentation.py
Generates a modern, professional, 16:9 widescreen PowerPoint presentation (.pptx)
showcasing all features of the Sistem Informasi Sekolah Terpadu.
"""

import sys
import os
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE

def create_presentation():
    prs = Presentation()
    # 16:9 Widescreen dimensions
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)
    blank_layout = prs.slide_layouts[6] # completely blank layout

    # Color Palette
    C_NAVY_DARK  = RGBColor(15, 23, 42)    # #0F172A Slate 900
    C_NAVY_BLUE  = RGBColor(30, 41, 59)    # #1E293B Slate 800
    C_BLUE       = RGBColor(37, 99, 235)   # #2563EB Blue 600
    C_LIGHT_BLUE = RGBColor(239, 246, 255) # #EFF6FF Blue 50
    C_EMERALD    = RGBColor(16, 185, 129)  # #10B981 Emerald 500
    C_EMERALD_DK = RGBColor(5, 150, 105)   # #059669 Emerald 600
    C_EMERALD_LT = RGBColor(236, 253, 245) # #ECFDF5 Emerald 50
    C_AMBER      = RGBColor(217, 119, 6)   # #D97706 Amber 600
    C_AMBER_LT   = RGBColor(254, 243, 199) # #FEF3C7 Amber 100
    C_PURPLE     = RGBColor(124, 58, 237)  # #7C3AED Purple 600
    C_PURPLE_LT  = RGBColor(245, 243, 255) # #F5F3FF Purple 50
    C_SLATE_TXT  = RGBColor(51, 65, 85)    # #334155 Slate 700
    C_MUTED_TXT  = RGBColor(100, 116, 139) # #64748B Slate 500
    C_WHITE      = RGBColor(255, 255, 255)
    C_BG_PAGE    = RGBColor(248, 250, 252) # #F8FAFC Slate 50
    C_CARD_BG    = RGBColor(255, 255, 255)
    C_BORDER     = RGBColor(226, 232, 240) # #E2E8F0 Slate 200

    def add_page_bg(slide, dark=False):
        bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
        bg.fill.solid()
        bg.fill.fore_color.rgb = C_NAVY_DARK if dark else C_BG_PAGE
        bg.line.fill.background() # no line

    def add_header(slide, category, title, subtitle=None):
        # Category tag
        cat_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.45), Inches(11.7), Inches(0.35))
        tf_cat = cat_box.text_frame
        tf_cat.word_wrap = True
        tf_cat.margin_left = tf_cat.margin_top = tf_cat.margin_right = tf_cat.margin_bottom = 0
        p_cat = tf_cat.paragraphs[0]
        p_cat.text = category.upper()
        p_cat.font.size = Pt(10)
        p_cat.font.bold = True
        p_cat.font.color.rgb = C_BLUE

        # Title
        title_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.8), Inches(11.7), Inches(0.6))
        tf_t = title_box.text_frame
        tf_t.word_wrap = True
        tf_t.margin_left = tf_t.margin_top = tf_t.margin_right = tf_t.margin_bottom = 0
        p_t = tf_t.paragraphs[0]
        p_t.text = title
        p_t.font.size = Pt(22)
        p_t.font.bold = True
        p_t.font.color.rgb = C_NAVY_DARK

        if subtitle:
            sub_box = slide.shapes.add_textbox(Inches(0.8), Inches(1.42), Inches(11.7), Inches(0.4))
            tf_s = sub_box.text_frame
            tf_s.word_wrap = True
            tf_s.margin_left = tf_s.margin_top = tf_s.margin_right = tf_s.margin_bottom = 0
            p_s = tf_s.paragraphs[0]
            p_s.text = subtitle
            p_s.font.size = Pt(12)
            p_s.font.color.rgb = C_MUTED_TXT

    def add_card(slide, left, top, width, height, title, items, bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK):
        card = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
        card.fill.solid()
        card.fill.fore_color.rgb = bg_color
        card.line.color.rgb = border_color
        card.line.width = Pt(1.2)

        tb = slide.shapes.add_textbox(left + Inches(0.25), top + Inches(0.25), width - Inches(0.5), height - Inches(0.5))
        tf = tb.text_frame
        tf.word_wrap = True
        tf.margin_left = tf.margin_top = tf.margin_right = tf.margin_bottom = 0

        p_head = tf.paragraphs[0]
        p_head.text = title
        p_head.font.size = Pt(15)
        p_head.font.bold = True
        p_head.font.color.rgb = header_color
        p_head.space_after = Pt(10)

        for item in items:
            p = tf.add_paragraph()
            p.text = f"• {item}"
            p.font.size = Pt(11)
            p.font.color.rgb = C_SLATE_TXT
            p.space_after = Pt(6)

    # ==========================================
    # SLIDE 1: COVER / TITLE SLIDE (Dark Navy)
    # ==========================================
    s1 = prs.slides.add_slide(blank_layout)
    add_page_bg(s1, dark=True)

    # Accent decorative shape
    deco = s1.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), Inches(1.5), Inches(0.6), Inches(0.1))
    deco.fill.solid()
    deco.fill.fore_color.rgb = C_EMERALD
    deco.line.fill.background()

    # Title & Subtitle Box
    tbox = s1.shapes.add_textbox(Inches(0.8), Inches(1.8), Inches(11.5), Inches(3.2))
    tf1 = tbox.text_frame
    tf1.word_wrap = True

    p0 = tf1.paragraphs[0]
    p0.text = "SISTEM MANAJEMEN SEKOLAH MODERN"
    p0.font.size = Pt(34)
    p0.font.bold = True
    p0.font.color.rgb = C_WHITE

    p1 = tf1.add_paragraph()
    p1.text = "Presensi Digital IoT • Administrasi Keuangan SPP & QRIS Dinamis • Tabungan Siswa • WhatsApp Gateway"
    p1.font.size = Pt(18)
    p1.font.color.rgb = C_EMERALD
    p1.space_before = Pt(12)

    p2 = tf1.add_paragraph()
    p2.text = "Panduan Presentasi Lengkap Fitur, Arsitektur, dan Cara Penggunaan Sistem"
    p2.font.size = Pt(14)
    p2.font.color.rgb = C_MUTED_TXT
    p2.space_before = Pt(8)

    # 3 Badges at bottom
    badges = [
        ("📡 IoT & Kiosk Presensi", "RFID & Dual Camera QR Scanner dengan suara & notifikasi real-time"),
        ("💳 QRIS Dinamis & SPP", "EMVCo TLV + Kode Unik, Webhook Bank Otomatis & Kuitansi Cetak"),
        ("🪙 Tabungan Digital & WA", "Buku Tabungan Per Kelas dikelola Guru & Bot Interaktif WhatsApp")
    ]
    for idx, (b_title, b_desc) in enumerate(badges):
        b_left = Inches(0.8 + idx * 3.9)
        b_top = Inches(5.2)
        b_shape = s1.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, b_left, b_top, Inches(3.7), Inches(1.5))
        b_shape.fill.solid()
        b_shape.fill.fore_color.rgb = C_NAVY_BLUE
        b_shape.line.color.rgb = C_BLUE
        b_shape.line.width = Pt(1)

        btb = s1.shapes.add_textbox(b_left + Inches(0.2), b_top + Inches(0.2), Inches(3.3), Inches(1.1))
        btf = btb.text_frame
        btf.word_wrap = True
        bp0 = btf.paragraphs[0]
        bp0.text = b_title
        bp0.font.size = Pt(13)
        bp0.font.bold = True
        bp0.font.color.rgb = C_WHITE
        bp1 = btf.add_paragraph()
        bp1.text = b_desc
        bp1.font.size = Pt(10)
        bp1.font.color.rgb = C_MUTED_TXT
        bp1.space_before = Pt(4)

    # ==========================================
    # SLIDE 2: RINGKASAN EKSEKUTIF & ARSITEKTUR SOLUSI
    # ==========================================
    s2 = prs.slides.add_slide(blank_layout)
    add_page_bg(s2)
    add_header(s2, "Gambaran Umum", "Arsitektur & Nilai Unggulan Sistem Sekolah", "Solusi terpadu modern tanpa setup database yang rumit, dirancang untuk efisiensi instansi pendidikan.")

    add_card(s2, Inches(0.8), Inches(2.0), Inches(3.7), Inches(4.8), 
             "🚀 Performa & Zero Setup", [
                 "Menggunakan Node.js Express + SQLite WAL Mode (Write-Ahead Logging) super cepat.",
                 "Tanpa instalasi server database berat (MySQL/Postgres) - cukup 1 file database portable.",
                 "Dapat berjalan lancar di PC Standalone, LAN Sekolah, maupun VPS Cloud.",
                 "Dukungan PM2 Daemon Service: aplikasi tetap bekerja meski terminal console ditutup."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK)

    add_card(s2, Inches(4.8), Inches(2.0), Inches(3.7), Inches(4.8), 
             "📲 Ekosistem Komunikasi 2-Arah", [
                 "WhatsApp Gateway terintegrasi penuh menggunakan library resmi Baileys v6.",
                 "Mekanisme Onboarding 2-Arah (Simpan Kontak & Balas 'YA') anti-banned nomor bot.",
                 "Message Queue Worker cerdas dengan jeda anti-spam (1.5 - 3 detik per pesan).",
                 "Bot AI Interaktif melayani pertanyaan presensi, SPP, mutasi tabungan, & jadwal 24/7."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_BLUE)

    add_card(s2, Inches(8.8), Inches(2.0), Inches(3.7), Inches(4.8), 
             "⚖️ Tata Kelola Berintegritas", [
                 "Pemisahan peran ketat (RBAC): Administrator, Guru/Wali Kelas, dan Petugas TU.",
                 "Keuangan SPP transparan dengan QRIS Dinamis & rekonsiliasi otomatis 0 detik.",
                 "Tabungan Siswa dikelola langsung Guru Rombel: aman, transparan, & tanpa manipulasi.",
                 "Orang tua murid mendapatkan notifikasi instan untuk presensi, pembayaran, & tabungan."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_EMERALD_DK)

    # ==========================================
    # SLIDE 3: MODUL PRESENSI & KIOSK GERBANG
    # ==========================================
    s3 = prs.slides.add_slide(blank_layout)
    add_page_bg(s3)
    add_header(s3, "Modul Presensi Siswa", "Layar Kiosk Gerbang Sekolah & Dual Scanner IoT", "Mendukung alur masuk cepat pagi hari tanpa antrean panjang di gerbang sekolah.")

    add_card(s3, Inches(0.8), Inches(2.0), Inches(5.6), Inches(4.8), 
             "🛠️ Fitur Kiosk Gerbang (/kiosk)", [
                 "Single Tap In: Siswa cukup tap 1x tanpa repot memilih masuk/pulang.",
                 "Dual Scanner Hardware: RFID USB Keyboard Wedge & QR Camera Webcam/HP.",
                 "Mobile-Friendly Kiosk: Balik kamera depan/belakang, pilih lensa, & tombol ambil foto native HP (capture environment 100% kompatibel).",
                 "Audio FX Synthesizer: Nada chime merdu saat hadir tepat waktu, warning saat telat, dan buzz jika kartu tidak terdaftar.",
                 "Popup Real-Time: Foto siswa, nama, kelas, jam tap, dan badge status tampil 3.5 detik.",
                 "Anti-Double Scan: Mencegah kecurangan atau tap ganda di hari yang sama."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK)

    add_card(s3, Inches(6.8), Inches(2.0), Inches(5.7), Inches(4.8), 
             "📋 Cara Penggunaan Kiosk Gerbang", [
                 "1. Buka URL http://localhost:3000/kiosk pada monitor/tablet gerbang.",
                 "2. Hubungkan Barcode Scanner atau RFID Reader ke port USB komputer.",
                 "3. Jika menggunakan smartphone, akses IP LAN komputer atau tunnel HTTPS.",
                 "4. Siswa mendekatkan kartu pelajar CR80 ke scanner atau kamera.",
                 "5. Sistem otomatis memvalidasi kartu, membunyikan audio, dan menampilkan profil siswa.",
                 "6. Pada detik yang sama, notifikasi otomatis terkirim ke WhatsApp Wali Murid.",
                 "7. Jam masuk & toleransi terlambat dapat diatur fleksibel di CMS Dashboard."
             ], bg_color=C_LIGHT_BLUE, border_color=C_BLUE, header_color=C_BLUE)

    # ==========================================
    # SLIDE 4: PORTAL SEKOLAH & CEK KEHADIRAN MANDIRI
    # ==========================================
    s4 = prs.slides.add_slide(blank_layout)
    add_page_bg(s4)
    add_header(s4, "Portal Publik & CMS", "Website Profil Sekolah & Cek Kehadiran Mandiri", "Halaman publik modern dan transparan untuk branding institusi dan layanan wali murid.")

    add_card(s4, Inches(0.8), Inches(2.0), Inches(5.6), Inches(4.8), 
             "🌐 Website Profil Sekolah (/)", [
                 "Hero Banner Interaktif: Galeri foto sekolah dengan efek transparan profesional.",
                 "Sambutan Kepala Sekolah lengkap dengan foto resmi dan visi-misi.",
                 "Fasilitas & Program Unggulan: Lab Komputer, Perpustakaan, Ekstrakurikuler Robotika.",
                 "Agenda Kegiatan & Pengumuman Sekolah dinamis yang mudah diperbarui via CMS.",
                 "Galeri Foto Kegiatan & Prestasi Siswa interaktif.",
                 "Kontak Lengkap: Alamat, Akreditasi, WhatsApp, Email, dan Telepon resmi sekolah."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK)

    add_card(s4, Inches(6.8), Inches(2.0), Inches(5.7), Inches(4.8), 
             "🔍 Fitur Cek Kehadiran Mandiri (#cek-kehadiran)", [
                 "Pencarian Mudah: Wali murid cukup memasukkan NIS atau NISN ananda.",
                 "Kartu Profil Siswa Lengkap: Menampilkan foto, nama, NIS, kelas, dan kontak wali kelas.",
                 "✨ Saldo Tabungan Siswa: Nominal saldo tabungan terkini langsung terlihat di kartu siswa bersama nama Wali Kelas penanggung jawab!",
                 "Statistik Kehadiran Bulanan: Jumlah Hadir, Terlambat, Izin, Sakit, Alpa, dan Persentase.",
                 "Kalender Presensi Interaktif: Riwayat kehadiran siswa hari demi hari.",
                 "Tombol Izin/Sakit Mandiri: Akses instan untuk mengajukan surat permohonan online."
             ], bg_color=C_EMERALD_LT, border_color=C_EMERALD, header_color=C_EMERALD_DK)

    # ==========================================
    # SLIDE 5: PENGAJUAN IZIN & SAKIT MANDIRI ONLINE
    # ==========================================
    s5 = prs.slides.add_slide(blank_layout)
    add_page_bg(s5)
    add_header(s5, "Layanan Digital Orang Tua", "Pengajuan Izin & Sakit Mandiri Berbasis Web", "Memudahkan orang tua mengajukan permohonan tanpa surat fisik yang mudah tercecer.")

    add_card(s5, Inches(0.8), Inches(2.0), Inches(5.6), Inches(4.8), 
             "📝 Fitur & Mekanisme Pengajuan", [
                 "Formulir Mandiri: Dapat diakses dari beranda sekolah tanpa perlu login akun.",
                 "Pilihan Kategori: Izin keperluan keluarga atau Sakit.",
                 "Rentang Tanggal Fleksibel: Pengajuan bisa 1 hari maupun beberapa hari.",
                 "Upload Bukti Dokter / Surat: Mendukung upload foto surat dokter (JPG/PNG).",
                 "🔒 Verifikasi Keamanan 4 Digit WhatsApp: Sistem memvalidasi 4 digit terakhir nomor telepon orang tua yang terdaftar untuk mencegah manipulasi orang luar.",
                 "Notifikasi Persetujuan Otomatis: Wali murid menerima notifikasi WhatsApp saat izin disetujui atau ditolak oleh Wali Kelas."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK)

    add_card(s5, Inches(6.8), Inches(2.0), Inches(5.7), Inches(4.8), 
             "🔄 Alur Kerja Persetujuan (Workflow)", [
                 "1. Orang tua buka website sekolah -> Cek Kehadiran -> Masukkan NIS/NISN.",
                 "2. Klik tombol [+ Ajukan Surat Izin / Sakit].",
                 "3. Isi alasan, tanggal, unggah foto bukti, dan masukkan 4 digit nomor WA.",
                 "4. Data permohonan masuk ke Dashboard Wali Kelas & Guru bersangkutan.",
                 "5. Guru memeriksa dokumen bukti surat dan klik [Setujui] atau [Tolak].",
                 "6. Status presensi siswa pada rentang tanggal otomatis tercatat 'IZIN' / 'SAKIT'.",
                 "7. Pesan konfirmasi otomatis dikirimkan ke WhatsApp Wali Murid."
             ], bg_color=C_PURPLE_LT, border_color=C_PURPLE, header_color=C_PURPLE)

    # ==========================================
    # SLIDE 6: KEUANGAN SEKOLAH & MANAJEMEN SPP
    # ==========================================
    s6 = prs.slides.add_slide(blank_layout)
    add_page_bg(s6)
    add_header(s6, "Administrasi Finansial", "Manajemen Tagihan SPP & Kasir Pembayaran Tunai", "Sistem penagihan multi-kategori fleksibel dengan audit trail pembayaran lengkap.")

    add_card(s6, Inches(0.8), Inches(2.0), Inches(5.6), Inches(4.8), 
             "💰 Kelola Kategori Biaya & Tagihan", [
                 "CRUD Kategori Biaya Lengkap: Buat kategori SPP, Uang Gedung, Seragam, Ujian, dll.",
                 "Tipe Periode Tagihan: Bulanan, Mingguan, Sekali Bayar (Insidental), atau Kustom.",
                 "Terbitkan Tagihan Massal: Terbitkan tagihan SPP bulanan ke seluruh siswa sekolah atau per rombel kelas dalam 1 klik.",
                 "Tagihan Khusus Satuan: Terbitkan tagihan perseorangan dengan nominal bebas.",
                 "Filter Fleksibel: Filter berdasarkan status (UNPAID, PAID, CANCELLED), kelas, kategori biaya, dan periode bulan.",
                 "Batal & Edit Tagihan: Membatalkan atau merevisi tagihan yang belum dibayar."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK)

    add_card(s6, Inches(6.8), Inches(2.0), Inches(5.7), Inches(4.8), 
             "🧾 Pembayaran Kasir TU & Cetak Kuitansi", [
                 "Bayar Tunai di Kasir TU: Petugas TU dapat mencatat pembayaran tunai langsung di modal kasir cepat.",
                 "Cetak Kuitansi Resmi (/dashboard/finance/receipts/:no/print): Layout kuitansi siap cetak dengan kop sekolah, watermark LUNAS, rincian biaya, kode unik, dan tanda tangan kasir.",
                 "Notifikasi Kuitansi Otomatis: Setiap pembayaran tunai yang dicatat TU otomatis mengirimkan notifikasi kuitansi pelunasan ke WhatsApp orang tua.",
                 "Audit Trail Lengkap: Setiap transaksi tercatat nomor kuitansi, petugas kasir, saluran pembayaran, tanggal, dan catatan referensi."
             ], bg_color=C_AMBER_LT, border_color=C_AMBER, header_color=C_AMBER)

    # ==========================================
    # SLIDE 7: QRIS DINAMIS & WEBHOOK REKONSILIASI
    # ==========================================
    s7 = prs.slides.add_slide(blank_layout)
    add_page_bg(s7)
    add_header(s7, "Inovasi Pembayaran Digital", "QRIS Statis-ke-Dinamis EMVCo & Webhook Otomatis", "Orang tua membayar dengan nominal tepat, sistem memverifikasi dan melunasi invoice dalam 0 detik.")

    add_card(s7, Inches(0.8), Inches(2.0), Inches(5.6), Inches(4.8), 
             "⚡ Algoritma QRIS Dinamis EMVCo TLV", [
                 "Adaptasi Standar EMVCo TLV (Tag-Length-Value) Merchant Presented Mode.",
                 "Mengubah Tag 01 dari '11' (Statis) menjadi '12' (Dinamis).",
                 "Menyisipkan Tag 54 (Transaction Amount) dengan nominal tagihan tepat.",
                 "Menghitung ulang Checksum CRC16 CCITT False (0x1021, init 0xFFFF).",
                 "Menghasilkan Barcode QRIS dalam format Data URL web & PNG Buffer kirim WhatsApp.",
                 "Fitur Auto-Decode QRIS: Upload foto QRIS sekolah, sistem otomatis mengekstrak string EMVCo dan nama merchant via jsQR tanpa input manual!"
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK)

    add_card(s7, Inches(6.8), Inches(2.0), Inches(5.7), Inches(4.8), 
             "🔄 Kode Unik & Webhook Notifikasi Bank", [
                 "Algoritma Kode Unik Anti-Bentrok: Sistem menambahkan 3 digit unik (100-998) yang dijamin berbeda antar tagihan UNPAID.",
                 "Integrasi MacroDroid / SMS Forwarder: HP sekolah menerima notifikasi push m-Banking/E-Wallet dan meneruskannya ke Webhook API.",
                 "Regex Parsing Cerdas: Mengekstrak nominal transfer otomatis dari teks SMS/push notifikasi bank (BCA, BRI, Mandiri, Dana, dll).",
                 "Verifikasi 0 Detik: Tagihan otomatis berstatus PAID, kuitansi resmi dibuat, dan notifikasi konfirmasi terkirim ke WA wali murid.",
                 "Dilindungi Secret Key API untuk mencegah manipulasi data."
             ], bg_color=C_LIGHT_BLUE, border_color=C_BLUE, header_color=C_BLUE)

    # ==========================================
    # SLIDE 8: TABUNGAN SISWA DIGITAL
    # ==========================================
    s8 = prs.slides.add_slide(blank_layout)
    add_page_bg(s8)
    add_header(s8, "Edukasi Finansial Siswa", "Modul Buku Tabungan Siswa Dikelola Guru", "Mendidik budaya gemar menabung secara digital, transparan, dan dapat dipantau orang tua.")

    add_card(s8, Inches(0.8), Inches(2.0), Inches(5.6), Inches(4.8), 
             "🪙 Wewenang & Tata Kelola Guru Rombel", [
                 "Wewenang Eksklusif Guru: Pengelolaan setoran dan penarikan tabungan adalah wewenang penuh Guru / Wali Kelas masing-masing.",
                 "Proteksi Admin: Admin hanya melihat rekapitulasi dana di tiap guru/rombel. Tombol Setor & Tarik dihilangkan untuk Admin guna mencegah salah kelola.",
                 "Setor & Tarik via Dashboard Web: Guru mencatat mutasi dengan nominal instan atau kustom di menu Tabungan Siswa.",
                 "Ikon Koin Emas (coins) yang elegan dan bernilai islami/edukatif menggantikan celengan lama.",
                 "Audit Mutasi Transaksi: Mencatat tanggal, jenis transaksi, nominal, sisa saldo, dan catatan keperluan."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK)

    add_card(s8, Inches(6.8), Inches(2.0), Inches(5.7), Inches(4.8), 
             "📲 Pencatatan Cepat via WhatsApp Bot", [
                 "Guru Tambah Tabungan via WA: Cukup ketik:\n👉 SETOR <NAMA/NIS> <JUMLAH> [CATATAN]\nContoh: SETOR Ahmad 20rb Uang kas jumat",
                 "Guru Catat Penarikan via WA: Cukup ketik:\n👉 TARIK <NAMA/NIS> <JUMLAH> [CATATAN]\nContoh: TARIK Ahmad 10000 Buku gambar",
                 "Notifikasi Otomatis ke WhatsApp Orang Tua: Setiap setoran dicatat, orang tua menerima rincian saldo baru & nama guru penanggung jawab!",
                 "Validasi Saldo: Sistem menolak penarikan jika saldo tabungan siswa tidak mencukupi."
             ], bg_color=C_EMERALD_LT, border_color=C_EMERALD, header_color=C_EMERALD_DK)

    # ==========================================
    # SLIDE 9: BOT WA UNTUK ORANG TUA
    # ==========================================
    s9 = prs.slides.add_slide(blank_layout)
    add_page_bg(s9)
    add_header(s9, "WhatsApp Gateway Interaktif", "Panduan Perintah Bot untuk Orang Tua Murid", "Layanan mandiri 24 jam di aplikasi WhatsApp tanpa perlu menginstal aplikasi tambahan.")

    add_card(s9, Inches(0.8), Inches(2.0), Inches(5.6), Inches(4.8), 
             "👨‍👩‍👧 Perintah Presensi & Tagihan SPP", [
                 "• CEK / PRESENSI:\nCek rekap kehadiran ananda hari ini, persentase bulanan, dan 5 riwayat kehadiran terakhir. (Multi-anak otomatis didukung: CEK 1 / CEK 2).",
                 "• SPP / TAGIHAN:\nMenampilkan daftar tagihan biaya sekolah belum lunas, total kewajiban, jatuh tempo, serta riwayat tagihan lunas.",
                 "• BAYAR <ID>:\nBot langsung mengirimkan GAMBAR BARCODE QRIS DINAMIS ber-nominal tepat ke chat WA! Orang tua cukup scan barcode untuk bayar via m-Banking atau E-Wallet apa saja."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK)

    add_card(s9, Inches(6.8), Inches(2.0), Inches(5.7), Inches(4.8), 
             "📖 Perintah Tabungan & Informasi Sekolah", [
                 "• TABUNGAN:\nCek buku tabungan digital ananda (Total saldo saat ini, statistik setoran/penarikan, 5 mutasi terakhir, dan nama serta kontak WA Wali Kelas penanggung jawab).",
                 "• JADWAL:\nInfo jam masuk, toleransi gerbang, hari aktif, & tata tertib.",
                 "• WALI:\nMenampilkan nama & kontak WA Wali Kelas (link wa.me).",
                 "• IZIN:\nPanduan pengajuan izin sakit online via website sekolah.",
                 "• INFO:\nProfil sekolah, akreditasi, alamat, email, & telepon.",
                 "• MENU:\nMenampilkan daftar seluruh perintah bantuan."
             ], bg_color=C_LIGHT_BLUE, border_color=C_BLUE, header_color=C_BLUE)

    # ==========================================
    # SLIDE 10: BOT WA UNTUK GURU & WALI KELAS
    # ==========================================
    s10 = prs.slides.add_slide(blank_layout)
    add_page_bg(s10)
    add_header(s10, "WhatsApp Gateway Interaktif", "Panduan Perintah Bot Khusus Guru & Wali Kelas", "Asisten cerdas bagi Wali Kelas untuk mengelola kelas dan keuangan siswa langsung dari WhatsApp.")

    add_card(s10, Inches(0.8), Inches(2.0), Inches(5.6), Inches(4.8), 
             "🪙 Perintah Tabungan Siswa via WA", [
                 "• TABUNGAN / TOTAL TABUNGAN:\nRekapitulasi total dana tabungan yang dipegang guru, persentase penabung aktif, total setoran/penarikan, dan daftar saldo seluruh siswa di rombel kelasnya.",
                 "• TABUNGAN <NAMA/NIS> (Contoh: TABUNGAN Ahmad):\nCek buku tabungan digital siswa tertentu. Bot cerdas mencari nama murid di rombel guru bersangkutan!",
                 "• SETOR <NAMA/NIS> <JUMLAH> (Contoh: SETOR Ahmad 20rb):\nTambah saldo tabungan siswa langsung dari WA. Notifikasi otomatis diteruskan ke nomor orang tua.",
                 "• TARIK <NAMA/NIS> <JUMLAH> (Contoh: TARIK 1001 10rb):\nCatat penarikan tabungan siswa dengan validasi sisa saldo."
             ], bg_color=C_CARD_BG, border_color=C_BORDER, header_color=C_NAVY_DARK)

    add_card(s10, Inches(6.8), Inches(2.0), Inches(5.7), Inches(4.8), 
             "📋 Perintah Presensi & Manajemen Kelas", [
                 "• REKAP / URUTAN:\nMembuat dan menampilkan rekap urutan kehadiran kronologis siswa di kelas binaan pada hari ini secara instan.",
                 "• BELUM / ALPA:\nMenampilkan daftar seluruh siswa kelas binaan yang belum melakukan tap presensi hari ini beserta kontak WA orang tuanya.",
                 "• Proteksi Otorisasi:\nPerintah khusus guru dilindungi validasi nomor telepon di database. Pengguna selain guru tidak dapat menjalankan perintah ini.",
                 "• Notifikasi Guru Otomatis:\nGuru menerima notifikasi saat ada siswa kelasnya yang mengajukan surat izin sakit online."
             ], bg_color=C_EMERALD_LT, border_color=C_EMERALD, header_color=C_EMERALD_DK)

    # ==========================================
    # SLIDE 11: PERAN & HAK AKSES SISTEM (RBAC)
    # ==========================================
    s11 = prs.slides.add_slide(blank_layout)
    add_page_bg(s11)
    add_header(s11, "Keamanan & Wewenang", "Matriks Hak Akses Pengguna (Role-Based Access Control)", "Menjaga integritas data sekolah dengan membatasi wewenang sesuai tugas pokok masing-masing.")

    # Table of RBAC
    table_shape = s11.shapes.add_table(5, 5, Inches(0.8), Inches(2.1), Inches(11.7), Inches(4.6))
    table = table_shape.table
    table.columns[0].width = Inches(2.7)
    table.columns[1].width = Inches(2.25)
    table.columns[2].width = Inches(2.25)
    table.columns[3].width = Inches(2.25)
    table.columns[4].width = Inches(2.25)

    headers = ["Modul / Fitur Sistem", "Administrator", "Guru / Wali Kelas", "Petugas Kasir TU", "Orang Tua Murid"]
    for i, h in enumerate(headers):
        cell = table.cell(0, i)
        cell.text = h
        cell.fill.solid()
        cell.fill.fore_color.rgb = C_NAVY_DARK
        for p in cell.text_frame.paragraphs:
            p.font.size = Pt(11)
            p.font.bold = True
            p.font.color.rgb = C_WHITE
            p.alignment = PP_ALIGN.CENTER

    data = [
        ["Presensi & Kiosk Gerbang", "Full Akses & Setting Jam", "Monitor Rombel & Rekap", "Lihat Layar Kiosk", "Cek Kehadiran Mandiri"],
        ["Pengajuan Izin & Sakit", "Full Akses Rekap", "Verifikasi & Approve Rombel", "Lihat Rekap", "Mengajukan Surat Mandiri"],
        ["Keuangan SPP & Kuitansi", "Kelola Tagihan & QRIS", "Lihat Status Siswa Rombel", "Catat Kasir & Cetak Kuitansi", "Cek Tagihan & Bayar QRIS"],
        ["Tabungan Siswa Digital", "Lihat Grand Total Sekolah", "Pegang Kas Rombel, Setor & Tarik", "Tidak Memiliki Akses", "Cek Saldo & Mutasi Anak"]
    ]

    for row_idx, row_data in enumerate(data):
        for col_idx, text in enumerate(row_data):
            cell = table.cell(row_idx + 1, col_idx)
            cell.text = text
            cell.fill.solid()
            cell.fill.fore_color.rgb = C_WHITE if row_idx % 2 == 0 else C_BG_PAGE
            for p in cell.text_frame.paragraphs:
                p.font.size = Pt(10)
                p.font.color.rgb = C_SLATE_TXT
                if col_idx == 0:
                    p.font.bold = True
                    p.alignment = PP_ALIGN.LEFT
                else:
                    p.alignment = PP_ALIGN.CENTER

    # ==========================================
    # SLIDE 12: ALUR OPERASIONAL HARIAN (SOP)
    # ==========================================
    s12 = prs.slides.add_slide(blank_layout)
    add_page_bg(s12)
    add_header(s12, "Standar Operasional Prosedur", "Alur Operasional Harian Sekolah Terpadu", "Panduan langkah praktis pemanfaatan sistem dari pagi hari hingga jam pulang sekolah.")

    cards_sop = [
        ("🌅 1. Sesi Pagi (06:30 - 07:30)", [
            "Petugas menyalakan Kiosk Gerbang (/kiosk).",
            "Siswa tap kartu RFID / QR Code.",
            "Audio menyambut kehadiran siswa.",
            "WhatsApp otomatis mengabari orang tua jam tiba ananda.",
            "Guru cek siswa belum hadir via WA (ketik: BELUM)."
        ], C_CARD_BG, C_BORDER, C_NAVY_DARK),
        ("☀️ 2. Sesi Siang (08:00 - 12:00)", [
            "Wali Kelas verifikasi permohonan izin/sakit yang masuk via web.",
            "Siswa menabung ke Guru Kelas saat istirahat.",
            "Guru catat tabungan via web atau WA (SETOR Ahmad 10rb).",
            "Orang tua terima notifikasi tabungan baru.",
            "Kasir TU melayani pembayaran SPP tunai atau pantau QRIS."
        ], C_LIGHT_BLUE, C_BLUE, C_BLUE),
        ("🌇 3. Sesi Sore (13:00 - 16:00)", [
            "Guru cek rekap kehadiran kelas (ketik: REKAP).",
            "Guru cek total dana tabungan dipegangnya (ketik: TOTAL TABUNGAN).",
            "Admin cetak rekapitulasi presensi & kuitansi keuangan bulanan.",
            "Orang tua mengecek status pembayaran SPP (ketik: SPP)."
        ], C_EMERALD_LT, C_EMERALD, C_EMERALD_DK)
    ]

    for idx, (sop_t, sop_items, bg, border, hcolor) in enumerate(cards_sop):
        add_card(s12, Inches(0.8 + idx * 3.9), Inches(2.0), Inches(3.7), Inches(4.8),
                 sop_t, sop_items, bg_color=bg, border_color=border, header_color=hcolor)

    # ==========================================
    # SLIDE 13: PENUTUP & KESIMPULAN
    # ==========================================
    s13 = prs.slides.add_slide(blank_layout)
    add_page_bg(s13, dark=True)

    add_header(s13, "Kesimpulan & Kontak", "Transformasi Digital Sekolah yang Nyata & Efisien", "Kemudahan implementasi, keandalan teknologi, dan keterbukaan komunikasi sekolah.")

    add_card(s13, Inches(0.8), Inches(2.0), Inches(5.6), Inches(4.8), 
             "✨ Mengapa Memilih Sistem Ini?", [
                 "Teruji & All-in-One: Mengintegrasikan Presensi, Keuangan QRIS, Tabungan Siswa, dan WhatsApp Bot dalam 1 ekosistem terpadu.",
                 "Biaya Operasional Rendah: Menggunakan SQLite WAL murni tanpa biaya lisensi database komersial.",
                 "Kecepatan & Responsivitas Tinggi: Respons pesan bot hitungan detik dengan antrean anti-banned.",
                 "Kemitraan Erat Sekolah & Wali Murid: Orang tua selalu terinformasi secara real-time mengenai kehadiran, SPP, dan tabungan ananda.",
                 "Desain Elegan & Siap Pakai: Kartu CR80, kuitansi resmi, dan landing page modern siap digunakan."
             ], bg_color=C_NAVY_BLUE, border_color=C_BLUE, header_color=C_WHITE)

    # Developer & support contact card
    cont_card = s13.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(6.8), Inches(2.0), Inches(5.7), Inches(4.8))
    cont_card.fill.solid()
    cont_card.fill.fore_color.rgb = C_NAVY_BLUE
    cont_card.line.color.rgb = C_EMERALD
    cont_card.line.width = Pt(1.5)

    ctb = s13.shapes.add_textbox(Inches(7.1), Inches(2.3), Inches(5.1), Inches(4.2))
    ctf = ctb.text_frame
    ctf.word_wrap = True
    ctf.margin_left = ctf.margin_top = ctf.margin_right = ctf.margin_bottom = 0

    cp0 = ctf.paragraphs[0]
    cp0.text = "👨‍💻 Pengembang & Layanan Dukungan"
    cp0.font.size = Pt(16)
    cp0.font.bold = True
    cp0.font.color.rgb = C_WHITE
    cp0.space_after = Pt(14)

    contacts = [
        ("Institusi / Pengembang:", "ALIJAYA-NET"),
        ("WhatsApp Dukungan:", "+62 819-4721-5703 (081947215703)"),
        ("Layanan Bantuan:", "• Konsultasi & Instalasi Server Sekolah (LAN / Cloud VPS)"),
        ("", "• Pengadaan Mesin RFID USB & Kartu CR80 PVC"),
        ("", "• Kustomisasi Format Laporan Dinas & Webhook"),
        ("Repository Resmi:", "alijayanet/website-absen-sekolah"),
        ("Lisensi Perangkat Lunak:", "MIT License (Bebas Digunakan & Dikembangkan)")
    ]

    for label, val in contacts:
        p = ctf.add_paragraph()
        if label:
            p.text = f"{label} {val}"
        else:
            p.text = f"   {val}"
        p.font.size = Pt(11)
        p.font.color.rgb = C_EMERALD if "WhatsApp" in label or "ALIJAYA" in val else C_MUTED_TXT
        p.space_after = Pt(6)

    # Save presentation
    output_path = os.path.join(os.getcwd(), "Presentasi_Fitur_Sistem_Sekolah.pptx")
    prs.save(output_path)
    print(f"Presentation successfully created at: {output_path}")

if __name__ == "__main__":
    create_presentation()
