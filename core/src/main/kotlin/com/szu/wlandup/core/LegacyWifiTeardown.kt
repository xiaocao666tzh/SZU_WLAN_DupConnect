package com.szu.wlandup.core

/**
 * Pure plan for pre-Q Wi-Fi tear-down used by [com.szu.wlandup.WifiConnector].
 * Ensures failure→disconnect is a real disconnect/disable, not a no-op.
 */
data class LegacyWifiTeardownPlan(
    val shouldDisconnectRadio: Boolean,
    val disableNetworkIds: List<Int>,
)

object LegacyWifiTeardown {
    fun plan(
        activeNetId: Int?,
        targetSsid: String,
        configuredNetworks: List<ConfiguredWifiNetwork>,
    ): LegacyWifiTeardownPlan = plan(
        activeNetId = activeNetId,
        targetSsids = listOf(targetSsid),
        configuredNetworks = configuredNetworks,
    )

    fun plan(
        activeNetId: Int?,
        targetSsids: List<String>,
        configuredNetworks: List<ConfiguredWifiNetwork>,
    ): LegacyWifiTeardownPlan {
        val matchSsids = targetSsids.flatMap { ssid -> listOf(ssid, "\"$ssid\"") }.toSet()
        val ids = linkedSetOf<Int>()
        activeNetId?.let { ids += it }
        configuredNetworks
            .filter { net ->
                net.networkId == activeNetId || net.ssid in matchSsids
            }
            .forEach { ids += it.networkId }
        return LegacyWifiTeardownPlan(
            shouldDisconnectRadio = true,
            disableNetworkIds = ids.toList(),
        )
    }
}

data class ConfiguredWifiNetwork(
    val networkId: Int,
    val ssid: String,
)

/** Applies a tear-down plan against a narrow Wi-Fi control surface. */
interface LegacyWifiControls {
    fun disconnectRadio()
    fun disableNetwork(networkId: Int)
}

fun LegacyWifiTeardownPlan.applyTo(controls: LegacyWifiControls) {
    if (shouldDisconnectRadio) {
        controls.disconnectRadio()
    }
    disableNetworkIds.forEach { controls.disableNetwork(it) }
}
