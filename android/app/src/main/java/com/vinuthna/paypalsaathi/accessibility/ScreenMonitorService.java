package com.vinuthna.paypalsaathi.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.vinuthna.paypalsaathi.ScreenMonitorContract;
import com.vinuthna.paypalsaathi.detection.ScreenClassifier;

import java.util.Locale;

public class ScreenMonitorService extends AccessibilityService {
    private static final String PAYPAL_PACKAGE_KEYWORD = "paypal";

    private String lastCategory = "";
    private long lastBroadcastAt = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String packageName = event.getPackageName().toString();

        if (!packageName.toLowerCase(Locale.ROOT)
                .contains(PAYPAL_PACKAGE_KEYWORD)) {
            return;
        }

        // Text is used transiently in memory for coarse classification only.
        // Never persist it, log it, broadcast it, or send it to a server.
        StringBuilder transientText = new StringBuilder();
        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root != null) {
            collectSafeText(root, transientText, 0);
            root.recycle();
        }

        for (CharSequence item : event.getText()) {
            if (item != null) {
                appendLimited(transientText, item.toString());
            }
        }

        String category = ScreenClassifier.classify(transientText.toString());
        transientText.setLength(0);

        long now = System.currentTimeMillis();

        if (category.equals(lastCategory) && now - lastBroadcastAt < 1500L) {
            return;
        }

        lastCategory = category;
        lastBroadcastAt = now;

        Intent intent = new Intent(
                ScreenMonitorContract.ACTION_SCREEN_CATEGORY);

        intent.setPackage(getPackageName());
        intent.putExtra(ScreenMonitorContract.EXTRA_CATEGORY, category);
        intent.putExtra(
                ScreenMonitorContract.EXTRA_EVENT_TYPE,
                eventTypeLabel(event.getEventType()));
        intent.putExtra(
                ScreenMonitorContract.EXTRA_PACKAGE, "PayPal app");

        sendBroadcast(intent);
    }

    private void collectSafeText(
            AccessibilityNodeInfo node,
            StringBuilder output,
            int depth) {

        if (node == null || depth > 18 || output.length() >= 5000) return;

        // Do not inspect password fields or their descendants.
        if (node.isPassword()) return;

        CharSequence text = node.getText();
        if (text != null) appendLimited(output, text.toString());

        CharSequence description = node.getContentDescription();
        if (description != null) {
            appendLimited(output, description.toString());
        }

        for (int i = 0;
             i < node.getChildCount() && output.length() < 5000;
             i++) {

            AccessibilityNodeInfo child = node.getChild(i);

            if (child != null) {
                collectSafeText(child, output, depth + 1);
                child.recycle();
            }
        }
    }

    private void appendLimited(StringBuilder output, String value) {
        if (value == null || output.length() >= 5000) return;

        output.append(' ').append(
                value,
                0,
                Math.min(value.length(), 300));
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

    @Override
    public void onInterrupt() {
        // No persistent operation to interrupt.
    }
}