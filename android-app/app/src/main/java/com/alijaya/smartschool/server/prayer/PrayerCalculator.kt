package com.alijaya.smartschool.server.prayer

import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.*

/**
 * Layanan Hisab Perhitungan Waktu Sholat Standar Kementerian Agama RI (Kemenag)
 * Formula Falakiyah Astronomi Murni (100% Offline tanpa internet)
 */
object PrayerCalculator {

    private fun degToRad(deg: Double): Double = deg * Math.PI / 180.0
    private fun radToDeg(rad: Double): Double = rad * 180.0 / Math.PI

    private fun fixAngle(a: Double): Double {
        var res = a - 360.0 * floor(a / 360.0)
        if (res < 0) res += 360.0
        return res
    }

    private fun fixHour(h: Double): Double {
        var res = h - 24.0 * floor(h / 24.0)
        if (res < 0) res += 24.0
        return res
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun formatTime(decHours: Double): String {
        if (decHours.isNaN()) return "--:--"
        val hClean = fixHour(decHours)
        val hours = floor(hClean).toInt()
        val minutes = floor((hClean - hours) * 60).toInt()
        return String.format(Locale.US, "%02d:%02d", hours, minutes)
    }

    private fun timeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            return h * 60 + m
        }
        return 0
    }

    /**
     * Hitung jadwal sholat lengkap hari ini
     */
    fun calculateTodayPrayer(
        latitude: Double = -6.3263,
        longitude: Double = 108.3200,
        timezone: Double = 7.0,
        ihtiyat: Int = 2
    ): JSONObject {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        // 1. Julian Date
        val jd = julianDate(year, month, day)
        val d = jd - 2451545.0

        // 2. Posisi Matahari
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(degToRad(g)) + 0.020 * sin(degToRad(2 * g)))
        val e = 23.439 - 0.00000036 * d

        // 3. Deklinasi & Right Ascension
        val sinDelta = sin(degToRad(e)) * sin(degToRad(l))
        val delta = radToDeg(asin(sinDelta))
        val raRad = atan2(cos(degToRad(e)) * sin(degToRad(l)), cos(degToRad(l)))
        val ra = fixAngle(radToDeg(raRad)) / 15.0

        // 4. Equation of Time (Perata Waktu)
        val eqT = (q / 15.0) - ra

        // 5. Waktu Kulminasi Atas / Zawal (Midday Noon)
        val midday = fixHour(12.0 + timezone - (longitude / 15.0) - eqT)

        // Helper sudut jam matahari
        val getHourAngle: (Double) -> Double = { altDeg ->
            val cosH = (sin(degToRad(altDeg)) - sin(degToRad(latitude)) * sin(degToRad(delta))) /
                    (cos(degToRad(latitude)) * cos(degToRad(delta)))
            when {
                cosH > 1 -> 0.0
                cosH < -1 -> 180.0
                else -> radToDeg(acos(cosH))
            }
        }

        // Kemenag Standard:
        // Subuh: -20°
        // Terbit/Syuruq: -0.833°
        // Isya: -18°
        // Ashar: bayangan = 1 + bayangan tengah hari
        val tSubuh = getHourAngle(-20.0) / 15.0
        val tTerbit = getHourAngle(-0.833) / 15.0
        val tIsya = getHourAngle(-18.0) / 15.0

        val asrAlt = radToDeg(atan(1.0 / (1.0 + tan(degToRad(abs(latitude - delta))))))
        val tAshar = getHourAngle(asrAlt) / 15.0

        val ihtHours = ihtiyat / 60.0

        val subuhDec = midday - tSubuh + ihtHours
        val terbitDec = midday - tTerbit
        val dzuhurDec = midday + ihtHours
        val asharDec = midday + tAshar + ihtHours
        val maghribDec = midday + tTerbit + ihtHours
        val isyaDec = midday + tIsya + ihtHours
        val imsakDec = subuhDec - (10.0 / 60.0)

        val imsakStr = formatTime(imsakDec)
        val subuhStr = formatTime(subuhDec)
        val terbitStr = formatTime(terbitDec)
        val dzuhurStr = formatTime(dzuhurDec)
        val asharStr = formatTime(asharDec)
        val maghribStr = formatTime(maghribDec)
        val isyaStr = formatTime(isyaDec)

        val scheduleObj = JSONObject().apply {
            put("imsak", imsakStr)
            put("subuh", subuhStr)
            put("terbit", terbitStr)
            put("dzuhur", dzuhurStr)
            put("ashar", asharStr)
            put("maghrib", maghribStr)
            put("isya", isyaStr)
            put("timezoneName", "WIB")
        }

        // Tentukan sholat berikutnya & hitung countdown
        val nowHours = cal.get(Calendar.HOUR_OF_DAY)
        val nowMins = cal.get(Calendar.MINUTE)
        val nowSecs = cal.get(Calendar.SECOND)
        val nowTotalSeconds = nowHours * 3600 + nowMins * 60 + nowSecs

        data class PrayerEntry(val key: String, val label: String, val timeStr: String)
        val entries = listOf(
            PrayerEntry("subuh", "Subuh", subuhStr),
            PrayerEntry("terbit", "Syuruq", terbitStr),
            PrayerEntry("dzuhur", "Dzuhur", dzuhurStr),
            PrayerEntry("ashar", "Ashar", asharStr),
            PrayerEntry("maghrib", "Maghrib", maghribStr),
            PrayerEntry("isya", "Isya", isyaStr)
        )

        var nextEntry: PrayerEntry? = null
        var diffSeconds = 0

        for (p in entries) {
            val pSec = timeToMinutes(p.timeStr) * 60
            if (pSec > nowTotalSeconds) {
                nextEntry = p
                diffSeconds = pSec - nowTotalSeconds
                break
            }
        }

        if (nextEntry == null) {
            // Semua jadwal hari ini sudah lewat, berikutnya Subuh besok
            nextEntry = entries[0]
            val subuhTomorrowSec = timeToMinutes(subuhStr) * 60
            diffSeconds = (86400 - nowTotalSeconds) + subuhTomorrowSec
        }

        val cHours = diffSeconds / 3600
        val cMins = (diffSeconds % 3600) / 60
        val cSecs = diffSeconds % 60
        val formattedCountdown = String.format(Locale.US, "%02d:%02d:%02d", cHours, cMins, cSecs)

        val nextInfoObj = JSONObject().apply {
            put("next", JSONObject().apply {
                put("key", nextEntry.key)
                put("label", nextEntry.label)
                put("time", nextEntry.timeStr)
            })
            put("formattedCountdown", formattedCountdown)
        }

        return JSONObject().apply {
            put("schedule", scheduleObj)
            put("nextInfo", nextInfoObj)
        }
    }
}
