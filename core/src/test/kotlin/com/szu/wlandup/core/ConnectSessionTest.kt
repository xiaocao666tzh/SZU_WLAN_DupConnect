package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectSessionTest {
    private class FakeWifi : WifiController {
        var connectedSsid: String? = null
        var disconnectCount = 0
        var connectResultBySsid: Map<String, Boolean> = CampusNetworks.CONNECT_ORDER.associateWith { true }
        override fun connect(ssid: String): Boolean {
            val ok = connectResultBySsid[ssid] == true
            connectedSsid = if (ok) ssid else null
            return ok
        }
        override fun disconnect() {
            disconnectCount++
            connectedSsid = null
        }
    }

    private class FakePortal(
        var dormOk: Boolean = true,
        var teachOk: Boolean = true,
    ) : ZonePortalClient {
        var lastZone: CampusZone? = null
        var lastCreds: Credentials? = null
        override fun login(zone: CampusZone, credentials: Credentials): Boolean {
            lastZone = zone
            lastCreds = credentials
            return when (zone) {
                CampusZone.DORM -> dormOk
                CampusZone.TEACH -> teachOk
                CampusZone.NONE -> false
            }
        }
    }

    private class FakeProbe(var ok: Boolean = true) : InternetProbe {
        override fun canReachBaidu(): Boolean = ok
    }

    private class FakeInspector(var snapshot: NetworkSnapshot = NetworkSnapshot()) : NetworkInspector {
        override fun snapshot(): NetworkSnapshot = snapshot
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
        val portal = FakePortal(dormOk = false)
        val sleeper = RecordingSleeper()
        val session = ConnectSession(wifi, portal, FakeProbe(), FakeInspector(), sleeper)

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
        val portal = FakePortal(dormOk = true)
        val probe = FakeProbe(ok = true)
        val sleeper = RecordingSleeper()
        val session = ConnectSession(wifi, portal, probe, FakeInspector(), sleeper)

        portal.dormOk = false
        session.runOnce(Credentials("a", "b"))
        assertEquals(1, session.attemptCount)

        portal.dormOk = true
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
        assertEquals(CampusNetworks.DORM_SSIDS.first(), wifi.connectedSsid)
        assertEquals(CampusZone.DORM, portal.lastZone)
        assertEquals(Credentials("a", "b"), portal.lastCreds)
        assertEquals(listOf(3_000L, 3_000L), sleeper.sleeps)
    }

    @Test
    fun fallsBackToTeachSsidAndUsesTeachPortal() {
        val wifi = FakeWifi().apply {
            connectResultBySsid = mapOf(
                "SZU_CTC&CMCC" to false,
                "SZU_WLAN" to true,
                "SZU-WLAN" to false,
            )
        }
        val portal = FakePortal(teachOk = true)
        val session = ConnectSession(
            wifi = wifi,
            portal = portal,
            probe = FakeProbe(true),
            inspector = FakeInspector(NetworkSnapshot(ssid = "SZU_WLAN")),
            clock = RecordingSleeper(),
        )

        val result = session.runOnce(Credentials("u", "p"))
        assertTrue(result is ConnectResult.Success)
        assertEquals("SZU_WLAN", wifi.connectedSsid)
        assertEquals(CampusZone.TEACH, portal.lastZone)
        assertEquals(CampusZone.TEACH, session.lastZone)
    }

    @Test
    fun unknownZoneFailsAfterConnect() {
        val wifi = FakeWifi().apply {
            connectResultBySsid = mapOf("GuestNet" to true)
        }
        val session = ConnectSession(
            wifi = wifi,
            portal = FakePortal(),
            probe = FakeProbe(true),
            inspector = FakeInspector(NetworkSnapshot(ssid = "GuestNet")),
            clock = RecordingSleeper(),
            ssidOrder = listOf("GuestNet"),
        )
        val result = session.runOnce(Credentials("u", "p"))
        assertTrue(result is ConnectResult.Failure)
        assertEquals(1, wifi.disconnectCount)
    }

    @Test
    fun successMessageExactCopy() {
        assertEquals(
            "我在深大联网仅用7次就联网成功，你也快来试试吧",
            SuccessMessage.format(7),
        )
    }
}
