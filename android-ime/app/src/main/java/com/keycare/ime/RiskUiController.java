package com.keycare.ime;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * RiskUiController - Manages the risk banner and UI state to prevent duplicates.
 * Features:
 * - Single banner instance (no stacking)
 * - Debounced updates (prevents rapid re-renders)
 * - Smooth fade/slide animations
 * - Cancellation of in-flight updates
 */
public class RiskUiController {

    // Debounce delay in milliseconds
    private static final long DEBOUNCE_DELAY = 300;
    
    // Animation durations
    private static final long ANIM_SHOW_DURATION = 250;
    private static final long ANIM_HIDE_DURATION = 150;

    // Current risk state
    public enum RiskLevel {
        SAFE, RISKY, DANGER
    }

    private RiskLevel currentRiskLevel = RiskLevel.SAFE;
    private boolean isBannerVisible = false;
    private boolean isAnimating = false;

    // Views (set via init)
    private LinearLayout riskBanner;
    private TextView bannerTitle;
    private TextView bannerSubtitle;
    private Button bannerCtaButton;
    private TextView riskBadge;
    private TextView riskScore;
    private Button rewriteButton;

    // Drawable resources
    private int bgBannerWarning;
    private int bgBannerDanger;
    private int pillSafe;
    private int pillRisky;
    private int pillDanger;

    // Debounce handler
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingUpdate;

    // Listener for CTA button clicks
    public interface OnRewriteClickListener {
        void onRewriteClick();
    }
    
    private OnRewriteClickListener rewriteClickListener;

    /**
     * Initialize the controller with views.
     * Call this from onCreateInputView after inflating the layout.
     */
    public void init(LinearLayout riskBanner,
                     TextView bannerTitle,
                     TextView bannerSubtitle,
                     Button bannerCtaButton,
                     TextView riskBadge,
                     TextView riskScore,
                     Button rewriteButton,
                     int bgBannerWarning,
                     int bgBannerDanger,
                     int pillSafe,
                     int pillRisky,
                     int pillDanger) {
        this.riskBanner = riskBanner;
        this.bannerTitle = bannerTitle;
        this.bannerSubtitle = bannerSubtitle;
        this.bannerCtaButton = bannerCtaButton;
        this.riskBadge = riskBadge;
        this.riskScore = riskScore;
        this.rewriteButton = rewriteButton;
        this.bgBannerWarning = bgBannerWarning;
        this.bgBannerDanger = bgBannerDanger;
        this.pillSafe = pillSafe;
        this.pillRisky = pillRisky;
        this.pillDanger = pillDanger;

        // Ensure banner starts hidden
        if (riskBanner != null) {
            riskBanner.setVisibility(View.GONE);
        }
        isBannerVisible = false;
        currentRiskLevel = RiskLevel.SAFE;

        // Setup CTA button click
        if (bannerCtaButton != null) {
            bannerCtaButton.setOnClickListener(v -> {
                if (rewriteClickListener != null) {
                    rewriteClickListener.onRewriteClick();
                }
            });
        }
    }

    /**
     * Set listener for rewrite button clicks
     */
    public void setOnRewriteClickListener(OnRewriteClickListener listener) {
        this.rewriteClickListener = listener;
    }

    /**
     * Update risk state with debouncing.
     * Rapid calls will be coalesced to prevent UI flicker.
     * 
     * @param label The risk label (SAFE, OFFENSIVE, etc.)
     * @param score The risk score (0.0 - 1.0)
     */
    public void updateRisk(String label, double score) {
        // Cancel any pending updates
        cancelPendingUpdate();

        // Schedule debounced update
        pendingUpdate = () -> applyRiskUpdate(label, score);
        debounceHandler.postDelayed(pendingUpdate, DEBOUNCE_DELAY);
    }

    /**
     * Update risk state immediately without debouncing.
     * Use this for final/confirmed states.
     */
    public void updateRiskImmediate(String label, double score) {
        cancelPendingUpdate();
        applyRiskUpdate(label, score);
    }
    
    /**
     * Update risk state with custom "why" explanation from Gemini.
     * Use this for Gemini 3 mediation responses.
     * 
     * @param label The risk label (SAFE, HARMFUL, DANGEROUS)
     * @param score The risk score (0.0 - 1.0)
     * @param why The explanation from Gemini
     */
    public void updateRiskWithExplanation(String label, double score, String why) {
        cancelPendingUpdate();
        applyRiskUpdateWithWhy(label, score, why);
    }
    
