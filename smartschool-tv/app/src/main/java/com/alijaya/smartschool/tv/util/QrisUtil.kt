package com.alijaya.smartschool.tv.util

object QrisUtil {

    private fun normalize(raw: String): String {
        var s = raw.replace("\r", "").replace("\n", "").replace("\t", "").trim()
        val idx = s.indexOf("000201")
        if (idx > 0) s = s.substring(idx)
        val lastCrc = s.lastIndexOf("6304")
        if (lastCrc >= 0 && s.length >= lastCrc + 8) {
            s = s.substring(0, lastCrc + 8)
        }
        return s
    }

    private fun crc16(input: String): Int {
        var crc = 0xFFFF
        for (i in input.indices) {
            crc = crc xor ((input[i].code and 0xFF) shl 8)
            for (b in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) and 0xFFFF
                else (crc shl 1) and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    data class TlvItem(val tag: String, val value: String)

    private fun parseTlv(input: String): List<TlvItem> {
        val raw = normalize(input)
        if (raw.length < 8) throw IllegalArgumentException("Format QRIS terlalu pendek")
        val list = mutableListOf<TlvItem>()
        var i = 0
        while (i < raw.length) {
            if (i + 4 > raw.length) break
            val tag = raw.substring(i, i + 2)
            val len = raw.substring(i + 2, i + 4).toIntOrNull() ?: break
            val start = i + 4
            val end = (start + len).coerceAtMost(raw.length)
            val value = raw.substring(start, end)
            list.add(TlvItem(tag, value))
            i = end
        }
        return list
    }

    private fun buildTlv(items: List<TlvItem>): String {
        val sb = StringBuilder()
        for (item in items) {
            val len = item.value.length
            sb.append(item.tag)
            sb.append(String.format("%02d", len))
            sb.append(item.value)
        }
        return sb.toString()
    }

    fun convertStaticToDynamic(staticPayload: String, amount: Long): String {
        if (amount <= 0) throw IllegalArgumentException("Nominal QRIS harus lebih besar dari 0")
        val source = parseTlv(staticPayload)
        val managed = setOf("54", "55", "56", "57", "63")
        val result = mutableListOf<TlvItem>()
        var amountInserted = false

        for (el in source) {
            if (el.tag in managed) continue
            if (el.tag == "01") {
                result.add(TlvItem("01", "12")) // Ubah jadi 12 (Dynamic)
                continue
            }
            if (el.tag == "58" && !amountInserted) {
                result.add(TlvItem("54", amount.toString()))
                amountInserted = true
            }
            result.add(el)
        }

        if (!amountInserted) {
            result.add(TlvItem("54", amount.toString()))
        }

        val body = buildTlv(result)
        val partial = body + "6304"
        val crc = String.format("%04X", crc16(partial))
        return partial + crc
    }
}
