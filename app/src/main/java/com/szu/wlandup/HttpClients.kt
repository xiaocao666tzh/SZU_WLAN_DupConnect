package com.szu.wlandup

import android.content.Context
import android.net.wifi.WifiManager
import com.szu.wlandup.core.CampusNetworks
import com.szu.wlandup.core.CampusZone
import com.szu.wlandup.core.Credentials
import com.szu.wlandup.core.InternetProbe
import com.szu.wlandup.core.NetworkInspector
import com.szu.wlandup.core.NetworkSnapshot
import com.szu.wlandup.core.PortalLogin
import com.szu.wlandup.core.PortalNetworkContext
import com.szu.wlandup.core.SrunLogin
import com.szu.wlandup.core.ZonePortalClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DualZonePortalClient(
    private val context: Context,
) : ZonePortalClient {
    override fun login(zone: CampusZone, credentials: Credentials): Boolean {
        return when (zone) {
            CampusZone.DORM -> loginDorm(credentials)
            CampusZone.TEACH -> loginTeach(credentials)
            CampusZone.NONE -> false
        }
    }

    private fun loginDorm(credentials: Credentials): Boolean {
        val network = PortalNetworkContext(
            wlanUserIp = currentWifiIpv4(context),
            wlanUserMac = "000000000000",
        )
        val url = URL(PortalLogin.buildLoginUrl(credentials, network))
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
            val code = conn.responseCode
            val body = readBody(conn)
            code == 200 && PortalLogin.isDormLoginSuccess(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun loginTeach(credentials: Credentials): Boolean {
        // Already-online short circuit (Mac login_teach step ①).
        val status = getQuiet(SrunLogin.statusUrl())
        if (SrunLogin.isAlreadyOnline(status)) return true

        val callback = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())

        var origin = CampusNetworks.SRUN_BASE
        var challengeBody = getQuiet(
            SrunLogin.buildChallengeUrl(credentials.userAccount, callback, origin),
        )
        if (challengeBody.isNullOrBlank()) {
            val location = peekRedirectLocation(CampusNetworks.CAPTIVE_PROBE_URL)
            val detected = SrunLogin.originFromLocation(location)
            origin = detected ?: CampusNetworks.SRUN_HTTP_FALLBACK
            challengeBody = getQuiet(
                SrunLogin.buildChallengeUrl(credentials.userAccount, callback, origin),
            )
        }

        val challenge = SrunLogin.parseChallengeJsonp(challengeBody.orEmpty()) ?: return false
        val acId = scrapeAcId(origin)
        val loginUrl = SrunLogin.buildEncryptedLogin(
            credentials = credentials,
            challenge = challenge,
            acId = acId,
            callback = callback,
            origin = origin,
        )
        val resp = getQuiet(loginUrl) ?: return false
        return SrunLogin.isLoginSuccess(resp)
    }

    private fun scrapeAcId(origin: String): String {
        val html = getQuiet("${SrunLogin.trimOrigin(origin)}/")
            ?: getQuiet("${SrunLogin.trimOrigin(origin)}/index_8.html")
            ?: return CampusNetworks.SRUN_AC_ID_FALLBACK
        return SrunLogin.extractAcId(html)
    }
}

class AndroidNetworkInspector(
    private val context: Context,
) : NetworkInspector {
    override fun snapshot(): NetworkSnapshot {
        val ssid = currentWifiSsid(context)
        val ipv4 = currentWifiIpv4(context).takeIf { it != "0.0.0.0" }
        // Only probe servers when SSID/IP cannot decide — keep the common path fast.
        val quickZone = CampusNetworks.zoneForSsid(ssid) ?: CampusNetworks.zoneForIpv4(ipv4)
        if (quickZone != null) {
            return NetworkSnapshot(ssid = ssid, ipv4 = ipv4)
        }
        val dormOk = headOk("${CampusNetworks.DORM_PORTAL_BASE}/")
        val teachState = getQuiet(SrunLogin.statusUrl())
        return NetworkSnapshot(
            ssid = ssid,
            ipv4 = ipv4,
            dormPortalReachable = dormOk,
            teachState = teachState,
        )
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

@Suppress("DEPRECATION")
fun currentWifiSsid(context: Context): String? {
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val raw = wifi.connectionInfo?.ssid ?: return null
    val clean = raw.trim().removeSurrounding("\"")
    if (clean.isEmpty() || clean.equals("<unknown ssid>", ignoreCase = true)) return null
    if (clean.contains("edacted", ignoreCase = true)) return null
    return clean
}

private fun getQuiet(url: String): String? {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", PortalLogin.USER_AGENT)
        setRequestProperty("Accept", "*/*")
    }
    return try {
        if (conn.responseCode !in 200..399) null
        else readBody(conn).ifBlank { null }
    } catch (_: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}

private fun headOk(url: String): Boolean {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 4_000
        readTimeout = 4_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", PortalLogin.USER_AGENT)
    }
    return try {
        conn.responseCode in 200..399
    } catch (_: Exception) {
        false
    } finally {
        conn.disconnect()
    }
}

private fun peekRedirectLocation(url: String): String? {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 6_000
        readTimeout = 6_000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", PortalLogin.USER_AGENT)
    }
    return try {
        val code = conn.responseCode
        if (code in 300..399) conn.getHeaderField("Location") else null
    } catch (_: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}

private fun readBody(conn: HttpURLConnection): String {
    val stream = try {
        conn.inputStream
    } catch (_: Exception) {
        conn.errorStream
    } ?: return ""
    return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
}