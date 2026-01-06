package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
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
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog

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
    private lateinit var waveformView: WaveformView

    // --- BLE STATE ---
    private var isConnected = false
    private var servicesDiscovered = false
    private var bluetoothGatt: BluetoothGatt? = null

    // --- CHARACTERISTICS ---
    private var controlChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val deviceName = "Massage_Pro_X1"  // FIXED: Match ESP32 exactly

    // FIXED: Match ESP32 UUIDs exactly
    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789ABCDEF0")
    private val CONTROL_CHAR_UUID = UUID.fromString("ABCDEF01-1234-5678-1234-56789ABCDEF0")
    private val NOTIFY_CHAR_UUID = UUID.fromString("ABCDEF02-1234-5678-1234-56789ABCDEF0")

    // Standard CCCD UUID (DO NOT CHANGE)
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // --- Assistant Result Handler ---
    private val assistantLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val level = data.getIntExtra("level", 3)
                    val heat = data.getBooleanExtra("heat", false)
                    val duration = data.getIntExtra("duration", 15)

                    applyAssistantSettings(level, heat, duration)
                }
            }
        }

    // Timer reference
    private var massageTimer: CountDownTimer? = null

    // FIXED: Command IDs to match ESP32 firmware
    private object Cmd {
        const val ROTATE: Byte = 0x01
        const val HEAT: Byte = 0x02
        const val ASSISTANT: Byte = 0x03  // Legacy
        const val LEVEL: Byte = 0x04
        const val ASSISTANT_CONFIG: Byte = 0x06
        const val ASSISTANT_STOP: Byte = 0x07
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- UI refs ---
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

        layoutControl = findViewById(R.id.layoutControl)
        layoutHealth = findViewById(R.id.layoutHealth)
        tabControl = findViewById(R.id.tabControl)
        tabHealth = findViewById(R.id.tabHealth)
        txtHeartRate = findViewById(R.id.txtHeartRate)
        txtSpO2 = findViewById(R.id.txtSpO2)
        waveformView = findViewById(R.id.waveformView)

        // --- Tab Click Listeners ---
        tabControl.setOnClickListener {
            layoutControl.visibility = View.VISIBLE
            layoutHealth.visibility = View.GONE
            tabControl.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            tabHealth.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }

        tabHealth.setOnClickListener {
            layoutControl.visibility = View.GONE
            layoutHealth.visibility = View.VISIBLE
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
                val level = value.toInt().coerceIn(0, 5).toByte()
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
            val intent = Intent(this, AssistantActivity::class.java)
            assistantLauncher.launch(intent)
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

                        if (service == null) {
                            showToast("Service not found! Check UUIDs.")
                            Log.e("BLE", "Available services:")
                            gatt.services.forEach { s ->
                                Log.e("BLE", "  - ${s.uuid}")
                            }
                            return@runOnUiThread
                        }

                        // Find characteristics
                        controlChar = service.getCharacteristic(CONTROL_CHAR_UUID)
                        notifyChar = service.getCharacteristic(NOTIFY_CHAR_UUID)

                        if (controlChar != null && notifyChar != null) {
                            servicesDiscovered = true
                            showToast("Ready!")
                            updateUI()
                            // Enable notifications
                            enableNotifications(gatt)
                        } else {
                            showToast("Characteristics not found. Check ESP32 UUIDs.")
                            Log.e("BLE", "Control: $controlChar, Notify: $notifyChar")
                        }
                    } else {
                        showToast("Service discovery failed: $status")
                    }
                }
            }

            // FIXED: Handle notification data from ESP32
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == NOTIFY_CHAR_UUID) {
                    val data = characteristic.value
                    if (data == null || data.isEmpty()) return

                    val packetType = data[0].toInt() and 0xFF

                    when (packetType) {
                        0xF1 -> {
                            // Health data: [0xF1][HR][SpO2]
                            if (data.size >= 3) {
                                val heartRate = data[1].toInt() and 0xFF
                                val spo2 = data[2].toInt() and 0xFF

                                runOnUiThread {
                                    txtHeartRate.text = if (heartRate > 0) "$heartRate BPM" else "-- BPM"
                                    txtSpO2.text = if (spo2 > 0) "$spo2 %" else "-- %"
                                }
                            }
                        }

                        0xF2 -> {
                            // Waveform data: [0xF2][IR_HIGH][IR_MID][IR_LOW]
                            if (data.size >= 4) {
                                val irValue = ((data[1].toInt() and 0xFF) shl 16) or
                                        ((data[2].toInt() and 0xFF) shl 8) or
                                        (data[3].toInt() and 0xFF)

                                runOnUiThread {
                                    waveformView.addDataPoint(irValue.toFloat())
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt) {
        if (notifyChar == null || !hasBlePermissions()) return

        // 1. Enable notifications locally
        val success = gatt.setCharacteristicNotification(notifyChar, true)
        Log.d("BLE", "setCharacteristicNotification: $success")

        // 2. Write to CCCD
        val descriptor = notifyChar?.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeSuccess = gatt.writeDescriptor(descriptor)
            Log.d("BLE", "writeDescriptor: $writeSuccess")
            showToast("Notifications enabled")
        } else {
            showToast("CCCD not found!")
            Log.e("BLE", "CCCD descriptor not found")
        }
    }

    // --- Send packets ---
    @SuppressLint("MissingPermission")
    private fun sendBlePacket(command: Byte, payload: ByteArray = byteArrayOf()) {
        if (!isConnected || !servicesDiscovered || controlChar == null) {
            showToast("BLE not ready")
            return
        }

        if (!hasBlePermissions()) {
            showToast("Missing Bluetooth permissions")
            return
        }

        val packet = byteArrayOf(command) + payload
        controlChar!!.value = packet

        try {
            bluetoothGatt?.let { gatt ->
                val success = gatt.writeCharacteristic(controlChar)
                if (success) {
                    Log.d("BLE", "Sent: ${packet.joinToString(" ") { String.format("%02X", it) }}")
                } else {
                    showToast("Write failed")
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
        notifyChar = null
        updateUI()
        showToast("Disconnected")
    }

    // --- UI ---
    private fun updateUI() {
        if (isConnected && servicesDiscovered) {
            btnConnect.text = "Connected"
            deviceInfoLayout.visibility = View.VISIBLE
            sliderIntensity.isEnabled = true
            sliderIntensity.valueTo = 5f  // FIXED: Match ESP32 (0-5)
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

    // --- Assistant Logic ---
    private fun applyAssistantSettings(level: Int, heat: Boolean, duration: Int) {
        if (!isConnected || !servicesDiscovered) {
            showToast("Please connect device first")
            return
        }

        // FIXED: Use CMD_ASSISTANT_CONFIG with proper packet format
        // Packet: [CMD][LEVEL][HEAT][DURATION_HIGH][DURATION_LOW]
        val durationHigh = (duration shr 8).toByte()
        val durationLow = (duration and 0xFF).toByte()
        val heatByte: Byte = if (heat) 1 else 0

        val packet = byteArrayOf(
            Cmd.ASSISTANT_CONFIG,
            level.toByte(),
            heatByte,
            durationHigh,
            durationLow
        )

        sendBlePacket(Cmd.ASSISTANT_CONFIG, packet.drop(1).toByteArray())

        // Show confirmation
        val message =
            "AI Settings Applied!\nLevel: $level\nHeat: ${if (heat) "ON" else "OFF"}\nDuration: $duration min"

        AlertDialog.Builder(this)
            .setTitle("Assistant Activated")
            .setMessage(message)
            .setPositiveButton("Start Timer") { _, _ ->
                startMassageTimer(duration)
            }
            .setNegativeButton("Skip Timer", null)
            .show()
    }

    private fun startMassageTimer(minutes: Int) {
        massageTimer?.cancel()

        massageTimer = object : CountDownTimer(minutes * 60 * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val mins = seconds / 60
                val secs = seconds % 60

                supportActionBar?.subtitle = "Timer: %02d:%02d".format(mins, secs)
            }

            override fun onFinish() {
                supportActionBar?.subtitle = null
                showSessionCompleteDialog()
            }
        }.start()
    }

    private fun showSessionCompleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("Session Complete")
            .setMessage("Your massage session is complete. Stop device?")
            .setPositiveButton("Stop") { _, _ ->
                // Send ASSISTANT_STOP command
                sendBlePacket(Cmd.ASSISTANT_STOP)
                sliderIntensity.value = 0f
                showToast("Device stopped")
            }
            .setNegativeButton("Keep On", null)
            .show()
    }
}