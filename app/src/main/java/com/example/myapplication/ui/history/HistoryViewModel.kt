package com.example.myapplication.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.api.BackendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryViewModel : ViewModel() {

    private val backendRepo = BackendRepository()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState

    private val _statisticsState = MutableStateFlow<StatisticsState>(StatisticsState.Idle)
    val statisticsState: StateFlow<StatisticsState> = _statisticsState

    // ISO 8601 timestamp format
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

    // ── Load sessions ──────────────────────────────────────────────────────────

    fun loadSessions() {
        viewModelScope.launch {
            _sessionState.value = SessionState.Loading
            try {
                val result = backendRepo.getMassageSessions(limit = 50)

                result.onSuccess { sessions ->
                    val items = sessions.map { dto ->
                        MassageSessionItem(
                            sessionId = dto.sessionId,
                            level = dto.level,
                            duration = dto.duration,
                            heatEnabled = dto.heatEnabled,
                            rotateEnabled = dto.rotateEnabled,
                            caloriesBurned = dto.caloriesBurned,
                            startedAt = parseTimestamp(dto.startedAt),
                            notes = dto.notes
                        )
                    }
                    _sessionState.value = SessionState.Success(items)
                }.onFailure { error ->
                    _sessionState.value = SessionState.Error("Không thể tải lịch sử: ${error.message}")
                }
            } catch (e: Exception) {
                _sessionState.value = SessionState.Error(e.message ?: "Đã có lỗi xảy ra")
            }
        }
    }

    // ── Load statistics ────────────────────────────────────────────────────────

    fun loadStatistics() {
        viewModelScope.launch {
            _statisticsState.value = StatisticsState.Loading
            try {
                val result = backendRepo.getSessionStatistics(days = 30)

                result.onSuccess { stats ->
                    _statisticsState.value = StatisticsState.Success(
                        SessionStatistics(
                            totalSessions = stats.totalSessions,
                            totalMinutes = stats.totalMinutes,
                            totalCalories = stats.totalCalories,
                            avgLevel = stats.avgLevel.toDouble()
                        )
                    )
                }.onFailure { error ->
                    _statisticsState.value = StatisticsState.Error("Không thể tải thống kê: ${error.message}")
                }
            } catch (e: Exception) {
                _statisticsState.value = StatisticsState.Error(e.message ?: "Đã có lỗi xảy ra")
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun parseTimestamp(timestamp: String): Long {
        return try {
            isoFormat.parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}