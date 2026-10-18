package com.omenight.bright.setup.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Security { Open, Psk, Sae, Unsupported }

data class WifiNetwork(val ssid: String, val bars: Int, val security: Security)

sealed interface Link {
    data object None : Link
    data object Ethernet : Link
    /** [ssid] is null when the system withholds the name. */
    data class Wifi(val ssid: String?) : Link
}

sealed interface Joining {
    data object Idle : Joining
    data class Connecting(val ssid: String) : Joining
    data class WrongPassword(val ssid: String) : Joining
    data class Failed(val ssid: String) : Joining
}

/**
 * Scans and joins Wi-Fi networks for the network page. Uses the framework calls TV settings uses,
 * open to this app because it is a system app and the device's setup wizard.
 */
@Suppress("DEPRECATION")
class WifiSetup(context: Context) {
    private val context = context.applicationContext
    private val wifi = this.context.getSystemService(WifiManager::class.java)
    private val connectivity = this.context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val location = this.context.getSystemService(LocationManager::class.java)
    private var running = false
    private var joiningId = -1
    private var locationWasOff = false

    private val _networks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val networks: StateFlow<List<WifiNetwork>> = _networks.asStateFlow()

    private val _link = MutableStateFlow<Link>(Link.None)
    val link: StateFlow<Link> = _link.asStateFlow()

    private val _joining = MutableStateFlow<Joining>(Joining.Idle)
    val joining: StateFlow<Joining> = _joining.asStateFlow()

    private val rescan = object : Runnable {
        override fun run() {
            wifi.startScan()
            handler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    private val joinTimeout = Runnable {
        (_joining.value as? Joining.Connecting)?.let { forget(); _joining.value = Joining.Failed(it.ssid) }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> publishScan()
                WifiManager.WIFI_STATE_CHANGED_ACTION -> if (wifi.isWifiEnabled) wifi.startScan()
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    val info: NetworkInfo? = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
                    if (info?.isConnected == true) onWifiConnected()
                }
                WifiManager.SUPPLICANT_STATE_CHANGED_ACTION -> {
                    val error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0)
                    val joining = _joining.value
                    if (error == WifiManager.ERROR_AUTHENTICATING && joining is Joining.Connecting) {
                        forget()
                        _joining.value = Joining.WrongPassword(joining.ssid)
                    }
                }
            }
        }
    }

    private val linkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refreshLink()
        override fun onLost(network: Network) = refreshLink()
    }

    fun start() {
        if (running) return
        running = true
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
        })
        connectivity.registerDefaultNetworkCallback(linkCallback)
        // Android 10 hides scan results and the SSID while location is off. The setup wizard permission
        // lifts that, so location is only switched on when the image left another wizard installed.
        val setupWizard = context.checkSelfPermission(NETWORK_SETUP_WIZARD) == PackageManager.PERMISSION_GRANTED
        locationWasOff = !setupWizard && !location.isLocationEnabled
        if (locationWasOff) setLocationEnabled(true)
        refreshLink()
        if (!wifi.isWifiEnabled) wifi.isWifiEnabled = true
        publishScan()
        handler.post(rescan)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(rescan)
        handler.removeCallbacks(joinTimeout)
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { connectivity.unregisterNetworkCallback(linkCallback) }
        if (locationWasOff) setLocationEnabled(false)
    }

    // A system API on Android 10, guarded by WRITE_SECURE_SETTINGS
    private fun setLocationEnabled(enabled: Boolean) {
        runCatching {
            LocationManager::class.java
                .getMethod("setLocationEnabledForUser", Boolean::class.javaPrimitiveType, UserHandle::class.java)
                .invoke(location, enabled, Process.myUserHandle())
        }.onFailure { Log.w(TAG, "location toggle", it) }
    }

    /** Joins [network], [password] is ignored for open networks. */
    fun join(network: WifiNetwork, password: String) {
        val config = WifiConfiguration().apply {
            SSID = "\"${network.ssid}\""
            when (network.security) {
                Security.Open -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                Security.Psk -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    preSharedKey = "\"$password\""
                }
                Security.Sae -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE)
                    // WPA3 mandates management frame protection, the field is hidden before Android 11
                    runCatching { WifiConfiguration::class.java.getField("requirePMF").setBoolean(this, true) }
                    preSharedKey = "\"$password\""
                }
                Security.Unsupported -> return
            }
        }
        forget()
        val id = wifi.addNetwork(config)
        if (id == -1 || !wifi.enableNetwork(id, true)) {
            Log.w(TAG, "addNetwork $id refused")
            _joining.value = Joining.Failed(network.ssid)
            return
        }
        joiningId = id
        wifi.reconnect()
        _joining.value = Joining.Connecting(network.ssid)
        handler.removeCallbacks(joinTimeout)
        handler.postDelayed(joinTimeout, JOIN_TIMEOUT_MS)
    }

    fun clearJoining() {
        if (_joining.value !is Joining.Connecting) _joining.value = Joining.Idle
    }

    // A wrong password would otherwise stay saved and the box would keep retrying it
    private fun forget() {
        if (joiningId != -1) wifi.removeNetwork(joiningId)
        joiningId = -1
    }

    private fun onWifiConnected() {
        refreshLink()
        val joining = _joining.value as? Joining.Connecting ?: return
        if (currentSsid() == joining.ssid) {
            handler.removeCallbacks(joinTimeout)
            joiningId = -1
            _joining.value = Joining.Idle
        }
    }

    private fun refreshLink() {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        _link.value = when {
            capabilities == null -> Link.None
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Link.Ethernet
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Link.Wifi(currentSsid())
            else -> Link.None
        }
    }

    private fun currentSsid(): String? {
        val info = wifi.connectionInfo ?: return null
        if (info.supplicantState != SupplicantState.COMPLETED) return null
        return info.ssid?.removeSurrounding("\"")?.takeUnless { it.isEmpty() || it == WifiManager.UNKNOWN_SSID }
    }

    // Rows keep their place across rescans so the list never shifts under the D-pad focus.
    // Only networks seen for the first time are sorted, strongest first, and appended.
    private fun publishScan() {
        val fresh = wifi.scanResults
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .mapValues { (ssid, results) ->
                val best = results.maxBy { it.level }
                WifiNetwork(ssid, bars(best.level), security(best))
            }
        val kept = _networks.value.mapNotNull { fresh[it.ssid] }
        val known = kept.mapTo(HashSet()) { it.ssid }
        val added = fresh.values
            .filterNot { it.ssid in known }
            .sortedWith(compareByDescending<WifiNetwork> { it.bars }.thenBy { it.ssid.lowercase() })
        _networks.value = kept + added
    }

    private companion object {
        const val TAG = "BrightSetup"
        const val NETWORK_SETUP_WIZARD = "android.permission.NETWORK_SETUP_WIZARD"
        const val SCAN_INTERVAL_MS = 15_000L
        const val JOIN_TIMEOUT_MS = 30_000L

        // A WPA2/WPA3 transition network is joined as WPA2, the system image is patched to match it that way
        fun security(result: ScanResult): Security {
            val caps = result.capabilities
            return when {
                "EAP" in caps || "WEP" in caps || "OWE" in caps -> Security.Unsupported
                "PSK" in caps -> Security.Psk
                "SAE" in caps -> Security.Sae
                else -> Security.Open
            }
        }

        fun bars(rssi: Int): Int = when {
            rssi >= -55 -> 3
            rssi >= -67 -> 2
            rssi >= -78 -> 1
            else -> 0
        }
    }
}
