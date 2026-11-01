package com.vinuthna.paypalsaathi.detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ScreenObservationBuilderTest {
    private static final String PAYPAL_PACKAGE =
            "com.paypal.android.p2pmobile";

    @Test
    public void visualFallbackUsesOnlyAllowlistedGenericHints() {
        String hints = ScreenDataSanitizer.extractVisualScreenHints(
                "Payment successful for Jane Doe, email jane@example.com, "
                        + "amount INR 5000, card ending 1234");

        assertEquals("payment successful", hints);
        assertFalse(hints.contains("Jane"));
        assertFalse(hints.contains("5000"));
        assertFalse(hints.contains("1234"));
        assertFalse(hints.contains("jane@example.com"));
    }
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void emptyTreeAndMissingLabelsStayUnknown() {
        ScreenState empty = ScreenObservationBuilder.build(
                Collections.emptyList(), PAYPAL_PACKAGE, NOW);
        ScreenState unlabeled = ScreenObservationBuilder.build(
                Collections.singletonList(element(
                        null,
                        null,
                        null,
                        "android.widget.Button",
                        true,
                        true,
                        false,
                        false,
                        false,
                        "unknown")),
                PAYPAL_PACKAGE,
                NOW);

        assertEquals(ScreenCategories.UNKNOWN, empty.category());
        assertEquals(ScreenCategories.UNKNOWN, unlabeled.category());
        assertTrue(empty.elements().isEmpty());
        assertTrue(unlabeled.elements().isEmpty());
    }

    @Test
    public void duplicateElementsAreDeduplicatedAndCollectionsAreImmutable() {
        ObservedUiElement send = button("Send", true, true);
        ObservedUiElement request = button("Request", true, true);
        ScreenState state = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("PayPal home"),
                        send,
                        send,
                        request),
                PAYPAL_PACKAGE,
                NOW);

        assertEquals(ScreenCategories.HOME, state.category());
        assertEquals(3, state.elements().size());
        assertEquals(2, state.safeActionDescriptors().size());
        try {
            state.elements().clear();
            throw new AssertionError("Expected immutable elements.");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
    }

    @Test
    public void disabledAndNonClickableControlsCannotBeRecommended() {
        ScreenState disabled = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("PayPal home"),
                        button("Send", true, false),
                        button("Request", true, true)),
                PAYPAL_PACKAGE,
                NOW);
        ScreenState nonClickable = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("Send"),
                        text("Request")),
                PAYPAL_PACKAGE,
                NOW);

        assertEquals(ScreenCategories.HOME, disabled.category());
        assertEquals(
                Collections.singletonList("Request|top|center"),
                disabled.safeActionDescriptors());
        assertEquals(ScreenCategories.UNKNOWN, nonClickable.category());
        assertTrue(nonClickable.safeActionDescriptors().isEmpty());
    }

    @Test
    public void recipientAndAuthorizationControlsAreNeverRecommended() {
        ScreenState recipient = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("Who do you want to pay?"),
                        button("Continue", true, true)),
                PAYPAL_PACKAGE,
                NOW);
        ScreenState review = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("Review your payment"),
                        button("Pay now", true, true)),
                PAYPAL_PACKAGE,
                NOW);

        assertEquals(ScreenCategories.RECIPIENT_SELECTION, recipient.category());
        assertTrue(recipient.safeActionDescriptors().isEmpty());
        assertEquals(ScreenCategories.REVIEW_PAYMENT, review.category());
        assertTrue(review.safeActionDescriptors().isEmpty());
    }

    @Test
    public void editableSensitiveValuesAndFinancialAmountsAreNotRetained() {
        ScreenState state = ScreenObservationBuilder.build(
                Arrays.asList(
                        element(
                                "987654",
                                "Verification code 987654",
                                "Enter code",
                                "android.widget.EditText",
                                false,
                                true,
                                true,
                                true,
                                true,
                                "sensitive"),
                        text("Amount ₹25,000.00")),
                PAYPAL_PACKAGE,
                NOW);

        assertFalse(state.elements().isEmpty());
        for (ScreenElement element : state.elements()) {
            assertFalse(String.valueOf(element.label()).contains("987654"));
            assertFalse(String.valueOf(
                    element.accessibilityDescription()).contains("987654"));
            assertFalse(String.valueOf(element.label()).contains("25,000"));
            if (element.editable()) {
                assertNull(element.label());
                assertNull(element.accessibilityDescription());
                assertEquals("sensitive", element.editableFieldType());
            }
        }
    }

    @Test
    public void sanitizerRemovesIdentifiersAndSensitiveValuesFromLabels() {
        String sanitized = ScreenDataSanitizer.sanitizeLabel(
                "Send to user@example.test for ₹1,250.00, reference 123456789012");

        assertNotNull(sanitized);
        assertFalse(sanitized.contains("user@example.test"));
        assertFalse(sanitized.contains("1,250"));
        assertFalse(sanitized.contains("123456789012"));
    }

    @Test
    public void stateContainsStableIdentityAndStructuredSafeElementMetadata() {
        ScreenState state = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("PayPal home"),
                        button("Send", true, true)),
                PAYPAL_PACKAGE,
                NOW);
        ScreenElement send = state.elements().get(1);

        assertNotNull(state.screenIdentifier());
        assertNotNull(state.fingerprint());
        assertEquals(NOW, state.observedAt());
        assertEquals(ScreenElement.Role.BUTTON, send.role());
        assertTrue(send.clickable());
        assertTrue(send.enabled());
        assertTrue(send.safeToRecommend());
        assertEquals(
                ScreenElement.RiskCategory.SAFE_NAVIGATION,
                send.riskCategory());
        assertTrue(send.supportedActions().contains("tap"));
    }

    @Test
    public void screenStateDefensivelyCopiesAndRejectsInvalidMetadata() {
        List<ScreenElement> elements = new ArrayList<>();
        elements.add(ScreenElementExtractor.extract(
                Collections.singletonList(button("Request", true, true)),
                ScreenCategories.HOME,
                90).get(0));
        ScreenState valid = ScreenObservationBuilder.build(
                Arrays.asList(text("PayPal home"), button("Send", true, true)),
                PAYPAL_PACKAGE,
                NOW);
        ScreenState copied = new ScreenState(
                valid.screenIdentifier(),
                valid.category(),
                elements,
                Arrays.asList("Send|bottom|left", "person@example.test|middle|center"),
                101,
                "moderate",
                false,
                Arrays.asList("home_heading", "account=123456789"),
                NOW,
                valid.fingerprint(),
                true);
        elements.clear();

        assertEquals(1, copied.elements().size());
        assertEquals(
                Collections.singletonList("Send|bottom|left"),
                copied.safeActionDescriptors());
        assertEquals(Collections.singletonList("home_heading"), copied.evidenceCodes());
        assertEquals(100, copied.confidence());

        ScreenState invalid = new ScreenState(
                null,
                "not_a_category",
                null,
                Collections.singletonList("untrusted label|top|left"),
                100,
                "strong",
                false,
                Collections.singletonList("private-value"),
                -1,
                "not-a-fingerprint",
                true);
        assertEquals(ScreenCategories.UNKNOWN, invalid.category());
        assertEquals("unknown:0000000000000000", invalid.screenIdentifier());
        assertEquals(0, invalid.confidence());
        assertEquals("insufficient", invalid.evidenceStrength());
        assertTrue(invalid.ambiguous());
        assertTrue(invalid.safeActionDescriptors().isEmpty());
        assertTrue(invalid.evidenceCodes().isEmpty());
        assertEquals(0L, invalid.observedAt());
    }

    @Test
    public void fingerprintsDoNotDependOnTraversalOrder() {
        ScreenState first = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("PayPal home"),
                        button("Send", true, true),
                        button("Request", true, true)),
                PAYPAL_PACKAGE,
                NOW);
        ScreenState reordered = ScreenObservationBuilder.build(
                Arrays.asList(
                        button("Request", true, true),
                        text("PayPal home"),
                        button("Send", true, true)),
                PAYPAL_PACKAGE,
                NOW + 100);

        assertEquals(first.fingerprint(), reordered.fingerprint());
    }

    @Test
    public void fingerprintIgnoresUnallowlistedTextButTracksControlState() {
        ScreenState first = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("PayPal home"),
                        button("Send", true, true)),
                PAYPAL_PACKAGE,
                NOW);
        ScreenState differentPrivateText = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("PayPal home Account holder"),
                        button("Send", true, true)),
                PAYPAL_PACKAGE,
                NOW + 100);
        ScreenState disabledControl = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("PayPal home"),
                        button("Send", true, false)),
                PAYPAL_PACKAGE,
                NOW + 200);

        assertEquals(first.fingerprint(), differentPrivateText.fingerprint());
        assertNotEquals(first.fingerprint(), disabledControl.fingerprint());
    }

    @Test
    public void financialAuthorizationIsIdentifiedButNeverRecommended() {
        ScreenState review = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("Review your payment"),
                        button("Pay now", true, true)),
                PAYPAL_PACKAGE,
                NOW);
        ScreenElement payNow = review.elements().get(1);

        assertEquals(
                ScreenElement.RiskCategory.FINANCIAL_AUTHORIZATION,
                payNow.riskCategory());
        assertFalse(payNow.safeToRecommend());
        assertNull(payNow.actionDescriptor());
    }

    @Test
    public void reviewAndConfirmationControlsAreNotAssumedToBeSafeNavigation() {
        ScreenState review = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("Review your payment"),
                        button("Continue", true, true)),
                PAYPAL_PACKAGE,
                NOW);
        ScreenState confirmation = ScreenObservationBuilder.build(
                Arrays.asList(
                        text("Payment successful"),
                        button("Continue", true, true)),
                PAYPAL_PACKAGE,
                NOW);

        assertEquals(
                ScreenElement.RiskCategory.FINANCIAL_AUTHORIZATION,
                review.elements().get(1).riskCategory());
        assertEquals(
                ScreenElement.RiskCategory.UNKNOWN_RISK,
                confirmation.elements().get(1).riskCategory());
        assertTrue(review.safeActionDescriptors().isEmpty());
        assertTrue(confirmation.safeActionDescriptors().isEmpty());
    }

    @Test
    public void unsafeElementMetadataCannotBeMarkedAsRecommended() {
        ScreenElement element = new ScreenElement(
                "Pay now ₹100.00",
                "card number: 4111111111111111",
                null,
                false,
                false,
                false,
                false,
                null,
                Collections.singletonList("tap"),
                true,
                ScreenElement.RiskCategory.FINANCIAL_AUTHORIZATION,
                Collections.emptyList(),
                99,
                "Send|top|left");

        assertEquals(ScreenElement.Role.UNKNOWN, element.role());
        assertEquals(ScreenElement.RiskCategory.FINANCIAL_AUTHORIZATION,
                element.riskCategory());
        assertFalse(element.safeToRecommend());
        assertEquals(0, element.confidence());
        assertNull(element.actionDescriptor());
        assertFalse(String.valueOf(element.label()).contains("100.00"));
        assertFalse(String.valueOf(element.accessibilityDescription())
                .contains("4111111111111111"));
    }

    @Test
    public void errorAndLoadingScreensAreClassifiedConservatively() {
        assertEquals(
                ScreenCategories.ERROR,
                ScreenClassifier.classify("Something went wrong. Try again later."));
        assertEquals(
                ScreenCategories.LOADING,
                ScreenClassifier.classify("Please wait while loading"));
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenClassifier.classifyScreen(
                        "Loading. Something went wrong.",
                        Collections.emptyList(),
                        PAYPAL_PACKAGE).category());
    }

    @Test
    public void localizedHindiAndTeluguLabelsAreRecognized() {
        assertEquals(
                ScreenCategories.RECIPIENT_SELECTION,
                ScreenClassifier.classify("किसे भुगतान करना है"));
        assertEquals(
                ScreenCategories.AMOUNT_ENTRY,
                ScreenClassifier.classify("మొత్తం నమోదు చేయండి"));
        assertEquals(
                ScreenCategories.WALLET,
                ScreenClassifier.classify("వాలెట్"));
    }

    @Test
    public void errorAndLoadingCategoriesMapToExistingApiContract() {
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenCategories.apiCategory(ScreenCategories.ERROR));
        assertEquals(
                ScreenCategories.UNKNOWN,
                ScreenCategories.apiCategory(ScreenCategories.LOADING));
        assertEquals(
                ScreenCategories.HOME,
                ScreenCategories.apiCategory(ScreenCategories.HOME));
    }

    private static ObservedUiElement text(String label) {
        return element(
                label,
                null,
                null,
                "android.widget.TextView",
                false,
                true,
                false,
                false,
                false,
                "unknown");
    }

    private static ObservedUiElement button(
            String label,
            boolean clickable,
            boolean enabled
    ) {
        return element(
                label,
                null,
                null,
                "android.widget.Button",
                clickable,
                enabled,
                false,
                false,
                false,
                "unknown");
    }

    private static ObservedUiElement element(
            String text,
            String description,
            String hint,
            String className,
            boolean clickable,
            boolean enabled,
            boolean focused,
            boolean editable,
            boolean sensitive,
            String fieldType
    ) {
        return new ObservedUiElement(
                text,
                description,
                hint,
                className,
                clickable,
                enabled,
                focused,
                editable,
                sensitive,
                fieldType,
                Collections.singletonList("tap"),
                300,
                500,
                500,
                600,
                1_000,
                2_000);
    }
}
