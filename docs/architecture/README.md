# KeyCare-Gemini3 Architecture

## System Overview

KeyCare-Gemini3 is a real-time AI communication mediation system consisting of three main components:

```
┌────────────────────────────────────────────────────────────────────┐
│                         USER'S ANDROID DEVICE                       │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐      ┌─────────────────────────────────────────┐  │
│  │   Any App   │      │           KeyCare IME                    │  │
│  │  (WhatsApp, │◀────▶│  ┌─────────────────────────────────┐    │  │
│  │  Messages,  │      │  │  Text Input Field               │    │  │
│  │   etc.)     │      │  └─────────────────────────────────┘    │  │
│  └─────────────┘      │  ┌─────────────────────────────────┐    │  │
│                       │  │  Mediation Suggestion UI        │    │  │
│                       │  │  [Risk: ⚠️] [Accept] [Dismiss]   │    │  │
│                       │  └─────────────────────────────────┘    │  │
│                       └──────────────────┬──────────────────────┘  │
│                                          │                          │
└──────────────────────────────────────────┼──────────────────────────┘
                                           │
                                           │ HTTPS POST /mediate
                                           │ {text, tone, lang_hint}
                                           ▼
┌────────────────────────────────────────────────────────────────────┐
│                         BACKEND SERVER                              │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     FastAPI Application                      │   │
│  │                                                              │   │
│  │  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐   │   │
│  │  │   /health    │    │   /mediate   │    │  Middleware  │   │   │
│  │  │   endpoint   │    │   endpoint   │    │  (CORS,Rate) │   │   │
│  │  └──────────────┘    └──────┬───────┘    └──────────────┘   │   │
│  │                             │                                │   │
│  │                             ▼                                │   │
│  │  ┌──────────────────────────────────────────────────────┐   │   │
│  │  │            Gemini Mediation Service                   │   │   │
│  │  │  - Build structured prompt                            │   │   │
│  │  │  - Call Gemini 3 API                                  │   │   │
│  │  │  - Parse JSON response                                │   │   │
│  │  └──────────────────────────────────────────────────────┘   │   │
│  │                             │                                │   │
│  └─────────────────────────────┼────────────────────────────────┘   │
│                                │                                    │
└────────────────────────────────┼────────────────────────────────────┘
                                 │
                                 │ Gemini API Call
                                 │ (Structured Output)
                                 ▼
┌────────────────────────────────────────────────────────────────────┐
│                         GOOGLE GEMINI 3                             │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Gemini 3 Pro Model                        │   │
│  │                                                              │   │
│  │  Input:                                                      │   │
│  │  - System prompt (mediator instructions)                     │   │
│  │  - User message to analyze                                   │   │
│  │  - Tone preference                                           │   │
│  │  - Language hint                                             │   │
│  │                                                              │   │
│  │  Output (JSON):                                              │   │
│  │  {                                                           │   │
│  │    "risk_level": "safe|harmful|dangerous",                   │   │
│  │    "why": "Brief explanation",                               │   │
│  │    "rewrite": "Respectful version",                          │   │
│  │    "language": "en|fr|ar|darija"                             │   │
│  │  }                                                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└────────────────────────────────────────────────────────────────────┘
```

## Component Details

### 1. Android IME (Input Method Editor)

**Location:** `android-ime/`

**Responsibilities:**
- Capture user text input in real-time
- Display suggestion UI when mediation is available
- Allow user to accept or dismiss rewrites
- Handle keyboard functionality (typing, backspace, etc.)

**Key Features:**
- Non-blocking UI (mediation happens in background)
- Visual risk indicators (green/yellow/red)
- One-tap accept/dismiss

### 2. Backend API (FastAPI)

**Location:** `backend-api/`

**Responsibilities:**
- Receive text analysis requests
- Build structured prompts for Gemini 3
- Parse and validate Gemini responses
- Return clean JSON to the client

**Endpoints:**
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/mediate` | POST | Analyze and rewrite text |

**Security:**
- API key stored in environment variables
- CORS configured for allowed origins
- Rate limiting (optional)

### 3. Gemini 3 Integration

**Purpose:** Provide intelligent communication analysis

**Capabilities Used:**
- **Structured Output:** JSON schema enforcement for reliable parsing
- **Multilingual Understanding:** EN/FR/AR/Darija support
- **Tone Analysis:** Detect harmful, aggressive, or dangerous language
- **Creative Rewriting:** Transform messages while preserving intent

## Data Flow

```
1. User types: "You're such an idiot!"
                    │
                    ▼
2. IME captures text and sends to backend
                    │
                    ▼
3. Backend builds prompt:
   "Analyze this message for communication risk..."
                    │
                    ▼
4. Gemini 3 returns:
   {
     "risk_level": "harmful",
     "why": "Contains personal insult",
     "rewrite": "I'm frustrated with this situation",
     "language": "en"
   }
                    │
                    ▼
5. Backend returns response to IME
                    │
                    ▼
6. IME shows suggestion UI:
   ┌─────────────────────────────────────┐
   │ ⚠️ Harmful: Contains personal insult │
   │                                      │
   │ Suggested: "I'm frustrated with..."  │
   │                                      │
   │     [Accept]        [Dismiss]        │
   └─────────────────────────────────────┘
                    │
                    ▼
7. User chooses Accept → Rewrites message
   User chooses Dismiss → Keeps original
```

## Multilingual Support

| Code | Language | Notes |
|------|----------|-------|
| `en` | English | Default |
| `fr` | French | Full support |
| `ar` | Arabic (MSA) | RTL support |
| `darija` | Moroccan Arabic | Dialect awareness |

## Security Considerations

1. **No Secrets in Code:** All API keys use environment variables
2. **HTTPS Only:** All API calls use encrypted connections
3. **No Message Storage:** Messages are processed and discarded
4. **User Control:** Users can disable mediation at any time

## Future Enhancements

- [ ] On-device ML model for faster inference
- [ ] Custom tone profiles
- [ ] Learning from user preferences
- [ ] Browser extension version
- [ ] iOS keyboard support
