package com.szu.wlandup.core

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Dormitory Dr.COM eportal login shaped like:
 * curl 'http://172.30.255.42:801/eportal/portal/login?callback=dr1003&login_method=1&user_account=,0,<Account>&...'
 *
 * Teaching-area Srun helpers live in [SrunLogin].
 */
object PortalLogin {
    const val HOST = CampusNetworks.DORM_PORTAL_HOST
    const val PORT = CampusNetworks.DORM_PORTAL_PORT
    const val PATH = "/eportal/portal/login"
    const val PARAM_ACCOUNT = "user_account"
    const val PARAM_PASSWORD = "user_password"
    /** @deprecated Use [CampusNetworks.DORM_SSIDS] / [CampusNetworks.CONNECT_ORDER]. */
    const val TARGET_SSID = "SZU_CTC&CMCC"
    const val BAIDU_PROBE_URL = CampusNetworks.BAIDU_PROBE_URL
    const val WLAN_AC_IP = "172.30.255.41"
    const val REFERER = "http://$HOST/"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
    const val ACCEPT = "*/*"
    const val ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7"

    /** Portal expects user_account as ",0,<campus-id>". */
    fun formatUserAccount(account: String): String = ",0,$account"

    fun buildLoginUrl(
        credentials: Credentials,
        network: PortalNetworkContext,
    ): String {
        // Keep ",0," commas literal like the campus curl; only encode the campus id.
        val account = ",0," + urlEncode(credentials.userAccount)
        val password = urlEncode(credentials.userPassword)
        val wlanIp = urlEncode(network.wlanUserIp)
        val wlanMac = urlEncode(network.wlanUserMac)
        val query = listOf(
            "callback=dr1003",
            "login_method=1",
            "$PARAM_ACCOUNT=$account",
            "$PARAM_PASSWORD=$password",
            "wlan_user_ip=$wlanIp",
            "wlan_user_ipv6=",
            "wlan_user_mac=$wlanMac",
            "wlan_ac_ip=$WLAN_AC_IP",
            "wlan_ac_name=",
            "jsVersion=4.1.3",
            "terminal_type=1",
            "lang=zh-cn",
            "v=7915",
            "lang=zh",
        ).joinToString("&")
        return "http://$HOST:$PORT$PATH?$query"
    }

    fun buildLoginUri(
        credentials: Credentials,
        network: PortalNetworkContext,
    ): URI = URI(buildLoginUrl(credentials, network))

    /**
     * Dorm eportal success: JSONP `result: 1`, or already-online / auth-success text.
     * HTTP 200 alone is not enough (portal often returns 200 with an error body).
     */
    fun isDormLoginSuccess(body: String): Boolean {
        if (Regex(""""result"\s*:\s*1""").containsMatchIn(body)) return true
        if (body.contains("已经在线")) return true
        if (body.contains("认证成功")) return true
        return false
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

data class PortalNetworkContext(
    val wlanUserIp: String,
    val wlanUserMac: String = "000000000000",
)
