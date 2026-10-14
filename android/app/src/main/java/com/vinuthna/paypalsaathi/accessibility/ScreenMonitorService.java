package com.vinuthna.paypalsaathi.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.vinuthna.paypalsaathi.CategoryStore;
import com.vinuthna.paypalsaathi.R;
import com.vinuthna.paypalsaathi.ScreenContext;
import com.vinuthna.paypalsaathi.ScreenMonitorContract;
import com.vinuthna.paypalsaathi.detection.ScreenClassifier;
import com.vinuthna.paypalsaathi.detection.ScreenCategories;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Accessibility-based screen monitor for the official PayPal app.
 *
 * Privacy rules:
 * - Only processes the configured official PayPal package.
 * - Skips password fields and their descendants.
 * - Keeps scraped text transient in memory for classification only.
 * - Never persists, logs, or uploads raw screen text.
 * - Broadcasts a category and allow-listed navigation labels, not screen text.
 * - Never clicks controls or submits payments.
 */
public class ScreenMonitorService extends AccessibilityService {
    private static final String PAYPAL_PACKAGE =
            "com.paypal.android.p2pmobile";
    private static final int MAX_TEXT_CHARS = 2_000;
    private static final int MAX_DEPTH = 18;
    private static final int MAX_NODES = 120;
    private static final int MAX_NAVIGATION_HINTS = 6;

    private final CategoryEventGate categoryEventGate = new CategoryEventGate();

    private FloatingAssistantOverlay overlay;
    private boolean paypalActive;
    private String currentCategory = ScreenCategories.UNKNOWN;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName = event.getPackageName().toString();

        if (!isWindowTransitionEvent(event.getEventType())
                && !PAYPAL_PACKAGE.equals(packageName)) {
            return;
        }

        int eventType = event.getEventType();
        OverlayForegroundPolicy.Decision decision =
                OverlayForegroundPolicy.decide(
                        packageName,
                        foregroundApplicationPackage(),
                        PAYPAL_PACKAGE,
                        getPackageName());
        if (decision == OverlayForegroundPolicy.Decision.IGNORE) {
            return;
        }
        if (decision == OverlayForegroundPolicy.Decision.HIDE) {
            leavePayPal();
            return;
        }

        enterPayPal();

        if (!PAYPAL_PACKAGE.equals(packageName)) {
            return;
        }

