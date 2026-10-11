const ejs = require('ejs');
const fs = require('fs');
const db = require('../src/database/db');

const tpl = fs.readFileSync('src/views/landing.ejs', 'utf8');

// Query student 1001
const studentData = db.prepare(`
  SELECT s.*, c.name as class_name 
  FROM students s 
  LEFT JOIN classes c ON s.class_id = c.id 
  WHERE s.nis = '1001' AND s.is_active = 1
`).get();

const waliKelas = db.prepare(`
  SELECT u.name, u.phone 
  FROM users u 
  WHERE u.role = 'guru' AND u.class_id = ? 
  LIMIT 1
`).get(studentData.class_id);

const attendanceSummary = {
  hadir: 20,
  terlambat: 2,
  sakit: 1,
  izin: 0,
  alpa: 0,
  total: 23,
  persentase: 96
};

const html = ejs.render(tpl, {
  settings: { school_name: 'SMA Negeri 1 Prestasi' },
  currentUser: null,
  keyword: '1001',
  studentData,
  waliKelas,
  attendanceSummary,
  recentAttendances: [],
  recentLeaves: [],
  errorMsg: null,
  leaveSuccess: null,
  leaveError: null,
  gallery: [],
  teachers: [],
  overview: { totalStudents: 10, totalClasses: 2, totalTeachers: 5, todayAttended: 8 }
}, { filename: 'src/views/landing.ejs' });

console.log('--- VERIFYING LANDING PAGE SALDO TABUNGAN CARD ---');
console.log('1. Contains "Saldo Tabungan":', html.includes('Saldo Tabungan'));
console.log('2. Contains "data-lucide=\\"coins\\"":', html.includes('data-lucide="coins"'));
console.log('3. Contains Student Name (Ahmad Fauzi):', html.includes('Ahmad Fauzi'));
console.log('4. Contains Wali Kelas Name:', html.includes(waliKelas.name));
console.log('5. Formatted Savings Balance present:', html.includes(Number(studentData.savings_balance || 0).toLocaleString('id-ID')));

if (html.includes('Saldo Tabungan') && html.includes(Number(studentData.savings_balance || 0).toLocaleString('id-ID'))) {
  console.log('\n✅ VERIFICATION RESULT: PASSED! Saldo Tabungan card is properly integrated into the student card on the landing page.');
} else {
  console.error('\n❌ VERIFICATION RESULT: FAILED!');
  process.exit(1);
}
