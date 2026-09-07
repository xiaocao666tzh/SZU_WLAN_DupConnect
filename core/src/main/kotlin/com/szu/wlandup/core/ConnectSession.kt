package com.szu.wlandup.core

/**
 * Pure connect/portal/retry state machine driven by the Android UI layer.
 * Network I/O is injected so unit tests supply controlled outcomes.
 */
class ConnectSession(
    private val wifi: WifiController,
    private val portal: PortalClient,
    private val probe: InternetProbe,
    private val clock: Sleeper = Sleeper { Thread.sleep(it) },
    private val reconnectDelayMs: Long = 3_000L,
) {
    private val logLines = mutableListOf<String>()
    var attemptCount: Int = 0
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
     * One full cycle: connect Wi-Fi → portal login → baidu probe.
     * On any failure: disconnect, wait 3s, increment counter, return Failure (caller retries).
     * Success only after portal OK and baidu reachable; then resets counter/logs.
     */
    fun runOnce(credentials: Credentials): ConnectResult {
        appendLog("Connecting to ${PortalLogin.TARGET_SSID}…")
        if (!wifi.connect(PortalLogin.TARGET_SSID)) {
            return failAndPrepareRetry("Wi-Fi connect failed")
        }
        appendLog("Wi-Fi connected")

        appendLog("Portal login…")
        val portalOk = try {
            portal.login(credentials)
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

fun interface PortalClient {
    fun login(credentials: Credentials): Boolean
}

fun interface InternetProbe {
    fun canReachBaidu(): Boolean
}

fun interface Sleeper {
    fun sleep(millis: Long)
}
