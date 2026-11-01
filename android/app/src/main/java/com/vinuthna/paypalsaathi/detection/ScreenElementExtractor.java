package com.vinuthna.paypalsaathi.detection;

import com.vinuthna.paypalsaathi.ScreenContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScreenElementExtractor {
    private static final List<String> SAFE_ACTIONS = Arrays.asList(
            "tap",
            "focus",
            "scroll_forward",
            "scroll_backward");

    private ScreenElementExtractor() {}

    public static List<ScreenElement> extract(
            List<ObservedUiElement> observations,
            String screenCategory,
            int screenConfidence
    ) {
        if (observations == null || observations.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        Map<String, ScreenElement> unique = new LinkedHashMap<>();
        for (ObservedUiElement observed : observations) {
            if (observed == null) {
                continue;
            }

            ScreenElement.Role role = roleFor(observed.className(), observed.editable());
            boolean sensitiveField = observed.sensitive()
                    || ScreenDataSanitizer.isSensitiveField(
                            false,
                            null,
                            observed.className(),
                            observed.hint(),
                            observed.contentDescription());
            String label = sensitiveField
                    ? null
                    : ScreenDataSanitizer.sanitizeLabel(
                            observed.editable() ? observed.hint() : observed.text());
            String description = sensitiveField
                    ? null
                    : ScreenDataSanitizer.sanitizeLabel(
                            observed.contentDescription());
            if (label == null && description != null) {
                label = description;
            }
            if (label == null && !observed.editable()) {
                continue;
            }

            String safeNavigationLabel = ScreenContext.safeNavigationLabel(label);
            boolean supportedTap = observed.supportedActions().contains("tap");
            String actionDescriptor = null;
            if (observed.clickable()
                    && observed.enabled()
                    && supportedTap
                    && safeNavigationLabel != null) {
                actionDescriptor = ScreenContext.safeActionDescriptor(
                        safeNavigationLabel,
                        true,
                        observed.left(),
                        observed.top(),
                        observed.right(),
                        observed.bottom(),
                        observed.screenWidth(),
                        observed.screenHeight());
            }

            ScreenElement.RiskCategory risk = riskCategory(
                    screenCategory,
                    label,
                    safeNavigationLabel,
                    sensitiveField,
                    observed.editable());
            boolean buttonRole = role == ScreenElement.Role.BUTTON
                    || role == ScreenElement.Role.IMAGE_BUTTON
                    || role == ScreenElement.Role.TAB;
            boolean safeToRecommend = actionDescriptor != null
                    && buttonRole
                    && screenConfidence >= 75
                    && risk == ScreenElement.RiskCategory.SAFE_NAVIGATION;
            List<String> evidence = new ArrayList<>();
            if (safeNavigationLabel != null) {
                evidence.add("allowlisted_navigation_label");
            }
            if (observed.clickable() && observed.enabled() && supportedTap) {
                evidence.add("enabled_clickable_control");
            }
            List<String> actions = safeSupportedActions(observed.supportedActions());
            String fieldType = observed.editable()
                    ? (sensitiveField ? "sensitive" : safeFieldType(observed.fieldType()))
                    : null;
            int confidence = safeToRecommend ? screenConfidence : 0;
            ScreenElement element = new ScreenElement(
                    label,
                    description,
                    role,
                    observed.clickable(),
                    observed.enabled(),
                    observed.focused(),
                    observed.editable(),
                    fieldType,
                    actions,
                    safeToRecommend,
                    risk,
                    evidence,
                    confidence,
                    actionDescriptor);
            String key = role + "|" + label + "|" + description + "|"
                    + observed.clickable() + "|" + observed.enabled()
                    + "|" + observed.editable();
            unique.putIfAbsent(key, element);
        }
        return java.util.Collections.unmodifiableList(
                new ArrayList<>(unique.values()));
    }

    private static ScreenElement.Role roleFor(String className, boolean editable) {
        if (editable) {
            return ScreenElement.Role.EDIT_TEXT;
        }
        String normalized = className == null
                ? ""
                : className.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("imagebutton")) {
            return ScreenElement.Role.IMAGE_BUTTON;
        }
        if (normalized.endsWith("button")) {
            return ScreenElement.Role.BUTTON;
        }
        if (normalized.endsWith("tab") || normalized.endsWith("tabview")) {
            return ScreenElement.Role.TAB;
        }
        if (normalized.contains("listitem")) {
            return ScreenElement.Role.LIST_ITEM;
        }
        if (normalized.endsWith("listview")
                || normalized.endsWith("recyclerview")
                || normalized.endsWith("scrollview")
                || normalized.endsWith("viewgroup")
                || normalized.endsWith("linearlayout")
                || normalized.endsWith("framelayout")
                || normalized.endsWith("constraintlayout")) {
            return ScreenElement.Role.CONTAINER;
        }
        if (normalized.endsWith("checkbox")) {
            return ScreenElement.Role.CHECKBOX;
        }
        if (normalized.endsWith("radiobutton")) {
            return ScreenElement.Role.RADIO_BUTTON;
        }
        if (normalized.endsWith("switch")) {
            return ScreenElement.Role.SWITCH;
        }
        if (normalized.endsWith("edittext")) {
            return ScreenElement.Role.EDIT_TEXT;
        }
        if (normalized.endsWith("imageview")) {
            return ScreenElement.Role.IMAGE;
        }
        if (normalized.endsWith("textview")) {
            return ScreenElement.Role.TEXT;
        }
        return ScreenElement.Role.UNKNOWN;
    }

    private static ScreenElement.RiskCategory riskCategory(
            String screenCategory,
            String label,
            String safeNavigationLabel,
            boolean sensitiveField,
            boolean editable
    ) {
        if (sensitiveField || ScreenCategories.SECURITY.equals(screenCategory)
                || ScreenCategories.SIGN_IN.equals(screenCategory)) {
            return ScreenElement.RiskCategory.SECURITY;
        }
        if (ScreenCategories.RECIPIENT_SELECTION.equals(screenCategory)) {
            return ScreenElement.RiskCategory.RECIPIENT_SELECTION;
        }
        if (ScreenCategories.AMOUNT_ENTRY.equals(screenCategory) || editable) {
            return ScreenElement.RiskCategory.AMOUNT_ENTRY;
        }
        if (isAuthorizationLabel(label)
                || ("Send".equals(label)
                    && !ScreenCategories.HOME.equals(screenCategory))) {
            return ScreenElement.RiskCategory.FINANCIAL_AUTHORIZATION;
        }
        if (ScreenCategories.REVIEW_PAYMENT.equals(screenCategory)
                && !"Back".equals(safeNavigationLabel)) {
            return ScreenElement.RiskCategory.FINANCIAL_AUTHORIZATION;
        }
        if (ScreenCategories.CONFIRMATION.equals(screenCategory)
                && safeNavigationLabel != null
                && !"Back".equals(safeNavigationLabel)
                && !"Home".equals(safeNavigationLabel)
                && !"Activity".equals(safeNavigationLabel)) {
            return ScreenElement.RiskCategory.UNKNOWN_RISK;
        }
        if (safeNavigationLabel != null) {
            return ScreenElement.RiskCategory.SAFE_NAVIGATION;
        }
        return ScreenElement.RiskCategory.NOT_ACTIONABLE;
    }

    private static boolean isAuthorizationLabel(String label) {
        return "Pay".equals(label)
                || "Confirm".equals(label)
                || "Pay now".equals(label)
                || "Review and send".equals(label);
    }

    private static List<String> safeSupportedActions(List<String> actions) {
        List<String> safe = new ArrayList<>();
        for (String action : actions) {
            if (SAFE_ACTIONS.contains(action) && !safe.contains(action)) {
                safe.add(action);
            }
        }
        return safe;
    }

    private static String safeFieldType(String fieldType) {
        if ("text".equals(fieldType)
                || "number".equals(fieldType)
                || "email".equals(fieldType)
                || "phone".equals(fieldType)
                || "date".equals(fieldType)) {
            return fieldType;
        }
        return "unknown";
    }
}
