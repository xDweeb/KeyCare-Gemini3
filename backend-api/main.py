"""
KeyCare-Gemini3 Backend API
===========================
FastAPI server that provides AI-powered communication mediation using Gemini 3.
"""

import os
import json
import httpx
import re
import logging
from typing import Literal
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# Load environment variables
load_dotenv()

# ============================================
# Logging Configuration
# ============================================
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger("keycare")

# ============================================
# Configuration
# ============================================
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "*").split(",")
DEBUG = os.getenv("DEBUG", "false").lower() == "true"
VERSION = "1.3.0"

# ============================================
# Profanity Lists - ONLY for rewrite validation (NOT for risk detection!)
# Gemini decides risk_level. These lists are ONLY used to validate the rewrite is clean.
# ============================================
REWRITE_BLOCKLIST = {
    # English profanity/slurs (for rewrite validation only)
    "fuck", "shit", "bitch", "ass", "damn", "bastard", "crap", "dick", "cock", 
    "pussy", "whore", "slut", "retard", "fag", "nigger", "cunt",
    # French profanity
    "merde", "putain", "connard", "connasse", "enculé", "salaud", "salope", 
    "bordel", "nique", "foutre",
    # Arabic profanity  
    "زب", "كس", "نيك", "شرموط", "عاهرة", "منيوك", "طيز", "خرا", "قحبة",
    # Darija profanity
    "zebi", "zeb", "kahba", "9a7ba", "nikomok", "lmok", "tboun",
}

# ============================================
# FastAPI App
# ============================================
app = FastAPI(
    title="KeyCare-Gemini3 API",
    description="AI-powered communication mediation using Gemini 3",
    version="1.0.0",
)

# CORS Middleware - Allow all origins for demo
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
    expose_headers=["*"],
    max_age=3600,
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
# Validation & Sanitization Functions
# ============================================
def rewrite_contains_profanity(text: str) -> bool:
    """
    Check if the REWRITE contains profanity. 
    Used ONLY as a post-validation guardrail, NOT for risk detection.
    """
    text_lower = text.lower()
    # Check exact word matches
    words = set(re.findall(r'\b\w+\b', text_lower))
    if words & REWRITE_BLOCKLIST:
        return True
    # Check for Arabic/substring matches
    for word in REWRITE_BLOCKLIST:
        if word in text_lower:
            return True
    return False


def normalize_risk_level(risk: str | None) -> str:
    """Normalize risk_level to lowercase and validate. Default to 'harmful' if invalid."""
    if not risk:
        return "harmful"
    risk_clean = str(risk).strip().lower()
    if risk_clean in ("safe", "harmful", "dangerous"):
        return risk_clean
    return "harmful"


def validate_response_structure(result: dict, original_text: str, tone: str, detected_lang: str) -> dict:
    """
    Validate and normalize the Gemini response structure.
    - Normalize risk_level to lowercase
    - Ensure 'why' is present
    - Ensure 'rewrite' is non-empty
    - Ensure 'language' matches detected language
    
    NOTE: This does NOT override risk_level based on keywords. Gemini decides risk.
    """
    # Normalize risk_level (lowercase, validate)
    risk_level = normalize_risk_level(result.get("risk_level"))
    
    # Validate 'why'
    why = result.get("why", "").strip()
    if not why:
        if risk_level == "safe":
            why = "Message appears respectful and constructive."
        elif risk_level == "harmful":
            why = "Message contains language that could hurt someone."
        else:
            why = "Message contains threatening or dangerous content."
    
    # Validate 'rewrite'
    rewrite = result.get("rewrite", "").strip()
    if not rewrite:
        rewrite = get_generic_rewrite(tone, detected_lang)
    
    # Enforce language from detection
    language = result.get("language", detected_lang).strip().lower()
    if language not in ("en", "fr", "ar", "darija"):
        language = detected_lang
    
    return {
        "risk_level": risk_level,
        "why": why,
        "rewrite": rewrite,
        "language": language
    }


