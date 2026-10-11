const db = require('../src/database/db');

console.log('--- Testing Principal & Signature DB Settings ---');

const testEntries = [
  ['school_principal_name', 'Drs. H. Mulyadi, M.Pd'],
  ['school_principal_nip', '19720315 199802 1 002'],
  ['card_issue_city', 'Cirebon'],
  ['card_issue_date', '15 Juli 2026'],
  ['school_principal_signature', '/uploads/photos/signature_sample.png'],
  ['school_principal_stamp', '/uploads/logos/stamp_sample.png']
];

const upsert = db.prepare('INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value');
for (const [k, v] of testEntries) {
  upsert.run(k, v);
  console.log(`✓ Set ${k} = "${v}"`);
}

// Verify reading
const rows = db.prepare("SELECT key, value FROM settings WHERE key LIKE 'school_principal_%' OR key LIKE 'card_issue_%'").all();
console.log('Retrieved settings:', rows);

console.log('--- All DB Settings verified successfully! ---');
