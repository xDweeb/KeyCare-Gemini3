package com.keycare.ime;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RewriteApiClient - Handles API calls to /rewrite endpoint.
 * Features:
 * - Async API calls with callbacks (NO main thread blocking)
 * - Timeout handling with configurable timeouts
 * - Automatic fallback to SuggestionEngine on failure
 * - Request cancellation support
 * - Retry logic (max 1 retry)
 * - Robust error handling - never crashes
 */
public class RewriteApiClient {

    private static final String TAG = "KEYCARE_REWRITE";
    private static final String TAG_API = "KEYCARE_API";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SuggestionEngine fallbackEngine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Future<?> currentTask;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public RewriteApiClient(SuggestionEngine fallbackEngine) {
        this.fallbackEngine = fallbackEngine;
    }

    /**
     * @deprecated Use constructor without baseUrl - URL is now hardcoded in ApiConfig
     */
    @Deprecated
    public RewriteApiClient(String baseUrl, SuggestionEngine fallbackEngine) {
        this.fallbackEngine = fallbackEngine;
    }

    /**
     * @deprecated URL is now hardcoded in ApiConfig
     */
    @Deprecated
    public void setBaseUrl(String baseUrl) {
        // No-op - URL is hardcoded
    }

    /**
     * Suggestion data class
     */
    public static class Suggestion {
        public final String text;
        public final String reason;
        public final String source;

        public Suggestion(String text, String reason) {
            this.text = text;
            this.reason = reason;
            this.source = "local";
        }

        public Suggestion(String text, String reason, String source) {
            this.text = text;
            this.reason = reason;
            this.source = source;
        }
    }

    /**
     * Callback interface for rewrite results
     */
    public interface RewriteCallback {
        void onSuccess(List<Suggestion> suggestions);
        void onError(String error, List<Suggestion> fallbackSuggestions);
    }

