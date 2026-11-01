# PayPal Saathi AI

Android companion + Node.js API that gives **screen-aware, goal-based guidance** for first-time PayPal users.

Android screen classification and the backend intent fallback remain rule-based. Submitted help questions can use a local retrieval-augmented answer pipeline: official PayPal India Help pages are chunked and embedded into a persistent SQLite index, then retrieved evidence is sent to a local Ollama model. There is no cloud LLM provider.

Saathi can open only a small allow-list of observed navigation controls after the user taps the matching button in the overlay. It never selects recipients, enters financial data, or submits/authorizes payments. It never asks for passwords, OTPs, PINs, or card numbers.

## Prerequisites

- Node.js **20+**
- Android Studio / Android SDK (API 35), JDK **17**
- PayPal mobile app on emulator or device (for screen monitoring)
- USB debugging for physical devices

## Project layout

```
paypal-saathi-ai-main/
├── android/          Java/XML Android app
├── backend/          Express API (local RAG + rule-based fallback)
└── docs/             Architecture, privacy, local model details
```

## Backend setup

```bash
cd backend
npm install
npm start
```

Default: `http://127.0.0.1:3000` (loopback only; for local development).

The question-answer path defaults to local Ollama model `qwen2.5:3b-instruct` and the CPU embedding model `Xenova/all-MiniLM-L6-v2`. On first ingestion, Transformers.js downloads the small quantized embedding model into ignored `backend/data/model-cache/`. For general help questions, only the question, inferred task intent, and retrieved excerpts are sent to the configured loopback Ollama endpoint. Screen categories and accessibility metadata are not sent to the model. When accessibility is enabled, the API may receive a canonical screen category, a heuristic confidence/evidence summary, up to eight allow-listed navigation labels, and clickable-control labels with coarse top/middle/bottom and left/center/right positions. Confidence is a rule-based evidence score, not a calibrated probability or proof of screen identity. No free-form screen text, exact bounds, screenshots, or field values are sent.

The Android task engine recognizes common goals locally and deterministically, persists only generic task metadata, and replans from fresh observed screens. It supports sending-money guidance, activity, status/refund review, payment errors, Wallet, and a conservative Account Settings entry. The floating assistant shows progress, pause/resume/cancel, repeat, and (for explicitly allow-listed navigation only) a user-triggered open button. Every opened control is revalidated against the current PayPal root, enabled state, label, bounds band, screen fingerprint, task stage, and a short freshness window. No coordinates are clicked. Recipient choice, amounts, credentials, review, and authorization remain manual. Payment status screens never prove that a transfer succeeded.

Accessibility is the primary screen-understanding source. On Android 11+, users may separately opt in to a local screenshot/OCR fallback for unknown or ambiguous PayPal screens. The screenshot is processed on-device with bundled Latin and Devanagari ML Kit models; only a short allow-list of generic screen hints is used, never as an actionable control. Images and recognized text are not persisted, logged, sent to the backend/model, or used to select financial actions. Without consent, or when the fallback cannot safely identify a screen, Saathi asks the user to clarify. Voice is deferred. This remains a prototype and is not validated across PayPal versions, locales, or accounts.

The backend defaults to loopback and keeps the existing unauthenticated emulator / `adb reverse` workflow local. Every `/api/*` route requires authentication in production and whenever `HOST` is non-loopback. Non-loopback production additionally requires HTTPS served directly by Node; `HOST=0.0.0.0` or `HOST=::` is never selected implicitly. CORS and rate limiting are defense-in-depth, not authentication.

The project has no account identity or token-issuance system. Production API access expects a short-lived RS256 JWT issued by a separately configured OIDC identity provider. The backend verifies the signature, exact issuer and audience, subject, issue/expiry times, a maximum one-hour lifetime, and the `saathi:guidance` scope. Android now provides a browser-based AppAuth Authorization Code + PKCE flow, keeps serialized token state encrypted with an Android Keystore AES-GCM key, refreshes access tokens for protected API requests, clears local authorization after refresh failure or HTTP 401, and supports provider logout plus optional refresh-token revocation. No client secret is embedded. The provider has not been selected or registered, so the Android OIDC flow is configurable but cannot be end-to-end validated until provider-issued values and a matching backend deployment exist.

Configure Android with Gradle project properties or environment variables (the latter use the `PAYPAL_SAATHI_` prefix): `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_AUDIENCE`, `OIDC_SCOPE` (must include `openid saathi:guidance`), and `OIDC_REDIRECT_URI`. The native client must be registered as a public client using Authorization Code + PKCE and the exact redirect URI; align the issuer, audience, scope, and public signing key with backend `AUTH_ISSUER`, `AUTH_AUDIENCE`, and `AUTH_PUBLIC_KEY_PEM`. Optionally set `OIDC_REVOCATION_ENDPOINT` to the provider's HTTPS RFC 7009 endpoint. Never add a client secret to Gradle properties committed to source or to the APK. Release builds fail unless production API/TLS signing and required OIDC settings are configured.

