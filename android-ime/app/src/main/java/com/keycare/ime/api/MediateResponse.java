package com.keycare.ime.api;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data model for the /mediate API response.
 * Matches the backend-api contract.
 * 
 * Response JSON:
 * {
 *   "risk_level": "safe | harmful | dangerous",
 *   "why": "short one-line explanation",
 *   "rewrite": "rewritten respectful message",
 *   "language": "en | fr | ar | darija"
 * }
 */
public class MediateResponse {
    
    /**
     * Risk levels returned by the API.
     */
    public enum RiskLevel {
        SAFE("safe"),
        HARMFUL("harmful"),
        DANGEROUS("dangerous");
        
        private final String value;
        
        RiskLevel(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static RiskLevel fromString(String value) {
            if (value == null) return SAFE;
            switch (value.toLowerCase()) {
                case "harmful":
                    return HARMFUL;
                case "dangerous":
                    return DANGEROUS;
                case "safe":
                default:
                    return SAFE;
            }
        }
        
        /**
         * Map to UI display text.
         */
        public String getDisplayText() {
            switch (this) {
                case HARMFUL:
                    return "RISKY";
                case DANGEROUS:
                    return "DANGER";
                case SAFE:
                default:
                    return "SAFE";
            }
        }
        
        /**
         * Map to numeric score for UI compatibility.
         */
        public double toScore() {
            switch (this) {
                case DANGEROUS:
                    return 0.9;
                case HARMFUL:
                    return 0.6;
                case SAFE:
                default:
                    return 0.1;
            }
        }
    }
    
    private RiskLevel riskLevel;
    private String why;
    private String rewrite;
    private String language;
    
    public MediateResponse() {
        this.riskLevel = RiskLevel.SAFE;
        this.why = "";
        this.rewrite = "";
        this.language = "en";
    }
    
    public MediateResponse(RiskLevel riskLevel, String why, String rewrite, String language) {
        this.riskLevel = riskLevel;
        this.why = why;
        this.rewrite = rewrite;
        this.language = language;
    }
    
    // Getters
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getWhy() { return why; }
    public String getRewrite() { return rewrite; }
    public String getLanguage() { return language; }
    
    // Setters
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public void setWhy(String why) { this.why = why; }
    public void setRewrite(String rewrite) { this.rewrite = rewrite; }
    public void setLanguage(String language) { this.language = language; }
    
    /**
     * Parse response from JSON string.
     */
    public static MediateResponse fromJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        return fromJson(json);
    }
    
    /**
     * Parse response from JSONObject.
     */
    public static MediateResponse fromJson(JSONObject json) {
        MediateResponse response = new MediateResponse();
        
        response.riskLevel = RiskLevel.fromString(json.optString("risk_level", "safe"));
        response.why = json.optString("why", "");
        response.rewrite = json.optString("rewrite", "");
        response.language = json.optString("language", "en");
        
        return response;
    }
    
    /**
     * Check if this response has a valid rewrite suggestion.
     */
    public boolean hasRewrite() {
        return rewrite != null && !rewrite.isEmpty();
    }
    
    /**
     * Check if the message needs mediation (not safe).
     */
    public boolean needsMediation() {
        return riskLevel != RiskLevel.SAFE;
    }
    
    @Override
    public String toString() {
        return "MediateResponse{" +
                "riskLevel=" + riskLevel +
                ", why='" + why + '\'' +
                ", rewrite='" + rewrite + '\'' +
                ", language='" + language + '\'' +
                '}';
    }
}
