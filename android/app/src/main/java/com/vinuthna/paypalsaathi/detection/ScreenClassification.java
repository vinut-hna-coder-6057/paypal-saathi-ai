package com.vinuthna.paypalsaathi.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScreenClassification {
    private final String category;
    private final int confidence;
    private final String evidenceStrength;
    private final boolean ambiguous;
    private final List<String> evidenceCodes;
    private final List<String> actionableElements;

    public ScreenClassification(
            String category,
            int confidence,
            String evidenceStrength,
            boolean ambiguous,
            List<String> evidenceCodes,
            List<String> actionableElements
    ) {
        this.category = ScreenCategories.isKnownCategory(category)
                ? category
                : ScreenCategories.UNKNOWN;
        boolean unknownCategory = ScreenCategories.UNKNOWN.equals(this.category);
        this.confidence = unknownCategory
                ? 0
                : Math.max(0, Math.min(confidence, 100));
        this.evidenceStrength = !unknownCategory
                && ("strong".equals(evidenceStrength)
                    || "moderate".equals(evidenceStrength))
                ? evidenceStrength
                : "insufficient";
        this.ambiguous = ambiguous || unknownCategory;
        this.evidenceCodes = immutableCopy(evidenceCodes);
        this.actionableElements = immutableCopy(actionableElements);
    }

    public static ScreenClassification unknown(
            boolean ambiguous,
            String evidenceCode
    ) {
        return new ScreenClassification(
                ScreenCategories.UNKNOWN,
                0,
                "insufficient",
                ambiguous,
                Collections.singletonList(evidenceCode),
                Collections.emptyList());
    }

    private static List<String> immutableCopy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public String category() {
        return category;
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

    public List<String> actionableElements() {
        return actionableElements;
    }
}