For production, configure the external identity provider and TLS files before starting the server. Example PowerShell configuration (replace paths and issuer/audience with your deployment values):

```powershell
$env:NODE_ENV = 'production'
$env:HOST = '0.0.0.0'
$env:TLS_CERT_FILE = 'C:\secrets\api-cert.pem'
$env:TLS_KEY_FILE = 'C:\secrets\api-key.pem'
$env:AUTH_PUBLIC_KEY_PEM = Get-Content 'C:\secrets\issuer-public-key.pem' -Raw
$env:AUTH_ISSUER = 'https://identity.example.com/'
$env:AUTH_AUDIENCE = 'paypal-saathi-api'
npm start
```

In development, leave `HOST` at `127.0.0.1`; emulator access through `10.0.2.2` and physical-device `adb reverse` continue to work without tokens. Any explicit non-loopback development bind also requires the JWT verification settings; use it only on a trusted network. Production non-loopback binds fail at startup unless both TLS files and all authentication settings are present.

Check that Ollama is running and the configured answer model is installed:

```powershell
ollama --version
ollama list
```

Model selection and local RAG settings are configurable with environment variables; see `backend/.env.example`. `OLLAMA_BASE_URL` is restricted to loopback addresses so question content cannot accidentally be sent to a remote model endpoint.

### Import the official Help Center knowledge base

The importer uses a short allow-list of public PayPal India Help Center article URLs, checks PayPal `robots.txt`, stays on the official HTTPS Help Center host, limits response size and redirects, and waits between requests. It does not crawl the site or bypass access challenges.

```powershell
cd backend
npm run ingest:paypal -- --confirm-permitted
```

The source allow-list covers sending and receiving payments, bank linking, email confirmation, password reset, refunds, payment holds, and account limitations. The importer checks PayPal’s robots rules and may skip or stop on pages that are unavailable, disallowed, redirected outside the approved path, or challenged. It does not work around those restrictions. Documents and vectors are kept locally in the ignored `backend/data/paypal-knowledge.sqlite`; repeated imports skip unchanged documents and vectors. If PayPal returns an access challenge, the importer stops. Do not work around it. If you have local HTML exports that you are permitted to index, create a JSON manifest beside those files:

```json
[
  {
    "file": "send-help.html",
    "url": "https://www.paypal.com/in/cshelp/article/how-do-i-send-payments-help293"
  }
]
```

Then import with `npm run ingest:paypal -- --confirm-permitted --manifest .\sources.json`. The importer accepts only HTML files located beside or below the manifest and official PayPal India Help article URLs.

Check index integrity, compatible embedding counts, last successful ingestion, and configured local model availability without exposing question content:

```powershell
npm run diagnostics:knowledge
```

`POST /api/classify` retains its existing fields and adds `nextStep` (or `null`) for the supported screen-aware sending flow, plus `screenClassification` (or `null`) containing the validated canonical category, confidence/evidence metadata, and sanitized actionable descriptors. The backend validates categories and bounds/allow-lists the metadata; because this is client-supplied accessibility data, it does not cryptographically prove what is on screen. The deterministic home rule requires at least two distinct allow-listed clickable controls; specific sending guidance additionally requires fresh, non-ambiguous metadata with confidence of at least 75 and a clickable `Send` descriptor. Weak, conflicting, stale, or missing evidence falls back to asking the user to describe the screen. General `guidance` includes citations to retrieved sources when evidence is sufficient; Ollama unavailability uses the existing transparent rule-based fallback. Neither user questions nor screen content are stored in the vector database.

Classifier test fixtures are synthetic and exercise supported signals; they are not verified accessibility snapshots from every PayPal version. The classifier is English-oriented and depends on what the installed PayPal app exposes to Android accessibility. Validate recognition and control positions on the actual device before relying on screen-specific guidance.

Optional env (see `backend/.env.example`; export in your shell — no dotenv loader):

