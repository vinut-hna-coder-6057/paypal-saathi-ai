package com.vinuthna.paypalsaathi.detection;

public final class ScreenTransitionDetector {
    public enum Type {
        SCREEN_CHANGED,
        SCREEN_UNCHANGED,
        SCREEN_PARTIALLY_UPDATED,
        LOADING_STARTED,
        LOADING_FINISHED,
        UNKNOWN_TRANSITION,
        OBSERVATION_UNAVAILABLE,
        STALE_OBSERVATION
    }

    public static final class Result {
        private final Type type;
        private final String evidence;

        Result(Type type, String evidence) {
            this.type = type;
            this.evidence = evidence;
        }

        public Type type() { return type; }
        public String evidence() { return evidence; }
    }

    private static final long MAX_OBSERVATION_AGE_MS = 15_000L;
    private static final long FUTURE_CLOCK_SKEW_MS = 5_000L;
    private static final int MIN_TRANSITION_CONFIDENCE = 75;

    private ScreenTransitionDetector() {}

    public static Result detect(
            ScreenState previous,
            ScreenState current,
            long now
    ) {
        if (current == null || !current.observationAvailable()) {
            return new Result(Type.OBSERVATION_UNAVAILABLE, "no_current_observation");
        }
        if (current.observedAt() <= 0L
                || current.observedAt() > now + FUTURE_CLOCK_SKEW_MS
                || now - current.observedAt() > MAX_OBSERVATION_AGE_MS) {
            return new Result(Type.STALE_OBSERVATION, "observation_outside_freshness_window");
        }
        if (previous != null
                && previous.observationAvailable()
                && (previous.observedAt() <= 0L
                    || previous.observedAt() > now + FUTURE_CLOCK_SKEW_MS
                    || now - previous.observedAt() > MAX_OBSERVATION_AGE_MS)) {
            return new Result(Type.UNKNOWN_TRANSITION, "previous_observation_stale");
        }
        boolean previousLoading = previous != null
                && previous.observationAvailable()
                && ScreenCategories.LOADING.equals(previous.category());
        boolean currentLoading = ScreenCategories.LOADING.equals(current.category());
        if (currentLoading) {
            if (!previousLoading) {
                return new Result(Type.LOADING_STARTED, "loading_screen_signal");
            }
            return current.fingerprint().equals(previous.fingerprint())
                    ? new Result(Type.SCREEN_UNCHANGED, "matching_loading_fingerprint")
                    : new Result(
                            Type.SCREEN_PARTIALLY_UPDATED,
                            "loading_content_changed");
        }
        if (ScreenCategories.UNKNOWN.equals(current.category())) {
            return new Result(Type.UNKNOWN_TRANSITION, "screen_category_unknown");
        }
        if (previousLoading) {
            if (current.confidence() < MIN_TRANSITION_CONFIDENCE) {
                return new Result(
                        Type.UNKNOWN_TRANSITION,
                        "loading_finished_without_confident_screen");
            }
            return new Result(Type.LOADING_FINISHED, "confident_screen_after_loading");
        }
        if (previous == null || !previous.observationAvailable()) {
            return new Result(Type.UNKNOWN_TRANSITION, "no_comparable_previous_screen");
        }
        if (current.fingerprint().equals(previous.fingerprint())) {
            return new Result(Type.SCREEN_UNCHANGED, "matching_screen_fingerprint");
        }
        if (current.category().equals(previous.category())) {
            return new Result(
                    Type.SCREEN_PARTIALLY_UPDATED,
                    "same_category_changed_content");
        }
        if (current.confidence() < MIN_TRANSITION_CONFIDENCE
                || previous.confidence() < MIN_TRANSITION_CONFIDENCE
                || ScreenCategories.UNKNOWN.equals(previous.category())) {
            return new Result(
                    Type.UNKNOWN_TRANSITION,
                    "insufficient_transition_confidence");
        }
        return new Result(Type.SCREEN_CHANGED, "confident_category_change");
    }
}
