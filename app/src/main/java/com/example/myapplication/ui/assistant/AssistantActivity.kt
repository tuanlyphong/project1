package com.example.myapplication.ui.assistant

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch

class AssistantActivity : AppCompatActivity() {
    
    private val viewModel: AssistantViewModel by viewModels()
    
    // Input Fields
    private lateinit var edtAge: EditText
    private lateinit var edtWeight: EditText
    private lateinit var edtHeight: EditText
    
    // Health Condition Chips
    private lateinit var chipBackPain: Chip
    private lateinit var chipNeckPain: Chip
    private lateinit var chipStress: Chip
    private lateinit var chipFatigue: Chip
    private lateinit var chipInsomnia: Chip
    
    // Goal Chips
    private lateinit var chipRelaxation: Chip
    private lateinit var chipPainRelief: Chip
    private lateinit var chipRecovery: Chip
    private lateinit var chipEnergy: Chip
    
    // Buttons
    private lateinit var btnGetRecommendation: MaterialButton
    private lateinit var btnApplyRecommendation: MaterialButton
    
    // Loading and Result
    private lateinit var layoutLoading: MaterialCardView
    private lateinit var cardRecommendation: MaterialCardView
    
    // Result Display
    private lateinit var txtRecommendedMode: MaterialTextView
    private lateinit var txtRecommendedLevel: MaterialTextView
    private lateinit var txtRecommendedDuration: MaterialTextView
    private lateinit var txtRecommendedHeat: MaterialTextView
    private lateinit var txtExplanation: MaterialTextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistant)
        
        initViews()
        setupListeners()
        observeViewModel()
    }
    
    private fun initViews() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        
        // Input Fields
        edtAge = findViewById(R.id.edtAge)
        edtWeight = findViewById(R.id.edtWeight)
        edtHeight = findViewById(R.id.edtHeight)
        
        // Health Condition Chips
        chipBackPain = findViewById(R.id.chipBackPain)
        chipNeckPain = findViewById(R.id.chipNeckPain)
        chipStress = findViewById(R.id.chipStress)
        chipFatigue = findViewById(R.id.chipFatigue)
        chipInsomnia = findViewById(R.id.chipInsomnia)
        
        // Goal Chips
        chipRelaxation = findViewById(R.id.chipRelaxation)
        chipPainRelief = findViewById(R.id.chipPainRelief)
        chipRecovery = findViewById(R.id.chipRecovery)
        chipEnergy = findViewById(R.id.chipEnergy)
        
        // Buttons
        btnGetRecommendation = findViewById(R.id.btnGetRecommendation)
        btnApplyRecommendation = findViewById(R.id.btnApplyRecommendation)
        
        // Loading and Result
        layoutLoading = findViewById(R.id.layoutLoading)
        cardRecommendation = findViewById(R.id.cardRecommendation)
        
        // Result Display
        txtRecommendedMode = findViewById(R.id.txtRecommendedMode)
        txtRecommendedLevel = findViewById(R.id.txtRecommendedLevel)
        txtRecommendedDuration = findViewById(R.id.txtRecommendedDuration)
        txtRecommendedHeat = findViewById(R.id.txtRecommendedHeat)
        txtExplanation = findViewById(R.id.txtExplanation)
    }
    
    private fun setupListeners() {
        btnGetRecommendation.setOnClickListener {
            generateRecommendation()
        }
        
        btnApplyRecommendation.setOnClickListener {
            applyRecommendation()
        }
        
        // Quick presets
        findViewById<View>(R.id.cardMorningEnergy).setOnClickListener {
            applyPreset(AssistantViewModel.PresetType.MORNING_ENERGY)
        }
        
        findViewById<View>(R.id.cardRelaxation).setOnClickListener {
            applyPreset(AssistantViewModel.PresetType.RELAXATION)
        }
        
        findViewById<View>(R.id.cardDeepTissue).setOnClickListener {
            applyPreset(AssistantViewModel.PresetType.DEEP_TISSUE)
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.recommendation.collect { recommendation ->
                recommendation?.let {
                    displayRecommendation(it)
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                if (isLoading) {
                    layoutLoading.visibility = View.VISIBLE
                    cardRecommendation.visibility = View.GONE
                } else {
                    layoutLoading.visibility = View.GONE
                    if (viewModel.recommendation.value != null) {
                        cardRecommendation.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
    
    private fun generateRecommendation() {
        // Validate input
        if (edtAge.text.toString().isEmpty() ||
            edtWeight.text.toString().isEmpty() ||
            edtHeight.text.toString().isEmpty()
        ) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        val age = edtAge.text.toString().toIntOrNull() ?: 0
        val weight = edtWeight.text.toString().toFloatOrNull() ?: 0f
        val height = edtHeight.text.toString().toFloatOrNull() ?: 0f
        
        if (age <= 0 || weight <= 0 || height <= 0) {
            Toast.makeText(this, "Please enter valid values", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get health conditions
        val conditions = mutableListOf<String>()
        if (chipBackPain.isChecked) conditions.add("back_pain")
        if (chipNeckPain.isChecked) conditions.add("neck_pain")
        if (chipStress.isChecked) conditions.add("stress")
        if (chipFatigue.isChecked) conditions.add("fatigue")
        if (chipInsomnia.isChecked) conditions.add("insomnia")
        
        // Get goal
        val goal = when {
            chipPainRelief.isChecked -> "pain_relief"
            chipRecovery.isChecked -> "recovery"
            chipEnergy.isChecked -> "energy"
            else -> "relaxation"
        }
        
        viewModel.generateRecommendation(age, weight, height, conditions, goal)
    }
    
    private fun applyPreset(presetType: AssistantViewModel.PresetType) {
        val recommendation = viewModel.getPreset(presetType)
        displayRecommendation(recommendation)
        cardRecommendation.visibility = View.VISIBLE
    }
    
    private fun displayRecommendation(recommendation: com.example.myapplication.data.models.MassageRecommendation) {
        txtRecommendedMode.text = recommendation.mode
        txtRecommendedLevel.text = "Level ${recommendation.level} of 5"
        txtRecommendedDuration.text = "${recommendation.duration} minutes"
        txtRecommendedHeat.text = if (recommendation.useHeat) "Recommended ✓" else "Not needed"
        txtRecommendedHeat.setTextColor(
            resources.getColor(
                if (recommendation.useHeat) android.R.color.holo_red_dark 
                else android.R.color.darker_gray,
                null
            )
        )
        txtExplanation.text = recommendation.explanation
    }
    
    private fun applyRecommendation() {
        val recommendation = viewModel.recommendation.value ?: return
        
        // Send recommendation back to MainActivity
        val intent = Intent().apply {
            putExtra("level", recommendation.level)
            putExtra("heat", recommendation.useHeat)
            putExtra("duration", recommendation.duration)
        }
        setResult(RESULT_OK, intent)
        finish()
        
        Toast.makeText(this, "Settings applied!", Toast.LENGTH_SHORT).show()
    }
}
