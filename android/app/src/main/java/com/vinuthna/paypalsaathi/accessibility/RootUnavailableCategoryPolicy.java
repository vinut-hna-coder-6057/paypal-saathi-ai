package com.vinuthna.paypalsaathi.accessibility;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

final class RootUnavailableCategoryPolicy {
    private final long gracePeriodMs;
    private long unavailableSinceMs = -1L;

    RootUnavailableCategoryPolicy(long gracePeriodMs) {
        this.gracePeriodMs = Math.max(0L, gracePeriodMs);
    }

    boolean shouldPreserve(
            String category,
            boolean rootAvailable,
            boolean rootPackageMatches,
            long elapsedRealtimeMs
    ) {
        if (rootAvailable && rootPackageMatches) {
            reset();
            return false;
        }

        return shouldPreserveUncertainCategory(category, elapsedRealtimeMs);
    }

    boolean shouldPreserveUncertainCategory(
            String category,
            long elapsedRealtimeMs
    ) {
        if (!ScreenCategories.isKnownCategory(category)
                || ScreenCategories.UNKNOWN.equals(category)) {
            reset();
            return false;
        }

        if (unavailableSinceMs < 0L) {
            unavailableSinceMs = elapsedRealtimeMs;
            return gracePeriodMs > 0L;
        }

        if (elapsedRealtimeMs - unavailableSinceMs < gracePeriodMs) {
            return true;
        }

        reset();
        return false;
    }

    long remainingMs(long elapsedRealtimeMs) {
        if (unavailableSinceMs < 0L) {
            return gracePeriodMs;
        }
        return Math.max(
                0L,
                gracePeriodMs - (elapsedRealtimeMs - unavailableSinceMs));
    }

    void reset() {
        unavailableSinceMs = -1L;
    }
}
