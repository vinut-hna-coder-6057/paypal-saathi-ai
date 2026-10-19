package com.vinuthna.paypalsaathi;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestionInputValidatorTest {

    @Test
    public void blocksCurrencyAmountsInQuestions() {
               String[] sensitiveQuestions = {
                "I sent INR 500 to my friend. How do I check it?",
                "I sent Rs 500 to my friend. How do I check it?",
                "I transferred INR 2,500 to my friend. Where can I see it?",
                "I paid $25.50. How do I check the payment status?",
                "I paid \u20AC20. How do I check the payment status?",
                "I paid \u00A310. How do I check the payment status?",
                "I paid \u20B9500. How do I check the payment status?"
        };

        for (String question : sensitiveQuestions) {
            assertTrue(
                    "Expected sensitive question to be blocked: " + question,
                    QuestionInputValidator.containsLikelySensitiveValue(question)
            );
        }
    }

    @Test
    public void continuesToAllowGeneralQuestionsWithoutSensitiveValues() {
        String[] generalQuestions = {
                "How do I send money using PayPal?",
                "How do I check my transaction history?",
                "How can I protect my account?",
                "What should I do when an OTP prompt appears?"
        };

        for (String question : generalQuestions) {
            assertFalse(
                    "Expected general question to be allowed: " + question,
                    QuestionInputValidator.containsLikelySensitiveValue(question)
            );
        }
    }
}