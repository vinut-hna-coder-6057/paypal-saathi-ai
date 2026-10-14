package com.vinuthna.paypalsaathi.detection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScreenClassifierTest {
    // These are synthetic classifier fixtures, not text observed from PayPal.
    @Test
    public void emptyTextIsUnknown() {
        assertEquals(ScreenCategories.UNKNOWN, ScreenClassifier.classify(""));
        assertEquals(ScreenCategories.UNKNOWN, ScreenClassifier.classify(null));
        assertEquals(ScreenCategories.UNKNOWN, ScreenClassifier.classify("  !!!  "));
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
