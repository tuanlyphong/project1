package com.example.myapplication.ui.main

import android.widget.ImageButton
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.models.MassageSettings
import com.example.myapplication.data.api.BackendRepository  // ✅ FIXED: Changed from network.ApiClient
import com.example.myapplication.ui.assistant.AssistantActivity
import com.example.myapplication.ui.history.HistoryActivity  // ✅ FIXED: Added import
import com.example.myapplication.ui.profile.ProfileActivity // ✅ FIXED: Added import
import com.example.myapplication.utils.PermissionHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val backendRepo = BackendRepository()  // ✅ FIXED: Added repository

    // UI Components - Connection
    private lateinit var btnConnect: MaterialButton
    private lateinit var deviceInfoLayout: LinearLayout
    private lateinit var imgDevice: ImageView
    private lateinit var txtDeviceName: MaterialTextView
    private lateinit var txtBattery: MaterialTextView

    private lateinit var btnHistory: ImageButton
    private lateinit var btnProfile: ImageButton

    // UI Components - Control
    private lateinit var sliderIntensity: Slider
    private lateinit var txtLevel: MaterialTextView
    private lateinit var btnRotate: MaterialButton
    private lateinit var btnHeat: MaterialButton
    private lateinit var btnAssistant: MaterialButton
    private lateinit var sliderVolume: Slider
    private lateinit var txtVolume: MaterialTextView
    private lateinit var btnMute: MaterialButton

    // Timer
    private var massageTimer: CountDownTimer? = null

    // Session tracking
    private var sessionStartTime: Long = 0

    // Assistant Launcher
    private val assistantLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val level = data.getIntExtra("level", 3)
                    val heat = data.getBooleanExtra("heat", false)
                    val duration = data.getIntExtra("duration", 15)

                    handleAssistantSettings(level, heat, duration)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        observeViewModel()

        // Check permissions
        if (!PermissionHelper.hasAllPermissions(this)) {
            PermissionHelper.requestPermissions(this)
        }
    }

    private fun initViews() {
        // Connection
        btnConnect = findViewById(R.id.btnConnect)
        deviceInfoLayout = findViewById(R.id.deviceInfoLayout)
        imgDevice = findViewById(R.id.imgDevice)
        txtDeviceName = findViewById(R.id.txtDeviceName)
        txtBattery = findViewById(R.id.txtBattery)

        btnHistory = findViewById(R.id.btnHistory)
        btnProfile = findViewById(R.id.btnProfile)

        // Control
        sliderIntensity = findViewById(R.id.sliderIntensity)
        txtLevel = findViewById(R.id.txtLevel)
        btnRotate = findViewById(R.id.btnRotate)
        btnHeat = findViewById(R.id.btnHeat)
        btnAssistant = findViewById(R.id.btnAssistant)
        sliderVolume = findViewById(R.id.sliderVolume)
        txtVolume = findViewById(R.id.txtVolume)
        btnMute = findViewById(R.id.btnMute)
    }

    private fun setupListeners() {
        // Connection
        btnConnect.setOnClickListener {
            if (viewModel.uiState.value.isConnected) {
                viewModel.disconnect()
            } else {
                if (PermissionHelper.hasAllPermissions(this)) {
                    viewModel.connect()
                } else {
                    Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Navigation buttons
        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Control
        sliderIntensity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                txtLevel.text = "Level: ${value.toInt()}"
                viewModel.setIntensityLevel(value.toInt())
            }
        }

        btnRotate.setOnClickListener {
            viewModel.toggleRotate()
        }

        btnHeat.setOnClickListener {
            viewModel.toggleHeat()
        }

        btnAssistant.setOnClickListener {
            val intent = Intent(this, AssistantActivity::class.java)
            assistantLauncher.launch(intent)
        }

        sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                txtVolume.text = "Volume: ${value.toInt()}%"
                viewModel.setVolume(value.toInt())
            }
        }

        btnMute.setOnClickListener {
            viewModel.toggleMute()
        }
    }

    private fun observeViewModel() {
        // UI State
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateConnectionUI(state)
            }
        }

        // Massage Settings
        lifecycleScope.launch {
            viewModel.massageSettings.collect { settings ->
                sliderIntensity.value = settings.level.toFloat()
                txtLevel.text = "Level: ${settings.level}"
                sliderVolume.value = settings.volume.toFloat()
                txtVolume.text = "Volume: ${settings.volume}%"
                btnMute.text = if (settings.isMuted) "🔇 Unmute" else "🔊 Mute"

                // Update rotate button state
                btnRotate.text = if (settings.isRotating) "🔄 Rotating" else "🔄 Rotate"
                btnRotate.backgroundTintList = ContextCompat.getColorStateList(
                    this@MainActivity,
                    if (settings.isRotating) R.color.purple_700 else R.color.purple_500
                )

                // Update heat button state
                btnHeat.text = if (settings.useHeat) "🔥 Heat ON" else "🔥 Heat"
                btnHeat.backgroundTintList = ContextCompat.getColorStateList(
                    this@MainActivity,
                    if (settings.useHeat) android.R.color.holo_red_dark else R.color.purple_500
                )
            }
        }

        // Toast Messages
        lifecycleScope.launch {
            viewModel.toastMessage.collect { message ->
                message?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.toastShown()
                }
            }
        }
    }

    private fun updateConnectionUI(state: MainUiState) {
        if (state.isConnected && state.servicesDiscovered) {
            btnConnect.text = "Connected ✓"
            btnConnect.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            deviceInfoLayout.visibility = View.VISIBLE
            sliderIntensity.isEnabled = true
            btnRotate.isEnabled = true
            btnHeat.isEnabled = true
            btnAssistant.isEnabled = true
            sliderVolume.isEnabled = true
            btnMute.isEnabled = true
            txtDeviceName.text = "Massage Pro X1"
            txtBattery.text = "Battery: 85%"
            imgDevice.setImageResource(R.drawable.ic_launcher_foreground)
        } else {
            btnConnect.text = if (state.isConnecting) "Connecting..." else "Connect Device"
            btnConnect.backgroundTintList = ContextCompat.getColorStateList(this, R.color.purple_500)
            deviceInfoLayout.visibility = View.GONE
            sliderIntensity.isEnabled = false
            btnRotate.isEnabled = false
            btnHeat.isEnabled = false
            btnAssistant.isEnabled = false
            sliderVolume.isEnabled = false
            btnMute.isEnabled = false
        }
    }

    private fun handleAssistantSettings(level: Int, heat: Boolean, duration: Int) {
        viewModel.applyAssistantSettings(level, heat, duration)

        // Track session start time
        sessionStartTime = System.currentTimeMillis()

        val message = "AI Settings Applied!\nLevel: $level\nHeat: ${if (heat) "ON" else "OFF"}\nDuration: $duration min"

        AlertDialog.Builder(this)
            .setTitle("🤖 Assistant Activated")
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
                supportActionBar?.subtitle = "⏱️ Timer: %02d:%02d".format(mins, secs)
            }

            override fun onFinish() {
                supportActionBar?.subtitle = null
                showSessionCompleteDialog()
            }
        }.start()
    }

    private fun showSessionCompleteDialog() {
        val currentSettings = viewModel.massageSettings.value

        AlertDialog.Builder(this)
            .setTitle("✅ Session Complete")
            .setMessage("Your massage session is complete. Stop device?")
            .setPositiveButton("Stop") { _, _ ->
                viewModel.stopAssistant()
                saveSession(currentSettings)
                Toast.makeText(this, "Device stopped", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Keep On", null)
            .show()
    }

    // ✅ FIXED: Updated to use BackendRepository instead of ApiClient
    private fun saveSession(settings: MassageSettings) {
        lifecycleScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    Log.w("MainActivity", "User not logged in, skipping save")
                    return@launch
                }

                val endTime = System.currentTimeMillis()
                val durationMinutes = ((endTime - sessionStartTime).toDouble() / 60000.0).toInt()
                val calories = durationMinutes * settings.level * 8

                // ✅ FIXED: Use BackendRepository.saveMassageSession()
                val result = backendRepo.saveMassageSession(
                    level = settings.level,
                    duration = durationMinutes,
                    heatEnabled = settings.useHeat,
                    rotateEnabled = settings.isRotating,
                    startedAt = sessionStartTime,
                    endedAt = endTime,
                    caloriesBurned = calories,
                    notes = ""
                )

                result.onSuccess {
                    Toast.makeText(this@MainActivity, "✅ Session saved!", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Log.e("MainActivity", "Save failed: ${error.message}")
                    Toast.makeText(this@MainActivity, "Failed to save session", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Save error", e)
                Toast.makeText(this@MainActivity, "Error saving session: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.handlePermissionResult(
            requestCode,
            grantResults,
            onGranted = {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            },
            onDenied = {
                Toast.makeText(this, "Permissions required for Bluetooth", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        massageTimer?.cancel()
    }
}
