package com.vinuthna.paypalsaathi;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

import java.util.Collections;
import java.util.List;

public final class QuestionRequestTracker {
    private String screenCategory = ScreenCategories.UNKNOWN;
    private List<String> visibleLabels = Collections.emptyList();
    private long observedAt;
    private long contextVersion;
    private long nextRequestId;
    private long activeRequestId = -1L;

    public String screenCategory() {
        return screenCategory;
    }

    public boolean updateScreenCategory(String category) {
        return updateScreenContext(category, Collections.emptyList(), 0L);
    }

    public boolean updateScreenContext(
            String category,
            List<String> labels,
            long observedAtMillis
    ) {
        String safeCategory = ScreenCategories.isKnownCategory(category)
                ? category
                : ScreenCategories.UNKNOWN;
        List<String> safeLabels = ScreenContext.sanitizeNavigationLabels(labels);

        boolean changed = !screenCategory.equals(safeCategory)
                || !visibleLabels.equals(safeLabels);
        screenCategory = safeCategory;
        visibleLabels = safeLabels;
        observedAt = observedAtMillis;
        if (!changed) {
            return false;
        }

        contextVersion++;
        activeRequestId = -1L;
        return true;
    }

    public void invalidate() {
        contextVersion++;
        activeRequestId = -1L;
        screenCategory = ScreenCategories.UNKNOWN;
        visibleLabels = Collections.emptyList();
        observedAt = 0L;
    }

    public Request begin(String question) {
        if (activeRequestId != -1L
                || question == null
                || question.trim().isEmpty()
                || question.trim().length() > 500) {
            return null;
        }

        activeRequestId = ++nextRequestId;
        return new Request(
                activeRequestId,
                contextVersion,
                screenCategory,
                visibleLabels,
                observedAt,
                question.trim());
    }

    public boolean isCurrent(Request request) {
        return request != null
                && request.requestId == activeRequestId
                && request.contextVersion == contextVersion;
    }

    public void finish(Request request) {
        if (isCurrent(request)) {
            activeRequestId = -1L;
        }
    }

    public static final class Request {
        private final long requestId;
        private final long contextVersion;
        private final String screenCategory;
        private final List<String> visibleLabels;
        private final long observedAt;
        private final String question;

        private Request(
                long requestId,
                long contextVersion,
                String screenCategory,
                List<String> visibleLabels,
                long observedAt,
                String question
        ) {
            this.requestId = requestId;
            this.contextVersion = contextVersion;
            this.screenCategory = screenCategory;
            this.visibleLabels = visibleLabels;
            this.observedAt = observedAt;
            this.question = question;
        }

        public String screenCategory() {
            return screenCategory;
        }

        public String question() {
            return question;
        }

        public List<String> visibleLabels() {
            return visibleLabels;
        }

        public long observedAt() {
            return observedAt;
        }
    }
}
