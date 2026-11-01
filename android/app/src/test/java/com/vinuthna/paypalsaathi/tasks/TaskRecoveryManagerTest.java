package com.vinuthna.paypalsaathi.tasks;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskRecoveryManagerTest {
    @Test
    public void uncertainPaymentNeverRetriesOrClaimsAnOutcome() {
        TaskRecoveryManager.Recovery recovery =
                new TaskRecoveryManager().recover(
                        TaskRecoveryManager.Problem.UNCERTAIN_PAYMENT);

        assertFalse(recovery.continueTask());
        assertTrue(recovery.requiresFreshObservation());
        assertFalse(recovery.automaticRetryAllowed());
        assertTrue(recovery.message().contains(
                "Do not try the payment again"));
        assertTrue(recovery.message().contains("check Activity"));
    }

    @Test
    public void transientScreenProblemsRequireObservationWithoutRetry() {
        TaskRecoveryManager.Recovery recovery =
                new TaskRecoveryManager().recover(
                        TaskRecoveryManager.Problem.STALE_OBSERVATION);

        assertTrue(recovery.continueTask());
        assertTrue(recovery.requiresFreshObservation());
        assertFalse(recovery.automaticRetryAllowed());
    }
}
