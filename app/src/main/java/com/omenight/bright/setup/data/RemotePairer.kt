package com.omenight.bright.setup.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface RemoteState {
    data object Unavailable : RemoteState
    data object Searching : RemoteState
    data class Pairing(val name: String) : RemoteState
    data class Paired(val name: String) : RemoteState
    data class Failed(val name: String) : RemoteState
}

/** A named device seen nearby, offered for manual pairing when it does not look like a remote. */
data class NearbyDevice(val address: String, val name: String)

/**
 * Keeps Bluetooth discovery running while the remote page is open and bonds the first device
 * that looks like a remote. Anything else seen nearby is listed so it can be picked by hand.
 */
class RemotePairer(context: Context) {
    private val context = context.applicationContext
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val devices = HashMap<String, BluetoothDevice>()
    private var target: BluetoothDevice? = null
    private var running = false

    private val _state = MutableStateFlow<RemoteState>(if (adapter == null) RemoteState.Unavailable else RemoteState.Searching)
    val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val _nearby = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearby: StateFlow<List<NearbyDevice>> = _nearby.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0) == BluetoothAdapter.STATE_ON) discover()
                BluetoothDevice.ACTION_FOUND -> device?.let(::onFound)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (target == null) discover()
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> device?.let { onBondChanged(it, intent) }
                BluetoothDevice.ACTION_PAIRING_REQUEST -> device?.let { onPairingRequest(it, intent) }
            }
        }
    }

    fun start() {
        val adapter = adapter ?: return
        if (running) return
        running = true
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            // Ahead of the system pairing dialog, which would otherwise ask for consent on screen
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1
        }
        context.registerReceiver(receiver, filter)
        bondedRemote()?.let { _state.value = RemoteState.Paired(it.label()) }
        if (adapter.isEnabled) discover() else adapter.enable()
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { context.unregisterReceiver(receiver) }
        adapter?.cancelDiscovery()
    }

    /** Pairs a device from the nearby list. */
    fun pair(address: String) {
        devices[address]?.let(::bond)
    }

    /** Back to searching after a failure, or to pair one more remote. */
    fun retry() {
        target = null
        _state.value = RemoteState.Searching
        discover()
    }

    private fun discover() {
        val adapter = adapter ?: return
        if (!running || target != null || !adapter.isEnabled || adapter.isDiscovering) return
        adapter.startDiscovery()
    }

    private fun onFound(device: BluetoothDevice) {
        val name = device.name?.trim().orEmpty()
        if (name.isEmpty() || device.bondState == BluetoothDevice.BOND_BONDED) return
        devices[device.address] = device
        if (target == null && _state.value !is RemoteState.Paired && looksLikeRemote(device, name)) {
            bond(device)
            return
        }
        _nearby.update { list ->
            if (list.any { it.address == device.address }) list else (list + NearbyDevice(device.address, name)).take(6)
        }
    }

    private fun bond(device: BluetoothDevice) {
        target = device
        adapter?.cancelDiscovery()
        _state.value = RemoteState.Pairing(device.label())
        if (!device.createBond()) fail(device)
    }

    private fun onBondChanged(device: BluetoothDevice, intent: Intent) {
        if (device.address != target?.address) return
        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
            BluetoothDevice.BOND_BONDED -> {
                target = null
                connectInput(device)
                _state.value = RemoteState.Paired(device.label())
                _nearby.update { list -> list.filterNot { it.address == device.address } }
            }
            BluetoothDevice.BOND_NONE -> fail(device)
        }
    }

    private fun fail(device: BluetoothDevice) {
        target = null
        _state.value = RemoteState.Failed(device.label())
    }

    // Remotes use Just Works or a fixed PIN, so answer for the user instead of showing a dialog
    private fun BroadcastReceiver.onPairingRequest(device: BluetoothDevice, intent: Intent) {
        if (device.address != target?.address) return
        val handled = runCatching {
            when (intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)) {
                BluetoothDevice.PAIRING_VARIANT_PIN -> device.setPin("0000".toByteArray())
                BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION, PAIRING_VARIANT_CONSENT -> device.setPairingConfirmation(true)
                else -> false
            }
        }.onFailure { Log.w(TAG, "pairing confirmation", it) }.getOrDefault(false)
        if (handled && isOrderedBroadcast) abortBroadcast()
    }

    // Bonding alone does not always bring the HID link up, the TV settings pairer connects it explicitly
    private fun connectInput(device: BluetoothDevice) {
        adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                runCatching { proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, device) }
                    .onFailure { Log.w(TAG, "hid connect", it) }
                adapter.closeProfileProxy(profile, proxy)
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }, HID_HOST)
    }

    private fun bondedRemote(): BluetoothDevice? =
        adapter?.bondedDevices?.firstOrNull { looksLikeRemote(it, it.name?.trim().orEmpty()) }

    private fun BluetoothDevice.label(): String = name?.trim().takeUnless { it.isNullOrEmpty() } ?: address

    private companion object {
        const val TAG = "BrightSetup"

        // Both hidden in the SDK
        const val HID_HOST = 4
        const val PAIRING_VARIANT_CONSENT = 3

        // The factory remote calls itself "Bluetooth remote", others usually say remote, RCU or пульт
        val remoteName = Regex("remote|rcu|пульт|\\bRC\\b", RegexOption.IGNORE_CASE)

        // A bare peripheral or the "remote control" minor class, keyboards, mice and gamepads stay manual
        fun looksLikeRemote(device: BluetoothDevice, name: String): Boolean {
            val type = device.bluetoothClass?.deviceClass ?: 0
            val remoteClass = type == BluetoothClass.Device.Major.PERIPHERAL || (type and 0x1F3C) == 0x50C
            return remoteClass || remoteName.containsMatchIn(name)
        }
    }
}
