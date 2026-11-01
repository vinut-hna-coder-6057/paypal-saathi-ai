package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaskState {
    public enum Status {
        IDLE,
        UNDERSTANDING_GOAL,
        OBSERVING_SCREEN,
        PLANNING_STEP,
        WAITING_FOR_USER,
        VERIFYING_TRANSITION,
        RECOVERING,
        NEEDS_CLARIFICATION,
        PAUSED,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    private final TaskGoal goal;
    private final Status status;
    private final String currentStepId;
    private final List<String> completedStepIds;
    private final String expectedNextScreen;
    private final String lastObservedScreenIdentifier;
    private final String lastObservedCategory;
    private final long lastObservationTimestamp;
    private final TaskStep.Status currentStepStatus;
    private final int retryCount;
    private final String pendingClarification;
    private final String lastTransitionReason;
    private final long createdAt;
    private final long updatedAt;

    TaskState(
            TaskGoal goal,
            Status status,
            String currentStepId,
            List<String> completedStepIds,
            String expectedNextScreen,
            String lastObservedScreenIdentifier,
            String lastObservedCategory,
            long lastObservationTimestamp,
            TaskStep.Status currentStepStatus,
            int retryCount,
            String pendingClarification,
            String lastTransitionReason,
            long createdAt,
            long updatedAt
    ) {
        this.goal = goal;
        this.status = status == null ? Status.IDLE : status;
        this.currentStepId = safeId(currentStepId);
        this.completedStepIds = immutableIds(completedStepIds);
        this.expectedNextScreen = ScreenCategories.isKnownCategory(expectedNextScreen)
                ? expectedNextScreen
                : null;
        this.lastObservedScreenIdentifier =
                safeScreenIdentifier(lastObservedScreenIdentifier);
        this.lastObservedCategory = ScreenCategories.isKnownCategory(
                lastObservedCategory) ? lastObservedCategory : ScreenCategories.UNKNOWN;
        this.lastObservationTimestamp = Math.max(0L, lastObservationTimestamp);
        this.currentStepStatus = currentStepStatus;
        this.retryCount = Math.max(0, Math.min(retryCount, 3));
        this.pendingClarification = safeClarification(pendingClarification);
        this.lastTransitionReason = safeReason(lastTransitionReason);
        this.createdAt = Math.max(0L, createdAt);
        this.updatedAt = Math.max(0L, updatedAt);
    }

    static TaskState idle(long now) {
        return new TaskState(null, Status.IDLE, null, null, null, null,
                ScreenCategories.UNKNOWN, 0L, null, 0, null,
                "idle", now, now);
    }

    TaskState transition(
            Status next,
            String reason,
            String stepId,
            List<String> completed,
            String expectedScreen,
            String screenIdentifier,
            String screenCategory,
            long observedAt,
            TaskStep.Status stepStatus,
            int retries,
            String clarification,
            long now
    ) {
        return new TaskState(
                goal,
                next,
                stepId,
                completed,
                expectedScreen,
                screenIdentifier,
                screenCategory,
                observedAt,
                stepStatus,
                retries,
                clarification,
                reason,
                createdAt,
                now);
    }

    TaskState withGoal(TaskGoal newGoal, Status next, String reason, String clarification, long now) {
        return new TaskState(newGoal, next, null, Collections.emptyList(), null,
                null, ScreenCategories.UNKNOWN, 0L, null, 0, clarification,
                reason, newGoal.createdAt(), now);
    }

    TaskState restoredPaused(long now) {
        return transition(Status.PAUSED, "restored_requires_fresh_observation",
                "review_details".equals(currentStepId)
                        ? currentStepId : null,
                completedStepIds, null, null, ScreenCategories.UNKNOWN,
                0L, null, retryCount,
                "uncertain_payment".equals(pendingClarification)
                        ? pendingClarification : null,
                now);
    }

    private static List<String> immutableIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            String safe = safeId(value);
            if (safe != null && !copy.contains(safe)) {
                copy.add(safe);
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static String safeId(String value) {
        return value != null && value.matches("[a-z_]{1,48}")
                ? value : null;
    }

    private static String safeScreenIdentifier(String value) {
        return value != null && value.matches("[a-z_]+:[a-f0-9]{16}")
                ? value : null;
    }

    private static String safeClarification(String value) {
        switch (value == null ? "" : value) {
            case "ambiguous_goal":
            case "unsupported_workflow":
            case "unknown_screen":
            case "stale_observation":
            case "unexpected_screen":
            case "uncertain_payment":
                return value;
            default:
                return null;
        }
    }

    private static String safeReason(String value) {
        switch (value == null ? "" : value) {
            case "idle":
            case "goal_understood":
            case "clarification_required":
            case "fresh_observation_required":
            case "step_planned":
            case "expected_screen_observed":
            case "loading_observed":
            case "unexpected_screen_observed":
            case "screen_unavailable":
            case "screen_not_supported":
            case "unexpected_screen":
            case "unknown_screen":
            case "user_paused":
            case "user_resumed":
            case "user_cancelled":
            case "user_confirmed_guidance_complete":
            case "observed_status_screen":
            case "task_expired":
            case "restored_requires_fresh_observation":
            case "clarification_resolved":
            case "workflow_not_supported":
            case "stale_observation":
            case "user_navigated_away":
            case "uncertain_payment":
                return value;
            default:
                return "state_updated";
        }
    }

    public TaskGoal goal() { return goal; }
    public Status status() { return status; }
    public String currentStepId() { return currentStepId; }
    public List<String> completedStepIds() { return completedStepIds; }
    public String expectedNextScreen() { return expectedNextScreen; }
    public String lastObservedScreenIdentifier() { return lastObservedScreenIdentifier; }
    public String lastObservedCategory() { return lastObservedCategory; }
    public long lastObservationTimestamp() { return lastObservationTimestamp; }
    public TaskStep.Status currentStepStatus() { return currentStepStatus; }
    public int retryCount() { return retryCount; }
    public String pendingClarification() { return pendingClarification; }
    public String lastTransitionReason() { return lastTransitionReason; }
    public long createdAt() { return createdAt; }
    public long updatedAt() { return updatedAt; }
}
