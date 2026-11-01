package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.ScreenContext;
import com.vinuthna.paypalsaathi.detection.ScreenCategories;
import com.vinuthna.paypalsaathi.detection.ScreenElement;
import com.vinuthna.paypalsaathi.detection.ScreenState;

import java.util.List;

public final class TaskStepPlanner {
    public enum Outcome {
        STEP_READY,
        TASK_COMPLETE,
        NEEDS_CLARIFICATION,
        WAIT_FOR_OBSERVATION
    }

    public static final class Plan {
        private final Outcome outcome;
        private final TaskStep step;
        private final String message;
        private final String reason;

        private Plan(Outcome outcome, TaskStep step, String message, String reason) {
            this.outcome = outcome;
            this.step = step;
            this.message = message;
            this.reason = reason;
        }

        public Outcome outcome() { return outcome; }
        public TaskStep step() { return step; }
        public String message() { return message; }
        public String reason() { return reason; }
    }

    private static final long MAX_OBSERVATION_AGE_MS = 15_000L;
    private static final int MIN_PLANNING_CONFIDENCE = 75;

    private final TaskWorkflowRegistry workflows;

    public TaskStepPlanner(TaskWorkflowRegistry workflows) {
        this.workflows = workflows;
    }

    public Plan plan(TaskGoal goal, TaskState task, ScreenState screen, long now) {
        if (goal == null || task == null || screen == null
                || !screen.observationAvailable()) {
            return waitForObservation("Please keep PayPal open while I check the screen.",
                    "screen_unavailable");
        }
        if (screen.observedAt() <= 0L
                || screen.observedAt() > now
                || now - screen.observedAt() > MAX_OBSERVATION_AGE_MS) {
            return clarify("I need a fresh screen before I can suggest the next step.",
                    "stale_observation");
        }
        if (ScreenCategories.UNKNOWN.equals(screen.category())
                || screen.ambiguous()
                || screen.confidence() < MIN_PLANNING_CONFIDENCE) {
            return clarify("I could not identify this screen clearly. Please describe it without sharing payment details.",
                    "unknown_screen");
        }
        if (!workflows.supports(goal.type())) {
            return clarify("I can answer a general question about this, but guided steps are not available yet.",
                    "workflow_not_supported");
        }

        if (goal.type() == TaskGoal.Type.SEND_MONEY
                && ScreenCategories.CONFIRMATION.equals(screen.category())) {
            return step(goal, screen, "check_activity",
                    "I cannot verify the payment outcome. Do not try it again. Please open Activity in PayPal and check this transaction's status.",
                    ScreenCategories.ACTIVITY,
                    ScreenElement.RiskCategory.NOT_ACTIONABLE,
                    true,
                    TaskStep.Status.WAITING_FOR_USER);
        }
        if (goal.type() == TaskGoal.Type.SEND_MONEY
                && ScreenCategories.ACTIVITY.equals(screen.category())) {
            return step(goal, screen, "confirm_activity_review",
                    "Please review the transaction status directly in Activity. Saathi cannot verify the outcome or see transaction details. After you have checked it, tap Done; do not share private details.",
                    null,
                    ScreenElement.RiskCategory.NOT_ACTIONABLE,
                    true,
                    TaskStep.Status.WAITING_FOR_USER);
        }
        if ((goal.type() == TaskGoal.Type.VIEW_ACTIVITY
                && ScreenCategories.ACTIVITY.equals(screen.category()))
                || (goal.type() == TaskGoal.Type.OPEN_WALLET
                && ScreenCategories.WALLET.equals(screen.category()))) {
            return complete("You are on the requested PayPal section. I cannot inspect private account or transaction details.",
                    "expected_screen_observed");
        }
        if (goal.type() == TaskGoal.Type.ACCOUNT_SETTINGS
                && ScreenCategories.ACCOUNT_SETTINGS.equals(screen.category())) {
            return complete("You are on the account settings screen. Review any account changes directly in PayPal.",
                    "expected_screen_observed");
        }
        if (goal.type() == TaskGoal.Type.UNDERSTAND_PAYMENT_ERROR
                && ScreenCategories.ERROR.equals(screen.category())) {
            return step(goal, screen, "review_error",
                    "Read the error message in PayPal. If a payment result is uncertain, do not try it again; check Activity first.",
                    null, ScreenElement.RiskCategory.NOT_ACTIONABLE,
                    false, TaskStep.Status.WAITING_FOR_USER);
        }
        if ((goal.type() == TaskGoal.Type.CHECK_TRANSACTION_STATUS
                || goal.type() == TaskGoal.Type.FIND_REFUND)
                && ScreenCategories.ACTIVITY.equals(screen.category())) {
            String instruction = goal.type() == TaskGoal.Type.FIND_REFUND
                    ? "Find the relevant transaction and review its refund status directly in PayPal. I cannot read transaction details."
                    : "Find the relevant transaction and review the status directly in PayPal. I cannot read transaction details.";
            return step(goal, screen, "review_activity", instruction, null,
                    ScreenElement.RiskCategory.NOT_ACTIONABLE, true,
                    TaskStep.Status.WAITING_FOR_USER);
        }

        String stepId = workflows.stepId(goal.type(), screen.category());
        String expected = workflows.expectedNextScreen(goal.type(), screen.category());
        if (stepId == null) {
            return clarify("This screen does not match a step I can safely guide. Please navigate to a familiar PayPal screen or ask a general question.",
                    "screen_not_supported");
        }

        if (goal.type() == TaskGoal.Type.SEND_MONEY) {
            switch (screen.category()) {
                case ScreenCategories.HOME:
                    return safeNavigationStep(goal, screen, stepId, expected,
                            "Send");
                case ScreenCategories.SEND_MONEY:
                    return safeNavigationStep(goal, screen, stepId, expected,
                            "Continue", "Next");
                case ScreenCategories.RECIPIENT_SELECTION:
                    return step(goal, screen, stepId,
                            "Choose the intended recipient directly in PayPal. I will not select anyone for you.",
                            expected, ScreenElement.RiskCategory.RECIPIENT_SELECTION,
                            true, TaskStep.Status.WAITING_FOR_USER);
                case ScreenCategories.AMOUNT_ENTRY:
                    return step(goal, screen, stepId,
                            "Enter the amount directly in PayPal. I will not read or store it.",
                            expected, ScreenElement.RiskCategory.AMOUNT_ENTRY,
                            true, TaskStep.Status.WAITING_FOR_USER);
                case ScreenCategories.REVIEW_PAYMENT:
                    return step(goal, screen, stepId,
                            "Check the recipient, amount, currency, and fees in PayPal. Any final payment decision is yours.",
                            expected, ScreenElement.RiskCategory.FINANCIAL_AUTHORIZATION,
                            true, TaskStep.Status.WAITING_FOR_USER);
                default:
                    return clarify("I cannot safely continue from this screen. Please check PayPal and tell me what you see without sharing private details.",
                            "unexpected_screen");
            }
        }

        if (goal.type() == TaskGoal.Type.OPEN_WALLET) {
            return safeNavigationStep(goal, screen, stepId, expected, "Wallet");
        }
        if (goal.type() == TaskGoal.Type.ACCOUNT_SETTINGS) {
            return safeNavigationStep(goal, screen, stepId, expected, "Settings");
        }
        return safeNavigationStep(
                goal,
                screen,
                stepId,
                expected,
                "Activity",
                "Transactions");
    }

