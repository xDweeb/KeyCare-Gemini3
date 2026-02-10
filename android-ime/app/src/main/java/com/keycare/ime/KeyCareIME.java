package com.keycare.ime;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Gemini 3 Mediation API imports
import com.keycare.ime.api.MediateRequest;
import com.keycare.ime.api.MediateResponse;
import com.keycare.ime.api.MediationApiClient;

public class KeyCareIME extends InputMethodService {

    private static final String PREFS_NAME = "keycare_prefs";
    private static final String TAG = "KeyCareIME";
    private static final long DEBOUNCE_DELAY = 800;  // 800ms debounce for Gemini mediation
    private static final long BACKSPACE_REPEAT_DELAY = 50;

    // Keyboard modes
    private static final int MODE_ALPHA = 0;
    private static final int MODE_NUM = 1;
    private static final int MODE_SYM = 2;
    private int currentMode = MODE_ALPHA;

    // Settings
    private SettingsManager settings;
    private KeyboardScaleManager scaleManager;
    private AudioManager audioManager;
    private Vibrator vibrator;

    // Views
    private View keyboardView;
    private LinearLayout keyboardContainer;
    private LinearLayout alphaContainer, numContainer, symContainer;
    private LinearLayout statusBar;
    private ImageButton btnSettingsToolbar;
    private TextView riskBadge, riskScore;
    private Button /* REMOVED: rewriteButton - using bannerCtaButton only */ unusedRewriteButton;
    private LinearLayout rewritePanel, riskBanner, clipboardPanel, emojiPanel;
    private TextView suggestionCalm, suggestionFirm, suggestionEducational;
    private Button closeRewritePanel, bannerCtaButton;
    private TextView bannerTitle, bannerSubtitle;
    private TextView clipboardContent;
    private Button btnPasteClipboard, closeClipboardPanel;
    private ImageButton btnEmoji, btnClipboard, btnMic;
    private Button keySpace;

    // State
    private StringBuilder currentText = new StringBuilder();
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Handler backspaceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    private Runnable backspaceRunnable;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private boolean isShiftOn = false;
    private boolean isBackspacePressed = false;
    private String lastCalm = "";
    private String lastFirm = "";
    private String lastEducational = "";

    // Risk UI Controller - prevents duplicate banners
    private RiskUiController riskUiController;
    
    // Suggestion Engine - generates varied suggestions
    private SuggestionEngine suggestionEngine;
    
    // Rewrite API Client - handles API calls with fallback
    private RewriteApiClient rewriteApiClient;
    
    // Gemini 3 Mediation API Client
    private MediationApiClient mediationApiClient;
    
    // Current mediation response (for rewrite button)
    private MediateResponse currentMediationResponse;
    
    // Fix AI Bottom Sheet Controller
    private FixAiBottomSheetController fixAiController;
    
    // Track in-flight API calls for cancellation
    private Future<?> currentDetectTask;
    private Future<?> currentRewriteTask;

    // Language support: EN (QWERTY), FR (AZERTY), AR (Arabic)
    private static final int LANG_EN = 0;
    private static final int LANG_FR = 1;
    private static final int LANG_AR = 2;
    private int currentLanguage = LANG_EN;
    
    // Shift and Caps Lock state
    private boolean isCapsLock = false;
    private long lastShiftTapTime = 0;
    private static final long DOUBLE_TAP_THRESHOLD = 400; // ms

