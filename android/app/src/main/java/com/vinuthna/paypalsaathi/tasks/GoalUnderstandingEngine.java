package com.vinuthna.paypalsaathi.tasks;

import java.util.Locale;

public final class GoalUnderstandingEngine {
    public static final class Result {
        private final TaskGoal goal;
        private final boolean recognized;
        private final String clarification;

        private Result(TaskGoal goal, boolean recognized, String clarification) {
            this.goal = goal;
            this.recognized = recognized;
            this.clarification = clarification;
        }

        public TaskGoal goal() { return goal; }
        public boolean recognized() { return recognized; }
        public String clarification() { return clarification; }
    }

    public Result understand(String request, long now) {
        if (request == null || request.trim().isEmpty()) {
            return unrecognized();
        }
        String text = normalize(request);
        boolean refund = containsAny(text, "refund", "money returned");
        boolean payment = hasWord(text, "payment")
                || hasWord(text, "transaction");
        boolean failureLanguage = containsAny(
                text,
                "failed",
                "fail",
                "failure",
                "declined",
                "unsuccessful",
                "did not go through",
                "didn t go through");
        boolean error = containsAny(text, "payment failed", "failed payment",
                "payment error", "payment declined", "why did my payment fail")
                || (payment && failureLanguage);
        boolean status = containsAny(text, "payment status", "transaction status",
                "pending payment", "payment pending", "payment successful",
                "payment went through", "check whether my payment",
                "check if my payment", "payment completed",
                "payment complete", "payment is complete",
                "payment was successful", "is my payment completed",
                "did my payment go through");
        boolean activity = containsAny(text, "activity", "transaction history",
                "previous transaction", "previous transactions", "past transaction",
                "past transactions", "recent transaction", "recent transactions",
                "previous payment", "previous payments", "past payment",
                "past payments", "recent payment", "recent payments",
                "previous payments");
        boolean wallet = containsAny(text, "wallet", "payment methods");
        boolean settings = containsAny(text, "account settings", "update my settings",
                "change my settings", "settings");
        boolean send = containsAny(text, "send money", "send funds",
                "transfer money", "send a payment", "send payment")
                || (hasWord(text, "send") && hasWord(text, "money"));

        int matches = count(refund, error, status, activity, wallet, settings, send);
        if (matches > 1 || (hasWord(text, "payment") && matches == 0)) {
            return clarification(now,
                    "Would you like to check a payment's status, find a refund, or get help with an error?");
        }
        if (refund) {
            return goal(TaskGoal.Type.FIND_REFUND, now, 90);
        }
        if (error) {
            return goal(TaskGoal.Type.UNDERSTAND_PAYMENT_ERROR, now, 88);
        }
        if (status) {
            return goal(TaskGoal.Type.CHECK_TRANSACTION_STATUS, now, 88);
        }
        if (activity) {
            return goal(TaskGoal.Type.VIEW_ACTIVITY, now, 86);
        }
        if (wallet) {
            return goal(TaskGoal.Type.OPEN_WALLET, now, 88);
        }
        if (settings) {
            return goal(TaskGoal.Type.ACCOUNT_SETTINGS, now, 82);
        }
        if (send) {
            return goal(TaskGoal.Type.SEND_MONEY, now, 90);
        }
        if (containsAny(text, "send", "transfer", "pay someone", "check payment")) {
            return clarification(now,
                    "What would you like to do: send money, check a payment, or find a refund?");
        }
        return unrecognized();
    }

    public Result resolveClarification(String answer, long now) {
        if (answer == null || answer.trim().isEmpty()) {
            return unrecognized();
        }
        String text = normalize(answer);
        if (containsAny(text, "refund", "money returned")) {
            return goal(TaskGoal.Type.FIND_REFUND, now, 82);
        }
        if (containsAny(text, "status", "pending", "successful", "went through")) {
            return goal(TaskGoal.Type.CHECK_TRANSACTION_STATUS, now, 82);
        }
        if (containsAny(text, "error", "failed", "declined")) {
            return goal(TaskGoal.Type.UNDERSTAND_PAYMENT_ERROR, now, 82);
        }
        if (containsAny(text, "activity", "transaction history",
                "previous payment", "previous payments", "transactions")) {
            return goal(TaskGoal.Type.VIEW_ACTIVITY, now, 82);
        }
        if (containsAny(text, "wallet", "payment methods")) {
            return goal(TaskGoal.Type.OPEN_WALLET, now, 82);
        }
        if (containsAny(text, "settings", "account settings")) {
            return goal(TaskGoal.Type.ACCOUNT_SETTINGS, now, 82);
        }
        if (containsAny(text, "send", "transfer")) {
            return goal(TaskGoal.Type.SEND_MONEY, now, 82);
        }
        return unrecognized();
    }

    private static Result goal(TaskGoal.Type type, long now, int confidence) {
        return new Result(new TaskGoal(type, now, confidence, false), true, null);
    }

    private static Result clarification(long now, String prompt) {
        return new Result(
                new TaskGoal(TaskGoal.Type.UNKNOWN, now, 0, true),
                true,
                prompt);
    }

    private static Result unrecognized() {
        return new Result(null, false, null);
    }

    private static int count(boolean... values) {
        int result = 0;
        for (boolean value : values) {
            if (value) {
                result++;
            }
        }
        return result;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if ((" " + text + " ").contains(" " + phrase + " ")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWord(String text, String word) {
        return (" " + text + " ").contains(" " + word + " ");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
