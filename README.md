# 🛡️ KeyCare-Gemini3

> **Real-time AI Communication Mediation Keyboard** — Built with Gemini 3 for the Gemini 3 Hackathon 2026

[![MIT License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Gemini 3](https://img.shields.io/badge/Powered%20by-Gemini%203-blue.svg)](https://ai.google.dev/)
[![Android](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://developer.android.com/)

---

## 🎯 What is KeyCare?

KeyCare is an **Android keyboard (IME)** that helps users communicate more thoughtfully. Before you send a message, KeyCare analyzes it in real-time using **Gemini 3** to:

- ✅ **Assess communication risk** — Safe / Harmful / Dangerous
- 💡 **Explain why** — A brief 1-line explanation
- ✨ **Rewrite respectfully** — Transform aggressive text into constructive dialogue
- 🌍 **Multilingual support** — English, French, Arabic + Darija dialect

**Goal:** Reduce online toxicity and help users express themselves better, one message at a time.

---

## 🎬 Demo

| Live Demo | Backend API | Landing Page |
|-----------|-------------|--------------|
| 🎮 [Try Demo](https://xdweeb.github.io/KeyCare-Gemini3/demo) | 🔗 [API Health](https://keycare-gemini3-api-2587283546dc.herokuapp.com/health) | 🌐 [key-care.app](https://xdweeb.github.io/KeyCare-Gemini3/) |

---

## 🏗️ Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │
│  Android IME    │────▶│  FastAPI        │────▶│  Gemini 3       │
│  (Keyboard)     │     │  Backend        │     │  API            │
│                 │◀────│                 │◀────│                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │
        │   Structured JSON     │
        │   {risk, why, rewrite}│
        ▼                       │
┌─────────────────┐             │
│  User sees      │             │
│  suggestion UI  │◀────────────┘
└─────────────────┘
```

**Flow:**
1. User types a message in any app
2. KeyCare IME captures the text
3. Text is sent to our FastAPI backend
4. Backend calls Gemini 3 with a structured mediation prompt
5. Gemini 3 returns: risk level, explanation, and rewritten text
6. User sees the suggestion and can accept/reject the rewrite

---

## 🤖 How We Used Gemini 3

KeyCare leverages **Gemini 3's advanced capabilities**:

| Feature | Gemini 3 Usage |
|---------|----------------|
| **Risk Assessment** | Structured output with classification (safe/harmful/dangerous) |
| **Explanation** | Concise reasoning in the user's language |
| **Rewriting** | Tone transformation while preserving intent |
| **Multilingual** | Native support for EN/FR/AR with dialect awareness |

**Prompt Engineering:** We use a carefully crafted system prompt that instructs Gemini 3 to act as a communication mediator, returning JSON-structured responses for reliable parsing.

---

## 📁 Project Structure

```
KeyCare-Gemini3/
├── android-ime/          # Android keyboard (IME) app
├── backend-api/          # FastAPI server (Gemini 3 integration)
├── web-landing/          # Demo landing page (placeholder)
├── docs/
│   ├── screenshots/      # App screenshots
│   └── architecture/     # Architecture diagrams
├── .env.example          # Environment variables template
├── .gitignore
├── LICENSE               # MIT
└── README.md
```

---

## 🚀 Local Setup

### Prerequisites
- Python 3.10+
- Android Studio (for IME)
- Gemini API key ([Get one here](https://ai.google.dev/))

### Backend Setup

```bash
# 1. Navigate to backend
cd backend-api

# 2. Create virtual environment
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 3. Install dependencies
pip install -r requirements.txt

# 4. Configure environment
cp .env.example .env
# Edit .env and add your GEMINI_API_KEY

# 5. Run server
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Android IME Setup

```bash
# 1. Open android-ime/ in Android Studio
# 2. Update the API endpoint in the app config
# 3. Build and install on device/emulator
# 4. Enable KeyCare keyboard in Settings > Language & Input
```

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Health check |
| `POST` | `/mediate` | Analyze and rewrite text |

**Example Request:**
```json
POST /mediate
{
  "text": "You're such an idiot!",
  "tone": "calm",
  "lang_hint": "auto"
}
```

**Example Response:**
```json
{
  "risk_level": "harmful",
  "why": "Contains a personal insult that could escalate conflict.",
  "rewrite": "I'm frustrated with how this situation turned out.",
  "language": "en"
}
```

---

## 🔒 Security

- ⚠️ **No secrets are committed** — All API keys use environment variables
- 🔐 `.env` files are gitignored
- 📋 Use `.env.example` as a template

---

## 👥 Team

Built with ❤️ for the **Gemini 3 Hackathon 2026**

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
