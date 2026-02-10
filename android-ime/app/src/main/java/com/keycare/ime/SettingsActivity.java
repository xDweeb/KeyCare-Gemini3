package com.keycare.ime;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Settings Activity - Clean user-facing settings.
 * Includes Gemini mediation preferences (tone, language).
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsManager settings;
    private KeyboardScaleManager scaleManager;
    private AiStatusManager aiStatusManager;

    // Views
    private Button btnManualResize;
    private TextView lblSizeValue;
    private Switch switchSound, switchVibration, switchSafetyBar;
    private ImageButton btnBack;
    private TextView btnReset;
    
    // AI Status views
    private View aiStatusDot;
    private TextView aiStatusText;
    
    // Mediation preference views
    private TextView btnTone;
    private TextView btnLangHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settings = new SettingsManager(this);
        scaleManager = new KeyboardScaleManager(this);
        aiStatusManager = new AiStatusManager();
        
        setupViews();
        loadSettings();
        setupListeners();
        setupAiStatusMonitor();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSizeLabel();
        aiStatusManager.startMonitoring();
        aiStatusManager.checkNow(); // Immediate check
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        aiStatusManager.stopMonitoring();
    }

    private void setupViews() {
        btnBack = findViewById(R.id.btnBack);
        btnReset = findViewById(R.id.btnReset);
        btnManualResize = findViewById(R.id.btnManualResize);
        lblSizeValue = findViewById(R.id.lblSizeValue);
        switchSound = findViewById(R.id.switchSound);
        switchVibration = findViewById(R.id.switchVibration);
        switchSafetyBar = findViewById(R.id.switchSafetyBar);
        
        // AI Status views
        aiStatusDot = findViewById(R.id.aiStatusDot);
        aiStatusText = findViewById(R.id.aiStatusText);
        
        // Mediation preference views
        btnTone = findViewById(R.id.btnTone);
        btnLangHint = findViewById(R.id.btnLangHint);
    }

    private void loadSettings() {
        updateSizeLabel();
        switchSound.setChecked(settings.isSoundEnabled());
        switchVibration.setChecked(settings.isVibrationEnabled());
        switchSafetyBar.setChecked(settings.isSafetyBarAlways());
        
        // Load mediation preferences
        updateToneButton();
        updateLangHintButton();
    }
    
    private void updateToneButton() {
        if (btnTone != null) {
            btnTone.setText(settings.getToneDisplayName() + " ▼");
        }
    }
    
    private void updateLangHintButton() {
        if (btnLangHint != null) {
            btnLangHint.setText(settings.getLangHintDisplayName() + " ▼");
        }
    }

    private void updateSizeLabel() {
        int percentage = scaleManager.getScalePercent();
        lblSizeValue.setText("Scale: " + percentage + "%");
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnReset.setOnClickListener(v -> {
            settings.resetToDefaults();
            scaleManager.resetToDefault();
            loadSettings();
            Toast.makeText(this, "Settings reset", Toast.LENGTH_SHORT).show();
        });

        btnManualResize.setOnClickListener(v -> {
            Intent intent = new Intent(this, KeyboardScaleActivity.class);
            startActivity(intent);
        });

        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setSoundEnabled(isChecked);
        });

        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setVibrationEnabled(isChecked);
        });

        switchSafetyBar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setSafetyBarAlways(isChecked);
        });
        
        // Tone selection
        if (btnTone != null) {
            btnTone.setOnClickListener(v -> showToneSelector());
        }
        
        // Language hint selection
        if (btnLangHint != null) {
            btnLangHint.setOnClickListener(v -> showLangHintSelector());
        }
    }
    
    private void showToneSelector() {
        String[] tones = {"Calm", "Friendly", "Professional"};
        String[] toneValues = {"calm", "friendly", "professional"};
        
        int currentIndex = 0;
        String currentTone = settings.getMediationTone();
        for (int i = 0; i < toneValues.length; i++) {
            if (toneValues[i].equals(currentTone)) {
                currentIndex = i;
                break;
            }
        }
        
        new AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Select Rewrite Tone")
            .setSingleChoiceItems(tones, currentIndex, (dialog, which) -> {
                settings.setMediationTone(toneValues[which]);
                updateToneButton();
                Toast.makeText(this, "Tone: " + tones[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showLangHintSelector() {
        String[] langs = {"Auto-detect", "English", "French", "Arabic", "Darija"};
        String[] langValues = {"auto", "en", "fr", "ar", "darija"};
        
        int currentIndex = 0;
        String currentLang = settings.getMediationLangHint();
        for (int i = 0; i < langValues.length; i++) {
            if (langValues[i].equals(currentLang)) {
                currentIndex = i;
                break;
            }
        }
        
        new AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Select Language Hint")
            .setSingleChoiceItems(langs, currentIndex, (dialog, which) -> {
                settings.setMediationLangHint(langValues[which]);
                updateLangHintButton();
                Toast.makeText(this, "Language: " + langs[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void setupAiStatusMonitor() {
        aiStatusManager.setStatusListener(status -> {
            runOnUiThread(() -> updateAiStatusUI(status));
        });
    }
    
    private void updateAiStatusUI(AiStatusManager.Status status) {
        if (aiStatusDot == null || aiStatusText == null) return;
        
        switch (status) {
            case ONLINE:
                aiStatusDot.setBackgroundResource(R.drawable.bg_status_online);
                aiStatusText.setText("AI Online");
                aiStatusText.setTextColor(0xFF00E5C4); // Green
                break;
            case OFFLINE:
                aiStatusDot.setBackgroundResource(R.drawable.bg_status_offline);
                aiStatusText.setText("AI Offline");
                aiStatusText.setTextColor(0xFFFF5252); // Red
                break;
            case CHECKING:
                aiStatusDot.setBackgroundResource(R.drawable.bg_status_checking);
                aiStatusText.setText("Checking...");
                aiStatusText.setTextColor(0xFF9AA4AF); // Grey
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (aiStatusManager != null) {
            aiStatusManager.shutdown();
        }
    }
}
