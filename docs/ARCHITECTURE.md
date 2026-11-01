# PayPal Saathi AI — Architecture

## Overview

PayPal Saathi is a **guidance companion** for first-time PayPal users. It helps people understand what they are looking at and what to check next. It is **not** an autonomous payment agent.

Screen-category detection and intent fallback are rule-based. Question answering uses local retrieval-augmented generation when the knowledge index and Ollama are available:

| Component | Role |
|-----------|------|
| Android accessibility service | Observes the official PayPal app UI (with user permission) |
| On-device screen intelligence | Builds an immutable, transient screen state from bounded accessibility metadata; exposes only category and allow-listed navigation context outside the service |
| On-device task engine | Deterministic goal recognition, task state machine, workflow planner, privacy-minimized persistence, and fresh-screen verification |
| Safe navigation gate | Revalidates a narrow navigation allow-list against the live PayPal screen after an explicit overlay-button tap |
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
        │ debounced event; bounded hierarchy traversal on worker thread
        ▼
ScreenDataSanitizer → ScreenElementExtractor → ScreenObservationBuilder
        │
        ├── ScreenClassifier → category + confidence/evidence
        ├── ScreenTransitionDetector → evidence-based transition result
        └── ScreenState (immutable, transient)
                │
                ├── SharedPreferences (category + bounded classification metadata)
                ├── SharedPreferences (generic task type/step metadata; 30-minute expiry)
                └── local broadcast (category + safe labels/actions + timestamp)
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
           ├── GET  /health and /health/ready (minimal public status only)
           └── protected /api/* (issuer-verified RS256 bearer token outside local loopback development)
```

The service owns a single `TaskOrchestrator`. It observes each new `ScreenState`, plans the next deterministic step, persists only generic task metadata, and publishes task state to the existing floating overlay. A task restore is always paused and requires a fresh observation. General questions continue through the existing API/RAG flow; task progression does not call an LLM on accessibility events.

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

`error` and `loading` are local observation categories. They are mapped to
`unknown` when creating an API request, because the backend API contract does
not treat them as canonical screen categories.

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
  ScreenContext.java           Allow-listed navigation/action metadata sanitizer
  ScreenMonitorContract.java
  accessibility/ScreenMonitorService.java  PayPal-only event, bounded observer, overlay integration
  tasks/
    GoalUnderstandingEngine.java  Local deterministic intent normalization
    TaskOrchestrator.java        Validated task lifecycle and transition verification
    TaskStepPlanner.java         Evidence-grounded workflow recommendations
    TaskWorkflowRegistry.java    Deterministic supported workflows
    TaskStateRepository.java     Bounded non-sensitive SharedPreferences format
    SafeActionPolicy.java        Central action allow-list and freshness gate
    TaskRecoveryManager.java     Structured safe recovery messages/policies
  detection/
    ObservedUiElement.java
    ScreenDataSanitizer.java
    ScreenElementExtractor.java
    ScreenElement.java
    ScreenObservationBuilder.java
    ScreenState.java
    ScreenClassifier.java      Deterministic evidence-based rules
    ScreenTransitionDetector.java
```

Screen events are coalesced with a short debounce. The active root is obtained by the service, then traversal, sanitization, model construction, and classification run on a dedicated worker thread. Traversal is capped at 120 nodes and depth 18; classification input is capped at 2,000 characters. Password nodes and sensitive editable subtrees are skipped, and editable values are never read. Accessibility nodes are recycled after processing.

The immutable `ScreenState` and its sanitized elements remain transient in memory. Role, enabled state, supported actions, conservative risk classification, evidence, and confidence are available locally. A safe action is recommendable only for an enabled, clickable, supported, allow-listed navigation control on a sufficiently confident screen. The user must tap a matching Saathi overlay button before the service re-reads the live PayPal hierarchy and invokes that exact node's accessibility click action. The service never uses coordinates. Only HOME → Send/Activity/Wallet/Settings/Help/Menu/More and the restricted Activity/Wallet/Help/Home/Back navigation set can be executed; sensitive/transactional categories are rejected. Payment authorization and sensitive controls are never executable.

The task repository stores a UUID, generic goal type, status, fixed step IDs, canonical screen category/identifier, bounded retry count, and timestamps. It does not store the original request, recipient, amount, account data, control labels, raw accessibility text, or screenshots. Active tasks expire after 30 minutes. Restored tasks are paused and require an explicit resume plus a fresh screen observation. Terminal states are cleared.

The last screen category and bounded metadata remain in app-private storage as described by the privacy policy. Only exact generic labels and coarse action-position bands can accompany a user-submitted question; they expire after 15 seconds at the backend. The score is a rule-based evidence score, not a calibrated probability. Unknown and conflicting evidence remain unknown. Classification, task planning, action-policy, persistence, and transition tests use synthetic data, but PayPal-version/locale compatibility is not established.

On Android 11+, an explicit opt-in permits a one-shot local screenshot/OCR fallback only for unknown or ambiguous PayPal screens. Bundled Latin and Devanagari OCR output is reduced to fixed generic screen hints, never controls or actions; the service re-reads the live accessibility hierarchy before applying those hints. Images and raw text are not stored, logged, uploaded, or passed to the backend/model. Accessibility remains the sole action source. The feature is not device-validated. Voice interaction is explicitly deferred.

## Networking

| Environment | API base URL |
|-------------|--------------|
| Android emulator (default debug) | `http://10.0.2.2:3000` |
| Diagnostic build / physical device + `adb reverse` | `http://127.0.0.1:3000` |
| Release | `https://api.example.com` (override with `-PRELEASE_API_BASE_URL=...`) |

Cleartext HTTP is allowed **only** for loopback / emulator host domains via `network_security_config.xml`. Production should use HTTPS.

## Backend access control

The default server bind is loopback-only. Local development on loopback does not require a token, so the existing emulator and `adb reverse` workflows continue to work. Any explicit non-loopback bind requires authentication configuration; a non-loopback production bind additionally requires certificate and private-key files and runs as HTTPS directly in Node.

All `/api/*` endpoints share a bearer-token middleware. It verifies RS256 access tokens against one configured RSA public key (minimum 2048 bits), exact HTTPS issuer, exact API audience, `sub`, `iat`, `exp`, a maximum one-hour token lifetime, and the `saathi:guidance` scope. Tokens are never logged or returned. Health endpoints are public and return only `{ "status": "ok" }` or `{ "status": "ready" }`.

The repository does not include user identity or token issuance. Android uses a configurable external OIDC provider with browser Authorization Code + PKCE; AppAuth token state is encrypted with Android Keystore, refreshed for protected requests, and cleared on refresh failure or HTTP 401. Logout uses provider end-session when available and optional configured refresh-token revocation. No permanent client secret is stored in the APK. A provider/public client registration and matching production issuer/audience/key/API are still external blockers. Access tokens already issued may remain valid at the backend until expiry.

## Payment safety constraints

- Never collect passwords, OTPs, PINs, or full card details.
- Never select recipients, enter amounts/account data, review or authorize a transaction.
- A user-triggered accessibility click may open only an exact allow-listed navigation control after live revalidation.
- Never use unrestricted coordinate clicks or generated text as an executable action.
- Never upload raw accessibility trees or screenshots.
- Never treat a detected `confirmation` category as proof of payment success.
- Always tell the user to verify recipient, amount, and fees in PayPal.

## Supported screens / limitations

Recognition is keyword-based on supported UI text exposed to accessibility services, with optional English/Latin and Devanagari on-device hints for unknown screens:

- Works best on common flows: home, send money, amount, review, wallet, activity, sign-in, verification.
- Unknown or ambiguous screens fall back to `unknown` with cautious guidance.
- Localized PayPal UIs, rapid animations, and custom views may not classify correctly; Telugu OCR visual hints are not implemented.
- Only package `com.paypal.android.p2pmobile` is configured for monitoring.
- Task workflows include send-money guidance, activity, transaction/refund review, payment-error explanation, Wallet, and Account Settings entry; users still perform all financial decisions.
- Local Account Settings category maps to `unknown` before API submission because the backend has no matching canonical category.
