package com.vinuthna.paypalsaathi.detection;

import com.vinuthna.paypalsaathi.ScreenContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScreenState {
    private final String screenIdentifier;
    private final String category;
    private final List<ScreenElement> elements;
    private final List<String> safeActionDescriptors;
    private final int confidence;
    private final String evidenceStrength;
    private final boolean ambiguous;
    private final List<String> evidenceCodes;
    private final long observedAt;
    private final String fingerprint;
    private final boolean observationAvailable;

    ScreenState(
            String screenIdentifier,
            String category,
            List<ScreenElement> elements,
            List<String> safeActionDescriptors,
            int confidence,
            String evidenceStrength,
            boolean ambiguous,
            List<String> evidenceCodes,
            long observedAt,
            String fingerprint,
            boolean observationAvailable
    ) {
        this.category = ScreenCategories.isKnownCategory(category)
                ? category
                : ScreenCategories.UNKNOWN;
        this.screenIdentifier = safeIdentifier(
                screenIdentifier,
                this.category,
                fingerprint);
        this.elements = immutableCopy(elements);
        this.confidence = ScreenCategories.UNKNOWN.equals(this.category)
                ? 0
                : Math.max(0, Math.min(confidence, 100));
        this.evidenceStrength = ScreenCategories.UNKNOWN.equals(this.category)
                ? "insufficient"
                : safeEvidenceStrength(evidenceStrength);
        this.ambiguous = ambiguous
                || ScreenCategories.UNKNOWN.equals(this.category);
        this.evidenceCodes = ScreenContext.sanitizeEvidenceCodes(evidenceCodes);
        this.observedAt = Math.max(0L, observedAt);
        this.fingerprint = safeFingerprint(fingerprint);
        this.safeActionDescriptors =
                ScreenContext.sanitizeActionDescriptors(safeActionDescriptors);
        this.observationAvailable = observationAvailable;
    }

    private static String safeIdentifier(
            String value,
            String category,
            String fingerprint
    ) {
        if (value != null
                && value.length() <= 100
                && value.equals(category + ":" + safeFingerprint(fingerprint))) {
            return value;
        }
        return category + ":" + safeFingerprint(fingerprint);
    }

    private static String safeFingerprint(String value) {
        return value != null && value.matches("[a-f0-9]{16}")
                ? value
                : "0000000000000000";
    }

    private static String safeEvidenceStrength(String value) {
        if ("strong".equals(value) || "moderate".equals(value)) {
            return value;
        }
        return "insufficient";
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> copy = new ArrayList<>();
        for (T value : values) {
            if (value != null) {
                copy.add(value);
            }
        }
        return Collections.unmodifiableList(copy);
    }

    public String screenIdentifier() { return screenIdentifier; }
    public String category() { return category; }
    public List<ScreenElement> elements() { return elements; }
    public List<String> safeActionDescriptors() { return safeActionDescriptors; }
    public int confidence() { return confidence; }
    public String evidenceStrength() { return evidenceStrength; }
    public boolean ambiguous() { return ambiguous; }
    public List<String> evidenceCodes() { return evidenceCodes; }
    public long observedAt() { return observedAt; }
    public String fingerprint() { return fingerprint; }
    public boolean observationAvailable() { return observationAvailable; }
}
