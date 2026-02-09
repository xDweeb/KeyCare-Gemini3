package com.keycare.ime;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AiStatusManager - Monitors AI service availability in background.
 * Provides status updates without user intervention.
 */
public class AiStatusManager {
    
    private static final String TAG = "AiStatusManager";
    
    public enum Status {
        ONLINE,     // API is reachable
        OFFLINE,    // API is not reachable
        CHECKING    // Currently checking status
    }
    
    public interface StatusListener {
        void onStatusChanged(Status status);
    }
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler checkHandler = new Handler(Looper.getMainLooper());
    
    private StatusListener listener;
    private Status currentStatus = Status.CHECKING;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private Runnable periodicCheck;
    
    public AiStatusManager() {
        periodicCheck = new Runnable() {
            @Override
            public void run() {
                if (isRunning.get()) {
                    checkHealth();
                    checkHandler.postDelayed(this, ApiConfig.HEALTH_CHECK_INTERVAL_MS);
                }
            }
        };
    }
    
    /**
     * Set status listener
     */
    public void setStatusListener(StatusListener listener) {
        this.listener = listener;
    }
    
    /**
     * Start monitoring (call when keyboard opens)
     */
    public void startMonitoring() {
        if (isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Starting AI status monitoring");
            checkHandler.post(periodicCheck);
        }
    }
    
    /**
     * Stop monitoring (call when keyboard closes)
     */
    public void stopMonitoring() {
        if (isRunning.compareAndSet(true, false)) {
            Log.d(TAG, "Stopping AI status monitoring");
            checkHandler.removeCallbacks(periodicCheck);
        }
    }
    
    /**
     * Get current status
     */
    public Status getCurrentStatus() {
        return currentStatus;
    }
    
    /**
     * Check if AI is online
     */
    public boolean isOnline() {
        return currentStatus == Status.ONLINE;
    }
    
    /**
     * Force immediate health check
     */
    public void checkNow() {
        checkHealth();
    }
    
    /**
     * Perform health check in background
     */
    private void checkHealth() {
        executor.execute(() -> {
            boolean online = false;
            
            try {
                URL url = new URL(ApiConfig.BASE_URL + ApiConfig.ENDPOINT_HEALTH);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT);
                conn.setReadTimeout(ApiConfig.READ_TIMEOUT);
                conn.setRequestMethod("GET");
                
                int responseCode = conn.getResponseCode();
                online = (responseCode == 200);
                
                conn.disconnect();
            } catch (Exception e) {
                Log.d(TAG, "Health check failed: " + e.getMessage());
                online = false;
            }
            
            final Status newStatus = online ? Status.ONLINE : Status.OFFLINE;
            
            if (newStatus != currentStatus) {
                currentStatus = newStatus;
                Log.d(TAG, "AI status changed: " + newStatus);
                
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onStatusChanged(newStatus);
                    }
                });
            }
        });
    }
    
    /**
     * Shutdown executor
     */
    public void shutdown() {
        stopMonitoring();
        executor.shutdown();
    }
}
