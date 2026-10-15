package com.vinuthna.paypalsaathi;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class QuestionRequestTrackerTest {
    @Test
    public void snapshotsCurrentCategoryForQuestionRequest() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();
        tracker.updateScreenCategory(ScreenCategories.ACTIVITY);

        QuestionRequestTracker.Request request =
                tracker.begin(" Where can I find a previous transaction? ");

        assertNotNull(request);
        assertEquals(ScreenCategories.ACTIVITY, request.screenCategory());
        assertEquals("Where can I find a previous transaction?", request.question());
    }

    @Test
    public void unknownAndUnsupportedContextsStayUnknown() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();

        assertEquals(ScreenCategories.UNKNOWN, tracker.screenCategory());
        tracker.updateScreenCategory("untrusted_category");
        assertEquals(ScreenCategories.UNKNOWN, tracker.screenCategory());
    }

    @Test
    public void categoryChangeInvalidatesOlderResponse() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();
        tracker.updateScreenCategory(ScreenCategories.HOME);
        QuestionRequestTracker.Request request = tracker.begin("How do I continue?");

        tracker.updateScreenCategory(ScreenCategories.WALLET);

        assertFalse(tracker.isCurrent(request));
    }

    @Test
    public void staleResponseCannotFinishNewerRequestAfterCategoryChange() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();
        tracker.updateScreenCategory(ScreenCategories.HOME);
        QuestionRequestTracker.Request stale =
                tracker.begin("How do I send money?");

        tracker.updateScreenCategory(ScreenCategories.SECURITY);
        QuestionRequestTracker.Request current =
                tracker.begin("What should I do on this screen?");

        tracker.finish(stale);

        assertFalse(tracker.isCurrent(stale));
        assertTrue(tracker.isCurrent(current));
    }

    @Test
    public void uncertainContextInvalidatesRequestsWithoutChangingLastCategory() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();
        tracker.updateScreenCategory(ScreenCategories.HOME);
        QuestionRequestTracker.Request request =
                tracker.begin("How do I send money?");

        tracker.invalidateRequests();

        assertFalse(tracker.isCurrent(request));
        assertEquals(ScreenCategories.HOME, tracker.screenCategory());
    }

    @Test
    public void identicalCategorySnapshotDoesNotInvalidateActiveRequest() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();
        tracker.updateScreenContext(
                ScreenCategories.HOME,
                Arrays.asList("Home"),
                Arrays.asList("Send|bottom|left"),
                82,
                "moderate",
                false,
                Arrays.asList("home_action_combination"),
                1_000L);
        QuestionRequestTracker.Request request =
                tracker.begin("How do I send money?");

        boolean changed = tracker.updateScreenContext(
                ScreenCategories.HOME,
                Arrays.asList("Home"),
                Arrays.asList("Send|bottom|left"),
                82,
                "moderate",
                false,
                Arrays.asList("home_action_combination"),
                1_100L);

        assertFalse(changed);
        assertTrue(tracker.isCurrent(request));
    }

    @Test
    public void snapshotsOnlyAllowListedRecentNavigationLabels() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();
        long observedAt = System.currentTimeMillis();
        tracker.updateScreenContext(
                ScreenCategories.ACTIVITY,
                Arrays.asList("Activity", "person@example.test", "Wallet"),
                observedAt);

        QuestionRequestTracker.Request request =
                tracker.begin("Where are my old payments?");

        assertNotNull(request);
        assertEquals(Arrays.asList("Activity", "Wallet"), request.visibleLabels());
        assertEquals(observedAt, request.observedAt());
    }

    @Test
    public void navigationContextChangesInvalidateOlderAnswers() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();
        tracker.updateScreenContext(
                ScreenCategories.HOME,
                Arrays.asList("Home"),
                System.currentTimeMillis());
        QuestionRequestTracker.Request request = tracker.begin("What is here?");

        tracker.updateScreenContext(
                ScreenCategories.HOME,
                Arrays.asList("Activity"),
                System.currentTimeMillis());

        assertFalse(tracker.isCurrent(request));
    }

    @Test
    public void actionableControlChangesInvalidateOlderAnswers() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();
        tracker.updateScreenContext(
                ScreenCategories.HOME,
                Arrays.asList("Home"),
                Arrays.asList("Send|bottom|left"),
                System.currentTimeMillis());
        QuestionRequestTracker.Request request =
                tracker.begin("How do I send money?");

        tracker.updateScreenContext(
                ScreenCategories.HOME,
                Arrays.asList("Home"),
                Arrays.asList("Request|bottom|right"),
                System.currentTimeMillis());

        assertFalse(tracker.isCurrent(request));
    }

    @Test
    public void preventsEmptyLongAndDuplicateSubmissions() {
        QuestionRequestTracker tracker = new QuestionRequestTracker();

        assertNull(tracker.begin(" "));
        assertNull(tracker.begin("x".repeat(501)));

        QuestionRequestTracker.Request request = tracker.begin("A general question");
        assertNotNull(request);
        assertNull(tracker.begin("A second question"));

        tracker.finish(request);
        assertNotNull(tracker.begin("A second question"));
    }

    @Test
    public void detectsLikelyCredentialValuesWithoutBlockingGeneralSecurityQuestions() {
        assertTrue(QuestionInputValidator.containsLikelySensitiveValue(
                "My OTP is 123456"));
        assertTrue(QuestionInputValidator.containsLikelySensitiveValue(
                "Card number: 4111 1111 1111 1111"));
        assertTrue(QuestionInputValidator.containsLikelySensitiveValue(
                "My account email is user@example.test"));
        assertTrue(QuestionInputValidator.containsLikelySensitiveValue(
                "My balance is INR 4500"));
        assertTrue(QuestionInputValidator.containsLikelySensitiveValue(
                "My phone is +91 98765 43210"));
        assertFalse(QuestionInputValidator.containsLikelySensitiveValue(
                "How do I protect my account and handle an OTP prompt?"));
    }
}
