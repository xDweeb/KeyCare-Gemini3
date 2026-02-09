# KeyCare-Gemini3 Backend API

FastAPI server providing AI-powered communication mediation using Gemini 3.

## Quick Start

```bash
# Create virtual environment
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Configure environment
cp .env.example .env
# Edit .env and add your GEMINI_API_KEY

# Run server
uvicorn main:app --reload
```

## API Endpoints

### Health Check
```
GET /health
```

### Mediate Message
```
POST /mediate
Content-Type: application/json

{
  "text": "Your message here",
  "tone": "calm",
  "lang_hint": "auto"
}
```

## Documentation

Once running, visit:
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc
