/**
 * test_monthly_summary.js — Pengujian Fitur Rekapitulasi Presensi Bulanan Guru & Orang Tua
 */

const db = require('../src/database/db');
const bot = require('../src/services/bot');
const { generateMonthlyStudentSummary, generateMonthlyClassSummary, parseMonthParam } = require('../src/services/summary');

async function runTests() {
  console.log('========================================================');
  console.log('🧪 Memulai Pengujian Fitur Rekapitulasi Presensi Bulanan');
  console.log('========================================================\n');

  // Ambil sample guru dan siswa
  const teacher = db.prepare("SELECT * FROM users WHERE role = 'guru' AND class_id IS NOT NULL AND phone IS NOT NULL LIMIT 1").get();
  const student = db.prepare("SELECT * FROM students WHERE class_id = ? AND is_active = 1 LIMIT 1").get(teacher.class_id);

  const teacherPhone = teacher.phone;
  const parentPhone = student.parent_phone || '081234567890';

  console.log(`📌 Konteks Uji:`);
  console.log(`   Guru: ${teacher.name} (${teacherPhone})`);
  console.log(`   Siswa: ${student.name} (NIS: ${student.nis}, Kelas ID: ${student.class_id})`);
  console.log(`   Wali Murid: ${parentPhone}\n`);

  // Pastikan ada beberapa catatan presensi di bulan September 2026
  const upsertAtt = db.prepare(`
    INSERT INTO attendances (student_id, date, time_in, status, late_minutes, notes, created_by)
    VALUES (?, ?, ?, ?, ?, ?, 'TEST')
    ON CONFLICT(student_id, date) DO UPDATE SET status = excluded.status, late_minutes = excluded.late_minutes
  `);
  upsertAtt.run(student.id, '2026-09-01', '07:05:00', 'HADIR', 0, null);
  upsertAtt.run(student.id, '2026-09-02', '07:18:00', 'TERLAMBAT', 3, null);
  upsertAtt.run(student.id, '2026-09-03', '07:00:00', 'SAKIT', 0, 'Demam');

  // -------------------------------------------------------------
  // TEST 1: Helper parseMonthParam
  // -------------------------------------------------------------
  console.log('--- TEST 1: Helper parseMonthParam ---');
  const p1 = parseMonthParam('');
  const p2 = parseMonthParam('agustus');
  const p3 = parseMonthParam('2026-05');
  console.log('Default:', p1);
  console.log('Agustus:', p2);
  console.log('2026-05:', p3);

  if (p2.monthPrefix !== '2026-08' || p3.monthPrefix !== '2026-05') {
    throw new Error('Test 1 Gagal: parseMonthParam tidak menghasilkan format YYYY-MM yang sesuai');
  }
  console.log('✅ TEST 1 Berhasil: Parsing parameter bulan akurat\n');

  // -------------------------------------------------------------
  // TEST 2: Orang Tua Mengetik "BULANAN"
  // -------------------------------------------------------------
  console.log('--- TEST 2: Orang Tua Mengetik "BULANAN" ---');
  const parentSenderJid = `${parentPhone.replace(/\D/g, '')}@s.whatsapp.net`;
  const parentResp = await bot.processIncomingMessage(parentSenderJid, 'BULANAN', parentPhone);
  console.log('Respons ke Orang Tua:\n', parentResp);

  if (!parentResp.includes('REKAPITULASI KEHADIRAN BULANAN') || !parentResp.includes(student.name)) {
    throw new Error('Test 2 Gagal: Respons bulanan orang tua tidak sesuai');
  }
  console.log('✅ TEST 2 Berhasil: Orang tua dapat melihat rekap bulanan ananda\n');

  // -------------------------------------------------------------
  // TEST 3: Guru Mengetik "REKAP BULANAN" (Rekap 1 Kelas)
  // -------------------------------------------------------------
  console.log('--- TEST 3: Guru Mengetik "REKAP BULANAN" ---');
  const teacherSenderJid = `${teacherPhone.replace(/\D/g, '')}@s.whatsapp.net`;
  const classResp = await bot.processIncomingMessage(teacherSenderJid, 'REKAP BULANAN', teacherPhone);
  console.log('Respons ke Guru (Kelas):\n', classResp);

  if (!classResp.includes('REKAP PRESENSI BULANAN KELAS') || !classResp.includes(student.name)) {
    throw new Error('Test 3 Gagal: Rekap kelas bulanan tidak mencantumkan data siswa');
  }
  console.log('✅ TEST 3 Berhasil: Guru dapat melihat rekapitulasi bulanan seluruh rombel kelasnya\n');

  // -------------------------------------------------------------
  // TEST 4: Guru Mengetik "REKAP BULANAN <NIS/NAMA>"
  // -------------------------------------------------------------
  console.log(`--- TEST 4: Guru Mengetik "REKAP BULANAN ${student.nis}" ---`);
  const studentResp = await bot.processIncomingMessage(teacherSenderJid, `REKAP BULANAN ${student.nis}`, teacherPhone);
  console.log('Respons ke Guru (Siswa Spesifik):\n', studentResp);

  if (!studentResp.includes('REKAPITULASI KEHADIRAN BULANAN') || !studentResp.includes(student.name)) {
    throw new Error('Test 4 Gagal: Rincian siswa spesifik tidak sesuai');
  }
  console.log('✅ TEST 4 Berhasil: Guru dapat memeriksa rincian bulanan murid tertentu\n');

  // -------------------------------------------------------------
  // TEST 5: Guru Mengetik "REKAP" (Rekap Harian biasa)
  // -------------------------------------------------------------
  console.log('--- TEST 5: Guru Mengetik "REKAP" (Harian) ---');
  const dailyResp = await bot.processIncomingMessage(teacherSenderJid, 'REKAP', teacherPhone);
  console.log('Respons ke Guru (Harian):\n', dailyResp);

  if (!dailyResp.includes('REKAP URUTAN PRESENSI SISWA')) {
    throw new Error('Test 5 Gagal: Perintah REKAP harian terganggu');
  }
  console.log('✅ TEST 5 Berhasil: Perintah REKAP harian tetap berjalan normal\n');

  console.log('========================================================');
  console.log('🎉 SEMUA PENGUJIAN REKAP BULANAN BERHASIL 100%!');
  console.log('========================================================');
  process.exit(0);
}

runTests().catch(err => {
  console.error('❌ Terjadi kesalahan pada test:', err);
  process.exit(1);
});
