/**
 * test/test_teacher_savings_bot.js
 * Comprehensive integration test for Teacher & Admin Savings WhatsApp queries:
 * 1. Teacher checks entire class savings via 'TABUNGAN', 'TOTAL TABUNGAN', 'TABUNGAN KELAS'
 * 2. Teacher checks individual student balance via 'TABUNGAN <NAME>' (e.g. TABUNGAN Ahmad)
 * 3. Teacher checks individual student balance via 'TABUNGAN <NIS>' (e.g. TABUNGAN 1001)
 * 4. Admin checks entire school savings recap via 'TOTAL TABUNGAN' or 'TABUNGAN'
 * 5. Teacher 'MENU' contains savings options
 */

'use strict';

const db = require('../src/database/db');
const botService = require('../src/services/bot.js');

async function runTests() {
  console.log('=== STARTING TEACHER SAVINGS BOT COMMAND TESTS ===\n');

  // 1. Get teacher with a class and phone
  const teacher = db.prepare(`
    SELECT u.*, c.name as class_name 
    FROM users u 
    JOIN classes c ON u.class_id = c.id 
    WHERE u.role = 'guru' AND u.phone IS NOT NULL AND u.phone != ''
    LIMIT 1
  `).get();

  if (!teacher) {
    throw new Error('No teacher found for test');
  }
  const teacherJid = `${teacher.phone.replace(/\D/g, '')}@s.whatsapp.net`;
  console.log(`[TEST] Teacher: ${teacher.name} (${teacher.phone}), Class: ${teacher.class_name} (ID: ${teacher.class_id})`);

  // Get student in this teacher's class
  const student = db.prepare(`
    SELECT * FROM students 
    WHERE class_id = ? AND is_active = 1
    LIMIT 1
  `).get(teacher.class_id);

  if (!student) {
    throw new Error('No student found in teacher class');
  }
  console.log(`[TEST] Student: ${student.name} (NIS: ${student.nis})\n`);

  // Get admin user
  const admin = db.prepare(`
    SELECT * FROM users 
    WHERE role = 'admin' AND phone IS NOT NULL AND phone != ''
    LIMIT 1
  `).get();
  console.log(`[TEST] Admin: ${admin ? admin.name + ' (' + admin.phone + ')' : 'None'}\n`);

  // --- Test 1: Teacher types "TABUNGAN" ---
  console.log('--- TEST 1: Teacher types "TABUNGAN" ---');
  const resp1 = await botService.processIncomingMessage(teacherJid, 'TABUNGAN');
  console.log(resp1);
  if (!resp1.includes('REKAPITULASI TABUNGAN KELAS') || !resp1.includes(teacher.class_name)) {
    throw new Error('Test 1 Failed: Expected class savings recap for teacher');
  }
  console.log('✅ Test 1 Passed: Teacher TABUNGAN returns class summary.\n');

  // --- Test 2: Teacher types "TOTAL TABUNGAN" ---
  console.log('--- TEST 2: Teacher types "TOTAL TABUNGAN" ---');
  const resp2 = await botService.processIncomingMessage(teacherJid, 'TOTAL TABUNGAN');
  console.log(resp2);
  if (!resp2.includes('REKAPITULASI TABUNGAN KELAS') || !resp2.includes('TOTAL SALDO KELAS')) {
    throw new Error('Test 2 Failed: Expected class savings recap for TOTAL TABUNGAN');
  }
  console.log('✅ Test 2 Passed: Teacher TOTAL TABUNGAN returns class summary.\n');

  // --- Test 3: Teacher types "TABUNGAN KELAS" ---
  console.log('--- TEST 3: Teacher types "TABUNGAN KELAS" ---');
  const resp3 = await botService.processIncomingMessage(teacherJid, 'TABUNGAN KELAS');
  console.log(resp3);
  if (!resp3.includes('REKAPITULASI TABUNGAN KELAS')) {
    throw new Error('Test 3 Failed: Expected class savings recap for TABUNGAN KELAS');
  }
  console.log('✅ Test 3 Passed: Teacher TABUNGAN KELAS returns class summary.\n');

  // --- Test 4: Teacher types "TABUNGAN <NAMA>" ---
  const firstName = student.name.split(' ')[0];
  console.log(`--- TEST 4: Teacher types "TABUNGAN ${firstName}" ---`);
  const resp4 = await botService.processIncomingMessage(teacherJid, `TABUNGAN ${firstName}`);
  console.log(resp4);
  if (!resp4.includes('BUKU TABUNGAN DIGITAL SISWA') || !resp4.includes(student.name) || !resp4.includes(teacher.name)) {
    throw new Error(`Test 4 Failed: Expected student savings message with student name (${student.name}) and teacher name (${teacher.name})`);
  }
  console.log(`✅ Test 4 Passed: Teacher checks student balance by name ("TABUNGAN ${firstName}").\n`);

  // --- Test 5: Teacher types "TABUNGAN <NIS>" ---
  console.log(`--- TEST 5: Teacher types "TABUNGAN ${student.nis}" ---`);
  const resp5 = await botService.processIncomingMessage(teacherJid, `TABUNGAN ${student.nis}`);
  console.log(resp5);
  if (!resp5.includes('BUKU TABUNGAN DIGITAL SISWA') || !resp5.includes(student.nis)) {
    throw new Error('Test 5 Failed: Expected student savings message for NIS');
  }
  console.log(`✅ Test 5 Passed: Teacher checks student balance by NIS ("TABUNGAN ${student.nis}").\n`);

  // --- Test 6: Admin types "TOTAL TABUNGAN" ---
  if (admin) {
    const adminJid = `${admin.phone.replace(/\D/g, '')}@s.whatsapp.net`;
    console.log(`--- TEST 6: Admin types "TOTAL TABUNGAN" ---`);
    const resp6 = await botService.processIncomingMessage(adminJid, 'TOTAL TABUNGAN');
    console.log(resp6);
    if (!resp6.includes('REKAPITULASI TABUNGAN SEKOLAH') || !resp6.includes('GRAND TOTAL SALDO SEKOLAH')) {
      throw new Error('Test 6 Failed: Expected school savings summary for admin');
    }
    console.log('✅ Test 6 Passed: Admin receives school-wide savings recap.\n');
  }

  // --- Test 7: Teacher types "MENU" ---
  console.log('--- TEST 7: Teacher types "MENU" ---');
  const resp7 = await botService.processIncomingMessage(teacherJid, 'MENU');
  console.log(resp7);
  if (!resp7.includes('TABUNGAN* / *TOTAL TABUNGAN*') || !resp7.includes('TABUNGAN <NAMA/NIS>')) {
    throw new Error('Test 7 Failed: Teacher menu does not include savings inquiry commands');
  }
  console.log('✅ Test 7 Passed: Teacher menu lists savings check commands.\n');

  console.log('🎉 ALL 7 TESTS PASSED SUCCESSFULLY! 🎉');
}

runTests().catch(err => {
  console.error('\n❌ TEST FAILED:', err);
  process.exit(1);
});
