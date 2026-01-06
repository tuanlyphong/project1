package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import java.util.ArrayList;
import java.util.List;

public class AssistantActivity extends AppCompatActivity {

    private EditText edtAge, edtWeight, edtHeight;
    private Chip chipBackPain, chipNeckPain, chipStress, chipFatigue, chipInsomnia;
    private Chip chipRelaxation, chipPainRelief, chipRecovery, chipEnergy;
    private MaterialButton btnGetRecommendation, btnApplyRecommendation;
    private LinearLayout layoutLoading;
    private MaterialCardView cardRecommendation;
    private TextView txtRecommendedMode, txtRecommendedLevel, txtRecommendedDuration;
    private TextView txtRecommendedHeat, txtExplanation;

    private MassageRecommendation currentRecommendation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant);

        initViews();
        setupListeners();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        edtAge = findViewById(R.id.edtAge);
        edtWeight = findViewById(R.id.edtWeight);
        edtHeight = findViewById(R.id.edtHeight);

        chipBackPain = findViewById(R.id.chipBackPain);
        chipNeckPain = findViewById(R.id.chipNeckPain);
        chipStress = findViewById(R.id.chipStress);
        chipFatigue = findViewById(R.id.chipFatigue);
        chipInsomnia = findViewById(R.id.chipInsomnia);

        chipRelaxation = findViewById(R.id.chipRelaxation);
        chipPainRelief = findViewById(R.id.chipPainRelief);
        chipRecovery = findViewById(R.id.chipRecovery);
        chipEnergy = findViewById(R.id.chipEnergy);

        btnGetRecommendation = findViewById(R.id.btnGetRecommendation);
        btnApplyRecommendation = findViewById(R.id.btnApplyRecommendation);
        layoutLoading = findViewById(R.id.layoutLoading);
        cardRecommendation = findViewById(R.id.cardRecommendation);

        txtRecommendedMode = findViewById(R.id.txtRecommendedMode);
        txtRecommendedLevel = findViewById(R.id.txtRecommendedLevel);
        txtRecommendedDuration = findViewById(R.id.txtRecommendedDuration);
        txtRecommendedHeat = findViewById(R.id.txtRecommendedHeat);
        txtExplanation = findViewById(R.id.txtExplanation);
    }

    private void setupListeners() {
        btnGetRecommendation.setOnClickListener(v -> getAIRecommendation());
        btnApplyRecommendation.setOnClickListener(v -> applyRecommendation());

        // Quick presets
        findViewById(R.id.cardMorningEnergy).setOnClickListener(v ->
                applyPreset(new MassageRecommendation(4, 10, false, "Morning Energy",
                        "Quick energizing massage to start your day")));

        findViewById(R.id.cardRelaxation).setOnClickListener(v ->
                applyPreset(new MassageRecommendation(2, 20, true, "Evening Relaxation",
                        "Gentle massage with heat for deep relaxation")));

        findViewById(R.id.cardDeepTissue).setOnClickListener(v ->
                applyPreset(new MassageRecommendation(5, 15, true, "Deep Tissue",
                        "Intense massage for muscle recovery")));
    }



    private MassageRecommendation generateRecommendation(int age, float weight, float height,
                                                         List<String> conditions, String goal) {
        int level = 3; // Default medium
        int duration = 15;
        boolean heat = false;
        String mode = "Balanced Massage";
        StringBuilder explanation = new StringBuilder();

        // Calculate BMI
        float bmi = weight / ((height/100) * (height/100));

        // Age-based adjustments
        if (age < 25) {
            level = 4;
            duration = 15;
            explanation.append("Young age: higher intensity recommended. ");
        } else if (age < 40) {
            level = 3;
            duration = 15;
            explanation.append("Prime age: moderate intensity for balance. ");
        } else if (age < 60) {
            level = 2;
            duration = 20;
            heat = true;
            explanation.append("Middle age: gentle with heat therapy. ");
        } else {
            level = 1;
            duration = 15;
            heat = true;
            explanation.append("Senior: very gentle massage recommended. ");
        }

        // BMI adjustments
        if (bmi > 25) {
            duration += 5;
            explanation.append("Higher BMI: longer duration beneficial. ");
        }

        // Condition-based adjustments
        if (conditions.contains("back_pain") || conditions.contains("neck_pain")) {
            heat = true;
            if (level > 3) level = 3;
            explanation.append("Pain conditions: heat therapy + moderate intensity. ");
            mode = "Pain Relief Mode";
        }

        if (conditions.contains("stress") || conditions.contains("insomnia")) {
            level = Math.min(level, 2);
            duration = Math.max(duration, 20);
            heat = true;
            explanation.append("Stress/insomnia: gentle, long session with heat. ");
            mode = "Relaxation Mode";
        }

        if (conditions.contains("fatigue")) {
            level = Math.max(level, 3);
            explanation.append("Fatigue: moderate intensity to boost circulation. ");
        }

        // Goal-based adjustments
        switch (goal) {
            case "pain_relief":
                heat = true;
                level = Math.min(level, 3);
                mode = "Therapeutic Mode";
                break;
            case "recovery":
                level = Math.max(level, 3);
                duration = 20;
                mode = "Recovery Mode";
                explanation.append("Recovery goal: longer, intense session. ");
                break;
            case "energy":
                level = Math.max(level, 4);
                duration = 10;
                heat = false;
                mode = "Energy Boost Mode";
                explanation.append("Energy boost: short, intense session. ");
                break;
        }

        // Safety limits
        level = Math.max(1, Math.min(5, level));
        duration = Math.max(10, Math.min(30, duration));

        return new MassageRecommendation(level, duration, heat, mode, explanation.toString());
    }
    private void getAIRecommendation() {
        // Validate input
        if (edtAge.getText().toString().isEmpty() ||
                edtWeight.getText().toString().isEmpty() ||
                edtHeight.getText().toString().isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        int age = Integer.parseInt(edtAge.getText().toString());
        float weight = Float.parseFloat(edtWeight.getText().toString());
        float height = Float.parseFloat(edtHeight.getText().toString());

        // Get health conditions
        List<String> conditions = new ArrayList<>();
        if (chipBackPain.isChecked()) conditions.add("back_pain");
        if (chipNeckPain.isChecked()) conditions.add("neck_pain");
        if (chipStress.isChecked()) conditions.add("stress");
        if (chipFatigue.isChecked()) conditions.add("fatigue");
        if (chipInsomnia.isChecked()) conditions.add("insomnia");

        // Get goal
        final String goal;
        if (chipPainRelief.isChecked()) {
            goal = "pain_relief";
        } else if (chipRecovery.isChecked()) {
            goal = "recovery";
        } else if (chipEnergy.isChecked()) {
            goal = "energy";
        } else {
            goal = "relaxation";
        }

        // Show loading
        layoutLoading.setVisibility(View.VISIBLE);
        cardRecommendation.setVisibility(View.GONE);

        // Generate recommendation (simulate AI processing)
        new android.os.Handler().postDelayed(() -> {
            // FIXED LINE: Just pass the 'goal' variable calculated above
            currentRecommendation = generateRecommendation(age, weight, height, conditions, goal);

            displayRecommendation(currentRecommendation);
            layoutLoading.setVisibility(View.GONE);
            cardRecommendation.setVisibility(View.VISIBLE);
        }, 1500);
    }

    private void displayRecommendation(MassageRecommendation rec) {
        txtRecommendedMode.setText(rec.mode);
        txtRecommendedLevel.setText("Level " + rec.level + " of 5");
        txtRecommendedDuration.setText(rec.duration + " minutes");
        txtRecommendedHeat.setText(rec.useHeat ? "Recommended ✓" : "Not needed");
        txtRecommendedHeat.setTextColor(getResources().getColor(
                // Use standard Android red
                rec.useHeat ? android.R.color.holo_red_dark : android.R.color.darker_gray));

        txtExplanation.setText(rec.explanation);
    }

    private void applyRecommendation() {
        if (currentRecommendation == null) return;

        // Send recommendation back to MainActivity
        android.content.Intent intent = new android.content.Intent();
        intent.putExtra("level", currentRecommendation.level);
        intent.putExtra("heat", currentRecommendation.useHeat);
        intent.putExtra("duration", currentRecommendation.duration);
        setResult(RESULT_OK, intent);
        finish();

        Toast.makeText(this, "Settings applied!", Toast.LENGTH_SHORT).show();
    }

    private void applyPreset(MassageRecommendation rec) {
        currentRecommendation = rec;
        displayRecommendation(rec);
        cardRecommendation.setVisibility(View.VISIBLE);
    }

    // Data class for recommendation
    private static class MassageRecommendation {
        int level;
        int duration;
        boolean useHeat;
        String mode;
        String explanation;

        MassageRecommendation(int level, int duration, boolean useHeat, String mode, String explanation) {
            this.level = level;
            this.duration = duration;
            this.useHeat = useHeat;
            this.mode = mode;
            this.explanation = explanation;
        }
    }
}