package com.vinuthna.paypalsaathi.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Short-lived accessibility data used to construct a sanitized screen model.
 */
public final class ObservedUiElement {
    private final String text;
    private final String contentDescription;
    private final String hint;
    private final String className;
    private final boolean clickable;
    private final boolean enabled;
    private final boolean focused;
    private final boolean editable;
    private final boolean sensitive;
    private final String fieldType;
    private final List<String> supportedActions;
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;
    private final int screenWidth;
    private final int screenHeight;

    public ObservedUiElement(
            String text,
            String contentDescription,
            String hint,
            String className,
            boolean clickable,
            boolean enabled,
            boolean focused,
            boolean editable,
            boolean sensitive,
            String fieldType,
            List<String> supportedActions,
            int left,
            int top,
            int right,
            int bottom,
            int screenWidth,
            int screenHeight
    ) {
        this.text = text;
        this.contentDescription = contentDescription;
        this.hint = hint;
        this.className = className;
        this.clickable = clickable;
        this.enabled = enabled;
        this.focused = focused;
        this.editable = editable;
        this.sensitive = sensitive;
        this.fieldType = fieldType;
        this.supportedActions = supportedActions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(supportedActions));
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    String text() { return text; }
    String contentDescription() { return contentDescription; }
    String hint() { return hint; }
    String className() { return className; }
    boolean clickable() { return clickable; }
    boolean enabled() { return enabled; }
    boolean focused() { return focused; }
    boolean editable() { return editable; }
    boolean sensitive() { return sensitive; }
    String fieldType() { return fieldType; }
    List<String> supportedActions() { return supportedActions; }
    int left() { return left; }
    int top() { return top; }
    int right() { return right; }
    int bottom() { return bottom; }
    int screenWidth() { return screenWidth; }
    int screenHeight() { return screenHeight; }
}
