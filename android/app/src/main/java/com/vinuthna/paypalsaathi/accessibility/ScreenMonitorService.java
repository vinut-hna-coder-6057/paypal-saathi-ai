package com.vinuthna.paypalsaathi.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.vinuthna.paypalsaathi.CategoryStore;
import com.vinuthna.paypalsaathi.R;
import com.vinuthna.paypalsaathi.ScreenContext;
import com.vinuthna.paypalsaathi.ScreenMonitorContract;
import com.vinuthna.paypalsaathi.detection.ScreenClassifier;
import com.vinuthna.paypalsaathi.detection.ScreenCategories;
import com.vinuthna.paypalsaathi.detection.ScreenClassification;

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
    private static final String TAG = "SaathiScreenMonitor";
    private static final String PAYPAL_PACKAGE =
            "com.paypal.android.p2pmobile";
    private static final int MAX_TEXT_CHARS = 2_000;
    private static final int MAX_DEPTH = 18;
    private static final int MAX_NODES = 120;
    private static final int MAX_NAVIGATION_HINTS = 6;
    private static final int MAX_ACTIONABLE_ELEMENTS = 8;
    // Bound both missing-root recovery and weak-evidence stabilization equally.
    private static final long CATEGORY_UNCERTAINTY_GRACE_MS = 1_000L;

    private final CategoryEventGate categoryEventGate = new CategoryEventGate();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final RootUnavailableCategoryPolicy rootUnavailablePolicy =
            new RootUnavailableCategoryPolicy(CATEGORY_UNCERTAINTY_GRACE_MS);

    private FloatingAssistantOverlay overlay;
    private boolean paypalActive;
    private String currentCategory = ScreenCategories.UNKNOWN;
    private Runnable pendingUncertainCategory;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName = event.getPackageName().toString();
        int eventType = event.getEventType();

        if (!isWindowTransitionEvent(eventType)
                && !PAYPAL_PACKAGE.equals(packageName)) {
            return;
        }

        boolean logEvent = isWindowTransitionEvent(eventType)
                || (!paypalActive && PAYPAL_PACKAGE.equals(packageName));
        if (logEvent) {
            Log.d(TAG, "Accessibility event: package=" + packageName
                    + ", type=" + AccessibilityEvent.eventTypeToString(eventType));
        }
        String foregroundPackage = foregroundApplicationPackage();
        if (logEvent) {
            Log.d(TAG, "Foreground application: "
                    + (foregroundPackage == null ? "unknown" : foregroundPackage));
        }
        OverlayForegroundPolicy.Decision decision =
                OverlayForegroundPolicy.decide(
                        packageName,
                        foregroundPackage,
                        PAYPAL_PACKAGE,
                        getPackageName());
        if (decision == OverlayForegroundPolicy.Decision.IGNORE) {
            if (logEvent) {
                Log.d(TAG, "Ignoring event for foreground policy.");
            }
            return;
        }
        if (decision == OverlayForegroundPolicy.Decision.HIDE) {
            Log.i(TAG, "PayPal is no longer foreground; hiding assistant.");
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
        Set<String> actionableElements = new LinkedHashSet<>();
        int[] visitedNodes = {0};
        DisplayMetrics screenMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(
                WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(screenMetrics);
        } else {
            screenMetrics.setTo(getResources().getDisplayMetrics());
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean rootPackageMatches = root != null
                && root.getPackageName() != null
                && PAYPAL_PACKAGE.contentEquals(root.getPackageName());
        boolean paypalRootAvailable = root != null && rootPackageMatches;

        if (paypalRootAvailable) {
            try {
                collectSafeText(
                        root,
                        transientText,
                        navigationHints,
                        actionableElements,
                        visitedNodes,
                        screenMetrics.widthPixels,
                        screenMetrics.heightPixels,
                        0);
            } finally {
                root.recycle();
            }
        } else if (root != null) {
            root.recycle();
        }

        if (!paypalRootAvailable) {
            long elapsedRealtime = SystemClock.elapsedRealtime();
            if (rootUnavailablePolicy.shouldPreserve(
                    currentCategory,
                    root != null,
                    rootPackageMatches,
                    elapsedRealtime)) {
                if (overlay != null) {
                    overlay.markScreenContextUncertain();
                }
                scheduleUncertainCategoryExpiry(eventType);
                return;
            }
            resetCategoryUncertainty();
        }

        List<String> actionList = new ArrayList<>(actionableElements);
        ScreenClassification classification = ScreenClassifier.classifyScreen(
                transientText.toString(),
                actionList,
                packageName);
        transientText.setLength(0);
        List<String> safeNavigationHints = new ArrayList<>(navigationHints);
        long observedAt = System.currentTimeMillis();

        if (ScreenCategories.UNKNOWN.equals(classification.category())
                && rootUnavailablePolicy.shouldPreserveUncertainCategory(
                        currentCategory,
                        SystemClock.elapsedRealtime())) {
            if (overlay != null) {
                overlay.markScreenContextUncertain();
            }
            scheduleUncertainCategoryExpiry(eventType);
            return;
        }
        resetCategoryUncertainty();

        publishClassification(
                classification,
                safeNavigationHints,
                eventTypeLabel(eventType),
                observedAt);
    }

    private void publishClassification(
            ScreenClassification classification,
            List<String> safeNavigationHints,
            String eventLabel,
            long observedAt
    ) {
        String category = classification.category();
        List<String> safeActionableElements =
                classification.actionableElements();
        boolean categoryChanged = !category.equals(currentCategory);
        currentCategory = category;
        if (overlay != null) {
            overlay.updateScreenContext(
                    category,
                    safeNavigationHints,
                    safeActionableElements,
                    classification.confidence(),
                    classification.evidenceStrength(),
                    classification.ambiguous(),
                    classification.evidenceCodes(),
                    observedAt);
        }

        if (!categoryEventGate.shouldEmit(
                category,
                safeNavigationHints,
                safeActionableElements,
                SystemClock.elapsedRealtime())) {
            return;
        }

        CategoryStore.save(
                this,
                category,
                eventLabel,
                classification.confidence(),
                classification.evidenceStrength(),
                classification.ambiguous());

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
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_ACTIONABLE_ELEMENTS,
                new ArrayList<>(safeActionableElements));
        intent.putExtra(
                ScreenMonitorContract.EXTRA_CONFIDENCE,
                classification.confidence());
        intent.putExtra(
                ScreenMonitorContract.EXTRA_EVIDENCE_STRENGTH,
                classification.evidenceStrength());
        intent.putExtra(
                ScreenMonitorContract.EXTRA_AMBIGUOUS,
                classification.ambiguous());
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_EVIDENCE_CODES,
                new ArrayList<>(classification.evidenceCodes()));
        intent.putExtra(ScreenMonitorContract.EXTRA_OBSERVED_AT, observedAt);
        sendBroadcast(intent);
    }

    private void scheduleUncertainCategoryExpiry(int eventType) {
        if (pendingUncertainCategory != null) {
            return;
        }

        long remainingMs = rootUnavailablePolicy.remainingMs(
                SystemClock.elapsedRealtime());
        Runnable expiry = new Runnable() {
            @Override
            public void run() {
                if (pendingUncertainCategory != this) {
                    return;
                }
                pendingUncertainCategory = null;
                if (!paypalActive
                        || !ScreenCategories.isKnownCategory(currentCategory)
                        || ScreenCategories.UNKNOWN.equals(currentCategory)) {
                    return;
                }
                if (rootUnavailablePolicy.shouldPreserve(
                        currentCategory,
                        false,
                        false,
                        SystemClock.elapsedRealtime())) {
                    scheduleUncertainCategoryExpiry(eventType);
                    return;
                }

                publishClassification(
                        ScreenClassification.unknown(
                                false,
                                "insufficient_accessibility"),
                        new ArrayList<>(),
                        eventTypeLabel(eventType),
                        System.currentTimeMillis());
            }
        };
        pendingUncertainCategory = expiry;
        mainHandler.postDelayed(expiry, remainingMs);
    }

    private void resetCategoryUncertainty() {
        if (pendingUncertainCategory != null) {
            mainHandler.removeCallbacks(pendingUncertainCategory);
            pendingUncertainCategory = null;
        }
        rootUnavailablePolicy.reset();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility service connected.");
        overlay = new FloatingAssistantOverlay(this);
        String foregroundPackage = foregroundApplicationPackage();
        Log.i(TAG, "Foreground application at connection: "
                + (foregroundPackage == null ? "unknown" : foregroundPackage));
        if (PAYPAL_PACKAGE.equals(foregroundPackage)) {
            enterPayPal();
        }
    }

    private void collectSafeText(
            AccessibilityNodeInfo node,
            StringBuilder output,
            Set<String> navigationHints,
            Set<String> actionableElements,
            int[] visitedNodes,
            int screenWidth,
            int screenHeight,
            int depth
    ) {
        if (node == null
                || depth > MAX_DEPTH
                || visitedNodes[0] >= MAX_NODES) {
            return;
        }
        visitedNodes[0]++;

        Rect clickableBounds = null;
        if (node.isClickable()) {
            Rect nodeBounds = new Rect();
            node.getBoundsInScreen(nodeBounds);
            clickableBounds = nodeBounds;
        }

        // Do not inspect password fields or their descendants.
        if (node.isPassword()) {
            return;
        }

        CharSequence text = node.getText();
        if (text != null) {
            appendLimited(output, text.toString());
            addNavigationHint(navigationHints, text);
            addActionableElement(
                    actionableElements,
                    text,
                    clickableBounds,
                    screenWidth,
                    screenHeight);
        }

        CharSequence description = node.getContentDescription();
        if (description != null) {
            appendLimited(output, description.toString());
            addNavigationHint(navigationHints, description);
            addActionableElement(
                    actionableElements,
                    description,
                    clickableBounds,
                    screenWidth,
                    screenHeight);
        }

        for (int i = 0;
             i < node.getChildCount()
                     && visitedNodes[0] < MAX_NODES;
             i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child != null) {
                try {
                    collectSafeText(
                            child,
                            output,
                            navigationHints,
                            actionableElements,
                            visitedNodes,
                            screenWidth,
                            screenHeight,
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

    private void addActionableElement(
            Set<String> actionableElements,
            CharSequence label,
            Rect bounds,
            int screenWidth,
            int screenHeight
    ) {
        if (bounds == null || actionableElements.size() >= MAX_ACTIONABLE_ELEMENTS) {
            return;
        }
        String descriptor = ScreenContext.safeActionDescriptor(
                label,
                true,
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                screenWidth,
                screenHeight);
        if (descriptor != null) {
            actionableElements.add(descriptor);
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
        if (!paypalActive) {
            paypalActive = true;
            currentCategory = ScreenCategories.UNKNOWN;
            categoryEventGate.reset();
            resetCategoryUncertainty();
            CategoryStore.clear(this);
            Log.i(TAG, "Official PayPal package became active.");
        }

        if (overlay != null) {
            overlay.show();
        }
    }

    private void leavePayPal() {
        if (!paypalActive) {
            return;
        }

        paypalActive = false;
        Log.i(TAG, "Clearing PayPal screen context.");
        currentCategory = ScreenCategories.UNKNOWN;
        categoryEventGate.reset();
        resetCategoryUncertainty();
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
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_ACTIONABLE_ELEMENTS,
                new ArrayList<>());
        intent.putExtra(ScreenMonitorContract.EXTRA_OBSERVED_AT, 0L);
        sendBroadcast(intent);
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.");
        leavePayPal();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Accessibility service destroyed.");
        leavePayPal();
        if (overlay != null) {
            overlay.hide();
        }
        super.onDestroy();
    }
}
