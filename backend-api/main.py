"""
KeyCare-Gemini3 Backend API
===========================
FastAPI server that provides AI-powered communication mediation using Gemini 3.
"""

import os
import json
import httpx
import re
from typing import Literal
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# Load environment variables
load_dotenv()

# ============================================
# Configuration
# ============================================
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "*").split(",")
DEBUG = os.getenv("DEBUG", "false").lower() == "true"

# ============================================
# FastAPI App
# ============================================
app = FastAPI(
    title="KeyCare-Gemini3 API",
    description="AI-powered communication mediation using Gemini 3",
    version="1.0.0",
)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ============================================
# Request/Response Models
# ============================================
class MediationRequest(BaseModel):
    """Request body for the /mediate endpoint."""
    text: str = Field(..., min_length=1, max_length=2000, description="The text to analyze and mediate")
    tone: Literal["calm", "friendly", "professional"] = Field(
        default="calm", 
        description="Desired tone for the rewritten message"
    )
    lang_hint: Literal["auto", "en", "fr", "ar", "darija"] = Field(
        default="auto",
        description="Language hint for better detection (auto = let Gemini detect)"
    )


class MediationResponse(BaseModel):
    """Response body from the /mediate endpoint."""
    risk_level: Literal["safe", "harmful", "dangerous"] = Field(
        ..., 
        description="Risk assessment of the original message"
    )
    why: str = Field(
        ..., 
        description="Brief explanation of the risk assessment"
    )
    rewrite: str = Field(
        ..., 
        description="Rewritten message in a respectful tone"
    )
    language: str = Field(
        ..., 
        description="Detected or specified language of the message"
    )


class HealthResponse(BaseModel):
    """Response body for the /health endpoint."""
    status: str
    version: str
    gemini_configured: bool


