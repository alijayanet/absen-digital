const https = require('https');
const crypto = require('crypto');

/**
 * Service Text-to-Speech (TTS) Suara Manusia Alami Logat Indonesia
 * Menggunakan engine Neural Indonesian Voice (Jernih, Fasih, Natural, & Bebas Ejaan Huruf)
 */
class TTSService {
  constructor() {
    // Cache memory untuk menyimpan audio MP3 (maksimal 200 kalimat unik)
    this.cache = new Map();
    this.maxCacheSize = 200;
  }

  /**
   * Format nama santri agar diucapkan utuh dan mengalir (tidak dieja per huruf)
   */
  formatStudentName(raw) {
    if (!raw) return '';
    let name = String(raw).trim();

    // 1. Bersihkan catatan dalam kurung / kurung siku
    name = name.replace(/\(.*?\)/g, ' ').replace(/\[.*?\]/g, ' ');

    // 2. Ubah huruf kapital (ALL CAPS) menjadi Title Case
    name = name.toLowerCase().replace(/(?:^|\s|\/|-)\S/g, (c) => c.toUpperCase());

    // 3. Ekspansi singkatan nama umum di Indonesia agar diucapkan wajar
    name = name
      .replace(/\bM\.\s*/gi, 'Muhammad ')
      .replace(/\bMuh\.\s*/gi, 'Muhammad ')
      .replace(/\bMoh\.\s*/gi, 'Muhammad ')
      .replace(/\bAch?\.\s*/gi, 'Ahmad ')
      .replace(/\bAbd\.\s*/gi, 'Abdul ')
      .replace(/\bIbnu\.\s*/gi, 'Ibnu ')
      .replace(/\bH\.\s*/gi, 'Haji ')
      .replace(/\bHj\.\s*/gi, 'Hajjah ')
      .replace(/\bSiti\.\s*/gi, 'Siti ');

    // 4. Hilangkan apostrof/tanda kutip (misal Syafi'i -> Syafii)
    name = name.replace(/['`’]/g, '');

    // 5. Bersihkan simbol aneh yang bisa memicu TTS mengeja
    name = name.replace(/[^a-zA-Z0-9\s]/g, ' ');

    // 6. Normalisasi spasi ganda
    return name.replace(/\s+/g, ' ').trim();
  }

  /**
   * Konversi angka nomor antrian ke teks terbilang Bahasa Indonesia
   */
  numberToWords(num) {
    const satuan = ['', 'satu', 'dua', 'tiga', 'empat', 'lima', 'enam', 'tujuh', 'delapan', 'sembilan', 'sepuluh', 'sebelas'];
    const n = parseInt(num, 10);
    if (isNaN(n) || n <= 0) return String(num);

    if (n < 10) {
      return `nol ${satuan[n]}`;
    }
    if (n < 12) {
      return satuan[n];
    }
    if (n < 20) {
      return `${satuan[n - 10]} belas`;
    }
    if (n < 100) {
      const puluhan = Math.floor(n / 10);
      const sisa = n % 10;
      return `${satuan[puluhan]} puluh${sisa !== 0 ? ' ' + satuan[sisa] : ''}`;
    }
    if (n < 200) {
      return `seratus ${this.numberToWords(n - 100).replace(/^nol /, '')}`;
    }
    if (n < 1000) {
      const ratusan = Math.floor(n / 100);
      const sisa = n % 100;
      return `${satuan[ratusan]} ratus${sisa !== 0 ? ' ' + this.numberToWords(sisa).replace(/^nol /, '') : ''}`;
    }
    return String(n);
  }

  /**
   * Format nama ustadz/guru dengan sapaan kehormatan wajar
   */
  formatTeacherName(raw) {
    if (!raw) return 'Ustadz';
    let name = String(raw).trim();
    // Hilangkan kata 'Meja ' jika terbawa
    name = name.replace(/^Meja\s+/i, '');

    // Standarisasi sapaan santun
    name = name
      .replace(/\bBpk\.?\s*/gi, 'Ustadz ')
      .replace(/\bPak\s+/gi, 'Ustadz ')
      .replace(/\bIbu\s+/gi, 'Ustadzah ')
      .replace(/\bHj\.?\s*/gi, 'Ustadzah ')
      .replace(/\bH\.?\s*/gi, 'Ustadz ');

    // Jika belum ada awalan Ustadz / Ustadzah / Guru, tambahkan awalan Ustadz
    if (!/\b(Ustadz|Ustadzah|Guru|Kiai)\b/i.test(name)) {
      name = 'Ustadz ' + name;
    }
    return this.formatStudentName(name);
  }

  /**
   * Format kalimat panggilan antrian yang fasih dan alami
   * Sekarang memanggil menuju Ustadz/Guru (bukan menuju meja generik)
   */
  buildCallSentence(prefix, queueNumber, studentName, teacherName) {
    const pfx = prefix || 'Nomor antrian';
    const numWord = this.numberToWords(queueNumber);
    const nameWord = this.formatStudentName(studentName);
    const teacherWord = this.formatTeacherName(teacherName);

    return `${pfx} ${numWord}, ${nameWord}, silakan menuju ${teacherWord}.`;
  }

  /**
   * Ambil buffer audio MP3 TTS dari cache atau dari layanan Google Translate TTS
   */
  async getAudioBuffer(text) {
    if (!text || typeof text !== 'string') {
      throw new Error('Teks tidak boleh kosong.');
    }

    const cleanText = text.trim();
    const cacheKey = crypto.createHash('md5').update(cleanText).digest('hex');

    // Cek cache memory
    if (this.cache.has(cacheKey)) {
      return this.cache.get(cacheKey);
    }

    // Ambil dari Google Translate TTS Bahasa Indonesia
    const url = `https://translate.google.com/translate_tts?ie=UTF-8&tl=id&client=tw-ob&q=${encodeURIComponent(cleanText)}`;

    const buffer = await new Promise((resolve, reject) => {
      const req = https.get(
        url,
        {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Referer': 'https://translate.google.com/'
          },
          timeout: 6000
        },
        (res) => {
          if (res.statusCode !== 200) {
            return reject(new Error(`TTS upstream error HTTP ${res.statusCode}`));
          }
          const chunks = [];
          res.on('data', (c) => chunks.push(c));
          res.on('end', () => resolve(Buffer.concat(chunks)));
        }
      );

      req.on('error', reject);
      req.on('timeout', () => {
        req.destroy();
        reject(new Error('TTS request timeout'));
      });
    });

    // Simpan ke cache memory
    if (this.cache.size >= this.maxCacheSize) {
      // Hapus entri tertua
      const firstKey = this.cache.keys().next().value;
      this.cache.delete(firstKey);
    }
    this.cache.set(cacheKey, buffer);

    return buffer;
  }
}

module.exports = new TTSService();
