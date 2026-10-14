package com.vinuthna.paypalsaathi;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Offline, rule-based guidance used when the backend is unreachable.
 * Mirrors backend safety messaging; not an AI model.
 */
public final class LocalGuidanceFallback {
    private LocalGuidanceFallback() {}

    public static JSONObject forCategory(String category) {
        String resolved = category == null || category.trim().isEmpty()
                ? ScreenCategories.UNKNOWN
                : category.trim();

        String title;
        String[] steps;

        switch (resolved) {
            case ScreenCategories.HOME:
                title = "Understand your PayPal home screen";
                steps = new String[] {
                        "Look at the options available on the current screen.",
                        "Choose the feature that matches what you want to do.",
                        "If you are unsure, ask Saathi before continuing."
                };
                break;
            case ScreenCategories.SEND_MONEY:
                title = "Sending money safely";
                steps = new String[] {
                        "Open the Send option in the official PayPal app.",
                        "Follow PayPal's instructions to select the intended recipient.",
                        "Verify the recipient and amount on PayPal before proceeding.",
                        "Review all transaction details yourself before approving anything."
                };
                break;
            case ScreenCategories.RECIPIENT_SELECTION:
                title = "Choose the intended recipient";
                steps = new String[] {
                        "Find the recipient using PayPal's available search or selection options.",
                        "Carefully verify the recipient's displayed identity.",
                        "Continue only when you are confident you selected the right person."
                };
                break;
            case ScreenCategories.AMOUNT_ENTRY:
                title = "Enter and verify the amount";
                steps = new String[] {
                        "Enter the amount directly in the official PayPal app.",
                        "Check the currency and amount displayed on the screen.",
                        "Do not continue if anything looks incorrect."
                };
                break;
            case ScreenCategories.REVIEW_PAYMENT:
                title = "Review before approving";
                steps = new String[] {
                        "Verify the recipient shown by PayPal.",
                        "Verify the amount and currency.",
                        "Make your own decision. Saathi will never approve the payment for you."
                };
                break;
            case ScreenCategories.CONFIRMATION:
                title = "Check the displayed payment status";
                steps = new String[] {
                        "Read the status shown in the official PayPal app.",
                        "Do not assume a payment succeeded just because this screen was detected."
                };
                break;
            case ScreenCategories.ACTIVITY:
                title = "Review transaction activity";
                steps = new String[] {
                        "Open Activity in the official PayPal app.",
                        "Review the displayed status and details yourself."
                };
                break;
            case ScreenCategories.WALLET:
                title = "Review wallet options";
                steps = new String[] {
                        "Open Wallet in the official PayPal app.",
                        "Never share card numbers or security credentials with Saathi."
                };
                break;
            case ScreenCategories.SECURITY:
            case ScreenCategories.SIGN_IN:
                title = "Protect your account";
                steps = new String[] {
                        "Enter passwords or verification codes only in the official PayPal app.",
                        "Never share your password, PIN, or OTP with Saathi."
                };
                break;
            case ScreenCategories.UNKNOWN:
            default:
                resolved = ScreenCategories.UNKNOWN;
                title = "Let's identify what you need help with";
                steps = new String[] {
                        "Read the options displayed in the official PayPal app.",
                        "Describe your goal to Saathi in general terms.",
                        "Do not include passwords, PINs, OTPs, or card information."
                };
                break;
        }

        JSONObject result = new JSONObject();

        try {
            result.put("category", resolved);
            result.put("title", title);
            JSONArray array = new JSONArray();
            for (String step : steps) {
                array.put(step);
            }
            result.put("steps", array);
            result.put("matchedBy", "local_fallback");
            result.put(
                    "safetyNote",
                    "Offline guidance. Verify all details in PayPal yourself."
            );
        } catch (Exception ignored) {
            // JSONObject put should not fail for these primitives.
        }

        return result;
    }
}
