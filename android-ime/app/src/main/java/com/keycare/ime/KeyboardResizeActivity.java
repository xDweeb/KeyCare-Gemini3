package com.keycare.ime;

import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity for manually resizing the keyboard with drag gestures.
 * Similar to Microsoft SwiftKey's resize feature.
 */
public class KeyboardResizeActivity extends AppCompatActivity {

    private KeyboardSizeManager sizeManager;
    private Vibrator vibrator;

    private View resizeHandle;
    private LinearLayout keyboardPreview;
    private TextView sizeIndicator;
    private Button btnReset;
    private Button btnConfirm;

    private float initialY;
    private int initialHeight;
    private boolean isDragging = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make fullscreen with translucent status bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        setContentView(R.layout.keyboard_resize_overlay);

        sizeManager = new KeyboardSizeManager(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        initViews();
        setupListeners();
        updatePreviewHeight();
    }

    private void initViews() {
        resizeHandle = findViewById(R.id.resizeHandleTop);
        keyboardPreview = findViewById(R.id.keyboardPreview);
        sizeIndicator = findViewById(R.id.sizeIndicator);
        btnReset = findViewById(R.id.btnResetSize);
        btnConfirm = findViewById(R.id.btnConfirmSize);

        // Set initial keyboard height
        updatePreviewHeight();
    }

    private void setupListeners() {
        // Resize handle touch listener
        resizeHandle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialY = event.getRawY();
                    initialHeight = sizeManager.getKeyboardHeightPx();
                    isDragging = true;
                    highlightHandle(true);
                    vibrateLight();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        float deltaY = event.getRawY() - initialY;
                        int newHeight = (int) (initialHeight - deltaY);
                        
                        // Clamp to bounds
                        newHeight = Math.max(sizeManager.getMinHeightPx(), 
                                   Math.min(sizeManager.getMaxHeightPx(), newHeight));
                        
                        sizeManager.setHeightFromPx(newHeight);
                        updatePreviewHeight();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    highlightHandle(false);
                    vibrateLight();
                    return true;
            }
            return false;
        });

        // Reset button
        btnReset.setOnClickListener(v -> {
            vibrateLight();
            sizeManager.resetToDefault();
            updatePreviewHeight();
        });

        // Confirm button
        btnConfirm.setOnClickListener(v -> {
            vibrateLight();
            sizeManager.saveSettings();
            finish();
        });

        // Cancel on back press or tap outside
        findViewById(R.id.resizeOverlayRoot).setOnClickListener(v -> {
            // Do nothing - prevent accidental closes
        });
    }

    private void updatePreviewHeight() {
        int height = sizeManager.getKeyboardHeightPx();

        // Update preview layout height
        ViewGroup.LayoutParams params = keyboardPreview.getLayoutParams();
        params.height = height;
        keyboardPreview.setLayoutParams(params);

        // Update size indicator
        int percentage = Math.round(sizeManager.getHeightRatio() * 100);
        sizeIndicator.setText("Height: " + percentage + "%");
    }

    private void highlightHandle(boolean highlight) {
        if (highlight) {
            resizeHandle.setAlpha(1.0f);
            resizeHandle.setScaleX(1.05f);
            resizeHandle.setScaleY(1.1f);
        } else {
            resizeHandle.animate()
                .alpha(0.8f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(150)
                .start();
        }
    }

    private void vibrateLight() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(10);
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Save and exit on back
        sizeManager.saveSettings();
        super.onBackPressed();
    }
}
