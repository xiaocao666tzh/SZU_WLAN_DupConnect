package com.szu.wlandup

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import com.szu.wlandup.core.ConfiguredWifiNetwork
import com.szu.wlandup.core.LegacyWifiControls
import com.szu.wlandup.core.LegacyWifiTeardown
import com.szu.wlandup.core.PortalLogin
import com.szu.wlandup.core.WifiController
import com.szu.wlandup.core.applyTo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WifiConnector(private val context: Context) : WifiController {
    private val connectivity =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Volatile
    private var boundNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var legacyNetId: Int? = null

    override fun connect(ssid: String): Boolean {
        if (!wifiManager.isWifiEnabled) {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = true
        }
        disconnect()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return connectWithSpecifier(ssid)
        }
        return connectLegacy(ssid)
    }

    override fun disconnect() {
        callback?.let {
            try {
                connectivity.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        callback = null
        boundNetwork = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivity.bindProcessToNetwork(null)
        }
        tearDownLegacyWifi()
    }

    private fun tearDownLegacyWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            legacyNetId = null
            return
        }
        @Suppress("DEPRECATION")
        val configured = try {
            wifiManager.configuredNetworks.orEmpty().map {
                ConfiguredWifiNetwork(it.networkId, it.SSID ?: "")
            }
        } catch (_: SecurityException) {
            emptyList()
        }
        val plan = LegacyWifiTeardown.plan(
            activeNetId = legacyNetId,
            targetSsid = PortalLogin.TARGET_SSID,
            configuredNetworks = configured,
        )
        plan.applyTo(object : LegacyWifiControls {
            @Suppress("DEPRECATION")
            override fun disconnectRadio() {
                wifiManager.disconnect()
            }

            @Suppress("DEPRECATION")
            override fun disableNetwork(networkId: Int) {
                wifiManager.disableNetwork(networkId)
            }
        })
        legacyNetId = null
    }

    private fun connectWithSpecifier(ssid: String): Boolean {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsidPattern(PatternMatcher(ssid, PatternMatcher.PATTERN_LITERAL))
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                boundNetwork = network
                connectivity.bindProcessToNetwork(network)
                ok.set(true)
                latch.countDown()
            }

            override fun onUnavailable() {
                ok.set(false)
                latch.countDown()
            }
        }
        callback = cb
        connectivity.requestNetwork(request, cb)
        latch.await(25, TimeUnit.SECONDS)
        return ok.get()
    }

    @Suppress("DEPRECATION")
    private fun connectLegacy(ssid: String): Boolean {
        val conf = android.net.wifi.WifiConfiguration().apply {
            SSID = "\"$ssid\""
            allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
        }
        val netId = wifiManager.addNetwork(conf)
        if (netId == -1) return false
        legacyNetId = netId
        wifiManager.disconnect()
        val enabled = wifiManager.enableNetwork(netId, true)
        wifiManager.reconnect()
        return enabled
    }
}
