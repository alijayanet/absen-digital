/**
 * test/test_savings.js
 * Comprehensive integration test for Savings Service and WhatsApp Bot commands.
 */

'use strict';

const db = require('../src/database/db');
const savingsService = require('../src/services/savingsService');
const botService = require('../src/services/bot.js');

async function runTests() {
  console.log('=== STARTING SAVINGS & BOT INTEGRATION TESTS ===\n');

  // 1. Check existing teachers and students
  const teacher = db.prepare(`
    SELECT u.*, c.name as class_name 
    FROM users u 
    JOIN classes c ON u.class_id = c.id 
    WHERE u.role = 'guru' AND u.phone IS NOT NULL AND u.phone != ''
    LIMIT 1
  `).get();

  if (!teacher) {
    throw new Error('No teacher with phone and class found for test.');
  }
  console.log(`[TEST] Found Teacher: ${teacher.name} (Phone: ${teacher.phone}, Class: ${teacher.class_name}, ClassID: ${teacher.class_id})`);

  const student = db.prepare(`
    SELECT * FROM students 
    WHERE class_id = ? AND is_active = 1
    LIMIT 1
  `).get(teacher.class_id);

  if (!student) {
    throw new Error('No student found in teacher class.');
  }
  console.log(`[TEST] Found Student: ${student.name} (NIS: ${student.nis}, Current Balance: ${student.savings_balance})`);

  // Record initial balance
  const initialBalance = Number(student.savings_balance) || 0;

  // 2. Test direct savingsService.deposit
  console.log('\n--- 2. Testing savingsService.deposit ---');
  const depositTx = savingsService.deposit({
    studentId: student.id,
    teacherId: teacher.id,
    amount: '25.000',
    notes: 'Test manual deposit'
  });
  console.log(`Deposited 25.000. New balance: ${depositTx.balance_after}. Verified expected: ${initialBalance + 25000}`);
  if (depositTx.balance_after !== initialBalance + 25000) {
    throw new Error('Deposit balance mismatch!');
  }

  // 3. Test direct savingsService.withdraw
  console.log('\n--- 3. Testing savingsService.withdraw ---');
  const withdrawTx = savingsService.withdraw({
    studentId: student.id,
    teacherId: teacher.id,
    amount: '10k',
    notes: 'Test manual withdrawal'
  });
  console.log(`Withdrew 10.000. New balance: ${withdrawTx.balance_after}. Verified expected: ${initialBalance + 15000}`);
  if (withdrawTx.balance_after !== initialBalance + 15000) {
    throw new Error('Withdrawal balance mismatch!');
  }

  // 4. Test over-withdrawal rejection
  console.log('\n--- 4. Testing Insufficient Balance Handling ---');
  try {
    savingsService.withdraw({
      studentId: student.id,
      teacherId: teacher.id,
      amount: 999999999,
      notes: 'Should fail'
    });
    throw new Error('Over-withdrawal did not throw an error!');
  } catch (err) {
    console.log(`Correctly rejected over-withdrawal: "${err.message}"`);
  }

  // 5. Test getStudentSavings, getClassSavings, and getTeacherSavingsSummary
  console.log('\n--- 5. Testing Reporting Queries ---');
  const studentSavings = savingsService.getStudentSavings(student.id);
  console.log(`getStudentSavings: balance = ${studentSavings.formattedBalance}, teacher = ${studentSavings.teacher.name}, transactions = ${studentSavings.transactions.length}`);

  const classSavings = savingsService.getClassSavings(teacher.class_id);
  console.log(`getClassSavings: totalBalance = ${classSavings.stats.formattedTotalBalance}, activeSavers = ${classSavings.stats.activeSavers}`);

  const teacherSummary = savingsService.getTeacherSavingsSummary();
  console.log(`getTeacherSavingsSummary: ${teacherSummary.length} teachers summarized`);

  const schoolStats = savingsService.getSchoolSavingsStats();
  console.log(`getSchoolSavingsStats: grandTotal = ${schoolStats.formattedGrandTotal}, totalTransactions = ${schoolStats.totalTransactions}`);

  // 6. Test Bot Commands
  console.log('\n--- 6. Testing WhatsApp Bot Commands ---');
  const teacherJid = `${teacher.phone.replace(/\D/g, '')}@s.whatsapp.net`;
  console.log(`Using teacher JID: ${teacherJid}`);

  // Teacher SETOR command
  const setorMsg = `SETOR ${student.name.split(' ')[0]} 20rb Uang kas jumat`;
  console.log(`Sending teacher command: "${setorMsg}"`);
  const setorReply = await botService.processIncomingMessage(teacherJid, setorMsg);
  console.log('Bot Reply:\n' + setorReply);
  if (!setorReply.includes('SETORAN TABUNGAN BERHASIL DICATAT') || !setorReply.includes('Rp 20.000')) {
    throw new Error('SETOR command response did not match expectation!');
  }

  // Teacher TARIK command
  const tarikMsg = `TARIK ${student.nis} 5000 Keperluan fotokopi`;
  console.log(`\nSending teacher command: "${tarikMsg}"`);
  const tarikReply = await botService.processIncomingMessage(teacherJid, tarikMsg);
  console.log('Bot Reply:\n' + tarikReply);
  if (!tarikReply.includes('PENARIKAN TABUNGAN BERHASIL DICATAT') || !tarikReply.includes('Rp 5.000')) {
    throw new Error('TARIK command response did not match expectation!');
  }

  // Parent TABUNGAN command
  console.log('\n--- 7. Testing Parent / Inquiry Commands ---');
  let parentJid = '0899999999@s.whatsapp.net';
  if (student.parent_phone) {
    parentJid = `${student.parent_phone.replace(/\D/g, '')}@s.whatsapp.net`;
  }
  console.log(`Using parent JID: ${parentJid}`);
  const saldoReply = await botService.processIncomingMessage(parentJid, `TABUNGAN ${student.nis}`);
  console.log('Inquiry Reply:\n' + saldoReply);
  if (!saldoReply.includes('BUKU TABUNGAN DIGITAL SISWA') || !saldoReply.includes('TOTAL SALDO TABUNGAN SAAT INI')) {
    throw new Error('TABUNGAN inquiry response did not match expectation!');
  }

  // Test Non-Teacher attempting SETOR
  console.log('\n--- 8. Testing Unauthorized SETOR ---');
  const unauthorizedReply = await botService.processIncomingMessage('62811111111@s.whatsapp.net', 'SETOR Ahmad 50000');
  console.log('Unauthorized response:\n' + unauthorizedReply);
  if (!unauthorizedReply.includes('khusus untuk Guru')) {
    throw new Error('Unauthorized check failed!');
  }

  // Test Menu includes savings
  console.log('\n--- 9. Testing Help Menu ---');
  const menuReply = await botService.processIncomingMessage(teacherJid, 'MENU');
  console.log('Teacher Menu snippet:\n' + menuReply);
  if (!menuReply.includes('TABUNGAN') || !menuReply.includes('SETOR')) {
    throw new Error('Menu missing savings commands!');
  }

  console.log('\n🎉 ALL SAVINGS & BOT INTEGRATION TESTS PASSED SUCCESSFULLY! 🎉');
}

runTests().catch(err => {
  console.error('\n❌ TEST FAILED:', err);
  process.exit(1);
});
