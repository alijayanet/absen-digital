const ejs = require('ejs');
const fs = require('fs');
const savingsService = require('../src/services/savingsService');
const tpl = fs.readFileSync('src/views/dashboard/savings/index.ejs', 'utf8');

const classData = savingsService.getClassSavings(1);

// Test 1: Admin viewing class
const htmlAdmin = ejs.render(tpl, {
  settings: {},
  currentUser: { role: 'admin', name: 'Administrator' },
  user: { role: 'admin', name: 'Administrator' },
  pageTitle: 'Tabungan Siswa',
  currentPath: '/dashboard/savings',
  isGuru: false,
  isAdmin: true,
  classData,
  teacherSummary: savingsService.getTeacherSavingsSummary(),
  schoolStats: savingsService.getSchoolSavingsStats(),
  classes: [],
  selectedClassId: 1,
  formatRupiah: savingsService.formatRupiah,
  error: null,
  success: null
}, { filename: 'src/views/dashboard/savings/index.ejs' });

console.log('--- ADMIN VIEW CHECKS ---');
console.log('1. Has "+ Setor Tabungan" button:', htmlAdmin.includes('+ Setor Tabungan'));
console.log('2. Has "- Tarik Saldo" button:', htmlAdmin.includes('- Tarik Saldo'));
console.log('3. Has "Aksi Cepat" table header:', htmlAdmin.includes('Aksi Cepat'));
console.log('4. Has id="depositModal":', htmlAdmin.includes('id="depositModal"'));
console.log('5. Has id="withdrawModal":', htmlAdmin.includes('id="withdrawModal"'));
console.log('6. Has "Riwayat" book button:', htmlAdmin.includes('Transaksi'));

// Test 2: Guru viewing class
const htmlGuru = ejs.render(tpl, {
  settings: {},
  currentUser: { role: 'guru', name: 'Budi Santoso' },
  user: { role: 'guru', name: 'Budi Santoso' },
  pageTitle: 'Tabungan Siswa',
  currentPath: '/dashboard/savings',
  isGuru: true,
  isAdmin: false,
  classData,
  teacherSummary: null,
  schoolStats: null,
  classes: [],
  selectedClassId: null,
  formatRupiah: savingsService.formatRupiah,
  error: null,
  success: null
}, { filename: 'src/views/dashboard/savings/index.ejs' });

console.log('\n--- GURU VIEW CHECKS ---');
console.log('1. Has "+ Setor Tabungan" button:', htmlGuru.includes('+ Setor Tabungan'));
console.log('2. Has "- Tarik Saldo" button:', htmlGuru.includes('- Tarik Saldo'));
console.log('3. Has "Aksi Cepat" table header:', htmlGuru.includes('Aksi Cepat'));
console.log('4. Has id="depositModal":', htmlGuru.includes('id="depositModal"'));
console.log('5. Has id="withdrawModal":', htmlGuru.includes('id="withdrawModal"'));

const adminClean = !htmlAdmin.includes('+ Setor Tabungan') && 
                   !htmlAdmin.includes('- Tarik Saldo') && 
                   !htmlAdmin.includes('Aksi Cepat') && 
                   !htmlAdmin.includes('id="depositModal"') && 
                   !htmlAdmin.includes('id="withdrawModal"');

const guruComplete = htmlGuru.includes('+ Setor Tabungan') && 
                     htmlGuru.includes('- Tarik Saldo') && 
                     htmlGuru.includes('Aksi Cepat') && 
                     htmlGuru.includes('id="depositModal"') && 
                     htmlGuru.includes('id="withdrawModal"');

if (adminClean && guruComplete) {
  console.log('\n✅ VERIFICATION RESULT: PASSED! Setor & Tarik buttons are completely hidden for Admin and fully accessible for Guru.');
} else {
  console.error('\n❌ VERIFICATION RESULT: FAILED!');
  process.exit(1);
}
