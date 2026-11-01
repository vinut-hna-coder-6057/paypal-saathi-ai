package com.vinuthna.paypalsaathi.detection;

import com.vinuthna.paypalsaathi.ScreenContext;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rule-based (non-AI) screen classifier.
 * Returns canonical category IDs from {@link ScreenCategories}.
 * Keyword matching is intentionally conservative. Not all PayPal screens or
 * localized labels can be recognized automatically.
 */
public final class ScreenClassifier {
    private ScreenClassifier() {}

    public static String classify(String rawText) {
        return classify(rawText, null);
    }

    public static String classify(
            String rawText,
            List<String> actionableElements
    ) {
        return classifyScreen(
                rawText,
                actionableElements,
                "com.paypal.android.p2pmobile").category();
    }

    public static ScreenClassification classifyScreen(
            String rawText,
            List<String> actionableElements,
            String sourcePackage
    ) {
        if (!"com.paypal.android.p2pmobile".equals(sourcePackage)) {
            return ScreenClassification.unknown(true, "unrelated_package");
        }

        List<String> safeActions =
                ScreenContext.sanitizeActionDescriptors(actionableElements);
        String text = normalize(rawText == null ? "" : rawText);
        if (text.isEmpty()) {
            if (hasConfidentHomeActions(safeActions)) {
                return result(
                        ScreenCategories.HOME,
                        82,
                        "moderate",
                        false,
                        Collections.singletonList("home_action_combination"),
                        safeActions);
            }
            return unknown(safeActions, false, "insufficient_accessibility");
        }

        // Authentication and payment-review signals take priority over navigation.
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
            return result(
                    ScreenCategories.SECURITY,
                    96,
                    "strong",
                    false,
                    Collections.singletonList("authentication_prompt"),
                    safeActions);
        }

        if (containsAny(text,
                "log in",
                "login",
                "sign in",
                "welcome to paypal",
                "create an account",
                "लॉग इन",
                "साइन इन",
                "సైన్ ఇన్")) {
            return result(
                    ScreenCategories.SIGN_IN,
                    92,
                    "strong",
                    false,
                    Collections.singletonList("sign_in_prompt"),
                    safeActions);
        }

        boolean loading = containsAny(text,
                "loading",
                "please wait",
                "लोड हो रहा",
                "कृपया प्रतीक्षा करें",
                "లోడ్ అవుతోంది",
                "దయచేసి వేచి ఉండండి");
        boolean error = containsAny(text,
                "something went wrong",
                "unable to complete",
                "an error occurred",
                "try again later",
                "సమస్య ఏర్పడింది",
                "లోపం",
                "कुछ गलत हुआ",
                "त्रुटि",
                "पुनः प्रयास करें");
        if (loading && error) {
            return unknown(safeActions, true, "conflicting_loading_error_signals");
        }
        if (loading) {
            return result(
                    ScreenCategories.LOADING,
                    86,
                    "strong",
                    false,
                    Collections.singletonList("loading_screen"),
                    safeActions);
        }
        if (error) {
            return result(
                    ScreenCategories.ERROR,
                    86,
                    "strong",
                    false,
                    Collections.singletonList("error_screen"),
                    safeActions);
        }

