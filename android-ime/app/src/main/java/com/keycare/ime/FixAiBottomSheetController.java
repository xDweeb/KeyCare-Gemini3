package com.keycare.ime;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * FixAiBottomSheetController - Manages the Fix with AI bottom sheet UI.
 * Features:
 * - Tone selection (Calm, Respectful, Professional)
 * - Loading state with skeleton
 * - Suggestion cards with selection
 * - Apply/Cancel actions
 */
public class FixAiBottomSheetController {

    public enum Tone {
        CALM("calm"),
        RESPECTFUL("respectful"),
        PROFESSIONAL("professional");

        public final String value;
        Tone(String value) { this.value = value; }
    }

    // Views
    private LinearLayout bottomSheet;
    private LinearLayout loadingContainer;
    private LinearLayout suggestionsContainer;
    private TextView toneCalm, toneRespectful, toneProfessional;
    private LinearLayout suggestion1Card, suggestion2Card, suggestion3Card;
    private TextView suggestion1Text, suggestion2Text, suggestion3Text;
    private TextView suggestion1Reason, suggestion2Reason, suggestion3Reason;
    private Button btnApply, btnCancel, btnCopy;

    // State
    private Tone currentTone = Tone.CALM;
    private int selectedIndex = -1;
    private List<RewriteApiClient.Suggestion> currentSuggestions = new ArrayList<>();

    // Callbacks
    private OnToneChangedListener onToneChangedListener;
    private OnSuggestionSelectedListener onSuggestionSelectedListener;
    private OnApplyClickListener onApplyClickListener;
    private OnCancelClickListener onCancelClickListener;
    private OnCopyClickListener onCopyClickListener;

    // Drawables
    private int bgToneChip;
    private int bgToneChipSelected;
    private int bgSuggestionCard;

    public interface OnToneChangedListener {
        void onToneChanged(Tone tone);
    }

    public interface OnSuggestionSelectedListener {
        void onSuggestionSelected(int index, RewriteApiClient.Suggestion suggestion);
    }

    public interface OnApplyClickListener {
        void onApply(RewriteApiClient.Suggestion suggestion);
    }

    public interface OnCancelClickListener {
        void onCancel();
    }
    
    public interface OnCopyClickListener {
        void onCopy(RewriteApiClient.Suggestion suggestion);
    }

    /**
     * Initialize with root view containing the bottom sheet
     */
    public void init(View rootView) {
        bottomSheet = rootView.findViewById(R.id.fixAiBottomSheet);
        if (bottomSheet == null) return;

        loadingContainer = rootView.findViewById(R.id.fixAiLoading);
        suggestionsContainer = rootView.findViewById(R.id.suggestionsContainer);

        // Tone chips
        toneCalm = rootView.findViewById(R.id.toneCalm);
        toneRespectful = rootView.findViewById(R.id.toneRespectful);
        toneProfessional = rootView.findViewById(R.id.toneProfessional);

        // Suggestion cards
        suggestion1Card = rootView.findViewById(R.id.suggestion1Card);
        suggestion2Card = rootView.findViewById(R.id.suggestion2Card);
        suggestion3Card = rootView.findViewById(R.id.suggestion3Card);

        suggestion1Text = rootView.findViewById(R.id.suggestion1Text);
        suggestion2Text = rootView.findViewById(R.id.suggestion2Text);
        suggestion3Text = rootView.findViewById(R.id.suggestion3Text);

        suggestion1Reason = rootView.findViewById(R.id.suggestion1Reason);
        suggestion2Reason = rootView.findViewById(R.id.suggestion2Reason);
        suggestion3Reason = rootView.findViewById(R.id.suggestion3Reason);

        // Buttons
        btnApply = rootView.findViewById(R.id.btnApplyFixAi);
        btnCancel = rootView.findViewById(R.id.btnCancelFixAi);
        btnCopy = rootView.findViewById(R.id.btnCopyFixAi);

        // Get drawable resources
        bgToneChip = R.drawable.bg_tone_chip;
        bgToneChipSelected = R.drawable.bg_tone_chip_selected;
        bgSuggestionCard = R.drawable.bg_suggestion_card;

        setupClickListeners();
        updateToneUI();
    }

