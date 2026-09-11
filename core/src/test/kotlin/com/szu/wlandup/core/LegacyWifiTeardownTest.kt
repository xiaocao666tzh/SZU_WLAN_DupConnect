package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyWifiTeardownTest {
    @Test
    fun planDisconnectsRadioAndDisablesActiveAndTargetNetworks() {
        val plan = LegacyWifiTeardown.plan(
            activeNetId = 7,
            targetSsid = PortalLogin.TARGET_SSID,
            configuredNetworks = listOf(
                ConfiguredWifiNetwork(7, "\"${PortalLogin.TARGET_SSID}\""),
                ConfiguredWifiNetwork(3, "\"Other\""),
                ConfiguredWifiNetwork(9, PortalLogin.TARGET_SSID),
            ),
        )

        assertTrue(plan.shouldDisconnectRadio)
        assertEquals(listOf(7, 9), plan.disableNetworkIds)
    }

    @Test
    fun applyToPerformsRealTearDownActions() {
        val disconnected = mutableListOf<String>()
        val disabled = mutableListOf<Int>()
        val plan = LegacyWifiTeardown.plan(
            activeNetId = 4,
            targetSsid = "SZU_CTC&CMCC",
            configuredNetworks = listOf(ConfiguredWifiNetwork(4, "\"SZU_CTC&CMCC\"")),
        )

        plan.applyTo(object : LegacyWifiControls {
            override fun disconnectRadio() {
                disconnected += "radio"
            }

            override fun disableNetwork(networkId: Int) {
                disabled += networkId
            }
        })

        assertEquals(listOf("radio"), disconnected)
        assertEquals(listOf(4), disabled)
    }

    @Test
    fun planDisablesAllCampusSsids() {
        val plan = LegacyWifiTeardown.plan(
            activeNetId = 1,
            targetSsids = CampusNetworks.CONNECT_ORDER,
            configuredNetworks = listOf(
                ConfiguredWifiNetwork(1, "\"SZU_CTC&CMCC\""),
                ConfiguredWifiNetwork(2, "\"SZU_WLAN\""),
                ConfiguredWifiNetwork(3, "\"Home\""),
            ),
        )
        assertEquals(listOf(1, 2), plan.disableNetworkIds)
    }
}
