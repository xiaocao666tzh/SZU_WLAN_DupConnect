package com.szu.wlandup.core

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object PortalLogin {
    const val HOST = "172.30.225.42"
    const val PORT = 801
    const val PATH = "/eportal/portal/login"
    const val PARAM_ACCOUNT = "user_account"
    const val PARAM_PASSWORD = "user_password"
    const val TARGET_SSID = "SZU_CTC&CMCC"
    const val BAIDU_PROBE_URL = "https://www.baidu.com"

    fun buildLoginUrl(credentials: Credentials): String {
        val account = urlEncode(credentials.userAccount)
        val password = urlEncode(credentials.userPassword)
        return "http://$HOST:$PORT$PATH?$PARAM_ACCOUNT=$account&$PARAM_PASSWORD=$password"
    }

    fun buildLoginUri(credentials: Credentials): URI = URI(buildLoginUrl(credentials))

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