    /**
     * Update banner to show mediation result from Gemini.
     * 
     * @param riskLevel "safe", "harmful", or "dangerous"
     * @param why The explanation text
     * @param hasRewrite Whether a rewrite suggestion is available
     */
    public void showMediationResult(String riskLevel, String why, boolean hasRewrite) {
        cancelPendingUpdate();
        
        RiskLevel level;
        double score;
        switch (riskLevel.toLowerCase()) {
            case "dangerous":
                level = RiskLevel.DANGER;
                score = 0.9;
                break;
            case "harmful":
                level = RiskLevel.RISKY;
                score = 0.6;
                break;
            default:
                level = RiskLevel.SAFE;
                score = 0.1;
                break;
        }
        
        // Update score display
        if (riskScore != null) {
            riskScore.setText(String.format("%.2f", score));
        }
        
        // Update badge
        updateBadge(level);
        
        if (level == RiskLevel.SAFE) {
            if (isBannerVisible) {
                animateHideBanner();
            }
        } else {
            // Show banner with custom "why" explanation
            updateBannerContentWithWhy(level, why);
            if (!isBannerVisible) {
                animateShowBanner();
            }
            
            // Update CTA button based on whether rewrite is available
            if (bannerCtaButton != null) {
                bannerCtaButton.setEnabled(hasRewrite);
                bannerCtaButton.setText(hasRewrite ? "Rewrite" : "Fix with AI");
            }
        }
        
        currentRiskLevel = level;
    }

    /**
     * Force hide the banner (e.g., when text is cleared or suggestion applied)
     */
    public void hideBanner() {
        cancelPendingUpdate();
        if (isBannerVisible && !isAnimating) {
            animateHideBanner();
        } else if (riskBanner != null) {
            riskBanner.setVisibility(View.GONE);
            isBannerVisible = false;
        }
        currentRiskLevel = RiskLevel.SAFE;
        updateBadgeForSafe();
    }

    /**
     * Reset to safe state (full reset including badge)
     */
    public void reset() {
        cancelPendingUpdate();
        currentRiskLevel = RiskLevel.SAFE;
        isBannerVisible = false;
        isAnimating = false;
        
        if (riskBanner != null) {
            riskBanner.clearAnimation();
            riskBanner.setVisibility(View.GONE);
        }
        
        updateBadgeForSafe();
        
        if (rewriteButton != null) {
            rewriteButton.setVisibility(View.GONE);
        }
    }

    /**
     * Get current risk level
     */
    public RiskLevel getCurrentRiskLevel() {
        return currentRiskLevel;
    }

    /**
     * Cancel pending debounced updates
     */
    public void cancelPendingUpdate() {
        if (pendingUpdate != null) {
            debounceHandler.removeCallbacks(pendingUpdate);
            pendingUpdate = null;
        }
    }

    // ==================== PRIVATE IMPLEMENTATION ====================

    private void applyRiskUpdate(String label, double score) {
        // Determine risk level
        RiskLevel newLevel;
        if ("SAFE".equals(label)) {
            newLevel = RiskLevel.SAFE;
        } else if ("OFFENSIVE".equals(label) && score >= 0.7) {
            newLevel = RiskLevel.DANGER;
        } else {
            newLevel = RiskLevel.RISKY;
        }

        // Update score display
        if (riskScore != null) {
            riskScore.setText(String.format("%.2f", score));
        }

        // Update badge
        updateBadge(newLevel);

        // Update banner visibility
        if (newLevel == RiskLevel.SAFE) {
            // Hide banner
            if (isBannerVisible) {
                animateHideBanner();
            }
            if (rewriteButton != null) {
                rewriteButton.setVisibility(View.GONE);
            }
        } else {
            // Show/update banner
            updateBannerContent(newLevel);
            if (!isBannerVisible) {
                animateShowBanner();
            }
            if (rewriteButton != null) {
                rewriteButton.setVisibility(View.VISIBLE);
            }
        }

        currentRiskLevel = newLevel;
    }

    private void updateBadge(RiskLevel level) {
        if (riskBadge == null) return;

        switch (level) {
            case SAFE:
                riskBadge.setText("SAFE");
                riskBadge.setTextColor(0xFF00E5C4);
                riskBadge.setBackgroundResource(pillSafe);
                break;
            case RISKY:
                riskBadge.setText("RISKY");
                riskBadge.setTextColor(0xFFFFA726);
                riskBadge.setBackgroundResource(pillRisky);
                break;
            case DANGER:
                riskBadge.setText("DANGER");
                riskBadge.setTextColor(0xFFFF5252);
                riskBadge.setBackgroundResource(pillDanger);
                break;
        }
    }

    private void updateBadgeForSafe() {
        if (riskBadge != null) {
            riskBadge.setText("SAFE");
            riskBadge.setTextColor(0xFF00E5C4);
            riskBadge.setBackgroundResource(pillSafe);
        }
        if (riskScore != null) {
            riskScore.setText("0.00");
        }
    }

