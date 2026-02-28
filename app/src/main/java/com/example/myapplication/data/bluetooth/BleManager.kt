package com.example.myapplication.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.example.myapplication.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null

    // State flows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults

    // Callbacks
    var onConnectionSuccess: (() -> Unit)? = null
    var onConnectionFailed: ((String) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        SERVICES_DISCOVERED
    }

    // Scan Callback
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name
            Log.d(TAG, "Scan found: name='$deviceName', address=${device.address}, rssi=${result.rssi}")
            if (deviceName == Constants.DEVICE_NAME) {
                stopScan()
                connectToDevice(device)
            }

            // Add to scan results
            val bleDevice = BleDevice(device, deviceName, device.address, result.rssi)
            val currentList = _scanResults.value.toMutableList()
            if (!currentList.contains(bleDevice)) {
                currentList.add(bleDevice)
                _scanResults.value = currentList
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            onConnectionFailed?.invoke("Scan failed with error: $errorCode")
        }
    }

    // GATT Callback
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _connectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    cleanup()
                    onDisconnected?.invoke()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(Constants.SERVICE_UUID)
                if (service != null) {
                    controlCharacteristic = service.getCharacteristic(Constants.CONTROL_CHAR_UUID)

                    _connectionState.value = ConnectionState.SERVICES_DISCOVERED
                    onConnectionSuccess?.invoke()
                    Log.d(TAG, "Services discovered successfully")
                } else {
                    onConnectionFailed?.invoke("Required service not found")
                }
            } else {
                onConnectionFailed?.invoke("Service discovery failed: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful")
            } else {
                Log.e(TAG, "Characteristic write failed: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            onConnectionFailed?.invoke("Bluetooth not available")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        _scanResults.value = emptyList()
        scanner.startScan(scanCallback)
        Log.d(TAG, "Started BLE scan")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        Log.d(TAG, "Stopped BLE scan")
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        Log.d(TAG, "Connecting to device: ${device.name}")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        cleanup()
    }

    private fun cleanup() {
        bluetoothGatt = null
        controlCharacteristic = null
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: Byte, payload: ByteArray = byteArrayOf()): Boolean {
        if (_connectionState.value != ConnectionState.SERVICES_DISCOVERED) {
            Log.e(TAG, "Cannot send command: services not discovered")
            return false
        }

        val characteristic = controlCharacteristic ?: return false
        val packet = byteArrayOf(command) + payload
        characteristic.value = packet

        val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        if (success) {
            Log.d(TAG, "Sent command: ${packet.joinToString(" ") { String.format("%02X", it) }}")
        }
        return success
    }

    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.SERVICES_DISCOVERED
    }

    companion object {
        private const val TAG = "BleManager"
    }
}