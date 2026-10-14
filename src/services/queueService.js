const db = require('../database/db');
const { getTodayWIB } = require('../utils/timeHelper');

/**
 * Service untuk Pengelolaan Antrian Mengaji Santri Multi-Meja Berbasis Kelas
 */
class QueueService {
  /**
   * Helper untuk mendapatkan tanggal hari ini dalam format YYYY-MM-DD (WIB)
   */
  getToday() {
    try {
      return getTodayWIB();
    } catch (_) {
      return new Date().toISOString().split('T')[0];
    }
  }

  /**
   * Daftarkan nomor antrian untuk santri saat presensi datang (Kiosk / manual)
   * Berbasis Ustadz / Guru Pengajar
   */
  assignQueueNumber(studentId, classId, dateStr = null, teacherId = null) {
    const date = dateStr || this.getToday();
    if (!studentId) return null;

    let targetTeacherId = teacherId;
    let targetClassId = classId;

    const studentRow = db.prepare('SELECT class_id, teacher_id FROM students WHERE id = ?').get(studentId);
    if (studentRow) {
      if (!targetTeacherId && studentRow.teacher_id) {
        targetTeacherId = studentRow.teacher_id;
      }
      if (!targetClassId && studentRow.class_id) {
        targetClassId = studentRow.class_id;
      }
    }

    if (!targetTeacherId && targetClassId) {
      const guruRow = db.prepare("SELECT id FROM users WHERE role = 'guru' AND class_id = ? LIMIT 1").get(targetClassId);
      if (guruRow) targetTeacherId = guruRow.id;
    }

    // Cek apakah santri sudah memiliki nomor antrian hari ini
    const existing = db.prepare(`
      SELECT q.*, 
             c.name as class_name, 
             s.name as student_name, 
             s.gender as student_gender,
             u.name as teacher_name
      FROM daily_queues q
      LEFT JOIN classes c ON q.class_id = c.id
      JOIN students s ON q.student_id = s.id
      LEFT JOIN users u ON q.teacher_id = u.id
      WHERE q.student_id = ? AND q.date = ?
    `).get(studentId, date);

    if (existing) {
      const ttsService = require('./ttsService');
      const teacherName = existing.teacher_name || existing.class_name;
      existing.teacher_name = ttsService.formatTeacherName(teacherName);
      existing.desk_name = `Meja ${existing.teacher_name}`;
      return existing;
    }

    // Cek pengaturan mode penomoran antrian:
    // "1" (Aktif) => Pisah per Meja / Ustadz (Santri Ust A nomor 1, Santri Ust B nomor 1)
    // "0" (Nonaktif) => Digabungkan / Global seluruh santri hari ini (1, 2, 3, ...)
    const isPerTeacher = (db.prepare("SELECT value FROM settings WHERE key = 'queue_per_teacher'").get()?.value || '1') === '1';

    // Ambil nomor antrian urutan berikutnya
    let nextRow;
    if (isPerTeacher) {
      if (targetTeacherId) {
        nextRow = db.prepare(`
          SELECT COALESCE(MAX(queue_number), 0) + 1 AS next_num 
          FROM daily_queues 
          WHERE (teacher_id = ? OR (teacher_id IS NULL AND class_id = ?)) AND date = ?
        `).get(targetTeacherId, targetClassId, date);
      } else {
        nextRow = db.prepare(`
          SELECT COALESCE(MAX(queue_number), 0) + 1 AS next_num 
          FROM daily_queues 
          WHERE class_id = ? AND date = ?
        `).get(targetClassId || 1, date);
      }
    } else {
      // Mode Gabungan: Nomor antrian global untuk seluruh santri hari ini
      nextRow = db.prepare(`
        SELECT COALESCE(MAX(queue_number), 0) + 1 AS next_num 
        FROM daily_queues 
        WHERE date = ?
      `).get(date);
    }

    const queueNumber = nextRow ? nextRow.next_num : 1;

    const insertStmt = db.prepare(`
      INSERT INTO daily_queues (student_id, class_id, teacher_id, date, queue_number, status)
      VALUES (?, ?, ?, ?, ?, 'WAITING')
    `);
    const res = insertStmt.run(studentId, targetClassId || 1, targetTeacherId || null, date, queueNumber);

    const created = db.prepare(`
      SELECT q.*, 
             c.name as class_name, 
             s.name as student_name, 
             s.gender as student_gender,
             u.name as teacher_name
      FROM daily_queues q
      LEFT JOIN classes c ON q.class_id = c.id
      JOIN students s ON q.student_id = s.id
      LEFT JOIN users u ON q.teacher_id = u.id
      WHERE q.id = ?
    `).get(res.lastInsertRowid);

    const ttsService = require('./ttsService');
    const teacherName = created.teacher_name || created.class_name;
    created.teacher_name = ttsService.formatTeacherName(teacherName);
    created.desk_name = `Meja ${created.teacher_name}`;

    return created;
  }

