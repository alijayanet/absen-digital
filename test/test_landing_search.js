const db = require('../src/database/db');
const ejs = require('ejs');
const path = require('path');
const fs = require('fs');

console.log('=== TEST LANDING PAGE STUDENT SEARCH ===');

// 1. Verify students in database
const allStudents = db.prepare('SELECT id, nis, nisn, name, class_id FROM students WHERE is_active = 1').all();
console.log(`Found ${allStudents.length} active students in database:`);
allStudents.forEach(s => console.log(` - [${s.nis}] ${s.name} (NISN: ${s.nisn || '-'})`));

// Helper search function identical to router
function searchStudent(keyword) {
  keyword = (keyword || '').trim();
  let matchedStudents = db.prepare(`
    SELECT s.*, c.name as class_name 
    FROM students s 
    LEFT JOIN classes c ON s.class_id = c.id 
    WHERE (s.nis = ? OR s.nisn = ?) AND s.is_active = 1
  `).all(keyword, keyword);

  if (matchedStudents.length === 0) {
    matchedStudents = db.prepare(`
      SELECT s.*, c.name as class_name 
      FROM students s 
      LEFT JOIN classes c ON s.class_id = c.id 
      WHERE s.name LIKE ? AND s.is_active = 1
      ORDER BY s.name ASC
      LIMIT 10
    `).all(`%${keyword}%`);
  }

  return matchedStudents;
}

// Test 1: Exact NIS
console.log('\n--- Test 1: Search by exact NIS "1001" ---');
const res1 = searchStudent('1001');
console.log('Matches:', res1.length, res1.map(s => s.name));
if (res1.length === 1 && res1[0].nis === '1001') {
  console.log('✅ PASSED: Exact NIS match');
} else {
  console.error('❌ FAILED');
}

// Test 2: Full Name
console.log('\n--- Test 2: Search by full name "Siti Nurhaliza" ---');
const res2 = searchStudent('Siti Nurhaliza');
console.log('Matches:', res2.length, res2.map(s => s.name));
if (res2.length === 1 && res2[0].name === 'Siti Nurhaliza') {
  console.log('✅ PASSED: Full name match');
} else {
  console.error('❌ FAILED');
}

// Test 3: Partial Name single match
console.log('\n--- Test 3: Search by partial name "Dimas" ---');
const res3 = searchStudent('Dimas');
console.log('Matches:', res3.length, res3.map(s => s.name));
if (res3.length === 1 && res3[0].name.includes('Dimas')) {
  console.log('✅ PASSED: Partial name single match');
} else {
  console.error('❌ FAILED');
}

// Test 4: Partial Name multiple match (e.g. search "a" which matches Ahmad, Siti Nurhaliza, Dimas Aditya)
console.log('\n--- Test 4: Search by partial name "a" (multiple matches) ---');
const res4 = searchStudent('a');
console.log('Matches:', res4.length, res4.map(s => `${s.name} (${s.nis})`));
if (res4.length > 1) {
  console.log('✅ PASSED: Multi-match triggers multipleStudents picker');
} else {
  console.error('❌ FAILED');
}

// Test 5: Render landing.ejs with multipleStudents
console.log('\n--- Test 5: Verify landing.ejs template compilation and rendering with multipleStudents ---');
try {
  const landingPath = path.join(__dirname, '../src/views/landing.ejs');
  const template = fs.readFileSync(landingPath, 'utf-8');
  const html = ejs.render(template, {
    keyword: 'a',
    currentUser: null,
    studentData: null,
    multipleStudents: res4,
    attendanceSummary: null,
    recentAttendances: [],
    recentLeaves: [],
    errorMsg: null,
    leaveSuccess: null,
    leaveError: null,
    waliKelas: null,
    gallery: [],
    teachers: [],
    overview: {
      totalStudents: 3,
      totalClasses: 2,
      totalTeachers: 2,
      todayAttended: 1
    },
    site_name: 'Smart School',
    settings: {
      school_name: 'SDIT Bina Insani',
      school_address: 'Jl. Pendidikan No. 12',
      school_phone: '08123456789',
      school_email: 'info@sdit.sch.id',
      school_logo: '/images/logo.png',
      school_hero_image: '/images/hero.jpg',
      school_hero_title: 'Mendidik Generasi Berakhlak',
      school_hero_desc: 'Sekolah Unggulan',
      school_hero_stats: '500+ Siswa',
      school_hero_badge: 'Akreditasi A',
      school_vision: 'Menjadi sekolah teladan',
      school_mission: 'Mendidik dengan kasih sayang',
      school_principal_name: 'H. Sudirman, M.Pd',
      school_principal_welcome: 'Selamat datang',
      school_principal_photo: '/images/principal.jpg',
      school_principal_nip: '197501012000031001',
      school_principal_signature: null,
      school_principal_stamp: null,
      card_issue_city: 'Kebumen',
      card_issue_date: '15 Juli 2024'
    },
    school_name: 'SDIT Bina Insani',
    school_address: 'Jl. Pendidikan No. 12',
    school_phone: '08123456789',
    school_email: 'info@sdit.sch.id',
    school_logo: '/images/logo.png',
    school_hero_image: '/images/hero.jpg',
    school_hero_title: 'Mendidik Generasi Berakhlak',
    school_hero_desc: 'Sekolah Unggulan',
    school_hero_stats: '500+ Siswa',
    school_hero_badge: 'Akreditasi A',
    school_vision: 'Menjadi sekolah teladan',
    school_mission: 'Mendidik dengan kasih sayang',
    school_principal_name: 'H. Sudirman, M.Pd',
    school_principal_welcome: 'Selamat datang',
    school_principal_photo: '/images/principal.jpg',
    school_principal_nip: '197501012000031001',
    school_principal_signature: null,
    school_principal_stamp: null,
    card_issue_city: 'Kebumen',
    card_issue_date: '15 Juli 2024'
  }, {
    filename: landingPath
  });

  if (html.includes('Ditemukan') && html.includes('Silakan klik nama siswa')) {
    console.log('✅ PASSED: landing.ejs renders multiple candidate cards successfully!');
  } else {
    console.error('❌ FAILED: rendered output missing expected multi-student block');
  }
} catch (renderErr) {
  console.error('❌ EJS Render Error:', renderErr);
}
