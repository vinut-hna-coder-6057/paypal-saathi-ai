package com.vinuthna.paypalsaathi;

import java.util.regex.Pattern;

public final class QuestionInputValidator {
    private static final Pattern CREDENTIAL_VALUE = Pattern.compile(
            "\\b(password|passcode|pin|otp|verification code|security code|"
                    + "cvv|cvc|recovery code|card number|account number)\\b"
                    + "\\s*(?:is\\s+|[:=]\\s*)\\S+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_NUMBER = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    private static final Pattern CODE_VALUE = Pattern.compile(
            "\\b(otp|verification code|security code|cvv|cvc|pin)\\b"
                    + "\\s*(is|:|=)?\\s*\\d{3,8}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_ADDRESS = Pattern.compile(
            "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_NUMBER = Pattern.compile(
            "(?:\\+?\\d[\\d ()-]{8,}\\d)");
 private static final Pattern SENSITIVE_AMOUNT = Pattern.compile(
        "(?:\\b(amount|balance|payment amount|transfer amount)\\b"
                + "\\s*(?:is\\s+|[:=]\\s*)?"
                + "(?:INR|USD|EUR|GBP|Rs\\.?|Rupees?|\\$|"
                + "\\x{20B9}|\\x{20AC}|\\x{00A3})?\\s*\\d[\\d,.]*"
                + "|(?:INR|USD|EUR|GBP|Rs\\.?|Rupees?|\\$|"
                + "\\x{20B9}|\\x{20AC}|\\x{00A3})\\s*\\d[\\d,.]*)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_IDENTIFIER = Pattern.compile(
            "\\b(account|transaction|reference|recipient)\\s*"
                    + "(id|number|details?)\\s*(is\\s+|[:=]\\s*)\\S+",
            Pattern.CASE_INSENSITIVE);

    private QuestionInputValidator() {}

    public static boolean containsLikelySensitiveValue(String question) {
        if (question == null) {
            return false;
        }
        return CREDENTIAL_VALUE.matcher(question).find()
                || LONG_NUMBER.matcher(question).find()
                || CODE_VALUE.matcher(question).find()
                || EMAIL_ADDRESS.matcher(question).find()
                || PHONE_NUMBER.matcher(question).find()
                || SENSITIVE_AMOUNT.matcher(question).find()
                || SENSITIVE_IDENTIFIER.matcher(question).find();
    }
}