| Variable | Default | Purpose |
|----------|---------|---------|
| `PORT` | `3000` | Listen port |
| `HOST` | `127.0.0.1` | IPv4/IPv6 bind address; non-loopback binding is explicit and requires authentication |
| `NODE_ENV` | `development` | `production` enforces authentication; non-loopback production also requires direct HTTPS |
| `AUTH_PUBLIC_KEY_PEM` | — | RSA public key (at least 2048 bits) used to verify issuer-signed RS256 access tokens; required in production or for non-loopback binds |
| `AUTH_ISSUER` | — | Exact HTTPS issuer URL expected in access-token `iss` |
| `AUTH_AUDIENCE` | — | Exact API audience expected in access-token `aud` |
| `TLS_CERT_FILE` | — | PEM certificate path; required with `TLS_KEY_FILE` for non-loopback production |
| `TLS_KEY_FILE` | — | Matching PEM private-key path; required with `TLS_CERT_FILE` for non-loopback production |
| `CORS_ORIGINS` | `http://localhost:3000` | Allowed browser origins |
| `RATE_LIMIT_PER_MINUTE` | `60` | Per-IP limit |
| `PAYPAL_LLM_MODEL` | `qwen2.5:3b-instruct` | Installed local Ollama answer model |
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | Loopback-only Ollama endpoint |
| `PAYPAL_EMBEDDING_MODEL` | `Xenova/all-MiniLM-L6-v2` | Local Transformers.js embedding model |
| `PAYPAL_KB_DB_PATH` | `backend/data/paypal-knowledge.sqlite` | Persistent local document/vector index |
| `PAYPAL_RAG_MIN_SIMILARITY` | `0.45` | Minimum cosine similarity before generation |
| `PAYPAL_LLM_TIMEOUT_MS` | `45000` | Local generation timeout |
| `PAYPAL_OLLAMA_KEEP_ALIVE` | `5m` | Retain the model briefly after generation (`0`, `1m`, `5m`, or `10m`) |

Development with reload:

```bash
npm run dev
```

### API endpoints

| Method | Path | Body | Success |
|--------|------|------|---------|
| `GET` | `/health` | — | Public liveness: `{ "status": "ok" }` |
| `GET` | `/health/ready` | — | Public API readiness: `{ "status": "ready" }`; optional AI/index dependencies remain lazy and may use fallbacks |
| `POST` | `/api/guidance` | Bearer token with `saathi:guidance`; `{ "screenCategory": "review_payment" }` | `{ category, title, steps, matchedBy, safetyNote }` |
| `POST` | `/api/classify` | Bearer token with `saathi:guidance`; `{ "question": "How do I send money?", "screenCategory": "home" }` (optional recent context contains allow-listed labels and coarse positions of clickable controls) | `{ category, guidance, disclaimer, matchedBy, screenCategory, nextStep }` |

Protected API errors return `{ "error": "..." }`; missing or invalid tokens receive `401`, and authenticated tokens without the required scope receive `403`. Other validation errors use `400` / `404` / `413` / `500`. Stack traces and token values are never returned or logged.

### Backend tests

```bash
cd backend
npm test
```

## Android setup

```bash
cd android
.\gradlew.bat assembleDebug
```

Debug APK:

`android/app/build/outputs/apk/debug/app-debug.apk`

Install:

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Emulator networking (default)

Debug `API_BASE_URL` is `http://10.0.2.2:3000` (emulator → host loopback).

1. Start the backend on the host at port **3000** (default loopback binding).
2. Build with `.\gradlew.bat assembleDebug -PAPI_BASE_URL=http://10.0.2.2:3000` (or use the same default URL).
3. Install `app\build\outputs\apk\debug\app-debug.apk` and launch it on the emulator.
4. Confirm **Backend: connected** on the home screen.

### Physical device + `adb reverse`

Start the backend on the development computer in one terminal:

```powershell
Set-Location backend
npm start
```

In a second PowerShell terminal, connect the device and build/install a regular debug APK with the physical-phone loopback URL:

```powershell
adb reverse tcp:3000 tcp:3000
Set-Location android
.\gradlew.bat assembleDebug -PAPI_BASE_URL=http://127.0.0.1:3000
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The `adb reverse` mapping forwards the phone's `127.0.0.1:3000` to the host backend. This regular debug build retains the accessibility service. The diagnostic build is a separate option that excludes the accessibility service. The URL override is applied only to that build invocation; omit it to restore the emulator default `http://10.0.2.2:3000`.

Cleartext HTTP is allowed only for `10.0.2.2`, `127.0.0.1`, `localhost`, and Genymotion `10.0.3.2`. Production/release must use HTTPS via `-PRELEASE_API_BASE_URL=https://your.host` and the external OIDC bearer-token flow described above.

### Signed release APK for testing

Release builds use the existing release keystore supplied through local environment variables. Do not put passwords in Gradle files, command-line arguments, or source control. The keystore is not included in this project; preserve the existing key if this application has previously been signed.

In PowerShell, set the following variables in the shell that will run Gradle:

