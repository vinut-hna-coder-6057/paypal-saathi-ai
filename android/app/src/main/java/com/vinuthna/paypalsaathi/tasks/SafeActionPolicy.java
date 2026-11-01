package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.ScreenContext;
import com.vinuthna.paypalsaathi.detection.ScreenCategories;
import com.vinuthna.paypalsaathi.detection.ScreenElement;
import com.vinuthna.paypalsaathi.detection.ScreenState;

public final class SafeActionPolicy {
    public enum Rejection {
        NONE,
        WRONG_APPLICATION,
        TASK_NOT_WAITING,
        STALE_OBSERVATION,
        UNCERTAIN_SCREEN,
        ACTION_NOT_ALLOWLISTED,
        ACTION_NOT_OBSERVED,
        DUPLICATE_ACTION
    }

    public static final class Decision {
        private final Rejection rejection;
        private final String executionKey;

        private Decision(Rejection rejection, String executionKey) {
            this.rejection = rejection;
            this.executionKey = executionKey;
        }

        public boolean allowed() {
            return rejection == Rejection.NONE;
        }

        public Rejection rejection() {
            return rejection;
        }

        public String executionKey() {
            return executionKey;
        }
    }

    private static final String PAYPAL_PACKAGE =
            "com.paypal.android.p2pmobile";
    private static final long MAX_OBSERVATION_AGE_MS = 15_000L;
    private static final int MIN_CONFIDENCE = 75;

    public Decision evaluate(
            String activePackage,
            TaskState task,
            TaskStep step,
            ScreenState screen,
            long now,
            String lastExecutionKey
    ) {
        if (!PAYPAL_PACKAGE.equals(activePackage)) {
            return reject(Rejection.WRONG_APPLICATION);
        }
        if (task == null
                || step == null
                || task.status() != TaskState.Status.WAITING_FOR_USER
                || task.goal() == null
                || !task.goal().taskId().equals(step.taskId())
                || step.riskCategory() != ScreenElement.RiskCategory.SAFE_NAVIGATION
                || !step.requiresUserConfirmation()
                || task.currentStepId() == null
                || !task.currentStepId().equals(step.stepId())) {
            return reject(Rejection.TASK_NOT_WAITING);
        }
        if (screen == null
                || !screen.observationAvailable()
                || screen.observedAt() <= 0L
                || screen.observedAt() > now
                || now - screen.observedAt() > MAX_OBSERVATION_AGE_MS) {
            return reject(Rejection.STALE_OBSERVATION);
        }
        if (screen.ambiguous()
                || screen.confidence() < MIN_CONFIDENCE
                || !screen.category().equals(step.currentScreenCategory())
                || task.lastObservedScreenIdentifier() == null
                || !task.lastObservedScreenIdentifier().equals(
                        screen.screenIdentifier())) {
            return reject(Rejection.UNCERTAIN_SCREEN);
        }

        String descriptor = ScreenContext.sanitizeActionDescriptors(
                java.util.Collections.singletonList(
                        step.recommendedAction())).stream()
                .findFirst()
                .orElse(null);
        String label = ScreenContext.actionLabel(descriptor);
        if (descriptor == null || !isActionAllowed(label, screen.category())) {
            return reject(Rejection.ACTION_NOT_ALLOWLISTED);
        }
        boolean observed = screen.safeActionDescriptors().contains(descriptor);
        if (!observed) {
            for (ScreenElement element : screen.elements()) {
                if (element.safeToRecommend()
                        && element.enabled()
                        && element.clickable()
                        && element.riskCategory()
                            == ScreenElement.RiskCategory.SAFE_NAVIGATION
                        && descriptor.equals(element.actionDescriptor())) {
                    observed = true;
                    break;
                }
            }
        }
        if (!observed) {
            return reject(Rejection.ACTION_NOT_OBSERVED);
        }

        String executionKey = screen.screenIdentifier() + "|" + descriptor;
        if (executionKey.equals(lastExecutionKey)) {
            return reject(Rejection.DUPLICATE_ACTION);
        }
        return new Decision(Rejection.NONE, executionKey);
    }

    public static boolean isActionAllowed(String label, String category) {
        if (label == null
                || ScreenCategories.SECURITY.equals(category)
                || ScreenCategories.SIGN_IN.equals(category)
                || ScreenCategories.AMOUNT_ENTRY.equals(category)
                || ScreenCategories.RECIPIENT_SELECTION.equals(category)
                || ScreenCategories.REVIEW_PAYMENT.equals(category)
                || ScreenCategories.CONFIRMATION.equals(category)
                || ScreenCategories.ERROR.equals(category)
                || ScreenCategories.LOADING.equals(category)
                || ScreenCategories.UNKNOWN.equals(category)) {
            return false;
        }
        if (ScreenCategories.HOME.equals(category)) {
            return "Send".equals(label)
                    || "Activity".equals(label)
                    || "Wallet".equals(label)
                    || "Settings".equals(label)
                    || "Help".equals(label)
                    || "Menu".equals(label)
                    || "More".equals(label);
        }
        return "Activity".equals(label)
                || "Wallet".equals(label)
                || "Help".equals(label)
                || "Home".equals(label)
                || "Back".equals(label);
    }

    private static Decision reject(Rejection rejection) {
        return new Decision(rejection, null);
    }
}
