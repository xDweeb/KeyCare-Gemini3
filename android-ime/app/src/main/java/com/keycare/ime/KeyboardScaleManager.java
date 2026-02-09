package com.keycare.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;

/**
 * Manages keyboard SCALE FACTOR for true proportional resizing.
 * 
 * Unlike height-only resizing, this scales:
 * - Key height and width
 * - Key corner radius
 * - Key font size
 * - Row gaps and margins
 * - Icon sizes
 * 
 * Scale factor 1.0 = default (100%)
 * Range: 0.75 (75%) to 1.30 (130%)
 */
public class KeyboardScaleManager {

    private static final String PREFS_NAME = "keycare_keyboard_scale";
    private static final String KEY_SCALE_FACTOR = "scale_factor";

    // Scale factor constraints
    public static final float MIN_SCALE = 0.75f;   // 75% of default
    public static final float MAX_SCALE = 1.30f;   // 130% of default
    public static final float DEFAULT_SCALE = 1.0f; // 100% (normal)

    // Base dimensions in dp (at scale 1.0)
    public static final float BASE_KEY_HEIGHT_DP = 48f;
    public static final float BASE_KEY_WIDTH_DP = 32f;   // Will be calculated based on screen width
    public static final float BASE_NUMBER_ROW_HEIGHT_DP = 44f;
    public static final float BASE_KEY_MARGIN_DP = 2f;
    public static final float BASE_KEY_CORNER_RADIUS_DP = 10f;
    public static final float BASE_KEY_FONT_SIZE_SP = 18f;
    public static final float BASE_NUMBER_FONT_SIZE_SP = 16f;
    public static final float BASE_SPECIAL_KEY_FONT_SIZE_SP = 13f;
    public static final float BASE_SPACEBAR_FONT_SIZE_SP = 12f;
    public static final float BASE_HORIZONTAL_PADDING_DP = 4f;
    public static final float BASE_VERTICAL_PADDING_DP = 8f;
    public static final float BASE_ICON_SIZE_DP = 24f;
    public static final float BASE_TOOLBAR_HEIGHT_DP = 44f;

    private final Context context;
    private final SharedPreferences prefs;
    private float density;

    private float currentScaleFactor;

