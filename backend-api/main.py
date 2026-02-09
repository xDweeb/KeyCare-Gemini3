"""
KeyCare-Gemini3 Backend API
===========================
FastAPI server that provides AI-powered communication mediation using Gemini 3.
"""

import os
import json
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
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3-pro")
ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "http://localhost:3000").split(",")
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
def call_gemini_mediation(text: str, tone: str, lang_hint: str) -> dict:
    """
    Call Gemini 3 API to perform communication mediation.
    
    TODO: Implement actual Gemini 3 API call here.
    
    Args:
        text: The user's original message to analyze
        tone: Desired tone for rewrite (calm/friendly/professional)
        lang_hint: Language hint (auto/en/fr/ar/darija)
    
    Returns:
        dict with keys: risk_level, why, rewrite, language
    """
    
    # =====================================================
    # TODO: IMPLEMENT GEMINI 3 API CALL
    # =====================================================
    # 
    # Option 1: Using the official Google Generative AI SDK
    # -----------------------------------------------------
    # import google.generativeai as genai
    # 
    # genai.configure(api_key=GEMINI_API_KEY)
    # model = genai.GenerativeModel(GEMINI_MODEL)
    # 
    # system_prompt = build_system_prompt(tone, lang_hint)
    # response = model.generate_content(
    #     [system_prompt, f"Analyze this message: {text}"],
    #     generation_config={
    #         "response_mime_type": "application/json",
    #         "response_schema": {
    #             "type": "object",
    #             "properties": {
    #                 "risk_level": {"type": "string", "enum": ["safe", "harmful", "dangerous"]},
    #                 "why": {"type": "string"},
    #                 "rewrite": {"type": "string"},
    #                 "language": {"type": "string"}
    #             },
    #             "required": ["risk_level", "why", "rewrite", "language"]
    #         }
    #     }
    # )
    # return json.loads(response.text)
    #
    # Option 2: Using httpx for REST API calls
    # -----------------------------------------
    # import httpx
    # 
    # url = f"https://generativelanguage.googleapis.com/v1/models/{GEMINI_MODEL}:generateContent"
    # headers = {"Content-Type": "application/json"}
    # params = {"key": GEMINI_API_KEY}
    # payload = {
    #     "contents": [{"parts": [{"text": build_prompt(text, tone, lang_hint)}]}],
    #     "generationConfig": {"responseMimeType": "application/json"}
    # }
    # 
    # response = httpx.post(url, json=payload, headers=headers, params=params)
    # response.raise_for_status()
    # return parse_gemini_response(response.json())
    #
    # =====================================================
    
    # PLACEHOLDER: Return mock response for development/testing
    # Remove this once Gemini 3 API is integrated
    
    # Simple heuristic for demo purposes
    harmful_words = ["idiot", "stupid", "hate", "kill", "die", "ugly", "dumb"]
    dangerous_words = ["threat", "hurt", "attack", "destroy"]
    
    text_lower = text.lower()
    
    if any(word in text_lower for word in dangerous_words):
        risk_level = "dangerous"
        why = "Message contains potentially threatening language."
        rewrite = "I'm feeling very frustrated and need to express my concerns."
    elif any(word in text_lower for word in harmful_words):
        risk_level = "harmful"
        why = "Message contains language that could be hurtful or escalate conflict."
        rewrite = f"I'd like to express my feelings in a more {tone} way about this situation."
    else:
        risk_level = "safe"
        why = "Message appears respectful and constructive."
        rewrite = text  # No rewrite needed
    
    # Detect language (placeholder logic)
    detected_lang = lang_hint if lang_hint != "auto" else "en"
    if any(ord(c) > 1536 and ord(c) < 1791 for c in text):  # Arabic Unicode range
        detected_lang = "ar"
    elif any(c in "éèêëàâäùûüôöîïç" for c in text.lower()):
        detected_lang = "fr"
    
    return {
        "risk_level": risk_level,
        "why": why,
        "rewrite": rewrite,
        "language": detected_lang
    }


def build_system_prompt(tone: str, lang_hint: str) -> str:
    """
    Build the system prompt for Gemini 3.
    
    This prompt instructs Gemini to act as a communication mediator.
    """
    lang_instruction = ""
    if lang_hint != "auto":
        lang_map = {
            "en": "English",
            "fr": "French",
            "ar": "Arabic (Modern Standard Arabic)",
            "darija": "Moroccan Arabic (Darija)"
        }
        lang_instruction = f"The user's language is likely {lang_map.get(lang_hint, lang_hint)}. "
    
    return f"""You are a communication mediator AI. Your job is to analyze messages and help users communicate more respectfully.

{lang_instruction}

For each message, you must:
1. Assess the risk level:
   - "safe": Message is respectful and constructive
   - "harmful": Message contains insults, passive aggression, or could hurt someone
   - "dangerous": Message contains threats, extreme hostility, or could cause harm

2. Provide a brief 1-line explanation of your assessment

3. Rewrite the message in a {tone} tone that:
   - Preserves the user's core intent
   - Removes harmful language
   - Is constructive and respectful
   - Maintains the same language as the original

4. Detect the language used

Respond ONLY with valid JSON in this exact format:
{{
    "risk_level": "safe|harmful|dangerous",
    "why": "Brief explanation",
    "rewrite": "The rewritten message",
    "language": "en|fr|ar|darija"
}}"""


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
            print("Warning: GEMINI_API_KEY not configured, using placeholder response")
    
    try:
        result = call_gemini_mediation(
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