def get_generic_rewrite(tone: str, lang: str) -> str:
    """Get a generic safe rewrite based on tone and language."""
    rewrites = {
        "en": {
            "calm": "I'd like to express my feelings about this calmly.",
            "friendly": "Hey, I wanted to share my thoughts on this.",
            "professional": "I would like to address this matter respectfully."
        },
        "fr": {
            "calm": "J'aimerais exprimer mes sentiments calmement.",
            "friendly": "Salut, je voulais partager mon avis là-dessus.",
            "professional": "Je souhaiterais aborder ce sujet avec respect."
        },
        "ar": {
            "calm": "أود أن أعبر عن مشاعري بهدوء.",
            "friendly": "مرحباً، أردت مشاركة رأيي في هذا.",
            "professional": "أود معالجة هذا الموضوع باحترام."
        },
        "darija": {
            "calm": "بغيت نعبر على رأيي بالهدوء.",
            "friendly": "أهلا، بغيت نقسم معاك شنو كنفكر.",
            "professional": "بغيت نتكلم على هاد الموضوع بالاحترام."
        }
    }
    lang_key = lang if lang in rewrites else "en"
    tone_key = tone if tone in rewrites[lang_key] else "calm"
    return rewrites[lang_key][tone_key]


# ============================================
# Gemini 3 Integration
# ============================================
async def call_gemini_mediation(text: str, tone: str, lang_hint: str) -> dict:
    """
    Call Gemini API to perform communication mediation.
    
    IMPORTANT: Gemini is the PRIMARY decision-maker for risk_level.
    Word lists are ONLY used to validate the rewrite is clean.
    
    Args:
        text: The user's original message to analyze
        tone: Desired tone for rewrite (calm/friendly/professional)
        lang_hint: Language hint (auto/en/fr/ar/darija)
    
    Returns:
        dict with keys: risk_level, why, rewrite, language
    """
    logger.info(f"[MEDIATE] Input: text_len={len(text)}, tone={tone}, lang_hint={lang_hint}")
    
    # Detect language for rewrite if auto
    detected_lang = detect_language(text, lang_hint) if lang_hint == "auto" else lang_hint
    
    # If no API key, use fallback (but this should not happen in production)
    if not GEMINI_API_KEY:
        logger.warning("[MEDIATE] No API key, using fallback")
        result = fallback_mediation(text, tone, detected_lang)
        return validate_response_structure(result, text, tone, detected_lang)
    
    # Call Gemini - it decides risk_level based on reasoning
    result = await _call_gemini_once(text, tone, lang_hint, detected_lang)
    if result is None:
        logger.warning("[MEDIATE] Gemini returned None, using fallback")
        result = fallback_mediation(text, tone, detected_lang)
    
    # Validate response structure (normalize risk_level, ensure all fields present)
    result = validate_response_structure(result, text, tone, detected_lang)
    
    logger.info(f"[MEDIATE] Gemini decision: risk={result['risk_level']}, lang={result['language']}")
    
    # POST-VALIDATION: Check if rewrite contains profanity (guardrail only)
    if rewrite_contains_profanity(result["rewrite"]):
        logger.warning(f"[MEDIATE] Rewrite contains profanity, regenerating...")
        result = await _regenerate_clean_rewrite(text, tone, result["language"])
        result = validate_response_structure(result, text, tone, detected_lang)
        
        # If still contains profanity, force generic rewrite
        if rewrite_contains_profanity(result["rewrite"]):
            logger.warning(f"[MEDIATE] Rewrite still has profanity, using generic")
            result["rewrite"] = get_generic_rewrite(tone, result["language"])
    
    logger.info(f"[MEDIATE] Final response: risk={result['risk_level']}, lang={result['language']}, rewrite_preview={result['rewrite'][:50]}...")
    
    return result


