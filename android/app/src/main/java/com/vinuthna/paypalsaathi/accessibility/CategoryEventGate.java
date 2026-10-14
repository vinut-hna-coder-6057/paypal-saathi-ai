package com.vinuthna.paypalsaathi.accessibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CategoryEventGate {
    private static final long SAME_CATEGORY_INTERVAL_MS = 5_000L;

    private String lastCategory = "";
    private List<String> lastNavigationHints = Collections.emptyList();
    private long lastEmittedAtMs;

    boolean shouldEmit(String category, long elapsedRealtimeMs) {
        return shouldEmit(category, Collections.emptyList(), elapsedRealtimeMs);
    }

    boolean shouldEmit(
            String category,
            List<String> navigationHints,
            long elapsedRealtimeMs
    ) {
        if (category.equals(lastCategory)
                && navigationHints.equals(lastNavigationHints)
                && elapsedRealtimeMs - lastEmittedAtMs < SAME_CATEGORY_INTERVAL_MS) {
            return false;
        }

        lastCategory = category;
        lastNavigationHints = Collections.unmodifiableList(
                new ArrayList<>(navigationHints));
        lastEmittedAtMs = elapsedRealtimeMs;
        return true;
    }

    void reset() {
        lastCategory = "";
        lastNavigationHints = Collections.emptyList();
        lastEmittedAtMs = 0L;
    }
}
