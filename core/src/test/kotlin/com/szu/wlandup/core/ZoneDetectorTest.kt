package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ZoneDetectorTest {
    @Test
    fun ssidDetectsDormAndTeach() {
        assertEquals(CampusZone.DORM, CampusNetworks.zoneForSsid("SZU_CTC&CMCC"))
        assertEquals(CampusZone.TEACH, CampusNetworks.zoneForSsid("SZU_WLAN"))
        assertEquals(CampusZone.TEACH, CampusNetworks.zoneForSsid("SZU-WLAN"))
        assertEquals(CampusZone.TEACH, CampusNetworks.zoneForSsid("\"SZU_WLAN\""))
        assertNull(CampusNetworks.zoneForSsid("<redacted>"))
        assertNull(CampusNetworks.zoneForSsid("HomeWiFi"))
    }

    @Test
    fun ipPrefixDetectsZones() {
        assertEquals(CampusZone.DORM, CampusNetworks.zoneForIpv4("172.24.1.20"))
        assertEquals(CampusZone.TEACH, CampusNetworks.zoneForIpv4("172.26.10.5"))
        assertNull(CampusNetworks.zoneForIpv4("10.0.0.1"))
    }

    @Test
    fun prefersSsidOverIp() {
        val zone = ZoneDetector.detect(
            NetworkSnapshot(ssid = "SZU_CTC&CMCC", ipv4 = "172.26.1.1"),
        )
        assertEquals(CampusZone.DORM, zone)
    }

    @Test
    fun usesIpWhenSsidMissing() {
        val zone = ZoneDetector.detect(NetworkSnapshot(ipv4 = "172.26.8.1"))
        assertEquals(CampusZone.TEACH, zone)
    }

    @Test
    fun usesCacheWhenGatewayUnchanged() {
        val zone = ZoneDetector.detect(
            NetworkSnapshot(gateway = "172.24.0.1"),
            cache = ZoneCache(CampusZone.DORM, "172.24.0.1"),
        )
        assertEquals(CampusZone.DORM, zone)
    }

    @Test
    fun ignoresCacheWhenGatewayChanged() {
        val zone = ZoneDetector.detect(
            NetworkSnapshot(
                gateway = "172.26.0.1",
                dormPortalReachable = false,
                teachState = "not_online_error",
            ),
            cache = ZoneCache(CampusZone.DORM, "172.24.0.1"),
        )
        assertEquals(CampusZone.TEACH, zone)
    }

    @Test
    fun probeTeachOnlineSessionWins() {
        val zone = ZoneDetector.detect(
            NetworkSnapshot(
                dormPortalReachable = true,
                teachState = "user_name=abc,ip=1.2.3.4",
            ),
        )
        assertEquals(CampusZone.TEACH, zone)
    }

    @Test
    fun probeBothReachablePrefersDorm() {
        val zone = ZoneDetector.detect(
            NetworkSnapshot(
                dormPortalReachable = true,
                teachState = "not_online_error",
            ),
        )
        assertEquals(CampusZone.DORM, zone)
    }

    @Test
    fun probeNeitherReturnsNone() {
        val zone = ZoneDetector.detect(
            NetworkSnapshot(dormPortalReachable = false, teachState = null),
        )
        assertEquals(CampusZone.NONE, zone)
    }

    @Test
    fun connectOrderListsDormThenTeach() {
        assertEquals(
            listOf("SZU_CTC&CMCC", "SZU_WLAN", "SZU-WLAN"),
            CampusNetworks.CONNECT_ORDER,
        )
    }
}
