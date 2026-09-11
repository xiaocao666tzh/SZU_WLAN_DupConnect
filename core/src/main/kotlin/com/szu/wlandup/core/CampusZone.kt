package com.szu.wlandup.core

/** Campus network zone used to pick the auth protocol. */
enum class CampusZone {
    /** Dormitory Dr.COM eportal (`SZU_CTC&CMCC`). */
    DORM,

    /** Teaching-area Srun (`SZU_WLAN` / `SZU-WLAN`). */
    TEACH,

    /** Not a known campus network — do nothing. */
    NONE,
}

/**
 * Dual-zone constants aligned with
 * [szu-net-autologin-mac](https://github.com/JennieYow/szu-net-autologin-mac).
 */
object CampusNetworks {
    val DORM_SSIDS: List<String> = listOf("SZU_CTC&CMCC")
    val TEACH_SSIDS: List<String> = listOf("SZU_WLAN", "SZU-WLAN")

    /** Prefer dorm first, then teaching SSIDs when actively associating. */
    val CONNECT_ORDER: List<String> = DORM_SSIDS + TEACH_SSIDS

    const val DORM_NET_PREFIX = "172.24."
    const val TEACH_NET_PREFIX = "172.26."

    const val DORM_PORTAL_HOST = "172.30.255.42"
    const val DORM_PORTAL_PORT = 801
    const val DORM_PORTAL_BASE = "http://$DORM_PORTAL_HOST:$DORM_PORTAL_PORT"

    const val SRUN_BASE = "https://net.szu.edu.cn"
    const val SRUN_HTTP_FALLBACK = "http://net.szu.edu.cn"
    /** Fallback ac_id when the login page cannot be scraped. */
    const val SRUN_AC_ID_FALLBACK = "18"

    const val BAIDU_PROBE_URL = "https://www.baidu.com"
    const val CAPTIVE_PROBE_URL = "http://www.msftconnecttest.com/connecttest.txt"

    fun zoneForSsid(ssid: String?): CampusZone? {
        if (ssid.isNullOrBlank()) return null
        val clean = ssid.trim().removeSurrounding("\"")
        if (clean.contains("edacted", ignoreCase = true)) return null
        return when {
            DORM_SSIDS.any { it.equals(clean, ignoreCase = false) } -> CampusZone.DORM
            TEACH_SSIDS.any { it.equals(clean, ignoreCase = false) } -> CampusZone.TEACH
            else -> null
        }
    }

    fun zoneForIpv4(ip: String?): CampusZone? {
        if (ip.isNullOrBlank()) return null
        return when {
            ip.startsWith(DORM_NET_PREFIX) -> CampusZone.DORM
            ip.startsWith(TEACH_NET_PREFIX) -> CampusZone.TEACH
            else -> null
        }
    }
}
