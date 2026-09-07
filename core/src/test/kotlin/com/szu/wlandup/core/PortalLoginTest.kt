package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class PortalLoginTest {
    @Test
    fun buildsExactPortalEndpointAndQueryKeys() {
        val uri: URI = PortalLogin.buildLoginUri(Credentials("ID123", "PASS&WD"))

        assertEquals("http", uri.scheme)
        assertEquals(PortalLogin.HOST, uri.host)
        assertEquals(PortalLogin.PORT, uri.port)
        assertEquals(PortalLogin.PATH, uri.path)

        val query = requireNotNull(uri.rawQuery)
        assertTrue(query.contains("${PortalLogin.PARAM_ACCOUNT}="))
        assertTrue(query.contains("${PortalLogin.PARAM_PASSWORD}="))

        val params = query.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        }
        assertEquals(setOf(PortalLogin.PARAM_ACCOUNT, PortalLogin.PARAM_PASSWORD), params.keys)
        assertEquals(
            "ID123",
            java.net.URLDecoder.decode(params.getValue(PortalLogin.PARAM_ACCOUNT), Charsets.UTF_8),
        )
        assertEquals(
            "PASS&WD",
            java.net.URLDecoder.decode(params.getValue(PortalLogin.PARAM_PASSWORD), Charsets.UTF_8),
        )
        assertEquals("PASS%26WD", params.getValue(PortalLogin.PARAM_PASSWORD))
    }

    @Test
    fun urlStringMatchesCurlShape() {
        val url = PortalLogin.buildLoginUrl(Credentials("ID", "PASSWD"))
        assertTrue(url.startsWith("http://172.30.225.42:801/eportal/portal/login?"))
        assertTrue(url.contains("user_account=ID"))
        assertTrue(url.contains("user_password=PASSWD"))
    }
}
