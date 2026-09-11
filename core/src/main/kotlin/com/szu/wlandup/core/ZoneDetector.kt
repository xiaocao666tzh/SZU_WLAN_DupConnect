package com.szu.wlandup.core

/**
 * Snapshot of local network facts used for zone detection.
 * [teachState] is the raw `rad_user_info` body (null = unreachable).
 */
data class NetworkSnapshot(
    val ssid: String? = null,
    val ipv4: String? = null,
    val gateway: String? = null,
    val dormPortalReachable: Boolean = false,
    val teachState: String? = null,
)

data class ZoneCache(
    val zone: CampusZone,
    val gateway: String,
)

/**
 * Four-level zone detection (fast → slow), matching the Mac autologin script:
 * 1. SSID match
 * 2. IP prefix match
 * 3. Gateway-stable zone cache
 * 4. Auth-server probing with dorm-preferring tie-break
 */
object ZoneDetector {
    fun detect(
        snapshot: NetworkSnapshot,
        cache: ZoneCache? = null,
    ): CampusZone {
        CampusNetworks.zoneForSsid(snapshot.ssid)?.let { return it }
        CampusNetworks.zoneForIpv4(snapshot.ipv4)?.let { return it }

        val gw = snapshot.gateway
        if (cache != null && !gw.isNullOrBlank() && cache.gateway == gw) {
            if (cache.zone == CampusZone.DORM || cache.zone == CampusZone.TEACH) {
                return cache.zone
            }
        }

        return probeServers(snapshot)
    }

    /**
     * Probe order from Mac v2.6:
     * - teach online session (non-empty, not not_online_error) → TEACH
     * - dorm reachable & teach unreachable → DORM
     * - teach reachable & dorm unreachable → TEACH
     * - both reachable → DORM (dorm LAN can reach both)
     * - neither → NONE
     */
    private fun probeServers(snapshot: NetworkSnapshot): CampusZone {
        val state = snapshot.teachState
        if (!state.isNullOrBlank() && !state.contains("not_online_error")) {
            return CampusZone.TEACH
        }
        val teachOk = !state.isNullOrBlank()
        val dormOk = snapshot.dormPortalReachable
        return when {
            dormOk && !teachOk -> CampusZone.DORM
            !dormOk && teachOk -> CampusZone.TEACH
            dormOk -> CampusZone.DORM
            else -> CampusZone.NONE
        }
    }
}
