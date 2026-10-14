# PayPal Saathi AI — Architecture

## Overview

PayPal Saathi is a **guidance companion** for first-time PayPal users. It helps people understand what they are looking at and what to check next. It is **not** an autonomous payment agent.

Screen-category detection and intent fallback are rule-based. Question answering uses local retrieval-augmented generation when the knowledge index and Ollama are available:

| Component | Role |
|-----------|------|
| Android accessibility service | Observes the official PayPal app UI (with user permission) |
| On-device screen context | Classifies bounded transient text and retains only category plus exact allow-listed navigation labels |
| Node.js / Express API | Returns curated screen guidance and grounded help answers |
| SQLite + Transformers.js | Persists official Help Center chunks and local embeddings; hybrid-ranks question evidence |
| Local Ollama model | Generates task answers from the question and retrieved evidence only; receives no screen context |
| MainActivity / floating overlay | Shows category, guidance, Q&A, service status, errors / retry, and clickable official source links |

The Android API response shape remains stable. Fresh allow-listed labels may be used separately to offer a cautious possible starting point, without claiming an unverified route. Web-scoped source steps are identified as web-only. Missing evidence returns an insufficiency response; model unavailability uses the existing safe rule-based fallback.

## System diagram

```
PayPal app (official)
        │ accessibility events (user-enabled)
        ▼
ScreenMonitorService
        │ bounded transient text (memory only)
        ▼
ScreenClassifier (rules) ──► category ID + allow-listed labels
        │
        ├── SharedPreferences (category only)
        └── local broadcast (category + safe labels + timestamp)
                ▼
           MainActivity
                │ question + recent allow-listed context (optional)
                ▼
        Express API (:3000)
           ├── POST /api/guidance (curated category steps)
           ├── POST /api/classify (question intent → hybrid retrieval → evidence gate → task-only Ollama answer)
           │      └── separate cautious navigation hint from fresh matching labels
           │      ├── SQLite vector search
           │      └── rule-based fallback for weak evidence/model failure
           └── GET  /health (aggregate, content-free pipeline metrics)
```

## Canonical screen categories

Shared between Android and backend:

- `home`
- `send_money`
- `recipient_selection`
- `amount_entry`
- `review_payment`
- `confirmation`
- `activity`
- `wallet`
- `security`
- `sign_in`
- `unknown`

## Backend layout

```
backend/src/
  app.js                 Express app factory (middleware + routes)
  server.js              Listen + graceful shutdown
  routes/                health, classify, guidance
  services/
    guidanceEngine.js    Rule-based screen → steps
    questionClassifier.js Rule-based question → topic and fallback
    screenCategories.js  Canonical labels
    embeddingService.js  Local MiniLM sentence embeddings
    vectorStore.js       Persistent SQLite chunks and exact cosine retrieval
    knowledgeBase.js     Robots-aware Help Center import and chunking
    questionAnswerService.js Retrieval, evidence gate, and safe fallback
    modelAdapter.js      Loopback-only Ollama generation
  middleware/errors.js   Safe client errors (no stack traces)
```

## Android layout

```
android/app/src/main/java/com/vinuthna/paypalsaathi/
  MainActivity.java
  GuidanceClient.java          HTTP to BuildConfig.API_BASE_URL
  LocalGuidanceFallback.java   Offline steps when server is down
  CategoryStore.java           Last category ID only
  ScreenContext.java           Allow-listed navigation label sanitizer
  ScreenMonitorContract.java
  accessibility/ScreenMonitorService.java
  detection/
    ScreenCategories.java
    ScreenClassifier.java      Rule-based (non-AI)
    ScreenCategoryPredictor.java  Future model interface
```

The screen service caps tree traversal at 120 nodes, depth 18, and 2,000 text characters. It skips password nodes and filters the exact official PayPal package. Only exact labels from a short generic navigation allow-list can accompany a question; they expire after 15 seconds at the backend. No confidence value is emitted because the keyword classifier is not probabilistically calibrated. OCR and vision are intentionally not used: they add screenshot processing and privacy cost without demonstrated need for the current accessibility path.

## Networking

| Environment | API base URL |
|-------------|--------------|
| Android emulator (default debug) | `http://10.0.2.2:3000` |
| Diagnostic build / physical device + `adb reverse` | `http://127.0.0.1:3000` |
| Release | `https://api.example.com` (override with `-PRELEASE_API_BASE_URL=...`) |

Cleartext HTTP is allowed **only** for loopback / emulator host domains via `network_security_config.xml`. Production should use HTTPS.

## Payment safety constraints

- Never collect passwords, OTPs, PINs, or full card details.
- Never click PayPal controls or submit payments.
- Never upload raw accessibility trees or screenshots.
- Never treat a detected `confirmation` category as proof of payment success.
- Always tell the user to verify recipient, amount, and fees in PayPal.

## Supported screens / limitations

Recognition is keyword-based on English UI text exposed to accessibility services:

- Works best on common flows: home, send money, amount, review, wallet, activity, sign-in, verification.
- Unknown or ambiguous screens fall back to `unknown` with cautious guidance.
- Localized PayPal UIs, rapid animations, and custom views may not classify correctly.
- Only package `com.paypal.android.p2pmobile` is configured for monitoring.
