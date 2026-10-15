package com.vinuthna.paypalsaathi.accessibility;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OverlayForegroundPolicyTest {
    private static final String PAYPAL = "com.paypal.android.p2pmobile";
    private static final String SAATHI = "com.vinuthna.paypalsaathi";

    @Test
    public void showsOnlyWhenPayPalIsTheTopApplication() {
        assertEquals(
                OverlayForegroundPolicy.Decision.SHOW,
                OverlayForegroundPolicy.decide(PAYPAL, PAYPAL, PAYPAL, SAATHI));
        assertEquals(
                OverlayForegroundPolicy.Decision.HIDE,
                OverlayForegroundPolicy.decide("com.android.launcher", "com.android.launcher", PAYPAL, SAATHI));
    }

    @Test
    public void ignoresCompanionOverlayAndUnknownWindowState() {
        assertEquals(
                OverlayForegroundPolicy.Decision.IGNORE,
                OverlayForegroundPolicy.decide(SAATHI, PAYPAL, PAYPAL, SAATHI));
        assertEquals(
                OverlayForegroundPolicy.Decision.IGNORE,
                OverlayForegroundPolicy.decide("com.android.inputmethod", null, PAYPAL, SAATHI));
    }

    @Test
    public void showsOnExactPayPalEventWhenForegroundWindowIsUnavailable() {
        assertEquals(
                OverlayForegroundPolicy.Decision.SHOW,
                OverlayForegroundPolicy.decide(PAYPAL, null, PAYPAL, SAATHI));
        assertEquals(
                OverlayForegroundPolicy.Decision.IGNORE,
                OverlayForegroundPolicy.decide(
                        "com.paypal.android.p2pmobile.untrusted",
                        null,
                        PAYPAL,
                        SAATHI));
    }
}