    public KeyboardScaleManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.density = context.getResources().getDisplayMetrics().density;
        loadSettings();
    }

    private void loadSettings() {
        currentScaleFactor = prefs.getFloat(KEY_SCALE_FACTOR, DEFAULT_SCALE);
        // Validate bounds
        currentScaleFactor = clamp(currentScaleFactor, MIN_SCALE, MAX_SCALE);
    }

    public void saveSettings() {
        prefs.edit()
            .putFloat(KEY_SCALE_FACTOR, currentScaleFactor)
            .apply();
    }

    public void resetToDefault() {
        currentScaleFactor = DEFAULT_SCALE;
        saveSettings();
    }

    // ==================== GETTERS & SETTERS ====================

    public float getScaleFactor() {
        return currentScaleFactor;
    }

    public void setScaleFactor(float scale) {
        currentScaleFactor = clamp(scale, MIN_SCALE, MAX_SCALE);
    }

    /**
     * Get scale factor as percentage (e.g., 100 for 1.0)
     */
    public int getScalePercent() {
        return Math.round(currentScaleFactor * 100);
    }

    /**
     * Set scale factor from percentage (e.g., 100 -> 1.0)
     */
    public void setScalePercent(int percent) {
        setScaleFactor(percent / 100f);
    }

    // ==================== SCALED DIMENSIONS (DP) ====================

    /**
     * Get scaled key height in dp
     */
    public float getScaledKeyHeightDp() {
        return BASE_KEY_HEIGHT_DP * currentScaleFactor;
    }

    /**
     * Get scaled number row height in dp
     */
    public float getScaledNumberRowHeightDp() {
        return BASE_NUMBER_ROW_HEIGHT_DP * currentScaleFactor;
    }

    /**
     * Get scaled key margin in dp
     */
    public float getScaledKeyMarginDp() {
        return BASE_KEY_MARGIN_DP * currentScaleFactor;
    }

    /**
     * Get scaled corner radius in dp
     */
    public float getScaledCornerRadiusDp() {
        return BASE_KEY_CORNER_RADIUS_DP * currentScaleFactor;
    }

    /**
     * Get scaled horizontal padding in dp
     */
    public float getScaledHorizontalPaddingDp() {
        return BASE_HORIZONTAL_PADDING_DP * currentScaleFactor;
    }

    /**
     * Get scaled vertical padding in dp
     */
    public float getScaledVerticalPaddingDp() {
        return BASE_VERTICAL_PADDING_DP * currentScaleFactor;
    }

    /**
     * Get scaled icon size in dp
     */
    public float getScaledIconSizeDp() {
        return BASE_ICON_SIZE_DP * currentScaleFactor;
    }

    /**
     * Get scaled toolbar height in dp
     */
    public float getScaledToolbarHeightDp() {
        return BASE_TOOLBAR_HEIGHT_DP * currentScaleFactor;
    }

    // ==================== SCALED DIMENSIONS (PX) ====================

    /**
     * Get scaled key height in pixels
     */
    public int getScaledKeyHeightPx() {
        return dpToPx(getScaledKeyHeightDp());
    }

    /**
     * Get scaled number row height in pixels
     */
    public int getScaledNumberRowHeightPx() {
        return dpToPx(getScaledNumberRowHeightDp());
    }

    /**
     * Get scaled key margin in pixels
     */
    public int getScaledKeyMarginPx() {
        return dpToPx(getScaledKeyMarginDp());
    }

    /**
     * Get scaled corner radius in pixels
     */
    public int getScaledCornerRadiusPx() {
        return dpToPx(getScaledCornerRadiusDp());
    }

    /**
     * Get scaled horizontal padding in pixels
     */
    public int getScaledHorizontalPaddingPx() {
        return dpToPx(getScaledHorizontalPaddingDp());
    }

    /**
     * Get scaled vertical padding in pixels
     */
    public int getScaledVerticalPaddingPx() {
        return dpToPx(getScaledVerticalPaddingDp());
    }

    /**
     * Get scaled icon size in pixels
     */
    public int getScaledIconSizePx() {
        return dpToPx(getScaledIconSizeDp());
    }

    /**
     * Get scaled toolbar height in pixels
     */
    public int getScaledToolbarHeightPx() {
        return dpToPx(getScaledToolbarHeightDp());
    }

    // ==================== SCALED FONT SIZES (SP) ====================

    /**
     * Get scaled key font size in sp
     */
    public float getScaledKeyFontSizeSp() {
        return BASE_KEY_FONT_SIZE_SP * currentScaleFactor;
    }

    /**
     * Get scaled number row font size in sp
     */
    public float getScaledNumberFontSizeSp() {
        return BASE_NUMBER_FONT_SIZE_SP * currentScaleFactor;
    }

    /**
     * Get scaled special key font size in sp (123, ABC, etc.)
     */
    public float getScaledSpecialKeyFontSizeSp() {
        return BASE_SPECIAL_KEY_FONT_SIZE_SP * currentScaleFactor;
    }

    /**
     * Get scaled spacebar font size in sp
     */
    public float getScaledSpacebarFontSizeSp() {
        return BASE_SPACEBAR_FONT_SIZE_SP * currentScaleFactor;
    }

    // ==================== TOTAL KEYBOARD HEIGHT ====================

    /**
     * Calculate total keyboard height based on:
     * - 5 rows of keys (number row + 4 letter rows)
     * - Scaled key heights
     * - Scaled margins/gaps
     * - Scaled padding
     */
    public int getTotalKeyboardHeightPx() {
        // Number row + 4 letter/symbol rows
        float numberRowHeight = getScaledNumberRowHeightDp();
        float letterRowHeight = getScaledKeyHeightDp();
        float margin = getScaledKeyMarginDp();
        float padding = getScaledVerticalPaddingDp();

        // Total = padding_top + number_row + 4*letter_rows + margins_between + padding_bottom
        // Each row has margin on both sides, so effectively margin*2 per row
        // But LinearLayout handles this with layout_margin="2dp" on each key
        float totalDp = padding + // top padding
                        numberRowHeight + (margin * 2) + // number row with margins
                        (letterRowHeight + margin * 2) * 4 + // 4 letter rows
                        padding; // bottom padding

        return dpToPx(totalDp);
    }

    // ==================== UTILITY ====================

    public int dpToPx(float dp) {
        return Math.round(dp * density);
    }

    public float pxToDp(int px) {
        return px / density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Refresh density (call after configuration change)
     */
    public void refreshDensity() {
        this.density = context.getResources().getDisplayMetrics().density;
    }
}
