package com.vinuthna.paypalsaathi;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HealthRequestGateTest {
    @Test
    public void newerRequestMakesOlderCallbackStale() {
        HealthRequestGate gate = new HealthRequestGate();
        long oldRequest = gate.beginRequest();
        long newRequest = gate.beginRequest();

        assertFalse(gate.isCurrent(oldRequest));
        assertTrue(gate.isCurrent(newRequest));
    }

    @Test
    public void invalidatedRequestCannotUpdateNewerBackendState() {
        HealthRequestGate gate = new HealthRequestGate();
        long request = gate.beginRequest();

        gate.invalidate();

        assertFalse(gate.isCurrent(request));
    }

    @Test
    public void destroyedActivityRejectsPendingAndFutureCallbacks() {
        HealthRequestGate gate = new HealthRequestGate();
        long request = gate.beginRequest();

        gate.close();

        assertFalse(gate.isCurrent(request));
        assertFalse(gate.isCurrent(gate.beginRequest()));
    }
}
