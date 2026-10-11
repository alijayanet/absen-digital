/**
 * test/test_command_filter.js
 * Test the new behavior:
 * 1. Unregistered numbers:
 *    - "Halo", "P", "Siang", "Selamat pagi", "Besok masuk gak?" -> returns null (no welcome message, no reply).
 *    - "MENU", "BANTUAN" -> returns null (no welcome message).
 * 2. Registered users (Teacher, Parent, Admin):
 *    - "Halo", "P", "Siang", "Makasih pak", "Besok masuk jam berapa?" -> returns null (bot stays silent, no interruption).
 *    - "MENU" -> returns personalized menu for their role.
 *    - Specific commands (e.g. "TABUNGAN", "CEK") -> correctly executed.
 */

const botService = require('../src/services/bot');
const db = require('../src/database/db');

async function runTests() {
  console.log('=== TEST BOT COMMAND FILTER & UNREGISTERED NUMBER RESTRICTION ===\n');

  // 1. Get sample registered users from database
  const teacher = db.prepare("SELECT * FROM users WHERE role = 'guru' AND phone IS NOT NULL AND phone != '' LIMIT 1").get();
  const admin = db.prepare("SELECT * FROM users WHERE role = 'admin' AND phone IS NOT NULL AND phone != '' LIMIT 1").get();
  const parentStudent = db.prepare("SELECT * FROM students WHERE parent_phone IS NOT NULL AND parent_phone != '' AND is_active = 1 LIMIT 1").get();

  const teacherPhone = teacher.phone;
  const adminPhone = admin.phone;
  const parentPhone = parentStudent.parent_phone;
  const unregisteredPhone = '089988776655';

  console.log(`Teacher Phone: ${teacherPhone} (${teacher.name})`);
  console.log(`Admin Phone: ${adminPhone} (${admin.name})`);
  console.log(`Parent Phone: ${parentPhone} (Wali dari ${parentStudent.name})`);
  console.log(`Unregistered Phone: ${unregisteredPhone}\n`);

  // --- TEST A: Unregistered Number ---
  console.log('--- Test A: Unregistered Number ---');
  
  const unregGreeting = await botService.processIncomingMessage(`${unregisteredPhone}@s.whatsapp.net`, 'Halo');
  console.log('A1. Unregistered sending "Halo":', unregGreeting === null ? 'PASS (null - no reply)' : 'FAIL: ' + unregGreeting);
  if (unregGreeting !== null) throw new Error('Unregistered number should get null for greeting!');

  const unregP = await botService.processIncomingMessage(`${unregisteredPhone}@s.whatsapp.net`, 'P');
  console.log('A2. Unregistered sending "P":', unregP === null ? 'PASS (null - no reply)' : 'FAIL: ' + unregP);
  if (unregP !== null) throw new Error('Unregistered number should get null for "P"!');

  const unregRandom = await botService.processIncomingMessage(`${unregisteredPhone}@s.whatsapp.net`, 'Besok libur gak ya?');
  console.log('A3. Unregistered sending casual question:', unregRandom === null ? 'PASS (null - no reply)' : 'FAIL: ' + unregRandom);
  if (unregRandom !== null) throw new Error('Unregistered number should get null for casual chat!');

  const unregMenu = await botService.processIncomingMessage(`${unregisteredPhone}@s.whatsapp.net`, 'MENU');
  console.log('A4. Unregistered sending "MENU":', unregMenu === null ? 'PASS (null - no welcome message)' : 'FAIL: ' + unregMenu);
  if (unregMenu !== null) throw new Error('Unregistered number should get null for MENU!');

  // --- TEST B: Registered Teacher ---
  console.log('\n--- Test B: Registered Teacher ---');
  
  const teacherCasual = await botService.processIncomingMessage(`${teacherPhone}@s.whatsapp.net`, 'Halo pak, besok rapat jam berapa?');
  console.log('B1. Teacher sending casual chat:', teacherCasual === null ? 'PASS (null - no bot interruption)' : 'FAIL: ' + teacherCasual);
  if (teacherCasual !== null) throw new Error('Teacher casual chat should get null!');

  const teacherThanks = await botService.processIncomingMessage(`${teacherPhone}@s.whatsapp.net`, 'Terima kasih ya');
  console.log('B2. Teacher sending "Terima kasih":', teacherThanks === null ? 'PASS (null - no bot interruption)' : 'FAIL: ' + teacherThanks);
  if (teacherThanks !== null) throw new Error('Teacher "Terima kasih" should get null!');

  const teacherMenu = await botService.processIncomingMessage(`${teacherPhone}@s.whatsapp.net`, 'MENU');
  const hasTeacherTitle = teacherMenu && teacherMenu.includes(teacher.name);
  const hasTeacherSavings = teacherMenu && teacherMenu.includes('MENU KHUSUS WALI KELAS');
  console.log('B3. Teacher sending "MENU":', (hasTeacherTitle && hasTeacherSavings) ? 'PASS (personalized teacher menu)' : 'FAIL');
  if (!hasTeacherTitle || !hasTeacherSavings) throw new Error('Teacher MENU failed!');

  const teacherCommand = await botService.processIncomingMessage(`${teacherPhone}@s.whatsapp.net`, 'TABUNGAN');
  const isTeacherSavings = teacherCommand && teacherCommand.includes('REKAPITULASI TABUNGAN KELAS');
  console.log('B4. Teacher sending command "TABUNGAN":', isTeacherSavings ? 'PASS (executed command)' : 'FAIL');
  if (!isTeacherSavings) throw new Error('Teacher TABUNGAN command failed!');

  // --- TEST C: Registered Parent ---
  console.log('\n--- Test C: Registered Parent ---');

  const parentCasual = await botService.processIncomingMessage(`${parentPhone}@s.whatsapp.net`, 'Siap pak terima kasih infonya');
  console.log('C1. Parent sending casual chat:', parentCasual === null ? 'PASS (null - no bot interruption)' : 'FAIL: ' + parentCasual);
  if (parentCasual !== null) throw new Error('Parent casual chat should get null!');

  const parentMenu = await botService.processIncomingMessage(`${parentPhone}@s.whatsapp.net`, 'MENU');
  const hasParentSection = parentMenu && parentMenu.includes('LAYANAN WALI MURID');
  const noTeacherSection = parentMenu && !parentMenu.includes('MENU KHUSUS WALI KELAS');
  console.log('C2. Parent sending "MENU":', (hasParentSection && noTeacherSection) ? 'PASS (personalized parent menu)' : 'FAIL');
  if (!hasParentSection || !noTeacherSection) throw new Error('Parent MENU failed!');

  const parentCek = await botService.processIncomingMessage(`${parentPhone}@s.whatsapp.net`, 'CEK');
  const hasAttendance = parentCek && parentCek.includes('LAPORAN KEHADIRAN SISWA');
  console.log('C3. Parent sending command "CEK":', hasAttendance ? 'PASS (executed command)' : 'FAIL');
  if (!hasAttendance) throw new Error('Parent CEK command failed!');

  // --- TEST D: Registered Admin ---
  console.log('\n--- Test D: Registered Admin ---');

  const adminMenu = await botService.processIncomingMessage(`${adminPhone}@s.whatsapp.net`, 'MENU');
  const hasAdminSection = adminMenu && adminMenu.includes('MENU KHUSUS ADMINISTRATOR');
  console.log('D1. Admin sending "MENU":', hasAdminSection ? 'PASS (personalized admin menu)' : 'FAIL');
  if (!hasAdminSection) throw new Error('Admin MENU failed!');

  console.log('\n🎉 ALL TESTS PASSED SUCCESSFULLY!');
}

runTests().catch(err => {
  console.error('\n❌ TEST RUN FAILED:', err);
  process.exit(1);
});
