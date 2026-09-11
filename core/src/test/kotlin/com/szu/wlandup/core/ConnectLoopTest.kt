package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ConnectLoopTest {
    private class FakeWifi : WifiController {
        val disconnects = AtomicInteger(0)
        override fun connect(ssid: String): Boolean = true
        override fun disconnect() {
            disconnects.incrementAndGet()
        }
    }

    private class FakePortal(private val okUntil: AtomicInteger) : ZonePortalClient {
        val logins = mutableListOf<Credentials>()
        override fun login(zone: CampusZone, credentials: Credentials): Boolean {
            logins += credentials
            return okUntil.getAndDecrement() <= 0
        }
    }

    @Test
    fun stopLatchAbortsLoopWithoutClearingStopFlag() {
        val stop = AtomicBoolean(false)
        val store = AtomicReference(Credentials("a", "b"))
        val portal = FakePortal(AtomicInteger(100))
        val session = ConnectSession(
            wifi = FakeWifi(),
            portal = portal,
            probe = InternetProbe { false },
            clock = Sleeper {
                // Simulate user deleting credentials during the 3s wait.
                store.set(null)
                stop.set(true)
            },
        )
        val loop = ConnectLoop(
            session = session,
            loadCredentials = { store.get() },
            shouldStop = { stop.get() },
        )

        val exit = loop.run()

        assertEquals(ConnectLoopExit.Stopped, exit)
        assertTrue(stop.get(), "stop latch must remain set for the caller to clear")
        assertEquals(1, portal.logins.size)
        assertEquals(Credentials("a", "b"), portal.logins.first())
    }

    @Test
    fun successExitKeepsWifiAssociationWhileAbortTearsDown() {
        assertEquals(false, ConnectLoopExit.Succeeded(ConnectResult.Success(1)).shouldTearDownWifi())
        assertEquals(true, ConnectLoopExit.Stopped.shouldTearDownWifi())
        assertEquals(true, ConnectLoopExit.CredentialsGone.shouldTearDownWifi())
    }

    @Test
    fun successfulLoopDoesNotDisconnectAfterPortalAndBaiduOk() {
        val wifi = FakeWifi()
        val session = ConnectSession(
            wifi = wifi,
            portal = ZonePortalClient { _, _ -> true },
            probe = InternetProbe { true },
            clock = Sleeper { },
        )
        val exit = ConnectLoop(
            session = session,
            loadCredentials = { Credentials("a", "b") },
            shouldStop = { false },
        ).run()

        assertTrue(exit is ConnectLoopExit.Succeeded)
        assertEquals(false, exit.shouldTearDownWifi())
        assertEquals(0, wifi.disconnects.get(), "success path must not tear down Wi-Fi")
    }

    @Test
    fun deletedCredentialsAreNeverReusedOnNextIteration() {
        val stop = AtomicBoolean(false)
        val store = AtomicReference<Credentials?>(Credentials("old", "secret"))
        val portal = FakePortal(AtomicInteger(100))
        val wifi = FakeWifi()
        val session = ConnectSession(
            wifi = wifi,
            portal = portal,
            probe = InternetProbe { false },
            clock = Sleeper {
                store.set(null) // delete during reconnect wait
            },
        )
        val loop = ConnectLoop(
            session = session,
            loadCredentials = { store.get() },
            shouldStop = { stop.get() },
        )

        val exit = loop.run()

        assertEquals(ConnectLoopExit.CredentialsGone, exit)
        assertEquals(1, portal.logins.size)
        assertEquals("old", portal.logins.single().userAccount)
        assertTrue(wifi.disconnects.get() >= 1)
    }
}