```powershell
$env:PAYPAL_SAATHI_RELEASE_STORE_FILE = 'C:\absolute\path\to\your-release-key.jks'
$env:PAYPAL_SAATHI_RELEASE_KEY_ALIAS = Read-Host 'Release key alias'
$storePassword = Read-Host 'Keystore password' -AsSecureString
$keyPassword = Read-Host 'Key password' -AsSecureString
$storeBstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storePassword)
$keyBstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keyPassword)
try {
    $env:PAYPAL_SAATHI_RELEASE_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($storeBstr)
    $env:PAYPAL_SAATHI_RELEASE_KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyBstr)
    Set-Location android
    .\gradlew.bat assembleRelease -PRELEASE_API_BASE_URL=https://your-deployed-api-host
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($storeBstr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyBstr)
    Remove-Item Env:PAYPAL_SAATHI_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:PAYPAL_SAATHI_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:PAYPAL_SAATHI_RELEASE_STORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:PAYPAL_SAATHI_RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
}
```

Replace the keystore path and API host with your actual values. Gradle refuses to build a release if the keystore settings are missing or the API URL is not a deployed HTTPS URL. The signed APK is written to `android/app/build/outputs/apk/release/app-release.apk`; signing files and APK outputs are ignored by Git.

For local installation, an APK signed with a release key cannot update an existing debug-signed installation of the same application ID. Uninstalling that debug app removes its local data; back up anything needed first, or test on a separate device. If the app is distributed through Google Play, use an internal testing track to test the Play App Signing build. A locally signed APK may not have the same signing certificate as the APK delivered by Play.

### Accessibility permission

1. Open the app → **Open Accessibility Settings**.
2. Enable **PayPal Saathi AI Screen Monitor**.
3. Open PayPal, navigate a flow, return to Saathi to see category + guidance.
4. Disable the service anytime to stop monitoring.

Only package `com.paypal.android.p2pmobile` is monitored.

### Android unit tests (screen intelligence)

```bash
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

## Architecture & docs

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — components, categories, networking
- [`docs/PRIVACY_AND_SAFETY.md`](docs/PRIVACY_AND_SAFETY.md) — payment safety & privacy
- [`docs/SCREEN_INTELLIGENCE_AUDIT.md`](docs/SCREEN_INTELLIGENCE_AUDIT.md) — current Android screen-observation design and limitations
- [`docs/TASK_ORCHESTRATION_DESIGN.md`](docs/TASK_ORCHESTRATION_DESIGN.md) — deterministic task lifecycle and persistence
- [`docs/NEXT_ACTION_PLANNING.md`](docs/NEXT_ACTION_PLANNING.md) — evidence-grounded planning
- [`docs/SAFE_NAVIGATION_POLICY.md`](docs/SAFE_NAVIGATION_POLICY.md) — user-confirmed safe navigation and prohibited financial actions
- [`docs/ERROR_RECOVERY_DESIGN.md`](docs/ERROR_RECOVERY_DESIGN.md) — bounded recovery behavior
- [`docs/ON_DEVICE_VISUAL_UNDERSTANDING.md`](docs/ON_DEVICE_VISUAL_UNDERSTANDING.md) — consented, local-only visual fallback and privacy controls
- [`docs/PRODUCTION_READINESS.md`](docs/PRODUCTION_READINESS.md) — release gates and validation
- [`docs/DEVICE_VALIDATION_GUIDE.md`](docs/DEVICE_VALIDATION_GUIDE.md) — controlled manual Android device scenarios; no real-payment testing
- [`docs/TECHNICAL_AUDIT.md`](docs/TECHNICAL_AUDIT.md) — current repository audit
- [`docs/MODEL_INTEGRATION.md`](docs/MODEL_INTEGRATION.md) — local RAG and model configuration

## Known limitations

- Screen classification is deterministic and recognizes selected English, Hindi, and Telugu phrases; localization and real PayPal UI behavior remain incomplete and many screens map to `unknown`.
- Screen-state confidence is a rule-based evidence score, not a calibrated probability. Synthetic tests do not establish device compatibility.
- Intent recognition and screen labels are rule-based, English-oriented in many paths, and require device/locale validation.
- The task engine never reads account transaction details or confirms financial outcomes.
- Optional local screenshot/OCR is consent-gated and not device-validated; no screenshot or recognized text is retained or uploaded.
- Safe navigation is intentionally limited to exact, allow-listed controls; all flows still work by manual tapping when a control is not eligible.
- Accessibility service and navigation behavior require physical-device validation.
- Local loopback API development does not require auth. Any non-loopback bind requires JWT verification configuration; non-loopback production also requires HTTPS.
- Release API host is a placeholder; external identity-provider registration, production API deployment, and device validation are incomplete.
- Voice interaction is explicitly deferred.
- End-to-end accessibility behavior requires a real PayPal app session (emulator or phone).

## License / ownership

Private prototype. Do not commit secrets, signing keys, or `.env` files (see `.gitignore`).
