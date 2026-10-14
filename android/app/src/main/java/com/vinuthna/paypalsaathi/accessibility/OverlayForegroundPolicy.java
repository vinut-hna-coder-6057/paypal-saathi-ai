package com.vinuthna.paypalsaathi.accessibility;

final class OverlayForegroundPolicy {
    enum Decision {
        IGNORE,
        SHOW,
        HIDE
    }

    private OverlayForegroundPolicy() {}

    static Decision decide(
            String eventPackage,
            String foregroundPackage,
            String paypalPackage,
            String companionPackage
    ) {
        if (companionPackage.equals(eventPackage)) {
            return Decision.IGNORE;
        }
        if (foregroundPackage == null) {
            return paypalPackage.equals(eventPackage)
                    ? Decision.SHOW
                    : Decision.IGNORE;
        }
        return paypalPackage.equals(foregroundPackage)
                ? Decision.SHOW
                : Decision.HIDE;
    }
}
