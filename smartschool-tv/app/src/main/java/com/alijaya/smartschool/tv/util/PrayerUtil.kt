package com.alijaya.smartschool.tv.util

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class PrayerSchedule(
    val date: String,
    val imsak: String,
    val subuh: String,
    val terbit: String,
    val dzuhur: String,
    val ashar: String,
    val maghrib: String,
    val isya: String,
    val nextPrayerName: String,
    val nextPrayerTime: String
)

/**
 * Layanan Hisab Perhitungan Waktu Sholat Standar Kemenag RI
 * Murni rumus astronomi falakiah matahari matematika offline (tanpa dependensi eksternal)
 */
object PrayerUtil {

    private fun degToRad(deg: Double): Double = (deg * Math.PI) / 180.0
    private fun radToDeg(rad: Double): Double = (rad * 180.0) / Math.PI

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

    private fun formatTime(decimalHours: Double): String {
        if (decimalHours.isNaN()) return "--:--"
        val fixed = fixHour(decimalHours)
        val hours = floor(fixed).toInt()
        val minutes = floor((fixed - hours) * 60).toInt()
        return "%02d:%02d".format(hours, minutes)
    }

    fun calculate(
        year: Int,
        month: Int, // 1 - 12
        day: Int,
        latitude: Double = -6.2088, // Default Jakarta / Jawa
        longitude: Double = 106.8456,
        timezone: Double = 7.0,
        ihtiyatMinutes: Double = 2.0
    ): PrayerSchedule {
        val jd = julianDate(year, month, day)
        val d = jd - 2451545.0

        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val L = fixAngle(q + 1.915 * sin(degToRad(g)) + 0.020 * sin(degToRad(2 * g)))
        val e = 23.439 - 0.00000036 * d

        val sinDelta = sin(degToRad(e)) * sin(degToRad(L))
        val delta = radToDeg(asin(sinDelta))

        val raRad = atan2(cos(degToRad(e)) * sin(degToRad(L)), cos(degToRad(L)))
        val ra = fixAngle(radToDeg(raRad)) / 15.0

        val eqT = (q / 15.0) - ra
        val midday = fixHour(12.0 + timezone - (longitude / 15.0) - eqT)

        fun getHourAngle(altitudeAngle: Double): Double {
            val cosH = (sin(degToRad(altitudeAngle)) - sin(degToRad(latitude)) * sin(degToRad(delta))) /
                    (cos(degToRad(latitude)) * cos(degToRad(delta)))
            return when {
                cosH > 1 -> 0.0
                cosH < -1 -> 180.0
                else -> radToDeg(acos(cosH))
            }
        }

        val tSubuh = getHourAngle(-20.0) / 15.0
        val tTerbit = getHourAngle(-0.833) / 15.0
        val tIsya = getHourAngle(-18.0) / 15.0

        val asrAltitude = radToDeg(atan(1.0 / (1.0 + tan(degToRad(abs(latitude - delta))))))
        val tAshar = getHourAngle(asrAltitude) / 15.0

        val ihtHours = ihtiyatMinutes / 60.0

        val subuhDec = midday - tSubuh + ihtHours
        val terbitDec = midday - tTerbit
        val dzuhurDec = midday + ihtHours
        val asharDec = midday + tAshar + ihtHours
        val maghribDec = midday + tTerbit + ihtHours
        val isyaDec = midday + tIsya + ihtHours
        val imsakDec = subuhDec - (10.0 / 60.0)

        val strSubuh = formatTime(subuhDec)
        val strDzuhur = formatTime(dzuhurDec)
        val strAshar = formatTime(asharDec)
        val strMaghrib = formatTime(maghribDec)
        val strIsya = formatTime(isyaDec)

        // Hitung sholat berikutnya
        val nowCal = Calendar.getInstance()
        val curMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        fun toMin(t: String): Int {
            val parts = t.split(":")
            return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }

        val times = listOf(
            "Subuh" to strSubuh,
            "Dzuhur" to strDzuhur,
            "Ashar" to strAshar,
            "Maghrib" to strMaghrib,
            "Isya" to strIsya
        )

        val next = times.firstOrNull { toMin(it.second) > curMinutes } ?: ("Subuh" to strSubuh)

        return PrayerSchedule(
            date = "%04d-%02d-%02d".format(year, month, day),
            imsak = formatTime(imsakDec),
            subuh = strSubuh,
            terbit = formatTime(terbitDec),
            dzuhur = strDzuhur,
            ashar = strAshar,
            maghrib = strMaghrib,
            isya = strIsya,
            nextPrayerName = next.first,
            nextPrayerTime = next.second
        )
    }

    fun today(
        latitude: Double = -6.2088,
        longitude: Double = 106.8456,
        timezone: Double = 7.0,
        ihtiyatMinutes: Double = 2.0
    ): PrayerSchedule {
        val cal = Calendar.getInstance()
        return calculate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            latitude = latitude,
            longitude = longitude,
            timezone = timezone,
            ihtiyatMinutes = ihtiyatMinutes
        )
    }
}