    // QWERTY layout
    private static final String[][] LAYOUT_EN = {
        {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
        {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
        {"z", "x", "c", "v", "b", "n", "m"}
    };

    // AZERTY layout
    private static final String[][] LAYOUT_FR = {
        {"a", "z", "e", "r", "t", "y", "u", "i", "o", "p"},
        {"q", "s", "d", "f", "g", "h", "j", "k", "l", "m"},
        {"w", "x", "c", "v", "b", "n"}
    };

    // Complete Arabic layout with all letters
    // Row 1: ض ص ث ق ف غ ع ه خ ح ج د (12 keys, but we use 10 in row 1, 2 go to other rows)
    private static final String[][] LAYOUT_AR = {
        {"ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح"},
        {"ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك"},
        {"ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز"}
    };
    
    // Arabic long-press alternatives
    private static final java.util.Map<String, String[]> ARABIC_LONG_PRESS = new java.util.HashMap<String, String[]>() {{
        put("ا", new String[]{"أ", "إ", "آ", "ٱ"});
        put("ي", new String[]{"ى", "ئ"});
        put("و", new String[]{"ؤ"});
        put("ه", new String[]{"ة"});
        put("ل", new String[]{"لا", "لأ", "لإ", "لآ"});
        put("ك", new String[]{"گ"});
        put("ت", new String[]{"ث"});
        put("ح", new String[]{"خ", "ج"});
        put("ع", new String[]{"غ"});
        put("س", new String[]{"ش"});
        put("ص", new String[]{"ض"});
        put("ط", new String[]{"ظ"});
        put("د", new String[]{"ذ"});
        put("ر", new String[]{"ز"});
        put("ن", new String[]{"ں"});
    }};
    
    // Additional Arabic keys for full coverage
    private static final String[] ARABIC_EXTRA = {"ج", "د", "ذ", "ط", "ظ"};

    // Emoji set (80 common emojis)
    private static final String[] EMOJIS = {
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😊",
        "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😋", "😛", "😜",
        "🤪", "😝", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑",
        "😶", "😏", "😒", "🙄", "😬", "😮", "😯", "😲", "😳", "🥺",
        "😢", "😭", "😤", "😠", "😡", "🤬", "😈", "👿", "💀", "☠️",
        "👍", "👎", "👊", "✊", "🤛", "🤜", "👏", "🙌", "👐", "🤲",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "❣️",
        "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "✨", "🔥"
    };

    private String currentLabel = "SAFE";
    private double currentScore = 0.0;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize settings and system services
        settings = new SettingsManager(this);
        scaleManager = new KeyboardScaleManager(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        // Initialize Risk UI Controller and Suggestion Engine
        riskUiController = new RiskUiController();
        suggestionEngine = new SuggestionEngine();
        
        // Initialize Rewrite API Client with fallback engine (uses ApiConfig.BASE_URL internally)
        rewriteApiClient = new RewriteApiClient(suggestionEngine);
        
        // Initialize Gemini 3 Mediation API Client
        mediationApiClient = new MediationApiClient();
        
        // Initialize Fix AI Bottom Sheet Controller
        fixAiController = new FixAiBottomSheetController();
    }

    @Override
    public View onCreateInputView() {
        keyboardView = getLayoutInflater().inflate(R.layout.keyboard_view, null);
        setupViews();
        setupKeyListeners();
        setupToolbarButtons();
        buildEmojiPanel();
        applyLanguageLayout();
        applyKeyboardScale();
        initRiskUiController();
        initFixAiBottomSheet();
        return keyboardView;
    }
    
    /**
     * Initialize the Fix AI Bottom Sheet with callbacks.
     */
    private void initFixAiBottomSheet() {
        if (fixAiController == null) {
            fixAiController = new FixAiBottomSheetController();
        }
        
        fixAiController.init(keyboardView);
        
        // When tone changes, fetch new suggestions
        fixAiController.setOnToneChangedListener(tone -> {
            if (currentText.length() > 0) {
                fetchFixAiSuggestions(currentText.toString(), tone.value);
            }
        });
        
        // When apply is clicked, replace text
        fixAiController.setOnApplyClickListener(suggestion -> {
            if (suggestion != null) {
                applyFixAiSuggestion(suggestion.text);
            }
        });
        
        // When copy is clicked, copy to clipboard
        fixAiController.setOnCopyClickListener(suggestion -> {
            if (suggestion != null) {
                copyToClipboard(suggestion.text);
            }
        });
        
        // When cancel is clicked
        fixAiController.setOnCancelClickListener(() -> {
            // Just hide, already handled by controller
        });
    }
    
    /**
     * Initialize the RiskUiController with views from the layout.
     * This ensures only one banner instance exists and prevents duplicates.
     */
    private void initRiskUiController() {
        if (riskUiController == null) {
            riskUiController = new RiskUiController();
        }
        
        riskUiController.init(
            riskBanner,
            bannerTitle,
            bannerSubtitle,
            bannerCtaButton,
            riskBadge,
            riskScore,
            null, // REMOVED: rewriteButton - using bannerCtaButton only
            R.drawable.bg_banner_warning_premium,
            R.drawable.bg_banner_danger_premium,
            R.drawable.pill_safe_premium,
            R.drawable.pill_risky_premium,
            R.drawable.pill_danger_premium
        );
        
        // Set listener for rewrite button in banner - opens Fix AI bottom sheet
        riskUiController.setOnRewriteClickListener(() -> {
            if (currentText.length() > 0) {
                openFixAiBottomSheet();
            }
        });
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        // Re-apply settings each time keyboard opens (settings may have changed)
        if (settings == null) {
            settings = new SettingsManager(this);
        }
        if (scaleManager == null) {
            scaleManager = new KeyboardScaleManager(this);
        } else {
            // Reload scale settings in case user changed them
            scaleManager = new KeyboardScaleManager(this);
        }
        applyKeyboardScale();
    }

    /**
     * Apply full proportional scaling to the keyboard.
     * This scales:
     * - Row heights (key height)
     * - Font sizes
     * - Margins and padding
     * - Toolbar height
     * 
     * The keyboard total height is derived from the scaled row heights,
     * NOT from a fixed container height.
     */
    private void applyKeyboardScale() {
        if (keyboardContainer == null || keyboardView == null) return;
        
        // Reload scale manager to get latest settings
        if (scaleManager == null) {
            scaleManager = new KeyboardScaleManager(this);
        }
        
        float scaleFactor = scaleManager.getScaleFactor();
        float density = getResources().getDisplayMetrics().density;
        
        // Base dimensions in dp
        float baseKeyHeight = 48f;
        float baseNumberRowHeight = 44f;
        float baseKeyMargin = 2f;
        float basePaddingH = 4f;
        float basePaddingV = 8f;
        float baseRow2Padding = 14f;
        
        // Base font sizes in sp
        float baseKeyFontSize = 18f;
        float baseNumberFontSize = 16f;
        float baseSpecialFontSize = 13f;
        float baseSpacebarFontSize = 12f;
        
        // Calculate scaled values
        int scaledKeyHeight = Math.round(baseKeyHeight * scaleFactor * density);
        int scaledNumberRowHeight = Math.round(baseNumberRowHeight * scaleFactor * density);
        int scaledKeyMargin = Math.round(baseKeyMargin * scaleFactor * density);
        int scaledPaddingH = Math.round(basePaddingH * scaleFactor * density);
        int scaledPaddingV = Math.round(basePaddingV * scaleFactor * density);
        int scaledRow2Padding = Math.round(baseRow2Padding * scaleFactor * density);
        float scaledKeyFontSize = baseKeyFontSize * scaleFactor;
        float scaledNumberFontSize = baseNumberFontSize * scaleFactor;
        float scaledSpecialFontSize = baseSpecialFontSize * scaleFactor;
        float scaledSpacebarFontSize = baseSpacebarFontSize * scaleFactor;
        
        // Apply padding to keyboard container
        keyboardContainer.setPadding(scaledPaddingH, scaledPaddingV / 2, scaledPaddingH, scaledPaddingV);
        
        // Apply scaling to alpha container
        if (alphaContainer != null) {
            scaleAlphaContainer(alphaContainer, scaledKeyHeight, scaledNumberRowHeight, 
                               scaledKeyMargin, scaledRow2Padding,
                               scaledKeyFontSize, scaledNumberFontSize, scaledSpecialFontSize, scaledSpacebarFontSize);
        }
        
        // Apply scaling to num container
        if (numContainer != null) {
            scaleNumericContainer(numContainer, scaledKeyHeight, scaledKeyMargin,
                                  scaledKeyFontSize, scaledSpecialFontSize, scaledSpacebarFontSize);
        }
        
        // Apply scaling to sym container
        if (symContainer != null) {
            scaleSymbolContainer(symContainer, scaledKeyHeight, scaledKeyMargin,
                                 scaledKeyFontSize, scaledSpecialFontSize, scaledSpacebarFontSize);
        }
        
        // Let the container height be wrap_content so it adapts to scaled row heights
        ViewGroup.LayoutParams params = keyboardContainer.getLayoutParams();
        if (params != null) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            keyboardContainer.setLayoutParams(params);
        }
    }
    
    /**
     * Scale the alpha (letters) container rows
     */
    private void scaleAlphaContainer(ViewGroup container, int keyHeight, int numberRowHeight,
                                      int margin, int row2Padding,
                                      float keyFontSize, float numberFontSize, 
                                      float specialFontSize, float spacebarFontSize) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                ViewGroup.LayoutParams rowParams = row.getLayoutParams();
                
                // Determine row type and set height
                if (i == 0) {
                    // Number row
                    rowParams.height = numberRowHeight;
                    scaleRowKeys(row, numberFontSize, margin);
                } else if (i == 2) {
                    // Row 2 (A-L) has special padding
                    rowParams.height = keyHeight;
                    row.setPadding(row2Padding, 0, row2Padding, 0);
                    scaleRowKeys(row, keyFontSize, margin);
                } else if (i == 4) {
                    // Bottom row (123, space, enter)
                    rowParams.height = keyHeight;
                    scaleBottomRow(row, specialFontSize, spacebarFontSize, margin);
                } else {
                    // Regular letter rows
                    rowParams.height = keyHeight;
                    scaleRowKeys(row, keyFontSize, margin);
                }
                row.setLayoutParams(rowParams);
            }
        }
    }
    
    /**
     * Scale the numeric container rows
     */
    private void scaleNumericContainer(ViewGroup container, int keyHeight, int margin,
                                        float keyFontSize, float specialFontSize, float spacebarFontSize) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                ViewGroup.LayoutParams rowParams = row.getLayoutParams();
                rowParams.height = keyHeight;
                row.setLayoutParams(rowParams);
                
                if (i == container.getChildCount() - 1) {
                    // Bottom row
                    scaleBottomRow(row, specialFontSize, spacebarFontSize, margin);
                } else {
                    scaleRowKeys(row, keyFontSize, margin);
                }
            }
        }
    }
    
    /**
     * Scale the symbol container rows
     */
    private void scaleSymbolContainer(ViewGroup container, int keyHeight, int margin,
                                       float keyFontSize, float specialFontSize, float spacebarFontSize) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                ViewGroup.LayoutParams rowParams = row.getLayoutParams();
                rowParams.height = keyHeight;
                row.setLayoutParams(rowParams);
                
                if (i == container.getChildCount() - 1) {
                    // Bottom row
                    scaleBottomRow(row, specialFontSize, spacebarFontSize, margin);
                } else {
                    scaleRowKeys(row, keyFontSize, margin);
                }
            }
        }
    }
    
    /**
     * Scale font size and margins for all keys in a row
     */
    private void scaleRowKeys(ViewGroup row, float fontSize, int margin) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            
            // Apply font size to buttons/textviews
            if (child instanceof Button) {
                ((Button) child).setTextSize(fontSize);
            } else if (child instanceof TextView) {
                ((TextView) child).setTextSize(fontSize);
            }
            // ImageButtons don't have text size
            
            // Apply margins
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            if (params != null) {
                params.setMargins(margin, margin, margin, margin);
                child.setLayoutParams(params);
            }
        }
    }
    
    /**
     * Scale the bottom row (123, space, enter) with different font sizes
     */
    private void scaleBottomRow(ViewGroup row, float specialFontSize, float spacebarFontSize, int margin) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            
            // Apply margins
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            if (params != null) {
                params.setMargins(margin, margin, margin, margin);
                child.setLayoutParams(params);
            }
            
            // Apply font size - spacebar gets smaller font, special keys get special size
            if (child instanceof Button) {
                Button btn = (Button) child;
                String text = btn.getText().toString();
                
                if (text.contains("KeyCare") || text.contains("•")) {
                    // Spacebar
                    btn.setTextSize(spacebarFontSize);
                } else if (text.equals("123") || text.equals("ABC") || text.equals("#+=")) {
                    // Special keys
                    btn.setTextSize(specialFontSize);
                } else {
                    // Punctuation keys
                    btn.setTextSize(specialFontSize + 2);
                }
            }
        }
    }

    private void setupViews() {
        keyboardContainer = keyboardView.findViewById(R.id.keyboardContainer);
        alphaContainer = keyboardView.findViewById(R.id.alphaContainer);
        numContainer = keyboardView.findViewById(R.id.numContainer);
        symContainer = keyboardView.findViewById(R.id.symContainer);
        statusBar = keyboardView.findViewById(R.id.statusBar);
        riskBadge = keyboardView.findViewById(R.id.riskBadge);
        riskScore = keyboardView.findViewById(R.id.riskScore);
        // REMOVED: rewriteButton - using bannerCtaButton ("Fix with AI") only
        rewritePanel = keyboardView.findViewById(R.id.rewritePanel);
        riskBanner = keyboardView.findViewById(R.id.riskBanner);
        clipboardPanel = keyboardView.findViewById(R.id.clipboardPanel);
        emojiPanel = keyboardView.findViewById(R.id.emojiPanel);

        suggestionCalm = keyboardView.findViewById(R.id.suggestionCalm);
        suggestionFirm = keyboardView.findViewById(R.id.suggestionFirm);
        suggestionEducational = keyboardView.findViewById(R.id.suggestionEducational);
        closeRewritePanel = keyboardView.findViewById(R.id.closeRewritePanel);

        bannerTitle = keyboardView.findViewById(R.id.bannerTitle);
        bannerSubtitle = keyboardView.findViewById(R.id.bannerSubtitle);
        bannerCtaButton = keyboardView.findViewById(R.id.bannerCtaButton);

        clipboardContent = keyboardView.findViewById(R.id.clipboardContent);
        btnPasteClipboard = keyboardView.findViewById(R.id.btnPasteClipboard);
        closeClipboardPanel = keyboardView.findViewById(R.id.closeClipboardPanel);

        btnEmoji = keyboardView.findViewById(R.id.btnEmoji);
        btnClipboard = keyboardView.findViewById(R.id.btnClipboard);
        btnMic = keyboardView.findViewById(R.id.btnMic);
        keySpace = keyboardView.findViewById(R.id.key_space);

        // REMOVED: rewriteButton click handler - using bannerCtaButton only

        // Banner CTA button - Opens Fix AI Bottom Sheet
        if (bannerCtaButton != null) {
            bannerCtaButton.setOnClickListener(v -> {
                if (currentText.length() > 0) {
                    animateButtonPress(v);
                    openFixAiBottomSheet();
                }
            });
        }

        // Close panel
        if (closeRewritePanel != null) {
            closeRewritePanel.setOnClickListener(v -> hideRewritePanel());
        }

        // Suggestion clicks - replace current text
        if (suggestionCalm != null) {
            suggestionCalm.setOnClickListener(v -> applySuggestion(lastCalm));
            suggestionCalm.setOnLongClickListener(v -> { copyToClipboard(lastCalm); return true; });
        }
        if (suggestionFirm != null) {
            suggestionFirm.setOnClickListener(v -> applySuggestion(lastFirm));
            suggestionFirm.setOnLongClickListener(v -> { copyToClipboard(lastFirm); return true; });
        }
        if (suggestionEducational != null) {
            suggestionEducational.setOnClickListener(v -> applySuggestion(lastEducational));
            suggestionEducational.setOnLongClickListener(v -> { copyToClipboard(lastEducational); return true; });
        }

        // Clipboard panel
        if (closeClipboardPanel != null) {
            closeClipboardPanel.setOnClickListener(v -> hideClipboardPanel());
        }
        if (btnPasteClipboard != null) {
            btnPasteClipboard.setOnClickListener(v -> pasteFromClipboard());
        }
    }

    private void setupToolbarButtons() {
        // Emoji button - opens emoji panel
        if (btnEmoji != null) {
            btnEmoji.setOnClickListener(v -> toggleEmojiPanel());
        }

        // Clipboard button - safe implementation
        if (btnClipboard != null) {
            btnClipboard.setOnClickListener(v -> {
                try {
                    if (clipboardPanel != null && clipboardPanel.getVisibility() == View.VISIBLE) {
                        hideClipboardPanel();
                    } else {
                        showClipboardPanel();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Clipboard error", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Mic button - open settings instead (repurposed)
        if (btnMic != null) {
            btnMic.setOnClickListener(v -> openSettings());
        }
    }

    private void openSettings() {
        try {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open settings", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== EMOJI PANEL ====================

    private void buildEmojiPanel() {
        if (emojiPanel == null) return;

        emojiPanel.removeAllViews();

        // Header with close button
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(24, 16, 24, 8);

        TextView title = new TextView(this);
        title.setText("😀 Emoji");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        title.setLayoutParams(titleParams);

        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(0xFF9AA4AF);
        closeBtn.setTextSize(14);
        closeBtn.setBackgroundColor(0x00000000);
        closeBtn.setOnClickListener(v -> hideEmojiPanel());

        header.addView(title);
        header.addView(closeBtn);
        emojiPanel.addView(header);

        // Scrollable emoji grid
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 180));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(10);
        grid.setPadding(8, 8, 8, 8);

        for (String emoji : EMOJIS) {
            TextView emojiBtn = new TextView(this);
            emojiBtn.setText(emoji);
            emojiBtn.setTextSize(22);
            emojiBtn.setGravity(Gravity.CENTER);
            emojiBtn.setPadding(8, 8, 8, 8);
            emojiBtn.setOnClickListener(v -> commitEmoji(emoji));
            grid.addView(emojiBtn);
        }

        scrollView.addView(grid);
        emojiPanel.addView(scrollView);
    }

    private void toggleEmojiPanel() {
        if (emojiPanel == null) return;

        if (emojiPanel.getVisibility() == View.VISIBLE) {
            hideEmojiPanel();
        } else {
            showEmojiPanel();
        }
    }

    private void showEmojiPanel() {
        if (emojiPanel == null) return;
        hideClipboardPanel();
        emojiPanel.setAlpha(0f);
        emojiPanel.setVisibility(View.VISIBLE);
        emojiPanel.animate().alpha(1f).setDuration(200).start();
    }

    private void hideEmojiPanel() {
        if (emojiPanel == null) return;
        emojiPanel.animate().alpha(0f).setDuration(150)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    emojiPanel.setVisibility(View.GONE);
                    emojiPanel.animate().setListener(null);
                }
            }).start();
    }

    private void commitEmoji(String emoji) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(emoji, 1);
        }
    }

    // ==================== CLIPBOARD (SAFE) ====================

    private void showClipboardPanel() {
        if (clipboardPanel == null) return;

        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            String clipText = "Clipboard is empty";
            int textColor = 0xFF9AA4AF;

            if (clipboard != null) {
                if (clipboard.hasPrimaryClip()) {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        ClipData.Item item = clip.getItemAt(0);
                        if (item != null) {
                            CharSequence text = item.getText();
                            if (text != null && text.length() > 0) {
                                clipText = text.toString();
                                textColor = 0xFFFFFFFF;
                            } else {
                                clipText = "No text in clipboard";
                            }
                        }
                    }
                }
            }

            if (clipboardContent != null) {
                clipboardContent.setText(clipText);
                clipboardContent.setTextColor(textColor);
            }

            hideEmojiPanel();
            clipboardPanel.setAlpha(0f);
            clipboardPanel.setVisibility(View.VISIBLE);
            clipboardPanel.animate().alpha(1f).setDuration(200).start();

        } catch (Exception e) {
            Toast.makeText(this, "Cannot access clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideClipboardPanel() {
        if (clipboardPanel == null) return;

        try {
            clipboardPanel.animate().alpha(0f).setDuration(150)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        clipboardPanel.setVisibility(View.GONE);
                        clipboardPanel.animate().setListener(null);
                    }
                }).start();
        } catch (Exception e) {
            clipboardPanel.setVisibility(View.GONE);
        }
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    ClipData.Item item = clip.getItemAt(0);
                    if (item != null) {
                        CharSequence text = item.getText();
                        if (text != null && text.length() > 0) {
                            InputConnection ic = getCurrentInputConnection();
                            if (ic != null) {
                                ic.commitText(text, 1);
                                currentText.append(text);
                                hideClipboardPanel();
                                scheduleDetection();
                                return;
                            }
                        }
                    }
                }
            }
            Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Paste failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && text != null && !text.isEmpty()) {
                ClipData clip = ClipData.newPlainText("KeyCare Suggestion", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Copy failed", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== LANGUAGE SWITCHING ====================

    private void cycleLanguage() {
        currentLanguage = (currentLanguage + 1) % 3;
        applyLanguageLayout();

        String langName;
        switch (currentLanguage) {
            case LANG_FR: langName = "FR"; break;
            case LANG_AR: langName = "AR"; break;
            default: langName = "EN"; break;
        }

        if (keySpace != null) {
            keySpace.setText(langName + " • KeyCare");
        }

        // Set RTL for Arabic
        if (currentLanguage == LANG_AR) {
            setRTLMode(true);
        } else {
            setRTLMode(false);
        }

        Toast.makeText(this, "Language: " + langName, Toast.LENGTH_SHORT).show();
    }

    private void setRTLMode(boolean rtl) {
        int direction = rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR;
        int layoutDir = rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;

        // Apply RTL to keyboard root and container
        if (keyboardView != null) {
            keyboardView.setLayoutDirection(layoutDir);
        }
        if (alphaContainer != null) {
            alphaContainer.setLayoutDirection(layoutDir);
        }
        
        if (suggestionCalm != null) suggestionCalm.setTextDirection(direction);
        if (suggestionFirm != null) suggestionFirm.setTextDirection(direction);
        if (suggestionEducational != null) suggestionEducational.setTextDirection(direction);
        if (bannerSubtitle != null) bannerSubtitle.setTextDirection(direction);
        if (rewritePanel != null) rewritePanel.setLayoutDirection(layoutDir);
    }

    private void applyLanguageLayout() {
        String[][] layout;
        switch (currentLanguage) {
            case LANG_FR: layout = LAYOUT_FR; break;
            case LANG_AR: layout = LAYOUT_AR; break;
            default: layout = LAYOUT_EN; break;
        }
        
        // Determine if uppercase labels should be shown (for EN/FR only)
        boolean shouldUppercase = (isShiftOn || isCapsLock) && currentLanguage != LANG_AR;

        // Row 1 keys
        Button[] row1Keys = {
            keyboardView.findViewById(R.id.key_q),
            keyboardView.findViewById(R.id.key_w),
            keyboardView.findViewById(R.id.key_e),
            keyboardView.findViewById(R.id.key_r),
            keyboardView.findViewById(R.id.key_t),
            keyboardView.findViewById(R.id.key_y),
            keyboardView.findViewById(R.id.key_u),
            keyboardView.findViewById(R.id.key_i),
            keyboardView.findViewById(R.id.key_o),
            keyboardView.findViewById(R.id.key_p)
        };

        for (int i = 0; i < row1Keys.length && i < layout[0].length; i++) {
            if (row1Keys[i] != null) {
                String label = layout[0][i];
                if (currentLanguage != LANG_AR) {
                    label = shouldUppercase ? label.toUpperCase() : label.toLowerCase();
                }
                row1Keys[i].setText(label);
            }
        }

        // Row 2 keys (9 for EN, 10 for FR, 10 for AR)
        Button[] row2Keys = {
            keyboardView.findViewById(R.id.key_a),
            keyboardView.findViewById(R.id.key_s),
            keyboardView.findViewById(R.id.key_d),
            keyboardView.findViewById(R.id.key_f),
            keyboardView.findViewById(R.id.key_g),
            keyboardView.findViewById(R.id.key_h),
            keyboardView.findViewById(R.id.key_j),
            keyboardView.findViewById(R.id.key_k),
            keyboardView.findViewById(R.id.key_l)
        };

        for (int i = 0; i < row2Keys.length && i < layout[1].length; i++) {
            if (row2Keys[i] != null) {
                String label = layout[1][i];
                if (currentLanguage != LANG_AR) {
                    label = shouldUppercase ? label.toUpperCase() : label.toLowerCase();
                }
                row2Keys[i].setText(label);
            }
        }

        // Row 3 keys (7 for EN, 6 for FR, 9 for AR)
        Button[] row3Keys = {
            keyboardView.findViewById(R.id.key_z),
            keyboardView.findViewById(R.id.key_x),
            keyboardView.findViewById(R.id.key_c),
            keyboardView.findViewById(R.id.key_v),
            keyboardView.findViewById(R.id.key_b),
            keyboardView.findViewById(R.id.key_n),
            keyboardView.findViewById(R.id.key_m)
        };

        for (int i = 0; i < row3Keys.length && i < layout[2].length; i++) {
            if (row3Keys[i] != null) {
                String label = layout[2][i];
                if (currentLanguage != LANG_AR) {
                    label = shouldUppercase ? label.toUpperCase() : label.toLowerCase();
                }
                row3Keys[i].setText(label);
            }
        }
        
        // Handle extra keys for Arabic
        Button keyExtra1 = keyboardView.findViewById(R.id.key_extra1);
        Button keyExtra2 = keyboardView.findViewById(R.id.key_extra2);
        
        if (currentLanguage == LANG_AR) {
            // Show extra keys for Arabic row 3 (which has 9 letters)
            if (keyExtra1 != null) {
                keyExtra1.setVisibility(View.VISIBLE);
                if (layout[2].length > 7) keyExtra1.setText(layout[2][7]);
            }
            if (keyExtra2 != null) {
                keyExtra2.setVisibility(View.VISIBLE);
                if (layout[2].length > 8) keyExtra2.setText(layout[2][8]);
            }
        } else {
            // Hide extra keys for EN/FR
            if (keyExtra1 != null) keyExtra1.setVisibility(View.GONE);
            if (keyExtra2 != null) keyExtra2.setVisibility(View.GONE);
        }
        
        // Update punctuation for Arabic
        Button commaKey = keyboardView.findViewById(R.id.key_comma);
        Button periodKey = keyboardView.findViewById(R.id.key_period);
        if (currentLanguage == LANG_AR) {
            if (commaKey != null) commaKey.setText("،");
            if (periodKey != null) periodKey.setText("؟");
        } else {
            if (commaKey != null) commaKey.setText(",");
            if (periodKey != null) periodKey.setText(".");
        }
        
        // Hide shift key for Arabic (no uppercase)
        ImageButton shiftKey = keyboardView.findViewById(R.id.key_shift);
        if (shiftKey != null) {
            if (currentLanguage == LANG_AR) {
                // For Arabic, change shift to show additional characters or tashkeel
                shiftKey.setVisibility(View.VISIBLE);
                shiftKey.setImageResource(R.drawable.ic_shift);
            } else {
                shiftKey.setVisibility(View.VISIBLE);
            }
        }
    }

    private String getCurrentLangCode() {
        switch (currentLanguage) {
            case LANG_FR: return "fr";
            case LANG_AR: return "ar";
            default: return "en";
        }
    }

    // ==================== MODE SWITCHING ====================

    private void switchMode(int newMode) {
        currentMode = newMode;

        if (alphaContainer != null) {
            alphaContainer.setVisibility(newMode == MODE_ALPHA ? View.VISIBLE : View.GONE);
        }
        if (numContainer != null) {
            numContainer.setVisibility(newMode == MODE_NUM ? View.VISIBLE : View.GONE);
        }
        if (symContainer != null) {
            symContainer.setVisibility(newMode == MODE_SYM ? View.VISIBLE : View.GONE);
        }
    }

    private void setupNumberRowListeners() {
        // Number row in alpha mode (1-0)
        int[] numRowKeys = {
            R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4, R.id.key_5,
            R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9, R.id.key_0
        };

        for (int keyId : numRowKeys) {
            Button key = keyboardView.findViewById(keyId);
            if (key != null) {
                key.setOnClickListener(v -> commitChar(((Button) v).getText().toString()));
            }
        }
    }

    private void setupNumContainerListeners() {
        // Numbers 1-0
        int[] numKeys = {
            R.id.num_1, R.id.num_2, R.id.num_3, R.id.num_4, R.id.num_5,
            R.id.num_6, R.id.num_7, R.id.num_8, R.id.num_9, R.id.num_0
        };
        for (int keyId : numKeys) {
            Button key = keyboardView.findViewById(keyId);
            if (key != null) {
                key.setOnClickListener(v -> commitChar(((Button) v).getText().toString()));
            }
        }

        // Symbol keys in num container
        int[] numSymKeys = {
            R.id.num_at, R.id.num_hash, R.id.num_dollar, R.id.num_percent,
            R.id.num_amp, R.id.num_minus, R.id.num_plus, R.id.num_lparen, R.id.num_rparen,
            R.id.num_star, R.id.num_dquote, R.id.num_squote, R.id.num_colon,
            R.id.num_semi, R.id.num_excl, R.id.num_quest, R.id.num_comma, R.id.num_period
        };
        for (int keyId : numSymKeys) {
            Button key = keyboardView.findViewById(keyId);
            if (key != null) {
                key.setOnClickListener(v -> commitChar(((Button) v).getText().toString()));
            }
        }

        // #+= key -> switch to symbols
        View symKey = keyboardView.findViewById(R.id.key_symbols);
        if (symKey != null) {
            symKey.setOnClickListener(v -> switchMode(MODE_SYM));
        }

        // ABC key -> switch to alpha
        View abcNumKey = keyboardView.findViewById(R.id.key_abc_num);
        if (abcNumKey != null) {
            abcNumKey.setOnClickListener(v -> switchMode(MODE_ALPHA));
        }

        // Space in num container
        View numSpace = keyboardView.findViewById(R.id.num_space);
        if (numSpace != null) {
            numSpace.setOnClickListener(v -> commitChar(" "));
        }

        // Backspace in num container
        View numBackspace = keyboardView.findViewById(R.id.num_backspace);
        if (numBackspace != null) {
            numBackspace.setOnClickListener(v -> handleBackspace());
            numBackspace.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isBackspacePressed = true;
                        startContinuousBackspace();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isBackspacePressed = false;
                        stopContinuousBackspace();
                        break;
                }
                return false;
            });
        }

        // Enter in num container
        View numEnter = keyboardView.findViewById(R.id.num_enter);
        if (numEnter != null) {
            numEnter.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.sendKeyEvent(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
                    ic.sendKeyEvent(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
                }
                resetState();
            });
        }

        // Language in num container
        View numLang = keyboardView.findViewById(R.id.num_language);
        if (numLang != null) {
            numLang.setOnClickListener(v -> cycleLanguage());
        }
    }

    private void setupSymContainerListeners() {
        // All symbol keys
        int[] symKeys = {
            R.id.sym_tilde, R.id.sym_backtick, R.id.sym_pipe, R.id.sym_bullet,
            R.id.sym_sqrt, R.id.sym_pi, R.id.sym_div, R.id.sym_mul, R.id.sym_para, R.id.sym_delta,
            R.id.sym_pound, R.id.sym_euro, R.id.sym_yen, R.id.sym_cent,
            R.id.sym_caret, R.id.sym_degree, R.id.sym_eq, R.id.sym_lbrace, R.id.sym_rbrace,
            R.id.sym_backslash, R.id.sym_copy, R.id.sym_reg, R.id.sym_tm, R.id.sym_check,
            R.id.sym_lbrack, R.id.sym_rbrack, R.id.sym_comma, R.id.sym_period
        };
        for (int keyId : symKeys) {
            Button key = keyboardView.findViewById(keyId);
            if (key != null) {
                key.setOnClickListener(v -> commitChar(((Button) v).getText().toString()));
            }
        }

        // 123 key -> switch to numbers
        View key123Sym = keyboardView.findViewById(R.id.key_123_sym);
        if (key123Sym != null) {
            key123Sym.setOnClickListener(v -> switchMode(MODE_NUM));
        }

        // ABC key -> switch to alpha
        View abcSymKey = keyboardView.findViewById(R.id.key_abc_sym);
        if (abcSymKey != null) {
            abcSymKey.setOnClickListener(v -> switchMode(MODE_ALPHA));
        }

        // Space in sym container
        View symSpace = keyboardView.findViewById(R.id.sym_space);
        if (symSpace != null) {
            symSpace.setOnClickListener(v -> commitChar(" "));
        }

        // Backspace in sym container
        View symBackspace = keyboardView.findViewById(R.id.sym_backspace);
        if (symBackspace != null) {
            symBackspace.setOnClickListener(v -> handleBackspace());
            symBackspace.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isBackspacePressed = true;
                        startContinuousBackspace();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isBackspacePressed = false;
                        stopContinuousBackspace();
                        break;
                }
                return false;
            });
        }

        // Enter in sym container
        View symEnter = keyboardView.findViewById(R.id.sym_enter);
        if (symEnter != null) {
            symEnter.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.sendKeyEvent(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
                    ic.sendKeyEvent(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
                }
                resetState();
            });
        }

        // Language in sym container
        View symLang = keyboardView.findViewById(R.id.sym_language);
        if (symLang != null) {
            symLang.setOnClickListener(v -> cycleLanguage());
        }
    }

    // ==================== KEY LISTENERS ====================

    private void setupKeyListeners() {
        // Letter keys
        int[] letterKeys = {
            R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r, R.id.key_t,
            R.id.key_y, R.id.key_u, R.id.key_i, R.id.key_o, R.id.key_p,
            R.id.key_a, R.id.key_s, R.id.key_d, R.id.key_f, R.id.key_g,
            R.id.key_h, R.id.key_j, R.id.key_k, R.id.key_l,
            R.id.key_z, R.id.key_x, R.id.key_c, R.id.key_v,
            R.id.key_b, R.id.key_n, R.id.key_m
        };

        for (int keyId : letterKeys) {
            Button key = keyboardView.findViewById(keyId);
            if (key != null) {
                key.setOnClickListener(v -> {
                    String letter = ((Button) v).getText().toString();
                    // For non-Arabic, apply shift/caps state
                    if (currentLanguage != LANG_AR) {
                        if (isShiftOn || isCapsLock) {
                            letter = letter.toUpperCase();
                        } else {
                            letter = letter.toLowerCase();
                        }
                        // If shift is on (not caps lock), turn it off after one letter
                        if (isShiftOn && !isCapsLock) {
                            isShiftOn = false;
                            updateShiftState();
                        }
                    }
                    commitChar(letter);
                });
            }
        }

        // Space - trigger immediate mediation after space
        View spaceKey = keyboardView.findViewById(R.id.key_space);
        if (spaceKey != null) {
            spaceKey.setOnClickListener(v -> {
                commitChar(" ");
                // Trigger immediate mediation on space (user likely paused to think)
                triggerImmediateMediation();
            });
        }

        // Backspace with long press support
        View backspaceKey = keyboardView.findViewById(R.id.key_backspace);
        if (backspaceKey != null) {
            backspaceKey.setOnClickListener(v -> handleBackspace());
            backspaceKey.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isBackspacePressed = true;
                        startContinuousBackspace();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isBackspacePressed = false;
                        stopContinuousBackspace();
                        break;
                }
                return false;
            });
        }

        // Enter - trigger mediation before sending (last chance check)
        View enterKey = keyboardView.findViewById(R.id.key_enter);
        if (enterKey != null) {
            enterKey.setOnClickListener(v -> {
                // Trigger immediate mediation before sending
                triggerImmediateMediation();
                
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.sendKeyEvent(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
                    ic.sendKeyEvent(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
                }
                resetState();
            });
        }

        // Shift with double-tap for Caps Lock
        View shiftKey = keyboardView.findViewById(R.id.key_shift);
        if (shiftKey != null) {
            shiftKey.setOnClickListener(v -> {
                long currentTime = System.currentTimeMillis();
                
                if (currentTime - lastShiftTapTime < DOUBLE_TAP_THRESHOLD) {
                    // Double tap - toggle Caps Lock
                    isCapsLock = !isCapsLock;
                    isShiftOn = isCapsLock;
                    Toast.makeText(this, isCapsLock ? "CAPS LOCK ON" : "CAPS LOCK OFF", Toast.LENGTH_SHORT).show();
                } else {
                    // Single tap - toggle shift (turn off caps lock)
                    if (isCapsLock) {
                        isCapsLock = false;
                        isShiftOn = false;
                    } else {
                        isShiftOn = !isShiftOn;
                    }
                }
                
                lastShiftTapTime = currentTime;
                updateShiftState();
            });
        }

        // Comma and period - output whatever character is displayed (changes for Arabic)
        View commaKey = keyboardView.findViewById(R.id.key_comma);
        if (commaKey != null) {
            commaKey.setOnClickListener(v -> {
                String text = ((Button) v).getText().toString();
                commitChar(text);
            });
        }

        View periodKey = keyboardView.findViewById(R.id.key_period);
        if (periodKey != null) {
            periodKey.setOnClickListener(v -> {
                String text = ((Button) v).getText().toString();
                commitChar(text);
            });
        }
        
        // Extra keys for Arabic (hidden in EN/FR)
        Button keyExtra1 = keyboardView.findViewById(R.id.key_extra1);
        if (keyExtra1 != null) {
            keyExtra1.setOnClickListener(v -> {
                String letter = ((Button) v).getText().toString();
                commitChar(letter);
            });
        }
        
        Button keyExtra2 = keyboardView.findViewById(R.id.key_extra2);
        if (keyExtra2 != null) {
            keyExtra2.setOnClickListener(v -> {
                String letter = ((Button) v).getText().toString();
                commitChar(letter);
            });
        }

        // Numbers key (123) - switch to numeric mode
        View numbersKey = keyboardView.findViewById(R.id.key_numbers);
        if (numbersKey != null) {
            numbersKey.setOnClickListener(v -> switchMode(MODE_NUM));
        }

        // Setup number row keys (1-0) in alpha mode
        setupNumberRowListeners();

        // Setup numeric container keys
        setupNumContainerListeners();

        // Setup symbol container keys
        setupSymContainerListeners();

        // Language switch
        View langKey = keyboardView.findViewById(R.id.key_language);
        if (langKey != null) {
            langKey.setOnClickListener(v -> cycleLanguage());
        }
    }

    private void startContinuousBackspace() {
        backspaceRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBackspacePressed) {
                    handleBackspace();
                    backspaceHandler.postDelayed(this, BACKSPACE_REPEAT_DELAY);
                }
            }
        };
        backspaceHandler.postDelayed(backspaceRunnable, 400);
    }

    private void stopContinuousBackspace() {
        if (backspaceRunnable != null) {
            backspaceHandler.removeCallbacks(backspaceRunnable);
        }
    }

    private void updateShiftState() {
        ImageButton shiftKey = keyboardView.findViewById(R.id.key_shift);
        if (shiftKey != null) {
            if (isCapsLock) {
                // Caps lock active - show filled shift icon
                shiftKey.setBackgroundResource(R.drawable.bg_shift_capslock);
            } else if (isShiftOn) {
                shiftKey.setBackgroundResource(R.drawable.bg_shift_active);
            } else {
                shiftKey.setBackgroundResource(R.drawable.bg_key_special_premium);
            }
        }
        
        // Update key labels to show uppercase/lowercase (only for non-Arabic)
        if (currentLanguage != LANG_AR) {
            updateKeyLabels();
        }
    }
    
    private void updateKeyLabels() {
        String[][] layout;
        switch (currentLanguage) {
            case LANG_FR: layout = LAYOUT_FR; break;
            default: layout = LAYOUT_EN; break;
        }
        
        boolean shouldUppercase = isShiftOn || isCapsLock;
        
        // Row 1 keys
        Button[] row1Keys = {
            keyboardView.findViewById(R.id.key_q),
            keyboardView.findViewById(R.id.key_w),
            keyboardView.findViewById(R.id.key_e),
            keyboardView.findViewById(R.id.key_r),
            keyboardView.findViewById(R.id.key_t),
            keyboardView.findViewById(R.id.key_y),
            keyboardView.findViewById(R.id.key_u),
            keyboardView.findViewById(R.id.key_i),
            keyboardView.findViewById(R.id.key_o),
            keyboardView.findViewById(R.id.key_p)
        };
        
        for (int i = 0; i < row1Keys.length && i < layout[0].length; i++) {
            if (row1Keys[i] != null) {
                String label = layout[0][i];
                row1Keys[i].setText(shouldUppercase ? label.toUpperCase() : label.toLowerCase());
            }
        }
        
        // Row 2 keys
        Button[] row2Keys = {
            keyboardView.findViewById(R.id.key_a),
            keyboardView.findViewById(R.id.key_s),
            keyboardView.findViewById(R.id.key_d),
            keyboardView.findViewById(R.id.key_f),
            keyboardView.findViewById(R.id.key_g),
            keyboardView.findViewById(R.id.key_h),
            keyboardView.findViewById(R.id.key_j),
            keyboardView.findViewById(R.id.key_k),
            keyboardView.findViewById(R.id.key_l)
        };
        
        for (int i = 0; i < row2Keys.length && i < layout[1].length; i++) {
            if (row2Keys[i] != null) {
                String label = layout[1][i];
                row2Keys[i].setText(shouldUppercase ? label.toUpperCase() : label.toLowerCase());
            }
        }
        
        // Row 3 keys
        Button[] row3Keys = {
            keyboardView.findViewById(R.id.key_z),
            keyboardView.findViewById(R.id.key_x),
            keyboardView.findViewById(R.id.key_c),
            keyboardView.findViewById(R.id.key_v),
            keyboardView.findViewById(R.id.key_b),
            keyboardView.findViewById(R.id.key_n),
            keyboardView.findViewById(R.id.key_m)
        };
        
        for (int i = 0; i < row3Keys.length && i < layout[2].length; i++) {
            if (row3Keys[i] != null) {
                String label = layout[2][i];
                row3Keys[i].setText(shouldUppercase ? label.toUpperCase() : label.toLowerCase());
            }
        }
    }

    private void commitChar(String c) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(c, 1);
            currentText.append(c);
            scheduleDetection();
            playKeyFeedback();
        }
    }

    private void playKeyFeedback() {
        if (settings == null) return;

        // Play sound
        if (settings.isSoundEnabled() && audioManager != null) {
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
        }

        // Vibrate
        if (settings.isVibrationEnabled() && vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(15);
            }
        }
    }

    /**
     * Handle backspace key press.
     * ISSUE 1 FIX: Properly handles selected text deletion.
     * 
     * Priority:
     * 1. If text is selected -> delete the selection
     * 2. Otherwise -> delete one character before cursor
     */
    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        // ISSUE 1 FIX: Check for selected text first
        CharSequence selectedText = ic.getSelectedText(0);
        
        if (selectedText != null && selectedText.length() > 0) {
            // Text is selected - delete the selection by replacing with empty string
            ic.beginBatchEdit();
            ic.commitText("", 1); // This replaces selection with empty string
            ic.endBatchEdit();
            
            // Update our internal buffer
            currentText = new StringBuilder();
            // Reload from field to keep in sync
            syncCurrentTextFromField(ic);
        } else {
            // No selection - normal backspace (delete one char before cursor)
            ic.deleteSurroundingText(1, 0);
            if (currentText.length() > 0) {
                currentText.deleteCharAt(currentText.length() - 1);
            }
        }
        
        scheduleDetection();
        playKeyFeedback();
    }
    
    /**
     * Clear all text in the input field.
     * ISSUE 1 FIX: Robust implementation for "Clear Text" functionality.
     */
    private void clearAllText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        try {
            ic.beginBatchEdit();
            
            // Get all text in field
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.hintMaxChars = 10000;
            request.hintMaxLines = 1000;
            ExtractedText extractedText = ic.getExtractedText(request, 0);
            
            if (extractedText != null && extractedText.text != null) {
                int textLength = extractedText.text.length();
                if (textLength > 0) {
                    // Select all text (from start to end)
                    ic.setSelection(0, textLength);
                    // Delete by committing empty string
                    ic.commitText("", 1);
                }
            } else {
                // Fallback: try to get text before and after cursor
                CharSequence before = ic.getTextBeforeCursor(10000, 0);
                CharSequence after = ic.getTextAfterCursor(10000, 0);
                
                int beforeLen = before != null ? before.length() : 0;
                int afterLen = after != null ? after.length() : 0;
                
                if (beforeLen > 0 || afterLen > 0) {
                    ic.deleteSurroundingText(beforeLen, afterLen);
                }
            }
            
            ic.endBatchEdit();
            
            // Reset internal state
            currentText = new StringBuilder();
            resetBadge();
            
        } catch (Exception e) {
            // Fallback on error
            ic.endBatchEdit();
            currentText = new StringBuilder();
        }
    }
    
    /**
     * Sync our internal currentText buffer with the actual field content.
     * Used after operations that may change the field content externally.
     */
    private void syncCurrentTextFromField(InputConnection ic) {
        if (ic == null) return;
        
        try {
            ExtractedTextRequest request = new ExtractedTextRequest();
            ExtractedText et = ic.getExtractedText(request, 0);
            if (et != null && et.text != null) {
                currentText = new StringBuilder(et.text.toString());
            } else {
                currentText = new StringBuilder();
            }
        } catch (Exception e) {
            // Keep existing buffer on error
        }
    }

    // ==================== API CALLS ====================

    /**
     * Schedule detection with debouncing.
     * Uses the new Gemini 3 mediation API.
     */
    private void scheduleDetection() {
        if (debounceRunnable != null) {
            debounceHandler.removeCallbacks(debounceRunnable);
        }

        debounceRunnable = () -> {
            if (currentText.length() > 0) {
                // Use Gemini 3 mediation API
                callGeminiMediationAPI(currentText.toString());
            } else {
                // No text - reset to safe state using controller
                if (riskUiController != null) {
                    riskUiController.reset();
                } else {
                    resetBadge();
                }
            }
        };

        debounceHandler.postDelayed(debounceRunnable, DEBOUNCE_DELAY);
    }
    
    /**
     * Call the Gemini 3 Mediation API.
     * This is the main integration point with the backend.
     * 
     * Uses user preferences for tone and language hint from SettingsManager.
     * Updates UI with risk level, explanation, and stores rewrite for later use.
     */
    private void callGeminiMediationAPI(String text) {
        if (mediationApiClient == null || text == null || text.trim().isEmpty()) {
            return;
        }
        
        // Get user preferences
        String tone = settings != null ? settings.getMediationTone() : "calm";
        String langHint = settings != null ? settings.getMediationLangHint() : "auto";
        
        // Override lang_hint based on current keyboard language if set to auto
        if ("auto".equals(langHint)) {
            langHint = getCurrentLangCode();
            // Map keyboard lang code to API lang code
            if ("en".equals(langHint) || "fr".equals(langHint) || "ar".equals(langHint)) {
                // These are valid API codes
            } else {
                langHint = "auto"; // Fallback to auto
            }
        }
        
        // Build request
        MediateRequest request = new MediateRequest.Builder()
            .text(text)
            .tone(tone)
            .langHint(langHint)
            .build();
        
        Log.d(TAG, "[MEDIATE] Calling API - text_len: " + text.length() + ", tone: " + tone + ", lang: " + langHint);
        
        // Make async API call
        mediationApiClient.requestMediation(request, new MediationApiClient.MediationCallback() {
            @Override
            public void onSuccess(MediateResponse response) {
                Log.d(TAG, "[MEDIATE] Response received: " + response.toString());
                
                // Store response for rewrite button
                currentMediationResponse = response;
                
                // Update UI based on risk level
                MediateResponse.RiskLevel riskLevel = response.getRiskLevel();
                double score = riskLevel.toScore();
                String label = riskLevel.getDisplayText();
                
                Log.d(TAG, "[MEDIATE] Risk: " + riskLevel.getValue() + ", Score: " + score + ", Label: " + label);
                
                // Store current values
                currentLabel = label;
                currentScore = score;
                
                // Update risk UI using controller
                if (riskUiController != null) {
                    // Map RiskLevel to label format expected by controller
                    String controllerLabel = "SAFE";
                    if (riskLevel == MediateResponse.RiskLevel.HARMFUL) {
                        controllerLabel = "OFFENSIVE";
                    } else if (riskLevel == MediateResponse.RiskLevel.DANGEROUS) {
                        controllerLabel = "OFFENSIVE"; // Controller uses score to distinguish
                    }
                    
                    Log.d(TAG, "[MEDIATE] Updating UI - controllerLabel: " + controllerLabel + ", score: " + score);
                    
                    // Update with explanation from Gemini
                    riskUiController.updateRiskWithExplanation(controllerLabel, score, response.getWhy());
                } else {
                    Log.d(TAG, "[MEDIATE] Updating badge - label: " + label + ", score: " + score);
                    updateBadge(label, score);
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "[MEDIATE] API error: " + error);
                // On error, keep current state - don't disrupt user
                // Optionally fall back to local detection
                currentMediationResponse = null;
            }
        });
    }
    
    /**
     * Trigger immediate mediation (on space/enter).
     * Bypasses debouncing for instant feedback.
     */
    private void triggerImmediateMediation() {
        if (currentText.length() > 0) {
            // Cancel any pending debounced call
            if (debounceRunnable != null) {
                debounceHandler.removeCallbacks(debounceRunnable);
            }
            // Call immediately
            callGeminiMediationAPI(currentText.toString());
        }
    }

    private void callDetectAPI(String text) {
        // ISSUE 2 FIX: Cancel previous in-flight detection to avoid out-of-order updates
        if (currentDetectTask != null && !currentDetectTask.isDone()) {
            currentDetectTask.cancel(true);
        }
        
        currentDetectTask = executor.submit(() -> {
            try {
                URL url = new URL(ApiConfig.BASE_URL + ApiConfig.ENDPOINT_DETECT);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT);
                conn.setReadTimeout(ApiConfig.READ_TIMEOUT);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("text", text);
                json.put("lang", getCurrentLangCode());

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject result = new JSONObject(response.toString());
                    double score = result.getDouble("risk");
                    String labelMain = result.getString("label");

                    // Check if task was cancelled
                    if (Thread.currentThread().isInterrupted()) {
                        conn.disconnect();
                        return;
                    }

                    new Handler(Looper.getMainLooper()).post(() -> {
                        currentLabel = labelMain;
                        currentScore = score;
                        // ISSUE 2 FIX: Use RiskUiController for debounced, single-instance updates
                        if (riskUiController != null) {
                            riskUiController.updateRiskImmediate(labelMain, score);
                        } else {
                            updateBadge(labelMain, score);
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                // Silent fail - don't update UI on error
            }
        });
    }

    private void callRewriteAPI(String text) {
        // Cancel previous in-flight rewrite to avoid stale results
        if (currentRewriteTask != null && !currentRewriteTask.isDone()) {
            currentRewriteTask.cancel(true);
        }
        
        currentRewriteTask = executor.submit(() -> {
            try {
                URL url = new URL(ApiConfig.BASE_URL + ApiConfig.ENDPOINT_REWRITE);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT);
                conn.setReadTimeout(ApiConfig.READ_TIMEOUT);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("text", text);
                json.put("lang", getCurrentLangCode());

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                // Check if task was cancelled
                if (Thread.currentThread().isInterrupted()) {
                    conn.disconnect();
                    return;
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject result = new JSONObject(response.toString());
                    
                    // ISSUE 3 FIX: Use SuggestionEngine for varied suggestions
                    // Server returns base suggestions, but we enhance with local variety
                    String serverCalm = result.optString("calm", "");
                    String serverFirm = result.optString("firm", "");
                    String serverEducational = result.optString("educational", "");
                    
                    // If server returned suggestions, use them
                    // Otherwise, generate local suggestions using SuggestionEngine
                    final String finalCalm;
                    final String finalFirm;
                    final String finalEducational;
                    
                    if (!serverCalm.isEmpty() && !serverFirm.isEmpty() && !serverEducational.isEmpty()) {
                        // Use server suggestions
                        finalCalm = serverCalm;
                        finalFirm = serverFirm;
                        finalEducational = serverEducational;
                    } else {
                        // ISSUE 3 FIX: Generate varied local suggestions
                        SuggestionEngine.RiskState riskState = SuggestionEngine.RiskState.RISKY;
                        if ("SAFE".equals(currentLabel)) {
                            riskState = SuggestionEngine.RiskState.SAFE;
                        } else if (currentScore >= 0.7) {
                            riskState = SuggestionEngine.RiskState.DANGER;
                        }
                        
                        SuggestionEngine.SuggestionResult suggestions = 
                            suggestionEngine.generate(text, getCurrentLangCode(), riskState);
                        
                        finalCalm = suggestions.calm;
                        finalFirm = suggestions.firm;
                        finalEducational = suggestions.educational;
                    }

                    new Handler(Looper.getMainLooper()).post(() -> {
                        lastCalm = finalCalm;
                        lastFirm = finalFirm;
                        lastEducational = finalEducational;
                        
                        if (suggestionCalm != null) suggestionCalm.setText("😌 " + truncate(lastCalm, 35));
                        if (suggestionFirm != null) suggestionFirm.setText("💪 " + truncate(lastFirm, 35));
                        if (suggestionEducational != null) suggestionEducational.setText("📚 " + truncate(lastEducational, 35));
                        showRewritePanel();
                        resetButtonStates();
                    });
                } else {
                    // Server error - generate local suggestions
                    generateLocalSuggestions(text);
                }
                conn.disconnect();
            } catch (Exception e) {
                // Network error - generate local suggestions as fallback
                generateLocalSuggestions(text);
            }
        });
    }
    
    /**
     * ISSUE 3 FIX: Generate local suggestions when server is unavailable.
     * Uses SuggestionEngine for variety and avoids repetition.
     */
    private void generateLocalSuggestions(String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (suggestionEngine != null) {
                SuggestionEngine.RiskState riskState = SuggestionEngine.RiskState.RISKY;
                if ("SAFE".equals(currentLabel)) {
                    riskState = SuggestionEngine.RiskState.SAFE;
                } else if (currentScore >= 0.7) {
                    riskState = SuggestionEngine.RiskState.DANGER;
                }
                
                SuggestionEngine.SuggestionResult suggestions = 
                    suggestionEngine.generate(text, getCurrentLangCode(), riskState);
                
                lastCalm = suggestions.calm;
                lastFirm = suggestions.firm;
                lastEducational = suggestions.educational;
                
                if (suggestionCalm != null) suggestionCalm.setText("😌 " + truncate(lastCalm, 35));
                if (suggestionFirm != null) suggestionFirm.setText("💪 " + truncate(lastFirm, 35));
                if (suggestionEducational != null) suggestionEducational.setText("📚 " + truncate(lastEducational, 35));
                showRewritePanel();
            }
            resetButtonStates();
        });
    }
    private void resetButtonStates() {
        // REMOVED: rewriteButton reset - using bannerCtaButton only
        if (bannerCtaButton != null) {
            bannerCtaButton.setEnabled(true);
            bannerCtaButton.setText("Fix with AI");
        }
    }

    // ==================== FIX AI BOTTOM SHEET ====================
    
    /**
     * Open the Fix AI bottom sheet and fetch suggestions.
     * Uses Gemini rewrite from mediation response if available.
     */
    private void openFixAiBottomSheet() {
        if (fixAiController == null || currentText.length() == 0) return;
        
        Log.d(TAG, "Opening Fix AI - using Gemini mediation API");
        
        // Hide the old rewrite panel if visible
        hideRewritePanel();
        
        // Check if we have a Gemini rewrite from the mediation response
        if (currentMediationResponse != null && currentMediationResponse.hasRewrite()) {
            Log.d(TAG, "Using Gemini rewrite: " + currentMediationResponse.getRewrite());
            
            // Create suggestions list with Gemini rewrite as primary
            java.util.List<RewriteApiClient.Suggestion> suggestions = new java.util.ArrayList<>();
            
            // Add Gemini rewrite as the main suggestion
            suggestions.add(new RewriteApiClient.Suggestion(
                "✨ Gemini Rewrite",
                currentMediationResponse.getRewrite(),
                "gemini"
            ));
            
            // Also add explanation as context
            if (currentMediationResponse.getWhy() != null && !currentMediationResponse.getWhy().isEmpty()) {
                Log.d(TAG, "Gemini explanation: " + currentMediationResponse.getWhy());
            }
            
            // Show suggestions immediately
            fixAiController.showSuggestions(suggestions);
        } else {
            // No Gemini rewrite available, fetch from API
            Log.d(TAG, "No Gemini rewrite, fetching from API");
            fixAiController.showLoading();
            String tone = fixAiController.getCurrentTone().value;
            fetchFixAiSuggestions(currentText.toString(), tone);
        }
    }
    
    /**
     * Apply the Gemini rewrite directly (bypasses Fix AI sheet).
     * Use this for quick one-tap rewrite.
     */
    private void applyGeminiRewrite() {
        if (currentMediationResponse != null && currentMediationResponse.hasRewrite()) {
            String rewrite = currentMediationResponse.getRewrite();
            applyFixAiSuggestion(rewrite);
        }
    }
    
    /**
     * Fetch suggestions from API (with fallback).
     * SAFE: Handles all errors gracefully, never crashes.
     */
    private void fetchFixAiSuggestions(String text, String tone) {
        if (rewriteApiClient == null) {
            Log.e(TAG, "RewriteApiClient is null");
            return;
        }
        
        String lang = getCurrentLangCode();
        String riskLabel = currentLabel;
        double riskScoreVal = currentScore;
        
        Log.d(TAG, "Fetching rewrite - lang: " + lang + ", tone: " + tone);
        
        rewriteApiClient.requestRewrite(text, lang, tone, riskLabel, riskScoreVal,
            new RewriteApiClient.RewriteCallback() {
                @Override
                public void onSuccess(java.util.List<RewriteApiClient.Suggestion> suggestions) {
                    Log.d(TAG, "Rewrite success - got " + suggestions.size() + " suggestions");
                    try {
                        if (fixAiController != null) {
                            fixAiController.showSuggestions(suggestions);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing suggestions: " + e.getMessage());
                    }
                }
                
                @Override
                public void onError(String error, java.util.List<RewriteApiClient.Suggestion> fallbackSuggestions) {
                    Log.w(TAG, "Rewrite error: " + error + ", using fallback");
                    try {
                        // Show fallback suggestions
                        if (fixAiController != null) {
                            fixAiController.showSuggestions(fallbackSuggestions);
                        }
                        // Show brief toast about using offline suggestions
                        if (error != null && !error.isEmpty()) {
                            Toast.makeText(KeyCareIME.this, "Using offline suggestions", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing fallback suggestions: " + e.getMessage());
                    }
                }
            });
    }
    
    /**
     * Apply the selected suggestion from Fix AI.
     * Replaces the current text in the input field.
     */
    private void applyFixAiSuggestion(String suggestion) {
        if (suggestion == null || suggestion.isEmpty()) return;
        
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        
        ic.beginBatchEdit();
        try {
            // Get current text to determine what to replace
            String fullText = getFullInputText();
            
            if (fullText != null && !fullText.isEmpty()) {
                // Delete all current text
                ExtractedTextRequest req = new ExtractedTextRequest();
                req.hintMaxChars = 10000;
                ExtractedText extracted = ic.getExtractedText(req, 0);
                
                if (extracted != null && extracted.text != null) {
                    int textLen = extracted.text.length();
                    // Move cursor to end and delete backwards
                    ic.setSelection(textLen, textLen);
                    ic.deleteSurroundingText(textLen, 0);
                }
            }
            
            // Insert new suggestion
            ic.commitText(suggestion, 1);
            
            // Update our tracking
            currentText.setLength(0);
            currentText.append(suggestion);
            
            // Hide bottom sheet
            if (fixAiController != null) {
                fixAiController.hide();
            }
            
            // Reset risk state and re-analyze
            resetBadge();
            
            // Show toast
            Toast.makeText(this, "✓ Applied", Toast.LENGTH_SHORT).show();
            
        } finally {
            ic.endBatchEdit();
        }
    }
    
    /**
     * Get full text from current input field.
     * Limited to last 800 chars for performance.
     */
    private String getFullInputText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        
        ExtractedTextRequest req = new ExtractedTextRequest();
        req.hintMaxChars = 800;
        ExtractedText extracted = ic.getExtractedText(req, 0);
        
        if (extracted != null && extracted.text != null) {
            return extracted.text.toString();
        }
        
        // Fallback: get before + after cursor
        CharSequence before = ic.getTextBeforeCursor(400, 0);
        CharSequence after = ic.getTextAfterCursor(400, 0);
        
        StringBuilder sb = new StringBuilder();
        if (before != null) sb.append(before);
        if (after != null) sb.append(after);
        
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    private void applySuggestion(String suggestion) {
        if (suggestion == null || suggestion.isEmpty()) return;

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            int len = currentText.length();
            ic.deleteSurroundingText(len, 0);
            ic.commitText(suggestion, 1);
            currentText = new StringBuilder(suggestion);
            hideRewritePanel();
            // ISSUE 2 FIX: Use RiskUiController to hide banner
            if (riskUiController != null) {
                riskUiController.hideBanner();
            } else {
                hideBanner();
            }
            scheduleDetection();
        }
    }

    // ==================== UI ANIMATIONS ====================

    private void showRewritePanel() {
        if (rewritePanel == null) return;
        rewritePanel.setAlpha(0f);
        rewritePanel.setVisibility(View.VISIBLE);
        rewritePanel.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    private void hideRewritePanel() {
        if (rewritePanel == null) return;
        rewritePanel.animate()
            .alpha(0f)
            .setDuration(150)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    rewritePanel.setVisibility(View.GONE);
                    rewritePanel.animate().setListener(null);
                }
            }).start();
    }

    private void showBanner(boolean isDanger) {
        if (riskBanner == null) return;

        if (isDanger) {
            riskBanner.setBackgroundResource(R.drawable.bg_banner_danger_premium);
            if (bannerTitle != null) {
                bannerTitle.setText("🚨 DANGER DETECTED");
                bannerTitle.setTextColor(0xFFFF5252);
            }
            if (bannerSubtitle != null) {
                bannerSubtitle.setText("This message contains potentially harmful content");
            }
        } else {
            riskBanner.setBackgroundResource(R.drawable.bg_banner_warning_premium);
            if (bannerTitle != null) {
                bannerTitle.setText("⚠ RISK DETECTED");
                bannerTitle.setTextColor(0xFFFFA726);
            }
            if (bannerSubtitle != null) {
                bannerSubtitle.setText("This message may be perceived as aggressive");
            }
        }

        riskBanner.setAlpha(0f);
        riskBanner.setTranslationY(-20f);
        riskBanner.setVisibility(View.VISIBLE);
        riskBanner.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    private void hideBanner() {
        if (riskBanner == null || riskBanner.getVisibility() != View.VISIBLE) return;

        riskBanner.animate()
            .alpha(0f)
            .translationY(-20f)
            .setDuration(150)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    riskBanner.setVisibility(View.GONE);
                    riskBanner.animate().setListener(null);
                }
            }).start();
    }

    private void animateButtonPress(View v) {
        if (v == null) return;
        v.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction(() -> v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(100)
                .start())
            .start();
    }

    private void updateBadge(String label, double score) {
        if (riskScore != null) {
            riskScore.setText(String.format("%.2f", score));
        }

        if (riskBadge == null) return;

        if ("SAFE".equals(label)) {
            riskBadge.setText("SAFE");
            riskBadge.setTextColor(0xFF00E5C4);
            riskBadge.setBackgroundResource(R.drawable.pill_safe_premium);
            // REMOVED: rewriteButton visibility - using bannerCtaButton only
            hideBanner();
        } else if ("OFFENSIVE".equals(label)) {
            if (score >= 0.7) {
                riskBadge.setText("DANGER");
                riskBadge.setTextColor(0xFFFF5252);
                riskBadge.setBackgroundResource(R.drawable.pill_danger_premium);
                showBanner(true);
            } else {
                riskBadge.setText("RISKY");
                riskBadge.setTextColor(0xFFFFA726);
                riskBadge.setBackgroundResource(R.drawable.pill_risky_premium);
                showBanner(false);
            }
            // REMOVED: rewriteButton visibility - using bannerCtaButton only
        } else {
            riskBadge.setText(label);
            riskBadge.setTextColor(0xFFFFA726);
            riskBadge.setBackgroundResource(R.drawable.pill_risky_premium);
            // REMOVED: rewriteButton visibility - using bannerCtaButton only
        }
    }

    private void resetBadge() {
        // ISSUE 2 FIX: Use RiskUiController if available
        if (riskUiController != null) {
            riskUiController.reset();
            hideRewritePanel();
            currentLabel = "SAFE";
            currentScore = 0.0;
            return;
        }
        
        // Fallback to direct manipulation
        if (riskBadge != null) {
            riskBadge.setText("SAFE");
            riskBadge.setTextColor(0xFF00E5C4);
            riskBadge.setBackgroundResource(R.drawable.pill_safe_premium);
        }
        if (riskScore != null) {
            riskScore.setText("0.00");
        }
        // REMOVED: rewriteButton visibility - using bannerCtaButton only
        hideRewritePanel();
        hideBanner();
        currentLabel = "SAFE";
        currentScore = 0.0;
    }

    private void resetState() {
        currentText = new StringBuilder();
        // Cancel any pending API calls
        if (currentDetectTask != null) {
            currentDetectTask.cancel(true);
        }
        if (currentRewriteTask != null) {
            currentRewriteTask.cancel(true);
        }
        // Cancel pending UI updates
        if (riskUiController != null) {
            riskUiController.cancelPendingUpdate();
        }
        resetBadge();
    }

    // ==================== LIFECYCLE ====================

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        try {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                android.view.inputmethod.ExtractedTextRequest req =
                    new android.view.inputmethod.ExtractedTextRequest();
                android.view.inputmethod.ExtractedText et = ic.getExtractedText(req, 0);
                if (et != null && et.text != null) {
                    currentText = new StringBuilder(et.text.toString());
                } else {
                    currentText = new StringBuilder();
                }
            } else {
                currentText = new StringBuilder();
            }
        } catch (Exception e) {
            currentText = new StringBuilder();
        }
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        resetState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Cancel in-flight tasks
        if (currentDetectTask != null) {
            currentDetectTask.cancel(true);
        }
        if (currentRewriteTask != null) {
            currentRewriteTask.cancel(true);
        }
        
        // Shutdown executor
        if (executor != null) {
            executor.shutdownNow();
        }
        
        // Cancel pending UI updates
        if (riskUiController != null) {
            riskUiController.cancelPendingUpdate();
        }
        
        // Clear suggestion history
        if (suggestionEngine != null) {
            suggestionEngine.clearHistory();
        }
        
        stopContinuousBackspace();
    }
}