async def _call_gemini_once(text: str, tone: str, lang_hint: str, detected_lang: str) -> dict | None:
    """Single call to Gemini API. Gemini decides risk_level based on reasoning."""
    system_prompt = build_system_prompt(tone, lang_hint, detected_lang)
    user_message = f'Analyze this message: "{text}"'
    
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
            "temperature": 0.3,  # Lower temperature for more consistent output
            "topP": 0.8,
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
    
    # Retry logic for rate limits
    max_retries = 3
    for attempt in range(max_retries):
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                response = await client.post(
                    url,
                    json=payload,
                    params={"key": GEMINI_API_KEY},
                    headers={"Content-Type": "application/json"}
                )
                
                if response.status_code == 429:
                    # Rate limited - wait and retry
                    wait_time = (attempt + 1) * 2  # 2s, 4s, 6s
                    logger.warning(f"[GEMINI] Rate limited (429), waiting {wait_time}s before retry {attempt + 1}/{max_retries}")
                    import asyncio
                    await asyncio.sleep(wait_time)
                    continue
                
                if response.status_code != 200:
                    logger.error(f"[GEMINI] API error: {response.status_code} - {response.text[:200]}")
                    return None
                
                data = response.json()
                
                # Extract text from Gemini response
                candidates = data.get("candidates", [])
                if not candidates:
                    logger.warning("[GEMINI] No candidates in response")
                    return None
                
                content = candidates[0].get("content", {})
                parts = content.get("parts", [])
                if not parts:
                    logger.warning("[GEMINI] No parts in response")
                    return None
                
                response_text = parts[0].get("text", "")
                logger.info(f"[GEMINI] Raw response (truncated): {response_text[:200]}...")
                
                # Parse JSON response from Gemini
                result = parse_gemini_json(response_text)
                if result:
                    logger.info(f"[GEMINI] Parsed JSON: risk={result.get('risk_level')}, lang={result.get('language')}")
                return result
                    
        except httpx.TimeoutException:
            logger.error("[GEMINI] Request timeout")
            return None
        except Exception as e:
            logger.error(f"[GEMINI] Exception: {e}")
            return None
    
    logger.error("[GEMINI] Max retries exceeded due to rate limiting")
    return None


async def _regenerate_clean_rewrite(text: str, tone: str, target_lang: str) -> dict:
    """
    Regenerate with a stricter prompt focused on getting a CLEAN rewrite.
    Called only when the first rewrite contained profanity.
    """
    strict_prompt = f"""The previous rewrite contained profanity. Generate a COMPLETELY CLEAN rewrite.

CRITICAL RULES:
- NO profanity, curse words, or slurs
- NO insults or negative language about anyone
- Write the rewrite in {target_lang.upper()} language
- Tone: {tone}
- Express frustration constructively, without attacking

Original message: "{text}"

Return ONLY valid JSON:
{{"risk_level": "harmful", "why": "Contains inappropriate language.", "rewrite": "YOUR_CLEAN_REWRITE_IN_{target_lang.upper()}", "language": "{target_lang}"}}"""

    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"
    
    payload = {
        "contents": [{"parts": [{"text": strict_prompt}]}],
        "generationConfig": {
            "temperature": 0.1,
            "maxOutputTokens": 300,
            "responseMimeType": "application/json"
        }
    }
    
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(
                url,
                json=payload,
                params={"key": GEMINI_API_KEY},
                headers={"Content-Type": "application/json"}
            )
            
            if response.status_code == 200:
                data = response.json()
                candidates = data.get("candidates", [])
                if candidates:
                    response_text = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "")
                    logger.info(f"[GEMINI] Strict retry response: {response_text[:100]}...")
                    result = parse_gemini_json(response_text)
                    if result:
                        return result
    except Exception as e:
        logger.error(f"[GEMINI] Strict retry failed: {e}")
    
    # Fallback to generic rewrite
    return {
        "risk_level": "harmful",
        "why": "Contains inappropriate language.",
        "rewrite": get_generic_rewrite(tone, target_lang),
        "language": target_lang
    }


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


