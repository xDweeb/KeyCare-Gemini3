package com.keycare.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Manages keyboard size persistence and calculations.
 * Supports manual resize with min/max constraints.
 */
public class KeyboardSizeManager {

    private static final String PREFS_NAME = "keycare_keyboard_size";
    private static final String KEY_HEIGHT_RATIO = "keyboard_height_ratio";
    private static final String KEY_PADDING_HORIZONTAL = "keyboard_padding_horizontal";

    // Height constraints as ratio of screen height
    public static final float MIN_HEIGHT_RATIO = 0.25f;  // 25% of screen
    public static final float MAX_HEIGHT_RATIO = 0.55f;  // 55% of screen
    public static final float DEFAULT_HEIGHT_RATIO = 0.38f; // 38% of screen (default)

    // Horizontal padding constraints
    public static final int MIN_PADDING_DP = 0;
    public static final int MAX_PADDING_DP = 40;
    public static final int DEFAULT_PADDING_DP = 0;

    private final Context context;
    private final SharedPreferences prefs;

    private float currentHeightRatio;
    private int currentPaddingDp;

    public KeyboardSizeManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadSettings();
    }

    private void loadSettings() {
        currentHeightRatio = prefs.getFloat(KEY_HEIGHT_RATIO, DEFAULT_HEIGHT_RATIO);
        currentPaddingDp = prefs.getInt(KEY_PADDING_HORIZONTAL, DEFAULT_PADDING_DP);

        // Validate bounds
        currentHeightRatio = clamp(currentHeightRatio, MIN_HEIGHT_RATIO, MAX_HEIGHT_RATIO);
        currentPaddingDp = clamp(currentPaddingDp, MIN_PADDING_DP, MAX_PADDING_DP);
    }

    public void saveSettings() {
        prefs.edit()
            .putFloat(KEY_HEIGHT_RATIO, currentHeightRatio)
            .putInt(KEY_PADDING_HORIZONTAL, currentPaddingDp)
            .apply();
    }

    public void resetToDefault() {
        currentHeightRatio = DEFAULT_HEIGHT_RATIO;
        currentPaddingDp = DEFAULT_PADDING_DP;
        saveSettings();
    }

    /**
     * Get the keyboard height in pixels based on current ratio
     */
    public int getKeyboardHeightPx() {
        int screenHeight = getScreenHeight();
        return (int) (screenHeight * currentHeightRatio);
    }

    /**
     * Get horizontal padding in pixels
     */
    public int getHorizontalPaddingPx() {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (currentPaddingDp * density);
    }

    /**
     * Set keyboard height from pixel value (during drag)
     */
    public void setHeightFromPx(int heightPx) {
        int screenHeight = getScreenHeight();
        float ratio = (float) heightPx / screenHeight;
        currentHeightRatio = clamp(ratio, MIN_HEIGHT_RATIO, MAX_HEIGHT_RATIO);
    }

    /**
     * Set horizontal padding from pixel value (during drag)
     */
    public void setPaddingFromPx(int paddingPx) {
        float density = context.getResources().getDisplayMetrics().density;
        int paddingDp = (int) (paddingPx / density);
        currentPaddingDp = clamp(paddingDp, MIN_PADDING_DP, MAX_PADDING_DP);
    }

    /**
     * Adjust height by delta pixels (during drag)
     */
    public void adjustHeightByPx(int deltaPx) {
        int currentHeight = getKeyboardHeightPx();
        int newHeight = currentHeight - deltaPx; // Negative because drag up = increase
        setHeightFromPx(newHeight);
    }

    public float getHeightRatio() {
        return currentHeightRatio;
    }

    public void setHeightRatio(float ratio) {
        currentHeightRatio = clamp(ratio, MIN_HEIGHT_RATIO, MAX_HEIGHT_RATIO);
    }

    public int getPaddingDp() {
        return currentPaddingDp;
    }

    public void setPaddingDp(int paddingDp) {
        currentPaddingDp = clamp(paddingDp, MIN_PADDING_DP, MAX_PADDING_DP);
    }

    public int getScreenHeight() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels;
    }

    public int getScreenWidth() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        return metrics.widthPixels;
    }

    public int getMinHeightPx() {
        return (int) (getScreenHeight() * MIN_HEIGHT_RATIO);
    }

    public int getMaxHeightPx() {
        return (int) (getScreenHeight() * MAX_HEIGHT_RATIO);
    }

    public int getDefaultHeightPx() {
        return (int) (getScreenHeight() * DEFAULT_HEIGHT_RATIO);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
