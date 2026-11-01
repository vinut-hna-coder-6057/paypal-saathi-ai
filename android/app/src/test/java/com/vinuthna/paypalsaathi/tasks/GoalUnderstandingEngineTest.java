package com.vinuthna.paypalsaathi.tasks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GoalUnderstandingEngineTest {
    private final GoalUnderstandingEngine engine = new GoalUnderstandingEngine();

    @Test
    public void recognizesSupportedGoalsAcrossNaturalPhrasings() {
        assertType("I want to send money.", TaskGoal.Type.SEND_MONEY);
        assertType("Please help me transfer money.", TaskGoal.Type.SEND_MONEY);
        assertType("I want to see my previous transactions.",
                TaskGoal.Type.VIEW_ACTIVITY);
        assertType("Show me my previous payments.",
                TaskGoal.Type.VIEW_ACTIVITY);
        assertType("Check whether my payment was successful.",
                TaskGoal.Type.CHECK_TRANSACTION_STATUS);
        assertType("Is my payment completed?",
                TaskGoal.Type.CHECK_TRANSACTION_STATUS);
        assertType("I have a pending payment.", TaskGoal.Type.CHECK_TRANSACTION_STATUS);
        assertType("I want to find my refund.", TaskGoal.Type.FIND_REFUND);
        assertType("Why did my payment fail?", TaskGoal.Type.UNDERSTAND_PAYMENT_ERROR);
        assertType("Why did this payment fail?",
                TaskGoal.Type.UNDERSTAND_PAYMENT_ERROR);
        assertType("Open my wallet.", TaskGoal.Type.OPEN_WALLET);
        assertType("Update my account settings.", TaskGoal.Type.ACCOUNT_SETTINGS);
    }

    @Test
    public void goalContainsOnlyGenericIntentAndNoOriginalPrivateRequest() {
        String request = "Send money to Alex for INR 2500";
        TaskGoal goal = engine.understand(request, 1_800_000_000_000L).goal();

        assertNotNull(goal);
        assertEquals(TaskGoal.Type.SEND_MONEY, goal.type());
        assertEquals("send_money", goal.normalizedIntent());
        assertFalse(goal.normalizedIntent().contains("Alex"));
        assertFalse(goal.normalizedIntent().contains("2500"));
    }

    @Test
    public void ambiguousEmptyAndUnsupportedRequestsAreHandledConservatively() {
        GoalUnderstandingEngine.Result ambiguous =
                engine.understand("I need help with a payment.", 100L);
        assertTrue(ambiguous.recognized());
        assertTrue(ambiguous.goal().clarificationRequired());
        assertEquals(TaskGoal.Type.UNKNOWN, ambiguous.goal().type());
        assertNotNull(ambiguous.clarification());

        assertFalse(engine.understand("", 100L).recognized());
        assertFalse(engine.understand("Tell me a joke.", 100L).recognized());
        assertFalse(engine.understand(null, 100L).recognized());
    }

    @Test
    public void clarificationResolvesOnlyExplicitSafeChoices() {
        assertEquals(
                TaskGoal.Type.FIND_REFUND,
                engine.resolveClarification("refund", 100L).goal().type());
        assertEquals(
                TaskGoal.Type.CHECK_TRANSACTION_STATUS,
                engine.resolveClarification("status", 100L).goal().type());
        assertFalse(engine.resolveClarification("maybe", 100L).recognized());
    }

    private void assertType(String request, TaskGoal.Type expected) {
        GoalUnderstandingEngine.Result result =
                engine.understand(request, 1_800_000_000_000L);
        assertTrue(request, result.recognized());
        assertEquals(request, expected, result.goal().type());
        assertFalse(request, result.goal().clarificationRequired());
        assertNull(result.clarification());
    }
}