def fallback_mediation(text: str, tone: str, detected_lang: str) -> dict:
    """
    Fallback when Gemini API is unavailable.
    Returns a conservative "harmful" response with a generic rewrite.
    
    NOTE: In production, Gemini should always be available.
    This fallback defaults to "harmful" to be safe.
    """
    logger.warning("[FALLBACK] Using fallback mediation (Gemini unavailable)")
    
    return {
        "risk_level": "harmful",
        "why": "Unable to analyze message. Treating as potentially harmful for safety.",
        "rewrite": get_generic_rewrite(tone, detected_lang),
        "language": detected_lang
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


def build_system_prompt(tone: str, lang_hint: str, detected_lang: str) -> str:
    """
    Build the system prompt for Gemini 3.
    
    Gemini is the PRIMARY decision-maker for risk_level based on:
    - Tone and intent analysis
    - Context understanding
    - Semantic meaning (not just keywords)
    """
    return f"""You are KeyCare, an AI communication safety filter. Your job is to analyze messages for harmful intent and provide respectful rewrites.

OUTPUT FORMAT (ONLY valid JSON, no markdown):
{{
  "risk_level": "safe" | "harmful" | "dangerous",
  "why": "brief explanation",
  "rewrite": "respectful alternative",
  "language": "en" | "fr" | "ar" | "darija"
}}

RISK CLASSIFICATION (based on INTENT and TONE, not just keywords):

🔴 DANGEROUS:
- Explicit threats of violence ("I will hurt you", "you deserve to die")
- Calls to harm someone
- Self-harm references
- Incitement to violence

🟠 HARMFUL:
- Direct insults about a person ("you are stupid", "you're an idiot")
- Comparative put-downs ("you are the worst", "most useless person ever")
- Condescending attacks ("you can't do anything right", "you're terrible")
- Profanity directed at someone
- Mockery, bullying, or harassment
- Aggressive/hostile tone meant to hurt
- Passive-aggressive attacks

🟢 SAFE:
- Genuinely polite and constructive messages
- Neutral or positive communication
- Constructive criticism without personal attacks
- Expressing disagreement respectfully

EXAMPLES:
"You are the worst player ever" → HARMFUL (comparative insult attacking ability)
"You're so stupid" → HARMFUL (direct insult)
"I'll kill you" → DANGEROUS (threat)
"Thanks for helping!" → SAFE (appreciation)
"I disagree with your approach" → SAFE (respectful disagreement)

⚠️ CRITICAL LANGUAGE RULE:
The rewrite MUST be in: {detected_lang.upper()}
- If input is Arabic → rewrite in Arabic
- If input is French → rewrite in French  
- If input is Darija → rewrite in Darija
- If input is English → rewrite in English

REWRITE GUIDELINES:
- Remove all hostility while preserving the core message
- Be concise and natural (not preachy)
- No profanity or insults in the rewrite
- Match the input language exactly

Tone preference: {tone}
Target language for rewrite: {detected_lang}

Analyze the message and respond with ONLY the JSON:"""


# ============================================
# API Endpoints
# ============================================
@app.get("/", tags=["Root"])
async def root():
    """Root endpoint - API info."""
    return {
        "name": "KeyCare-Gemini3 API",
        "version": "1.0.0",
        "docs": "/docs",
        "health": "/health"
    }


@app.options("/mediate")
async def mediate_options():
    """CORS preflight handler for /mediate endpoint."""
    return {"status": "ok"}


@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health_check():
    """
    Health check endpoint.
    
    Returns the API status and whether Gemini is configured.
    """
    return HealthResponse(
        status="healthy",
        version=VERSION,
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
        logger.error(f"[MEDIATE] Exception: {e}")
        # Return a safe fallback rather than crashing
        return MediationResponse(
            risk_level="harmful",
            why="Unable to process message.",
            rewrite="I'd like to express my thoughts respectfully.",
            language="en"
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
