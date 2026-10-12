package com.vinuthna.paypalsaathi.detection;

import java.util.Locale;

public final class ScreenClassifier {
    private ScreenClassifier() {}

    public static String classify(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return "Screen details unavailable";
        }

        String text = rawText.toLowerCase(Locale.ROOT);

        if (containsAny(text, "verification code", "one-time password",
                "otp", "passcode", "password", "security code",
                "confirm your identity")) {
            return "Sensitive / verification screen";
        }

        if (containsAny(text, "review your payment", "review payment",
                "confirm payment", "send payment", "pay now",
                "complete purchase")) {
            return "Payment review screen";
        }

        if (containsAny(text, "enter amount", "how much", "currency")) {
            return "Amount entry may be visible";
        }

        if (containsAny(text, "send money", "pay someone", "recipient", "send")) {
            return "Send money flow may be visible";
        }

        if (containsAny(text, "wallet", "banks and cards", "bank account")) {
            return "Wallet screen may be visible";
        }

        if (containsAny(text, "activity", "transactions", "recent activity")) {
            return "Activity screen may be visible";
        }

        if (containsAny(text, "log in", "login", "sign in", "welcome to paypal")) {
            return "Sign-in / welcome screen";
        }

        if (containsAny(text, "home", "balance", "dashboard")) {
            return "Home screen may be visible";
        }

        return "PayPal screen — not classified";
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }
}