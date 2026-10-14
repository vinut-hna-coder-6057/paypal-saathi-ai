package com.vinuthna.paypalsaathi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ScreenContext {
    private static final int MAX_LABELS = 8;
    private ScreenContext() {}

    public static String safeNavigationLabel(CharSequence value) {
        if (value == null || value.length() > 32) {
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
}
