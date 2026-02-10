package com.keycare.ime.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * MediationApiClient - Handles API calls to the /mediate endpoint using OkHttp.
 * 
 * Features:
 * - Async API calls with callbacks (NO main thread blocking)
 * - Uses OkHttp for efficient HTTP handling
 * - Request cancellation support
 * - Timeout handling (8s connect, 15s read for Gemini latency)
 * - Debounce built-in (800ms)
 * - Robust error handling with graceful degradation
 * - NO API keys stored in the app (all auth is server-side)
 */
public class MediationApiClient {
    
    private static final String TAG = "KEYCARE_MEDIATION";
    
    // API Configuration - Heroku backend (handles Gemini API calls server-side)
    private static final String BASE_URL = "https://keycare-gemini3-api-2587283546dc.herokuapp.com";
    private static final String ENDPOINT_MEDIATE = "/mediate";
    
    // Timeouts (Gemini can take time to respond)
    private static final int CONNECT_TIMEOUT_SECONDS = 8;
    private static final int READ_TIMEOUT_SECONDS = 15;
    private static final int WRITE_TIMEOUT_SECONDS = 10;
    
    // Debounce configuration (800ms as requested)
    private static final long DEBOUNCE_DELAY_MS = 800;
    
    // Media type for JSON requests
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    // OkHttp client (singleton, reused for connection pooling)
    private final OkHttpClient client;
    
    // Handler for main thread callbacks
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Handler for debouncing
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    
    // Current request (for cancellation)
    private Call currentCall;
    private Runnable pendingDebounce;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    
    /**
     * Callback interface for mediation results.
     * All callbacks are invoked on the main thread.
     */
    public interface MediationCallback {
        /**
         * Called when mediation succeeds.
         * @param response The parsed mediation response from Gemini
         */
        void onSuccess(MediateResponse response);
        
        /**
         * Called when mediation fails.
         * @param error Human-readable error message
         */
        void onError(String error);
    }
    
    /**
     * Create a new MediationApiClient.
     * Uses OkHttp with configured timeouts for Gemini API latency.
     */
    public MediationApiClient() {
        client = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }
    
    /**
     * Request mediation from the API with debouncing.
     * Call this on every keystroke - debouncing is handled internally.
     * 
     * @param request The mediation request
     * @param callback Callback for results (called on main thread)
     */
    public void requestMediationDebounced(MediateRequest request, MediationCallback callback) {
        // Cancel pending debounce
        if (pendingDebounce != null) {
            debounceHandler.removeCallbacks(pendingDebounce);
        }
        
        // Schedule new debounced call
        pendingDebounce = () -> requestMediation(request, callback);
        debounceHandler.postDelayed(pendingDebounce, DEBOUNCE_DELAY_MS);
    }
    
    /**
     * Request mediation immediately (no debounce).
     * Use this when user presses space/enter or pauses typing.
     * 
     * @param request The mediation request
     * @param callback Callback for results (called on main thread)
     */
    public void requestMediation(MediateRequest request, MediationCallback callback) {
        // Cancel any previous request
        cancelCurrentRequest();
        isCancelled.set(false);
        
        String textPreview = request.getText().length() > 50 
            ? request.getText().substring(0, 50) + "..." 
            : request.getText();
        Log.d(TAG, "Requesting mediation - text: " + textPreview);
        
        // Build request body
        String jsonBody = request.toJson();
        Log.d(TAG, "Request body: " + jsonBody);
        
        RequestBody body = RequestBody.create(jsonBody, JSON);
        
        Request httpRequest = new Request.Builder()
            .url(BASE_URL + ENDPOINT_MEDIATE)
            .post(body)
            .addHeader("Accept", "application/json")
            .build();
        
        // Make async request
        currentCall = client.newCall(httpRequest);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (isCancelled.get() || call.isCanceled()) {
                    Log.d(TAG, "Request cancelled");
                    return;
                }
                
                String error;
                if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                    error = "Request timeout - Gemini is taking longer than usual";
                    Log.e(TAG, "Request timeout", e);
                } else if (e.getMessage() != null && e.getMessage().contains("Unable to resolve host")) {
                    error = "No network connection";
                    Log.e(TAG, "No network", e);
                } else {
                    error = "Network error: " + e.getMessage();
                    Log.e(TAG, "Network error", e);
                }
                
                final String finalError = error;
                mainHandler.post(() -> {
                    if (!isCancelled.get()) {
                        callback.onError(finalError);
                    }
                });
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (isCancelled.get() || call.isCanceled()) {
                    Log.d(TAG, "Request cancelled after response");
                    response.close();
                    return;
                }
                
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBody = responseBody != null ? responseBody.string() : "Unknown error";
                        Log.e(TAG, "API error: " + response.code() + " - " + errorBody);
                        
                        final String error = "API error: " + response.code();
                        mainHandler.post(() -> {
                            if (!isCancelled.get()) {
                                callback.onError(error);
                            }
                        });
                        return;
                    }
                    
                    if (responseBody == null) {
                        Log.e(TAG, "Empty response body");
                        mainHandler.post(() -> {
                            if (!isCancelled.get()) {
                                callback.onError("Empty response from server");
                            }
                        });
                        return;
                    }
                    
                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Response: " + jsonResponse);
                    
                    // Parse response
                    MediateResponse mediateResponse = MediateResponse.fromJson(jsonResponse);
                    
                    // Callback on main thread
                    mainHandler.post(() -> {
                        if (!isCancelled.get()) {
                            callback.onSuccess(mediateResponse);
                        }
                    });
                    
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parse error", e);
                    mainHandler.post(() -> {
                        if (!isCancelled.get()) {
                            callback.onError("Invalid response format");
                        }
                    });
                } catch (IOException e) {
                    Log.e(TAG, "IO error reading response", e);
                    mainHandler.post(() -> {
                        if (!isCancelled.get()) {
                            callback.onError("Error reading response");
                        }
                    });
                }
            }
        });
    }
    
    /**
     * Cancel the current request and any pending debounced requests.
     * Safe to call multiple times.
     */
    public void cancelCurrentRequest() {
        isCancelled.set(true);
        
        // Cancel debounced request
        if (pendingDebounce != null) {
            debounceHandler.removeCallbacks(pendingDebounce);
            pendingDebounce = null;
        }
        
        // Cancel in-flight request
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            Log.d(TAG, "Cancelled current request");
        }
    }
    
    /**
     * Trigger mediation immediately (e.g., on space/enter).
     * Cancels any pending debounce and makes request immediately.
     */
    public void triggerImmediate(MediateRequest request, MediationCallback callback) {
        // Cancel any pending debounce
        if (pendingDebounce != null) {
            debounceHandler.removeCallbacks(pendingDebounce);
            pendingDebounce = null;
        }
        
        // Make immediate request
        requestMediation(request, callback);
    }
    
    /**
     * Check API health.
     * Useful for setup/onboarding to verify connectivity.
     */
    public void checkHealth(HealthCallback callback) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/health")
            .get()
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Health check failed", e);
                mainHandler.post(() -> callback.onResult(false));
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                boolean healthy = response.isSuccessful();
                response.close();
                mainHandler.post(() -> callback.onResult(healthy));
            }
        });
    }
    
    /**
     * Callback interface for health check results.
     */
    public interface HealthCallback {
        void onResult(boolean healthy);
    }
}
