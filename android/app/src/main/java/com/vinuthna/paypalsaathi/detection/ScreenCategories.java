package com.vinuthna.paypalsaathi.detection;

/**
 * Canonical screen-category IDs shared with the backend.
 * Rule-based today; a future on-device or server model should emit the same IDs.
 */
public final class ScreenCategories {
    public static final String HOME = "home";
    public static final String SEND_MONEY = "send_money";
    public static final String RECIPIENT_SELECTION = "recipient_selection";
    public static final String AMOUNT_ENTRY = "amount_entry";
    public static final String REVIEW_PAYMENT = "review_payment";
    public static final String CONFIRMATION = "confirmation";
    public static final String ACTIVITY = "activity";
    public static final String WALLET = "wallet";
    public static final String SECURITY = "security";
    public static final String SIGN_IN = "sign_in";
    public static final String UNKNOWN = "unknown";

    private ScreenCategories() {}

    public static String displayLabel(String category) {
        if (category == null) {
            return "Unknown screen";
        }

        switch (category) {
            case HOME:
                return "Home";
            case SEND_MONEY:
                return "Send money";
            case RECIPIENT_SELECTION:
                return "Recipient selection";
            case AMOUNT_ENTRY:
                return "Amount entry";
            case REVIEW_PAYMENT:
                return "Payment review";
            case CONFIRMATION:
                return "Confirmation / status";
            case ACTIVITY:
                return "Activity";
            case WALLET:
                return "Wallet";
            case SECURITY:
                return "Security / verification";
            case SIGN_IN:
                return "Sign-in";
            case UNKNOWN:
            default:
                return "Unknown / unclassified";
        }
    }

    public static boolean isKnownCategory(String category) {
        if (category == null) {
            return false;
        }

        switch (category) {
            case HOME:
            case SEND_MONEY:
            case RECIPIENT_SELECTION:
            case AMOUNT_ENTRY:
            case REVIEW_PAYMENT:
            case CONFIRMATION:
            case ACTIVITY:
            case WALLET:
            case SECURITY:
            case SIGN_IN:
            case UNKNOWN:
                return true;
            default:
                return false;
        }
    }
}
