const express = require('express');
const router = express.Router();
const db = require('../database/db');
const queueService = require('../services/queueService');
const { isAuthenticated } = require('../middlewares/auth');

/**
 * 1. Halaman Layar Display TV Antrian Mengaji (Publik - Bisa dibuka di Browser Smart TV / Aula / APK)
 */
router.get(['/display', '/queue/display'], (req, res) => {
  const settingsRows = db.prepare('SELECT key, value FROM settings').all();
  const settings = {};
  for (const s of settingsRows) settings[s.key] = s.value;

  const data = queueService.getDisplayData();
  res.render('queue/display', {
    settings,
    initialData: data,
    title: 'Layar Display Antrian Mengaji'
  });
});

/**
 * 2. API Data Display TV (Polling JSON setiap 2-3 detik)
 */
router.get(['/api/display-data', '/api/queue/display-data'], (req, res) => {
  try {
    const data = queueService.getDisplayData();
    res.json({ success: true, ...data });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * 2b. API Audio TTS Suara Manusia Logat Indonesia Alami (Google Neural TTS Stream)
 */
const ttsService = require('../services/ttsService');

router.get(['/api/tts', '/api/queue/tts'], async (req, res) => {
  try {
    const text = req.query.text || '';
    if (!text || !text.trim()) {
      return res.status(400).send('Parameter text wajib diisi');
    }

    const audioBuffer = await ttsService.getAudioBuffer(text);
    res.set({
      'Content-Type': 'audio/mpeg',
      'Content-Length': audioBuffer.length,
      'Cache-Control': 'public, max-age=86400'
    });
    res.end(audioBuffer);
  } catch (err) {
    res.status(502).json({ success: false, message: err.message });
  }
});

/**
 * 3. Halaman Panel Panggilan Meja / Kelas Ustadz (Dashboard)
 */
router.get('/dashboard/queue/desk', isAuthenticated, (req, res) => {
  const user = req.session.user;
  const isGuru = user && user.role === 'guru';

  // Jika login sebagai guru namun belum memiliki kelas binaan
  if (isGuru && !user.class_id) {
    return res.render('queue/desk', {
      currentPath: '/dashboard/queue/desk',
      classes: [],
      selectedClassId: null,
      deskData: null,
      isGuru: true,
      unassignedGuru: true,
      title: 'Panel Panggilan Meja Ustadz'
    });
  }

  // Jika guru: ambil hanya kelas miliknya. Jika admin: ambil seluruh kelas terdaftar
  const classes = (isGuru && user.class_id)
    ? db.prepare('SELECT * FROM classes WHERE id = ?').all(user.class_id)
    : db.prepare('SELECT * FROM classes ORDER BY name ASC').all();

  if (classes.length === 0) {
    return res.render('queue/desk', {
      currentPath: '/dashboard/queue/desk',
      classes: [],
      selectedClassId: null,
      deskData: null,
      isGuru,
      unassignedGuru: false,
      title: 'Panel Panggilan Meja Ustadz'
    });
  }

  // Jika guru: mutlak menggunakan kelas binaannya. Jika admin: dapat memilih via query classId
  let selectedClassId = (isGuru && user.class_id)
    ? user.class_id
    : (req.query.classId ? parseInt(req.query.classId, 10) : classes[0].id);

  // Pastikan classId yang dipilih ada di dalam daftar kelas yang diizinkan
  const classExists = classes.find(c => c.id === selectedClassId);
  if (!classExists) {
    selectedClassId = classes[0].id;
  }

  const deskData = queueService.getDeskData(selectedClassId);

  res.render('queue/desk', {
    currentPath: '/dashboard/queue/desk',
    classes,
    selectedClassId,
    deskData,
    isGuru,
    unassignedGuru: false,
    title: isGuru ? `Meja Mengaji ${deskData?.classInfo?.name || ''}` : 'Panel Panggilan Meja Ustadz'
  });
});

/**
 * 4. API Data Desk untuk Refresh Real-time
 */
router.get('/api/queue/desk-data', isAuthenticated, (req, res) => {
  try {
    const user = req.session.user;
    const isGuru = user && user.role === 'guru';

    // Jika guru, paksa ke kelas miliknya
    let classId = (isGuru && user.class_id) ? user.class_id : parseInt(req.query.classId, 10);
    if (!classId) {
      return res.status(400).json({ success: false, message: 'Class ID diperlukan atau akun guru belum memiliki kelas binaan.' });
    }

    const deskData = queueService.getDeskData(classId);
    res.json({ success: true, deskData });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * 5. API Aksi Panggilan Ustadz (Next, Recall, Skip, Finish)
 */
router.post('/api/queue/action', isAuthenticated, (req, res) => {
  try {
    const user = req.session.user;
    const isGuru = user && user.role === 'guru';
    let { action, classId, queueId } = req.body;

    // Jika login sebagai guru, batasi aksi secara ketat hanya untuk kelas binaan guru tersebut
    if (isGuru) {
      if (!user.class_id) {
        return res.status(403).json({ success: false, message: 'Akun Anda belum memiliki kelas binaan / meja mengaji.' });
      }
      classId = user.class_id;

      // Jika ada target queueId, pastikan nomor antrian tersebut memang milik kelas guru yang sedang login
      if (queueId) {
        const queueRow = db.prepare('SELECT class_id FROM daily_queues WHERE id = ?').get(queueId);
        if (queueRow && queueRow.class_id !== user.class_id) {
          return res.status(403).json({ success: false, message: 'Anda hanya berhak memproses antrian santri di meja kelas Anda sendiri.' });
        }
      }
    }

    if (action === 'NEXT') {
      if (!classId) return res.status(400).json({ success: false, message: 'Class ID diperlukan' });
      const result = queueService.callNext(classId);
      return res.json(result);
    }

    if (action === 'RECALL') {
      if (!queueId) return res.status(400).json({ success: false, message: 'Queue ID diperlukan' });
      const result = queueService.recall(queueId);
      return res.json(result);
    }

    if (action === 'SPECIFIC') {
      if (!queueId) return res.status(400).json({ success: false, message: 'Queue ID diperlukan' });
      const result = queueService.callSpecific(queueId);
      return res.json(result);
    }

    if (action === 'SKIP') {
      if (!queueId) return res.status(400).json({ success: false, message: 'Queue ID diperlukan' });
      const result = queueService.skip(queueId);
      return res.json(result);
    }

    if (action === 'FINISH') {
      if (!queueId) return res.status(400).json({ success: false, message: 'Queue ID diperlukan' });
      const result = queueService.finish(queueId);
      return res.json(result);
    }

    return res.status(400).json({ success: false, message: 'Aksi antrian tidak valid' });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
