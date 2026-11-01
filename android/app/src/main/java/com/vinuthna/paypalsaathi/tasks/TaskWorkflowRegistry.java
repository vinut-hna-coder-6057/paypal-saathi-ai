package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class TaskWorkflowRegistry {
    private static final Set<TaskGoal.Type> SUPPORTED = Collections.unmodifiableSet(
            EnumSet.copyOf(Arrays.asList(
                    TaskGoal.Type.SEND_MONEY,
                    TaskGoal.Type.VIEW_ACTIVITY,
                    TaskGoal.Type.CHECK_TRANSACTION_STATUS,
                    TaskGoal.Type.FIND_REFUND,
                    TaskGoal.Type.UNDERSTAND_PAYMENT_ERROR,
                    TaskGoal.Type.OPEN_WALLET,
                    TaskGoal.Type.ACCOUNT_SETTINGS)));

    public boolean supports(TaskGoal.Type type) {
        return type != null && SUPPORTED.contains(type);
    }

    public String stepId(TaskGoal.Type goal, String category) {
        if (goal == TaskGoal.Type.SEND_MONEY) {
            switch (category) {
                case ScreenCategories.HOME:
                case ScreenCategories.SEND_MONEY:
                    return "open_send";
                case ScreenCategories.RECIPIENT_SELECTION:
                    return "choose_recipient";
                case ScreenCategories.AMOUNT_ENTRY:
                    return "enter_amount";
                case ScreenCategories.REVIEW_PAYMENT:
                    return "review_details";
                case ScreenCategories.CONFIRMATION:
                    return "check_activity";
                case ScreenCategories.ACTIVITY:
                    return "confirm_activity_review";
                default:
                    return null;
            }
        }
        if (goal == TaskGoal.Type.VIEW_ACTIVITY
                || goal == TaskGoal.Type.CHECK_TRANSACTION_STATUS
                || goal == TaskGoal.Type.FIND_REFUND) {
            return ScreenCategories.ACTIVITY.equals(category)
                    ? "review_activity"
                    : "open_activity";
        }
        if (goal == TaskGoal.Type.OPEN_WALLET) {
            return ScreenCategories.WALLET.equals(category)
                    ? "wallet_open"
                    : "open_wallet";
        }
        if (goal == TaskGoal.Type.ACCOUNT_SETTINGS) {
            return ScreenCategories.ACCOUNT_SETTINGS.equals(category)
                    ? "settings_open"
                    : ScreenCategories.HOME.equals(category)
                        ? "open_settings" : null;
        }
        if (goal == TaskGoal.Type.UNDERSTAND_PAYMENT_ERROR
                && ScreenCategories.ERROR.equals(category)) {
            return "review_error";
        }
        return null;
    }

    public String expectedNextScreen(TaskGoal.Type goal, String category) {
        if (goal == TaskGoal.Type.SEND_MONEY) {
            switch (category) {
                case ScreenCategories.HOME:
                case ScreenCategories.SEND_MONEY:
                    return ScreenCategories.RECIPIENT_SELECTION;
                case ScreenCategories.RECIPIENT_SELECTION:
                    return ScreenCategories.AMOUNT_ENTRY;
                case ScreenCategories.AMOUNT_ENTRY:
                    return ScreenCategories.REVIEW_PAYMENT;
                case ScreenCategories.REVIEW_PAYMENT:
                    return ScreenCategories.CONFIRMATION;
                case ScreenCategories.CONFIRMATION:
                    return ScreenCategories.ACTIVITY;
                default:
                    return null;
            }
        }
        if (goal == TaskGoal.Type.VIEW_ACTIVITY
                || goal == TaskGoal.Type.CHECK_TRANSACTION_STATUS
                || goal == TaskGoal.Type.FIND_REFUND) {
            return ScreenCategories.ACTIVITY.equals(category)
                    ? null
                    : ScreenCategories.ACTIVITY;
        }
        if (goal == TaskGoal.Type.OPEN_WALLET) {
            return ScreenCategories.WALLET.equals(category)
                    ? null
                    : ScreenCategories.WALLET;
        }
        if (goal == TaskGoal.Type.ACCOUNT_SETTINGS
                && ScreenCategories.HOME.equals(category)) {
            return ScreenCategories.ACCOUNT_SETTINGS;
        }
        return null;
    }
}
