# PayPal Saathi AI

Android companion + Node.js API that gives **step-by-step payment guidance** for first-time PayPal users.

Android screen classification and the backend intent fallback remain rule-based. Submitted help questions can use a local retrieval-augmented answer pipeline: official PayPal India Help pages are chunked and embedded into a persistent SQLite index, then retrieved evidence is sent to a local Ollama model. There is no cloud LLM provider.

Saathi **never** clicks PayPal controls, submits payments, or asks for passwords, OTPs, PINs, or card numbers.

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

For the supported “send money” goal, a deterministic screen-step selector uses the current category and only an exact allow-listed clickable “Send” label with coarse position metadata. It returns one instruction and the expected next screen; on screen changes the floating panel repeats the goal with fresh context. Uncertain screens ask for a general description, and security screens pause the flow. This is conservative rule-based assistance, not reliable recognition of every PayPal page. It does not tap controls. Visual button highlighting is not implemented.

The backend has no authentication. Keep the default loopback binding for local development; the Android emulator reaches it through `10.0.2.2`, and a physical phone can use `adb reverse tcp:3000 tcp:3000`. `HOST` accepts an IPv4 or IPv6 address literal. To listen beyond loopback, explicitly set `HOST` to a trusted interface address. `0.0.0.0` listens on every IPv4 interface and `::` listens on every IPv6 interface. Restrict access with a firewall or private network. A LAN or production deployment additionally requires an authenticated access design and HTTPS termination; changing `HOST` does not provide authentication or encryption, and CORS is not authentication.

PowerShell example for an explicitly exposed development backend (use only on a trusted network):

```powershell
$env:HOST = '0.0.0.0'
npm start
```

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
| `HOST` | `127.0.0.1` | IPv4/IPv6 bind address; set explicitly to expose beyond loopback |
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
| `GET` | `/health` | — | Liveness only: `{ status, service, version, classifier, modelIntegrated, pipelineMetrics, supportedScreenCategories }`; does not confirm AI or database readiness |
| `POST` | `/api/guidance` | `{ "screenCategory": "review_payment" }` | `{ category, title, steps, matchedBy, safetyNote }` |
| `POST` | `/api/classify` | `{ "question": "How do I send money?", "screenCategory": "home" }` (optional recent context contains allow-listed labels and coarse positions of clickable controls) | `{ category, guidance, disclaimer, matchedBy, screenCategory, nextStep }` |

Errors return `{ "error": "..." }` with `400` / `403` / `404` / `413` / `500`. Stack traces are never returned.

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

Cleartext HTTP is allowed only for `10.0.2.2`, `127.0.0.1`, `localhost`, and Genymotion `10.0.3.2`. Production/release should use HTTPS via `-PRELEASE_API_BASE_URL=https://your.host`.

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

### Android unit tests (classifier)

```bash
cd android
.\gradlew.bat testDebugUnitTest
```

## Architecture & docs

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — components, categories, networking
- [`docs/PRIVACY_AND_SAFETY.md`](docs/PRIVACY_AND_SAFETY.md) — payment safety & privacy
- [`docs/MODEL_INTEGRATION.md`](docs/MODEL_INTEGRATION.md) — local RAG and model configuration

## Known limitations

- Keyword screen recognition is English-oriented and incomplete; many PayPal screens map to `unknown`.
- The floating overlay and accessibility behavior remain device-dependent and require physical-device validation.
- Local API has no auth — use only on trusted networks.
- Release HTTPS host is a placeholder until you deploy one.
- End-to-end accessibility behavior requires a real PayPal app session (emulator or phone).

## License / ownership

Private prototype. Do not commit secrets, signing keys, or `.env` files (see `.gitignore`).
