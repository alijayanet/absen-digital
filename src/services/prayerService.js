const db = require('../database/db');

/**
 * Layanan Hisab Perhitungan Waktu Sholat Standar Kementerian Agama RI (Kemenag)
 * Menggunakan rumus astronomi falakiah matahari murni (offline, tanpa dependensi eksternal)
 */
class PrayerService {
  constructor() {
    this.degToRad = (deg) => (deg * Math.PI) / 180.0;
    this.radToDeg = (rad) => (rad * 180.0) / Math.PI;
  }

  /**
   * Normalisasi sudut 0 - 360 derajat
   */
  fixAngle(a) {
    a = a - 360.0 * Math.floor(a / 360.0);
    return a < 0 ? a + 360.0 : a;
  }

  /**
   * Normalisasi jam 0 - 24
   */
  fixHour(h) {
    h = h - 24.0 * Math.floor(h / 24.0);
    return h < 0 ? h + 24.0 : h;
  }

  /**
   * Hitung Julian Date dari tanggal kalender
   */
  julianDate(year, month, day) {
    if (month <= 2) {
      year -= 1;
      month += 12;
    }
    const a = Math.floor(year / 100);
    const b = 2 - a + Math.floor(a / 4);
    return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
  }

  /**
   * Format desimal jam menjadi string "HH:mm"
   */
  formatTime(decimalHours) {
    if (isNaN(decimalHours)) return '--:--';
    decimalHours = this.fixHour(decimalHours);
    const hours = Math.floor(decimalHours);
    const minutes = Math.floor((decimalHours - hours) * 60);
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
  }

  /**
   * Konversi "HH:mm" ke menit dari tengah malam (0 - 1439)
   */
  timeToMinutes(timeStr) {
    if (!timeStr || !timeStr.includes(':')) return 0;
    const [h, m] = timeStr.split(':').map(Number);
    return h * 60 + m;
  }

  /**
   * Hitung jadwal sholat lengkap berdasarkan tanggal dan koordinat
   * @param {Date} date - Objek tanggal
   * @param {number} latitude - Garis lintang (misal -6.3263 untuk Indramayu)
   * @param {number} longitude - Garis bujur (misal 108.3200 untuk Indramayu)
   * @param {number} timezone - Zona waktu (WIB = 7, WITA = 8, WIT = 9)
   * @param {number} ihtiyat - Menit pengaman (Kemenag standard = 2 menit)
   */
  calculate(date = new Date(), latitude = -6.3263, longitude = 108.3200, timezone = 7, ihtiyat = 2) {
    const lat = Number(latitude);
    const lng = Number(longitude);
    const tz = Number(timezone);
    const iht = Number(ihtiyat);

    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    const day = date.getDate();

    // 1. Julian Date & hari sejak J2000.0
    const jd = this.julianDate(year, month, day);
    const d = jd - 2451545.0;

    // 2. Posisi Astronomi Matahari
    const g = this.fixAngle(357.529 + 0.98560028 * d); // Anomali rata-rata
    const q = this.fixAngle(280.459 + 0.98564736 * d); // Bujur rata-rata
    const L = this.fixAngle(q + 1.915 * Math.sin(this.degToRad(g)) + 0.020 * Math.sin(this.degToRad(2 * g))); // Bujur ekliptika
    const e = 23.439 - 0.00000036 * d; // Kemiringan sumbu bumi

    // 3. Deklinasi Matahari & Right Ascension
    const sinDelta = Math.sin(this.degToRad(e)) * Math.sin(this.degToRad(L));
    const delta = this.radToDeg(Math.asin(sinDelta)); // Deklinasi (-23.44 s/d +23.44)

    const raRad = Math.atan2(Math.cos(this.degToRad(e)) * Math.sin(this.degToRad(L)), Math.cos(this.degToRad(L)));
    let ra = this.fixAngle(this.radToDeg(raRad)) / 15.0; // Right ascension dalam jam

    // 4. Equation of Time (Perata Waktu) dalam jam
    const eqT = (q / 15.0) - ra;

    // 5. Waktu Kulminasi Atas / Zawal (Midday Solar Noon)
    const midday = this.fixHour(12 + tz - (lng / 15.0) - eqT);

    // Helper menghitung sudut jam matahari (t) untuk ketinggian matahari (h)
    const getHourAngle = (altitudeAngle) => {
      const cosH = (Math.sin(this.degToRad(altitudeAngle)) - Math.sin(this.degToRad(lat)) * Math.sin(this.degToRad(delta))) /
                    (Math.cos(this.degToRad(lat)) * Math.cos(this.degToRad(delta)));
      if (cosH > 1) return 0; // Matahari selalu di bawah sudut ini
      if (cosH < -1) return 180; // Matahari selalu di atas sudut ini
      return this.radToDeg(Math.acos(cosH));
    };

    // Standard Kemenag RI:
    // Subuh: sudut matahari -20°
    // Terbit: sudut matahari -0.833°
    // Isya: sudut matahari -18°
    // Ashar: bayangan = 1 + bayangan tengah hari (Madzhab Syafi'i)
    const tSubuh = getHourAngle(-20.0) / 15.0;
    const tTerbit = getHourAngle(-0.833) / 15.0;
    const tIsya = getHourAngle(-18.0) / 15.0;

    // Ketinggian matahari waktu Ashar
    const asrAltitude = this.radToDeg(Math.atan(1.0 / (1.0 + Math.tan(this.degToRad(Math.abs(lat - delta))))));
    const tAshar = getHourAngle(asrAltitude) / 15.0;

    // Ihtiyat pengaman (menit ke jam)
    const ihtiyatHours = iht / 60.0;

    // Hasil perhitungan waktu sholat
    const subuhDec = midday - tSubuh + ihtiyatHours;
    const terbitDec = midday - tTerbit;
    const dzuhurDec = midday + ihtiyatHours;
    const asharDec = midday + tAshar + ihtiyatHours;
    const maghribDec = midday + tTerbit + ihtiyatHours;
    const isyaDec = midday + tIsya + ihtiyatHours;
    const imsakDec = subuhDec - (10.0 / 60.0); // Imsak = 10 menit sebelum Subuh

    return {
      date: date.toISOString().split('T')[0],
      imsak: this.formatTime(imsakDec),
      subuh: this.formatTime(subuhDec),
      terbit: this.formatTime(terbitDec),
      dzuhur: this.formatTime(dzuhurDec),
      ashar: this.formatTime(asharDec),
      maghrib: this.formatTime(maghribDec),
      isya: this.formatTime(isyaDec),
      coordinates: { latitude: lat, longitude: lng },
      timezone: tz,
      ihtiyat: iht
    };
  }

