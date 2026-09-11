package com.szu.wlandup.core

/**
 * Pure connect/portal/retry state machine driven by the Android UI layer.
 * Network I/O is injected so unit tests supply controlled outcomes.
 *
 * Dual-zone flow (from szu-net-autologin-mac):
 * connect campus SSID → detect dorm/teach zone → zone portal login → baidu probe.
 */
class ConnectSession(
    private val wifi: WifiController,
    private val portal: ZonePortalClient,
    private val probe: InternetProbe,
    private val inspector: NetworkInspector = NetworkInspector { NetworkSnapshot() },
    private val clock: Sleeper = Sleeper { Thread.sleep(it) },
    private val reconnectDelayMs: Long = 3_000L,
    private val ssidOrder: List<String> = CampusNetworks.CONNECT_ORDER,
) {
    private val logLines = mutableListOf<String>()
    var attemptCount: Int = 0
        private set
    var lastZone: CampusZone = CampusZone.NONE
        private set
    var lastSsid: String? = null
        private set

    fun logs(): List<String> = logLines.toList()

    fun clearLogs() {
        logLines.clear()
    }

    fun resetAfterSuccess() {
        attemptCount = 0
        clearLogs()
    }

    fun appendLog(line: String) {
        logLines.add(line)
    }

    /**
     * One full cycle: connect Wi-Fi → detect zone → portal login → baidu probe.
     * On any failure: disconnect, wait 3s, increment counter, return Failure (caller retries).
     * Success only after portal OK and baidu reachable; then resets counter/logs.
     */
    fun runOnce(credentials: Credentials): ConnectResult {
        appendLog("Connecting campus Wi-Fi…")
        val ssid = connectCampus()
            ?: return failAndPrepareRetry("Wi-Fi connect failed")
        lastSsid = ssid
        appendLog("Wi-Fi connected ($ssid)")

        val snapshot = inspector.snapshot().let { snap ->
            if (snap.ssid.isNullOrBlank()) snap.copy(ssid = ssid) else snap
        }
        val zone = ZoneDetector.detect(snapshot)
        lastZone = zone
        appendLog("Zone: $zone")
        if (zone == CampusZone.NONE) {
            return failAndPrepareRetry("Not a known campus zone")
        }

        appendLog("Portal login ($zone)…")
        val portalOk = try {
            portal.login(zone, credentials)
        } catch (e: Exception) {
            return failAndPrepareRetry("Portal error: ${e.message}")
        }
        if (!portalOk) {
            return failAndPrepareRetry("Portal login rejected")
        }
        appendLog("Portal login OK")

        appendLog("Probing baidu.com…")
        val online = try {
            probe.canReachBaidu()
        } catch (e: Exception) {
            return failAndPrepareRetry("Baidu probe error: ${e.message}")
        }
        if (!online) {
            return failAndPrepareRetry("Baidu unreachable")
        }

        appendLog("Internet OK")
        val usedAttempts = attemptCount + 1
        resetAfterSuccess()
        return ConnectResult.Success(usedAttempts)
    }

    private fun connectCampus(): String? {
        for (ssid in ssidOrder) {
            appendLog("Trying $ssid…")
            if (wifi.connect(ssid)) return ssid
        }
        return null
    }

    private fun failAndPrepareRetry(reason: String): ConnectResult.Failure {
        appendLog("FAIL: $reason")
        wifi.disconnect()
        appendLog("Disconnected; waiting ${reconnectDelayMs}ms")
        clock.sleep(reconnectDelayMs)
        attemptCount += 1
        appendLog("Retry scheduled (attempts=$attemptCount)")
        return ConnectResult.Failure(reason, attemptCount, reconnectDelayMs)
    }
}

sealed class ConnectResult {
    data class Success(val attemptsUsed: Int) : ConnectResult()
    data class Failure(
        val reason: String,
        val attemptCount: Int,
        val reconnectDelayMs: Long,
    ) : ConnectResult()
}

interface WifiController {
    fun connect(ssid: String): Boolean
    fun disconnect()
}

/** Zone-aware portal login (dorm eportal or teach Srun). */
fun interface ZonePortalClient {
    fun login(zone: CampusZone, credentials: Credentials): Boolean
}

/** Optional live network facts after Wi-Fi association. */
fun interface NetworkInspector {
    fun snapshot(): NetworkSnapshot
}

fun interface InternetProbe {
    fun canReachBaidu(): Boolean
}

fun interface Sleeper {
    fun sleep(millis: Long)
}

/** Adapter for dorm-only callers/tests. */
fun dormOnlyPortal(login: (Credentials) -> Boolean): ZonePortalClient =
    ZonePortalClient { zone, credentials ->
        when (zone) {
            CampusZone.DORM -> login(credentials)
            CampusZone.TEACH, CampusZone.NONE -> false
        }
    }
