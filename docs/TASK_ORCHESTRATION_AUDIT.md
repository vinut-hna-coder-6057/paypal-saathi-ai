# Task Orchestration Implementation Audit

This document records the baseline findings and constraints before task orchestration was implemented. The current implementation is described in [TASK_ORCHESTRATION_DESIGN.md](./TASK_ORCHESTRATION_DESIGN.md) and [SAFE_NAVIGATION_POLICY.md](./SAFE_NAVIGATION_POLICY.md).

## Scope and current state

This audit covers the Android guidance submission path, accessibility service, structured screen model, screen transition detector, and floating overlay before Milestone 3 implementation.

- `ScreenMonitorService` is already the owner of the accessibility lifecycle and screen-observation sequence. It holds the latest `ScreenState` transiently, computes `ScreenTransitionDetector.Result`, updates the overlay, and broadcasts only minimized category/navigation metadata.
- `ScreenState` exposes confidence, ambiguity, evidence, freshness, categories, safe action descriptors, and a fingerprint suitable for deterministic planning. Financial/unknown-risk controls are not safe recommendations.
- `ScreenTransitionDetector` confirms category transitions only from fresh observations with adequate confidence. A recommendation by itself is not evidence that the user acted.
- `FloatingAssistantOverlay` currently stores an `activeGoal` string and repeats the whole question to the backend after screen updates. That behavior is request-oriented, does not own structured progress, and cannot verify steps. The overlay currently owns pause/cancel-adjacent presentation behavior but is not a suitable task-state owner.
- `GuidanceClient` and `QuestionRequestTracker` provide the existing user-submitted RAG question path and safe context serialization. They should remain available for general questions, but not determine task progression.
- `CategoryStore` persists screen category/status metadata only. There are no task goal, task state machine, workflow, step planner, or task persistence components.
- The existing overlay layout has a single stop action and answer panel; it has no task progress, repeat, pause, or resume presentation.
- Android unit tests are plain JUnit and can directly test deterministic Java models. Android persistence can use app-private `SharedPreferences`, with a pure Java codec/storage seam for process-restoration tests.

## Design and safety constraints

1. Keep task ownership in `ScreenMonitorService`, not the overlay; expose a narrow listener for overlay goal/pause/resume/cancel/repeat requests.
2. Keep goal interpretation, workflow rules, planning, and orchestration deterministic and local. Continue to route non-goal/general help questions through the current RAG client.
3. Store only task type, fixed workflow step IDs, canonical categories, bounded confidence/timestamps/retry count, and lifecycle state. Never retain the raw user request, recipient, amount, transaction ID, field value, accessibility tree, or screen labels in task persistence.
4. Persist active task state across service/activity recreation, but clear terminal states immediately and expire abandoned active state after a short bounded retention interval. On restore, require a fresh screen observation and re-plan; never resume a stale action automatically.
5. Recommend only fresh enabled safe-navigation descriptors actually present in `ScreenState`. Sensitive input, recipient choice, financial authorization, and transaction retry remain user-controlled.
6. Advance a step only after a fresh expected screen transition is observed. Duplicate, unavailable, stale, loading, unknown, and unexpected observations cannot complete a pending step.
7. Preserve existing screen classification, backend authentication, RAG, and general question UX.

## Planned implementation boundaries

- Add immutable `TaskGoal`, `TaskState`, and `TaskStep` models plus deterministic `GoalUnderstandingEngine`, `TaskWorkflowRegistry`, `TaskStepPlanner`, and `TaskOrchestrator` in a dedicated `tasks` package.
- Add `TaskStateRepository` for bounded non-sensitive local persistence and test its encoding/restoration/expiry without storing raw input.
- Have the service feed actual screen observations and transition evidence to the orchestrator and publish task snapshots to the overlay.
- Extend the existing overlay with goal-specific task controls and progress while keeping general question/RAG behavior intact.
- Add synthetic unit tests for intent recognition, state transitions, workflow planning, persistence, recovery, and a multi-screen send-money path.
- Document the implemented behavior and limitations separately in `docs/TASK_ORCHESTRATION_DESIGN.md`.

## Known limitations before implementation

- No real PayPal device/locale validation is available from the checked-in tests.
- Current screen categories and safe-navigation allowlists are incomplete; unsupported steps must ask the user or stop rather than infer controls.
- The status category does not establish that a payment succeeded. User-facing task completion must not be represented as a verified financial transaction.
- OIDC login/token acquisition and all backend security work are unrelated and must not be modified for this milestone.
