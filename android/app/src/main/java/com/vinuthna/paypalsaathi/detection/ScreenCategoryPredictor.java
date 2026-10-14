package com.vinuthna.paypalsaathi.detection;

/**
 * Placeholder interface for a future AI/ML screen classifier.
 * Do not implement a model behind this interface in this repository phase.
 *
 * The rule-based {@link ScreenClassifier} remains the active implementation.
 * When integrating a model:
 * 1. Implement this interface.
 * 2. Return a canonical {@link ScreenCategories} ID.
 * 3. If confidence is low or the label is invalid, return {@link ScreenCategories#UNKNOWN}.
 * 4. Never return raw screen text from the predictor to the network.
 */
public interface ScreenCategoryPredictor {
    final class Prediction {
        public final String category;
        public final float confidence;
        public final boolean uncertain;

        public Prediction(String category, float confidence, boolean uncertain) {
            this.category = category;
            this.confidence = confidence;
            this.uncertain = uncertain;
        }
    }

    /**
     * @param transientScreenText short, non-sensitive window text used only in memory
     * @return prediction with a canonical category ID
     */
    Prediction predict(String transientScreenText);
}
