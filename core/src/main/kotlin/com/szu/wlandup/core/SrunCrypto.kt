package com.szu.wlandup.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Deep Lan (Srun) portal crypto used by teaching-area Wi-Fi.
 *
 * Pipeline: HMAC-MD5(password, challenge) → XXTEA info blob → custom base64 → SHA1 chksum.
 * Algorithm matches the JS portal / Mac autologin / BIT-srun-login-script ports.
 */
object SrunCrypto {
    private const val MASK = 0xFFFFFFFFL
    private val CUSTOM_ALPHA =
        "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA".toCharArray()

    fun hmacMd5Hex(password: String, token: String): String {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(token.toByteArray(StandardCharsets.UTF_8), "HmacMD5"))
        val digest = mac.doFinal(password.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun sha1Hex(value: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    /** Custom-alphabet base64 used by Srun (`{SRBX1}` payload). */
    fun customBase64(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val out = StringBuilder()
        val imax = data.size - data.size % 3
        var i = 0
        while (i < imax) {
            val b10 = ((data[i].toInt() and 0xff) shl 16) or
                ((data[i + 1].toInt() and 0xff) shl 8) or
                (data[i + 2].toInt() and 0xff)
            out.append(CUSTOM_ALPHA[(b10 ushr 18) and 63])
            out.append(CUSTOM_ALPHA[(b10 ushr 12) and 63])
            out.append(CUSTOM_ALPHA[(b10 ushr 6) and 63])
            out.append(CUSTOM_ALPHA[b10 and 63])
            i += 3
        }
        when (data.size - imax) {
            1 -> {
                val b10 = (data[i].toInt() and 0xff) shl 16
                out.append(CUSTOM_ALPHA[(b10 ushr 18) and 63])
                out.append(CUSTOM_ALPHA[(b10 ushr 12) and 63])
                out.append('=')
                out.append('=')
            }
            2 -> {
                val b10 = ((data[i].toInt() and 0xff) shl 16) or
                    ((data[i + 1].toInt() and 0xff) shl 8)
                out.append(CUSTOM_ALPHA[(b10 ushr 18) and 63])
                out.append(CUSTOM_ALPHA[(b10 ushr 12) and 63])
                out.append(CUSTOM_ALPHA[(b10 ushr 6) and 63])
                out.append('=')
            }
        }
        return out.toString()
    }

    /** XXTEA-style xencode used before custom base64. */
    fun xencode(message: String, key: String): ByteArray {
        if (message.isEmpty()) return ByteArray(0)
        val pwd = sencode(message, addLen = true).toMutableList()
        val pwdk = sencode(key, addLen = false).toMutableList()
        while (pwdk.size < 4) pwdk += 0L

        val n = pwd.size - 1
        var z = pwd[n]
        var y: Long
        val c = (0x86014019L or 0x183639A0L) and MASK
        var q = 6 + 52 / (n + 1)
        var d = 0L
        while (q > 0) {
            d = (d + c) and MASK
            val e = (d ushr 2) and 3
            var p = 0
            while (p < n) {
                y = pwd[p + 1]
                var m = ((z ushr 5) xor ((y shl 2) and MASK)) and MASK
                m = (m + ((((y ushr 3) xor ((z shl 4) and MASK)) xor (d xor y)) and MASK)) and MASK
                m = (m + ((pwdk[((p and 3) xor e.toInt())] xor z) and MASK)) and MASK
                pwd[p] = (pwd[p] + m) and MASK
                z = pwd[p]
                p++
            }
            y = pwd[0]
            var m = ((z ushr 5) xor ((y shl 2) and MASK)) and MASK
            m = (m + ((((y ushr 3) xor ((z shl 4) and MASK)) xor (d xor y)) and MASK)) and MASK
            m = (m + ((pwdk[((n and 3) xor e.toInt())] xor z) and MASK)) and MASK
            pwd[n] = (pwd[n] + m) and MASK
            z = pwd[n]
            q--
        }
        return lencode(pwd)
    }

    fun buildInfoPayload(
        username: String,
        password: String,
        ip: String,
        acid: String,
        token: String,
    ): String {
        val raw =
            "{\"username\":\"$username\",\"password\":\"$password\",\"ip\":\"$ip\",\"acid\":\"$acid\",\"enc_ver\":\"srun_bx1\"}"
        return "{SRBX1}" + customBase64(xencode(raw, token))
    }

    fun buildChksum(
        token: String,
        username: String,
        hmd5: String,
        acId: String,
        ip: String,
        info: String,
        n: String = "200",
        type: String = "1",
    ): String {
        val str = buildString {
            append(token).append(username)
            append(token).append(hmd5)
            append(token).append(acId)
            append(token).append(ip)
            append(token).append(n)
            append(token).append(type)
            append(token).append(info)
        }
        return sha1Hex(str)
    }

    private fun sencode(msg: String, addLen: Boolean): List<Long> {
        // Portal JS uses charCodeAt; campus accounts/tokens are ASCII.
        val chars = msg.map { it.code and 0xff }
        val out = ArrayList<Long>()
        var i = 0
        while (i < chars.size) {
            var v = 0L
            var j = 0
            while (j < 4 && i + j < chars.size) {
                v = v or ((chars[i + j].toLong() and 0xffL) shl (8 * j))
                j++
            }
            out += v and MASK
            i += 4
        }
        if (addLen) out += chars.size.toLong() and MASK
        return out
    }

    private fun lencode(msg: List<Long>): ByteArray {
        val out = ByteArray(msg.size * 4)
        for (i in msg.indices) {
            val v = msg[i]
            out[i * 4] = (v and 0xffL).toByte()
            out[i * 4 + 1] = ((v ushr 8) and 0xffL).toByte()
            out[i * 4 + 2] = ((v ushr 16) and 0xffL).toByte()
            out[i * 4 + 3] = ((v ushr 24) and 0xffL).toByte()
        }
        return out
    }
}