    /**
     * Request rewrite suggestions from API.
     * Falls back to local suggestions on failure.
     * SAFE: All operations run off main thread, callbacks run on main thread.
     */
    public void requestRewrite(String text, String lang, String tone, 
                               String riskLabel, double riskScore,
                               RewriteCallback callback) {
        // Cancel any existing request
        cancelCurrentRequest();
        isCancelled.set(false);
        
        Log.d(TAG, "Requesting rewrite - text length: " + text.length() + ", lang: " + lang + ", tone: " + tone);

        currentTask = executor.submit(() -> {
            try {
                if (isCancelled.get()) {
                    Log.d(TAG, "Request cancelled before execution");
                    return;
                }
                
                // Try API call with retry
                List<Suggestion> suggestions = null;
                Exception lastError = null;
                
                for (int attempt = 0; attempt <= 1; attempt++) { // Max 1 retry
                    if (isCancelled.get()) {
                        Log.d(TAG, "Request cancelled during retry loop");
                        return;
                    }
                    
                    try {
                        Log.d(TAG_API, "API attempt " + (attempt + 1));
                        suggestions = callRewriteApi(text, lang, tone, riskLabel, riskScore);
                        break; // Success, exit retry loop
                    } catch (Exception e) {
                        lastError = e;
                        Log.w(TAG_API, "API attempt " + (attempt + 1) + " failed: " + e.getMessage());
                        
                        if (attempt == 0 && !isCancelled.get()) {
                            // Wait before retry
                            try {
                                Thread.sleep(ApiConfig.RETRY_DELAY_MS);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                }
                
                if (isCancelled.get()) {
                    Log.d(TAG, "Request cancelled after API attempts");
                    return;
                }
                
                if (suggestions != null && !suggestions.isEmpty()) {
                    Log.d(TAG, "API success - got " + suggestions.size() + " suggestions");
                    final List<Suggestion> finalSuggestions = suggestions;
                    mainHandler.post(() -> {
                        try {
                            callback.onSuccess(finalSuggestions);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in onSuccess callback: " + e.getMessage());
                        }
                    });
                } else {
                    // Fallback to local suggestions
                    Log.d(TAG, "Using fallback suggestions due to: " + 
                          (lastError != null ? lastError.getMessage() : "empty response"));
                    List<Suggestion> fallback = generateFallbackSuggestions(text, lang, tone);
                    String errorMsg = getErrorMessage(lastError);
                    
                    mainHandler.post(() -> {
                        try {
                            callback.onError(errorMsg, fallback);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in onError callback: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                // Ultimate safety catch - NEVER crash
                Log.e(TAG, "Unexpected error in rewrite request: " + e.getMessage(), e);
                
                if (!isCancelled.get()) {
                    List<Suggestion> fallback = generateFallbackSuggestions(text, lang, tone);
                    mainHandler.post(() -> {
                        try {
                            callback.onError("Unexpected error", fallback);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error in emergency fallback callback: " + ex.getMessage());
                        }
                    });
                }
            }
        });
    }
    
    /**
     * Get user-friendly error message
     */
    private String getErrorMessage(Exception e) {
        if (e == null) return "Connection issue";
        
        if (e instanceof SocketTimeoutException) {
            return "Request timed out - using offline suggestions";
        } else if (e instanceof UnknownHostException) {
            return "No internet connection - using offline suggestions";
        } else if (e.getMessage() != null && e.getMessage().contains("API error")) {
            return "Server error - using offline suggestions";
        }
        return "Connection issue - using offline suggestions";
    }

    /**
     * Cancel any in-flight request
     */
    public void cancelCurrentRequest() {
        isCancelled.set(true);
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
            Log.d(TAG, "Cancelled current request");
        }
    }

    /**
     * Make actual API call to /rewrite
     * NEVER called on main thread.
     */
    private List<Suggestion> callRewriteApi(String text, String lang, String tone,
                                            String riskLabel, double riskScore) throws Exception {
        String urlStr = ApiConfig.BASE_URL + ApiConfig.ENDPOINT_REWRITE;
        Log.d(TAG_API, "Calling API: " + urlStr);
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT);
            conn.setReadTimeout(ApiConfig.READ_TIMEOUT);
            conn.setDoOutput(true);

            // Build request JSON
            JSONObject json = new JSONObject();
            json.put("text", text);
            json.put("lang", lang);
            json.put("tone", tone);
            json.put("risk_label", riskLabel);
            json.put("risk_score", riskScore);
            
            Log.d(TAG_API, "Request payload: text_len=" + text.length() + ", lang=" + lang + ", tone=" + tone);

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes("UTF-8"));
            }

            // Read response
            int responseCode = conn.getResponseCode();
            Log.d(TAG_API, "Response code: " + responseCode);
            
            if (responseCode == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                Log.d(TAG_API, "Response received, length: " + response.length());
                return parseRewriteResponse(response.toString());
            } else {
                Log.e(TAG_API, "API error response: " + responseCode);
                throw new Exception("API error: " + responseCode);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Parse API response JSON
     */
    private List<Suggestion> parseRewriteResponse(String jsonStr) throws Exception {
        List<Suggestion> suggestions = new ArrayList<>();
        
        try {
            JSONObject json = new JSONObject(jsonStr);

            if (json.has("suggestions")) {
                JSONArray arr = json.getJSONArray("suggestions");
                Log.d(TAG_API, "Parsing " + arr.length() + " suggestions from response");
                
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String text = item.optString("text", "");
                    String reason = item.optString("reason", null);
                    if (!text.isEmpty()) {
                        suggestions.add(new Suggestion(text, reason));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG_API, "Error parsing response: " + e.getMessage());
            throw e;
        }

        if (suggestions.isEmpty()) {
            Log.w(TAG_API, "No suggestions in response");
            throw new Exception("No suggestions in response");
        }
        
        Log.d(TAG_API, "Successfully parsed " + suggestions.size() + " suggestions");
        return suggestions;
    }

    /**
     * Generate fallback suggestions using local SuggestionEngine
     */
    private List<Suggestion> generateFallbackSuggestions(String text, String lang, String tone) {
        List<Suggestion> suggestions = new ArrayList<>();

        if (fallbackEngine != null) {
            // Map tone to suggestion engine's format
            SuggestionEngine.SuggestionResult result = fallbackEngine.generate(
                    text, lang, SuggestionEngine.RiskState.RISKY);

            // Select based on tone
            switch (tone.toLowerCase()) {
                case "calm":
                    suggestions.add(new Suggestion(result.calm, "Calm approach"));
                    suggestions.add(new Suggestion(result.firm, "Clear boundaries"));
                    suggestions.add(new Suggestion(result.educational, "Informative tone"));
                    break;
                case "respectful":
                    suggestions.add(new Suggestion(result.calm, "Respectful tone"));
                    suggestions.add(new Suggestion(result.educational, "Understanding approach"));
                    suggestions.add(new Suggestion(result.firm, "Direct but kind"));
                    break;
                case "professional":
                    suggestions.add(new Suggestion(result.firm, "Professional clarity"));
                    suggestions.add(new Suggestion(result.calm, "Composed response"));
                    suggestions.add(new Suggestion(result.educational, "Constructive feedback"));
                    break;
                default:
                    suggestions.add(new Suggestion(result.calm, "Gentle approach"));
                    suggestions.add(new Suggestion(result.firm, "Clear message"));
                    suggestions.add(new Suggestion(result.educational, "Helpful context"));
            }
        } else {
            // Ultimate fallback
            suggestions.add(new Suggestion(
                    "I'd like to express this more thoughtfully.",
                    "General improvement"));
            suggestions.add(new Suggestion(
                    "Let me rephrase this in a better way.",
                    "Clearer communication"));
            suggestions.add(new Suggestion(
                    "I want to communicate this respectfully.",
                    "Respectful tone"));
        }

        return suggestions;
    }

    /**
     * Shutdown executor
     */
    public void shutdown() {
        executor.shutdown();
    }
}
