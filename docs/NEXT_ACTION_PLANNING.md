# Next-action planning

## Status

**IMPLEMENTED AND TESTED** for deterministic recommendations from accessibility observations. **DEVICE VALIDATION PENDING** for PayPal UI and locale variations.

## Inputs and output

`TaskStepPlanner` uses a structured goal, current task state, fresh immutable `ScreenState`, observed enabled controls, and the workflow registry. Each `TaskStep` contains a fixed step/task ID, current category, exact allow-listed action descriptor when available, user-facing instruction, expected next category, risk, confidence, evidence codes, confirmation requirement, and status.

Recommendations are grounded in an enabled, clickable supported control on the current screen or are explicit manual instructions for sensitive steps. The engine does not invent a button. It refuses to recommend from unavailable, stale (>15 seconds), low-confidence (<75), ambiguous, unknown, unsupported, or mismatched observations.

Duplicate screen identifiers do not cause repeated recommendations. A pending step advances only when the expected category is observed through a validated transition; an unexpected category enters recovery/clarification.

## LLM boundary

The planner is deterministic and does not invoke Ollama. The existing RAG/LLM path remains available for user-submitted explanations and general questions, but cannot select controls, change task state, or override the navigation policy. If the backend/model/retrieval is unavailable, the task engine continues locally.

## Limitations

Confidence is a heuristic score, not a calibrated probability. Category coverage is limited and English-oriented. Transaction details and account data are not interpreted or asserted by the planner. A detected confirmation/status page does not verify payment success.

## Validation

Planner and orchestrator synthetic tests run with:

```powershell
.\gradlew.bat testDebugUnitTest
```