    private Plan safeNavigationStep(
            TaskGoal goal,
            ScreenState screen,
            String stepId,
            String expected,
            String... expectedLabels
    ) {
        for (ScreenElement element : screen.elements()) {
            String label = ScreenContext.actionLabel(element.actionDescriptor());
            boolean matchingLabel = false;
            for (String expectedLabel : expectedLabels) {
                matchingLabel |= expectedLabel.equals(label);
            }
            if (element.safeToRecommend()
                    && element.enabled()
                    && element.clickable()
                    && element.confidence() >= MIN_PLANNING_CONFIDENCE
                    && matchingLabel
                    && screen.safeActionDescriptors().contains(
                            element.actionDescriptor())) {
                String instruction = "Please tap " + label
                        + ". I will guide you through the next step.";
                return step(goal, screen, stepId, instruction,
                        expected, ScreenElement.RiskCategory.SAFE_NAVIGATION,
                        true, TaskStep.Status.WAITING_FOR_USER,
                        element.actionDescriptor(), element.confidence(),
                        screen.evidenceCodes());
            }
        }
        return clarify("I cannot find an enabled, safe navigation control on this screen. Please choose what to do in PayPal yourself.",
                "screen_not_supported");
    }

    private Plan step(
            TaskGoal goal,
            ScreenState screen,
            String stepId,
            String instruction,
            String expected,
            ScreenElement.RiskCategory risk,
            boolean confirm,
            TaskStep.Status status
    ) {
        return step(goal, screen, stepId, instruction, expected, risk,
                confirm, status, null, screen.confidence(),
                screen.evidenceCodes());
    }

    private Plan step(
            TaskGoal goal,
            ScreenState screen,
            String stepId,
            String instruction,
            String expected,
            ScreenElement.RiskCategory risk,
            boolean confirm,
            TaskStep.Status status,
            String action,
            int confidence,
            List<String> evidence
    ) {
        return new Plan(Outcome.STEP_READY,
                new TaskStep(
                        stepId,
                        goal.taskId(),
                        stepId.replace('_', ' '),
                        instruction,
                        screen.category(),
                        expected,
                        action,
                        risk,
                        confidence,
                        evidence,
                        confirm,
                        status),
                instruction,
                "step_planned");
    }

    private static Plan complete(String message, String reason) {
        return new Plan(Outcome.TASK_COMPLETE, null, message, reason);
    }

    private static Plan clarify(String message, String reason) {
        return new Plan(Outcome.NEEDS_CLARIFICATION, null, message, reason);
    }

    private static Plan waitForObservation(String message, String reason) {
        return new Plan(Outcome.WAIT_FOR_OBSERVATION, null, message, reason);
    }
}
