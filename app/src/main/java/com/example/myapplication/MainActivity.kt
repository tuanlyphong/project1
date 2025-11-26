package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import java.util.*

class MainActivity : AppCompatActivity() {

    // --- UI (EXISTING MASSAGE CONTROLS) ---
    private lateinit var btnConnect: MaterialButton
    private lateinit var deviceInfoLayout: LinearLayout
    private lateinit var imgDevice: ImageView
    private lateinit var txtDeviceName: MaterialTextView
    private lateinit var txtBattery: MaterialTextView
    private lateinit var sliderIntensity: Slider
    private lateinit var txtLevel: MaterialTextView
    private lateinit var btnRotate: MaterialButton
    private lateinit var btnHeat: MaterialButton
    private lateinit var btnAssistant: MaterialButton

    // --- UI (NEW HEALTH TAB & SWITCHING) ---
    private lateinit var layoutControl: View
    private lateinit var layoutHealth: View
    private lateinit var tabControl: MaterialButton
    private lateinit var tabHealth: MaterialButton
    private lateinit var txtHeartRate: MaterialTextView
    private lateinit var txtSpO2: MaterialTextView

    // --- BLE STATE ---
    private var isConnected = false
    private var servicesDiscovered = false
    private var bluetoothGatt: BluetoothGatt? = null

    // --- CHARACTERISTICS ---
    private var controlChar: BluetoothGattCharacteristic? = null
    private var spo2Char: BluetoothGattCharacteristic? = null // NEW

    private val deviceName = "MassageProX1"

    // Custom UUIDs (Shared Service and Control Characteristic)
    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val CONTROL_CHAR_UUID = UUID.fromString("abcdef01-1234-5678-1234-56789abcdef0")

    // NEW SpO2 Characteristic UUID (Must match the ID you set on the ESP32)
    private val SPO2_CHAR_UUID = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")
    // Standard CCCD UUID (DO NOT CHANGE)
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Command IDs
    private object Cmd {
        const val ROTATE: Byte = 0x01
        const val HEAT: Byte = 0x02
        const val ASSISTANT: Byte = 0x03
        const val LEVEL: Byte = 0x10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- UI refs (Existing) ---
        btnConnect = findViewById(R.id.btnConnect)
        deviceInfoLayout = findViewById(R.id.deviceInfoLayout)
        imgDevice = findViewById(R.id.imgDevice)
        txtDeviceName = findViewById(R.id.txtDeviceName)
        txtBattery = findViewById(R.id.txtBattery)
        sliderIntensity = findViewById(R.id.sliderIntensity)
        txtLevel = findViewById(R.id.txtLevel)
        btnRotate = findViewById(R.id.btnRotate)
        btnHeat = findViewById(R.id.btnHeat)
        btnAssistant = findViewById(R.id.btnAssistant)

        // --- UI refs (NEW) ---
        layoutControl = findViewById(R.id.layoutControl)
        layoutHealth = findViewById(R.id.layoutHealth)
        tabControl = findViewById(R.id.tabControl)
        tabHealth = findViewById(R.id.tabHealth)
        txtHeartRate = findViewById(R.id.txtHeartRate)
        txtSpO2 = findViewById(R.id.txtSpO2)

        // --- Tab Click Listeners (NEW) ---
        tabControl.setOnClickListener {
            // Show Control, Hide Health
            layoutControl.visibility = View.VISIBLE
            layoutHealth.visibility = View.GONE
            // Using ContextCompat for color consistency
            tabControl.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            tabHealth.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }

        tabHealth.setOnClickListener {
            // Show Health, Hide Control
            layoutControl.visibility = View.GONE
            layoutHealth.visibility = View.VISIBLE

            // Using ContextCompat for color consistency
            tabControl.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            tabHealth.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        }

        checkPermissions()

        // Connect / Disconnect
        btnConnect.setOnClickListener {
            if (!isConnected) {
                if (hasBlePermissions()) scanAndConnect()
                else showToast("Bluetooth permissions required")
            } else disconnectBle()
        }

        // Slider → LEVEL command
        sliderIntensity.addOnChangeListener { _, value, _ ->
            txtLevel.text = "Level: ${value.toInt()}"
            if (isConnected && servicesDiscovered) {
                val level = value.toInt().coerceIn(0, 255).toByte()
                sendBlePacket(Cmd.LEVEL, byteArrayOf(level))
            }
        }

        btnRotate.setOnClickListener {
            if (isConnected && servicesDiscovered) sendBlePacket(Cmd.ROTATE)
            else showToast("Please wait for connection to complete")
        }
        btnHeat.setOnClickListener {
            if (isConnected && servicesDiscovered) sendBlePacket(Cmd.HEAT)
            else showToast("Please wait for connection to complete")
        }
        btnAssistant.setOnClickListener {
            if (isConnected && servicesDiscovered) sendBlePacket(Cmd.ASSISTANT)
            else showToast("Please wait for connection to complete")
        }
    }

