package com.vinuthna.paypalsaathi;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

import java.util.Collections;
import java.util.List;

public final class QuestionRequestTracker {
    private String screenCategory = ScreenCategories.UNKNOWN;
    private List<String> visibleLabels = Collections.emptyList();
    private List<String> actionableElements = Collections.emptyList();
    private int confidence;
    private String evidenceStrength = "insufficient";
    private boolean ambiguous = true;
    private List<String> evidenceCodes = Collections.emptyList();
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
        return updateScreenContext(
                category,
                labels,
                Collections.emptyList(),
                0,
                "insufficient",
                true,
                Collections.emptyList(),
                observedAtMillis);
    }

    public boolean updateScreenContext(
            String category,
            List<String> labels,
            List<String> actions,
            long observedAtMillis
    ) {
        return updateScreenContext(
                category,
                labels,
                actions,
                0,
                "insufficient",
                true,
                Collections.emptyList(),
                observedAtMillis);
    }

    public boolean updateScreenContext(
            String category,
            List<String> labels,
            List<String> actions,
            int confidence,
            String evidenceStrength,
            boolean ambiguous,
            List<String> evidenceCodes,
            long observedAtMillis
    ) {
        String safeCategory = ScreenCategories.isKnownCategory(category)
                ? category
                : ScreenCategories.UNKNOWN;
        List<String> safeLabels = ScreenContext.sanitizeNavigationLabels(labels);
        List<String> safeActions =
                ScreenContext.sanitizeActionDescriptors(actions);
        int safeConfidence = Math.max(0, Math.min(confidence, 100));
        String safeStrength = "strong".equals(evidenceStrength)
                || "moderate".equals(evidenceStrength)
                ? evidenceStrength
                : "insufficient";
        List<String> safeEvidence =
                ScreenContext.sanitizeEvidenceCodes(evidenceCodes);
        boolean safeAmbiguous = ambiguous
                || ScreenCategories.UNKNOWN.equals(safeCategory);

        boolean changed = !screenCategory.equals(safeCategory)
                || !visibleLabels.equals(safeLabels)
                || !actionableElements.equals(safeActions)
                || this.confidence != safeConfidence
                || !this.evidenceStrength.equals(safeStrength)
                || this.ambiguous != safeAmbiguous
                || !this.evidenceCodes.equals(safeEvidence);
        screenCategory = safeCategory;
        visibleLabels = safeLabels;
        actionableElements = safeActions;
        this.confidence = safeConfidence;
        this.evidenceStrength = safeStrength;
        this.ambiguous = safeAmbiguous;
        this.evidenceCodes = safeEvidence;
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
        actionableElements = Collections.emptyList();
        confidence = 0;
        evidenceStrength = "insufficient";
        ambiguous = true;
        evidenceCodes = Collections.emptyList();
        observedAt = 0L;
    }

    public void invalidateRequests() {
        contextVersion++;
        activeRequestId = -1L;
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
                actionableElements,
                confidence,
                evidenceStrength,
                ambiguous,
                evidenceCodes,
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
        private final List<String> actionableElements;
        private final int confidence;
        private final String evidenceStrength;
        private final boolean ambiguous;
        private final List<String> evidenceCodes;
        private final long observedAt;
        private final String question;

        private Request(
                long requestId,
                long contextVersion,
                String screenCategory,
                List<String> visibleLabels,
                List<String> actionableElements,
                int confidence,
                String evidenceStrength,
                boolean ambiguous,
                List<String> evidenceCodes,
                long observedAt,
                String question
        ) {
            this.requestId = requestId;
            this.contextVersion = contextVersion;
            this.screenCategory = screenCategory;
            this.visibleLabels = visibleLabels;
            this.actionableElements = actionableElements;
            this.confidence = confidence;
            this.evidenceStrength = evidenceStrength;
            this.ambiguous = ambiguous;
            this.evidenceCodes = evidenceCodes;
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

        public List<String> actionableElements() {
            return actionableElements;
        }

        public int confidence() {
            return confidence;
        }

        public String evidenceStrength() {
            return evidenceStrength;
        }

        public boolean ambiguous() {
            return ambiguous;
        }

        public List<String> evidenceCodes() {
            return evidenceCodes;
        }

        public long observedAt() {
            return observedAt;
        }
    }
}
