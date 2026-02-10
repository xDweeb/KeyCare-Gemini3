package com.keycare.ime;

/**
 * API Configuration - Internal use only.
 * This class contains hardcoded API settings that are NOT exposed to users.
 * 
 * IMPORTANT: The API URL is hardcoded and cannot be changed by users.
 * This ensures security and prevents potential misuse.
 */
public final class ApiConfig {
    
    private ApiConfig() {
        // Prevent instantiation
    }
    
    /**
     * Production API base URL (Heroku).
     * IMPORTANT: This is hardcoded and NOT configurable by users.
     * Uses HTTPS for secure communication.
     */
    public static final String BASE_URL = "https://keycare-gemini3-api-2587283546dc.herokuapp.com";
    
    /**
     * API Endpoints
     */
    public static final String ENDPOINT_HEALTH = "/health";
    public static final String ENDPOINT_MEDIATE = "/mediate";
    public static final String ENDPOINT_DETECT = "/mediate";  // Legacy alias
    public static final String ENDPOINT_REWRITE = "/mediate"; // Legacy alias
    
    /**
     * Timeouts (milliseconds)
     * - CONNECT_TIMEOUT: Time to establish connection (handles slow networks)
     * - READ_TIMEOUT: Time to wait for response data (handles slow server responses)
     */
    public static final int CONNECT_TIMEOUT = 8000;  // 8 seconds
    public static final int READ_TIMEOUT = 12000;    // 12 seconds
    
    /**
     * Retry configuration
     * - MAX_RETRIES: Number of retry attempts on failure
     * - RETRY_DELAY_MS: Wait time between retries
     * - HEALTH_CHECK_INTERVAL_MS: Background health check frequency
     */
    public static final int MAX_RETRIES = 2;
    public static final long RETRY_DELAY_MS = 1000;  // 1 second
    public static final long HEALTH_CHECK_INTERVAL_MS = 30000; // 30 seconds
}