  /**
   * Ambil data lengkap seluruh Meja Ustadz / Guru untuk Layar Display TV
   */
  getDisplayData(dateStr = null) {
    const date = dateStr || this.getToday();
    const ttsService = require('./ttsService');

    // 1. Ambil seluruh Ustadz / Guru pengajar yang terdaftar
    const teachers = db.prepare(`
      SELECT u.id as teacher_id, u.name as teacher_name, u.class_id,
             c.name as class_name
      FROM users u
      LEFT JOIN classes c ON u.class_id = c.id
      WHERE u.role = 'guru'
      ORDER BY u.name ASC
    `).all();

    let tables = [];

    if (teachers.length > 0) {
      // Tampilkan meja berdasarkan Meja Ustadz / Guru
      tables = teachers.map((t, idx) => {
        const deskNumber = idx + 1;
        const cleanTeacher = ttsService.formatTeacherName(t.teacher_name);
        const tableName = `Meja ${cleanTeacher}`;

        // Santri yang sedang dipanggil / mengaji di meja ustadz ini
        const current = db.prepare(`
          SELECT q.*, s.name as student_name, s.gender as student_gender, s.nis, s.photo
          FROM daily_queues q
          JOIN students s ON q.student_id = s.id
          WHERE (q.teacher_id = ? OR (q.teacher_id IS NULL AND q.class_id = ?))
            AND q.date = ? 
            AND q.status IN ('CALLING', 'SERVING')
          ORDER BY q.called_at DESC
          LIMIT 1
        `).get(t.teacher_id, t.class_id, date);

        // Daftar santri berikutnya yang sedang menunggu
        const nextWaiting = db.prepare(`
          SELECT q.*, s.name as student_name, s.nis
          FROM daily_queues q
          JOIN students s ON q.student_id = s.id
          WHERE (q.teacher_id = ? OR (q.teacher_id IS NULL AND q.class_id = ?))
            AND q.date = ? 
            AND q.status = 'WAITING'
          ORDER BY q.queue_number ASC
          LIMIT 4
        `).all(t.teacher_id, t.class_id, date);

        // Statistik antrian ustadz ini
        const stats = db.prepare(`
          SELECT 
            COUNT(*) as total,
            COALESCE(SUM(CASE WHEN status = 'WAITING' THEN 1 ELSE 0 END), 0) as waiting,
            COALESCE(SUM(CASE WHEN status IN ('CALLING', 'SERVING') THEN 1 ELSE 0 END), 0) as serving,
            COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) as completed,
            COALESCE(SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END), 0) as skipped
          FROM daily_queues
          WHERE (teacher_id = ? OR (teacher_id IS NULL AND class_id = ?))
            AND date = ?
        `).get(t.teacher_id, t.class_id, date);

        return {
          deskNumber,
          tableId: t.teacher_id,
          classId: t.class_id || t.teacher_id,
          teacherId: t.teacher_id,
          tableName,
          className: cleanTeacher, // Ditampilkan di TV sebagai Ustadz/Guru
          teacherName: t.teacher_name,
          current: current || null,
          nextWaiting: nextWaiting || [],
          stats: stats || { total: 0, waiting: 0, serving: 0, completed: 0, skipped: 0 }
        };
      });
    } else {
      // Fallback jika belum ada akun guru terdaftar, gunakan kelas
      const classes = db.prepare('SELECT * FROM classes ORDER BY name ASC').all();
      tables = classes.map((cls, idx) => {
        const deskNumber = idx + 1;
        const tableName = `Meja ${cls.name}`;
        const current = db.prepare(`
          SELECT q.*, s.name as student_name, s.gender as student_gender, s.nis, s.photo
          FROM daily_queues q
          JOIN students s ON q.student_id = s.id
          WHERE q.class_id = ? AND q.date = ? AND q.status IN ('CALLING', 'SERVING')
          ORDER BY q.called_at DESC
          LIMIT 1
        `).get(cls.id, date);

        const nextWaiting = db.prepare(`
          SELECT q.*, s.name as student_name, s.nis
          FROM daily_queues q
          JOIN students s ON q.student_id = s.id
          WHERE q.class_id = ? AND q.date = ? AND q.status = 'WAITING'
          ORDER BY q.queue_number ASC
          LIMIT 4
        `).all(cls.id, date);

        const stats = db.prepare(`
          SELECT 
            COUNT(*) as total,
            COALESCE(SUM(CASE WHEN status = 'WAITING' THEN 1 ELSE 0 END), 0) as waiting,
            COALESCE(SUM(CASE WHEN status IN ('CALLING', 'SERVING') THEN 1 ELSE 0 END), 0) as serving,
            COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) as completed,
            COALESCE(SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END), 0) as skipped
          FROM daily_queues
          WHERE class_id = ? AND date = ?
        `).get(cls.id, date);

        return {
          deskNumber,
          classId: cls.id,
          tableName,
          className: tableName,
          teacherName: 'Ustadz / Wali Kelas',
          current: current || null,
          nextWaiting: nextWaiting || [],
          stats: stats || { total: 0, waiting: 0, serving: 0, completed: 0, skipped: 0 }
        };
      });
    }

    // Ambil panggilan terakhir dalam 60 detik terakhir untuk audio trigger TV
    const lastCall = db.prepare(`
      SELECT q.id, q.queue_number, q.call_count, q.called_at, 
             s.name as student_name, s.gender as student_gender, 
             COALESCE(u.name, c.name, 'Ustadz') as teacher_name,
             c.name as class_name
      FROM daily_queues q
      JOIN students s ON q.student_id = s.id
      LEFT JOIN users u ON q.teacher_id = u.id
      LEFT JOIN classes c ON q.class_id = c.id
      WHERE q.date = ? AND q.status = 'CALLING' AND q.called_at IS NOT NULL
      ORDER BY q.called_at DESC
      LIMIT 1
    `).get(date);

    if (lastCall) {
      const prefixRow = db.prepare(`SELECT value FROM settings WHERE key = 'queue_calling_prefix'`).get();
      const prefix = (prefixRow && prefixRow.value) ? prefixRow.value : 'Nomor antrian';

      const teacherTarget = ttsService.formatTeacherName(lastCall.teacher_name || lastCall.class_name);

      lastCall.queueId = lastCall.id;
      lastCall.queueNumber = lastCall.queue_number;
      lastCall.callCount = lastCall.call_count || 1;
      lastCall.calledAt = lastCall.called_at;
      lastCall.studentName = lastCall.student_name;
      lastCall.gender = lastCall.student_gender;
      lastCall.className = teacherTarget;
      lastCall.teacherName = teacherTarget;

      // Format panggilan: "Nomor antrian lima, Ahmad Dahlan, silakan menuju Ustadz Alijaya."
      lastCall.speechSentence = ttsService.buildCallSentence(
        prefix,
        lastCall.queue_number,
        lastCall.student_name,
        teacherTarget
      );
    }

    const prayerService = require('./prayerService');

    const schoolNameRow = db.prepare("SELECT value FROM settings WHERE key = 'school_name'").get();
    const schoolLogoRow = db.prepare("SELECT value FROM settings WHERE key = 'school_logo'").get();
    const schoolTaglineRow = db.prepare("SELECT value FROM settings WHERE key = 'school_tagline'").get();
    const annTitleRow = db.prepare("SELECT value FROM settings WHERE key = 'announcement_title'").get();
    const annContentRow = db.prepare("SELECT value FROM settings WHERE key = 'announcement_content'").get();

    const schoolName = (schoolNameRow && schoolNameRow.value) ? schoolNameRow.value : 'Majlis Ta\'lim & TPQ';
    const schoolLogo = (schoolLogoRow && schoolLogoRow.value) ? schoolLogoRow.value : null;
    const schoolTagline = (schoolTaglineRow && schoolTaglineRow.value) ? schoolTaglineRow.value : 'Sistem Presensi RFID USB & Antrian Mengaji Santri';
    
    const annTitle = annTitleRow ? annTitleRow.value : '';
    const annContent = annContentRow ? annContentRow.value : '';

    const parts = [];
    if (schoolName) parts.push(`Selamat Datang di ${schoolName}`);
    if (annTitle || annContent) {
      const fullAnn = [annTitle, annContent].filter(Boolean).join(': ');
      parts.push(fullAnn);
    }
    parts.push('Santri wajib melakukan presensi kartu RFID sebelum mengaji');
    parts.push('Mohon tertib, tenang, dan jaga kebersihan selama kegiatan belajar mengajar');
    const announcement = parts.join('   •••   ');

    return {
      date,
      schoolName,
      school_name: schoolName,
      schoolLogo,
      school_logo: schoolLogo,
      schoolTagline,
      school_tagline: schoolTagline,
      announcement,
      tables,
      lastCall: lastCall || null,
      prayer: prayerService.getTodaySchedule()
    };
  }