        List<String> strongMatches = new ArrayList<>();
        List<String> strongEvidence = new ArrayList<>();
        if (containsAny(text,
                "account settings",
                "profile settings",
                "settings and privacy")) {
            strongMatches.add(ScreenCategories.ACCOUNT_SETTINGS);
            strongEvidence.add("settings_heading");
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
                "complete purchase",
                "भुगतान की समीक्षा",
                "भुगतान की जांच करें",
                "చెల్లింపును సమీక్షించండి")) {
            strongMatches.add(ScreenCategories.REVIEW_PAYMENT);
            strongEvidence.add("review_prompt");
        }

        if (containsAny(text,
                "payment sent",
                "you sent",
                "payment complete",
                "transaction complete",
                "payment successful",
                "transaction successful",
                "payment received",
                "भुगतान सफल",
                "भुगतान भेजा गया",
                "చెల్లింపు విజయవంతమైంది",
                "చెల్లింపు పంపబడింది")) {
            strongMatches.add(ScreenCategories.CONFIRMATION);
            strongEvidence.add("confirmation_prompt");
        }

        if (containsAny(text,
                "enter amount",
                "enter an amount",
                "how much",
                "amount to send",
                "choose amount",
                "राशि दर्ज करें",
                "कितनी राशि",
                "మొత్తం నమోదు చేయండి",
                "ఎంత మొత్తం")) {
            strongMatches.add(ScreenCategories.AMOUNT_ENTRY);
            strongEvidence.add("amount_prompt");
        }

        if (containsAny(text,
                "choose who to pay",
                "select a contact",
                "choose recipient",
                "select recipient",
                "who do you want to pay",
                "who are you sending money to",
                "किसे भुगतान करना है",
                "प्राप्तकर्ता चुनें",
                "ఎవరికి పంపాలి",
                "గ్రహీతను ఎంచుకోండి")) {
            strongMatches.add(ScreenCategories.RECIPIENT_SELECTION);
            strongEvidence.add("recipient_prompt");
        }

        if (strongMatches.size() > 1) {
            return unknown(safeActions, true, "conflicting_screen_signals");
        }
        if (!strongMatches.isEmpty()) {
            return result(
                    strongMatches.get(0),
                    92,
                    "strong",
                    false,
                    Collections.singletonList(strongEvidence.get(0)),
                    safeActions);
        }

        if (hasConfidentHomeActions(safeActions)) {
            return result(
                    ScreenCategories.HOME,
                    82,
                    "moderate",
                    false,
                    Collections.singletonList("home_action_combination"),
                    safeActions);
        }

        boolean sendFlowHeading = containsAny(text,
                "send money flow",
                "send money",
                "pay someone",
                "पैसे भेजें",
                "धन भेजें",
                "డబ్బు పంపండి");
        if (sendFlowHeading && hasSendFlowControl(safeActions)) {
            return result(
                    ScreenCategories.SEND_MONEY,
                    78,
                    "moderate",
                    false,
                    Collections.singletonList("send_flow_heading"),
                    safeActions);
        }

        if (containsAny(text,
                "wallet screen",
                "banks and cards",
                "payment methods",
                "linked accounts",
                "manage payment methods",
                "वॉलेट",
                "बटुआ",
                "వాలెట్")
                || text.equals("wallet")) {
            return result(
                    ScreenCategories.WALLET,
                    84,
                    "moderate",
                    false,
                    Collections.singletonList("wallet_heading"),
                    safeActions);
        }

        if (containsAny(text,
                "transactions",
                "recent activity",
                "transaction history",
                "गतिविधि",
                "लेनदेन",
                "కార్యకలాపం",
                "లావాదేవీలు")
                || text.equals("activity")) {
            return result(
                    ScreenCategories.ACTIVITY,
                    82,
                    "moderate",
                    false,
                    Collections.singletonList("activity_heading"),
                    safeActions);
        }

        if (containsAny(text,
                "paypal home",
                "home screen",
                "dashboard",
                "account overview")
                || text.equals("home")) {
            return result(
                    ScreenCategories.HOME,
                    84,
                    "moderate",
                    false,
                    Collections.singletonList("home_heading"),
                    safeActions);
        }

        return unknown(safeActions, false, "insufficient_accessibility");
    }

    private static ScreenClassification result(
            String category,
            int confidence,
            String strength,
            boolean ambiguous,
            List<String> evidence,
            List<String> actions
    ) {
        return new ScreenClassification(
                category,
                confidence,
                strength,
                ambiguous,
                evidence,
                actions);
    }

    private static ScreenClassification unknown(
            List<String> actions,
            boolean ambiguous,
            String evidence
    ) {
        return new ScreenClassification(
                ScreenCategories.UNKNOWN,
                0,
                "insufficient",
                ambiguous,
                Collections.singletonList(evidence),
                actions);
    }

    private static boolean hasConfidentHomeActions(List<String> elements) {
        if (elements == null || elements.isEmpty()) {
            return false;
        }

        Set<String> homeActions = new HashSet<>();
        for (String element : elements) {
            String label = ScreenContext.actionLabel(element);
            if ("Send".equals(label)
                    || "Request".equals(label)
                    || "Add card or bank".equals(label)
                    || "Go to Resolution Centre".equals(label)) {
                homeActions.add(label);
            }
        }
        return homeActions.size() >= 2;
    }

    private static boolean hasSendFlowControl(List<String> actions) {
        for (String action : actions) {
            String label = ScreenContext.actionLabel(action);
            if ("Continue".equals(label) || "Next".equals(label)) {
                return true;
            }
        }
        return false;
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
