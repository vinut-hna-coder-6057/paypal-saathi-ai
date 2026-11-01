# Manual Android device validation

## Scope and safety rules

This guide is for controlled compatibility checks, not a financial transaction
test. Use a dedicated test account and supported test environment. Never tap a
button that sends, authorizes, or completes a payment. Do not enter real
recipient, amount, account, credential, or transaction data into Saathi. If a
scenario would require a real payment to reach its expected screen, stop and
use the synthetic regression test described in scenario 12.

The Android goal/task engine runs locally. A backend is optional for these
scenarios; local API development uses the loopback configuration. Remote API
validation requires a separately registered OIDC test client and matching
protected API configuration. Do not use production identity credentials.

For evidence, record device model, Android version, PayPal app version, locale,
Saathi debug build version, scenario number, and pass/fail. Do not include
account identifiers, transaction details, screenshots, credentials, or
unreviewed logs in a report. Optional diagnostic logs:

```powershell
adb logcat -s SaathiScreenMonitor SaathiOverlay
```

The current log tags report lifecycle and error conditions, not task prompts,
accessible text, transaction data, or other-app package names. Keep collected
logs local and review them before sharing. A category shown in the overlay is a
heuristic, not proof of PayPal screen identity.

## Scenarios

### 1. PayPal app launch

- **Preconditions:** Install the debug build; grant the overlay permission if
  Android requests it; leave the accessibility service enabled.
- **Steps:** Launch the official PayPal app. Observe the Saathi floating button,
  expand it, minimize it, and expand it again.
- **Assistant:** The overlay appears only while PayPal is foregrounded and
  presents the available screen   category or a conservative unknown state. Optional visual OCR remains off
  unless separately enabled in Saathi.
- **Safety:** Saathi does not navigate or activate PayPal controls merely
  because the app launched.
- **Pass/fail:** Pass if the overlay can be opened/minimized without blocking
  PayPal permanently and no action occurs without a user gesture. Fail if it
  appears over unrelated apps or triggers a PayPal control.
- **Evidence:** `SaathiOverlay` lifecycle/error messages; no screen text should
  appear in logcat.

### 2. Sign-in screen detection

- **Preconditions:** PayPal is signed out or shows its sign-in screen. Use the
  official PayPal app only.
- **Steps:** Open the overlay and inspect its category. Do not enter
  credentials into Saathi. If normal sign-in is needed to continue later, enter
  credentials only in PayPal.
- **Assistant:** It identifies a sign-in screen when accessible evidence is
  sufficient; otherwise it reports unknown and asks for a general description.
- **Safety:** Saathi must not read or request a password, PIN, or OTP, and must
  not offer to fill or submit sign-in fields.
- **Pass/fail:** Pass if no credential is displayed in the overlay or diagnostic
  logs and no sign-in action is automated. Incorrect confident classification
  is a compatibility failure; unknown is a safe fallback.
- **Evidence:** Overlay category and `SaathiScreenMonitor` errors only; do not
  capture or share the sign-in screen.

### 3. Home screen detection

- **Preconditions:** A dedicated test account is signed in, with no payment
  operation in progress.
- **Steps:** Open PayPal Home and expand Saathi.
- **Assistant:** It reports Home only when the classifier has sufficient
  evidence; otherwise it reports unknown.
- **Safety:** No navigation is executed automatically.
- **Pass/fail:** Pass for correct Home recognition or a safe unknown fallback.
  Fail for a confident incorrect category or any unsolicited action.
- **Evidence:** Overlay category and lifecycle/error logs; do not record account
  balances or other accessible content.

### 4. Navigation to Account Settings

- **Preconditions:** PayPal Home is visible and the Settings control is
  available.
- **Steps:** Submit “Open account settings.” Confirm the displayed task and
  observed Settings navigation suggestion. Tap the Saathi **Open Settings**
  button, if offered. Otherwise use PayPal manually.