  /**
   * Ambil data kerja untuk Meja Panggilan Ustadz (Desk)
   */
  getDeskData(deskId, dateStr = null) {
    const date = dateStr || this.getToday();

    let teacherInfo = db.prepare(`SELECT id, name, class_id FROM users WHERE role = 'guru' AND id = ?`).get(deskId);
    let classInfo = null;

    if (teacherInfo) {
      classInfo = teacherInfo.class_id 
        ? db.prepare('SELECT * FROM classes WHERE id = ?').get(teacherInfo.class_id) 
        : { id: deskId, name: teacherInfo.name };
      if (classInfo) classInfo.teacher_name = teacherInfo.name;
    } else {
      classInfo = db.prepare(`
        SELECT c.*, 
          (SELECT u.name FROM users u WHERE u.role = 'guru' AND u.class_id = c.id LIMIT 1) as teacher_name
        FROM classes c
        WHERE c.id = ?
      `).get(deskId);
      if (classInfo) {
        teacherInfo = db.prepare(`SELECT id, name, class_id FROM users WHERE role = 'guru' AND class_id = ? LIMIT 1`).get(classInfo.id);
      }
    }

    if (!classInfo && !teacherInfo) return null;

    const teacherId = teacherInfo ? teacherInfo.id : null;
    const classId = classInfo ? classInfo.id : null;

    // Santri yang sedang duduk / mengaji saat ini
    const current = db.prepare(`
      SELECT q.*, s.name as student_name, s.nis, s.nisn, s.photo, s.parent_phone
      FROM daily_queues q
      JOIN students s ON q.student_id = s.id
      WHERE ((q.teacher_id = ? AND ? IS NOT NULL) OR (q.teacher_id IS NULL AND q.class_id = ?))
        AND q.date = ? 
        AND q.status IN ('CALLING', 'SERVING')
      ORDER BY q.called_at DESC
      LIMIT 1
    `).get(teacherId, teacherId, classId, date);

    // Daftar antrian santri yang menunggu
    const waitingList = db.prepare(`
      SELECT q.*, s.name as student_name, s.nis, s.photo
      FROM daily_queues q
      JOIN students s ON q.student_id = s.id
      WHERE ((q.teacher_id = ? AND ? IS NOT NULL) OR (q.teacher_id IS NULL AND q.class_id = ?))
        AND q.date = ? 
        AND q.status = 'WAITING'
      ORDER BY q.queue_number ASC
    `).all(teacherId, teacherId, classId, date);

    // Daftar santri yang dilewati (tunda)
    const skippedList = db.prepare(`
      SELECT q.*, s.name as student_name, s.nis, s.photo
      FROM daily_queues q
      JOIN students s ON q.student_id = s.id
      WHERE ((q.teacher_id = ? AND ? IS NOT NULL) OR (q.teacher_id IS NULL AND q.class_id = ?))
        AND q.date = ? 
        AND q.status = 'SKIPPED'
      ORDER BY q.queue_number ASC
    `).all(teacherId, teacherId, classId, date);

    // Daftar santri yang sudah selesai hari ini
    const completedList = db.prepare(`
      SELECT q.*, s.name as student_name, s.nis, s.photo
      FROM daily_queues q
      JOIN students s ON q.student_id = s.id
      WHERE ((q.teacher_id = ? AND ? IS NOT NULL) OR (q.teacher_id IS NULL AND q.class_id = ?))
        AND q.date = ? 
        AND q.status = 'COMPLETED'
      ORDER BY q.completed_at DESC
      LIMIT 15
    `).all(teacherId, teacherId, classId, date);

    // Statistik
    const stats = db.prepare(`
      SELECT 
        COUNT(*) as total,
        COALESCE(SUM(CASE WHEN status = 'WAITING' THEN 1 ELSE 0 END), 0) as waiting,
        COALESCE(SUM(CASE WHEN status IN ('CALLING', 'SERVING') THEN 1 ELSE 0 END), 0) as serving,
        COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) as completed,
        COALESCE(SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END), 0) as skipped
      FROM daily_queues
      WHERE ((teacher_id = ? AND ? IS NOT NULL) OR (teacher_id IS NULL AND class_id = ?))
        AND date = ?
    `).get(teacherId, teacherId, classId, date);

    return {
      classInfo,
      current: current || null,
      waitingList: waitingList || [],
      skippedList: skippedList || [],
      completedList: completedList || [],
      stats: stats || { total: 0, waiting: 0, serving: 0, completed: 0, skipped: 0 }
    };
  }

