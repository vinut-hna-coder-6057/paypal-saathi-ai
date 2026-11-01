package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;
import com.vinuthna.paypalsaathi.detection.ScreenState;
import com.vinuthna.paypalsaathi.detection.ScreenTransitionDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaskOrchestrator {
    private final TaskStepPlanner planner;
    private final TaskRecoveryManager recoveryManager =
            new TaskRecoveryManager();
    private TaskState state;
    private TaskStep currentStep;
    private String currentMessage;

    public TaskOrchestrator() {
        this(new TaskStepPlanner(new TaskWorkflowRegistry()));
    }

    public TaskOrchestrator(TaskStepPlanner planner) {
        this.planner = planner;
        state = TaskState.idle(System.currentTimeMillis());
    }

    public TaskState state() { return state; }
    public TaskStep currentStep() { return currentStep; }
    public String currentMessage() { return currentMessage; }
    public void showMessage(String message) { currentMessage = message; }
    public void repeatInstruction() {
        if (currentStep != null) {
            currentMessage = currentStep.instruction();
        }
    }

    public boolean start(TaskGoal goal, String clarification, long now) {
        if (goal == null || !canReplaceTask()) {
            return false;
        }
        currentStep = null;
        TaskState.Status initial = TaskState.Status.UNDERSTANDING_GOAL;
        state = new TaskState(
                goal,
                initial,
                null,
                Collections.emptyList(),
                null,
                null,
                ScreenCategories.UNKNOWN,
                0L,
                null,
                0,
                null,
                "goal_understood",
                goal.createdAt(),
                now);
        if (goal.clarificationRequired()) {
            transition(TaskState.Status.NEEDS_CLARIFICATION,
                    "clarification_required", null, null, null, null,
                    ScreenCategories.UNKNOWN, 0L, null, 0,
                    "ambiguous_goal", now);
            currentMessage = clarification == null
                    ? "What would you like help with? Please describe the PayPal task."
                    : clarification;
        } else {
            transition(TaskState.Status.OBSERVING_SCREEN,
                    "fresh_observation_required", null, null, null, null,
                    ScreenCategories.UNKNOWN, 0L, null, 0, null, now);
            currentMessage = "Okay, I will help you one step at a time.";
        }
        return true;
    }

    public boolean resolveClarification(TaskGoal goal, long now) {
        if (state.status() != TaskState.Status.NEEDS_CLARIFICATION
                || goal == null
                || goal.clarificationRequired()) {
            return false;
        }
        TaskGoal continuedGoal = new TaskGoal(
                state.goal().taskId(),
                goal.type(),
                state.createdAt(),
                goal.confidence(),
                false);
        transition(TaskState.Status.UNDERSTANDING_GOAL,
                "clarification_resolved", null, null, null, null,
                ScreenCategories.UNKNOWN, 0L, null, state.retryCount(),
                null, now, continuedGoal);
        transition(TaskState.Status.OBSERVING_SCREEN,
                "fresh_observation_required", null, null, null, null,
                ScreenCategories.UNKNOWN, 0L, null, state.retryCount(),
                null, now);
        currentStep = null;
        currentMessage = "Thanks. I will check the current PayPal screen.";
        return true;
    }

    public TaskState observe(
            ScreenState screen,
            ScreenTransitionDetector.Result transition,
            long now
    ) {
        if (state.goal() == null
                || state.status() == TaskState.Status.PAUSED
                || isTerminal(state.status())
                || state.status() == TaskState.Status.NEEDS_CLARIFICATION) {
            return state;
        }
        if (screen == null || !screen.observationAvailable()) {
            if (state.status() == TaskState.Status.WAITING_FOR_USER) {
                transition(TaskState.Status.VERIFYING_TRANSITION,
                        "screen_unavailable",
                        state.currentStepId(),
                        state.completedStepIds(),
                        state.expectedNextScreen(),
                        state.lastObservedScreenIdentifier(),
                        state.lastObservedCategory(),
                        state.lastObservationTimestamp(),
                        state.currentStepStatus(),
                        state.retryCount(),
                        state.pendingClarification(),
                        now);
            }
            currentMessage = isUncertainPaymentStep()
                    ? recoveryManager.recover(
                            TaskRecoveryManager.Problem.UNCERTAIN_PAYMENT).message()
                    : recoveryManager.recover(
                            TaskRecoveryManager.Problem.SCREEN_UNAVAILABLE).message();
            return state;
        }
        if (!isFresh(screen, now)) {
            if (isUncertainPaymentStep()) {
                enterUncertainPaymentClarification(screen, now);
                return state;
            }
            recover("stale_observation", "stale_observation", now);
            currentMessage = recoveryManager.recover(
                    TaskRecoveryManager.Problem.STALE_OBSERVATION).message();
            return state;
        }
        if (screen.screenIdentifier().equals(state.lastObservedScreenIdentifier())) {
            return state;
        }

        if (state.status() == TaskState.Status.WAITING_FOR_USER
                || state.status() == TaskState.Status.VERIFYING_TRANSITION) {
            verifyPendingStep(screen, transition, now);
            return state;
        }
        if (state.status() == TaskState.Status.OBSERVING_SCREEN
                || state.status() == TaskState.Status.RECOVERING) {
            planOn(screen, now);
        }
        return state;
    }

    public TaskState pause(long now) {
        if (state.goal() == null || isTerminal(state.status())
                || state.status() == TaskState.Status.PAUSED) {
            return state;
        }
        transition(TaskState.Status.PAUSED, "user_paused",
                state.currentStepId(), state.completedStepIds(),
                state.expectedNextScreen(), state.lastObservedScreenIdentifier(),
                state.lastObservedCategory(), state.lastObservationTimestamp(),
                state.currentStepStatus(), state.retryCount(), null, now);
        currentMessage = "Your task is paused. Resume when you are ready.";
        return state;
    }

    public TaskState resume(long now) {
        if (state.status() != TaskState.Status.PAUSED) {
            return state;
        }
        if (hasUncertainPaymentContext()) {
            enterUncertainPaymentClarification(null, now);
            return state;
        }
        transition(TaskState.Status.OBSERVING_SCREEN, "user_resumed",
                null, state.completedStepIds(), null, null,
                ScreenCategories.UNKNOWN, 0L, null, state.retryCount(),
                null, now);
        currentStep = null;
        currentMessage = "I will check the current screen before continuing.";
        return state;
    }

    public TaskState cancel(long now) {
        if (state.goal() == null || isTerminal(state.status())) {
            return state;
        }
        transition(TaskState.Status.CANCELLED, "user_cancelled",
                null, state.completedStepIds(), null, null,
                ScreenCategories.UNKNOWN, 0L, null, state.retryCount(),
                null, now);
        currentStep = null;
        currentMessage = "The guidance task was cancelled.";
        return state;
    }

    public TaskState confirmGuidanceComplete(long now) {
        if (state.status() != TaskState.Status.WAITING_FOR_USER
                || state.expectedNextScreen() != null
                || state.currentStepId() == null) {
            return state;
        }
        List<String> completed = append(
                state.completedStepIds(),
                state.currentStepId());
        transition(TaskState.Status.COMPLETED,
                "user_confirmed_guidance_complete",
                state.currentStepId(),
                completed,
                null,
                state.lastObservedScreenIdentifier(),
                state.lastObservedCategory(),
                state.lastObservationTimestamp(),
                TaskStep.Status.COMPLETED,
                state.retryCount(),
                null,
                now);
        currentStep = null;
        currentMessage = "Guidance finished. Saathi did not verify any transaction details.";
        return state;
    }

    public TaskState restore(TaskState restored, long now) {
        if (restored == null
                || restored.goal() == null
                || isTerminal(restored.status())) {
            state = TaskState.idle(now);
            currentStep = null;
            currentMessage = null;
            return state;
        }
        state = restored.restoredPaused(now);
        currentStep = null;
        currentMessage = hasUncertainPaymentContext()
                ? recoveryManager.recover(
                        TaskRecoveryManager.Problem.UNCERTAIN_PAYMENT).message()
                : "Your task is paused. Resume to check the current screen.";
        return state;
    }

    private void verifyPendingStep(
            ScreenState screen,
            ScreenTransitionDetector.Result observedTransition,
            long now
    ) {
        String expected = state.expectedNextScreen();
        if (expected != null && expected.equals(screen.category())) {
            transition(TaskState.Status.VERIFYING_TRANSITION,
                    "expected_screen_observed",
                    state.currentStepId(),
                    state.completedStepIds(),
                    expected,
                    screen.screenIdentifier(),
                    screen.category(),
                    screen.observedAt(),
                    state.currentStepStatus(),
                    state.retryCount(),
                    null,
                    now);
            if (isVerifiedTransition(observedTransition)) {
                List<String> completed = append(
                        state.completedStepIds(),
                        state.currentStepId());
                transition(TaskState.Status.OBSERVING_SCREEN,
                        "expected_screen_observed",
                        null,
                        completed,
                        null,
                        screen.screenIdentifier(),
                        screen.category(),
                        screen.observedAt(),
                        TaskStep.Status.COMPLETED,
                        state.retryCount(),
                        null,
                        now);
                currentStep = null;
                planOn(screen, now);
            } else {
                if (isUncertainPaymentStep()) {
                    enterUncertainPaymentClarification(screen, now);
                    return;
                }
                recover("unexpected_screen_observed", "unexpected_screen", now);
                currentMessage = recoveryManager.recover(
                        TaskRecoveryManager.Problem.UNEXPECTED_SCREEN).message();
                if (!ScreenCategories.LOADING.equals(screen.category())) {
                    planOn(screen, now);
                }
            }
            return;
        }

        if (ScreenCategories.LOADING.equals(screen.category())) {
            transition(TaskState.Status.VERIFYING_TRANSITION,
                    "loading_observed",
                    state.currentStepId(),
                    state.completedStepIds(),
                    expected,
                    screen.screenIdentifier(),
                    screen.category(),
                    screen.observedAt(),
                    state.currentStepStatus(),
                    state.retryCount(),
                    null,
                    now);
            currentMessage = recoveryManager.recover(
                    TaskRecoveryManager.Problem.LOADING).message();
            return;
        }

        if (observedTransition != null
                && observedTransition.type()
                    == ScreenTransitionDetector.Type.SCREEN_UNCHANGED) {
            return;
        }

        if (isUncertainPaymentStep()) {
            enterUncertainPaymentClarification(screen, now);
            return;
        }

        recover("unexpected_screen_observed", "unexpected_screen", now);
        planOn(screen, now);
    }

    private void planOn(ScreenState screen, long now) {
        if (state.status() == TaskState.Status.OBSERVING_SCREEN
                || state.status() == TaskState.Status.RECOVERING) {
            transition(TaskState.Status.PLANNING_STEP, "step_planned",
                    null, state.completedStepIds(), null,
                    screen.screenIdentifier(), screen.category(),
                    screen.observedAt(), null, state.retryCount(), null, now);
        } else {
            return;
        }

        TaskStepPlanner.Plan plan = planner.plan(state.goal(), state, screen, now);
        currentMessage = plan.message();
        switch (plan.outcome()) {
            case STEP_READY:
                currentStep = plan.step();
                transition(TaskState.Status.WAITING_FOR_USER,
                        "step_planned",
                        currentStep.stepId(),
                        state.completedStepIds(),
                        currentStep.expectedNextScreen(),
                        screen.screenIdentifier(),
                        screen.category(),
                        screen.observedAt(),
                        currentStep.status(),
                        state.retryCount(),
                        null,
                        now);
                break;
            case TASK_COMPLETE:
                currentStep = null;
                transition(TaskState.Status.COMPLETED,
                        plan.reason(),
                        null,
                        state.completedStepIds(),
                        null,
                        screen.screenIdentifier(),
                        screen.category(),
                        screen.observedAt(),
                        TaskStep.Status.COMPLETED,
                        state.retryCount(),
                        null,
                        now);
                break;
            case NEEDS_CLARIFICATION:
                currentStep = null;
                transition(TaskState.Status.NEEDS_CLARIFICATION,
                        plan.reason(),
                        null,
                        state.completedStepIds(),
                        null,
                        screen.screenIdentifier(),
                        screen.category(),
                        screen.observedAt(),
                        TaskStep.Status.NEEDS_CLARIFICATION,
                        state.retryCount(),
                        clarificationCode(plan.reason()),
                        now);
                break;
            case WAIT_FOR_OBSERVATION:
                currentStep = null;
                transition(TaskState.Status.OBSERVING_SCREEN,
                        plan.reason(),
                        null,
                        state.completedStepIds(),
                        null,
                        screen.screenIdentifier(),
                        screen.category(),
                        screen.observedAt(),
                        null,
                        state.retryCount(),
                        null,
                        now);
                break;
            default:
                throw new IllegalStateException("Unsupported planning outcome.");
        }
    }

    private void recover(String reason, String clarification, long now) {
        if (state.status() != TaskState.Status.RECOVERING) {
            transition(TaskState.Status.RECOVERING, reason,
                    null, state.completedStepIds(), null,
                    null, state.lastObservedCategory(),
                    state.lastObservationTimestamp(), null,
                    Math.min(3, state.retryCount() + 1),
                    clarification, now);
        }
        currentStep = null;
    }

    private boolean isUncertainPaymentStep() {
        return state.goal() != null
                && state.goal().type() == TaskGoal.Type.SEND_MONEY
                && "review_details".equals(state.currentStepId());
    }

    private boolean hasUncertainPaymentContext() {
        return isUncertainPaymentStep()
                || (state.goal() != null
                    && state.goal().type() == TaskGoal.Type.SEND_MONEY
                    && ("uncertain_payment".equals(
                            state.pendingClarification())
                        || state.completedStepIds().contains("review_details")));
    }

    private void enterUncertainPaymentClarification(
            ScreenState screen,
            long now
    ) {
        transition(TaskState.Status.NEEDS_CLARIFICATION,
                "uncertain_payment",
                null,
                state.completedStepIds(),
                null,
                screen == null ? null : screen.screenIdentifier(),
                screen == null ? ScreenCategories.UNKNOWN : screen.category(),
                screen == null ? 0L : screen.observedAt(),
                TaskStep.Status.NEEDS_CLARIFICATION,
                state.retryCount(),
                "uncertain_payment",
                now);
        currentStep = null;
        currentMessage = recoveryManager.recover(
                TaskRecoveryManager.Problem.UNCERTAIN_PAYMENT).message();
    }

    private void transition(
            TaskState.Status next,
            String reason,
            String stepId,
            List<String> completed,
            String expected,
            String screenId,
            String screenCategory,
            long observedAt,
            TaskStep.Status stepStatus,
            int retries,
            String clarification,
            long now
    ) {
        transition(next, reason, stepId, completed, expected, screenId,
                screenCategory, observedAt, stepStatus, retries,
                clarification, now, state.goal());
    }

    private void transition(
            TaskState.Status next,
            String reason,
            String stepId,
            List<String> completed,
            String expected,
            String screenId,
            String screenCategory,
            long observedAt,
            TaskStep.Status stepStatus,
            int retries,
            String clarification,
            long now,
            TaskGoal goal
    ) {
        if (!isAllowed(state.status(), next)) {
            throw new IllegalStateException(
                    "Invalid task transition: " + state.status() + " -> " + next);
        }
        TaskState previous = state;
        state = new TaskState(
                goal,
                next,
                stepId,
                completed,
                expected,
                screenId,
                screenCategory,
                observedAt,
                stepStatus,
                retries,
                clarification,
                reason,
                previous.goal() == null ? now : previous.createdAt(),
                now);
    }

    private static boolean isAllowed(TaskState.Status from, TaskState.Status to) {
        if (to == TaskState.Status.CANCELLED) {
            return from != TaskState.Status.IDLE
                    && from != TaskState.Status.COMPLETED
                    && from != TaskState.Status.CANCELLED
                    && from != TaskState.Status.FAILED;
        }
        switch (from) {
            case IDLE:
            case COMPLETED:
            case CANCELLED:
            case FAILED:
                return to == TaskState.Status.UNDERSTANDING_GOAL
                        || to == TaskState.Status.IDLE;
            case UNDERSTANDING_GOAL:
                return to == TaskState.Status.OBSERVING_SCREEN
                        || to == TaskState.Status.NEEDS_CLARIFICATION
                        || to == TaskState.Status.FAILED;
            case OBSERVING_SCREEN:
                return to == TaskState.Status.PLANNING_STEP
                        || to == TaskState.Status.RECOVERING
                        || to == TaskState.Status.PAUSED
                        || to == TaskState.Status.NEEDS_CLARIFICATION;
            case PLANNING_STEP:
                return to == TaskState.Status.WAITING_FOR_USER
                        || to == TaskState.Status.NEEDS_CLARIFICATION
                        || to == TaskState.Status.COMPLETED
                        || to == TaskState.Status.OBSERVING_SCREEN;
            case WAITING_FOR_USER:
                return to == TaskState.Status.VERIFYING_TRANSITION
                        || to == TaskState.Status.RECOVERING
                        || to == TaskState.Status.PAUSED
                        || to == TaskState.Status.COMPLETED
                        || to == TaskState.Status.NEEDS_CLARIFICATION;
            case VERIFYING_TRANSITION:
                return to == TaskState.Status.OBSERVING_SCREEN
                        || to == TaskState.Status.RECOVERING
                        || to == TaskState.Status.NEEDS_CLARIFICATION
                        || to == TaskState.Status.PAUSED
                        || to == TaskState.Status.VERIFYING_TRANSITION;
            case RECOVERING:
                return to == TaskState.Status.PLANNING_STEP
                        || to == TaskState.Status.NEEDS_CLARIFICATION
                        || to == TaskState.Status.OBSERVING_SCREEN
                        || to == TaskState.Status.FAILED
                        || to == TaskState.Status.PAUSED;
            case NEEDS_CLARIFICATION:
                return to == TaskState.Status.UNDERSTANDING_GOAL
                        || to == TaskState.Status.OBSERVING_SCREEN
                        || to == TaskState.Status.PAUSED;
            case PAUSED:
                return to == TaskState.Status.OBSERVING_SCREEN
                        || to == TaskState.Status.NEEDS_CLARIFICATION;
            default:
                return false;
        }
    }

    private static boolean isFresh(ScreenState screen, long now) {
        return screen.observedAt() > 0L
                && screen.observedAt() <= now
                && now - screen.observedAt() <= 15_000L;
    }

    private static boolean isVerifiedTransition(
            ScreenTransitionDetector.Result result
    ) {
        return result != null
                && (result.type() == ScreenTransitionDetector.Type.SCREEN_CHANGED
                    || result.type() == ScreenTransitionDetector.Type.LOADING_FINISHED);
    }

    private boolean canReplaceTask() {
        return state.status() == TaskState.Status.IDLE || isTerminal(state.status());
    }

    private static boolean isTerminal(TaskState.Status status) {
        return status == TaskState.Status.COMPLETED
                || status == TaskState.Status.CANCELLED
                || status == TaskState.Status.FAILED;
    }

    private static List<String> append(List<String> values, String value) {
        List<String> result = new ArrayList<>(values);
        if (value != null && !result.contains(value)) {
            result.add(value);
        }
        return result;
    }

    private static String clarificationCode(String reason) {
        return "stale_observation".equals(reason)
                ? "stale_observation"
                : "screen_not_supported".equals(reason)
                    ? "unexpected_screen"
                    : "unknown_screen";
    }
}
