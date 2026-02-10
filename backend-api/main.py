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
VERSION = "1.1.0"

# ============================================
# Profanity Lists (for validation)
# ============================================
PROFANITY_EN = {"fuck", "shit", "bitch", "ass", "damn", "bastard", "crap", "dick", "cock", "pussy", "whore", "slut", "retard", "fag", "nigger", "cunt"}
INSULTS_EN = {"stupid", "idiot", "dumb", "moron", "loser", "ugly", "pathetic", "worthless", "trash", "garbage", "disgusting"}
PROFANITY_FR = {"merde", "putain", "connard", "connasse", "enculé", "salaud", "salope", "bordel", "nique", "foutre", "con", "conne"}
INSULTS_FR = {"idiot", "stupide", "débile", "crétin", "nul", "nulle", "imbécile", "abruti"}
PROFANITY_AR = {"زب", "كس", "نيك", "شرموط", "عاهرة", "منيوك", "طيز", "خرا", "قحبة"}
INSULTS_AR = {"غبي", "أحمق", "حمار", "تافه", "كلب", "حيوان", "وسخ"}
PROFANITY_DARIJA = {"zebi", "zeb", "kahba", "9a7ba", "nikomok", "lmok", "tboun", "hmar", "7mar", "mok", "sir t9awed"}

ALL_PROFANITY = PROFANITY_EN | INSULTS_EN | PROFANITY_FR | INSULTS_FR | PROFANITY_AR | INSULTS_AR | PROFANITY_DARIJA

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
def contains_profanity(text: str) -> bool:
    """Check if text contains any profanity or insults."""
    text_lower = text.lower()
    # Check exact word matches
    words = set(re.findall(r'\b\w+\b', text_lower))
    if words & ALL_PROFANITY:
        return True
    # Check for Arabic/substring matches
    for word in ALL_PROFANITY:
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


def validate_and_fix_response(result: dict, original_text: str, tone: str, lang: str) -> dict:
    """
    Validate and fix the Gemini response to ensure it meets requirements.
    - Normalize risk_level
    - Ensure 'why' is present
    - Ensure 'rewrite' is non-empty and profanity-free
    - Ensure 'language' is valid
    """
    # Normalize risk_level
    risk_level = normalize_risk_level(result.get("risk_level"))
    
    # Validate 'why'
    why = result.get("why", "").strip()
    if not why:
        if risk_level == "safe":
            why = "Message appears respectful."
        elif risk_level == "harmful":
            why = "Message contains language that could hurt someone."
        else:
            why = "Message contains threatening or dangerous content."
    
    # Validate 'rewrite'
    rewrite = result.get("rewrite", "").strip()
    if not rewrite:
        rewrite = get_generic_rewrite(tone, lang)
    
    # Validate 'language'
    language = result.get("language", "en").strip().lower()
    if language not in ("en", "fr", "ar", "darija"):
        language = detect_language(original_text, "auto")
    
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
    
    Args:
        text: The user's original message to analyze
        tone: Desired tone for rewrite (calm/friendly/professional)
        lang_hint: Language hint (auto/en/fr/ar/darija)
    
    Returns:
        dict with keys: risk_level, why, rewrite, language
    """
    # Log incoming request
    logger.info(f"[MEDIATE] text_len={len(text)}, tone={tone}, lang_hint={lang_hint}")
    
    # If no API key, use fallback heuristics
    if not GEMINI_API_KEY:
        logger.warning("[MEDIATE] No API key, using fallback")
        result = fallback_mediation(text, tone, lang_hint)
        return validate_and_fix_response(result, text, tone, lang_hint)
    
    # First attempt
    result = await _call_gemini_once(text, tone, lang_hint)
    if result is None:
        result = fallback_mediation(text, tone, lang_hint)
    
    # Validate and normalize
    result = validate_and_fix_response(result, text, tone, lang_hint)
    
    # Check if rewrite still contains profanity -> retry with stricter prompt
    if contains_profanity(result["rewrite"]):
        logger.warning(f"[MEDIATE] Rewrite contains profanity, retrying with stricter prompt")
        result = await _call_gemini_strict_retry(text, tone, lang_hint, result["language"])
        result = validate_and_fix_response(result, text, tone, lang_hint)
        
        # If still contains profanity, force generic rewrite
        if contains_profanity(result["rewrite"]):
            logger.warning(f"[MEDIATE] Rewrite still has profanity, forcing generic")
            result["rewrite"] = get_generic_rewrite(tone, result["language"])
            result["risk_level"] = "harmful"
    
    # Final check: if original text has profanity but marked safe, override to harmful
    if result["risk_level"] == "safe" and contains_profanity(text):
        logger.warning(f"[MEDIATE] Original has profanity but marked safe, overriding to harmful")
        result["risk_level"] = "harmful"
        result["why"] = "Message contains inappropriate language."
    
    logger.info(f"[MEDIATE] Final: risk={result['risk_level']}, lang={result['language']}, rewrite_len={len(result['rewrite'])}")
    
    return result


async def _call_gemini_once(text: str, tone: str, lang_hint: str) -> dict | None:
    """Single call to Gemini API."""
    system_prompt = build_system_prompt(tone, lang_hint)
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
    
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(
                url,
                json=payload,
                params={"key": GEMINI_API_KEY},
                headers={"Content-Type": "application/json"}
            )
            
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
            logger.info(f"[GEMINI] Raw response: {response_text[:150]}...")
            
            # Parse JSON response from Gemini
            result = parse_gemini_json(response_text)
            return result
                
    except httpx.TimeoutException:
        logger.error("[GEMINI] Request timeout")
        return None
    except Exception as e:
        logger.error(f"[GEMINI] Exception: {e}")
        return None


async def _call_gemini_strict_retry(text: str, tone: str, lang_hint: str, detected_lang: str) -> dict:
    """Retry with an even stricter prompt for rewrite-only."""
    strict_prompt = f"""The previous rewrite still contained profanity. Generate a COMPLETELY CLEAN rewrite.

