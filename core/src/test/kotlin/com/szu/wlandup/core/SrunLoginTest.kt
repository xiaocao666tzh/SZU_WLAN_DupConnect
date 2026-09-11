package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrunLoginTest {
    @Test
    fun parsesChallengeJsonp() {
        val body =
            """dr1003({"challenge":"abcTOKEN","client_ip":"172.26.1.9","res":"ok","error":"ok"})"""
        val challenge = SrunLogin.parseChallengeJsonp(body)
        assertNotNull(challenge)
        assertEquals("abcTOKEN", challenge!!.token)
        assertEquals("172.26.1.9", challenge.clientIp)
    }

    @Test
    fun rejectsBadChallenge() {
        assertNull(SrunLogin.parseChallengeJsonp("""cb({"res":"fail"})"""))
        assertNull(SrunLogin.parseChallengeJsonp(""))
    }

    @Test
    fun loginSuccessDetection() {
        assertTrue(SrunLogin.isLoginSuccess("""cb({"res":"ok","suc_msg":"login_ok"})"""))
        assertFalse(SrunLogin.isLoginSuccess("""cb({"res":"auth_error"})"""))
    }

    @Test
    fun alreadyOnlineDetection() {
        assertTrue(SrunLogin.isAlreadyOnline("user_name=x,ip=1.2.3.4"))
        assertFalse(SrunLogin.isAlreadyOnline("not_online_error"))
        assertFalse(SrunLogin.isAlreadyOnline(null))
        assertFalse(SrunLogin.isAlreadyOnline(""))
    }

    @Test
    fun extractsAcIdFromHtml() {
        assertEquals("18", SrunLogin.extractAcId("""<a href="/srun?ac_id=18&theme=1">"""))
        assertEquals("18", SrunLogin.extractAcId("<html></html>"))
        assertEquals("8", SrunLogin.extractAcId("ac_id=8", fallback = "18"))
    }

    @Test
    fun originFromLocationHeader() {
        assertEquals(
            "http://net.szu.edu.cn",
            SrunLogin.originFromLocation("http://net.szu.edu.cn/srun_portal_pc?ac_id=18"),
        )
        assertEquals(
            "https://net.szu.edu.cn",
            SrunLogin.originFromLocation("https://net.szu.edu.cn/cgi-bin/srun_portal"),
        )
        assertNull(SrunLogin.originFromLocation(null))
        assertNull(SrunLogin.originFromLocation("not-a-url"))
    }

    @Test
    fun buildEncryptedLoginContainsRequiredParams() {
        val url = SrunLogin.buildEncryptedLogin(
            credentials = Credentials("123456", "secret"),
            challenge = SrunChallenge(token = "tok123", clientIp = "172.26.1.2"),
            acId = "18",
            callback = "cb1",
            origin = "https://net.szu.edu.cn",
        )
        assertTrue(url.startsWith("https://net.szu.edu.cn/cgi-bin/srun_portal?"))
        assertTrue(url.contains("action=login"))
        assertTrue(url.contains("username=123456"))
        assertTrue(url.contains("ac_id=18"))
        assertTrue(url.contains("ip=172.26.1.2"))
        assertTrue(url.contains("password="))
        assertTrue(url.contains("chksum="))
        assertTrue(url.contains("info="))
    }
}
