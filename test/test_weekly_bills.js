const financeService = require('../src/services/financeService');
const db = require('../src/database/db');

console.log('--- Testing Weekly Fee Category & Bill Generation ---');

const weeklyCat = db.prepare("SELECT * FROM fee_categories WHERE frequency = 'WEEKLY'").get();
console.log('Found weekly category:', weeklyCat);

if (!weeklyCat) {
  console.error('No weekly category found!');
  process.exit(1);
}

const result = financeService.generateMonthlyBills({
  period: '2026-09-W1',
  categoryId: weeklyCat.id,
  baseAmount: 5000
});

console.log('Generation result:', result);

const sampleBill = db.prepare("SELECT * FROM student_bills WHERE category_id = ? AND period = '2026-09-W1' LIMIT 1").get(weeklyCat.id);
console.log('Created sample bill:', {
  id: sampleBill.id,
  bill_code: sampleBill.bill_code,
  title: sampleBill.title,
  period: sampleBill.period,
  base_amount: sampleBill.base_amount,
  total_amount: sampleBill.total_amount,
  status: sampleBill.status
});

if (sampleBill.title.includes('Minggu ke-1 September 2026')) {
  console.log('SUCCESS: Title formatting matches expected Indonesian weekly format!');
} else {
  console.error('FAIL: Unexpected title formatting:', sampleBill.title);
  process.exit(1);
}

// Clean up test bills for 2026-09-W1
db.prepare("DELETE FROM student_bills WHERE category_id = ? AND period = '2026-09-W1'").run(weeklyCat.id);
console.log('Cleaned up test bills successfully.');

console.log('--- ALL WEEKLY TESTS PASSED ---');
process.exit(0);
