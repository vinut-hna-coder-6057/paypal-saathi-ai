# Error recovery design

## Status

**IMPLEMENTED AND UNIT-TESTED** for deterministic task/screen recovery. Technical backend/model failures continue to use their existing explicit local fallback paths.

`TaskRecoveryManager` classifies screen unavailable, stale/unknown/unexpected screen, loading, navigation away, uncertain payment outcome, expired task, storage failure, and service unavailable conditions. Recovery output states whether a task may continue, whether a new observation is required, whether automatic retry is permitted, and a short user-facing explanation. The current policy allows no automatic retry.

`TaskOrchestrator` clears stale recommendations and re-plans only from a new fresh observation. Loading waits; unknown and unsupported screens request clarification; unexpected screen transitions are not marked complete. Leaving PayPal pauses the task. Service interruption also clears the active screen context and pauses. A restored task is paused and observation-required.

If a payment outcome is uncertain, Saathi tells the user not to retry and to check Activity. It does not claim success or failure, and it does not start another payment. Technical exception details are logged only as exception class/code; private screen text is not logged.

Backend, Ollama, embedding, and RAG outages do not block the local task engine. General-answer failures use the existing fallback/error behavior and do not change task state.

## Validation

```powershell
.\gradlew.bat testDebugUnitTest
```

Tests use synthetic observations and storage. Physical service interruption, storage exhaustion, and network timeout behavior still need device/integration testing.