# ============================================
# Gemini 3 Integration
# ============================================
async def call_gemini_mediation(text: str, tone: str, lang_hint: str) -> dict:
    """
    Call Gemini API to perform communication mediation.
    
    Args:
        text: The user's original message to analyze
        tone: Desired tone for rewrite (calm/friendly/professional)
        lang_hint: Language hint (auto/en/fr/ar/darija)
    
    Returns:
        dict with keys: risk_level, why, rewrite, language
    """
    
    # If no API key, use fallback heuristics
    if not GEMINI_API_KEY:
        return fallback_mediation(text, tone, lang_hint)
    
    # Build the prompt
    system_prompt = build_system_prompt(tone, lang_hint)
    user_message = f'Analyze this message: "{text}"'
    
    # Gemini API endpoint
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"
    
    payload = {
        "contents": [
            {
                "parts": [
                    {"text": system_prompt},
                    {"text": user_message}
                ]
            }
        ],
        "generationConfig": {
            "temperature": 0.7,
            "topP": 0.9,
            "maxOutputTokens": 500,
            "responseMimeType": "application/json"
        },
        "safetySettings": [
            {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_MEDIUM_AND_ABOVE"},
            {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"}
        ]
    }
    
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(
                url,
                json=payload,
                params={"key": GEMINI_API_KEY},
                headers={"Content-Type": "application/json"}
            )
            
            if response.status_code != 200:
                if DEBUG:
                    print(f"Gemini API error: {response.status_code} - {response.text}")
                return fallback_mediation(text, tone, lang_hint)
            
            data = response.json()
            
            # Extract text from Gemini response
            candidates = data.get("candidates", [])
            if not candidates:
                return fallback_mediation(text, tone, lang_hint)
            
            content = candidates[0].get("content", {})
            parts = content.get("parts", [])
            if not parts:
                return fallback_mediation(text, tone, lang_hint)
            
            response_text = parts[0].get("text", "")
            
            # Parse JSON response from Gemini
            result = parse_gemini_json(response_text)
            
            if result:
                return result
            else:
                return fallback_mediation(text, tone, lang_hint)
                
    except httpx.TimeoutException:
        if DEBUG:
            print("Gemini API timeout")
        return fallback_mediation(text, tone, lang_hint)
    except Exception as e:
        if DEBUG:
            print(f"Gemini API exception: {e}")
        return fallback_mediation(text, tone, lang_hint)


def parse_gemini_json(response_text: str) -> dict | None:
    """
    Parse JSON from Gemini response.
    Handles cases where response might be wrapped in markdown code blocks.
    """
    try:
        # Try direct JSON parse first
        return json.loads(response_text)
    except json.JSONDecodeError:
        pass
    
    # Try to extract JSON from markdown code block
    json_match = re.search(r'```(?:json)?\s*([\s\S]*?)\s*```', response_text)
    if json_match:
        try:
            return json.loads(json_match.group(1))
        except json.JSONDecodeError:
            pass
    
    # Try to find JSON object in text
    json_match = re.search(r'\{[\s\S]*\}', response_text)
    if json_match:
        try:
            return json.loads(json_match.group(0))
        except json.JSONDecodeError:
            pass
    
    return None


def fallback_mediation(text: str, tone: str, lang_hint: str) -> dict:
    """
    Fallback heuristic-based mediation when Gemini API is unavailable.
    """
    # Harmful word patterns for multiple languages
    harmful_patterns = {
        "en": ["idiot", "stupid", "hate", "ugly", "dumb", "shut up", "loser"],
        "fr": ["idiot", "stupide", "déteste", "nul", "tais-toi", "con"],
        "ar": ["غبي", "أحمق", "أكره", "اخرس"],
        "darija": ["hmar", "7mar", "zebi", "m3a9"]
    }
    
    dangerous_patterns = {
        "en": ["kill", "die", "threat", "hurt", "attack", "destroy"],
        "fr": ["tuer", "mort", "menace", "blesser", "attaquer"],
        "ar": ["قتل", "موت", "تهديد"],
        "darija": ["n9tel", "mout"]
    }
    
    text_lower = text.lower()
    
    # Check for dangerous content
    for patterns in dangerous_patterns.values():
        if any(word in text_lower for word in patterns):
            return {
                "risk_level": "dangerous",
                "why": "Message contains potentially threatening language.",
                "rewrite": "I'm feeling very frustrated and need to express my concerns respectfully.",
                "language": detect_language(text, lang_hint)
            }
    
    # Check for harmful content
    for patterns in harmful_patterns.values():
        if any(word in text_lower for word in patterns):
            return {
                "risk_level": "harmful",
                "why": "Message contains language that could hurt someone.",
                "rewrite": f"I'd like to express my feelings about this situation.",
                "language": detect_language(text, lang_hint)
            }
    
    # Safe message
    return {
        "risk_level": "safe",
        "why": "Message appears respectful and constructive.",
        "rewrite": text,
        "language": detect_language(text, lang_hint)
    }


def detect_language(text: str, lang_hint: str) -> str:
    """Detect language from text or use hint."""
    if lang_hint != "auto":
        return lang_hint
    
    # Arabic Unicode range detection
    if any(ord(c) > 1536 and ord(c) < 1791 for c in text):
        # Check for Darija patterns (Latin transliteration mixed or specific words)
        darija_markers = ["hna", "dyal", "wach", "kifach", "3", "7", "9"]
        if any(marker in text.lower() for marker in darija_markers):
            return "darija"
        return "ar"
    
    # French accented characters
    if any(c in "éèêëàâäùûüôöîïç" for c in text.lower()):
        return "fr"
    
    return "en"


def build_system_prompt(tone: str, lang_hint: str) -> str:
    """
    Build the system prompt for Gemini 3.
    
    This is the official KeyCare mediation prompt that instructs Gemini
    to analyze messages and provide respectful rewrites.
    """
    return f"""You are KeyCare, a real-time communication mediation assistant inside an Android keyboard.

Task:
Given a user message, detect communication risk and propose a respectful rewrite that preserves intent.

Return ONLY valid JSON (no markdown, no extra text). Use this exact schema:
{{
  "risk_level": "safe" | "harmful" | "dangerous",
  "why": "one short sentence explaining the risk",
  "rewrite": "rewritten message in the SAME language as the input",
  "language": "en" | "fr" | "ar" | "darija"
}}

Rules:
- The user stays in control. Do not refuse unless the content is clearly dangerous.
- If the message is "safe", still return a slightly improved rewrite (optional), but keep it close to original.
- If the input is Arabic dialect (Darija), set language="darija" and rewrite in Darija (Arabic script or Latin mix allowed).
- Keep the rewrite concise and natural. No lecturing.
- If the message includes direct threats or explicit violent intent, set risk_level="dangerous" and rewrite to a non-threatening de-escalation statement.
- Do not include personal data. Do not add new facts.
- Output must be strictly valid JSON.

Inputs:
text: "{{{{TEXT}}}}"
tone: "{tone}"   (one of: calm, friendly, professional)
lang_hint: "{lang_hint}" (auto, en, fr, ar, darija)

Tone control:
- calm: de-escalate and soften
- friendly: warm and polite
- professional: formal and respectful"""


# ============================================
# API Endpoints
# ============================================
@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health_check():
    """
    Health check endpoint.
    
    Returns the API status and whether Gemini is configured.
    """
    return HealthResponse(
        status="healthy",
        version="1.0.0",
        gemini_configured=bool(GEMINI_API_KEY)
    )


@app.post("/mediate", response_model=MediationResponse, tags=["Mediation"])
async def mediate_message(request: MediationRequest):
    """
    Analyze and mediate a message using Gemini 3.
    
    This endpoint:
    - Assesses the communication risk (safe/harmful/dangerous)
    - Provides a brief explanation
    - Rewrites the message in a respectful tone
    - Detects the language
    """
    if not GEMINI_API_KEY:
        # In production, you might want to return an error
        # For hackathon demo, we'll use the placeholder response
        if DEBUG:
            print("Warning: GEMINI_API_KEY not configured, using fallback heuristics")
    
    try:
        result = await call_gemini_mediation(
            text=request.text,
            tone=request.tone,
            lang_hint=request.lang_hint
        )
        
        return MediationResponse(
            risk_level=result["risk_level"],
            why=result["why"],
            rewrite=result["rewrite"],
            language=result["language"]
        )
    
    except Exception as e:
        if DEBUG:
            raise HTTPException(status_code=500, detail=str(e))
        raise HTTPException(
            status_code=500, 
            detail="An error occurred while processing your request"
        )


# ============================================
# Main Entry Point
# ============================================
if __name__ == "__main__":
    import uvicorn
    
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", 8000))
    
    print(f"🚀 Starting KeyCare-Gemini3 API on {host}:{port}")
    print(f"📚 Docs available at http://{host}:{port}/docs")
    
    uvicorn.run(app, host=host, port=port, reload=DEBUG)
