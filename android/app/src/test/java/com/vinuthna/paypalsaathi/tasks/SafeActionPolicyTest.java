package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.detection.ObservedUiElement;
import com.vinuthna.paypalsaathi.detection.ScreenElement;
import com.vinuthna.paypalsaathi.detection.ScreenObservationBuilder;
import com.vinuthna.paypalsaathi.detection.ScreenState;
import com.vinuthna.paypalsaathi.detection.ScreenTransitionDetector;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SafeActionPolicyTest {
    private static final String PAYPAL_PACKAGE =
            "com.paypal.android.p2pmobile";
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void allowsOnlyObservedFreshTaskBoundNavigationAfterUserConfirmation() {
        TaskOrchestrator orchestrator = activityTask();
        TaskState task = orchestrator.state();
        TaskStep step = orchestrator.currentStep();
        ScreenState screen = home(NOW);

        SafeActionPolicy.Decision decision = new SafeActionPolicy().evaluate(
                PAYPAL_PACKAGE, task, step, screen, NOW, null);

        assertTrue(decision.allowed());
        assertEquals(SafeActionPolicy.Rejection.NONE, decision.rejection());
    }

    @Test
    public void rejectsWrongAppStaleDuplicateAndFinancialActions() {
        TaskOrchestrator orchestrator = activityTask();
        SafeActionPolicy policy = new SafeActionPolicy();
        TaskState task = orchestrator.state();
        TaskStep step = orchestrator.currentStep();
        ScreenState screen = home(NOW);
        SafeActionPolicy.Decision allowed = policy.evaluate(
                PAYPAL_PACKAGE, task, step, screen, NOW, null);

        assertEquals(SafeActionPolicy.Rejection.WRONG_APPLICATION,
                policy.evaluate("com.example.other", task, step, screen,
                        NOW, null).rejection());
        assertEquals(SafeActionPolicy.Rejection.STALE_OBSERVATION,
                policy.evaluate(PAYPAL_PACKAGE, task, step,
                        home(NOW - 20_000L), NOW, null).rejection());
        assertEquals(SafeActionPolicy.Rejection.DUPLICATE_ACTION,
                policy.evaluate(PAYPAL_PACKAGE, task, step, screen, NOW,
                        allowed.executionKey()).rejection());

        TaskOrchestrator send = new TaskOrchestrator();
        send.start(new TaskGoal(TaskGoal.Type.SEND_MONEY, NOW, 90, false),
                null, NOW);
        ScreenState sendHome = screen("PayPal home", NOW,
                button("Send"), button("Activity"));
        send.observe(sendHome, transition(null, sendHome), NOW);
        TaskStep sendStep = send.currentStep();
        assertTrue(policy.evaluate(PAYPAL_PACKAGE, send.state(), sendStep,
                sendHome, NOW, null).allowed());

        ScreenState recipient = screen(
                "Who do you want to pay?", NOW + 100L,
                button("Continue"));
        send.observe(recipient, transition(sendHome, recipient), NOW + 100L);
        assertEquals(ScreenElement.RiskCategory.RECIPIENT_SELECTION,
                send.currentStep().riskCategory());
        assertEquals(SafeActionPolicy.Rejection.TASK_NOT_WAITING,
                policy.evaluate(PAYPAL_PACKAGE, send.state(), send.currentStep(),
                        recipient, NOW + 100L, null).rejection());

        ScreenState amount = screen(
                "Enter an amount", NOW + 200L, amountField());
        send.observe(amount, transition(recipient, amount), NOW + 200L);
        ScreenState review = screen(
                "Review your payment", NOW + 300L, button("Pay now"));
        send.observe(review, transition(amount, review), NOW + 300L);
        assertEquals(ScreenElement.RiskCategory.FINANCIAL_AUTHORIZATION,
                send.currentStep().riskCategory());
        assertEquals(SafeActionPolicy.Rejection.TASK_NOT_WAITING,
                policy.evaluate(PAYPAL_PACKAGE, send.state(), send.currentStep(),
                        review, NOW + 300L, null).rejection());
    }

    @Test
    public void rejectsActionsMissingFromTheTaskObservation() {
        TaskOrchestrator orchestrator = activityTask();
        ScreenState missingActivity = screen(
                "PayPal home", NOW,
                button("Send"), button("Request"));

        assertEquals(SafeActionPolicy.Rejection.UNCERTAIN_SCREEN,
                new SafeActionPolicy().evaluate(
                        PAYPAL_PACKAGE,
                        orchestrator.state(),
                        orchestrator.currentStep(),
                        missingActivity,
                        NOW,
                        null).rejection());
    }

    private static TaskOrchestrator activityTask() {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(new TaskGoal(TaskGoal.Type.VIEW_ACTIVITY,
                NOW, 90, false), null, NOW);
        ScreenState home = home(NOW);
        orchestrator.observe(home, transition(null, home), NOW);
        return orchestrator;
    }

    private static ScreenState home(long observedAt) {
        return screen("PayPal home", observedAt,
                button("Activity"), button("Send"));
    }

    private static ScreenTransitionDetector.Result transition(
            ScreenState before,
            ScreenState after
    ) {
        return ScreenTransitionDetector.detect(before, after, after.observedAt());
    }

    private static ScreenState screen(
            String text,
            long observedAt,
            ObservedUiElement... extra
    ) {
        List<ObservedUiElement> values = new ArrayList<>();
        values.add(new ObservedUiElement(
                text, null, null, "android.widget.TextView",
                false, true, false, false, false, "unknown",
                Collections.emptyList(),
                300, 500, 500, 600, 1_000, 2_000));
        values.addAll(Arrays.asList(extra));
        return ScreenObservationBuilder.build(values, PAYPAL_PACKAGE, observedAt);
    }

    private static ObservedUiElement button(String label) {
        return new ObservedUiElement(
                label, null, null, "android.widget.Button",
                true, true, false, false, false, "unknown",
                Collections.singletonList("tap"),
                300, 500, 500, 600, 1_000, 2_000);
    }

    private static ObservedUiElement amountField() {
        return new ObservedUiElement(
                null, null, null, "android.widget.EditText",
                false, true, false, true, false, "number",
                Collections.singletonList("focus"),
                300, 500, 500, 600, 1_000, 2_000);
    }
}
