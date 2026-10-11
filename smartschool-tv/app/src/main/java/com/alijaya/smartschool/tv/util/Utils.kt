package com.alijaya.smartschool.tv.util

import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Passwords {
    private const val ITER = 10_000
    private val rnd = SecureRandom()

    fun hash(plain: String): String {
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        return "pbkdf2$" + ITER + "$" + b64(salt) + "$" + b64(derive(plain, salt, ITER))
    }

    fun verify(plain: String, stored: String): Boolean {
        val p = stored.split("$")
        if (p.size != 4 || p[0] != "pbkdf2") return false
        val salt = Base64.decode(p[2], Base64.NO_WRAP)
        val expected = Base64.decode(p[3], Base64.NO_WRAP)
        val actual = derive(plain, salt, p[1].toInt())
        return java.security.MessageDigest.isEqual(expected, actual)
    }

    private fun derive(plain: String, salt: ByteArray, iter: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(plain.toCharArray(), salt, iter, 256)).encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
}

object Tokens {
    private val rnd = SecureRandom()
    private const val ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun random(len: Int = 10) = (1..len).map { ALPHA[rnd.nextInt(ALPHA.length)] }.joinToString("")
}

object Clock {
    val zone: ZoneId = ZoneId.of("Asia/Jakarta")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    fun today(): String = LocalDate.now(zone).toString()
    fun nowTime(): String = LocalTime.now(zone).format(timeFmt)
}
