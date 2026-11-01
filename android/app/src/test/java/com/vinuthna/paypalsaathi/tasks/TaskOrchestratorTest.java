package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.detection.ObservedUiElement;
import com.vinuthna.paypalsaathi.detection.ScreenCategories;
import com.vinuthna.paypalsaathi.detection.ScreenElement;
import com.vinuthna.paypalsaathi.detection.ScreenObservationBuilder;
import com.vinuthna.paypalsaathi.detection.ScreenState;
import com.vinuthna.paypalsaathi.detection.ScreenTransitionDetector;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaskOrchestratorTest {
    private static final String PAYPAL_PACKAGE =
            "com.paypal.android.p2pmobile";
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void sendMoneyFlowPlansAndVerifiesEachScreenWithoutAutomatingPayment() {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        assertTrue(orchestrator.start(
                goal(TaskGoal.Type.SEND_MONEY),
                null,
                NOW));

        ScreenState home = screen("PayPal home", NOW,
                button("Send", true), button("Request", true));
        observe(orchestrator, null, home, NOW);
        assertEquals(TaskState.Status.WAITING_FOR_USER,
                orchestrator.state().status());
        assertEquals("Send|top|center",
                orchestrator.currentStep().recommendedAction());
        assertEquals(ScreenCategories.RECIPIENT_SELECTION,
                orchestrator.state().expectedNextScreen());

        ScreenState recipient = screen(
                "Who do you want to pay?",
                NOW + 100,
                button("Continue", true));
        observe(orchestrator, home, recipient, NOW + 100);
        assertEquals(TaskState.Status.WAITING_FOR_USER,
                orchestrator.state().status());
        assertEquals(TaskStep.Status.WAITING_FOR_USER,
                orchestrator.currentStep().status());
        assertNull(orchestrator.currentStep().recommendedAction());
        assertEquals("choose_recipient", orchestrator.state().currentStepId());

        ScreenState amount = screen(
                "Enter an amount",
                NOW + 200,
                editableField());
        observe(orchestrator, recipient, amount, NOW + 200);
        assertEquals("enter_amount", orchestrator.state().currentStepId());
        assertNull(orchestrator.currentStep().recommendedAction());
        assertEquals(ScreenCategories.REVIEW_PAYMENT,
                orchestrator.state().expectedNextScreen());

        ScreenState review = screen(
                "Review your payment",
                NOW + 300,
                button("Pay now", true));
        observe(orchestrator, amount, review, NOW + 300);
        assertEquals("review_details", orchestrator.state().currentStepId());
        assertNull(orchestrator.currentStep().recommendedAction());
        assertEquals(ScreenElement.RiskCategory.FINANCIAL_AUTHORIZATION,
                orchestrator.currentStep().riskCategory());

        ScreenState confirmation = screen(
                "Payment successful",
                NOW + 400,
                button("Continue", true));
        observe(orchestrator, review, confirmation, NOW + 400);
        assertEquals(TaskState.Status.WAITING_FOR_USER,
                orchestrator.state().status());
        assertEquals("check_activity", orchestrator.state().currentStepId());
        assertEquals(ScreenCategories.ACTIVITY,
                orchestrator.state().expectedNextScreen());
        assertTrue(orchestrator.currentMessage().contains(
                "Do not try it again"));
        assertTrue(orchestrator.currentMessage().contains("Activity"));
        assertFalse(orchestrator.currentMessage().contains(
                "Your payment succeeded"));
        assertTrue(orchestrator.state().completedStepIds().contains(
                "review_details"));

        ScreenState activity = screen(
                "Transaction history",
                NOW + 500,
                button("Home", true));
        observe(orchestrator, confirmation, activity, NOW + 500);
        assertEquals(TaskState.Status.WAITING_FOR_USER,
                orchestrator.state().status());
        assertEquals("confirm_activity_review",
                orchestrator.state().currentStepId());
        assertNull(orchestrator.state().expectedNextScreen());
        assertTrue(orchestrator.currentMessage().contains(
                "After you have checked it"));

        orchestrator.confirmGuidanceComplete(NOW + 600);
        assertEquals(TaskState.Status.COMPLETED, orchestrator.state().status());
        assertTrue(orchestrator.currentMessage().contains(
                "did not verify any transaction details"));
    }

    @Test
    public void activityGoalCompletesOnObservedActivityScreen() {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(goal(TaskGoal.Type.VIEW_ACTIVITY), null, NOW);
        ScreenState home = screen("PayPal home", NOW,
                button("Send", true), button("Activity", true));
        observe(orchestrator, null, home, NOW);
        assertEquals("Activity|top|center",
                orchestrator.currentStep().recommendedAction());

        ScreenState activity = screen(
                "Transaction history",
                NOW + 100,
                button("Home", true));
        observe(orchestrator, home, activity, NOW + 100);
        assertEquals(TaskState.Status.COMPLETED, orchestrator.state().status());
        assertTrue(orchestrator.currentMessage().contains("cannot inspect"));
    }

    @Test
    public void accountSettingsWorkflowUsesObservedHomeControlAndStopsAtSettings() {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(goal(TaskGoal.Type.ACCOUNT_SETTINGS), null, NOW);
        ScreenState home = screen("PayPal home", NOW,
                button("Settings", true), button("Activity", true));
        observe(orchestrator, null, home, NOW);
        assertEquals("Settings|top|center",
                orchestrator.currentStep().recommendedAction());

        ScreenState settings = screen(
                "Account settings", NOW + 100,
                button("Back", true));
        observe(orchestrator, home, settings, NOW + 100);
        assertEquals(TaskState.Status.COMPLETED, orchestrator.state().status());
        assertTrue(orchestrator.currentMessage().contains(
                "directly in PayPal"));
    }

    @Test
    public void interruptedOrUnverifiedReviewNeverRestartsSendMoney() {
        TaskOrchestrator interrupted = sendTaskAtReview();
        interrupted.pause(NOW + 400);
        interrupted.resume(NOW + 500);
        assertEquals(TaskState.Status.NEEDS_CLARIFICATION,
                interrupted.state().status());
        assertEquals("uncertain_payment",
                interrupted.state().pendingClarification());
        assertNull(interrupted.currentStep());
        assertTrue(interrupted.currentMessage().contains(
                "Do not try the payment again"));
        assertTrue(interrupted.currentMessage().contains("check Activity"));

        TaskOrchestrator unverified = sendTaskAtReview();
        ScreenState confirmation = screen(
                "Payment successful", NOW + 400, button("Continue", true));
        ScreenState unavailable =
                ScreenObservationBuilder.unavailable(NOW + 350);
        unverified.observe(
                confirmation,
                transition(unavailable, confirmation),
                NOW + 400);
        assertEquals(TaskState.Status.NEEDS_CLARIFICATION,
                unverified.state().status());
        assertFalse(unverified.state().completedStepIds().contains(
                "review_details"));
        assertTrue(unverified.currentMessage().contains(
                "Do not try the payment again"));
    }

    @Test
    public void restoredReviewTaskRequiresActivityClarificationBeforeResume() {
        TaskOrchestrator interrupted = sendTaskAtReview();
        TaskOrchestrator restored = new TaskOrchestrator();
        restored.restore(interrupted.state(), NOW + 400);

        assertEquals(TaskState.Status.PAUSED, restored.state().status());
        assertNull(restored.currentStep());
        assertTrue(restored.currentMessage().contains(
                "Do not try the payment again"));
        restored.resume(NOW + 500);
        assertEquals(TaskState.Status.NEEDS_CLARIFICATION,
                restored.state().status());
        assertTrue(restored.currentMessage().contains("check Activity"));
    }

    @Test
    public void restoredPostReviewTaskStillRequiresActivityCheck() {
        TaskOrchestrator afterStatus = sendTaskAtReview();
        ScreenState confirmation = screen(
                "Payment successful", NOW + 400, button("Continue", true));
        ScreenState review = screen(
                "Review your payment", NOW + 300, button("Pay now", true));
        observe(afterStatus, review, confirmation, NOW + 400);
        assertTrue(afterStatus.state().completedStepIds().contains(
                "review_details"));

        TaskOrchestrator restored = new TaskOrchestrator();
        restored.restore(afterStatus.state(), NOW + 500);
        restored.resume(NOW + 600);

        assertEquals(TaskState.Status.NEEDS_CLARIFICATION,
                restored.state().status());
        assertEquals("uncertain_payment",
                restored.state().pendingClarification());
        assertTrue(restored.currentMessage().contains("check Activity"));
        assertFalse(restored.currentMessage().contains("send money again"));
    }

    @Test
    public void unknownScreensAndUnexpectedTransitionsNeverCompleteSteps() {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(goal(TaskGoal.Type.SEND_MONEY), null, NOW);
        ScreenState home = screen("PayPal home", NOW,
                button("Send", true), button("Request", true));
        observe(orchestrator, null, home, NOW);

        ScreenState wallet = screen("Banks and cards", NOW + 100);
        observe(orchestrator, home, wallet, NOW + 100);
        assertEquals(TaskState.Status.NEEDS_CLARIFICATION,
                orchestrator.state().status());
        assertTrue(orchestrator.state().completedStepIds().isEmpty());
        assertEquals("unexpected_screen",
                orchestrator.state().pendingClarification());

        ScreenState unknown = screen("Unexpected page", NOW + 200);
        assertEquals(ScreenCategories.UNKNOWN, unknown.category());
        assertEquals(TaskState.Status.NEEDS_CLARIFICATION,
                orchestrator.observe(
                        unknown,
                        transition(wallet, unknown),
                        NOW + 200).status());
    }

    @Test
    public void duplicateEventsPauseResumeCancelAndClarificationAreDeterministic() {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        TaskGoal ambiguous = new TaskGoal(
                TaskGoal.Type.UNKNOWN, NOW, 0, true);
        orchestrator.start(ambiguous, "Which payment task?", NOW);
        assertEquals(TaskState.Status.NEEDS_CLARIFICATION,
                orchestrator.state().status());
        TaskGoal resolved = goal(TaskGoal.Type.CHECK_TRANSACTION_STATUS);
        assertTrue(orchestrator.resolveClarification(resolved, NOW + 1));

        ScreenState home = screen("PayPal home", NOW + 2,
                button("Activity", true), button("Send", true));
        observe(orchestrator, null, home, NOW + 2);
        TaskState beforeDuplicate = orchestrator.state();
        assertEquals(beforeDuplicate,
                orchestrator.observe(
                        home,
                        transition(home, home),
                        NOW + 3));

        orchestrator.pause(NOW + 4);
        assertEquals(TaskState.Status.PAUSED, orchestrator.state().status());
        orchestrator.resume(NOW + 5);
        assertEquals(TaskState.Status.OBSERVING_SCREEN,
                orchestrator.state().status());
        assertNull(orchestrator.currentStep());
        orchestrator.cancel(NOW + 6);
        assertEquals(TaskState.Status.CANCELLED, orchestrator.state().status());
        assertTrue(orchestrator.start(
                goal(TaskGoal.Type.OPEN_WALLET),
                null,
                NOW + 7));
    }

    @Test
    public void taskDoesNotAdvanceOnUnverifiedExpectedScreenOrStaleObservation() {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(goal(TaskGoal.Type.SEND_MONEY), null, NOW);
        ScreenState home = screen("PayPal home", NOW,
                button("Send", true), button("Request", true));
        observe(orchestrator, null, home, NOW);

        ScreenState recipient = screen(
                "Who do you want to pay?",
                NOW + 100,
                button("Continue", true));
        ScreenState staleHome = screen(
                "PayPal home",
                NOW - 20_000,
                button("Send", true),
                button("Request", true));
        ScreenTransitionDetector.Result unverified =
                transition(staleHome, recipient);
        orchestrator.observe(recipient, unverified, NOW + 100);
        assertFalse(orchestrator.state().completedStepIds().contains("open_send"));
        assertEquals(TaskState.Status.WAITING_FOR_USER,
                orchestrator.state().status());
        assertEquals(ScreenCategories.RECIPIENT_SELECTION,
                orchestrator.state().lastObservedCategory());

        ScreenState stale = screen("PayPal home", NOW);
        TaskState result = orchestrator.observe(
                stale,
                transition(home, stale),
                NOW + 20_000);
        assertEquals(TaskState.Status.RECOVERING, result.status());
        assertFalse(result.completedStepIds().contains("open_send"));
    }

    private static TaskGoal goal(TaskGoal.Type type) {
        return new TaskGoal(type, NOW, 90, false);
    }

    private static ScreenTransitionDetector.Result transition(
            ScreenState previous,
            ScreenState current
    ) {
        return ScreenTransitionDetector.detect(
                previous,
                current,
                current.observedAt());
    }

    private static void observe(
            TaskOrchestrator orchestrator,
            ScreenState previous,
            ScreenState current,
            long now
    ) {
        orchestrator.observe(current, transition(previous, current), now);
    }

    private static TaskOrchestrator sendTaskAtReview() {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(goal(TaskGoal.Type.SEND_MONEY), null, NOW);
        ScreenState home = screen("PayPal home", NOW,
                button("Send", true), button("Activity", true));
        observe(orchestrator, null, home, NOW);
        ScreenState recipient = screen(
                "Who do you want to pay?", NOW + 100,
                button("Continue", true));
        observe(orchestrator, home, recipient, NOW + 100);
        ScreenState amount = screen(
                "Enter an amount", NOW + 200, editableField());
        observe(orchestrator, recipient, amount, NOW + 200);
        ScreenState review = screen(
                "Review your payment", NOW + 300,
                button("Pay now", true));
        observe(orchestrator, amount, review, NOW + 300);
        return orchestrator;
    }

    private static ScreenState screen(
            String text,
            long time,
            ObservedUiElement... extra
    ) {
        java.util.List<ObservedUiElement> values = new java.util.ArrayList<>();
        values.add(element(
                text,
                "android.widget.TextView",
                false,
                true,
                false,
                false,
                Collections.emptyList()));
        values.addAll(Arrays.asList(extra));
        return ScreenObservationBuilder.build(values, PAYPAL_PACKAGE, time);
    }

    private static ObservedUiElement button(String label, boolean enabled) {
        return element(label, "android.widget.Button", true, enabled,
                false, false, Collections.singletonList("tap"));
    }

    private static ObservedUiElement editableField() {
        return new ObservedUiElement(
                null, null, null, "android.widget.EditText",
                false, true, false, true, false, "number",
                Collections.singletonList("focus"),
                300, 500, 500, 600, 1_000, 2_000);
    }

    private static ObservedUiElement element(
            String text,
            String className,
            boolean clickable,
            boolean enabled,
            boolean editable,
            boolean sensitive,
            java.util.List<String> actions
    ) {
        return new ObservedUiElement(
                text, null, null, className,
                clickable, enabled, false, editable, sensitive,
                editable ? "number" : "unknown",
                actions,
                300, 500, 500, 600, 1_000, 2_000);
    }
}
