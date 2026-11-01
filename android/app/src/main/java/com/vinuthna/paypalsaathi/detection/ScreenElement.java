package com.vinuthna.paypalsaathi.detection;

import com.vinuthna.paypalsaathi.ScreenContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ScreenElement {
    private static final List<String> SAFE_ACTION_TYPES = Arrays.asList(
            "tap",
            "focus",
            "scroll_forward",
            "scroll_backward");
    private static final List<String> SAFE_EVIDENCE_CODES = Arrays.asList(
            "allowlisted_navigation_label",
            "enabled_clickable_control");

    public enum Role {
        BUTTON,
        IMAGE_BUTTON,
        TAB,
        TEXT,
        EDIT_TEXT,
        CHECKBOX,
        RADIO_BUTTON,
        SWITCH,
        IMAGE,
        LIST_ITEM,
        CONTAINER,
        UNKNOWN,
        OTHER
    }

    public enum RiskCategory {
        SAFE_NAVIGATION,
        RECIPIENT_SELECTION,
        AMOUNT_ENTRY,
        FINANCIAL_AUTHORIZATION,
        SECURITY,
        UNKNOWN_RISK,
        NOT_ACTIONABLE
    }

    private final String label;
    private final String accessibilityDescription;
    private final Role role;
    private final boolean clickable;
    private final boolean enabled;
    private final boolean focused;
    private final boolean editable;
    private final String editableFieldType;
    private final List<String> supportedActions;
    private final boolean safeToRecommend;
    private final RiskCategory riskCategory;
    private final List<String> evidence;
    private final int confidence;
    private final String actionDescriptor;

    ScreenElement(
            String label,
            String accessibilityDescription,
            Role role,
            boolean clickable,
            boolean enabled,
            boolean focused,
            boolean editable,
            String editableFieldType,
            List<String> supportedActions,
            boolean safeToRecommend,
            RiskCategory riskCategory,
            List<String> evidence,
            int confidence,
            String actionDescriptor
    ) {
        this.label = ScreenDataSanitizer.sanitizeLabel(label);
        this.accessibilityDescription =
                ScreenDataSanitizer.sanitizeLabel(accessibilityDescription);
        this.role = role == null ? Role.UNKNOWN : role;
        this.clickable = clickable;
        this.enabled = enabled;
        this.focused = focused;
        this.editable = editable;
        this.editableFieldType = safeFieldType(editableFieldType, editable);
        this.supportedActions = safeValues(
                supportedActions,
                SAFE_ACTION_TYPES);
        this.riskCategory = riskCategory == null
                ? RiskCategory.NOT_ACTIONABLE
                : riskCategory;
        List<String> safeDescriptors =
                ScreenContext.sanitizeActionDescriptors(
                        Collections.singletonList(actionDescriptor));
        boolean recommendableRole = this.role == Role.BUTTON
                || this.role == Role.IMAGE_BUTTON
                || this.role == Role.TAB;
        this.safeToRecommend = safeToRecommend
                && recommendableRole
                && clickable
                && enabled
                && !editable
                && this.supportedActions.contains("tap")
                && !safeDescriptors.isEmpty()
                && this.riskCategory == RiskCategory.SAFE_NAVIGATION;
        this.evidence = safeValues(evidence, SAFE_EVIDENCE_CODES);
        this.confidence = this.safeToRecommend
                ? Math.max(0, Math.min(confidence, 100))
                : 0;
        this.actionDescriptor = this.safeToRecommend
                ? safeDescriptors.get(0)
                : null;
    }

    private static List<String> safeValues(
            List<String> values,
            List<String> allowed
    ) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> safe = new ArrayList<>();
        for (String value : values) {
            if (allowed.contains(value) && !safe.contains(value)) {
                safe.add(value);
            }
        }
        return Collections.unmodifiableList(safe);
    }

    private static String safeFieldType(String value, boolean editable) {
        if (!editable) {
            return null;
        }
        if ("text".equals(value)
                || "number".equals(value)
                || "email".equals(value)
                || "phone".equals(value)
                || "date".equals(value)
                || "sensitive".equals(value)) {
            return value;
        }
        return "unknown";
    }

    public String label() { return label; }
    public String accessibilityDescription() { return accessibilityDescription; }
    public Role role() { return role; }
    public boolean clickable() { return clickable; }
    public boolean enabled() { return enabled; }
    public boolean focused() { return focused; }
    public boolean editable() { return editable; }
    public String editableFieldType() { return editableFieldType; }
    public List<String> supportedActions() { return supportedActions; }
    public boolean safeToRecommend() { return safeToRecommend; }
    public RiskCategory riskCategory() { return riskCategory; }
    public List<String> evidence() { return evidence; }
    public int confidence() { return confidence; }
    public String actionDescriptor() { return actionDescriptor; }
}
