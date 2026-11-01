package com.vinuthna.paypalsaathi.detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ScreenTransitionDetectorTest {
    private static final String PAYPAL_PACKAGE =
            "com.paypal.android.p2pmobile";
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void distinguishesUnchangedPartialAndConfidentScreenChanges() {
        ScreenState home = state("PayPal home", NOW);
        ScreenState sameHome = state("PayPal home", NOW + 100);
        ScreenState updatedHome = stateWithDisabledSend(NOW + 200);
        ScreenState signIn = state("Sign in", NOW + 300);

        assertEquals(
                ScreenTransitionDetector.Type.SCREEN_UNCHANGED,
                detect(home, sameHome, sameHome.observedAt()).type());
        assertEquals(
                ScreenTransitionDetector.Type.SCREEN_PARTIALLY_UPDATED,
                detect(home, updatedHome, updatedHome.observedAt()).type());
        assertEquals(
                ScreenTransitionDetector.Type.SCREEN_CHANGED,
                detect(home, signIn, signIn.observedAt()).type());
        assertNotEquals(home.fingerprint(), updatedHome.fingerprint());
    }

    @Test
    public void lowConfidenceAndUnknownCategoriesDoNotProveTransitions() {
        ScreenState home = state("PayPal home", NOW);
        ScreenState unknown = state("Unexpected page", NOW + 100);
        ScreenState unavailable = ScreenObservationBuilder.unavailable(NOW + 200);

        assertEquals(
                ScreenTransitionDetector.Type.UNKNOWN_TRANSITION,
                detect(home, unknown, unknown.observedAt()).type());
        assertEquals(
                ScreenTransitionDetector.Type.OBSERVATION_UNAVAILABLE,
                detect(home, unavailable, unavailable.observedAt()).type());
    }

    @Test
    public void detectsLoadingAndRejectsStaleObservations() {
        ScreenState home = state("PayPal home", NOW);
        ScreenState loading = state("Please wait while loading", NOW + 100);
        ScreenState stale = state("Sign in", NOW);

        assertEquals(
                ScreenTransitionDetector.Type.LOADING_STARTED,
                detect(home, loading, loading.observedAt()).type());
        ScreenState loaded = state("PayPal home", NOW + 150);
        assertEquals(
                ScreenTransitionDetector.Type.LOADING_FINISHED,
                detect(loading, loaded, loaded.observedAt()).type());
        assertEquals(
                ScreenTransitionDetector.Type.STALE_OBSERVATION,
                detect(home, stale, NOW + 20_000).type());
    }

    @Test
    public void firstObservationDoesNotClaimThatANewScreenWasOpened() {
        ScreenState home = state("PayPal home", NOW);

        assertEquals(
                ScreenTransitionDetector.Type.UNKNOWN_TRANSITION,
                ScreenTransitionDetector.detect(null, home, NOW).type());
    }

    @Test
    public void unsupportedPackageNeverBecomesSpecificScreen() {
        ScreenState state = ScreenObservationBuilder.build(
                Arrays.asList(
                        new ObservedUiElement(
                                "PayPal home",
                                null,
                                null,
                                "android.widget.TextView",
                                false,
                                true,
                                false,
                                false,
                                false,
                                "unknown",
                                Collections.emptyList(),
                                0,
                                0,
                                10,
                                10,
                                1_000,
                                2_000)),
                "com.example.other",
                NOW);

        assertEquals(ScreenCategories.UNKNOWN, state.category());
        assertEquals(
                ScreenTransitionDetector.Type.UNKNOWN_TRANSITION,
                ScreenTransitionDetector.detect(null, state, NOW).type());
    }

    private static ScreenTransitionDetector.Result detect(
            ScreenState previous,
            ScreenState current,
            long now
    ) {
        return ScreenTransitionDetector.detect(previous, current, now);
    }

    private static ScreenState state(String text, long observedAt) {
        ObservedUiElement element = new ObservedUiElement(
                text,
                null,
                null,
                "android.widget.TextView",
                false,
                true,
                false,
                false,
                false,
                "unknown",
                Collections.emptyList(),
                0,
                0,
                10,
                10,
                1_000,
                2_000);
        return ScreenObservationBuilder.build(
                Collections.singletonList(element),
                PAYPAL_PACKAGE,
                observedAt);
    }

    private static ScreenState stateWithDisabledSend(long observedAt) {
        ObservedUiElement heading = new ObservedUiElement(
                "PayPal home",
                null,
                null,
                "android.widget.TextView",
                false,
                true,
                false,
                false,
                false,
                "unknown",
                Collections.emptyList(),
                0,
                0,
                10,
                10,
                1_000,
                2_000);
        ObservedUiElement disabledSend = new ObservedUiElement(
                "Send",
                null,
                null,
                "android.widget.Button",
                true,
                false,
                false,
                false,
                false,
                "unknown",
                Collections.singletonList("tap"),
                0,
                0,
                10,
                10,
                1_000,
                2_000);
        return ScreenObservationBuilder.build(
                Arrays.asList(heading, disabledSend),
                PAYPAL_PACKAGE,
                observedAt);
    }
}
