const express = require('express');
const router = express.Router();
const db = require('../database/db');
const queue = require('../services/queue');
const { getTodayWIB, getTimeSecondWIB } = require('../utils/timeHelper');

// Halaman Kiosk Scanner Gerbang Sekolah
router.get('/kiosk', (req, res) => {
  const settingsRows = db.prepare('SELECT key, value FROM settings').all();
  const settings = {};
  for (const row of settingsRows) {
    settings[row.key] = row.value;
  }

  // Jika fitur antrian mengaji aktif dan bukan mode paksa kiosk-only, arahkan ke layar terpadu /display
  if (settings.queue_enabled === '1' && req.query.mode !== 'kiosk_only') {
    return res.redirect('/display');
  }

  res.render('kiosk', { settings });
});

// Endpoint API Pencatatan Absensi (RFID & QR Code)
router.post('/api/attendance/scan', (req, res) => {
  try {
    const rawCode = (req.body.code || '').trim();

    if (!rawCode) {
      return res.status(400).json({
        success: false,
        code: 'EMPTY_CODE',
        message: 'Kode RFID atau QR Code tidak boleh kosong.'
      });
    }

    // Cari data siswa berdasarkan rfid_uid, qr_code_token, nis, atau nisn
    const student = db.prepare(`
      SELECT s.*, c.name as class_name, u.name as teacher_name 
      FROM students s 
      LEFT JOIN classes c ON s.class_id = c.id 
      LEFT JOIN users u ON s.teacher_id = u.id
      WHERE (s.rfid_uid = ? OR s.qr_code_token = ? OR s.nis = ? OR s.nisn = ?) 
        AND s.is_active = 1
    `).get(rawCode, rawCode, rawCode, rawCode);

    if (!student) {
      return res.status(404).json({
        success: false,
        code: 'NOT_FOUND',
        message: 'Kartu atau kode QR tidak terdaftar di sistem.'
      });
    }

    // Waktu hari ini dalam WIB
    const dateStr = getTodayWIB();          // YYYY-MM-DD
    const timeIn = getTimeSecondWIB();       // HH:MM:SS

    // Cek fitur antrian mengaji
    let studentQueue = null;
    const isQueueEnabled = db.prepare("SELECT value FROM settings WHERE key = 'queue_enabled'").get()?.value === '1';
    if (isQueueEnabled && (student.teacher_id || student.class_id)) {
      const queueService = require('../services/queueService');
      studentQueue = queueService.assignQueueNumber(student.id, student.class_id, dateStr, student.teacher_id);
    }

    // Cek apakah siswa sudah melakukan absensi hari ini
    const existing = db.prepare('SELECT * FROM attendances WHERE student_id = ? AND date = ?').get(student.id, dateStr);

    if (existing) {
      if (isQueueEnabled && !studentQueue) {
        studentQueue = db.prepare(`
          SELECT q.*, u.name as teacher_name, c.name as class_name
          FROM daily_queues q
          LEFT JOIN users u ON q.teacher_id = u.id
          LEFT JOIN classes c ON q.class_id = c.id
          WHERE q.student_id = ? AND q.date = ?
        `).get(student.id, dateStr);
      }

      return res.status(200).json({
        success: false,
        code: 'ALREADY_ATTENDED',
        message: `Siswa ${student.name} sudah melakukan absensi hari ini pada pukul ${existing.time_in} WIB.`,
        student: {
          id: student.id,
          name: student.name,
          nis: student.nis,
          gender: student.gender,
          class_name: student.class_name,
          teacher_name: student.teacher_name,
          photo: student.photo
        },
        attendance: existing,
        queue: studentQueue
      });
    }

    // Ambil konfigurasi jam sekolah
    const jamMasukSetting = db.prepare("SELECT value FROM settings WHERE key = 'jam_masuk'").get()?.value || '07:00';
    const toleransiSetting = db.prepare("SELECT value FROM settings WHERE key = 'toleransi_telat'").get()?.value || '07:15';

    // Konversi jam ke total menit
    const [masukH, masukM] = jamMasukSetting.split(':').map(Number);
    const [toleransiH, toleransiM] = toleransiSetting.split(':').map(Number);
    const [timeH, timeM] = timeIn.split(':').map(Number); // gunakan WIB dari timeIn
    const totalMinutesNow = timeH * 60 + timeM;
    const totalMinutesMasuk = masukH * 60 + masukM;
    const totalMinutesToleransi = toleransiH * 60 + toleransiM;

    let status = 'HADIR';
    let lateMinutes = 0;

    if (totalMinutesNow > totalMinutesToleransi) {
      status = 'TERLAMBAT';
      lateMinutes = totalMinutesNow - totalMinutesMasuk;
    }

    // Simpan data absensi
    const insertAttendance = db.prepare(`
      INSERT INTO attendances (student_id, date, time_in, status, late_minutes, created_by)
      VALUES (?, ?, ?, ?, ?, 'KIOSK')
    `);
    const insertResult = insertAttendance.run(student.id, dateStr, timeIn, status, lateMinutes);

    // Kirim notifikasi WhatsApp jika nomor wali murid tersedia
    if (student.parent_phone) {
      const templateSetting = db.prepare("SELECT value FROM settings WHERE key = 'wa_template_absen'").get()?.value;
      const schoolNameSetting = db.prepare("SELECT value FROM settings WHERE key = 'school_name'").get()?.value || 'Sekolah';

      let template = templateSetting || 'Siswa {nama_siswa} telah hadir di sekolah pada {jam} ({status}).';

      // Format tanggal Indonesia
      const now = new Date();
      const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', timeZone: 'Asia/Jakarta' };
      const indoDate = now.toLocaleDateString('id-ID', options);
      const [h, m] = timeIn.split(':');

      const statusIndo = status === 'HADIR' ? 'Hadir Tepat Waktu' : 'Terlambat';
      const ketTelat = status === 'TERLAMBAT' ? `\n⚠️ *Keterlambatan:* ${lateMinutes} menit` : '';
      const teacherInfo = (studentQueue && studentQueue.teacher_name) ? ` (Ustadz ${studentQueue.teacher_name})` : (student.class_name ? ` (${student.class_name})` : '');
      const ketAntrian = studentQueue ? `\n📋 *Nomor Antrian Mengaji:* #${studentQueue.queue_number}${teacherInfo}` : '';

      const message = template
        .replace(/{nama_siswa}/g, student.name)
        .replace(/{kelas}/g, student.class_name || '-')
        .replace(/{tanggal}/g, indoDate)
        .replace(/{jam}/g, `${h}:${m}`)
        .replace(/{status}/g, statusIndo)
        .replace(/{keterangan_telat}/g, ketTelat)
        .replace(/{nomor_antrian}/g, studentQueue ? `#${studentQueue.queue_number}` : '')
        .replace(/{nama_sekolah}/g, schoolNameSetting) + ketAntrian;

      queue.enqueue({
        studentId: student.id,
        phone: student.parent_phone,
        message,
        type: 'ATTENDANCE'
      });
    }

    return res.status(200).json({
      success: true,
      code: 'SUCCESS',
      message: status === 'HADIR' ? 'Absensi Berhasil: Hadir Tepat Waktu' : `Absensi Berhasil: Terlambat ${lateMinutes} Menit`,
      status,
      lateMinutes,
      timeIn,
      student: {
        id: student.id,
        name: student.name,
        nis: student.nis,
        nisn: student.nisn,
        gender: student.gender,
        class_name: student.class_name,
        teacher_name: student.teacher_name,
        photo: student.photo
      },
      queue: studentQueue
    });

  } catch (err) {
    console.error('Error saat proses scan absensi:', err);
    return res.status(500).json({
      success: false,
      code: 'SERVER_ERROR',
      message: 'Terjadi kesalahan sistem saat mencatat absensi.'
    });
  }
});

module.exports = router;
