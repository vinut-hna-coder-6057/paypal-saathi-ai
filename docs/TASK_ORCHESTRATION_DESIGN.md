# Task orchestration design

## Status

**IMPLEMENTED AND TESTED** for the deterministic Android task engine. **DEVICE VALIDATION PENDING** for accessibility behavior on real PayPal versions, devices, and locales.

Voice is explicitly deferred. The consented local visual fallback is an optional observation aid only; see [ON_DEVICE_VISUAL_UNDERSTANDING.md](./ON_DEVICE_VISUAL_UNDERSTANDING.md).

## Ownership and data flow

`ScreenMonitorService` is the single runtime owner of `TaskOrchestrator` and the latest transient `ScreenState`. The existing floating overlay submits task goals and pause/resume/cancel/repeat requests through a narrow listener. For a task, the service passes newly built screen observations and transition evidence to the deterministic engine; it does not send every accessibility event to the backend or an LLM. General help questions retain the existing RAG path.

`GoalUnderstandingEngine` normalizes supported natural-language phrasings into an enum goal. It does not retain the original request. Ambiguous payment requests create a clarification state. `TaskWorkflowRegistry` and `TaskStepPlanner` use actual observed screen categories and enabled, safe-to-recommend controls. A recommendation is not proof that the user acted. `TaskOrchestrator` advances only after a fresh expected screen transition.

## Supported task types

- Send money: navigation assistance through the observed Send entry; recipient selection, amount entry, review, and authorization remain manual.
- View activity, check transaction status, and find a refund: guide to Activity, then ask the user to inspect the transaction directly.
- Understand a payment error: explain only what the currently observed error screen supports; no payment retry.
- Open Wallet.
- Account Settings: open the observed Settings entry and stop at a confidently classified account-settings page. Local `account_settings` is mapped to `unknown` before backend submission.

PayPal screen variants and regional availability are not guaranteed. Unsupported or uncertain screens request clarification rather than invent a route.

## State and recovery

The state machine includes idle, goal understanding, observation, planning, waiting, transition verification, recovery, clarification, pause, completion, cancellation, and failure. Invalid transitions are rejected. Duplicate screen fingerprints do not repeat planning. Pausing, cancellation, task replacement, and completion are explicit.

`TaskStateRepository` stores only a random task ID, generic goal type, lifecycle status, fixed step IDs, canonical screen category/identifier, bounded retry count, and timestamps. It excludes the original request, recipient, amount, transaction data, control labels, raw accessibility text, and images. Active tasks expire after 30 minutes. Restoration clears the live step object, expected screen, and prior observation and always returns paused; the fixed step ID `review_details` is retained only to prevent restarting an uncertain send-money flow. The user must resume and the service must obtain a fresh observation; if that step was interrupted, Saathi asks the user to check Activity before continuing.

## Validation

From `android/`:

```powershell
.\gradlew.bat testDebugUnitTest
```

Tests use synthetic screen metadata and fake storage; they do not sign in to PayPal or transact.
