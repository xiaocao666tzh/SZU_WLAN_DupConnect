package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SrunCryptoTest {
    @Test
    fun hmacMd5MatchesKnownVector() {
        val hmd5 = SrunCrypto.hmacMd5Hex(
            password = "15879684798qq",
            token = "711ab370231392679fe06523b119a8fe096f5ed9bd206b4de8d7b5b994bbc3e5",
        )
        assertEquals("b7cc5da95734d0161fadc8ad87855e75", hmd5)
    }

    @Test
    fun sha1MatchesKnownVector() {
        assertEquals("7c4a8d09ca3762af61e59520943dc26494f8941b", SrunCrypto.sha1Hex("123456"))
    }

    @Test
    fun customBase64MatchesKnownVector() {
        assertEquals("9F9x0JHI", SrunCrypto.customBase64("132456".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun xencodeAndInfoPayloadMatchBitSrunVector() {
        val msg =
            "{\"username\":\"201626203044@cmcc\",\"password\":\"15879684798qq\",\"ip\":\"10.128.96.249\",\"acid\":\"1\",\"enc_ver\":\"srun_bx1\"}"
        val token = "e6843f26b8544327a3a25978dd3c5f89e6b745df1732993b88fe082c13a34cb9"
        val encoded = SrunCrypto.xencode(msg, token)
        assertEquals(116, encoded.size)
        assertEquals(
            listOf(102, 146, 239, 107, 228, 117, 59, 64, 183, 155, 138, 100, 238, 24, 148, 185),
            encoded.take(16).map { it.toInt() and 0xff },
        )
        val info = SrunCrypto.buildInfoPayload(
            username = "201626203044@cmcc",
            password = "15879684798qq",
            ip = "10.128.96.249",
            acid = "1",
            token = token,
        )
        assertEquals(
            "{SRBX1}13GwOQhjyto7UD3YETXHKszNW6cgCyaeZxbRoFRKgNRJTnTSqC/awYNrdZP1cgJfTPvesb2/jkTwKUtyOvK1yZkmA25ShYWGhKahj/1p0QO3aW/8Ue8NRUy0QcMqtvyS3XdsFypSV9EO10kTcp1PXHvhH64=",
            info,
        )
    }

    @Test
    fun chksumUsesTokenSandwich() {
        val token = "tok"
        val info = "{SRBX1}abc"
        val chk = SrunCrypto.buildChksum(
            token = token,
            username = "u",
            hmd5 = "h",
            acId = "18",
            ip = "1.2.3.4",
            info = info,
        )
        val expected = SrunCrypto.sha1Hex("toku" + "tokh" + "tok18" + "tok1.2.3.4" + "tok200" + "tok1" + "tok{SRBX1}abc")
        assertEquals(expected, chk)
    }
}
