package com.szu.wlandup

import android.content.Context
import android.net.wifi.WifiManager
import com.szu.wlandup.core.Credentials
import com.szu.wlandup.core.InternetProbe
import com.szu.wlandup.core.PortalClient
import com.szu.wlandup.core.PortalLogin
import com.szu.wlandup.core.PortalNetworkContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class HttpPortalClient(
    private val context: Context,
    private val networkContextProvider: () -> PortalNetworkContext = {
        PortalNetworkContext(
            wlanUserIp = currentWifiIpv4(context),
            wlanUserMac = "000000000000",
        )
    },
) : PortalClient {
    override fun login(credentials: Credentials): Boolean {
        val url = URL(PortalLogin.buildLoginUrl(credentials, networkContextProvider()))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", PortalLogin.ACCEPT)
            setRequestProperty("Accept-Language", PortalLogin.ACCEPT_LANGUAGE)
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Referer", PortalLogin.REFERER)
            setRequestProperty("User-Agent", PortalLogin.USER_AGENT)
        }
        return try {
            // Portal success gate: HTTP 200, then caller probes baidu.
            conn.responseCode == 200
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
            setRequestProperty("User-Agent", PortalLogin.USER_AGENT)
        }
        return try {
            val code = conn.responseCode
            code in 200..399
        } finally {
            conn.disconnect()
        }
    }
}

@Suppress("DEPRECATION")
fun currentWifiIpv4(context: Context): String {
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ip = wifi.connectionInfo.ipAddress
    if (ip == 0) return "0.0.0.0"
    return String.format(
        Locale.US,
        "%d.%d.%d.%d",
        ip and 0xff,
        ip shr 8 and 0xff,
        ip shr 16 and 0xff,
        ip shr 24 and 0xff,
    )
}
