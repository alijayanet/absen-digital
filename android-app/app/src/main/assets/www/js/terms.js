/**
 * SmartSchool Universal Terminology Engine (terms.js)
 * Mendukung 3 Mode Instansi:
 * 1. 'santri'   : Pesantren, TPQ, Rumah Tahfidz, Madrasah Diniyah
 * 2. 'murid'    : Sekolah Umum (PAUD, TK, SD, SMP, SMA, SMK)
 * 3. 'karyawan' : Perusahaan, Instansi, Kantor, Pabrik, Yayasan
 */

const TermDictionaries = {
  santri: {
    type: 'santri',
    singular: 'Santri',
    plural: 'Santri',
    male: 'Santri (Laki-laki)',
    female: 'Santriwati (Perempuan)',
    shortMale: 'Santri',
    shortFemale: 'Santriwati',
    guardian: 'Wali Santri',
    guardianName: 'Nama Wali Santri',
    guardianPhone: 'No. WA Wali Santri',
    idLabel: 'NIS',
    idFull: 'Nomor Induk Santri (NIS)',
    idPlaceholder: 'Contoh: 1001',
    groupLabel: 'Kelas',
    groupFull: 'Kelas / Jenjang',
    groupPlural: 'Daftar Kelas',
    groupLevel: 'Jenjang / Tingkat',
    groupLeader: 'Wali Kelas',
    teacherLabel: 'Guru / Wali',
    teacherSingular: 'Guru / Ustadz',
    teacherPlural: 'Daftar Guru & Wali Kelas',
    teacherLeaderCol: 'Wali Kelas Untuk',
    teacherMale: 'Ustadz (L)',
    teacherFemale: 'Ustadzah (P)',
    teacherMaleShort: 'Ust.',
    teacherFemaleShort: 'Ustdz.',
    schoolLabel: 'Nama Pesantren / Musholla',
    headLabel: 'Pimpinan / Pengasuh',
    cardTitle: 'KARTU SANTRI RESMI',
    kioskSubtitle: 'Presensi Mandiri Digital (RFID & QR Code)',
    kioskTapPrompt: 'Tap Kartu RFID Santri atau scan QR Code pada mesin scanner USB.',
    navStudents: 'Santri',
    navClasses: 'Kelas',
    navTeachers: 'Guru / Wali',
    studentModalTitle: 'Data Santri',
    emptyStudents: 'Tidak ada data santri yang ditemukan.',
    scanSuccessText: 'Santri atas nama',
    attendanceReportTitle: 'Laporan Presensi Santri'
  },
  murid: {
    type: 'murid',
    singular: 'Murid',
    plural: 'Murid',
    male: 'Siswa (Laki-laki)',
    female: 'Siswi (Perempuan)',
    shortMale: 'Siswa',
    shortFemale: 'Siswi',
    guardian: 'Orang Tua / Wali',
    guardianName: 'Nama Orang Tua / Wali',
    guardianPhone: 'No. WA Orang Tua / Wali',
    idLabel: 'NIS / NISN',
    idFull: 'Nomor Induk Siswa (NIS/NISN)',
    idPlaceholder: 'Contoh: 202401',
    groupLabel: 'Kelas',
    groupFull: 'Kelas / Rombel',
    groupPlural: 'Daftar Kelas & Rombel',
    groupLevel: 'Tingkat / Jenjang',
    groupLeader: 'Wali Kelas',
    teacherLabel: 'Dewan Guru',
    teacherSingular: 'Bapak / Ibu Guru',
    teacherPlural: 'Daftar Dewan Guru',
    teacherLeaderCol: 'Wali Kelas Untuk',
    teacherMale: 'Bapak Guru (L)',
    teacherFemale: 'Ibu Guru (P)',
    teacherMaleShort: 'Bpk.',
    teacherFemaleShort: 'Ibu',
    schoolLabel: 'Nama Sekolah / Madrasah',
    headLabel: 'Kepala Sekolah',
    cardTitle: 'KARTU PELAJAR RESMI',
    kioskSubtitle: 'Presensi Siswa Digital (RFID & QR Code)',
    kioskTapPrompt: 'Tap Kartu Pelajar atau scan QR Code pada mesin scanner USB.',
    navStudents: 'Murid',
    navClasses: 'Kelas',
    navTeachers: 'Dewan Guru',
    studentModalTitle: 'Data Murid / Siswa',
    emptyStudents: 'Tidak ada data murid yang ditemukan.',
    scanSuccessText: 'Siswa atas nama',
    attendanceReportTitle: 'Laporan Presensi Siswa'
  },
  karyawan: {
    type: 'karyawan',
    singular: 'Karyawan',
    plural: 'Karyawan',
    male: 'Karyawan (Laki-laki)',
    female: 'Karyawati (Perempuan)',
    shortMale: 'Karyawan',
    shortFemale: 'Karyawati',
    guardian: 'Kontak Darurat / Keluarga',
    guardianName: 'Nama Kontak Darurat',
    guardianPhone: 'No. WA Kontak Darurat',
    idLabel: 'NIK / NIP',
    idFull: 'Nomor Induk Karyawan (NIK/NIP)',
    idPlaceholder: 'Contoh: EMP001',
    groupLabel: 'Divisi',
    groupFull: 'Divisi / Unit Kerja',
    groupPlural: 'Daftar Divisi / Unit Kerja',
    groupLevel: 'Jabatan / Level',
    groupLeader: 'Kepala Divisi / Supervisor',
    teacherLabel: 'Pimpinan / HRD',
    teacherSingular: 'Pimpinan / Supervisor',
    teacherPlural: 'Daftar Pimpinan & HRD',
    teacherLeaderCol: 'Kepala Divisi Untuk',
    teacherMale: 'Bapak (L)',
    teacherFemale: 'Ibu (P)',
    teacherMaleShort: 'Bpk.',
    teacherFemaleShort: 'Ibu',
    schoolLabel: 'Nama Perusahaan / Instansi',
    headLabel: 'Direktur / Pimpinan Lembaga',
    cardTitle: 'KARTU IDENTITAS KARYAWAN',
    kioskSubtitle: 'Presensi Karyawan Digital (RFID & QR Code)',
    kioskTapPrompt: 'Tap ID Card Karyawan atau scan QR Code pada mesin scanner USB.',
    navStudents: 'Karyawan',
    navClasses: 'Divisi',
    navTeachers: 'Pimpinan / HRD',
    studentModalTitle: 'Data Karyawan / Pegawai',
    emptyStudents: 'Tidak ada data karyawan yang ditemukan.',
    scanSuccessText: 'Karyawan atas nama',
    attendanceReportTitle: 'Laporan Presensi Karyawan'
  }
};