    // --- Permissions ---
    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
            }
        }
    }

    // --- BLE ---
    @SuppressLint("MissingPermission")
    private fun scanAndConnect() {
        val bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = bluetoothAdapter?.bondedDevices?.firstOrNull { it.name == deviceName }
        if (device == null) {
            showToast("$deviceName not paired")
            return
        }
        connectBleDevice(device)
    }

    @SuppressLint("MissingPermission")
    private fun connectBleDevice(device: BluetoothDevice) {
        showToast("Connecting...")
        bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                runOnUiThread {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            showToast("Connected - Discovering services...")
                            isConnected = true
                            updateUI()
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            showToast("Disconnected")
                            isConnected = false
                            servicesDiscovered = false
                            updateUI()
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                runOnUiThread {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt.getService(SERVICE_UUID)

                        // Find both characteristics
                        controlChar = service?.getCharacteristic(CONTROL_CHAR_UUID)
                        spo2Char = service?.getCharacteristic(SPO2_CHAR_UUID)

                        if (controlChar != null && spo2Char != null) {
                            servicesDiscovered = true
                            showToast("Ready to control and monitor.")
                            updateUI()
                            // Enable notifications immediately after discovery
                            enableSpO2Notifications(gatt)
                        } else {
                            showToast("One or more characteristics not found. Check ESP32 UUIDs.")
                        }
                    } else {
                        showToast("Service discovery failed: $status")
                    }
                }
            }

            // NEW CALLBACK: Receives data pushed from the ESP32
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == SPO2_CHAR_UUID) {
                    // Read raw bytes from the characteristic
                    val data = characteristic.value

                    // ASSUMPTION: ESP32 sends a 2-byte packet: [Byte 0: HeartRate, Byte 1: SpO2]
                    if (data != null && data.size >= 2) {
                        // Use 'and 0xFF' to convert the signed byte to an unsigned integer (0-255)
                        val heartRate = data[0].toInt() and 0xFF
                        val spo2 = data[1].toInt() and 0xFF

                        // Update the UI on the main thread
                        runOnUiThread {
                            txtHeartRate.text = "$heartRate BPM"
                            txtSpO2.text = "$spo2 %"
                        }
                    }
                }
            }
        })
    }

    // NEW FUNCTION: Writes to the CCCD descriptor to subscribe to notifications
    @SuppressLint("MissingPermission")
    private fun enableSpO2Notifications(gatt: BluetoothGatt) {
        if (spo2Char == null || !hasBlePermissions()) return

        // 1. Enable notifications locally
        gatt.setCharacteristicNotification(spo2Char, true)

        // 2. Write to the CCCD (Client Characteristic Configuration Descriptor)
        val descriptor = spo2Char?.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            showToast("Notifications enabled.")
        } else {
            showToast("CCCD descriptor not found for SpO2!")
        }
    }

    // --- Send packets ---

    @SuppressLint("MissingPermission")
    private fun sendBlePacket(command: Byte, payload: ByteArray = byteArrayOf()) {
        if (!isConnected || !servicesDiscovered || controlChar == null) {
            showToast("BLE not ready")
            return
        }
        // ... (rest of sendBlePacket is unchanged) ...
        if (!hasBlePermissions()) {
            showToast("Missing Bluetooth permissions")
            return
        }

        val packet = byteArrayOf(command) + payload
        controlChar!!.value = packet

        try {
            bluetoothGatt?.let { gatt ->
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    val success = gatt.writeCharacteristic(controlChar)
                    if (success) {
                        showToast("Sent: ${packet.joinToString(" ") { String.format("%02X", it) }}")
                    } else {
                        showToast("Write failed")
                    }
                } else {
                    showToast("Permission denied at runtime")
                }
            }
        } catch (e: SecurityException) {
            showToast("Failed: ${e.message}")
        }
    }

    // --- Disconnect ---

    private fun disconnectBle() {
        if (!hasBlePermissions()) {
            showToast("Missing Bluetooth permissions")
            return
        }

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            showToast("Disconnect failed: ${e.message}")
        }

        isConnected = false
        servicesDiscovered = false
        controlChar = null
        updateUI()
        showToast("Disconnected")
    }

    // --- UI ---
    private fun updateUI() {
        if (isConnected && servicesDiscovered) {
            btnConnect.text = "Connected"
            deviceInfoLayout.visibility = View.VISIBLE
            sliderIntensity.isEnabled = true
            btnRotate.isEnabled = true
            btnHeat.isEnabled = true
            btnAssistant.isEnabled = true
            txtDeviceName.text = deviceName
            txtBattery.text = "Battery: 85%"
            imgDevice.setImageResource(R.drawable.ic_launcher_foreground)
        } else {
            btnConnect.text = if (isConnected) "Connecting..." else "Connect"
            deviceInfoLayout.visibility = View.GONE
            sliderIntensity.isEnabled = false
            btnRotate.isEnabled = false
            btnHeat.isEnabled = false
            btnAssistant.isEnabled = false
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}