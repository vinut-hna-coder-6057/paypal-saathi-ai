package com.vinuthna.paypalsaathi.accessibility;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CategoryEventGateTest {
    @Test
    public void suppressesBriefSameCategoryDuplicates() {
        CategoryEventGate gate = new CategoryEventGate();

        assertTrue(gate.shouldEmit("home", 1_000L));
        assertFalse(gate.shouldEmit("home", 5_999L));
    }

    @Test
    public void emitsCategoryChangesImmediately() {
        CategoryEventGate gate = new CategoryEventGate();

        assertTrue(gate.shouldEmit("home", 1_000L));
        assertTrue(gate.shouldEmit("send_money", 1_100L));
    }

    @Test
    public void emitsSameCategoryAgainAfterCooldown() {
        CategoryEventGate gate = new CategoryEventGate();

        assertTrue(gate.shouldEmit("home", 1_000L));
        assertTrue(gate.shouldEmit("home", 6_000L));
    }

    @Test
    public void emitsChangedNavigationHintsWithoutWaitingForCategoryChange() {
        CategoryEventGate gate = new CategoryEventGate();

        assertTrue(gate.shouldEmit("home", List.of("Home"), 1_000L));
        assertTrue(gate.shouldEmit("home", List.of("Activity"), 1_100L));
        assertFalse(gate.shouldEmit(
                "home",
                Collections.singletonList("Activity"),
                1_200L));
    }
}
