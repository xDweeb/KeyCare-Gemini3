package com.keycare.ime;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Centralized preferences manager for KeyCare IME settings.
 * All keyboard settings are persisted and accessed through this class.
 */
public class SettingsManager {

    private static final String PREFS_NAME = "keycare_settings";

    // Preference keys
    public static final String PREF_KEYBOARD_SCALE = "keyboard_scale";
    public static final String PREF_SOUND_ENABLED = "sound_enabled";
    public static final String PREF_VIBRATION_ENABLED = "vibration_enabled";
    public static final String PREF_SAFETY_BAR_ALWAYS = "safety_bar_always";
    public static final String PREF_KEYBOARD_ENABLED = "keyboard_enabled";
    public static final String PREF_ONBOARDING_COMPLETE = "onboarding_complete";
    
    // Gemini Mediation preferences
    public static final String PREF_MEDIATION_TONE = "mediation_tone";
    public static final String PREF_MEDIATION_LANG_HINT = "mediation_lang_hint";

    // Default values
    public static final float DEFAULT_KEYBOARD_SCALE = 1.0f;
    public static final float SCALE_SMALL = 0.85f;
    public static final float SCALE_NORMAL = 1.0f;
    public static final float SCALE_LARGE = 1.15f;
    public static final boolean DEFAULT_SOUND_ENABLED = true;
    public static final boolean DEFAULT_VIBRATION_ENABLED = true;
    public static final boolean DEFAULT_SAFETY_BAR_ALWAYS = false;
    
    // Mediation defaults
    public static final String DEFAULT_MEDIATION_TONE = "calm";
    public static final String DEFAULT_MEDIATION_LANG_HINT = "auto";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ==================== KEYBOARD SCALE ====================

    public float getKeyboardScale() {
        return prefs.getFloat(PREF_KEYBOARD_SCALE, DEFAULT_KEYBOARD_SCALE);
    }

    public void setKeyboardScale(float scale) {
        prefs.edit().putFloat(PREF_KEYBOARD_SCALE, scale).apply();
    }

    public String getScaleLabel() {
        float scale = getKeyboardScale();
        if (scale <= SCALE_SMALL) return "Small";
        if (scale >= SCALE_LARGE) return "Large";
        return "Normal";
    }

    public int getScaleIndex() {
        float scale = getKeyboardScale();
        if (scale <= SCALE_SMALL) return 0;
        if (scale >= SCALE_LARGE) return 2;
        return 1;
    }

    public void setScaleFromIndex(int index) {
        switch (index) {
            case 0: setKeyboardScale(SCALE_SMALL); break;
            case 2: setKeyboardScale(SCALE_LARGE); break;
            default: setKeyboardScale(SCALE_NORMAL); break;
        }
    }

    // ==================== SOUND ====================

    public boolean isSoundEnabled() {
        return prefs.getBoolean(PREF_SOUND_ENABLED, DEFAULT_SOUND_ENABLED);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_SOUND_ENABLED, enabled).apply();
    }

    // ==================== VIBRATION ====================

    public boolean isVibrationEnabled() {
        return prefs.getBoolean(PREF_VIBRATION_ENABLED, DEFAULT_VIBRATION_ENABLED);
    }

    public void setVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_VIBRATION_ENABLED, enabled).apply();
    }

    // ==================== SAFETY BAR ====================

    public boolean isSafetyBarAlways() {
        return prefs.getBoolean(PREF_SAFETY_BAR_ALWAYS, DEFAULT_SAFETY_BAR_ALWAYS);
    }

    public void setSafetyBarAlways(boolean always) {
        prefs.edit().putBoolean(PREF_SAFETY_BAR_ALWAYS, always).apply();
    }

    // ==================== KEYBOARD STATUS ====================

    public boolean isKeyboardEnabled() {
        return prefs.getBoolean(PREF_KEYBOARD_ENABLED, false);
    }

    public void setKeyboardEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_KEYBOARD_ENABLED, enabled).apply();
    }

    public boolean isOnboardingComplete() {
        return prefs.getBoolean(PREF_ONBOARDING_COMPLETE, false);
    }

    public void setOnboardingComplete(boolean complete) {
        prefs.edit().putBoolean(PREF_ONBOARDING_COMPLETE, complete).apply();
    }

    // ==================== RESET ====================

    public void resetToDefaults() {
        prefs.edit()
            .putFloat(PREF_KEYBOARD_SCALE, DEFAULT_KEYBOARD_SCALE)
            .putBoolean(PREF_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)
            .putBoolean(PREF_VIBRATION_ENABLED, DEFAULT_VIBRATION_ENABLED)
            .putBoolean(PREF_SAFETY_BAR_ALWAYS, DEFAULT_SAFETY_BAR_ALWAYS)
            .putString(PREF_MEDIATION_TONE, DEFAULT_MEDIATION_TONE)
            .putString(PREF_MEDIATION_LANG_HINT, DEFAULT_MEDIATION_LANG_HINT)
            .apply();
    }
    
    // ==================== GEMINI MEDIATION ====================
    
    /**
     * Get the preferred tone for message rewrites.
     * @return "calm", "friendly", or "professional"
     */
    public String getMediationTone() {
        return prefs.getString(PREF_MEDIATION_TONE, DEFAULT_MEDIATION_TONE);
    }
    
    /**
     * Set the preferred tone for message rewrites.
     * @param tone "calm", "friendly", or "professional"
     */
    public void setMediationTone(String tone) {
        prefs.edit().putString(PREF_MEDIATION_TONE, tone).apply();
    }
    
    /**
     * Get the language hint for mediation.
     * @return "auto", "en", "fr", "ar", or "darija"
     */
    public String getMediationLangHint() {
        return prefs.getString(PREF_MEDIATION_LANG_HINT, DEFAULT_MEDIATION_LANG_HINT);
    }
    
    /**
     * Set the language hint for mediation.
     * @param langHint "auto", "en", "fr", "ar", or "darija"
     */
    public void setMediationLangHint(String langHint) {
        prefs.edit().putString(PREF_MEDIATION_LANG_HINT, langHint).apply();
    }
    
    /**
     * Get tone display name for UI.
     */
    public String getToneDisplayName() {
        String tone = getMediationTone();
        switch (tone) {
            case "friendly": return "Friendly";
            case "professional": return "Professional";
            case "calm":
            default: return "Calm";
        }
    }
    
    /**
     * Get language hint display name for UI.
     */
    public String getLangHintDisplayName() {
        String langHint = getMediationLangHint();
        switch (langHint) {
            case "en": return "English";
            case "fr": return "French";
            case "ar": return "Arabic";
            case "darija": return "Darija";
            case "auto":
            default: return "Auto-detect";
        }
    }
}
