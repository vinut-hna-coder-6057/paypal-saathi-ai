# Technical Audit — PayPal Saathi AI

## Scope and method

This audit covers the current Android app, accessibility/screen pipeline, goal-based task engine, backend/RAG path, network and persistence configuration, tests, and current documentation. Findings distinguish checked-in implementation from device/external validation. Synthetic tests are not evidence of PayPal compatibility or release readiness.

## Executive summary

The project is a screen-aware guidance prototype with a deterministic on-device task engine, bounded task-state persistence, a rule-based screen classifier, local RAG/Ollama help, and a protected non-loopback backend. The existing floating assistant remains the only assistant UI. The task engine is independent of backend/model availability.

The Android service can open a narrow set of allow-listed navigation controls only after the user taps the corresponding overlay button and a live hierarchy revalidation passes. It never uses coordinates and never selects a recipient, enters financial data, reviews a transfer, or submits/authorizes payments. Payment outcomes are not asserted from screen category alone.

Optional local screenshot/OCR is implemented behind separate consent for Android 11+ unknown PayPal screens, but has not been device-validated. Android OIDC login/token lifecycle is also implemented but lacks an external provider registration and matching production API configuration. Voice is deferred. Real device, PayPal version, locale, accessibility policy, and release validation remain outstanding.

## Findings

| ID | Severity | Area | Status | Summary |
|---|---|---|---|---|
| AUDIT-001 | High | Remote Android identity | External configuration blocked | Android AppAuth PKCE, encrypted token state, refresh, 401 clearing, logout, and optional refresh-token revocation are wired; no provider/native-client registration or end-to-end sign-in has been performed. |
| AUDIT-002 | High | Screen classification | Open | Classification is heuristic and accessibility-dependent; confidence is not calibrated and no comprehensive PayPal-version/locale validation exists. |
| AUDIT-003 | High | Safe navigation/device behavior | Validation pending | Action policy and synthetic tests are implemented, but actual Android accessibility click behavior and platform-policy acceptability need real-device/external review. |
| AUDIT-004 | Medium | Visual understanding | Implemented, device validation pending | Consented local screenshot/OCR uses bundled Latin/Devanagari recognizers and fixed generic hints; no images or raw recognized text are retained or uploaded. Physical-device privacy/resource validation remains outstanding. |
| AUDIT-005 | Medium | Production release | Open | Signing, deployed HTTPS identity/API, external accessibility/privacy review, dependency review, and device validation remain release gates. |
| AUDIT-006 | Low | Voice | Deferred | No voice capabilities are implemented; text and the existing overlay remain the supported interaction. |

## Verified implementation

- **Backend:** loopback defaults remain. Existing shared RS256 authentication, issuer/audience/scope/token-time validation, and production HTTPS checks are preserved. Remote Android access requires configured external provider/API values and a provider test.
- **Screen intelligence:** bounded worker-thread AccessibilityService observation; sensitive editable values are not read; screen elements and labels are transient; unknown/conflicting evidence fails closed.
- **Task orchestration:** local deterministic goal matching, validated state transitions, workflow registry, planner, pause/resume/cancel/complete/recovery, and transition-based step verification.
- **Persistence:** app-private SharedPreferences stores only generic task type/IDs/status/category/timestamps and bounded counters; active tasks expire after 30 minutes and restore paused with fresh observation required.
- **Navigation:** centralized policy and live exact-node revalidation; only narrow allow-listed safe navigation can execute after an explicit user button tap. Failed actions are not automatically retried.
- **Financial safety:** recipient selection, amount/account entry, credentials, review, payment authorization, and transaction retry remain manual. A status/confirmation screen does not complete a send-money task: the user must reach Activity and acknowledge checking the result. Uncertain outcomes direct the user to Activity and never claim success/failure.
- **Logging:** accessibility event logs do not include package names, screen labels, user questions, or financial data. Android logs are limited to lifecycle/error details.
- **Authentication:** AppAuth uses browser Authorization Code + PKCE, Android Keystore AES-GCM for serialized token state, refresh-aware API calls, fail-closed remote requests, local clearing on refresh failure/401, and optional configured provider refresh-token revocation. No client secret is embedded. Provider revocation is not configured; issued API access tokens can remain valid until expiry.
- **Visual fallback:** explicit consent, Android 11+ gating, unknown-screen-only capture, bundled Latin/Devanagari OCR, fixed safe-heading allow-list, accessibility revalidation, transient bitmap cleanup, and suppression of structured accessibility context for visual-assisted requests.
- **Question answering:** existing RAG, embedding, SQLite, and local Ollama implementation is preserved. Task progression does not invoke an LLM on every event.
- **Privacy:** screenshot/OCR buffers are transient and are not uploaded, logged, persisted, or included in backend/model input. Raw accessibility trees and original requests are not task-persisted.

## Testing and limits

Android unit tests cover deterministic classifier, task, persistence, recovery, action-policy, and synthetic flow behavior. Backend authentication and RAG suites remain separate. Tests use synthetic data, not PayPal credentials or transactions.

The repository does not establish:

- PayPal screen recognition accuracy across devices, versions, or locales.
- Android overlay and accessibility action behavior on a physical device.
- AccessibilityService distribution-policy compliance.
- End-to-end authentication with a real external OIDC provider/API.
- OCR behavior and privacy/resource handling on physical PayPal devices.
- Voice interaction.
- Play Store approval or production release readiness.

## Android authentication status and external blocker

The Android client implements configurable browser-based Authorization Code
with PKCE, encrypted token persistence, access-token refresh, bearer headers
for protected POST requests, fail-closed remote behavior, local sign-out, and
provider end-session/optional RFC 7009 refresh-token revocation. `/health`
remains public. No issuer, public client, audience, redirect registration,
signing key, or provider revocation endpoint has been selected or tested.
Therefore remote production access remains blocked until provider values are
registered and aligned with backend `AUTH_ISSUER`, `AUTH_AUDIENCE`, and
`AUTH_PUBLIC_KEY_PEM`. Already-issued access tokens can remain valid until
their backend-enforced expiry. Keep RS256, issuer/audience, scope, expiry, and
HTTPS validation enabled.

See [TASK_ORCHESTRATION_DESIGN.md](./TASK_ORCHESTRATION_DESIGN.md), [SAFE_NAVIGATION_POLICY.md](./SAFE_NAVIGATION_POLICY.md), [ON_DEVICE_VISUAL_UNDERSTANDING.md](./ON_DEVICE_VISUAL_UNDERSTANDING.md), [DEVICE_VALIDATION_GUIDE.md](./DEVICE_VALIDATION_GUIDE.md), and [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md).
