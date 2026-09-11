package com.szu.wlandup.core

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Teaching-area Srun portal helpers (challenge → encrypted login).
 * Protocol aligned with szu-net-autologin-mac / SoY0ung SZU-SRUN docs.
 */
object SrunLogin {
    const val CHALLENGE_PATH = "/cgi-bin/get_challenge"
    const val LOGIN_PATH = "/cgi-bin/srun_portal"
    const val STATUS_PATH = "/cgi-bin/rad_user_info"
    const val DEFAULT_ORIGIN = CampusNetworks.SRUN_BASE

    fun statusUrl(origin: String = DEFAULT_ORIGIN): String =
        trimOrigin(origin) + STATUS_PATH

    fun buildChallengeUrl(
        username: String,
        callback: String,
        origin: String = DEFAULT_ORIGIN,
    ): String {
        val q = listOf(
            "callback=${urlEncode(callback)}",
            "username=${urlEncode(username)}",
        ).joinToString("&")
        return "${trimOrigin(origin)}$CHALLENGE_PATH?$q"
    }

    fun buildLoginUrl(
        username: String,
        hmd5: String,
        chksum: String,
        info: String,
        acId: String,
        ip: String,
        callback: String,
        origin: String = DEFAULT_ORIGIN,
    ): String {
        val q = listOf(
            "callback=${urlEncode(callback)}",
            "action=login",
            "username=${urlEncode(username)}",
            "password=${urlEncode("{MD5}$hmd5")}",
            "chksum=${urlEncode(chksum)}",
            "info=${urlEncode(info)}",
            "ac_id=${urlEncode(acId)}",
            "ip=${urlEncode(ip)}",
            "n=200",
            "type=1",
        ).joinToString("&")
        return "${trimOrigin(origin)}$LOGIN_PATH?$q"
    }

    fun buildEncryptedLogin(
        credentials: Credentials,
        challenge: SrunChallenge,
        acId: String,
        callback: String,
        origin: String = DEFAULT_ORIGIN,
    ): String {
        val hmd5 = SrunCrypto.hmacMd5Hex(credentials.userPassword, challenge.token)
        val info = SrunCrypto.buildInfoPayload(
            username = credentials.userAccount,
            password = credentials.userPassword,
            ip = challenge.clientIp,
            acid = acId,
            token = challenge.token,
        )
        val chksum = SrunCrypto.buildChksum(
            token = challenge.token,
            username = credentials.userAccount,
            hmd5 = hmd5,
            acId = acId,
            ip = challenge.clientIp,
            info = info,
        )
        return buildLoginUrl(
            username = credentials.userAccount,
            hmd5 = hmd5,
            chksum = chksum,
            info = info,
            acId = acId,
            ip = challenge.clientIp,
            callback = callback,
            origin = origin,
        )
    }

    fun parseChallengeJsonp(body: String): SrunChallenge? {
        val res = extractJsonString(body, "res") ?: return null
        if (res != "ok") return null
        val token = extractJsonString(body, "challenge") ?: return null
        val ip = extractJsonString(body, "client_ip") ?: return null
        if (token.isBlank() || ip.isBlank()) return null
        return SrunChallenge(token = token, clientIp = ip)
    }

    fun isLoginSuccess(body: String): Boolean {
        val res = extractJsonString(body, "res")
        if (res == "ok") return true
        return body.contains("login_ok") || body.contains("\"suc_msg\":\"login_ok\"")
    }

    /** Prefer an existing online session over a fresh login attempt. */
    fun isAlreadyOnline(statusBody: String?): Boolean {
        if (statusBody.isNullOrBlank()) return false
        return !statusBody.contains("not_online_error")
    }

    fun extractAcId(html: String, fallback: String = CampusNetworks.SRUN_AC_ID_FALLBACK): String {
        val match = Regex("""ac_id=([0-9]+)""").find(html)
        return match?.groupValues?.getOrNull(1) ?: fallback
    }

    /**
     * Extract portal origin from a captive-portal `Location` header value,
     * e.g. `http://net.szu.edu.cn/srun_portal_...?`.
     */
    fun originFromLocation(location: String?): String? {
        if (location.isNullOrBlank()) return null
        return try {
            val uri = URI(location.trim())
            if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) null
            else {
                val port = if (uri.port > 0) ":${uri.port}" else ""
                "${uri.scheme}://${uri.host}$port"
            }
        } catch (_: Exception) {
            null
        }
    }

    fun trimOrigin(origin: String): String = origin.trimEnd('/')

    fun extractJsonString(body: String, key: String): String? {
        val match = Regex(""""$key"\s*:\s*"([^"]*)"""").find(body)
        return match?.groupValues?.getOrNull(1)
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

data class SrunChallenge(
    val token: String,
    val clientIp: String,
)
