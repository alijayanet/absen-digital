const express = require('express');
const router = express.Router();
const db = require('../database/db');
const { isAuthenticated, isAdmin } = require('../middlewares/auth');
const whatsapp = require('../services/whatsapp');
const queue = require('../services/queue');

// Tampilan Manajemen WhatsApp Baileys
// Tampilan Manajemen WhatsApp Multi-Gateway
router.get('/dashboard/whatsapp', isAuthenticated, isAdmin, (req, res) => {
  const statusInfo = whatsapp.getStatus();
  const providerSettings = whatsapp.getProviderSettings();

  // Ambil 50 log antrean pesan terakhir
  const queueLogs = db.prepare(`
    SELECT q.*, s.name as student_name, s.nis 
    FROM wa_queue q
    LEFT JOIN students s ON q.student_id = s.id
    ORDER BY q.id DESC 
    LIMIT 50
  `).all();

  // Statistik antrean
  const stats = db.prepare(`
    SELECT 
      SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) as pending,
      SUM(CASE WHEN status = 'sending' THEN 1 ELSE 0 END) as sending,
      SUM(CASE WHEN status = 'sent' THEN 1 ELSE 0 END) as sent,
      SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed,
      COUNT(*) as total
    FROM wa_queue
  `).get() || { pending: 0, sending: 0, sent: 0, failed: 0, total: 0 };

  res.render('dashboard/whatsapp', {
    statusInfo,
    providerSettings,
    queueLogs,
    stats,
    successMsg: req.query.success || null,
    errorMsg: req.query.error || null
  });
});

// Simpan Pengaturan Provider WhatsApp (Baileys / Fonnte / Wablas)
router.post('/dashboard/whatsapp/settings', isAuthenticated, isAdmin, async (req, res) => {
  try {
    const { wa_provider, wa_fonnte_token, wa_wablas_token, wa_wablas_domain, wa_enabled } = req.body;

    const setStmt = db.prepare("INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value");
    
    const selectedProvider = wa_provider || 'baileys';
    setStmt.run('wa_provider', selectedProvider);
    if (wa_fonnte_token !== undefined) setStmt.run('wa_fonnte_token', wa_fonnte_token.trim());
    if (wa_wablas_token !== undefined) setStmt.run('wa_wablas_token', wa_wablas_token.trim());
    if (wa_wablas_domain !== undefined) setStmt.run('wa_wablas_domain', wa_wablas_domain.trim());
    setStmt.run('wa_enabled', wa_enabled === '1' ? '1' : '0');

    // Pengelolaan socket berdasarkan provider yang dipilih
    if (selectedProvider === 'baileys') {
      whatsapp.initWhatsApp();
    } else if (whatsapp.sock) {
      try {
        console.log('[WhatsApp] Beralih ke Cloud Gateway. Menutup socket Baileys untuk menghemat RAM STB...');
        whatsapp.sock.ev.removeAllListeners();
        whatsapp.sock.end();
        whatsapp.sock = null;
      } catch (_) {}
    }

    res.redirect('/dashboard/whatsapp?success=Pengaturan provider WhatsApp berhasil disimpan!');
  } catch (err) {
    res.redirect(`/dashboard/whatsapp?error=${encodeURIComponent(err.message)}`);
  }
});

// JSON API Status Real-Time WhatsApp untuk Polling di Frontend
router.get('/api/whatsapp/status', isAuthenticated, (req, res) => {
  res.json(whatsapp.getStatus());
});

// Kirim Pesan Uji Coba (Test Send)
router.post('/dashboard/whatsapp/test', isAuthenticated, isAdmin, async (req, res) => {
  try {
    const { test_phone, test_message } = req.body;
    if (!test_phone || !test_message) {
      return res.redirect('/dashboard/whatsapp?error=Nomor tujuan dan pesan uji coba wajib diisi.');
    }

    const currentStatus = whatsapp.getStatus();
    if (currentStatus.status !== 'connected') {
      if (currentStatus.provider === 'fonnte') {
        return res.redirect('/dashboard/whatsapp?error=Token Fonnte belum diisi atau tidak valid. Silakan periksa pengaturan Fonnte.');
      } else if (currentStatus.provider === 'wablas') {
        return res.redirect('/dashboard/whatsapp?error=Token / Domain Wablas belum diisi. Silakan periksa pengaturan Wablas.');
      } else {
        return res.redirect('/dashboard/whatsapp?error=WhatsApp belum terkoneksi. Silakan scan QR code terlebih dahulu.');
      }
    }

    await whatsapp.sendTextMessage(test_phone.trim(), test_message.trim());
    res.redirect('/dashboard/whatsapp?success=Pesan uji coba berhasil terkirim!');
  } catch (err) {
    console.error('Error test send WA:', err);
    res.redirect(`/dashboard/whatsapp?error=${encodeURIComponent(err.message)}`);
  }
});

// Reconnect WhatsApp
router.post('/dashboard/whatsapp/reconnect', isAuthenticated, isAdmin, async (req, res) => {
  try {
    await whatsapp.reconnect();
    res.redirect('/dashboard/whatsapp?success=Memulai ulang koneksi WhatsApp...');
  } catch (err) {
    res.redirect(`/dashboard/whatsapp?error=${encodeURIComponent(err.message)}`);
  }
});

// Bersihkan Cache Pre-Keys & Sesi Usang (Anti-Waiting & Bad MAC Auto-Repair)
router.post('/dashboard/whatsapp/repair-keys', isAuthenticated, isAdmin, async (req, res) => {
  try {
    const cleaned = await whatsapp.cleanStaleSessionKeys();
    res.redirect(`/dashboard/whatsapp?success=Berhasil membersihkan ${cleaned} berkas sesi/pre-key usang. WhatsApp sedang menghubungkan ulang tanpa perlu scan QR.`);
  } catch (err) {
    res.redirect(`/dashboard/whatsapp?error=${encodeURIComponent(err.message)}`);
  }
});

// Logout Sesi WhatsApp
router.post('/dashboard/whatsapp/logout', isAuthenticated, isAdmin, async (req, res) => {
  try {
    await whatsapp.logout();
    res.redirect('/dashboard/whatsapp?success=Sesi WhatsApp berhasil dikeluarkan.');
  } catch (err) {
    res.redirect(`/dashboard/whatsapp?error=${encodeURIComponent(err.message)}`);
  }
});

// Ulangi Antrean Pesan yang Gagal
router.post('/dashboard/whatsapp/retry-queue', isAuthenticated, isAdmin, (req, res) => {
  try {
    const retriedCount = queue.retryFailed();
    res.redirect(`/dashboard/whatsapp?success=${retriedCount} pesan gagal telah dimasukkan kembali ke antrean.`);
  } catch (err) {
    res.redirect(`/dashboard/whatsapp?error=${encodeURIComponent(err.message)}`);
  }
});

module.exports = router;
