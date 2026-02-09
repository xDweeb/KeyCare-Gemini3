package com.keycare.ime;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity for adjusting keyboard scale with live preview.
 * 
 * Features:
 * - Slider from 75% to 130%
 * - Live preview of keyboard at current scale
 * - Preset buttons (Small, Normal, Large)
 * - Reset, Cancel, Apply buttons
 * - Test input field
 * 
 * The scale factor affects:
 * - Key height and width (via row heights)
 * - Key font sizes
 * - Margins and padding
 */
public class KeyboardScaleActivity extends AppCompatActivity {

    private KeyboardScaleManager scaleManager;
    private float originalScale;  // Store original to allow cancel
    private float currentScale;   // Current preview scale

    // Views
    private ImageButton btnBack;
    private TextView btnReset;
    private EditText editTestInput;
    private TextView lblScaleValue;
    private SeekBar seekScale;
    private Button btnPresetSmall, btnPresetNormal, btnPresetLarge;
    private Button btnCancel, btnConfirm;
    
    // Preview keyboard views
    private View keyboardPreviewContainer;
    private LinearLayout previewRoot;
    private LinearLayout previewNumberRow, previewRow1, previewRow2, previewRow3, previewRow4;

    // Preset scale values
    private static final int PRESET_SMALL = 80;   // 80%
    private static final int PRESET_NORMAL = 100; // 100%
    private static final int PRESET_LARGE = 120;  // 120%

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyboard_scale);

        scaleManager = new KeyboardScaleManager(this);
        originalScale = scaleManager.getScaleFactor();
        currentScale = originalScale;

        setupViews();
        setupListeners();
        updateUI();
    }

    private void setupViews() {
        btnBack = findViewById(R.id.btnBack);
        btnReset = findViewById(R.id.btnReset);
        editTestInput = findViewById(R.id.editTestInput);
        lblScaleValue = findViewById(R.id.lblScaleValue);
        seekScale = findViewById(R.id.seekScale);
        btnPresetSmall = findViewById(R.id.btnPresetSmall);
        btnPresetNormal = findViewById(R.id.btnPresetNormal);
        btnPresetLarge = findViewById(R.id.btnPresetLarge);
        btnCancel = findViewById(R.id.btnCancel);
        btnConfirm = findViewById(R.id.btnConfirm);
        
        // Preview views
        keyboardPreviewContainer = findViewById(R.id.keyboardPreviewContainer);
        previewRoot = findViewById(R.id.previewRoot);
        previewNumberRow = findViewById(R.id.previewNumberRow);
        previewRow1 = findViewById(R.id.previewRow1);
        previewRow2 = findViewById(R.id.previewRow2);
        previewRow3 = findViewById(R.id.previewRow3);
        previewRow4 = findViewById(R.id.previewRow4);

        // Set initial slider position
        seekScale.setProgress(Math.round(currentScale * 100));
    }

    private void setupListeners() {
        // Back button
        btnBack.setOnClickListener(v -> {
            // Cancel and restore original
            finish();
        });

        // Reset button
        btnReset.setOnClickListener(v -> {
            setScale(KeyboardScaleManager.DEFAULT_SCALE);
            Toast.makeText(this, "Reset to 100%", Toast.LENGTH_SHORT).show();
        });

        // Scale slider
        seekScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setScale(progress / 100f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Preset buttons
        btnPresetSmall.setOnClickListener(v -> setScale(PRESET_SMALL / 100f));
        btnPresetNormal.setOnClickListener(v -> setScale(PRESET_NORMAL / 100f));
        btnPresetLarge.setOnClickListener(v -> setScale(PRESET_LARGE / 100f));

        // Cancel button
        btnCancel.setOnClickListener(v -> {
            // Discard changes
            finish();
        });

        // Confirm button
        btnConfirm.setOnClickListener(v -> {
            // Save the current scale
            scaleManager.setScaleFactor(currentScale);
            scaleManager.saveSettings();
            Toast.makeText(this, "Scale saved: " + Math.round(currentScale * 100) + "%", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void setScale(float scale) {
        // Clamp to valid range
        currentScale = Math.max(KeyboardScaleManager.MIN_SCALE, 
                               Math.min(KeyboardScaleManager.MAX_SCALE, scale));
        
        // Update slider without triggering listener
        seekScale.setProgress(Math.round(currentScale * 100));
        
        // Update UI
        updateUI();
    }

    private void updateUI() {
        // Update scale label
        int percent = Math.round(currentScale * 100);
        lblScaleValue.setText(percent + "%");

        // Update preview keyboard with scaled dimensions
        applyScaleToPreview();
        
        // Highlight active preset button
        updatePresetButtons();
    }

    /**
     * Apply the current scale factor to the preview keyboard.
     * This scales row heights, font sizes, margins, and padding.
     */
    private void applyScaleToPreview() {
        if (previewRoot == null) return;

        float density = getResources().getDisplayMetrics().density;

        // Base dimensions (at scale 1.0)
        float baseKeyHeight = 48f;
        float baseNumberRowHeight = 44f;
        float baseKeyMargin = 2f;
        float basePadding = 4f;
        float baseKeyFontSize = 18f;
        float baseNumberFontSize = 16f;
        float baseSpecialFontSize = 13f;
        float baseSpacebarFontSize = 12f;

        // Scaled dimensions
        int scaledKeyHeight = Math.round(baseKeyHeight * currentScale * density);
        int scaledNumberRowHeight = Math.round(baseNumberRowHeight * currentScale * density);
        int scaledKeyMargin = Math.round(baseKeyMargin * currentScale * density);
        int scaledPadding = Math.round(basePadding * currentScale * density);
        float scaledKeyFontSize = baseKeyFontSize * currentScale;
        float scaledNumberFontSize = baseNumberFontSize * currentScale;
        float scaledSpecialFontSize = baseSpecialFontSize * currentScale;
        float scaledSpacebarFontSize = baseSpacebarFontSize * currentScale;

        // Apply padding to root
        previewRoot.setPadding(scaledPadding, scaledPadding, scaledPadding, scaledPadding * 2);

        // Apply to number row
        if (previewNumberRow != null) {
            ViewGroup.LayoutParams params = previewNumberRow.getLayoutParams();
            params.height = scaledNumberRowHeight;
            previewNumberRow.setLayoutParams(params);
            applyFontSizeToRow(previewNumberRow, scaledNumberFontSize, scaledKeyMargin);
        }

        // Apply to letter rows
        if (previewRow1 != null) {
            ViewGroup.LayoutParams params = previewRow1.getLayoutParams();
            params.height = scaledKeyHeight;
            previewRow1.setLayoutParams(params);
            applyFontSizeToRow(previewRow1, scaledKeyFontSize, scaledKeyMargin);
        }

        if (previewRow2 != null) {
            ViewGroup.LayoutParams params = previewRow2.getLayoutParams();
            params.height = scaledKeyHeight;
            previewRow2.setLayoutParams(params);
            // Also scale the horizontal padding for row 2
            int scaledRowPadding = Math.round(14f * currentScale * density);
            previewRow2.setPadding(scaledRowPadding, 0, scaledRowPadding, 0);
            applyFontSizeToRow(previewRow2, scaledKeyFontSize, scaledKeyMargin);
        }

        if (previewRow3 != null) {
            ViewGroup.LayoutParams params = previewRow3.getLayoutParams();
            params.height = scaledKeyHeight;
            previewRow3.setLayoutParams(params);
            applyFontSizeToRow(previewRow3, scaledKeyFontSize, scaledKeyMargin);
        }

        if (previewRow4 != null) {
            ViewGroup.LayoutParams params = previewRow4.getLayoutParams();
            params.height = scaledKeyHeight;
            previewRow4.setLayoutParams(params);
            applyFontSizeToRow(previewRow4, scaledSpecialFontSize, scaledKeyMargin);
            
            // Special handling for spacebar font size
            TextView spaceBar = findViewById(R.id.previewSpace);
            if (spaceBar != null) {
                spaceBar.setTextSize(scaledSpacebarFontSize);
            }
        }
    }

    /**
     * Apply font size and margins to all children in a row
     */
    private void applyFontSizeToRow(ViewGroup row, float fontSize, int margin) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextSize(fontSize);
            }
            
            // Apply margins
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            params.setMargins(margin, margin, margin, margin);
            child.setLayoutParams(params);
        }
    }

    /**
     * Highlight the active preset button
     */
    private void updatePresetButtons() {
        int percent = Math.round(currentScale * 100);

        // Reset all buttons to default style
        btnPresetSmall.setBackgroundResource(R.drawable.bg_key_special_premium);
        btnPresetNormal.setBackgroundResource(R.drawable.bg_key_special_premium);
        btnPresetLarge.setBackgroundResource(R.drawable.bg_key_special_premium);
        btnPresetSmall.setTextColor(0xFFFFFFFF);
        btnPresetNormal.setTextColor(0xFFFFFFFF);
        btnPresetLarge.setTextColor(0xFFFFFFFF);

        // Highlight active preset
        if (percent == PRESET_SMALL) {
            btnPresetSmall.setBackgroundResource(R.drawable.bg_cta_premium);
            btnPresetSmall.setTextColor(0xFF0B0F14);
        } else if (percent == PRESET_NORMAL) {
            btnPresetNormal.setBackgroundResource(R.drawable.bg_cta_premium);
            btnPresetNormal.setTextColor(0xFF0B0F14);
        } else if (percent == PRESET_LARGE) {
            btnPresetLarge.setBackgroundResource(R.drawable.bg_cta_premium);
            btnPresetLarge.setTextColor(0xFF0B0F14);
        }
    }

    @Override
    public void onBackPressed() {
        // Treat back as cancel
        finish();
    }
}
