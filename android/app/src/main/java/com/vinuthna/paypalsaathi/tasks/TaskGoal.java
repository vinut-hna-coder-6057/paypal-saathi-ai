package com.vinuthna.paypalsaathi.tasks;

import java.util.UUID;

public final class TaskGoal {
    public enum Type {
        SEND_MONEY,
        VIEW_ACTIVITY,
        CHECK_TRANSACTION_STATUS,
        FIND_REFUND,
        UNDERSTAND_PAYMENT_ERROR,
        OPEN_WALLET,
        ACCOUNT_SETTINGS,
        UNKNOWN
    }

    private final String taskId;
    private final Type type;
    private final long createdAt;
    private final int confidence;
    private final boolean clarificationRequired;

    TaskGoal(Type type, long createdAt, int confidence, boolean clarificationRequired) {
        this(UUID.randomUUID().toString(), type, createdAt, confidence,
                clarificationRequired);
    }

    TaskGoal(
            String taskId,
            Type type,
            long createdAt,
            int confidence,
            boolean clarificationRequired
    ) {
        this.taskId = safeTaskId(taskId);
        this.type = type == null ? Type.UNKNOWN : type;
        this.createdAt = Math.max(0L, createdAt);
        this.confidence = Math.max(0, Math.min(confidence, 100));
        this.clarificationRequired = clarificationRequired
                || this.type == Type.UNKNOWN;
    }

    private static String safeTaskId(String value) {
        if (value != null
                && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
            return value;
        }
        return UUID.randomUUID().toString();
    }

    public String taskId() { return taskId; }
    public Type type() { return type; }
    public long createdAt() { return createdAt; }
    public int confidence() { return confidence; }
    public boolean clarificationRequired() { return clarificationRequired; }

    public String normalizedIntent() {
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }

    public String displayName() {
        switch (type) {
            case SEND_MONEY:
                return "Send money";
            case VIEW_ACTIVITY:
                return "View activity";
            case CHECK_TRANSACTION_STATUS:
                return "Check payment status";
            case FIND_REFUND:
                return "Find a refund";
            case UNDERSTAND_PAYMENT_ERROR:
                return "Understand a payment error";
            case OPEN_WALLET:
                return "Open wallet";
            case ACCOUNT_SETTINGS:
                return "Account settings";
            case UNKNOWN:
            default:
                return "PayPal guidance";
        }
    }
}
