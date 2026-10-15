package com.vinuthna.paypalsaathi.accessibility;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;
import com.vinuthna.paypalsaathi.detection.ScreenClassifier;
import com.vinuthna.paypalsaathi.detection.ScreenClassification;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RootUnavailableCategoryPolicyTest {
    private static final long GRACE_PERIOD_MS = 1_000L;

    @Test
    public void preservesRecentCategoryBrieflyWhenRootIsNullThenExpires() {
        RootUnavailableCategoryPolicy policy =
                new RootUnavailableCategoryPolicy(GRACE_PERIOD_MS);

        assertTrue(policy.shouldPreserve(
                ScreenCategories.HOME,
                false,
                false,
                100L));
        assertTrue(policy.shouldPreserve(
                ScreenCategories.HOME,
                false,
                false,
                999L));
        assertFalse(policy.shouldPreserve(
                ScreenCategories.HOME,
                false,
                false,
                1_100L));
    }

    @Test
    public void preservesSecurityBrieflyWhenRootIsNullThenExpires() {
        RootUnavailableCategoryPolicy policy =
                new RootUnavailableCategoryPolicy(GRACE_PERIOD_MS);

        assertTrue(policy.shouldPreserve(
                ScreenCategories.SECURITY,
                false,
                false,
                100L));
        assertTrue(policy.shouldPreserve(
                ScreenCategories.SECURITY,
                false,
                false,
                999L));
        assertFalse(policy.shouldPreserve(
                ScreenCategories.SECURITY,
                false,
                false,
                1_100L));
    }

    @Test
    public void validPayPalRootWithInsufficientEvidenceIsBoundedlyPreserved() {
        ScreenClassification observed = ScreenClassifier.classifyScreen(
                "",
                Collections.emptyList(),
                "com.paypal.android.p2pmobile");
        RootUnavailableCategoryPolicy policy =
                new RootUnavailableCategoryPolicy(GRACE_PERIOD_MS);

        assertEquals(ScreenCategories.UNKNOWN, observed.category());
        assertTrue(policy.shouldPreserveUncertainCategory(
                ScreenCategories.HOME,
                100L));
        assertTrue(policy.shouldPreserveUncertainCategory(
                ScreenCategories.HOME,
                999L));
        assertFalse(policy.shouldPreserveUncertainCategory(
                ScreenCategories.HOME,
                1_100L));
        assertFalse(policy.shouldPreserveUncertainCategory(
                ScreenCategories.UNKNOWN,
                1_101L));
    }

    @Test
    public void repeatedWeakObservationsDoNotExtendPreservationWindow() {
        RootUnavailableCategoryPolicy policy =
                new RootUnavailableCategoryPolicy(GRACE_PERIOD_MS);

        assertTrue(policy.shouldPreserveUncertainCategory(
                ScreenCategories.SECURITY,
                100L));
        assertTrue(policy.shouldPreserveUncertainCategory(
                ScreenCategories.SECURITY,
                900L));
        assertFalse(policy.shouldPreserveUncertainCategory(
                ScreenCategories.SECURITY,
                1_101L));
    }

    @Test
    public void preservesRecentCategoryBrieflyWhenRootPackageMismatches() {
        RootUnavailableCategoryPolicy policy =
                new RootUnavailableCategoryPolicy(GRACE_PERIOD_MS);

        assertTrue(policy.shouldPreserve(
                ScreenCategories.SECURITY,
                true,
                false,
                100L));
        assertTrue(policy.shouldPreserve(
                ScreenCategories.SECURITY,
                true,
                false,
                500L));
        assertFalse(policy.shouldPreserve(
                ScreenCategories.SECURITY,
                true,
                false,
                1_100L));
    }

    @Test
    public void validRootEndsPreservationAndUnknownIsNeverPreserved() {
        RootUnavailableCategoryPolicy policy =
                new RootUnavailableCategoryPolicy(GRACE_PERIOD_MS);

        assertTrue(policy.shouldPreserve(
                ScreenCategories.SECURITY,
                false,
                false,
                100L));
        assertFalse(policy.shouldPreserve(
                ScreenCategories.SECURITY,
                true,
                true,
                200L));
        assertFalse(policy.shouldPreserve(
                ScreenCategories.UNKNOWN,
                false,
                false,
                300L));
    }
}
