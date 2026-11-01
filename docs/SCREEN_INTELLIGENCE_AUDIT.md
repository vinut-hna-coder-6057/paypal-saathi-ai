# Screen Intelligence Audit — Milestone 2 baseline

This is the audit of the screen-observation milestone before task orchestration and safe navigation were added. For current behavior, see [ARCHITECTURE.md](./ARCHITECTURE.md) and [SAFE_NAVIGATION_POLICY.md](./SAFE_NAVIGATION_POLICY.md).

## Scope

This audit covers the Android screen-observation path in Milestone 2. It reviewed the accessibility service and configuration, classifier, screen context and request handling, floating overlay, app activity, API client, manifest, and unit tests. Findings are based on the checked-in source; the classifier fixtures are synthetic and are not claims of compatibility with every PayPal screen.

## Existing implementation and reusable components

- `ScreenMonitorService` is the sole accessibility service and is restricted to the official PayPal package for hierarchy collection. It already owns foreground handling, bounded node traversal, category persistence, app-private broadcasts, and uncertainty recovery.
- `ScreenContext` provides exact navigation-label and coarse action-position allowlists. `CategoryEventGate` suppresses repeated category broadcasts, while `RootUnavailableCategoryPolicy` prevents brief missing-root events from immediately replacing a recently confirmed category.
- `FloatingAssistantOverlay` preserves the existing expand/minimize, question, retry, and status behavior. It accepts category and safe context metadata and already gives an uncertainty explanation for unknown screens.
- `GuidanceClient` and `QuestionRequestTracker` provide the existing request flow and invalidate in-flight answers when screen context changes. Only allow-listed metadata is serialized for an explicitly submitted question.
- `ScreenClassifier` is deterministic and local. Its confidence is a rule-based evidence score, not a calibrated probability. Unknown, unsupported-package, ambiguous, and conflicting evidence remain conservative.
- The screen model and pipeline are separated into `ObservedUiElement`, `ScreenDataSanitizer`, `ScreenElementExtractor`, `ScreenObservationBuilder`, `ScreenState`, `ScreenElement`, and `ScreenTransitionDetector`. The pure Java portions can be tested without PayPal account data or screenshots.

## Milestone 2 data flow

1. A PayPal accessibility event passes the existing foreground policy.
2. The latest event is debounced. The active root is obtained, then bounded traversal, sanitization, model construction, classification, and fingerprinting run on a dedicated worker thread.
3. Traversal is capped at 120 nodes and depth 18. Password nodes and sensitive editable subtrees are skipped; editable values are never read. Text and descriptions are sanitized and bounded before entering the structured model.
4. The builder emits an immutable `ScreenState` with a canonical category, confidence/evidence, sanitized elements, allow-listed safe action descriptors, observation time, and stable fingerprint. Unavailable observations remain explicit.
5. The transition detector compares fresh consecutive observations. It distinguishes unchanged/partial changes, screen changes, loading start/finish, unknown transitions, unavailable observations, and stale data.
6. Only the current category, exact allow-listed labels, coarse action descriptors, and bounded classification metadata are broadcast or optionally included with a question the user submits. No raw hierarchy or `ScreenState` is sent to the backend.
7. At the Milestone 2 baseline, the observer was read-only. The later navigation capability is separate: an explicit user tap may execute a narrowly allow-listed navigation node after live revalidation. It never enters data, submits/repeats payments, or bypasses verification.

## Privacy boundaries

- Accessibility node objects stay inside the service observation layer and are recycled after traversal.
- Editable text values are not read. Password and sensitive editable nodes do not contribute labels or descendants to the model.
- Sanitized text can still include non-sensitive account-specific names in transient memory; the model is not persisted or logged. Only exact generic labels and coarse location bands cross the app boundary.
- The backend continues to receive question context only after explicit submission. Backend authentication, authorization, HTTPS requirements, and the local RAG pipeline are outside this milestone and must remain unchanged.

## Gaps and limitations

- Classification is a deterministic ruleset with only selected English, Hindi, and Telugu phrases; localization coverage and real PayPal UI behavior have not been validated on devices.
- A confidence score describes rule evidence only. It does not prove the screen identity or payment status.
- Element IDs and full parent/child relationships are not persisted in the model. The implementation uses bounded class/role mapping, sanitized labels, and coarse action positions rather than stable PayPal view identifiers.
- Supported safe navigation actions are an intentionally small exact allowlist. Financial decisions, authentication, editable controls, and unknown-risk controls cannot be recommended as safe actions.
- At the time of this baseline audit, task orchestration had not yet been added. Current task behavior is documented in [TASK_ORCHESTRATION_DESIGN.md](./TASK_ORCHESTRATION_DESIGN.md).
- Voice and OCR/vision remain unimplemented; task persistence and user-triggered safe navigation were added in later milestones. Real-device validation remains pending.

## Proposed responsibilities

Existing code is reused rather than duplicated:

| Responsibility | Existing component |
|---|---|
| Accessibility event and package boundary | `ScreenMonitorService` |
| Bounded hierarchy extraction | `ScreenMonitorService.collectObservation` |
| Sensitive-data filtering | `ScreenDataSanitizer` |
| Explicit role, risk, and action metadata | `ScreenElementExtractor`, `ScreenElement` |
| Immutable state, confidence, and fingerprint | `ScreenObservationBuilder`, `ScreenState` |
| Deterministic category classification | `ScreenClassifier` |
| Consecutive observation comparison | `ScreenTransitionDetector` |
| Safe metadata serialization | `ScreenContext`, `GuidanceClient` |
| Existing user interaction | `FloatingAssistantOverlay` |

## Verification plan

- Unit-test model immutability, null/invalid metadata, fingerprint consistency, role and risk mapping, disabled controls, sensitive-value filtering, and unknown classification.
- Use synthetic accessibility observations to cover supported categories, localization samples, conflicts, low confidence, and unsupported roles.
- Test repeated observations, partial changes, loading start/finish, stale/unavailable observations, and confidence-gated screen changes.
- Run Android unit tests, Android lint, and a debug APK build. Verify the backend suite remains unaffected and `git diff --check` passes.
- Separately validate accessibility behavior, consent, overlay usability, supported PayPal versions, and performance on real devices; automated synthetic tests do not establish device compatibility.