    private void updateBannerContent(RiskLevel level) {
        if (riskBanner == null) return;

        if (level == RiskLevel.DANGER) {
            riskBanner.setBackgroundResource(bgBannerDanger);
            if (bannerTitle != null) {
                bannerTitle.setText("🚨 DANGER DETECTED");
                bannerTitle.setTextColor(0xFFFF5252);
            }
            if (bannerSubtitle != null) {
                bannerSubtitle.setText("This message contains potentially harmful content");
            }
        } else {
            riskBanner.setBackgroundResource(bgBannerWarning);
            if (bannerTitle != null) {
                bannerTitle.setText("⚠ RISK DETECTED");
                bannerTitle.setTextColor(0xFFFFA726);
            }
            if (bannerSubtitle != null) {
                bannerSubtitle.setText("This message may be perceived as aggressive");
            }
        }
    }
    
    /**
     * Update banner content with custom "why" explanation from Gemini.
     */
    private void updateBannerContentWithWhy(RiskLevel level, String why) {
        if (riskBanner == null) return;

        if (level == RiskLevel.DANGER) {
            riskBanner.setBackgroundResource(bgBannerDanger);
            if (bannerTitle != null) {
                bannerTitle.setText("🚨 DANGEROUS");
                bannerTitle.setTextColor(0xFFFF5252);
            }
        } else {
            riskBanner.setBackgroundResource(bgBannerWarning);
            if (bannerTitle != null) {
                bannerTitle.setText("⚠ HARMFUL");
                bannerTitle.setTextColor(0xFFFFA726);
            }
        }
        
        // Show the "why" explanation from Gemini
        if (bannerSubtitle != null) {
            if (why != null && !why.isEmpty()) {
                bannerSubtitle.setText(why);
            } else {
                bannerSubtitle.setText(level == RiskLevel.DANGER 
                    ? "This message may cause harm"
                    : "This message could be improved");
            }
        }
    }
    
    /**
     * Apply risk update with custom "why" explanation.
     */
    private void applyRiskUpdateWithWhy(String label, double score, String why) {
        RiskLevel newLevel;
        if ("safe".equalsIgnoreCase(label)) {
            newLevel = RiskLevel.SAFE;
        } else if ("dangerous".equalsIgnoreCase(label)) {
            newLevel = RiskLevel.DANGER;
        } else {
            newLevel = RiskLevel.RISKY;
        }

        if (riskScore != null) {
            riskScore.setText(String.format("%.2f", score));
        }

        updateBadge(newLevel);

        if (newLevel == RiskLevel.SAFE) {
            if (isBannerVisible) {
                animateHideBanner();
            }
            if (rewriteButton != null) {
                rewriteButton.setVisibility(View.GONE);
            }
        } else {
            updateBannerContentWithWhy(newLevel, why);
            if (!isBannerVisible) {
                animateShowBanner();
            }
            if (rewriteButton != null) {
                rewriteButton.setVisibility(View.VISIBLE);
            }
        }

        currentRiskLevel = newLevel;
    }

    private void animateShowBanner() {
        if (riskBanner == null || isAnimating) return;

        isAnimating = true;
        riskBanner.setAlpha(0f);
        riskBanner.setTranslationY(-20f);
        riskBanner.setVisibility(View.VISIBLE);
        
        riskBanner.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ANIM_SHOW_DURATION)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    isAnimating = false;
                    isBannerVisible = true;
                    riskBanner.animate().setListener(null);
                }
            })
            .start();
    }

    private void animateHideBanner() {
        if (riskBanner == null || isAnimating) return;

        isAnimating = true;
        
        riskBanner.animate()
            .alpha(0f)
            .translationY(-20f)
            .setDuration(ANIM_HIDE_DURATION)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    riskBanner.setVisibility(View.GONE);
                    isAnimating = false;
                    isBannerVisible = false;
                    riskBanner.animate().setListener(null);
                }
            })
            .start();
    }

    /**
     * Enable/disable CTA button and update text
     */
    public void setCtaLoading(boolean loading) {
        if (bannerCtaButton != null) {
            bannerCtaButton.setEnabled(!loading);
            bannerCtaButton.setText(loading ? "..." : "Fix with AI");
        }
        if (rewriteButton != null) {
            rewriteButton.setEnabled(!loading);
            rewriteButton.setText(loading ? "..." : "Rewrite");
        }
    }

    /**
     * Reset CTA buttons to default state
     */
    public void resetCtaButtons() {
        if (bannerCtaButton != null) {
            bannerCtaButton.setEnabled(true);
            bannerCtaButton.setText("Fix with AI");
        }
        if (rewriteButton != null) {
            rewriteButton.setEnabled(true);
            rewriteButton.setText("Rewrite");
        }
    }
}
