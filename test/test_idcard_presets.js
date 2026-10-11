const ejs = require('ejs');
const fs = require('fs');
const path = require('path');
const idcardService = require('../src/services/idcard');

async function testIDCardRendering() {
  console.log('--- Testing ID Card Preset Rendering ---');
  
  const students = await idcardService.getAllCardData();
  console.log(`Fetched ${students.length} students with QR codes.`);

  const templatePath = path.join(__dirname, '../src/views/idcard-print.ejs');
  const templateStr = fs.readFileSync(templatePath, 'utf-8');

  const settings = {
    school_name: 'SMA Negeri 1 Prestasi',
    school_address: 'Jl. Pendidikan No. 45, Kota Harapan Bangsa',
    school_phone: '0812-3456-7890',
    school_logo: '/uploads/logos/school_logo.jpg',
    school_principal_name: 'Drs. H. Mulyadi, M.Pd',
    school_principal_nip: '19720315 199802 1 002',
    card_issue_city: 'Cirebon',
    card_issue_date: '15 Juli 2026',
    school_principal_signature: '',
    school_principal_stamp: ''
  };

  const testCases = [
    { orientation: 'landscape', model: 'smart', theme: 'navy', side: 'front', cutMarks: false },
    { orientation: 'portrait', model: 'ribbon', theme: 'emerald', side: 'both', cutMarks: true, sigType: 'digital' },
    { orientation: 'portrait', model: 'cyber', theme: 'ruby', side: 'back', cutMarks: false, sigType: 'manual' },
    { orientation: 'landscape', model: 'smart', theme: 'purple', side: 'both', cutMarks: true, sigType: 'digital' },
    { orientation: 'portrait', model: 'smart', theme: 'slate', side: 'front', cutMarks: false },
    { orientation: 'portrait', model: 'sideribbon', theme: 'amber', side: 'front', cutMarks: true },
    { orientation: 'portrait', model: 'sideribbon', theme: 'cleanwhite', side: 'both', cutMarks: false, sigType: 'digital' },
    { orientation: 'landscape', model: 'sideribbon', theme: 'amber', side: 'front', cutMarks: true },
    { orientation: 'landscape', model: 'sideribbon', theme: 'cleanwhite', side: 'both', cutMarks: false, sigType: 'manual' }
  ];

  for (let i = 0; i < testCases.length; i++) {
    const preset = testCases[i];
    try {
      const html = ejs.render(templateStr, {
        students: students.slice(0, 2),
        settings,
        preset
      });
      console.log(`✓ Test ${i + 1} (${preset.orientation}, ${preset.model}, ${preset.theme}, ${preset.side}): Rendered ${html.length} bytes OK.`);
    } catch (err) {
      console.error(`✗ Test ${i + 1} Failed:`, err);
      process.exit(1);
    }
  }

  console.log('--- ALL ID CARD PRESET RENDERING TESTS PASSED! ---');
}

testIDCardRendering();