- **Assistant:** It plans from the current Home observation and marks the task
  complete only after a fresh Account Settings observation.
- **Safety:** Only the explicitly selected, observed navigation control may be
  opened. Saathi must not change settings.
- **Pass/fail:** Pass if the task waits for the screen transition and completes
  only when settings is observed; fail if it claims completion before then or
  changes a setting.
- **Evidence:** `SaathiScreenMonitor` safe-navigation/lifecycle errors and the
  overlay's task status. Do not log settings values.

### 5. Start a supported non-financial task

- **Preconditions:** The test account is signed in and PayPal Home is visible.
- **Steps:** Submit “Open Activity.” Review the proposed step. Tap the matching
  Saathi navigation button only if shown; otherwise navigate manually in
  PayPal.
- **Assistant:** It gives a deterministic step and updates progress after a
  fresh Activity observation.
- **Safety:** It does not inspect or repeat transaction details and does not
  select or modify a transaction.
- **Pass/fail:** Pass if the Activity destination is observed before the task
  completes and transaction details remain user-reviewed in PayPal. Fail if
  Saathi claims to know transaction status.
- **Evidence:** Overlay status and lifecycle/error logs; avoid recording
  Activity content.

### 6. Completing and verifying task steps

- **Preconditions:** A supported non-financial task is active on a test
  account.
- **Steps:** For Account Settings or Activity, follow the displayed manual
  instruction or explicitly tap the offered safe-navigation button. Wait for
  the destination screen; repeat once with a delayed transition if practical.
- **Assistant:** It remains waiting while the prior screen is unchanged or
  loading, then advances only after a fresh expected-screen transition.
- **Safety:** No step advances because of a tap result alone; no financial
  control is activated.
- **Pass/fail:** Pass if unchanged/loading observations do not mark progress
  complete and the correct destination does. Fail if a stale observation or
  unrelated screen completes a step.
- **Evidence:** Overlay progress/status; relevant synthetic coverage is in
  `TaskOrchestratorTest` and `ScreenTransitionDetectorTest`.

### 7. Back navigation

- **Preconditions:** A supported task is waiting for a user-controlled step.
- **Steps:** Use Android/PayPal Back (not a Saathi action) to return to a prior
  PayPal screen. Wait for re-observation.
- **Assistant:** It recognizes the changed screen if supported; otherwise it
  asks for clarification or recovery and requires a fresh observation.
- **Safety:** It must not treat Back as successful completion of the pending
  step, execute a stale safe action, or resume a financial action.
- **Pass/fail:** Pass if progress is not advanced without the expected
  transition and a stale overlay action is rejected. Fail if it acts on the
  prior screen's control.
- **Evidence:** Overlay status and `SaathiScreenMonitor` safe-navigation
  rejection messages.

### 8. App switching and returning

- **Preconditions:** A non-financial task is active in PayPal.
- **Steps:** Switch to another app, confirm Saathi is no longer visible, then
  return to PayPal and explicitly resume if prompted.
- **Assistant:** It hides outside PayPal, pauses the active task, and checks a
  fresh screen after return.
- **Safety:** No background PayPal action occurs, and no screen from the other
  app is classified or displayed as PayPal content.
- **Pass/fail:** Pass if the overlay hides and the task waits for an explicit
  resume/fresh observation. Fail if it remains over the unrelated app or
  resumes a safe action automatically.
- **Evidence:** `SaathiScreenMonitor` lifecycle messages; logs must not reveal
  the other app's package name.

### 9. Accessibility service interruption and restart

- **Preconditions:** A non-financial task is active and its current step has
  not been completed.
- **Steps:** Disable **PayPal Saathi AI Screen Monitor** in Android
  Accessibility settings. Re-enable it, return to PayPal, and inspect task
  status. Resume only through the overlay.
- **Assistant:** It pauses on interruption and requires a fresh observation
  before further guidance.
- **Safety:** It does not automatically click the previously offered control.
- **Pass/fail:** Pass if the task is paused and an explicit resume/fresh screen
  check is required. Fail if a stale action is replayed.
