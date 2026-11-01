package com.vinuthna.paypalsaathi.detection;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisualFallbackPolicyTest {
    @Test
    public void requiresConsentSupportedPlatformAndForegroundPayPal() {
        VisualFallbackPolicy policy = new VisualFallbackPolicy();
        ScreenState unknown = ScreenObservationBuilder.build(
                Collections.emptyList(),
                "com.paypal.android.p2pmobile",
                100L);

        assertTrue(policy.shouldCapture(true, 30, true, unknown));
        assertFalse(policy.shouldCapture(false, 30, true, unknown));
        assertFalse(policy.shouldCapture(true, 29, true, unknown));
        assertFalse(policy.shouldCapture(true, 30, false, unknown));
    }

    @Test
    public void doesNotCaptureAlreadyClassifiedScreen() {
        VisualFallbackPolicy policy = new VisualFallbackPolicy();
        ScreenState home = ScreenObservationBuilder.build(
                Collections.emptyList(),
                "com.paypal.android.p2pmobile",
                100L,
                "PayPal home");

        assertFalse(policy.shouldCapture(true, 35, true, home));
    }

    @Test
    public void doesNotCaptureAmbiguousRecognizedScreen() {
        VisualFallbackPolicy policy = new VisualFallbackPolicy();
        ScreenState ambiguousHome = new ScreenState(
                "home:0000000000000000",
                ScreenCategories.HOME,
                Collections.emptyList(),
                Collections.emptyList(),
                60,
                "moderate",
                true,
                Collections.emptyList(),
                100L,
                "0000000000000000",
                true);

        assertFalse(policy.shouldCapture(true, 35, true, ambiguousHome));
    }
}
