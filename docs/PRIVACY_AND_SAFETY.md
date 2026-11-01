# Privacy and payment safety

## Product stance

Saathi provides **guidance only**. It must never act as an autonomous payment agent.

## Protections implemented

| Control | Status |
|---------|--------|
| No password / OTP / PIN / card collection in Saathi UI | Enforced in copy, autofill disabled on question field, and likely credential/account/financial values are blocked before requests |
| Accessibility hierarchy processing | Restricted to the configured PayPal package; bounded, debounced, performed on a worker thread; password nodes and sensitive editable subtrees are skipped |
| Editable values | Never read; only safe field metadata is retained |
| Raw screen text not persisted, logged, or uploaded | Sanitized text and element state remain transient in memory; category is stored, while only exact allow-listed navigation labels and coarse safe-action metadata are broadcast and optionally sent with a submitted question |
| Safe navigation | A user must tap a matching overlay button; the service revalidates the PayPal package, fresh screen fingerprint, enabled/clickable allow-listed node, task stage, and duplicate-action key before a single accessibility click |
| No financial automation | Recipient selection, amount/account entry, transaction review, confirmation, and authorization are never executed by Saathi |
| No inference of payment success from category alone | Confirmation / review copy tells users to trust PayPal’s UI |
| Backend does not log request bodies with secrets | No body logging middleware |
| Client-facing errors omit stack traces | `middleware/errors.js` |
| API authentication and authorization | Production and non-loopback bindings require issuer-signed RS256 access tokens with exact issuer/audience, expiry checks, and `saathi:guidance` scope |
| Remote production transport | Non-loopback production requires direct HTTPS with configured certificate and key files |
| Rate limiting + helmet + JSON size limit | `app.js` |
| Backup disabled | `allowBackup="false"` |
| Cleartext limited to loopback / emulator host | `network_security_config.xml` |

## Data minimized

- **On device:** last screen **category ID**, fixed event label, update time, confidence score, evidence-strength label, and ambiguity flag in app-private SharedPreferences. A bounded sanitized `ScreenState` (labels, roles, control metadata, coarse recommendations, confidence/evidence, and fingerprint) is transient in memory only. Active task state stores generic task type, fixed step IDs, canonical screen metadata, timestamps, bounded retry/status fields, and a random task ID. It expires after 30 minutes; restoration is paused and requires a fresh observation.
- **Android to API:** a submitted help `question`, canonical `screenCategory`, and optionally up to eight exact generic navigation labels and safe clickable-control labels with coarse position bands and a timestamp. When a screen category was assisted by OCR, the structured accessibility context is omitted. Raw screen text, OCR text, arbitrary descriptions, exact pixel bounds, element roles, editable values, and screenshots are not sent.
- **Host loopback:** questions that pass local sensitive-value checks, allow-listed labels, and retrieved public Help excerpts are sent to the configured local Ollama process for generation.
- **Not retained:** screenshot images, recognized OCR text, full accessibility trees, original task request, recipient, amount, or transaction details. OCR buffers exist transiently only after separate consent. Saathi OIDC access/refresh tokens are encrypted at rest using an Android Keystore AES-GCM key; access tokens are sent only as bearer credentials to the configured Saathi API. PayPal app authentication tokens are not read.

## Remaining limitations

- Non-editable accessible text may briefly exist in memory during local classification. Sanitization removes common emails, phone numbers, long identifiers, currency amounts, and labeled financial/authentication values, but pattern-based filtering cannot guarantee detection of every sensitive value. Password nodes and sensitive editable subtrees are skipped; editable values are not read. Collection is debounced and bounded by depth, node count, and text length. Sanitized element metadata is transient and never logged or persisted.
- Screen confidence is a deterministic evidence score, not a calibrated probability. Screen transitions use consecutive fresh observations and do not infer that a tap succeeded. Unknown or stale observations are not treated as verified screens.
- Help questions are sent to the configured API only when submitted. Likely credentials, email addresses, phone numbers, long identifiers, and explicitly stated balances/payment amounts are blocked before retrieval. Pattern checks cannot guarantee detection of every sensitive value.
- The backend does not persist questions. It embeds them locally, retrieves public PayPal Help excerpts from `backend/data/paypal-knowledge.sqlite`, and sends the question plus those excerpts only to a loopback Ollama service. The model endpoint is restricted to loopback; there is no third-party inference provider.
- The local vector database contains extracted official Help Center chunks and their vectors, not accessibility text or question history. The embedding model weights are downloaded from Hugging Face on first use; no question text is part of that download.
- The importer checks PayPal `robots.txt` and stops on access challenges. It does not bypass access controls. The operator must confirm local indexing is permitted.
- The local development API defaults to loopback (`127.0.0.1`) and does not require a token, preserving emulator and `adb reverse` use. Any non-loopback bind requires JWT verification configuration; non-loopback production additionally requires Node-served HTTPS. CORS is not authentication.
- Android uses AppAuth's browser-based Authorization Code + PKCE flow with build-time issuer/client/audience/scope/redirect configuration. Token state is encrypted with Android Keystore AES-GCM. Access tokens are refreshed before protected requests; refresh failure and HTTP 401 clear local authorization state. Logout clears local state, requests provider end-session when advertised, and revokes refresh tokens only when an HTTPS RFC 7009 endpoint is configured. The external provider/client registration and matching production API have not been selected or tested. Already-issued RS256 access tokens may remain valid at the backend until their expiry (maximum one hour); client logout is not immediate backend token revocation.
- Public `/health` and `/health/ready` endpoints return only a status string and do not expose model, database, metrics, or deployment configuration. Readiness covers the core HTTP API; optional embedding, knowledge-base, and Ollama components remain lazy and can use existing safe fallbacks.
- Optional OCR is available only after separate user consent, on Android 11+, and for unknown or ambiguous PayPal screens. Bundled Latin and Devanagari ML Kit models reduce text to a fixed allow-list of generic screen hints; OCR does not create actions. Raw images/text are not logged, persisted, or sent to any API/model. Accessibility remains the action source and is re-read before visual hints are applied. The path is not yet device-validated; pattern/heading filtering cannot guarantee that private content is never present in a transient screenshot buffer.
- The screen engine identifies safe navigation controls only when the role, enabled/clickable state, supported action, allow-listed label, and confidence checks pass. For the limited action allow-list, a user-triggered overlay button causes a second live hierarchy read and revalidation before a node click. No coordinates or LLM-generated text drive actions. Failed action calls are not automatically retried.
- Financial data entry and authorization controls are never executed. A `confirmation` screen is not proof of success; Saathi asks the user to check Activity when payment outcome is uncertain.
- Release HTTPS endpoint is a placeholder (`https://api.example.com`) until you deploy a real host and configure external identity/token issuance. Release builds now fail without required OIDC settings, but correct provider registration and end-to-end testing remain external prerequisites.
- The floating overlay and accessibility behavior still require physical-device testing.
- Safe-navigation revalidation and the overlay's behavior around the PayPal app require physical-device and accessibility-policy review before release.

## Enabling / disabling accessibility

1. Open Saathi → **Open Accessibility Settings** and review the screen-monitoring disclosure.
2. Enable **PayPal Saathi AI Screen Monitor**.
3. To stop monitoring, disable that service in the same settings screen.

Only `com.paypal.android.p2pmobile` is listed in the accessibility service metadata.
