package com.szu.wlandup

import com.szu.wlandup.core.Credentials
import com.szu.wlandup.core.InternetProbe
import com.szu.wlandup.core.PortalClient
import com.szu.wlandup.core.PortalLogin
import java.net.HttpURLConnection
import java.net.URL

class HttpPortalClient : PortalClient {
    override fun login(credentials: Credentials): Boolean {
        val url = URL(PortalLogin.buildLoginUrl(credentials))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            code in 200..399
        } finally {
            conn.disconnect()
        }
    }
}

class HttpBaiduProbe : InternetProbe {
    override fun canReachBaidu(): Boolean {
        val url = URL(PortalLogin.BAIDU_PROBE_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            code in 200..399
        } finally {
            conn.disconnect()
        }
    }
}