window.AppTerms = {
  currentType: 'santri',
  currentLogo: '',

  init: function() {
    // 1. Ambil dari localStorage untuk loading kilat (tanpa jeda/kedip)
    const cachedType = localStorage.getItem('smartschool_entity_type');
    if (cachedType && TermDictionaries[cachedType]) {
      this.currentType = cachedType;
    }
    const cachedLogo = localStorage.getItem('smartschool_logo');
    if (cachedLogo) {
      this.currentLogo = cachedLogo;
    }
    this.applyToDom();

    // 2. Sinkronisasi dengan server settings secara asinkron
    fetch('/api/settings')
      .then(res => res.json())
      .then(cfg => {
        if (!cfg) return;
        if (cfg.entity_type && TermDictionaries[cfg.entity_type]) {
          if (this.currentType !== cfg.entity_type) {
            this.setEntityType(cfg.entity_type);
          }
        }
        if (cfg.school_logo !== undefined && cfg.school_logo !== this.currentLogo) {
          this.setLogo(cfg.school_logo);
        }
      })
      .catch(err => console.warn('Could not sync terms/settings from server:', err));
  },

  getEntityType: function() {
    return this.currentType;
  },

  setEntityType: function(type) {
    if (!TermDictionaries[type]) type = 'santri';
    this.currentType = type;
    localStorage.setItem('smartschool_entity_type', type);
    this.applyToDom();
    window.dispatchEvent(new CustomEvent('termsChanged', { detail: this.getDict() }));
  },

  getLogo: function() {
    return this.currentLogo;
  },

  setLogo: function(logoData) {
    this.currentLogo = logoData || '';
    if (this.currentLogo) {
      localStorage.setItem('smartschool_logo', this.currentLogo);
    } else {
      localStorage.removeItem('smartschool_logo');
    }
    this.applyLogoToDom();
    window.dispatchEvent(new CustomEvent('logoChanged', { detail: { logo: this.currentLogo } }));
  },

  getDict: function() {
    return TermDictionaries[this.currentType] || TermDictionaries.santri;
  },

  get: function(key, fallback = '') {
    const dict = this.getDict();
    return dict[key] !== undefined ? dict[key] : fallback;
  },

  applyLogoToDom: function(root = document) {
    const logo = this.currentLogo;
    root.querySelectorAll('[data-brand-logo]').forEach(el => {
      if (logo) {
        el.innerHTML = `<img src="${logo}" alt="Logo" class="w-full h-full object-contain p-0.5 rounded-xl">`;
        el.classList.remove('bg-teal-600', 'text-white');
        el.classList.add('bg-white', 'shadow-sm');
      } else {
        el.innerHTML = `🏫`;
        el.classList.add('bg-teal-600', 'text-white');
        el.classList.remove('bg-white');
      }
    });
  },

  applyToDom: function(root = document) {
    const dict = this.getDict();

    // 1. Ganti teks elemen data-term
    root.querySelectorAll('[data-term]').forEach(el => {
      const key = el.getAttribute('data-term');
      if (dict[key] !== undefined) {
        el.textContent = dict[key];
      }
    });

    // 2. Ganti placeholder input data-term-placeholder
    root.querySelectorAll('[data-term-placeholder]').forEach(el => {
      const key = el.getAttribute('data-term-placeholder');
      if (dict[key] !== undefined) {
        el.setAttribute('placeholder', dict[key]);
      }
    });

    // 3. Ganti title data-term-title
    root.querySelectorAll('[data-term-title]').forEach(el => {
      const key = el.getAttribute('data-term-title');
      if (dict[key] !== undefined) {
        el.setAttribute('title', dict[key]);
      }
    });

    // 4. Update Navigasi
    root.querySelectorAll('[data-nav="students"]').forEach(el => el.textContent = dict.navStudents);
    root.querySelectorAll('[data-nav="classes"]').forEach(el => el.textContent = dict.navClasses);
    root.querySelectorAll('[data-nav="teachers"]').forEach(el => el.textContent = dict.navTeachers);

    // 5. Update Logo
    this.applyLogoToDom(root);

    // 6. Bind Mobile Navigation Toggles
    this.initMobileNav();
  },

  toggleMobileNav: function() {
    const menu = document.getElementById('mobileNavMenu');
    if (menu) {
      menu.classList.toggle('hidden');
    }
  },

  checkAdminAuth: function() {
    const path = window.location.pathname;
    // Pengecualian: halaman Kiosk publik dan halaman Login tidak memerlukan otentikasi
    if (path.includes('/kiosk') || path.includes('/login')) {
      return;
    }

    const token = localStorage.getItem('admin_token');
    const hasCookieToken = document.cookie.split(';').some(item => item.trim().startsWith('admin_token='));

    if (!token && !hasCookieToken) {
      window.location.href = '/login';
    }
  },

  logoutAdmin: function() {
    if (confirm('Apakah Anda yakin ingin keluar dari panel admin?')) {
      localStorage.removeItem('admin_token');
      document.cookie = 'admin_token=; path=/; max-age=0';
      window.location.href = '/login';
    }
  },

  initMobileNav: function() {
    document.querySelectorAll('[data-mobile-nav-toggle]').forEach(btn => {
      btn.onclick = (e) => {
        e.preventDefault();
        window.AppTerms.toggleMobileNav();
      };
    });

    document.querySelectorAll('[data-admin-logout]').forEach(btn => {
      btn.onclick = (e) => {
        e.preventDefault();
        window.AppTerms.logoutAdmin();
      };
    });
  }
};

window.toggleMobileNav = function() {
  if (window.AppTerms) window.AppTerms.toggleMobileNav();
};

window.logoutAdmin = function() {
  if (window.AppTerms) window.AppTerms.logoutAdmin();
};

// Inisialisasi otomatis saat dokumen siap
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => {
    window.AppTerms.checkAdminAuth();
    window.AppTerms.init();
  });
} else {
  window.AppTerms.checkAdminAuth();
  window.AppTerms.init();
}
