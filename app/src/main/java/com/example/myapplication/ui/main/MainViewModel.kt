package com.example.myapplication.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.bluetooth.BleManager
import com.example.myapplication.data.models.MassageSettings
import com.example.myapplication.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager(application)

    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    private val _massageSettings = MutableStateFlow(MassageSettings())
    val massageSettings: StateFlow<MassageSettings> = _massageSettings

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    init {
        setupBleCallbacks()
    }

    private fun setupBleCallbacks() {
        bleManager.onConnectionSuccess = {
            _uiState.value = _uiState.value.copy(
                isConnected = true,
                servicesDiscovered = true,
                isConnecting = false
            )
            showToast("✓ Connected successfully")
        }

        bleManager.onConnectionFailed = { error ->
            _uiState.value = _uiState.value.copy(
                isConnected = false,
                servicesDiscovered = false,
                isConnecting = false
            )
            showToast("✗ Connection failed: $error")
        }

        bleManager.onDisconnected = {
            _uiState.value = _uiState.value.copy(
                isConnected = false,
                servicesDiscovered = false,
                isConnecting = false
            )
            showToast("Disconnected")
        }
    }

    // Connection
    fun connect() {
        if (!_uiState.value.isConnected) {
            _uiState.value = _uiState.value.copy(isConnecting = true)
            bleManager.startScan()
        }
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    // Massage Controls
    fun setIntensityLevel(level: Int) {
        if (!bleManager.isConnected()) {
            showToast("⚠️ Device not connected")
            return
        }

        val clampedLevel = level.coerceIn(Constants.MIN_LEVEL, Constants.MAX_LEVEL)
        _massageSettings.value = _massageSettings.value.copy(level = clampedLevel)
        bleManager.sendCommand(Constants.BleCommands.LEVEL, byteArrayOf(clampedLevel.toByte()))
        showToast("Level set to $clampedLevel")
    }

    fun toggleRotate() {
        if (!bleManager.isConnected()) {
            showToast("⚠️ Device not connected")
            return
        }

        val newRotating = !_massageSettings.value.isRotating
        _massageSettings.value = _massageSettings.value.copy(isRotating = newRotating)
        bleManager.sendCommand(Constants.BleCommands.ROTATE)
        showToast(if (newRotating) "🔄 Rotation ON" else "🔄 Rotation OFF")
    }

    fun toggleHeat() {
        if (!bleManager.isConnected()) {
            showToast("⚠️ Device not connected")
            return
        }

        val newHeat = !_massageSettings.value.useHeat
        _massageSettings.value = _massageSettings.value.copy(useHeat = newHeat)
        bleManager.sendCommand(Constants.BleCommands.HEAT)
        showToast(if (newHeat) "🔥 Heat ON" else "🔥 Heat OFF")
    }

    // Audio Controls
    fun setVolume(volume: Int) {
        if (!bleManager.isConnected()) {
            showToast("⚠️ Device not connected")
            return
        }

        val clampedVolume = volume.coerceIn(0, 100)
        _massageSettings.value = _massageSettings.value.copy(
            volume = clampedVolume,
            isMuted = clampedVolume == 0
        )
        bleManager.sendCommand(Constants.BleCommands.AUDIO_VOLUME, byteArrayOf(clampedVolume.toByte()))
    }

    fun toggleMute() {
        if (!bleManager.isConnected()) {
            showToast("⚠️ Device not connected")
            return
        }

        val newMuted = !_massageSettings.value.isMuted
        _massageSettings.value = _massageSettings.value.copy(isMuted = newMuted)
        bleManager.sendCommand(Constants.BleCommands.AUDIO_MUTE)

        showToast(if (newMuted) "🔇 Muted" else "🔊 Unmuted")
    }

    // Assistant Settings
    fun applyAssistantSettings(level: Int, heat: Boolean, duration: Int) {
        if (!bleManager.isConnected()) {
            showToast("⚠️ Device not connected")
            return
        }

        val durationHigh = (duration shr 8).toByte()
        val durationLow = (duration and 0xFF).toByte()
        val heatByte: Byte = if (heat) 1 else 0

        val payload = byteArrayOf(
            level.toByte(),
            heatByte,
            durationHigh,
            durationLow
        )

        bleManager.sendCommand(Constants.BleCommands.ASSISTANT_CONFIG, payload)

        _massageSettings.value = _massageSettings.value.copy(
            level = level,
            useHeat = heat,
            duration = duration
        )

        showToast("🤖 AI settings applied")
    }

    fun stopAssistant() {
        if (!bleManager.isConnected()) return
        bleManager.sendCommand(Constants.BleCommands.ASSISTANT_STOP)
        _massageSettings.value = _massageSettings.value.copy(level = 0)
        showToast("⏹️ Assistant stopped")
    }

    // UI Helpers
    private fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun toastShown() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}

data class MainUiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val servicesDiscovered: Boolean = false
)