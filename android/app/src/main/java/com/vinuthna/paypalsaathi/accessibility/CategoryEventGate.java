package com.vinuthna.paypalsaathi.accessibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CategoryEventGate {
    private static final long SAME_CATEGORY_INTERVAL_MS = 5_000L;

    private String lastCategory = "";
    private List<String> lastNavigationHints = Collections.emptyList();
    private List<String> lastActionableElements = Collections.emptyList();
    private long lastEmittedAtMs;

    boolean shouldEmit(String category, long elapsedRealtimeMs) {
        return shouldEmit(category, Collections.emptyList(), elapsedRealtimeMs);
    }

    boolean shouldEmit(
            String category,
            List<String> navigationHints,
            long elapsedRealtimeMs
    ) {
        return shouldEmit(
                category,
                navigationHints,
                Collections.emptyList(),
                elapsedRealtimeMs);
    }

    boolean shouldEmit(
            String category,
            List<String> navigationHints,
            List<String> actionableElements,
            long elapsedRealtimeMs
    ) {
        if (category.equals(lastCategory)
                && navigationHints.equals(lastNavigationHints)
                && actionableElements.equals(lastActionableElements)
                && elapsedRealtimeMs - lastEmittedAtMs < SAME_CATEGORY_INTERVAL_MS) {
            return false;
        }

        lastCategory = category;
        lastNavigationHints = Collections.unmodifiableList(
                new ArrayList<>(navigationHints));
        lastActionableElements = Collections.unmodifiableList(
                new ArrayList<>(actionableElements));
        lastEmittedAtMs = elapsedRealtimeMs;
        return true;
    }

    void reset() {
        lastCategory = "";
        lastNavigationHints = Collections.emptyList();
        lastActionableElements = Collections.emptyList();
        lastEmittedAtMs = 0L;
    }
}
