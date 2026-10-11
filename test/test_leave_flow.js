/**
 * test_leave_flow.js — Pengujian Komprehensif Alur Izin/Sakit Mandiri via WhatsApp Bot
 */

const db = require('../src/database/db');
const bot = require('../src/services/bot');
const leaveService = require('../src/services/leaveService');
const { getTodayWIB, getRelativeDateWIB, isWeekend } = require('../src/utils/timeHelper');

async function runTests() {
  console.log('========================================================');
  console.log('🧪 Memulai Pengujian Fitur Izin/Sakit WhatsApp Bot');
  console.log('========================================================\n');

  // Ambil data sample guru dan siswa
  const teacher = db.prepare("SELECT * FROM users WHERE role = 'guru' AND class_id IS NOT NULL AND phone IS NOT NULL LIMIT 1").get();
  if (!teacher) {
    console.error('❌ Tidak ditemukan guru dengan kelas dan no HP untuk pengujian.');
    process.exit(1);
  }

  const student = db.prepare("SELECT * FROM students WHERE class_id = ? AND is_active = 1 LIMIT 1").get(teacher.class_id);
  if (!student) {
    console.error('❌ Tidak ditemukan siswa di kelas guru untuk pengujian.');
    process.exit(1);
  }

  const parentPhone = student.parent_phone || '081234567890';
  const teacherPhone = teacher.phone;

  console.log(`📌 Konteks Uji:`);
  console.log(`   Guru: ${teacher.name} (${teacherPhone})`);
  console.log(`   Siswa: ${student.name} (NIS: ${student.nis}, Kelas ID: ${student.class_id})`);
  console.log(`   Wali Murid: ${parentPhone}\n`);

  // Bersihkan data leave uji sebelumnya untuk siswa ini jika ada
  db.prepare("DELETE FROM leave_requests WHERE student_id = ?").run(student.id);

  // -------------------------------------------------------------
  // TEST 1: Orang tua mengirim permohonan sakit
  // -------------------------------------------------------------
  console.log('--- TEST 1: Orang Tua Mengirim "SAKIT demam tinggi" ---');
  const parentSenderJid = `${parentPhone.replace(/\D/g, '')}@s.whatsapp.net`;
  const parentResponse = await bot.processIncomingMessage(parentSenderJid, 'SAKIT demam tinggi', parentPhone);
  console.log('Respons ke Orang Tua:\n', parentResponse);

  if (!parentResponse.includes('MENUNGGU PERSETUJUAN WALI KELAS')) {
    throw new Error('Test 1 Gagal: Respons tidak mengandung status menunggu persetujuan');
  }

  // Cek database leave_requests
  const createdLeave = db.prepare("SELECT * FROM leave_requests WHERE student_id = ? ORDER BY id DESC LIMIT 1").get(student.id);
  if (!createdLeave || createdLeave.status !== 'PENDING' || createdLeave.type !== 'SAKIT') {
    throw new Error('Test 1 Gagal: Record leave_requests tidak tersimpan dengan benar');
  }
  console.log(`✅ TEST 1 Berhasil: Permohonan tercatat dengan ID #${createdLeave.id}\n`);

  // -------------------------------------------------------------
  // TEST 2: Guru memeriksa daftar PENDING
  // -------------------------------------------------------------
  console.log('--- TEST 2: Guru Mengetik "PENDING" ---');
  const teacherSenderJid = `${teacherPhone.replace(/\D/g, '')}@s.whatsapp.net`;
  const pendingResp = await bot.processIncomingMessage(teacherSenderJid, 'PENDING', teacherPhone);
  console.log('Respons ke Guru:\n', pendingResp);

  if (!pendingResp.includes(student.name) || !pendingResp.includes(`#${createdLeave.id}`)) {
    throw new Error('Test 2 Gagal: Daftar pending tidak mencantumkan permohonan siswa');
  }
  console.log('✅ TEST 2 Berhasil: Guru dapat melihat daftar permohonan pending\n');

  // -------------------------------------------------------------
  // TEST 3: Guru menyetujui permohonan "SETUJU <ID>"
  // -------------------------------------------------------------
  console.log(`--- TEST 3: Guru Mengetik "SETUJU ${createdLeave.id}" ---`);
  const approveResp = await bot.processIncomingMessage(teacherSenderJid, `SETUJU ${createdLeave.id}`, teacherPhone);
  console.log('Respons ke Guru:\n', approveResp);

  if (!approveResp.includes('BERHASIL DISETUJUI')) {
    throw new Error('Test 3 Gagal: Respons persetujuan tidak sukses');
  }

  // Cek database leave_requests status
  const updatedLeave = db.prepare("SELECT * FROM leave_requests WHERE id = ?").get(createdLeave.id);
  if (updatedLeave.status !== 'APPROVED') {
    throw new Error('Test 3 Gagal: Status leave_requests belum APPROVED');
  }

  // Cek database attendances
  const attRecord = db.prepare("SELECT * FROM attendances WHERE student_id = ? AND date = ?").get(student.id, createdLeave.start_date);
  if (!attRecord || attRecord.status !== 'SAKIT') {
    throw new Error('Test 3 Gagal: Catatan presensi belum sinkron ke status SAKIT');
  }
  console.log(`✅ TEST 3 Berhasil: Status leave APPROVED, Presensi ${attRecord.date} tercatat sebagai ${attRecord.status}\n`);

  // -------------------------------------------------------------
  // TEST 4: Orang tua mengajukan pada hari libur akhir pekan
  // -------------------------------------------------------------
  console.log('--- TEST 4: Pengajuan di Hari Libur Akhir Pekan ---');
  // Cari tanggal Sabtu terdekat
  let satDate = getTodayWIB();
  while (!isWeekend(satDate)) {
    const d = new Date(satDate + 'T12:00:00Z');
    d.setDate(d.getDate() + 1);
    satDate = d.toISOString().slice(0, 10);
  }
  const weekendResp = await bot.processIncomingMessage(parentSenderJid, `IZIN ${satDate} acara liburan`, parentPhone);
  console.log('Respons Akhir Pekan:\n', weekendResp);

  if (!weekendResp.includes('hari libur akhir pekan')) {
    throw new Error('Test 4 Gagal: Sistem tidak menolak pengajuan pada hari libur akhir pekan');
  }
  console.log('✅ TEST 4 Berhasil: Hari libur akhir pekan otomatis ditolak secara edukatif\n');

  // -------------------------------------------------------------
  // TEST 5: Guru menolak permohonan "TOLAK <ID> <alasan>"
  // -------------------------------------------------------------
  console.log('--- TEST 5: Pengajuan Izin dan Penolakan oleh Guru ---');
  // Buat permohonan baru untuk besok jika bukan akhir pekan, atau 3 hari ke depan
  let futureDate = getRelativeDateWIB(1);
  if (isWeekend(futureDate)) futureDate = getRelativeDateWIB(3);

  const parentResp2 = await bot.processIncomingMessage(parentSenderJid, `IZIN ${futureDate} kepentingan keluarga`, parentPhone);
  const createdLeave2 = db.prepare("SELECT * FROM leave_requests WHERE student_id = ? ORDER BY id DESC LIMIT 1").get(student.id);

  const rejectResp = await bot.processIncomingMessage(teacherSenderJid, `TOLAK ${createdLeave2.id} Surat keterangan belum ada`, teacherPhone);
  console.log('Respons Penolakan ke Guru:\n', rejectResp);

  const updatedLeave2 = db.prepare("SELECT * FROM leave_requests WHERE id = ?").get(createdLeave2.id);
  if (updatedLeave2.status !== 'REJECTED' || !updatedLeave2.rejection_note.includes('Surat keterangan')) {
    throw new Error('Test 5 Gagal: Status permohonan belum REJECTED dengan catatan penolakan');
  }
  console.log(`✅ TEST 5 Berhasil: Permohonan #${createdLeave2.id} berhasil ditolak dengan catatan alasan\n`);

  console.log('========================================================');
  console.log('🎉 SEMUA PENGUJIAN FITUR IZIN/SAKIT BERHASIL 100%!');
  console.log('========================================================');
  process.exit(0);
}

runTests().catch(err => {
  console.error('❌ Terjadi kesalahan pada test:', err);
  process.exit(1);
});
