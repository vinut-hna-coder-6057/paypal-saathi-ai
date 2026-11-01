package com.vinuthna.paypalsaathi.detection;

import com.vinuthna.paypalsaathi.ScreenContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ScreenObservationBuilder {
    private static final int MAX_SAFE_ACTIONS = 8;
    private static final int MAX_CLASSIFICATION_TEXT = 2_000;

    private ScreenObservationBuilder() {}

    public static ScreenState build(
            List<ObservedUiElement> observations,
            String sourcePackage,
            long observedAt
    ) {
        return build(observations, sourcePackage, observedAt, null);
    }

    public static ScreenState build(
            List<ObservedUiElement> observations,
            String sourcePackage,
            long observedAt,
            String visualScreenHints
    ) {
        List<ObservedUiElement> safeObservations = observations == null
                ? Collections.emptyList()
                : observations;
        StringBuilder safeText = new StringBuilder();
        List<String> preliminaryActions = new ArrayList<>();
        for (ObservedUiElement observation : safeObservations) {
            if (observation == null || observation.sensitive()) {
                continue;
            }
            appendSafe(safeText, observation.editable() ? observation.hint()
                    : observation.text());
            appendSafe(safeText, observation.contentDescription());
            if (observation.clickable()
                    && observation.enabled()
                    && observation.supportedActions().contains("tap")) {
                String label = ScreenDataSanitizer.sanitizeLabel(
                        observation.editable() ? observation.hint()
                                : observation.text());
                if (label == null) {
                    label = ScreenDataSanitizer.sanitizeLabel(
                            observation.contentDescription());
                }
                String descriptor = ScreenContext.safeActionDescriptor(
                        label,
                        true,
                        observation.left(),
                        observation.top(),
                        observation.right(),
                        observation.bottom(),
                        observation.screenWidth(),
                        observation.screenHeight());
                if (descriptor != null && !preliminaryActions.contains(descriptor)) {
                    preliminaryActions.add(descriptor);
                }
            }
        }
        String safeVisualHints =
                ScreenDataSanitizer.extractVisualScreenHints(visualScreenHints);
        appendSafe(safeText, safeVisualHints);

        ScreenClassification classification = ScreenClassifier.classifyScreen(
                safeText.toString(),
                preliminaryActions,
                sourcePackage);
        List<String> evidenceCodes = new ArrayList<>(
                classification.evidenceCodes());
        if (!safeVisualHints.isEmpty()) {
            evidenceCodes.add("visual_fallback");
        }
        List<ScreenElement> elements = ScreenElementExtractor.extract(
                safeObservations,
                classification.category(),
                classification.confidence());
        List<String> safeActions = new ArrayList<>();
        for (ScreenElement element : elements) {
            if (element.safeToRecommend()
                    && element.actionDescriptor() != null
                    && safeActions.size() < MAX_SAFE_ACTIONS
                    && !safeActions.contains(element.actionDescriptor())) {
                safeActions.add(element.actionDescriptor());
            }
        }

        String fingerprint = fingerprint(classification.category(), elements);
        String identifier = classification.category() + ":" + fingerprint;
        return new ScreenState(
                identifier,
                classification.category(),
                elements,
                safeActions,
                classification.confidence(),
                classification.evidenceStrength(),
                classification.ambiguous(),
                evidenceCodes,
                observedAt,
                fingerprint,
                true);
    }

    public static ScreenState unavailable(long observedAt) {
        String fingerprint = fingerprint(ScreenCategories.UNKNOWN,
                Collections.emptyList());
        return new ScreenState(
                ScreenCategories.UNKNOWN + ":" + fingerprint,
                ScreenCategories.UNKNOWN,
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                "insufficient",
                true,
                Collections.singletonList("observation_unavailable"),
                observedAt,
                fingerprint,
                false);
    }

    private static void appendSafe(StringBuilder output, String value) {
        String safe = ScreenDataSanitizer.sanitizeLabel(value);
        if (safe != null && output.length() < MAX_CLASSIFICATION_TEXT) {
            int remaining = MAX_CLASSIFICATION_TEXT - output.length();
            output.append(' ').append(safe, 0,
                    Math.min(safe.length(), Math.max(0, remaining - 1)));
        }
    }

    private static String fingerprint(
            String category,
            List<ScreenElement> elements
    ) {
        List<String> stableElements = new ArrayList<>();
        for (ScreenElement element : elements) {
            String safeNavigationLabel =
                    ScreenContext.safeNavigationLabel(element.label());
            stableElements.add(element.role()
                    + "|" + (safeNavigationLabel == null ? "" : safeNavigationLabel)
                    + "|" + element.clickable()
                    + "|" + element.enabled()
                    + "|" + element.editable()
                    + "|" + String.join(",", element.supportedActions())
                    + "|" + String.valueOf(element.actionDescriptor()));
        }
        stableElements.sort(Comparator.naturalOrder());
        String source = category + "\n" + String.join("\n", stableElements);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format(
                        java.util.Locale.ROOT,
                        "%02x",
                        digest[i] & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
