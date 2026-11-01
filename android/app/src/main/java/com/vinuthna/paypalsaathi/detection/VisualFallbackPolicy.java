package com.vinuthna.paypalsaathi.detection;

public final class VisualFallbackPolicy {
    private static final int SCREENSHOT_API_LEVEL = 30;

    public boolean shouldCapture(
            boolean userConsented,
            int apiLevel,
            boolean paypalIsForeground,
            ScreenState accessibilityState
    ) {
        return userConsented
                && apiLevel >= SCREENSHOT_API_LEVEL
                && paypalIsForeground
                && accessibilityState != null
                && accessibilityState.observationAvailable()
                && ScreenCategories.UNKNOWN.equals(
                        accessibilityState.category());
    }
}
