package com.vinuthna.paypalsaathi.detection;

import java.util.Locale;

/**
 * Rule-based (non-AI) screen classifier.
 * Returns canonical category IDs from {@link ScreenCategories}.
 * Keyword matching is intentionally conservative and English-oriented.
 * Not all PayPal screens can be recognized automatically.
 */
public final class ScreenClassifier {
    private ScreenClassifier() {}

    public static String classify(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return ScreenCategories.UNKNOWN;
        }

        String text = normalize(rawText);
        if (text.isEmpty()) {
            return ScreenCategories.UNKNOWN;
        }

        // Most sensitive first — never treat these as payment-success screens.
        if (containsAny(text,
                "verification code",
                "one-time password",
                "otp",
                "passcode",
                "password",
                "security code",
                "confirm your identity",
                "verify your identity",
                "two-factor authentication",
                "two-step verification")) {
            return ScreenCategories.SECURITY;
        }

        if (containsAny(text,
                "review your payment",
                "review payment",
                "review and send",
                "confirm payment",
                "send payment",
                "payment summary",
                "payment details",
                "pay now",
                "complete purchase")) {
            return ScreenCategories.REVIEW_PAYMENT;
        }

        if (containsAny(text,
                "payment sent",
                "you sent",
                "payment complete",
                "transaction complete",
                "payment successful",
                "transaction successful",
                "payment received")) {
            return ScreenCategories.CONFIRMATION;
        }

        if (containsAny(text,
                "enter amount",
                "enter an amount",
                "how much",
                "amount to send",
                "choose amount")) {
            return ScreenCategories.AMOUNT_ENTRY;
        }

        if (containsAny(text,
                "choose who to pay",
                "select a contact",
                "choose recipient",
                "select recipient",
                "who do you want to pay",
                "who are you sending money to")) {
            return ScreenCategories.RECIPIENT_SELECTION;
        }

        if (containsAny(text, "send money", "pay someone")) {
            return ScreenCategories.SEND_MONEY;
        }

        if (containsAny(text,
                "wallet screen",
                "banks and cards",
                "bank account",
                "payment methods",
                "linked accounts")
                || text.equals("wallet")) {
            return ScreenCategories.WALLET;
        }

        if (containsAny(text,
                "transactions",
                "recent activity",
                "transaction history")
                || text.equals("activity")) {
            return ScreenCategories.ACTIVITY;
        }

        if (containsAny(text,
                "log in",
                "login",
                "sign in",
                "welcome to paypal",
                "create an account")) {
            return ScreenCategories.SIGN_IN;
        }

        if (containsAny(text,
                "paypal home",
                "home screen",
                "dashboard",
                "account overview")
                || text.equals("home")) {
            return ScreenCategories.HOME;
        }

        return ScreenCategories.UNKNOWN;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String text, String... phrases) {
        String paddedText = " " + text + " ";
        for (String phrase : phrases) {
            String normalizedPhrase = normalize(phrase);
            if (paddedText.contains(" " + normalizedPhrase + " ")) {
                return true;
            }
        }
        return false;
    }
}
