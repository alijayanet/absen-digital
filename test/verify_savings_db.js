const db = require('../src/database/db');

const table = db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='student_savings'").get();
console.log('student_savings table:', table);

const col = db.prepare("PRAGMA table_info(students)").all().find(c => c.name === 'savings_balance');
console.log('savings_balance col:', col);

if (table && col) {
  console.log('SUCCESS: Table and column exist in SQLite database!');
} else {
  console.error('FAIL: Missing table or column');
  process.exit(1);
}
process.exit(0);
