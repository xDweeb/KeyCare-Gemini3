package com.keycare.ime.api;

/**
 * Data model for the /mediate API request.
 * Matches the backend-api contract.
 */
public class MediateRequest {
    
    private String text;
    private String tone;
    private String lang_hint;
    
    public MediateRequest(String text, String tone, String langHint) {
        this.text = text;
        this.tone = tone;
        this.lang_hint = langHint;
    }
    
    // Getters
    public String getText() { return text; }
    public String getTone() { return tone; }
    public String getLangHint() { return lang_hint; }
    
    // Setters
    public void setText(String text) { this.text = text; }
    public void setTone(String tone) { this.tone = tone; }
    public void setLangHint(String langHint) { this.lang_hint = langHint; }
    
    /**
     * Convert to JSON string for API call.
     */
    public String toJson() {
        return "{"
            + "\"text\":\"" + escapeJson(text) + "\","
            + "\"tone\":\"" + tone + "\","
            + "\"lang_hint\":\"" + lang_hint + "\""
            + "}";
    }
    
    /**
     * Escape special characters for JSON.
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    /**
     * Builder for creating MediateRequest with defaults.
     */
    public static class Builder {
        private String text = "";
        private String tone = "calm";
        private String langHint = "auto";
        
        public Builder text(String text) {
            this.text = text;
            return this;
        }
        
        public Builder tone(String tone) {
            this.tone = tone;
            return this;
        }
        
        public Builder langHint(String langHint) {
            this.langHint = langHint;
            return this;
        }
        
        public MediateRequest build() {
            return new MediateRequest(text, tone, langHint);
        }
    }
}
