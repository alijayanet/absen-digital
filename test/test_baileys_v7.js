const whatsapp = require('../src/services/whatsapp');
const { default: makeWASocket, Browsers, DisconnectReason } = require('@whiskeysockets/baileys');
const baileysPkg = require('@whiskeysockets/baileys/package.json');

console.log('=== TEST BAILEYS V7 & WHATSAPP SERVICE ===');
console.log('Installed Baileys Version:', baileysPkg.version);

if (baileysPkg.version.startsWith('7.')) {
  console.log('✅ PASSED: Baileys is version 7.x');
} else {
  console.error('❌ FAILED: Baileys is not version 7.x');
  process.exit(1);
}

// 1. Test Browsers helper
console.log('\n--- 1. Testing Browsers helper ---');
const browserInfo = Browsers.ubuntu('Chrome');
console.log('Browsers.ubuntu("Chrome"):', browserInfo);
if (Array.isArray(browserInfo) && browserInfo.length === 3) {
  console.log('✅ PASSED: Browsers.ubuntu helper returns valid tuple');
} else {
  console.error('❌ FAILED: Browsers.ubuntu helper invalid');
}

// 2. Test MessageStore & CacheStore
console.log('\n--- 2. Testing MessageStore & CacheStore ---');
const testKey = { remoteJid: '6281947215703@s.whatsapp.net', id: 'TEST_MSG_123' };
const testMsg = { conversation: 'Halo tes pesan dari Smart School' };

whatsapp.messageStore.cache(testKey, testMsg);

// Test cache store
whatsapp.msgRetryCounterCache.set('key1', 1);
if (whatsapp.msgRetryCounterCache.get('key1') === 1) {
  console.log('✅ PASSED: msgRetryCounterCache works');
} else {
  console.error('❌ FAILED: msgRetryCounterCache get failed');
}

// 3. Test cleanStaleSessionKeys
console.log('\n--- 3. Testing cleanStaleSessionKeys ---');
try {
  // Don't call reconnect in test, just test file cleaning
  const cleaned = whatsapp.cleanStaleSessionKeys(false);
  console.log('Cleaned stale keys count:', cleaned);
  console.log('✅ PASSED: cleanStaleSessionKeys executed cleanly without errors');
} catch (err) {
  console.error('❌ FAILED: cleanStaleSessionKeys error:', err);
}

// 4. Test formatJid and cleanPhone
console.log('\n--- 4. Testing Phone / JID Formatters ---');
const jid1 = whatsapp.formatJid('081947215703');
const jid2 = whatsapp.formatJid('+62 819-4721-5703');
console.log('Format 081947215703 ->', jid1);
console.log('Format +62 819-4721-5703 ->', jid2);

if (jid1 === '6281947215703@s.whatsapp.net' && jid2 === '6281947215703@s.whatsapp.net') {
  console.log('✅ PASSED: JID formatters working as expected');
} else {
  console.error('❌ FAILED: JID formatting mismatch');
}

// 5. Test getStatus()
console.log('\n--- 5. Testing getStatus() ---');
const status = whatsapp.getStatus();
console.log('Initial status:', status);
if (typeof status.status === 'string') {
  console.log('✅ PASSED: getStatus() returned valid status object');
} else {
  console.error('❌ FAILED: getStatus() invalid');
}

whatsapp.messageStore.get(testKey).then(retrieved => {
  if (retrieved && retrieved.conversation === 'Halo tes pesan dari Smart School') {
    console.log('✅ PASSED: MessageStore cached and retrieved message successfully!');
  } else {
    console.error('❌ FAILED: MessageStore retrieval failed', retrieved);
  }
  console.log('\n=== ALL BAILEYS V7 VERIFICATION TESTS COMPLETED ===');
  process.exit(0);
});
