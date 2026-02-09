package com.keycare.ime;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TrySetupActivity extends AppCompatActivity {

    private EditText editTestInput;
    private TextView lblLanguage, lblStatus, lblCharCount;
    private Button btnClear, btnOpenSettings;
    private ImageButton btnBack, btnSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_try_setup);

        setupViews();
        setupListeners();
    }

    private void setupViews() {
        editTestInput = findViewById(R.id.editTestInput);
        lblLanguage = findViewById(R.id.lblLanguage);
        lblStatus = findViewById(R.id.lblStatus);
        lblCharCount = findViewById(R.id.lblCharCount);
        btnClear = findViewById(R.id.btnClear);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        btnBack = findViewById(R.id.btnBack);
        btnSettings = findViewById(R.id.btnSettings);
    }

    private void setupListeners() {
        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Settings button (header)
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Clear button
        btnClear.setOnClickListener(v -> {
            editTestInput.setText("");
            updateCharCount(0);
        });

        // Open settings button
        btnOpenSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Text change listener for character count
        editTestInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateCharCount(s.length());
            }
        });

        // Focus on text input to show keyboard
        editTestInput.requestFocus();
    }

    private void updateCharCount(int count) {
        lblCharCount.setText(count + " chars");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update language label based on shared preferences or default
        // This would ideally sync with the keyboard's current language
        lblLanguage.setText("EN"); // Default
    }
}
