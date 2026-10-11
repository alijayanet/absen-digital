/**
 * test/test_lid_resolution.js
 * Verify that when an incoming message comes with an LID (e.g. 85280887435270@lid),
 * the bot resolves the LID to the teacher's phone (081947215703) and correctly identifies the teacher!
 */

const botService = require('../src/services/bot');
const { waLidStore } = require('../src/services/waLidStore');

async function testLid() {
  console.log('=== TEST LID RESOLUTION ===\n');

  // Verify waLidStore mapping
  const mapped = waLidStore.get('85280887435270@lid');
  console.log('waLidStore.get("85280887435270@lid") =', mapped);
  if (mapped !== '6281947215703') {
    throw new Error(`Expected 6281947215703, got ${mapped}`);
  }

  // Simulate incoming message from teacher LID
  console.log('\n--- Testing processIncomingMessage with LID: 85280887435270@lid, text: "Tabungan" ---');
  const reply = await botService.processIncomingMessage('85280887435270@lid', 'Tabungan');
  console.log('Bot Response:\n', reply);

  if (!reply.includes('REKAPITULASI TABUNGAN KELAS')) {
    throw new Error('FAILED: Expected class savings recap for teacher Budi Santoso!');
  }

  if (reply.includes('belum terdaftar sebagai wali murid')) {
    throw new Error('FAILED: Bot still says not registered!');
  }

  console.log('✅ SUCCESS: Teacher Budi Santoso was recognized via LID resolution!');
}

testLid().catch(err => {
  console.error('Test failed:', err);
  process.exit(1);
});
