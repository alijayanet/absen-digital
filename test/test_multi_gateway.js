const assert = require('assert');
const whatsapp = require('../src/services/whatsapp');
const db = require('../src/database/db');

console.log('--- TEST MULTI-GATEWAY WHATSAPP SERVICE ---');

// 1. Test Phone Formatter
console.log('1. Testing Phone Formatter...');
assert.strictEqual(whatsapp.formatPhoneForApi('081234567890'), '6281234567890');
assert.strictEqual(whatsapp.formatPhoneForApi('+62812-3456-7890'), '6281234567890');
assert.strictEqual(whatsapp.formatPhoneForApi('81234567890'), '6281234567890');
assert.strictEqual(whatsapp.formatPhoneForApi('6281234567890'), '6281234567890');
console.log('✅ Phone Formatter passed!');

// 2. Test Provider Settings & Dynamic Status
console.log('2. Testing Provider Settings & Status...');

// Set to Fonnte with empty token
db.prepare("INSERT INTO settings (key, value) VALUES ('wa_provider', 'fonnte') ON CONFLICT(key) DO UPDATE SET value = 'fonnte'").run();
db.prepare("INSERT INTO settings (key, value) VALUES ('wa_fonnte_token', '') ON CONFLICT(key) DO UPDATE SET value = ''").run();

let cfg = whatsapp.getProviderSettings();
assert.strictEqual(cfg.provider, 'fonnte');
assert.strictEqual(whatsapp.status, 'disconnected');
let status = whatsapp.getStatus();
assert.strictEqual(status.provider, 'fonnte');
assert.strictEqual(status.status, 'disconnected');

// Set to Fonnte with valid token
db.prepare("INSERT INTO settings (key, value) VALUES ('wa_fonnte_token', 'test_fonnte_token_12345') ON CONFLICT(key) DO UPDATE SET value = 'test_fonnte_token_12345'").run();
cfg = whatsapp.getProviderSettings();
assert.strictEqual(cfg.fonnte_token, 'test_fonnte_token_12345');
assert.strictEqual(whatsapp.status, 'connected');
status = whatsapp.getStatus();
assert.strictEqual(status.status, 'connected');
assert.strictEqual(status.providerName, 'Fonnte Cloud Gateway');
assert.ok(status.tokenMasked.includes('••••••••'));

// Set to Wablas
db.prepare("INSERT INTO settings (key, value) VALUES ('wa_provider', 'wablas') ON CONFLICT(key) DO UPDATE SET value = 'wablas'").run();
db.prepare("INSERT INTO settings (key, value) VALUES ('wa_wablas_token', 'test_wablas_token_9999') ON CONFLICT(key) DO UPDATE SET value = 'test_wablas_token_9999'").run();
db.prepare("INSERT INTO settings (key, value) VALUES ('wa_wablas_domain', 'https://kudus.wablas.com') ON CONFLICT(key) DO UPDATE SET value = 'https://kudus.wablas.com'").run();

cfg = whatsapp.getProviderSettings();
assert.strictEqual(cfg.provider, 'wablas');
assert.strictEqual(cfg.wablas_domain, 'https://kudus.wablas.com');
assert.strictEqual(whatsapp.status, 'connected');
status = whatsapp.getStatus();
assert.strictEqual(status.provider, 'wablas');
assert.strictEqual(status.status, 'connected');
assert.strictEqual(status.providerName, 'Wablas Cloud Gateway');

// Revert to Baileys
db.prepare("INSERT INTO settings (key, value) VALUES ('wa_provider', 'baileys') ON CONFLICT(key) DO UPDATE SET value = 'baileys'").run();
cfg = whatsapp.getProviderSettings();
assert.strictEqual(cfg.provider, 'baileys');
status = whatsapp.getStatus();
assert.strictEqual(status.provider, 'baileys');
console.log('✅ Provider Settings & Dynamic Status passed!');

console.log('🎉 ALL MULTI-GATEWAY TESTS PASSED SUCCESSFULLY!');
process.exit(0);
