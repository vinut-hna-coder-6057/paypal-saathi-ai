package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.ScreenContext;
import com.vinuthna.paypalsaathi.detection.ScreenCategories;
import com.vinuthna.paypalsaathi.detection.ScreenElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaskStep {
    public enum Status {
        WAITING_FOR_USER,
        COMPLETED,
        NEEDS_CLARIFICATION
    }

    private final String stepId;
    private final String taskId;
    private final String description;
    private final String instruction;
    private final String currentScreenCategory;
    private final String expectedNextScreen;
    private final String recommendedAction;
    private final ScreenElement.RiskCategory riskCategory;
    private final int confidence;
    private final List<String> evidenceCodes;
    private final boolean requiresUserConfirmation;
    private final Status status;

    TaskStep(
            String stepId,
            String taskId,
            String description,
            String instruction,
            String currentScreenCategory,
            String expectedNextScreen,
            String recommendedAction,
            ScreenElement.RiskCategory riskCategory,
            int confidence,
            List<String> evidenceCodes,
            boolean requiresUserConfirmation,
            Status status
    ) {
        this.stepId = stepId;
        this.taskId = taskId;
        this.description = description;
        this.instruction = instruction;
        this.currentScreenCategory = ScreenCategories.isKnownCategory(
                currentScreenCategory) ? currentScreenCategory : ScreenCategories.UNKNOWN;
        this.expectedNextScreen = ScreenCategories.isKnownCategory(expectedNextScreen)
                ? expectedNextScreen
                : null;
        List<String> safeAction = ScreenContext.sanitizeActionDescriptors(
                Collections.singletonList(recommendedAction));
        this.recommendedAction = safeAction.isEmpty() ? null : safeAction.get(0);
        this.riskCategory = riskCategory == null
                ? ScreenElement.RiskCategory.NOT_ACTIONABLE
                : riskCategory;
        this.confidence = Math.max(0, Math.min(confidence, 100));
        this.evidenceCodes = evidenceCodes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(evidenceCodes));
        this.requiresUserConfirmation = requiresUserConfirmation;
        this.status = status == null ? Status.WAITING_FOR_USER : status;
    }

    public String stepId() { return stepId; }
    public String taskId() { return taskId; }
    public String description() { return description; }
    public String instruction() { return instruction; }
    public String currentScreenCategory() { return currentScreenCategory; }
    public String expectedNextScreen() { return expectedNextScreen; }
    public String recommendedAction() { return recommendedAction; }
    public ScreenElement.RiskCategory riskCategory() { return riskCategory; }
    public int confidence() { return confidence; }
    public List<String> evidenceCodes() { return evidenceCodes; }
    public boolean requiresUserConfirmation() { return requiresUserConfirmation; }
    public Status status() { return status; }
}