        // Text is used transiently in memory for coarse classification only.
        StringBuilder transientText = new StringBuilder();
        Set<String> navigationHints = new LinkedHashSet<>();
        int[] visitedNodes = {0};
        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root != null
                && root.getPackageName() != null
                && PAYPAL_PACKAGE.contentEquals(root.getPackageName())) {
            try {
                collectSafeText(
                        root,
                        transientText,
                        navigationHints,
                        visitedNodes,
                        0);
            } finally {
                root.recycle();
            }
        } else if (root != null) {
            root.recycle();
        }

        String category = ScreenClassifier.classify(transientText.toString());
        transientText.setLength(0);
        List<String> safeNavigationHints = new ArrayList<>(navigationHints);
        long observedAt = System.currentTimeMillis();

        boolean categoryChanged = !category.equals(currentCategory);
        currentCategory = category;
        if (overlay != null) {
            overlay.updateScreenContext(
                    category,
                    safeNavigationHints,
                    observedAt);
        }

        if (!categoryEventGate.shouldEmit(
                category,
                safeNavigationHints,
                SystemClock.elapsedRealtime())) {
            return;
        }

        String eventLabel = eventTypeLabel(eventType);
        CategoryStore.save(this, category, eventLabel);

        Intent intent = new Intent(ScreenMonitorContract.ACTION_SCREEN_CATEGORY);
        intent.setPackage(getPackageName());
        intent.putExtra(ScreenMonitorContract.EXTRA_CATEGORY, category);
        intent.putExtra(ScreenMonitorContract.EXTRA_EVENT_TYPE, eventLabel);
        intent.putExtra(ScreenMonitorContract.EXTRA_PACKAGE, "PayPal app");
        intent.putExtra(
                ScreenMonitorContract.EXTRA_CATEGORY_CHANGED,
                categoryChanged);
        intent.putExtra(ScreenMonitorContract.EXTRA_PAYPAL_ACTIVE, true);
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_VISIBLE_LABELS,
                new ArrayList<>(safeNavigationHints));
        intent.putExtra(ScreenMonitorContract.EXTRA_OBSERVED_AT, observedAt);
        sendBroadcast(intent);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        overlay = new FloatingAssistantOverlay(this);
        String foregroundPackage = foregroundApplicationPackage();
        if (PAYPAL_PACKAGE.equals(foregroundPackage)) {
            enterPayPal();
        }
    }

    private void collectSafeText(
            AccessibilityNodeInfo node,
            StringBuilder output,
            Set<String> navigationHints,
            int[] visitedNodes,
            int depth
    ) {
        if (node == null
                || depth > MAX_DEPTH
                || output.length() >= MAX_TEXT_CHARS
                || visitedNodes[0] >= MAX_NODES) {
            return;
        }
        visitedNodes[0]++;

        // Do not inspect password fields or their descendants.
        if (node.isPassword()) {
            return;
        }

        CharSequence text = node.getText();
        if (text != null) {
            appendLimited(output, text.toString());
            addNavigationHint(navigationHints, text);
        }

        CharSequence description = node.getContentDescription();
        if (description != null) {
            appendLimited(output, description.toString());
            addNavigationHint(navigationHints, description);
        }

        for (int i = 0;
             i < node.getChildCount()
                     && output.length() < MAX_TEXT_CHARS
                     && visitedNodes[0] < MAX_NODES;
             i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child != null) {
                try {
                    collectSafeText(
                            child,
                            output,
                            navigationHints,
                            visitedNodes,
                            depth + 1);
                } finally {
                    child.recycle();
                }
            }
        }
    }

    private void addNavigationHint(
            Set<String> navigationHints,
            CharSequence value
    ) {
        String safeLabel = ScreenContext.safeNavigationLabel(value);
        if (safeLabel != null && navigationHints.size() < MAX_NAVIGATION_HINTS) {
            navigationHints.add(safeLabel);
        }
    }

    private void appendLimited(StringBuilder output, String value) {
        if (value == null || output.length() >= MAX_TEXT_CHARS) {
            return;
        }

        int available = MAX_TEXT_CHARS - output.length() - 1;
        if (available <= 0) {
            return;
        }
        output.append(' ').append(value, 0, Math.min(
                Math.min(value.length(), 300),
                available));
    }

    private String eventTypeLabel(int type) {
        switch (type) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                return "Window changed";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                return "Content changed";
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                return "View focused";
            default:
                return "UI event";
        }
    }

    private boolean isWindowTransitionEvent(int type) {
        return type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
    }

    private String foregroundApplicationPackage() {
        List<AccessibilityWindowInfo> windows = getWindows();
        int topLayer = Integer.MIN_VALUE;
        String topApplicationPackage = null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                    && window.getLayer() > topLayer) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) {
                    try {
                        CharSequence packageName = root.getPackageName();
                        topApplicationPackage =
                                packageName == null ? null : packageName.toString();
                        topLayer = window.getLayer();
                    } finally {
                        root.recycle();
                    }
                }
            }
            window.recycle();
        }
        return topApplicationPackage;
    }

    private void enterPayPal() {
        if (paypalActive) {
            return;
        }

        paypalActive = true;
        currentCategory = ScreenCategories.UNKNOWN;
        categoryEventGate.reset();
        CategoryStore.clear(this);
        if (overlay != null) {
            overlay.show();
        }
    }

    private void leavePayPal() {
        if (!paypalActive) {
            return;
        }

        paypalActive = false;
        currentCategory = ScreenCategories.UNKNOWN;
        categoryEventGate.reset();
        CategoryStore.clear(this);
        if (overlay != null) {
            overlay.hide();
        }

        Intent intent = new Intent(ScreenMonitorContract.ACTION_SCREEN_CATEGORY);
        intent.setPackage(getPackageName());
        intent.putExtra(
                ScreenMonitorContract.EXTRA_CATEGORY,
                ScreenCategories.UNKNOWN);
        intent.putExtra(
                ScreenMonitorContract.EXTRA_EVENT_TYPE,
                getString(R.string.floating_assistant_no_paypal_context));
        intent.putExtra(
                ScreenMonitorContract.EXTRA_CATEGORY_CHANGED,
                true);
        intent.putExtra(ScreenMonitorContract.EXTRA_PAYPAL_ACTIVE, false);
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_VISIBLE_LABELS,
                new ArrayList<>());
        intent.putExtra(ScreenMonitorContract.EXTRA_OBSERVED_AT, 0L);
        sendBroadcast(intent);
    }

    @Override
    public void onInterrupt() {
        leavePayPal();
    }

    @Override
    public void onDestroy() {
        leavePayPal();
        if (overlay != null) {
            overlay.hide();
        }
        super.onDestroy();
    }
}