- **Evidence:** `SaathiScreenMonitor` interruption/connection messages and
  overlay task status.

### 10. Unexpected or unrecognized screen

- **Preconditions:** A supported non-financial task is active.
- **Steps:** Navigate manually to a PayPal page not covered by the supported
  categories, or use a locale/page that lacks the expected accessible labels.
- **Assistant:** It reports unknown/ambiguous or asks for clarification rather
  than guessing a step.
- **Safety:** No safe action is offered from unknown, sign-in, security,
  loading, error, amount, recipient, review, or confirmation screens.
- **Pass/fail:** Pass if it fails closed and waits for user input/fresh
  observation. Fail if it confidently misclassifies or acts.
- **Evidence:** Overlay category and
  `ScreenClassifierTest`/`SafeActionPolicyTest`; do not share page contents.

### 11. Interrupted task restoration

- **Preconditions:** A non-financial Account Settings task is waiting at Home;
  do not tap its navigation button yet.
- **Steps:** Stop/restart the Saathi process using the test device's normal
  developer workflow, reopen/enable the accessibility service if Android
  requires it, return to PayPal, and inspect the restored task. Resume
  explicitly.
- **Assistant:** The task returns paused, uses only bounded generic saved
  state, and requires a fresh observation.
- **Safety:** No stored action is replayed; expired/invalid state is cleared
  safely.
- **Pass/fail:** Pass if it is paused and asks for a fresh screen before
  planning. Fail if the task auto-navigates or resumes with stale screen
  context.
- **Evidence:** `SaathiScreenMonitor` restoration errors/status and the
  synthetic `TaskStateRepositoryTest` cases.

### 12. Uncertain payment outcome recovery — no-payment test

- **Preconditions:** Do not create or submit a payment. The existing debug
  project and unit-test environment are available.
- **Steps:** Run
  `.\gradlew.bat testDebugUnitTest --tests com.vinuthna.paypalsaathi.tasks.TaskOrchestratorTest`
  from `android/`. Review the regression that simulates an interrupted review,
  an unverified status transition, a status page, and the required Activity
  review acknowledgement. Do not tap any PayPal send/authorize control.
- **Assistant:** An uncertain result directs the user to Activity, says not to
  retry, does not claim success/failure, and cannot finish the guided send
  task until Activity is observed and the user acknowledges reviewing it.
- **Safety:** No automatic retry or payment submission is allowed. No real
  transaction is needed for this regression.
- **Pass/fail:** Pass if the synthetic test suite succeeds and asserts the
  Activity gate and explicit acknowledgement. Device-level simulation of a
  post-authorization uncertain result is **blocked** because there is no
  non-transactional device fixture; do not replace it with a real payment.
- **Evidence:** The named Gradle test result. A physical-device uncertain
  outcome is not claimed or requested.

### 13. Floating overlay behavior

- **Preconditions:** PayPal is foreground and the accessibility service is
  enabled.
- **Steps:** Expand/minimize the floating button, scroll the panel, use
  Repeat/Pause/Resume/Stop on a non-financial task, and switch away from
  PayPal.
- **Assistant:** Controls remain responsive; task controls reflect the actual
  state; overlay hides when PayPal is no longer foreground.
- **Safety:** Repeat only repeats guidance. Pause/Stop never authorizes or
  retries a payment. Minimize the overlay before interacting with covered
  PayPal controls.
- **Pass/fail:** Pass if the panel does not trap interaction and all controls
  match their labels/state. Fail if hidden controls remain clickable or a
  task action runs without the corresponding user tap.
- **Evidence:** `SaathiOverlay` lifecycle/errors and visible task status.

### 14. Sensitive information privacy

- **Preconditions:** Use a dedicated test device and fake values only. Keep
  diagnostic logs local.