  /**
   * Tentukan zona waktu otomatis berdasarkan garis bujur (Longitude) Indonesia:
   * - < 114.0 : WIB (UTC+7) (Sumatera, Jawa, Madura, Kalbar, Kalteng)
   * - 114.0 s/d 125.0 : WITA (UTC+8) (Kalsel, Kaltim, Kaltara, Sulawesi, Bali, NTB, NTT)
   * - >= 125.0 : WIT (UTC+9) (Maluku, Maluku Utara, Papua)
   */
  determineTimezone(longitude) {
    const lng = parseFloat(longitude);
    if (isNaN(lng)) return 7;
    if (lng >= 125.0) return 9;
    if (lng >= 114.0) return 8;
    return 7;
  }

  /**
   * Dapatkan waktu sekarang (jam, menit, detik) tepat di zona waktu target (WIB/WITA/WIT)
   * Menggunakan Intl.DateTimeFormat agar tidak terpengaruh oleh jam UTC server
   */
  getNowInTimezone(tz) {
    const tzNum = Number(tz) || 7;
    const tzMap = { 7: 'Asia/Jakarta', 8: 'Asia/Makassar', 9: 'Asia/Jayapura' };
    const timeZone = tzMap[tzNum] || 'Asia/Jakarta';
    const now = new Date();
    
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone,
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
      hour12: false
    }).formatToParts(now);

    const getPart = (type) => parts.find(p => p.type === type).value;
    const hour = parseInt(getPart('hour'));
    const minute = parseInt(getPart('minute'));
    const second = parseInt(getPart('second'));
    const year = parseInt(getPart('year'));
    const month = parseInt(getPart('month'));
    const day = parseInt(getPart('day'));

    return {
      year,
      month,
      day,
      hour: hour === 24 ? 0 : hour,
      minute,
      second,
      timeZone,
      timeZoneLabel: tzNum === 9 ? 'WIT' : (tzNum === 8 ? 'WITA' : 'WIB')
    };
  }

  /**
   * Ambil data jadwal sholat hari ini berdasarkan pengaturan database
   */
  getTodaySchedule() {
    const settingsRows = db.prepare(`
      SELECT key, value FROM settings 
      WHERE key IN (
        'prayer_enabled', 'prayer_city_name', 'prayer_latitude', 
        'prayer_longitude', 'prayer_ihtiyat', 'prayer_alert_enabled', 
        'prayer_iqomah_duration', 'prayer_timezone'
      )
    `).all();

    const settings = {
      prayer_enabled: '1',
      prayer_city_name: 'Kab. Indramayu',
      prayer_latitude: '-6.3263',
      prayer_longitude: '108.3200',
      prayer_ihtiyat: '2',
      prayer_alert_enabled: '1',
      prayer_iqomah_duration: '10',
      prayer_timezone: ''
    };

    for (const row of settingsRows) {
      if (row.value !== null && row.value !== undefined && row.value !== '') {
        settings[row.key] = row.value;
      }
    }

    // Tentukan zona waktu: jika belum diatur manual, deteksi otomatis dari Longitude peta
    let tz = parseInt(settings.prayer_timezone);
    if (isNaN(tz) || tz === 0) {
      tz = this.determineTimezone(settings.prayer_longitude);
    }

    const localTime = this.getNowInTimezone(tz);
    const targetDate = new Date(Date.UTC(localTime.year, localTime.month - 1, localTime.day, 12, 0, 0));

    const schedule = this.calculate(
      targetDate, 
      parseFloat(settings.prayer_latitude) || -6.3263, 
      parseFloat(settings.prayer_longitude) || 108.3200, 
      tz, 
      parseInt(settings.prayer_ihtiyat) || 2
    );

    // Tentukan waktu sholat berikutnya & sholat aktif berdasarkan jam lokal di zona waktu tersebut
    const nowMinutes = localTime.hour * 60 + localTime.minute;
    const nextInfo = this.determineNextPrayer(schedule, nowMinutes, localTime.second);

    return {
      settings: {
        ...settings,
        prayer_timezone: String(tz),
        prayer_timezone_name: localTime.timeZoneLabel
      },
      schedule: {
        ...schedule,
        timezone: tz,
        timezoneName: localTime.timeZoneLabel
      },
      localTime,
      nextInfo
    };
  }

  /**
   * Menentukan waktu sholat berikutnya dan sisa countdown detik
   */
  determineNextPrayer(schedule, nowMinutes, nowSeconds = 0) {
    const prayerList = [
      { key: 'subuh', label: 'Subuh', time: schedule.subuh },
      { key: 'terbit', label: 'Syuruq', time: schedule.terbit },
      { key: 'dzuhur', label: 'Dzuhur', time: schedule.dzuhur },
      { key: 'ashar', label: 'Ashar', time: schedule.ashar },
      { key: 'maghrib', label: 'Maghrib', time: schedule.maghrib },
      { key: 'isya', label: 'Isya', time: schedule.isya }
    ];

    let nextPrayer = null;
    let activePrayer = null;

    for (let i = 0; i < prayerList.length; i++) {
      const p = prayerList[i];
      const pMinutes = this.timeToMinutes(p.time);

      if (nowMinutes < pMinutes) {
        nextPrayer = p;
        activePrayer = i > 0 ? prayerList[i - 1] : prayerList[prayerList.length - 1];
        break;
      }
    }

    // Jika sudah melewati Isya, sholat berikutnya adalah Subuh besok
    if (!nextPrayer) {
      nextPrayer = prayerList[0]; // Subuh
      activePrayer = prayerList[prayerList.length - 1]; // Isya
    }

    // Hitung sisa detik menuju waktu sholat berikutnya
    let targetMinutes = this.timeToMinutes(nextPrayer.time);
    if (targetMinutes <= nowMinutes) {
      targetMinutes += 24 * 60; // Hari berikutnya
    }

    const diffMinutes = targetMinutes - nowMinutes - 1;
    const diffSeconds = 60 - nowSeconds;
    const totalRemainingSeconds = Math.max(0, diffMinutes * 60 + diffSeconds);

    return {
      next: nextPrayer,
      active: activePrayer,
      remainingSeconds: totalRemainingSeconds,
      formattedCountdown: this.formatCountdown(totalRemainingSeconds)
    };
  }

  /**
   * Format detik menjadi "HH:mm:ss"
   */
  formatCountdown(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }
}

module.exports = new PrayerService();
