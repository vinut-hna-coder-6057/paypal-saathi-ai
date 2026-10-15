package com.vinuthna.paypalsaathi.detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenClassifierTest {
    // These are synthetic classifier fixtures, not text observed from PayPal.
    @Test
    public void emptyTextIsUnknown() {
        assertEquals(ScreenCategories.UNKNOWN, ScreenClassifier.classify(""));
        assertEquals(ScreenCategories.UNKNOWN, ScreenClassifier.classify(null));
        assertEquals(ScreenCategories.UNKNOWN, ScreenClassifier.classify("  !!!  "));
    }

    @Test
    public void classifiesHomeFromMultipleExactActionableHomeControls() {
        assertEquals(
                ScreenCategories.HOME,
                ScreenClassifier.classify(
                        "",
                        Arrays.asList(
                                "Send|bottom|left",
                                "Request|bottom|right")));
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenClassifier.classify(
                        "Send",
                        Collections.singletonList("Send|bottom|left")));
    }

    @Test
    public void homeClassificationRequiresPayPalAndReturnsCanonicalEvidence() {
        ScreenClassification classification = ScreenClassifier.classifyScreen(
                "",
                Arrays.asList(
                        "Send|bottom|left",
                        "Request|bottom|right"),
                "com.paypal.android.p2pmobile");

        assertEquals(ScreenCategories.HOME, classification.category());
        assertEquals(82, classification.confidence());
        assertEquals("moderate", classification.evidenceStrength());
        assertFalse(classification.ambiguous());
        assertEquals(
                Collections.singletonList("home_action_combination"),
                classification.evidenceCodes());
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenClassifier.classifyScreen(
                        "PayPal home",
                        Collections.emptyList(),
                        "com.example.unrelated").category());
    }

    @Test
    public void incompleteOrConflictingSignalsRemainUnknown() {
        ScreenClassification incomplete = ScreenClassifier.classifyScreen(
                "Send",
                Collections.singletonList("Send|bottom|left"),
                "com.paypal.android.p2pmobile");
        assertEquals(ScreenCategories.UNKNOWN, incomplete.category());
        assertEquals("insufficient", incomplete.evidenceStrength());
        assertTrue(incomplete.ambiguous());

        ScreenClassification conflicting = ScreenClassifier.classifyScreen(
                "Enter an amount. Review your payment.",
                Collections.emptyList(),
                "com.paypal.android.p2pmobile");
        assertEquals(ScreenCategories.UNKNOWN, conflicting.category());
        assertTrue(conflicting.ambiguous());
        assertEquals(
                Collections.singletonList("conflicting_screen_signals"),
                conflicting.evidenceCodes());
    }

    @Test
    public void classifiesSupportedPaymentFlowScreensFromSyntheticSignals() {
        assertEquals(
                ScreenCategories.RECIPIENT_SELECTION,
                ScreenClassifier.classifyScreen(
                        "Who do you want to pay?",
                        Collections.emptyList(),
                        "com.paypal.android.p2pmobile").category());
        assertEquals(
                ScreenCategories.AMOUNT_ENTRY,
                ScreenClassifier.classifyScreen(
                        "Enter an amount",
                        Collections.emptyList(),
                        "com.paypal.android.p2pmobile").category());
        assertEquals(
                ScreenCategories.REVIEW_PAYMENT,
                ScreenClassifier.classifyScreen(
                        "Review your payment",
                        Collections.emptyList(),
                        "com.paypal.android.p2pmobile").category());
        assertEquals(
                ScreenCategories.CONFIRMATION,
                ScreenClassifier.classifyScreen(
                        "Payment successful",
                        Collections.emptyList(),
                        "com.paypal.android.p2pmobile").category());
        assertEquals(
                ScreenCategories.SIGN_IN,
                ScreenClassifier.classifyScreen(
                        "Sign in",
                        Collections.emptyList(),
                        "com.paypal.android.p2pmobile").category());
    }

    @Test
    public void sensitiveScreenSignalsOverrideHomeActionControls() {
        assertEquals(
                ScreenCategories.SECURITY,
                ScreenClassifier.classify(
                        "Enter your verification code",
                        Arrays.asList(
                                "Send|bottom|left",
                                "Request|bottom|right")));
    }

    @Test
    public void classifiesSupportedCategories() {
        assertEquals(
                ScreenCategories.HOME,
                ScreenClassifier.classify("PayPal home")
        );
        assertEquals(
                ScreenCategories.SIGN_IN,
                ScreenClassifier.classify("Sign in")
        );
        assertEquals(
                ScreenCategories.SEND_MONEY,
                ScreenClassifier.classify("Send money")
        );
        assertEquals(
                ScreenCategories.RECIPIENT_SELECTION,
                ScreenClassifier.classify("Who do you want to pay?")
        );
        assertEquals(
                ScreenCategories.AMOUNT_ENTRY,
                ScreenClassifier.classify("Enter an amount")
        );
        assertEquals(
                ScreenCategories.REVIEW_PAYMENT,
                ScreenClassifier.classify("Review your payment")
        );
        assertEquals(
                ScreenCategories.CONFIRMATION,
                ScreenClassifier.classify("Payment successful")
        );
        assertEquals(
                ScreenCategories.ACTIVITY,
                ScreenClassifier.classify("Transaction history")
        );
        assertEquals(
                ScreenCategories.WALLET,
                ScreenClassifier.classify("Banks and cards")
        );
        assertEquals(
                ScreenCategories.SECURITY,
                ScreenClassifier.classify("Two-step verification")
        );
    }

    @Test
    public void securityTakesPriorityOverPaymentActions() {
        assertEquals(
                ScreenCategories.SECURITY,
                ScreenClassifier.classify("Enter your password to send money now")
        );
    }

    @Test
    public void reviewAndRecipientTakePriorityOverSendMoney() {
        assertEquals(
                ScreenCategories.REVIEW_PAYMENT,
                ScreenClassifier.classify("Review and send money")
        );
        assertEquals(
                ScreenCategories.RECIPIENT_SELECTION,
                ScreenClassifier.classify("Who are you sending money to?")
        );
    }

    @Test
    public void genericSendAndAmbiguousTextAreUnknown() {
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenClassifier.classify("Send")
        );
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenClassifier.classify("Send the document for payment")
        );
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenClassifier.classify("Settings Notifications Help")
        );
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenClassifier.classify("Settings Activity Wallet Offers")
        );
    }

    @Test
    public void matchingUsesWholePhrasesRatherThanSubstrings() {
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenClassifier.classify("Inactivity notice")
        );
        assertEquals(
                ScreenCategories.SECURITY,
                ScreenClassifier.classify("Please verify your identity.")
        );
    }
}
