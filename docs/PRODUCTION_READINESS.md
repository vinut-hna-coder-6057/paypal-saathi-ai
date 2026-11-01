# Production readiness

## Status summary

- **IMPLEMENTED AND TESTED:** deterministic task engine, bounded task persistence, accessibility hierarchy bounds/sanitization, local screen classification, transition validation, safe-action policy tests, explicit-privacy-consent OCR policy, OIDC configuration validation, and backend authentication/transport controls.
- **IMPLEMENTED, DEVICE VALIDATION PENDING:** overlay lifecycle, AccessibilityService observations, user-triggered safe navigation, one-shot local OCR fallback, and Android browser-based OIDC client/token lifecycle.
- **PARTIALLY IMPLEMENTED / EXTERNAL CONFIGURATION BLOCKED:** remote API sign-in. Android AppAuth PKCE, encrypted token state, token refresh, 401 handling, logout, and optional refresh-token revocation are wired, but no real provider registration or matching deployed issuer/API has been configured or tested.
- **PARTIALLY IMPLEMENTED:** local visual fallback uses bundled Latin/Devanagari OCR and fixed safe hints; sensitive-region behavior and physical-device resource/privacy behavior still require validation.
- **DEFERRED:** voice interaction.
- **NOT RELEASE-READY:** no real PayPal device/locale validation, external accessibility-policy/legal review, or deployed identity/API configuration has been completed.

## Controls currently in source

- Accessibility processing is restricted to the configured PayPal package for hierarchy traversal, bounded by 120 nodes/depth 18, and scheduled off the main observation path.
- Password and sensitive editable values are excluded; screen labels and tree data are not task-persisted or logged.
- Task persistence is app-private, allow-listed, expires after 30 minutes, clears terminal tasks, and restores paused.
- Navigation actions are exact-label allow-listed and require an explicit overlay tap plus live package, screen fingerprint, control enabled/clickable, task-stage, freshness, and duplicate checks.
- Financial data entry and authorization are never executed.
- Android backup is disabled, cleartext networking is limited to loopback/emulator hosts, and production backend configuration requires auth/TLS.
- The backend uses a loopback-only Ollama endpoint and existing RAG fallback behavior.

## Required before release

1. Select and register a real external OIDC provider/public native client; align issuer, audience, scope, signing key, callback URI, and optional revocation endpoint with the protected API. Exercise sign-in, expiry/refresh, 401, and logout using a provider test tenant.
2. Validate accessibility behavior, consented OCR, safe navigation, and fresh-screen revalidation on supported Android versions/devices and PayPal versions/locales, using test accounts only and no actual transactions.
3. Review Android AccessibilityService/overlay use and disclosure against current platform and distribution policies.
4. Complete privacy/legal review, threat modeling, dependency/license review, and operational incident/retention policies.
5. Configure a real HTTPS API host and production signing outside source control; verify release configuration and update process.
6. Test process death, service interruption, permission revocation, storage failures, token refresh failures, and screen changes on-device.
7. Confirm no task, screen, model, or log path retains sensitive financial values or screenshots.

## Commands

From `android/`:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

From `backend/`:

```powershell
npm test
```

From the repository root:

```powershell
git diff --check
git status --short
```

Debug builds and synthetic tests are not evidence of Play Store approval or production release readiness.

## Voice

No microphone, speech recognition, text-to-speech, audio service, audio permission, or voice-specific dependency is implemented. Voice is a separate future milestone.
