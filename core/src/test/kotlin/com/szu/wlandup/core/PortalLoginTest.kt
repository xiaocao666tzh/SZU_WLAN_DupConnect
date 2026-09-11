package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder

class PortalLoginTest {
    private val network = PortalNetworkContext(
        wlanUserIp = "172.29.34.195",
        wlanUserMac = "000000000000",
    )

    @Test
    fun formatsAccountAsCommaZeroId() {
        assertEquals(",0,2020123456", PortalLogin.formatUserAccount("2020123456"))
    }

    @Test
    fun buildsExactPortalEndpointAndQueryKeys() {
        val uri: URI = PortalLogin.buildLoginUri(Credentials("ID123", "PASS&WD"), network)

        assertEquals("http", uri.scheme)
        assertEquals("172.30.255.42", uri.host)
        assertEquals(PortalLogin.HOST, uri.host)
        assertEquals(801, uri.port)
        assertEquals(PortalLogin.PATH, uri.path)

        val query = requireNotNull(uri.rawQuery)
        val params = LinkedHashMap<String, String>()
        query.split("&").forEach {
            val parts = it.split("=", limit = 2)
            params[parts[0]] = parts.getOrElse(1) { "" }
        }

        assertEquals("dr1003", params["callback"])
        assertEquals("1", params["login_method"])
        assertEquals(
            ",0,ID123",
            URLDecoder.decode(params.getValue(PortalLogin.PARAM_ACCOUNT), Charsets.UTF_8),
        )
        assertEquals(
            "PASS&WD",
            URLDecoder.decode(params.getValue(PortalLogin.PARAM_PASSWORD), Charsets.UTF_8),
        )
        assertEquals("PASS%26WD", params.getValue(PortalLogin.PARAM_PASSWORD))
        assertEquals("172.29.34.195", params["wlan_user_ip"])
        assertEquals("", params["wlan_user_ipv6"])
        assertEquals("000000000000", params["wlan_user_mac"])
        assertEquals(PortalLogin.WLAN_AC_IP, params["wlan_ac_ip"])
        assertEquals("", params["wlan_ac_name"])
        assertEquals("4.1.3", params["jsVersion"])
        assertEquals("1", params["terminal_type"])
        assertEquals("7915", params["v"])
        // Duplicate lang keys: last one wins in this map; raw query still contains both.
        assertTrue(query.contains("lang=zh-cn"))
        assertTrue(query.endsWith("lang=zh") || query.contains("&lang=zh"))
    }

    @Test
    fun urlStringMatchesCurlShape() {
        val url = PortalLogin.buildLoginUrl(Credentials("ID", "PASSWD"), network)
        assertTrue(url.startsWith("http://172.30.255.42:801/eportal/portal/login?"))
        assertTrue(url.contains("callback=dr1003"))
        assertTrue(url.contains("login_method=1"))
        assertTrue(url.contains("user_account=,0,ID"))
        assertTrue(url.contains("user_password=PASSWD"))
        assertTrue(url.contains("wlan_user_ip=172.29.34.195"))
        assertTrue(url.contains("wlan_ac_ip=172.30.255.41"))
        assertFalse(url.contains("172.30.225.42"))
    }

    @Test
    fun dormSuccessRequiresResultBodyNotJustHttpOk() {
        assertTrue(PortalLogin.isDormLoginSuccess("""dr1003({"result":1,"msg":"认证成功"})"""))
        assertTrue(PortalLogin.isDormLoginSuccess("已经在线"))
        assertTrue(PortalLogin.isDormLoginSuccess("认证成功"))
        assertFalse(PortalLogin.isDormLoginSuccess("""dr1003({"result":0,"msg":"密码错误"})"""))
        assertFalse(PortalLogin.isDormLoginSuccess(""))
    }
}