    private void setupClickListeners() {
        // Tone selection
        if (toneCalm != null) {
            toneCalm.setOnClickListener(v -> selectTone(Tone.CALM));
        }
        if (toneRespectful != null) {
            toneRespectful.setOnClickListener(v -> selectTone(Tone.RESPECTFUL));
        }
        if (toneProfessional != null) {
            toneProfessional.setOnClickListener(v -> selectTone(Tone.PROFESSIONAL));
        }

        // Suggestion selection
        if (suggestion1Card != null) {
            suggestion1Card.setOnClickListener(v -> selectSuggestion(0));
        }
        if (suggestion2Card != null) {
            suggestion2Card.setOnClickListener(v -> selectSuggestion(1));
        }
        if (suggestion3Card != null) {
            suggestion3Card.setOnClickListener(v -> selectSuggestion(2));
        }

        // Action buttons
        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                if (selectedIndex >= 0 && selectedIndex < currentSuggestions.size()) {
                    if (onApplyClickListener != null) {
                        onApplyClickListener.onApply(currentSuggestions.get(selectedIndex));
                    }
                }
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                hide();
                if (onCancelClickListener != null) {
                    onCancelClickListener.onCancel();
                }
            });
        }
        
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                if (selectedIndex >= 0 && selectedIndex < currentSuggestions.size()) {
                    if (onCopyClickListener != null) {
                        onCopyClickListener.onCopy(currentSuggestions.get(selectedIndex));
                    }
                }
            });
        }
    }

    private void selectTone(Tone tone) {
        if (currentTone == tone) return;
        currentTone = tone;
        updateToneUI();
        if (onToneChangedListener != null) {
            onToneChangedListener.onToneChanged(tone);
        }
    }

    private void updateToneUI() {
        // Reset all
        setToneChipState(toneCalm, false);
        setToneChipState(toneRespectful, false);
        setToneChipState(toneProfessional, false);

        // Highlight selected
        switch (currentTone) {
            case CALM:
                setToneChipState(toneCalm, true);
                break;
            case RESPECTFUL:
                setToneChipState(toneRespectful, true);
                break;
            case PROFESSIONAL:
                setToneChipState(toneProfessional, true);
                break;
        }
    }

    private void setToneChipState(TextView chip, boolean selected) {
        if (chip == null) return;
        chip.setBackgroundResource(selected ? bgToneChipSelected : bgToneChip);
        chip.setTextColor(selected ? 0xFF00E5C4 : 0xFF9AA4AF);
    }

    private void selectSuggestion(int index) {
        if (index < 0 || index >= currentSuggestions.size()) return;
        
        selectedIndex = index;
        updateSuggestionSelection();
        
        if (onSuggestionSelectedListener != null) {
            onSuggestionSelectedListener.onSuggestionSelected(index, currentSuggestions.get(index));
        }
    }

    private void updateSuggestionSelection() {
        setSuggestionCardSelected(suggestion1Card, selectedIndex == 0);
        setSuggestionCardSelected(suggestion2Card, selectedIndex == 1);
        setSuggestionCardSelected(suggestion3Card, selectedIndex == 2);
    }

    private void setSuggestionCardSelected(View card, boolean selected) {
        if (card == null) return;
        card.setSelected(selected);
    }

    /**
     * Show loading state
     */
    public void showLoading() {
        if (bottomSheet == null) return;

        bottomSheet.setVisibility(View.VISIBLE);
        if (loadingContainer != null) loadingContainer.setVisibility(View.VISIBLE);
        if (suggestionsContainer != null) suggestionsContainer.setVisibility(View.GONE);

        // Animate in
        bottomSheet.setTranslationY(bottomSheet.getHeight());
        bottomSheet.animate()
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    /**
     * Show suggestions
     */
    public void showSuggestions(List<RewriteApiClient.Suggestion> suggestions) {
        if (bottomSheet == null) return;

        currentSuggestions = suggestions != null ? suggestions : new ArrayList<>();
        selectedIndex = currentSuggestions.isEmpty() ? -1 : 0; // Auto-select first

        if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
        if (suggestionsContainer != null) suggestionsContainer.setVisibility(View.VISIBLE);

        // Update UI
        updateSuggestionCard(0, suggestion1Card, suggestion1Text, suggestion1Reason);
        updateSuggestionCard(1, suggestion2Card, suggestion2Text, suggestion2Reason);
        updateSuggestionCard(2, suggestion3Card, suggestion3Text, suggestion3Reason);

        updateSuggestionSelection();

        // Show bottom sheet if hidden
        if (bottomSheet.getVisibility() != View.VISIBLE) {
            bottomSheet.setVisibility(View.VISIBLE);
            bottomSheet.setTranslationY(bottomSheet.getHeight());
            bottomSheet.animate()
                    .translationY(0)
                    .setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    private void updateSuggestionCard(int index, LinearLayout card, TextView textView, TextView reasonView) {
        if (card == null) return;

        if (index < currentSuggestions.size()) {
            RewriteApiClient.Suggestion suggestion = currentSuggestions.get(index);
            card.setVisibility(View.VISIBLE);
            if (textView != null) textView.setText(suggestion.text);
            if (reasonView != null) {
                if (suggestion.reason != null && !suggestion.reason.isEmpty()) {
                    reasonView.setText("💡 " + suggestion.reason);
                    reasonView.setVisibility(View.VISIBLE);
                } else {
                    reasonView.setVisibility(View.GONE);
                }
            }
        } else {
            card.setVisibility(View.GONE);
        }
    }

    /**
     * Hide bottom sheet
     */
    public void hide() {
        if (bottomSheet == null || bottomSheet.getVisibility() != View.VISIBLE) return;

        bottomSheet.animate()
                .translationY(bottomSheet.getHeight())
                .setDuration(200)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        bottomSheet.setVisibility(View.GONE);
                        bottomSheet.setTranslationY(0);
                    }
                })
                .start();
    }

    /**
     * Check if visible
     */
    public boolean isVisible() {
        return bottomSheet != null && bottomSheet.getVisibility() == View.VISIBLE;
    }

    /**
     * Get current tone
     */
    public Tone getCurrentTone() {
        return currentTone;
    }

    /**
     * Get selected suggestion
     */
    public RewriteApiClient.Suggestion getSelectedSuggestion() {
        if (selectedIndex >= 0 && selectedIndex < currentSuggestions.size()) {
            return currentSuggestions.get(selectedIndex);
        }
        return null;
    }

    // Setters for listeners
    public void setOnToneChangedListener(OnToneChangedListener listener) {
        this.onToneChangedListener = listener;
    }

    public void setOnSuggestionSelectedListener(OnSuggestionSelectedListener listener) {
        this.onSuggestionSelectedListener = listener;
    }

    public void setOnApplyClickListener(OnApplyClickListener listener) {
        this.onApplyClickListener = listener;
    }

    public void setOnCancelClickListener(OnCancelClickListener listener) {
        this.onCancelClickListener = listener;
    }
    
    public void setOnCopyClickListener(OnCopyClickListener listener) {
        this.onCopyClickListener = listener;
    }
}
