package com.vinuthna.paypalsaathi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ScreenContext {
    private static final int MAX_LABELS = 8;
    private static final int MAX_ACTIONS = 8;
    private static final List<String> EVIDENCE_CODES = java.util.Arrays.asList(
            "unrelated_package",
            "insufficient_accessibility",
            "conflicting_screen_signals",
            "conflicting_loading_error_signals",
            "authentication_prompt",
            "sign_in_prompt",
            "loading_screen",
            "error_screen",
            "review_prompt",
            "confirmation_prompt",
            "amount_prompt",
            "recipient_prompt",
            "home_action_combination",
            "send_flow_heading",
            "wallet_heading",
            "activity_heading",
            "home_heading",
            "settings_heading",
            "visual_fallback");
    private ScreenContext() {}

    public static String safeNavigationLabel(CharSequence value) {
        if (value == null || value.length() > 40) {
            return null;
        }
        switch (value.toString().trim().toLowerCase(Locale.ROOT)) {
            case "activity":
                return "Activity";
            case "wallet":
                return "Wallet";
            case "home":
                return "Home";
            case "send":
                return "Send";
            case "request":
                return "Request";
            case "add card or bank":
                return "Add card or bank";
            case "go to resolution centre":
            case "go to resolution center":
                return "Go to Resolution Centre";
            case "continue":
                return "Continue";
            case "next":
                return "Next";
            case "payments":
                return "Payments";
            case "transactions":
                return "Transactions";
            case "security":
                return "Security";
            case "settings":
                return "Settings";
            case "help":
                return "Help";
            case "profile":
                return "Profile";
            case "notifications":
                return "Notifications";
            case "cards":
                return "Cards";
            case "bank accounts":
                return "Bank Accounts";
            case "more":
                return "More";
            case "menu":
                return "Menu";
            case "back":
                return "Back";
            default:
                return null;
        }
    }

    public static List<String> sanitizeNavigationLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> safeLabels = new LinkedHashSet<>();
        for (String label : labels) {
            String safeLabel = safeNavigationLabel(label);
            if (safeLabel != null) {
                safeLabels.add(safeLabel);
            }
            if (safeLabels.size() == MAX_LABELS) {
                break;
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(safeLabels));
    }

    public static String safeActionDescriptor(
            CharSequence value,
            boolean actionable,
            int left,
            int top,
            int right,
            int bottom,
            int screenWidth,
            int screenHeight
    ) {
        String label = safeNavigationLabel(value);
        if (!actionable
                || label == null
                || screenWidth <= 0
                || screenHeight <= 0
                || left < 0
                || top < 0
                || right <= left
                || bottom <= top
                || right > screenWidth
                || bottom > screenHeight
                || (long) (right - left) * (bottom - top)
                        > (long) screenWidth * screenHeight / 3) {
            return null;
        }

        String vertical = positionBand(
                top + (bottom - top) / 2,
                screenHeight,
                false);
        String horizontal = positionBand(
                left + (right - left) / 2,
                screenWidth,
                true);
        return label + "|" + vertical + "|" + horizontal;
    }

    public static List<String> sanitizeActionDescriptors(List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> safeActions = new LinkedHashSet<>();
        for (String action : actions) {
            if (action == null) {
                continue;
            }
            String[] parts = action.split("\\|", -1);
            if (parts.length != 3) {
                continue;
            }
            String label = safeNavigationLabel(parts[0]);
            if (label == null
                    || !isVerticalPosition(parts[1])
                    || !isHorizontalPosition(parts[2])) {
                continue;
            }
            safeActions.add(label + "|" + parts[1] + "|" + parts[2]);
            if (safeActions.size() == MAX_ACTIONS) {
                break;
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(safeActions));
    }

    public static String actionLabel(String descriptor) {
        if (descriptor == null) {
            return null;
        }
        List<String> safe = sanitizeActionDescriptors(
                Collections.singletonList(descriptor));
        return safe.isEmpty() ? null : safe.get(0).split("\\|", -1)[0];
    }

    public static List<String> sanitizeEvidenceCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> safe = new LinkedHashSet<>();
        for (String code : codes) {
            if (EVIDENCE_CODES.contains(code)) {
                safe.add(code);
            }
            if (safe.size() == 4) {
                break;
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(safe));
    }

    public static boolean includesVisualFallback(List<String> codes) {
        return codes != null && codes.contains("visual_fallback");
    }

    private static String positionBand(
            int coordinate,
            int size,
            boolean horizontal
    ) {
        if (coordinate < size / 3) {
            return horizontal ? "left" : "top";
        }
        if (coordinate >= (size * 2) / 3) {
            return horizontal ? "right" : "bottom";
        }
        return horizontal ? "center" : "middle";
    }

    private static boolean isVerticalPosition(String value) {
        return "top".equals(value)
                || "middle".equals(value)
                || "bottom".equals(value);
    }

    private static boolean isHorizontalPosition(String value) {
        return "left".equals(value)
                || "center".equals(value)
                || "right".equals(value);
    }
}
