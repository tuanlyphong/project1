package com.example.myapplication.ui.history

// ─── Domain models ─────────────────────────────────────────────────────────────

data class MassageSessionItem(
    val sessionId: Int,
    val level: Int,
    val duration: Int,            // minutes
    val heatEnabled: Boolean,
    val rotateEnabled: Boolean,
    val caloriesBurned: Int,
    val startedAt: Long,          // epoch millis for SimpleDateFormat
    val notes: String?
)

data class SessionStatistics(
    val totalSessions: Int,
    val totalMinutes: Int,
    val totalCalories: Int,
    val avgLevel: Double
)

// ─── UI States ─────────────────────────────────────────────────────────────────

sealed class SessionState {
    object Idle : SessionState()
    object Loading : SessionState()
    data class Success(val sessions: List<MassageSessionItem>) : SessionState()
    data class Error(val message: String) : SessionState()
}

sealed class StatisticsState {
    object Idle : StatisticsState()
    object Loading : StatisticsState()
    data class Success(val stats: SessionStatistics) : StatisticsState()
    data class Error(val message: String) : StatisticsState()
}