RULES:
- NO profanity, insults, or negative language whatsoever
- Use {detected_lang} language
- Tone: {tone}
- Express the frustration constructively without attacking anyone

Original message: "{text}"

Return ONLY valid JSON:
{{"risk_level": "harmful", "why": "Contains inappropriate language.", "rewrite": "YOUR_CLEAN_REWRITE_HERE", "language": "{detected_lang}"}}"""

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
                    result = parse_gemini_json(response_text)
                    if result:
                        return result
    except Exception as e:
        logger.error(f"[GEMINI] Strict retry failed: {e}")
    
    # Fallback to generic
    return {
        "risk_level": "harmful",
        "why": "Contains inappropriate language.",
        "rewrite": get_generic_rewrite(tone, detected_lang),
        "language": detected_lang
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
    Build the STRICT system prompt for Gemini 3.
    
    This prompt is designed to NEVER let profanity/insults pass as "safe".
    """
    return f"""You are KeyCare, a strict communication safety filter inside an Android keyboard.

CRITICAL TASK: Detect harmful language and ALWAYS rewrite it to be completely clean and respectful.

Output ONLY valid JSON (no markdown, no extra text). Use this exact schema:
{{
  "risk_level": "safe" | "harmful" | "dangerous",
  "why": "one short sentence explaining why",
  "rewrite": "completely clean rewrite with NO profanity/insults",
  "language": "en" | "fr" | "ar" | "darija"
}}

STRICT CLASSIFICATION RULES (FOLLOW EXACTLY):
1. DANGEROUS = threats, violence, self-harm, calls to attack/kill someone
2. HARMFUL = ANY of these: insults, profanity, slurs, mockery, name-calling, passive-aggressive attacks, sarcasm meant to hurt, condescension, harassment, bullying
3. SAFE = ONLY if the message is genuinely polite, constructive, and contains ZERO negativity toward anyone

COMMON HARMFUL PATTERNS (mark as "harmful"):
- "you are stupid/idiot/dumb/loser" -> HARMFUL
- "shut up" -> HARMFUL  
- "you suck" / "this sucks" -> HARMFUL
- "I hate you" / "nobody likes you" -> HARMFUL
- Any curse words (fuck, shit, damn, merde, etc.) -> HARMFUL
- Any Arabic insults (غبي، حمار، etc.) -> HARMFUL
- Darija insults (hmar, zebi, 9a7ba, etc.) -> HARMFUL

REWRITE RULES (CRITICAL):
- The rewrite MUST be 100% clean - NO profanity, NO insults, NO negativity
- Remove ALL offensive words completely, don't just soften them
- Keep the same language as input (EN/FR/AR/Darija)
- Preserve the user's core intent (frustration is OK, insults are NOT)
- Be concise and natural, not preachy

EXAMPLES:
Input: "You are so stupid"
Output: {{"risk_level": "harmful", "why": "Contains a direct insult.", "rewrite": "I think there might be a misunderstanding here.", "language": "en"}}

Input: "Fuck off"  
Output: {{"risk_level": "harmful", "why": "Contains profanity and dismissive language.", "rewrite": "I need some space right now.", "language": "en"}}

Input: "أنت غبي جداً"
Output: {{"risk_level": "harmful", "why": "يحتوي على إهانة مباشرة.", "rewrite": "أعتقد أننا نختلف في الرأي.", "language": "ar"}}

Input: "Thanks for your help!"
Output: {{"risk_level": "safe", "why": "Message is polite and appreciative.", "rewrite": "Thanks for your help!", "language": "en"}}

Tone preference: {tone}
Language hint: {lang_hint}

NOW ANALYZE THE USER'S MESSAGE AND RESPOND WITH ONLY THE JSON:"""


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
