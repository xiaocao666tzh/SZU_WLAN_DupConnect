package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectSessionTest {
    private class FakeWifi : WifiController {
        var connectedSsid: String? = null
        var disconnectCount = 0
        var connectResult = true
        override fun connect(ssid: String): Boolean {
            connectedSsid = ssid
            return connectResult
        }
        override fun disconnect() {
            disconnectCount++
            connectedSsid = null
        }
    }

    private class FakePortal(var ok: Boolean = true) : PortalClient {
        var lastCreds: Credentials? = null
        override fun login(credentials: Credentials): Boolean {
            lastCreds = credentials
            return ok
        }
    }

    private class FakeProbe(var ok: Boolean = true) : InternetProbe {
        override fun canReachBaidu(): Boolean = ok
    }

    private class RecordingSleeper : Sleeper {
        val sleeps = mutableListOf<Long>()
        override fun sleep(millis: Long) {
            sleeps.add(millis)
        }
    }

    @Test
    fun failureIncrementsCounterDisconnectsAndWaits3s() {
        val wifi = FakeWifi()
        val portal = FakePortal(ok = false)
        val sleeper = RecordingSleeper()
        val session = ConnectSession(wifi, portal, FakeProbe(), sleeper)

        val result = session.runOnce(Credentials("a", "b"))

        assertTrue(result is ConnectResult.Failure)
        val failure = result as ConnectResult.Failure
        assertEquals(1, failure.attemptCount)
        assertEquals(1, session.attemptCount)
        assertEquals(1, wifi.disconnectCount)
        assertEquals(listOf(3_000L), sleeper.sleeps)
        assertEquals(3_000L, failure.reconnectDelayMs)
        assertTrue(session.logs().isNotEmpty())
    }

    @Test
    fun successOnlyAfterBaiduProbeThenResetsCounterAndLogs() {
        val wifi = FakeWifi()
        val portal = FakePortal(ok = true)
        val probe = FakeProbe(ok = true)
        val sleeper = RecordingSleeper()
        val session = ConnectSession(wifi, portal, probe, sleeper)

        // Simulate a prior failure so counter is non-zero before success.
        portal.ok = false
        session.runOnce(Credentials("a", "b"))
        assertEquals(1, session.attemptCount)

        portal.ok = true
        probe.ok = false
        val failProbe = session.runOnce(Credentials("a", "b"))
        assertTrue(failProbe is ConnectResult.Failure)
        assertEquals(2, session.attemptCount)

        probe.ok = true
        val success = session.runOnce(Credentials("a", "b"))

        assertTrue(success is ConnectResult.Success)
        val s = success as ConnectResult.Success
        assertEquals(3, s.attemptsUsed)
        assertEquals(0, session.attemptCount)
        assertTrue(session.logs().isEmpty())
        assertEquals(PortalLogin.TARGET_SSID, wifi.connectedSsid)
        assertEquals(Credentials("a", "b"), portal.lastCreds)
        assertEquals(listOf(3_000L, 3_000L), sleeper.sleeps)
    }

    @Test
    fun successMessageExactCopy() {
        assertEquals(
            "我在深大联网仅用7次就联网成功，你也快来试试吧",
            SuccessMessage.format(7),
        )
    }
}
