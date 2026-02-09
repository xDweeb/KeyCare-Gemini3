package com.keycare.ime;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "keycare_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private LinearLayout btnTrySetup, btnSettingsCard;
    private Button btnEnableKeyboard, btnSetDefault;
    private TextView lblKeyboardStatus, lblStatusMessage;
    private View statusDot;

    private SettingsManager settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if onboarding needs to be shown
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            // First launch - show onboarding
            Intent intent = new Intent(this, OnboardingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_home);

        settings = new SettingsManager(this);
        setupViews();
        setupListeners();
    }

    private void setupViews() {
        btnTrySetup = findViewById(R.id.btnTrySetup);
        btnSettingsCard = findViewById(R.id.btnSettingsCard);
        btnEnableKeyboard = findViewById(R.id.btnEnableKeyboard);
        btnSetDefault = findViewById(R.id.btnSetDefault);
        lblKeyboardStatus = findViewById(R.id.lblKeyboardStatus);
        lblStatusMessage = findViewById(R.id.lblStatusMessage);
        statusDot = findViewById(R.id.statusDot);
    }

    private void setupListeners() {
        // Try Your Setup card
        btnTrySetup.setOnClickListener(v -> {
            startActivity(new Intent(this, TrySetupActivity.class));
        });

        // Settings card
        btnSettingsCard.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Enable keyboard button
        btnEnableKeyboard.setOnClickListener(v -> {
            openInputMethodSettings();
        });

        // Set as default button
        btnSetDefault.setOnClickListener(v -> {
            openInputMethodPicker();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKeyboardStatus();
    }

    private void updateKeyboardStatus() {
        boolean isEnabled = isKeyboardEnabled();
        boolean isDefault = isKeyboardDefault();

        if (isEnabled && isDefault) {
            // Fully configured
            statusDot.setBackgroundResource(R.drawable.dot_status_green);
            lblKeyboardStatus.setText("Ready to Use");
            lblStatusMessage.setText("KeyCare keyboard is enabled and set as default");
            btnEnableKeyboard.setText("✓ Enabled");
            btnEnableKeyboard.setEnabled(false);
            btnSetDefault.setText("✓ Default");
            btnSetDefault.setEnabled(false);
        } else if (isEnabled) {
            // Enabled but not default
            statusDot.setBackgroundResource(R.drawable.dot_status_yellow);
            lblKeyboardStatus.setText("Almost Ready");
            lblStatusMessage.setText("Keyboard enabled. Tap 'Set Default' to use KeyCare");
            btnEnableKeyboard.setText("✓ Enabled");
            btnEnableKeyboard.setEnabled(false);
            btnSetDefault.setText("Set Default");
            btnSetDefault.setEnabled(true);
        } else {
            // Not enabled
            statusDot.setBackgroundResource(R.drawable.dot_status_red);
            lblKeyboardStatus.setText("Setup Required");
            lblStatusMessage.setText("Enable KeyCare keyboard in system settings");
            btnEnableKeyboard.setText("Enable");
            btnEnableKeyboard.setEnabled(true);
            btnSetDefault.setText("Set Default");
            btnSetDefault.setEnabled(true);
        }
    }

    private boolean isKeyboardEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        List<InputMethodInfo> enabledMethods = imm.getEnabledInputMethodList();
        
        for (InputMethodInfo info : enabledMethods) {
            if (info.getPackageName().equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isKeyboardDefault() {
        String defaultIME = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.DEFAULT_INPUT_METHOD
        );
        
        if (defaultIME != null) {
            ComponentName cn = ComponentName.unflattenFromString(defaultIME);
            if (cn != null && cn.getPackageName().equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private void openInputMethodSettings() {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openInputMethodPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showInputMethodPicker();
    }
}
