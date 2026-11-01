package com.vinuthna.paypalsaathi.detection;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ScreenDataSanitizer {
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern LONG_NUMBER = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){7,19}(?!\\d)");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)\\+?\\d[\\d ()-]{7,}\\d(?!\\d)");
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile(
            "(?i)(?:₹|\\bINR\\b|\\bUSD\\b|\\bEUR\\b|\\bGBP\\b|\\bRs\\.?|\\$|€|£)\\s*\\d[\\d,.]*");
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)\\b(password|passcode|pin|otp|one[- ]time code|verification code|security code|cvv|cvc|card number|account number|routing number)\\b\\s*(?:is\\s+|[:=]\\s*)?\\S+");
    private static final Pattern FINANCIAL_VALUE = Pattern.compile(
            "(?i)\\b(amount|balance|total|fee|transfer)\\b[^\\p{L}\\p{N}]{0,12}\\d[\\d,.]*");
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(password|passcode|pin|otp|one.?time|verification|security.?code|cvv|cvc|card.?number|account.?number|bank.?account|routing)");
    private static final String[] VISUAL_SCREEN_HINTS = {
        "account settings", "profile settings", "settings and privacy",
        "paypal home", "home screen", "dashboard",
        "sign in", "log in", "security", "verify your identity",
        "review your payment", "review payment", "enter an amount",
        "enter amount", "amount to send", "choose recipient",
        "select recipient", "who do you want to pay",
        "who are you sending money to", "send money", "pay someone",
        "payment successful", "payment received", "payment pending",
        "payment failed", "payment declined", "payment error",
        "recent activity", "transaction history",
        "banks and cards", "payment methods",
        "भुगतान सफल", "भुगतान भेजा गया", "राशि दर्ज करें",
        "किसे भुगतान करना है", "प्राप्तकर्ता चुनें", "वॉलेट",
        "गतिविधि", "लेनदेन",
        "చెల్లింపు విజయవంతమైంది", "చెల్లింపు పంపబడింది",
        "మొత్తం నమోదు చేయండి", "ఎవరికి పంపాలి", "గ్రహీతను ఎంచుకోండి",
        "వాలెట్", "కార్యకలాపం", "లావాదేవీలు"
    };

    private ScreenDataSanitizer() {}

    public static String sanitizeLabel(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String safe = value.replaceAll("\\s+", " ").trim();
        if (safe.length() > 300) {
            safe = safe.substring(0, 300);
        }
        safe = EMAIL.matcher(safe).replaceAll(" ");
        safe = PHONE.matcher(safe).replaceAll(" ");
        safe = LONG_NUMBER.matcher(safe).replaceAll(" ");
        safe = CURRENCY_AMOUNT.matcher(safe).replaceAll(" ");
        safe = SENSITIVE_VALUE.matcher(safe).replaceAll(" ");
        safe = FINANCIAL_VALUE.matcher(safe).replaceAll(" ");
        safe = safe.replaceAll("\\s+", " ").trim();
        return safe.isEmpty() ? null : safe;
    }

    public static String extractVisualScreenHints(String recognizedText) {
        if (recognizedText == null || recognizedText.trim().isEmpty()) {
            return "";
        }
        String normalized = recognizedText.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        List<String> hints = new ArrayList<>();
        for (String hint : VISUAL_SCREEN_HINTS) {
            if (normalized.contains(hint) && !hints.contains(hint)) {
                hints.add(hint);
                if (hints.size() == 8) {
                    break;
                }
            }
        }
        return String.join(" ", hints);
    }

    public static boolean isSensitiveField(
            boolean password,
            String resourceId,
            String className,
            String hint,
            String description
    ) {
        if (password) {
            return true;
        }
        String metadata = String.valueOf(resourceId) + " "
                + String.valueOf(className) + " "
                + String.valueOf(hint) + " "
                + String.valueOf(description);
        return SENSITIVE_FIELD.matcher(metadata.toLowerCase(Locale.ROOT)).find();
    }
}
