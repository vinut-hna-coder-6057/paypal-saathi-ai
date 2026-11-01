package com.vinuthna.paypalsaathi.tasks;

public final class TaskRecoveryManager {
    public enum Problem {
        SCREEN_UNAVAILABLE,
        STALE_OBSERVATION,
        UNKNOWN_SCREEN,
        UNEXPECTED_SCREEN,
        LOADING,
        NAVIGATED_AWAY,
        UNCERTAIN_PAYMENT,
        TASK_EXPIRED,
        RESTORATION_FAILURE,
        STORAGE_FAILURE,
        SERVICE_UNAVAILABLE
    }

    public static final class Recovery {
        private final boolean continueTask;
        private final boolean requiresFreshObservation;
        private final boolean automaticRetryAllowed;
        private final String message;

        private Recovery(
                boolean continueTask,
                boolean requiresFreshObservation,
                boolean automaticRetryAllowed,
                String message
        ) {
            this.continueTask = continueTask;
            this.requiresFreshObservation = requiresFreshObservation;
            this.automaticRetryAllowed = automaticRetryAllowed;
            this.message = message;
        }

        public boolean continueTask() { return continueTask; }
        public boolean requiresFreshObservation() {
            return requiresFreshObservation;
        }
        public boolean automaticRetryAllowed() {
            return automaticRetryAllowed;
        }
        public String message() { return message; }
    }

    public Recovery recover(Problem problem) {
        if (problem == null) {
            throw new IllegalArgumentException("A recovery problem is required.");
        }
        switch (problem) {
            case SCREEN_UNAVAILABLE:
                return recovery(true, true, false,
                        "I cannot read the current screen yet. Please wait or reopen PayPal.");
            case STALE_OBSERVATION:
                return recovery(true, true, false,
                        "I need a fresh screen before suggesting another step.");
            case UNKNOWN_SCREEN:
                return recovery(true, true, false,
                        "I could not identify this page clearly. Please describe it without sharing payment details.");
            case UNEXPECTED_SCREEN:
                return recovery(true, true, false,
                        "Your screen looks different. I will check it again before continuing.");
            case LOADING:
                return recovery(true, true, false,
                        "PayPal is loading. I will wait for the next screen.");
            case NAVIGATED_AWAY:
                return recovery(true, true, false,
                        "This task is paused because PayPal is no longer active.");
            case UNCERTAIN_PAYMENT:
                return recovery(false, true, false,
                        "I cannot tell whether the payment finished. Do not try the payment again; check Activity in PayPal.");
            case TASK_EXPIRED:
                return recovery(false, true, false,
                        "This saved task expired. Start again so I can check the current screen.");
            case RESTORATION_FAILURE:
                return recovery(false, true, false,
                        "I could not restore the saved task safely. Start a new task after checking PayPal.");
            case STORAGE_FAILURE:
                return recovery(false, true, false,
                        "I could not save this task safely. Please keep control of the next step and start again later.");
            case SERVICE_UNAVAILABLE:
                return recovery(false, true, false,
                        "Screen monitoring is unavailable. Re-enable the service, then check the current PayPal screen.");
            default:
                throw new IllegalStateException("Unsupported recovery problem.");
        }
    }

    private static Recovery recovery(
            boolean continueTask,
            boolean requiresFreshObservation,
            boolean automaticRetryAllowed,
            String message
    ) {
        return new Recovery(
                continueTask,
                requiresFreshObservation,
                automaticRetryAllowed,
                message);
    }
}
