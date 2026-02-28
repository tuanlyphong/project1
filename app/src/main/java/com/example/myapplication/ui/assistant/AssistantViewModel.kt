package com.example.myapplication.ui.assistant

import androidx.lifecycle.ViewModel
import com.example.myapplication.data.models.MassageRecommendation
import com.example.myapplication.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AssistantViewModel : ViewModel() {
    
    private val _recommendation = MutableStateFlow<MassageRecommendation?>(null)
    val recommendation: StateFlow<MassageRecommendation?> = _recommendation
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    fun generateRecommendation(
        age: Int,
        weight: Float,
        height: Float,
        conditions: List<String>,
        goal: String
    ) {
        _isLoading.value = true
        
        // Simulate AI processing delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val result = calculateRecommendation(age, weight, height, conditions, goal)
            _recommendation.value = result
            _isLoading.value = false
        }, 1500)
    }
    
    private fun calculateRecommendation(
        age: Int,
        weight: Float,
        height: Float,
        conditions: List<String>,
        goal: String
    ): MassageRecommendation {
        var level = 3 // Default medium
        var duration = 15
        var heat = false
        var mode = "Balanced Massage"
        val explanation = StringBuilder()
        
        // Calculate BMI
        val bmi = weight / ((height / 100) * (height / 100))
        
        // Age-based adjustments
        when {
            age < 25 -> {
                level = 4
                duration = 15
                explanation.append("Young age: higher intensity recommended. ")
            }
            age < 40 -> {
                level = 3
                duration = 15
                explanation.append("Prime age: moderate intensity for balance. ")
            }
            age < 60 -> {
                level = 2
                duration = 20
                heat = true
                explanation.append("Middle age: gentle with heat therapy. ")
            }
            else -> {
                level = 1
                duration = 15
                heat = true
                explanation.append("Senior: very gentle massage recommended. ")
            }
        }
        
        // BMI adjustments
        if (bmi > 25) {
            duration += 5
            explanation.append("Higher BMI: longer duration beneficial. ")
        }
        
        // Condition-based adjustments
        if (conditions.contains("back_pain") || conditions.contains("neck_pain")) {
            heat = true
            if (level > 3) level = 3
            explanation.append("Pain conditions: heat therapy + moderate intensity. ")
            mode = "Pain Relief Mode"
        }
        
        if (conditions.contains("stress") || conditions.contains("insomnia")) {
            level = minOf(level, 2)
            duration = maxOf(duration, 20)
            heat = true
            explanation.append("Stress/insomnia: gentle, long session with heat. ")
            mode = "Relaxation Mode"
        }
        
        if (conditions.contains("fatigue")) {
            level = maxOf(level, 3)
            explanation.append("Fatigue: moderate intensity to boost circulation. ")
        }
        
        // Goal-based adjustments
        when (goal) {
            "pain_relief" -> {
                heat = true
                level = minOf(level, 3)
                mode = "Therapeutic Mode"
            }
            "recovery" -> {
                level = maxOf(level, 3)
                duration = 20
                mode = "Recovery Mode"
                explanation.append("Recovery goal: longer, intense session. ")
            }
            "energy" -> {
                level = maxOf(level, 4)
                duration = 10
                heat = false
                mode = "Energy Boost Mode"
                explanation.append("Energy boost: short, intense session. ")
            }
        }
        
        // Safety limits
        level = level.coerceIn(Constants.MIN_LEVEL, Constants.MAX_LEVEL)
        duration = duration.coerceIn(Constants.MIN_DURATION, Constants.MAX_DURATION)
        
        return MassageRecommendation(
            level = level,
            duration = duration,
            useHeat = heat,
            mode = mode,
            explanation = explanation.toString()
        )
    }
    
    fun getPreset(presetType: PresetType): MassageRecommendation {
        return when (presetType) {
            PresetType.MORNING_ENERGY -> MassageRecommendation(
                level = 4,
                duration = 10,
                useHeat = false,
                mode = "Morning Energy",
                explanation = "Quick energizing massage to start your day"
            )
            PresetType.RELAXATION -> MassageRecommendation(
                level = 2,
                duration = 20,
                useHeat = true,
                mode = "Evening Relaxation",
                explanation = "Gentle massage with heat for deep relaxation"
            )
            PresetType.DEEP_TISSUE -> MassageRecommendation(
                level = 5,
                duration = 15,
                useHeat = true,
                mode = "Deep Tissue",
                explanation = "Intense massage for muscle recovery"
            )
        }
    }
    
    enum class PresetType {
        MORNING_ENERGY,
        RELAXATION,
        DEEP_TISSUE
    }
}
