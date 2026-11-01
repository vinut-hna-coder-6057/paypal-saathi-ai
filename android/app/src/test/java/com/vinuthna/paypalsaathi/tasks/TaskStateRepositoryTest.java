package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.detection.ObservedUiElement;
import com.vinuthna.paypalsaathi.detection.ScreenObservationBuilder;
import com.vinuthna.paypalsaathi.detection.ScreenState;
import com.vinuthna.paypalsaathi.detection.ScreenTransitionDetector;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaskStateRepositoryTest {
    private static final String PAYPAL_PACKAGE =
            "com.paypal.android.p2pmobile";
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void activeTaskRestoresPausedAndRequiresFreshObservation() {
        MemoryStorage storage = new MemoryStorage();
        TaskStateRepository repository = new TaskStateRepository(storage);
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(new TaskGoal(
                TaskGoal.Type.SEND_MONEY, NOW, 90, false), null, NOW);
        ScreenState home = home(NOW);
        orchestrator.observe(home, null, NOW);
        repository.save(orchestrator.state());

        TaskState restored = repository.restore(NOW + 1_000);
        assertEquals(TaskState.Status.PAUSED, restored.status());
        assertNull(restored.currentStepId());
        assertNull(restored.expectedNextScreen());
        assertNull(restored.lastObservedScreenIdentifier());
        assertEquals(0L, restored.lastObservationTimestamp());
        assertEquals(TaskState.Status.PAUSED,
                TaskStateRepositoryTest.restoreIntoOrchestrator(restored, NOW + 1_000)
                        .status());
    }

    @Test
    public void persistenceContainsOnlyGenericGoalAndNoOriginalRequestValues() {
        MemoryStorage storage = new MemoryStorage();
        TaskStateRepository repository = new TaskStateRepository(storage);
        GoalUnderstandingEngine.Result result = new GoalUnderstandingEngine()
                .understand("Send money to Alice for INR 2500", NOW);
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(result.goal(), null, NOW);
        orchestrator.observe(home(NOW), null, NOW);
        repository.save(orchestrator.state());

        assertNotNull(storage.value);
        assertFalse(storage.value.contains("Alice"));
        assertFalse(storage.value.contains("2500"));
        assertFalse(storage.value.contains("INR"));
        assertTrue(storage.value.contains("SEND_MONEY"));
    }

    @Test
    public void uncertainPaymentClarificationSurvivesPersistenceAndCannotResumeSending() {
        MemoryStorage storage = new MemoryStorage();
        TaskStateRepository repository = new TaskStateRepository(storage);
        TaskOrchestrator interrupted = sendTaskAtReview();
        interrupted.pause(NOW + 400L);
        interrupted.resume(NOW + 500L);
        repository.save(interrupted.state());

        TaskState restored = repository.restore(NOW + 1_000L);
        assertEquals(TaskState.Status.PAUSED, restored.status());
        assertEquals("uncertain_payment", restored.pendingClarification());

        TaskOrchestrator resumed = new TaskOrchestrator();
        resumed.restore(restored, NOW + 1_000L);
        resumed.resume(NOW + 1_100L);
        assertEquals(TaskState.Status.NEEDS_CLARIFICATION,
                resumed.state().status());
        assertTrue(resumed.currentMessage().contains("check Activity"));
    }

    @Test
    public void reviewStepRestoresOnlyAsPausedAndRequiresActivityCheck() {
        MemoryStorage storage = new MemoryStorage();
        TaskStateRepository repository = new TaskStateRepository(storage);
        repository.save(sendTaskAtReview().state());

        TaskState restored = repository.restore(NOW + 1_000L);
        assertEquals(TaskState.Status.PAUSED, restored.status());
        assertEquals("review_details", restored.currentStepId());
        assertNull(restored.expectedNextScreen());
        assertNull(restored.lastObservedScreenIdentifier());

        TaskOrchestrator resumed = new TaskOrchestrator();
        resumed.restore(restored, NOW + 1_000L);
        resumed.resume(NOW + 1_100L);
        assertEquals(TaskState.Status.NEEDS_CLARIFICATION,
                resumed.state().status());
        assertTrue(resumed.currentMessage().contains(
                "Do not try the payment again"));
    }

    @Test
    public void expiredMalformedAndTerminalTasksAreRemoved() {
        MemoryStorage storage = new MemoryStorage();
        TaskStateRepository repository = new TaskStateRepository(storage);
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(new TaskGoal(
                TaskGoal.Type.VIEW_ACTIVITY, NOW, 90, false), null, NOW);
        repository.save(orchestrator.state());

        assertNull(repository.restore(NOW + 30L * 60L * 1000L + 1L));
        assertEquals(TaskStateRepository.RestoreStatus.EXPIRED,
                repository.lastRestoreStatus());
        assertNull(storage.value);

        storage.value = "malformed|private data";
        assertNull(repository.restore(NOW));
        assertEquals(TaskStateRepository.RestoreStatus.INVALID,
                repository.lastRestoreStatus());
        assertNull(storage.value);

        orchestrator.cancel(NOW + 1);
        repository.save(orchestrator.state());
        assertNull(storage.value);
    }

    private static TaskState restoreIntoOrchestrator(TaskState restored, long now) {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        return orchestrator.restore(restored, now);
    }

    private static ScreenState home(long observedAt) {
        ObservedUiElement heading = new ObservedUiElement(
                "PayPal home", null, null, "android.widget.TextView",
                false, true, false, false, false, "unknown",
                Collections.emptyList(), 0, 0, 20, 20, 1_000, 2_000);
        ObservedUiElement send = new ObservedUiElement(
                "Send", null, null, "android.widget.Button",
                true, true, false, false, false, "unknown",
                Collections.singletonList("tap"), 300, 500, 500, 600,
                1_000, 2_000);
        ObservedUiElement request = new ObservedUiElement(
                "Request", null, null, "android.widget.Button",
                true, true, false, false, false, "unknown",
                Collections.singletonList("tap"), 300, 700, 500, 800,
                1_000, 2_000);
        return ScreenObservationBuilder.build(
                java.util.Arrays.asList(heading, send, request),
                PAYPAL_PACKAGE,
                observedAt);
    }

    private static TaskOrchestrator sendTaskAtReview() {
        TaskOrchestrator orchestrator = new TaskOrchestrator();
        orchestrator.start(new TaskGoal(
                TaskGoal.Type.SEND_MONEY, NOW, 90, false), null, NOW);
        ScreenState current = home(NOW);
        orchestrator.observe(current, null, NOW);

        ScreenState recipient = screen(
                "Who do you want to pay?", NOW + 100L, null);
        observe(orchestrator, current, recipient);
        current = recipient;

        ScreenState amount = screen(
                "Enter an amount", NOW + 200L,
                new ObservedUiElement(
                        null, null, null, "android.widget.EditText",
                        false, true, false, true, false, "number",
                        Collections.singletonList("focus"),
                        300, 500, 500, 600, 1_000, 2_000));
        observe(orchestrator, current, amount);
        current = amount;

        ScreenState review = screen(
                "Review your payment", NOW + 300L,
                new ObservedUiElement(
                        "Pay now", null, null, "android.widget.Button",
                        true, true, false, false, false, "unknown",
                        Collections.singletonList("tap"),
                        300, 500, 500, 600, 1_000, 2_000));
        observe(orchestrator, current, review);
        return orchestrator;
    }

    private static ScreenState screen(
            String heading,
            long observedAt,
            ObservedUiElement extra
    ) {
        ObservedUiElement text = new ObservedUiElement(
                heading, null, null, "android.widget.TextView",
                false, true, false, false, false, "unknown",
                Collections.emptyList(), 0, 0, 20, 20, 1_000, 2_000);
        return ScreenObservationBuilder.build(
                extra == null
                        ? Collections.singletonList(text)
                        : java.util.Arrays.asList(text, extra),
                PAYPAL_PACKAGE,
                observedAt);
    }

    private static void observe(
            TaskOrchestrator orchestrator,
            ScreenState previous,
            ScreenState current
    ) {
        orchestrator.observe(
                current,
                ScreenTransitionDetector.detect(
                        previous, current, current.observedAt()),
                current.observedAt());
    }

    private static final class MemoryStorage implements TaskStateRepository.Storage {
        private String value;

        @Override public String read() { return value; }
        @Override public void write(String value) { this.value = value; }
        @Override public void clear() { value = null; }
    }
}