- **Steps:** Verify the privacy disclosure. Confirm PayPal editable values do
  not appear in Saathi. If validating question-input blocking, use only a
  clearly fake test string (for example, `OTP is 123456`) and confirm Saathi
  rejects it before sending. Switch to another app and inspect the filtered
  logs without clearing or sharing them.
- **Assistant:** It blocks common sensitive-value patterns in typed help
  questions and gives a general correction message; no screen text or task
  prompt is written to diagnostic logs.
- **Safety:** Never substitute real credentials, phone numbers, identifiers,
  amounts, or transaction details. Do not save or share screenshots.
- **Pass/fail:** Pass if the fake sensitive input is rejected, no values or
  non-PayPal package names appear in logs, and OCR remains off unless its
  separate consent is enabled. Pattern matching is not a guarantee for every
  sensitive value.
- **Evidence:** `QuestionInputValidatorTest`, `ScreenObservationBuilderTest`, and
  filtered local logs after manual review.

### 15. OIDC sign-in, refresh, and sign-out

- **Preconditions:** A provider test tenant has registered the Android public
  client, exact redirect URI, Authorization Code + PKCE, API audience, and
  required scope. Use a matching test API with TLS and no real PayPal login.
- **Steps:** Configure the documented OIDC build properties, build/install the
  debug app, sign in through the system browser, request protected guidance,
  exercise token refresh using the provider's short-lived test token, then sign
  out. If a test-only API can return 401, verify the next protected request
  requires sign-in.
- **Assistant:** Sign-in status reflects authorization state; protected
  requests attach a refreshed bearer token and health checks remain public.
  Sign-out clears local state and reports whether provider refresh-token
  revocation was configured and succeeded.
- **Safety:** Never put a client secret in the app. Do not use PayPal app
  credentials, send tokens to logs, or test a payment.
- **Pass/fail:** Pass if PKCE browser sign-in, refresh, 401 reauthentication,
  local logout, and any configured provider logout/revocation work without
  exposing a token. Fail if a protected request proceeds without a token,
  refresh failure silently falls back, or a token appears in logs/storage
  outside the encrypted auth state.
- **Evidence:** Gradle OIDC configuration/unit tests, provider test-tenant
  result, protected API status, and filtered logs. Do not include tokens,
  identity details, or raw provider responses in evidence.

### 16. Consented on-device OCR fallback

- **Preconditions:** Android 11+ device/emulator, PayPal foreground on a
  non-financial test screen, accessibility enabled, and no actual transaction
  in progress. Do not use sign-in, recipient, amount, review, confirmation, or
  account-detail screens for this check.
- **Steps:** With consent off, use a supported synthetic/unknown non-financial
  screen and verify accessibility-only fallback. Enable OCR using the explicit
  consent prompt, repeat once, then disable consent and repeat. Do not capture
  screenshots or copy recognized text.
- **Assistant:** Only an allow-listed generic heading may improve screen
  classification. No OCR-derived control is offered; navigation still
  requires fresh accessibility evidence and an explicit Saathi tap.
- **Safety:** Image/text remain local and transient; OCR is never sent to the
  API, model, logs, or task persistence. No payment, credentials, or
  authentication-screen interaction is requested.
- **Pass/fail:** Pass if disabled/unsupported/capture-failure cases remain
  accessibility-only, consent enables at most a local hint, and screen changes
  invalidate stale hints. Fail on any image/text retention, log disclosure,
  OCR-generated action, or financial-screen capture.
- **Evidence:** `VisualFallbackPolicyTest`, `ScreenObservationBuilderTest`,
  filtered lifecycle/error logs, and a manual device result without screenshots
  or screen text.

## Release gate

Record each device result as pass, fail, or blocked; include the tested device,
Android version, PayPal version, and locale. Any incorrect confident screen
classification, stale action, unexpected financial action, sensitive-data
exposure, or payment-success claim is a stop-ship failure. Passing this guide
does not substitute for external accessibility/privacy review, provider
registration, physical-device validation, or production release configuration.