  /**
   * Panggil Santri Berikutnya (Next)
   */
  callNext(deskId, dateStr = null) {
    const date = dateStr || this.getToday();

    let teacherInfo = db.prepare(`SELECT id, name, class_id FROM users WHERE role = 'guru' AND id = ?`).get(deskId);
    let teacherId = teacherInfo ? teacherInfo.id : null;
    let classId = teacherInfo ? teacherInfo.class_id : deskId;
    if (!teacherId && classId) {
      const g = db.prepare(`SELECT id FROM users WHERE role = 'guru' AND class_id = ? LIMIT 1`).get(classId);
      if (g) teacherId = g.id;
    }

    // Selesaikan santri yang saat ini masih berstatus CALLING / SERVING
    db.prepare(`
      UPDATE daily_queues 
      SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP 
      WHERE ((teacher_id = ? AND ? IS NOT NULL) OR (teacher_id IS NULL AND class_id = ?))
        AND date = ? 
        AND status IN ('CALLING', 'SERVING')
    `).run(teacherId, teacherId, classId, date);

    const isPerTeacher = (db.prepare("SELECT value FROM settings WHERE key = 'queue_per_teacher'").get()?.value || '1') === '1';

    let nextStudent = null;
    if (isPerTeacher && teacherId) {
      // 1. Cari santri urutan teratas yang sedang WAITING di meja ustadz ini
      nextStudent = db.prepare(`
        SELECT * FROM daily_queues 
        WHERE ((teacher_id = ? AND ? IS NOT NULL) OR (teacher_id IS NULL AND class_id = ?))
          AND date = ? 
          AND status = 'WAITING'
        ORDER BY queue_number ASC
        LIMIT 1
      `).get(teacherId, teacherId, classId, date);
    }

    // 2. Fallback: jika mode gabungan ATAU jika antrian ustadz ini kosong namun masih ada santri lain yang WAITING
    if (!nextStudent) {
      nextStudent = db.prepare(`
        SELECT * FROM daily_queues 
        WHERE date = ? AND status = 'WAITING'
        ORDER BY queue_number ASC
        LIMIT 1
      `).get(date);

      if (nextStudent && teacherId) {
        db.prepare('UPDATE daily_queues SET teacher_id = ? WHERE id = ?').run(teacherId, nextStudent.id);
        nextStudent.teacher_id = teacherId;
      }
    }

    if (!nextStudent) {
      return { success: false, message: 'Semua antrian santri yang menunggu sudah dipanggil.' };
    }

    // Ubah status santri ini menjadi CALLING dan beri waktu panggil
    db.prepare(`
      UPDATE daily_queues 
      SET status = 'CALLING', called_at = CURRENT_TIMESTAMP, call_count = 1 
      WHERE id = ?
    `).run(nextStudent.id);

    const called = db.prepare(`
      SELECT q.*, s.name as student_name, s.gender as student_gender,
             COALESCE(u.name, c.name, 'Ustadz') as teacher_name,
             c.name as class_name
      FROM daily_queues q
      JOIN students s ON q.student_id = s.id
      LEFT JOIN users u ON q.teacher_id = u.id
      LEFT JOIN classes c ON q.class_id = c.id
      WHERE q.id = ?
    `).get(nextStudent.id);

    const ttsService = require('./ttsService');
    const teacherTarget = ttsService.formatTeacherName(called.teacher_name || called.class_name);
    called.className = teacherTarget;
    called.teacherName = teacherTarget;
    called.speechSentence = ttsService.buildCallSentence(
      'Nomor antrian',
      called.queue_number,
      called.student_name,
      teacherTarget
    );

    return {
      success: true,
      message: `Memanggil ${called.student_name} (${called.queue_number}) menuju ${teacherTarget}`,
      queue: called
    };
  }

  /**
   * Panggil Langsung Santri Tertentu dari List
   */
  callSpecific(queueId) {
    const target = db.prepare(`SELECT * FROM daily_queues WHERE id = ?`).get(queueId);
    if (!target) return { success: false, message: 'Data antrian tidak ditemukan.' };

    // Selesaikan yang sedang dipanggil di kelas yang sama
    db.prepare(`
      UPDATE daily_queues 
      SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP 
      WHERE class_id = ? AND date = ? AND status IN ('CALLING', 'SERVING') AND id != ?
    `).run(target.class_id, target.date, target.id);

    // Panggil target
    db.prepare(`
      UPDATE daily_queues 
      SET status = 'CALLING', called_at = CURRENT_TIMESTAMP, call_count = COALESCE(call_count, 0) + 1 
      WHERE id = ?
    `).run(queueId);

    const called = db.prepare(`
      SELECT q.*, s.name as student_name, c.name as class_name
      FROM daily_queues q
      JOIN students s ON q.student_id = s.id
      JOIN classes c ON q.class_id = c.id
      WHERE q.id = ?
    `).get(queueId);

    return { success: true, called };
  }

  /**
   * Panggil Ulang (Recall)
   */
  recall(queueId) {
    const target = db.prepare(`SELECT * FROM daily_queues WHERE id = ?`).get(queueId);
    if (!target) return { success: false, message: 'Data antrian tidak ditemukan.' };

    const now = Date.now();
    if (this._lastRecallId === queueId && (now - (this._lastRecallTime || 0) < 2500)) {
      // Abaikan duplicate recall dalam 2.5 detik
    } else {
      this._lastRecallId = queueId;
      this._lastRecallTime = now;
      db.prepare(`
        UPDATE daily_queues 
        SET status = 'CALLING', called_at = CURRENT_TIMESTAMP, call_count = COALESCE(call_count, 0) + 1 
        WHERE id = ?
      `).run(queueId);
    }

    const called = db.prepare(`
      SELECT q.*, s.name as student_name, c.name as class_name
      FROM daily_queues q
      JOIN students s ON q.student_id = s.id
      JOIN classes c ON q.class_id = c.id
      WHERE q.id = ?
    `).get(queueId);

    return { success: true, called };
  }

  /**
   * Lewati Santri (Skip / Tunda)
   */
  skip(queueId) {
    const target = db.prepare(`SELECT * FROM daily_queues WHERE id = ?`).get(queueId);
    if (!target) return { success: false, message: 'Data antrian tidak ditemukan.' };

    db.prepare(`
      UPDATE daily_queues 
      SET status = 'SKIPPED', called_at = NULL 
      WHERE id = ?
    `).run(queueId);

    return { success: true, message: 'Santri berhasil dilewati sementara.' };
  }

  /**
   * Selesaikan Santri ("Selesai saja" tanpa form rumit)
   */
  finish(queueId) {
    const target = db.prepare(`SELECT * FROM daily_queues WHERE id = ?`).get(queueId);
    if (!target) return { success: false, message: 'Data antrian tidak ditemukan.' };

    db.prepare(`
      UPDATE daily_queues 
      SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP 
      WHERE id = ?
    `).run(queueId);

    return { success: true, message: 'Santri telah selesai mengaji.' };
  }
}

module.exports = new QueueService();
