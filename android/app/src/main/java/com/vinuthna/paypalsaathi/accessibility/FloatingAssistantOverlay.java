package com.vinuthna.paypalsaathi.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.vinuthna.paypalsaathi.GuidanceClient;
import com.vinuthna.paypalsaathi.QuestionInputValidator;
import com.vinuthna.paypalsaathi.QuestionRequestTracker;
import com.vinuthna.paypalsaathi.R;
import com.vinuthna.paypalsaathi.ScreenContext;
import com.vinuthna.paypalsaathi.detection.ScreenCategories;
import com.vinuthna.paypalsaathi.detection.ScreenElement;
import com.vinuthna.paypalsaathi.tasks.SafeActionPolicy;
import com.vinuthna.paypalsaathi.tasks.TaskState;
import com.vinuthna.paypalsaathi.tasks.TaskStep;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

final class FloatingAssistantOverlay {
    interface TaskInteractionListener {
        boolean onGoalSubmitted(String request);
        void onPauseTask();
        void onResumeTask();
        void onCancelTask();
        void onConfirmTaskComplete();
        void onExecuteSafeAction();
        void onRepeatTask();
    }

    private static final String TAG = "SaathiOverlay";
    private static final int MAX_PANEL_HEIGHT_DP = 520;

    private final AccessibilityService service;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final QuestionRequestTracker requestTracker =
            new QuestionRequestTracker();
    private final TaskInteractionListener taskListener;

    private View root;
    private boolean visible;
    private boolean expanded;
    private boolean screenContextPending;
    private TaskState taskState;
    private TaskStep taskStep;
    private String taskMessage;

    FloatingAssistantOverlay(
            AccessibilityService service,
            TaskInteractionListener taskListener
    ) {
        this.service = service;
        this.taskListener = taskListener;
        windowManager = (WindowManager) service.getSystemService(
                AccessibilityService.WINDOW_SERVICE);
    }

    void show() {
        if (visible) {
            return;
        }
        if (windowManager == null) {
            Log.e(TAG, "Cannot show assistant: WindowManager unavailable.");
            return;
        }

        root = LayoutInflater.from(service)
                .inflate(
                        R.layout.floating_assistant,
                        new FrameLayout(service),
                        false);

        TextView floatingButton = root.findViewById(
                R.id.floatingAssistantButton);
        floatingButton.setOnClickListener(view -> expand());
        root.findViewById(R.id.floatingAssistantMinimize)
                .setOnClickListener(view -> minimize());
        root.findViewById(R.id.floatingAssistantStop)
                .setOnClickListener(view -> stopGuidance());
        root.findViewById(R.id.floatingAssistantRepeat)
                .setOnClickListener(view -> {
                    if (taskListener != null) {
                        taskListener.onRepeatTask();
                    }
                });
        root.findViewById(R.id.floatingAssistantPause)
                .setOnClickListener(view -> toggleTaskPause());
        root.findViewById(R.id.floatingAssistantDone)
                .setOnClickListener(view -> {
                    if (taskListener != null) {
                        taskListener.onConfirmTaskComplete();
                    }
                });
        root.findViewById(R.id.floatingAssistantOpenStep)
                .setOnClickListener(view -> {
                    if (taskListener != null) {
                        taskListener.onExecuteSafeAction();
                    }
                });
        root.findViewById(R.id.floatingAssistantSend)
                .setOnClickListener(view -> submitQuestion());
        root.findViewById(R.id.floatingAssistantRetry)
                .setOnClickListener(view -> submitQuestion());
        root.findViewById(R.id.floatingAssistantSuggestion)
                .setOnClickListener(view -> {
                    EditText input = root.findViewById(
                            R.id.floatingAssistantQuestion);
                    input.setText(service.getString(
                            R.string.floating_assistant_suggested_question));
                    input.setSelection(input.length());
                });

        updateCategory(ScreenCategories.UNKNOWN);
        try {
            windowManager.addView(root, createLayoutParams(false));
            visible = true;
            Log.i(TAG, "Assistant overlay added to the window manager.");
        } catch (WindowManager.BadTokenException
                 | SecurityException
                 | IllegalArgumentException exception) {
            root = null;
            expanded = false;
            visible = false;
            Log.e(TAG, "Could not add assistant overlay ("
                    + exception.getClass().getSimpleName() + ").");
            return;
        }
        updateConnectionStatus();
    }

    void hide() {
        visible = false;
        expanded = false;
        screenContextPending = false;
        requestTracker.invalidate();
        requestTracker.updateScreenCategory(ScreenCategories.UNKNOWN);

        if (root == null) {
            return;
        }

        View oldRoot = root;
        root = null;
        if (windowManager != null) {
            try {
                windowManager.removeView(oldRoot);
                Log.i(TAG, "Assistant overlay removed from the window manager.");
            } catch (IllegalArgumentException ignored) {
                Log.w(TAG, "Assistant overlay was already detached.");
            } catch (SecurityException exception) {
                Log.e(TAG, "Could not remove assistant overlay ("
                        + exception.getClass().getSimpleName() + ").");
            }
        }
    }

    void updateCategory(String category) {
        updateScreenContext(category, Collections.emptyList(), 0L);
    }

    void updateScreenContext(
            String category,
            List<String> visibleLabels,
            List<String> actionableElements,
            int confidence,
            String evidenceStrength,
            boolean ambiguous,
            List<String> evidenceCodes,
            long observedAt
    ) {
        boolean wasPending = screenContextPending;
        boolean changed = requestTracker.updateScreenContext(
                category,
                visibleLabels,
                actionableElements,
                confidence,
                evidenceStrength,
                ambiguous,
                evidenceCodes,
                observedAt);
        screenContextPending = false;
        renderCategory();
        if (wasPending && root != null) {
            setAnswer(ScreenCategories.UNKNOWN.equals(
                    requestTracker.screenCategory())
                    ? R.string.floating_assistant_unknown_explanation
                    : R.string.floating_assistant_context_resolved);
        }
        if ((changed || wasPending) && expanded && root != null) {
            setQuestionLoading(false);
            if (taskState == null || taskState.goal() == null) {
                if (!wasPending) {
                    setAnswer(R.string.floating_assistant_context_changed);
                }
            }
            root.findViewById(R.id.floatingAssistantRetry)
                    .setVisibility(View.GONE);
            renderTask();
        }
    }

    void updateScreenContext(
            String category,
            List<String> visibleLabels,
            long observedAt
    ) {
        updateScreenContext(
                category,
                visibleLabels,
                Collections.emptyList(),
                0,
                "insufficient",
                true,
                Collections.emptyList(),
                observedAt);
    }

    private void renderCategory() {
        if (root == null) {
            return;
        }

        String safeCategory = requestTracker.screenCategory();
        TextView categoryView = root.findViewById(
                R.id.floatingAssistantCategory);
        categoryView.setText(service.getString(
                R.string.detected_category_fmt,
                ScreenCategories.displayLabel(safeCategory),
                safeCategory));
        String explanation = contextExplanation(safeCategory);
        if (screenContextPending) {
            explanation += "\n"
                    + service.getString(
                            R.string.floating_assistant_context_last_confirmed);
        }
        ((TextView) root.findViewById(R.id.floatingAssistantContext))
                .setText(explanation);
    }

    void updateTask(TaskState state, TaskStep step, String message) {
        taskState = state;
        taskStep = step;
        taskMessage = message;
        renderTask();
    }

    private void renderTask() {
        if (root == null) {
            return;
        }
        boolean hasTask = taskState != null && taskState.goal() != null;
        TextView taskTitle = root.findViewById(R.id.floatingAssistantTask);
        TextView progress = root.findViewById(R.id.floatingAssistantTaskProgress);
        View repeat = root.findViewById(R.id.floatingAssistantRepeat);
        View pause = root.findViewById(R.id.floatingAssistantPause);
        View done = root.findViewById(R.id.floatingAssistantDone);
        View openStep = root.findViewById(R.id.floatingAssistantOpenStep);
        if (!hasTask) {
            taskTitle.setVisibility(View.GONE);
            progress.setVisibility(View.GONE);
            repeat.setVisibility(View.GONE);
            pause.setVisibility(View.GONE);
            done.setVisibility(View.GONE);
            openStep.setVisibility(View.GONE);
            if (taskMessage != null && !taskMessage.trim().isEmpty()) {
                ((TextView) root.findViewById(R.id.floatingAssistantAnswer))
                        .setText(taskMessage);
                root.findViewById(R.id.floatingAssistantRetry)
                        .setVisibility(View.GONE);
            }
            return;
        }

        taskTitle.setVisibility(View.VISIBLE);
        taskTitle.setText(service.getString(
                R.string.floating_assistant_task_title,
                taskState.goal().displayName()));
        progress.setVisibility(View.VISIBLE);
        String progressText;
        switch (taskState.status()) {
            case PAUSED:
                progressText = service.getString(
                        R.string.floating_assistant_task_paused);
                break;
            case COMPLETED:
                progressText = service.getString(
                        R.string.floating_assistant_task_completed);
                break;
            case CANCELLED:
                progressText = service.getString(
                        R.string.floating_assistant_task_cancelled);
                break;
            case NEEDS_CLARIFICATION:
                progressText = service.getString(
                        R.string.floating_assistant_task_clarification);
                break;
            default:
                progressText = service.getString(
                        R.string.floating_assistant_task_progress,
                        taskState.completedStepIds().size() + 1,
                        taskStep == null ? "" : taskStep.description());
                break;
        }
        progress.setText(progressText);

        boolean terminal = taskState.status() == TaskState.Status.COMPLETED
                || taskState.status() == TaskState.Status.CANCELLED
                || taskState.status() == TaskState.Status.FAILED;
        repeat.setVisibility(terminal ? View.GONE : View.VISIBLE);
        boolean safeNavigation = taskState.status() == TaskState.Status.WAITING_FOR_USER
                && taskStep != null
                && taskStep.riskCategory()
                    == ScreenElement.RiskCategory.SAFE_NAVIGATION
                && taskStep.recommendedAction() != null
                && SafeActionPolicy.isActionAllowed(
                        ScreenContext.actionLabel(
                                taskStep.recommendedAction()),
                        taskStep.currentScreenCategory());
        openStep.setVisibility(safeNavigation ? View.VISIBLE : View.GONE);
        if (safeNavigation) {
            String label = ScreenContext.actionLabel(
                    taskStep.recommendedAction());
            ((TextView) openStep).setText(service.getString(
                    R.string.floating_assistant_open_step,
                    label == null ? "" : label));
        }
        pause.setVisibility(terminal ? View.GONE : View.VISIBLE);
        ((TextView) pause).setText(
                taskState.status() == TaskState.Status.PAUSED
                        ? R.string.floating_assistant_resume_task
                        : R.string.floating_assistant_pause_task);
        done.setVisibility(
                taskState.status() == TaskState.Status.WAITING_FOR_USER
                        && taskStep != null
                        && taskStep.expectedNextScreen() == null
                        ? View.VISIBLE : View.GONE);
        if (taskMessage != null && !taskMessage.trim().isEmpty()) {
            ((TextView) root.findViewById(R.id.floatingAssistantAnswer))
                    .setText(taskMessage);
            root.findViewById(R.id.floatingAssistantRetry)
                    .setVisibility(View.GONE);
        }
    }

    private void toggleTaskPause() {
        if (taskListener == null || taskState == null
                || taskState.goal() == null) {
            return;
        }
        if (taskState.status() == TaskState.Status.PAUSED) {
            taskListener.onResumeTask();
        } else {
            taskListener.onPauseTask();
        }
    }

    private WindowManager.LayoutParams createLayoutParams(boolean panel) {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int width = panel
                ? Math.min(metrics.widthPixels - dp(24), dp(420))
                : dp(56);
        int height = panel
                ? Math.min(
                        Math.min(metrics.heightPixels - dp(80), dp(MAX_PANEL_HEIGHT_DP)),
                        Math.round(metrics.heightPixels * 0.58f))
                : dp(56);

        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (!panel) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Math.max(dp(56), width),
                Math.max(dp(56), height),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = panel
                ? Gravity.TOP | Gravity.CENTER_HORIZONTAL
                : Gravity.BOTTOM | Gravity.END;
        params.y = panel ? dp(56) : dp(32);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        return params;
    }

    private void expand() {
        if (root == null || !visible || expanded) {
            return;
        }
        expanded = true;
        root.findViewById(R.id.floatingAssistantButton)
                .setVisibility(View.GONE);
        root.findViewById(R.id.floatingAssistantPanel)
                .setVisibility(View.VISIBLE);
        windowManager.updateViewLayout(root, createLayoutParams(true));
        renderCategory();
        renderTask();
    }

    private void minimize() {
        if (root == null || !expanded) {
            return;
        }
        expanded = false;
        root.findViewById(R.id.floatingAssistantPanel)
                .setVisibility(View.GONE);
        root.findViewById(R.id.floatingAssistantButton)
                .setVisibility(View.VISIBLE);
        windowManager.updateViewLayout(root, createLayoutParams(false));
    }

    private String contextExplanation(String category) {
        switch (category) {
            case ScreenCategories.HOME:
                return "The available text suggests a PayPal overview area. The exact page state is not verified.";
            case ScreenCategories.SIGN_IN:
                return "The available text suggests a sign-in page. Enter credentials only in the official PayPal app.";
            case ScreenCategories.SEND_MONEY:
                return "A send-money-related page was recognized. Saathi cannot confirm the exact step or transaction state.";
            case ScreenCategories.RECIPIENT_SELECTION:
                return "A recipient-related page was recognized. Check the recipient yourself in PayPal.";
            case ScreenCategories.AMOUNT_ENTRY:
                return "An amount-related page was recognized. Verify the amount and currency yourself in PayPal.";
            case ScreenCategories.REVIEW_PAYMENT:
                return "A payment-review-related page was recognized. Verify all details yourself before deciding.";
            case ScreenCategories.CONFIRMATION:
                return "A status-related page was recognized. This does not verify whether a payment succeeded.";
            case ScreenCategories.ACTIVITY:
                return "An activity/history-related page was recognized. Saathi cannot inspect transaction details.";
            case ScreenCategories.WALLET:
                return "A wallet/payment-method-related page was recognized. Saathi does not read payment details.";
            case ScreenCategories.SECURITY:
                return "A security-related page was recognized. Never share passwords, PINs, or verification codes.";
            case ScreenCategories.UNKNOWN:
            default:
                return service.getString(
                        R.string.floating_assistant_unknown_explanation);
        }
    }

    private void updateConnectionStatus() {
        GuidanceClient.checkHealth(new GuidanceClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                mainHandler.post(() -> {
                    if (visible && root != null) {
                        TextView status = root.findViewById(
                                R.id.floatingAssistantConnection);
                        status.setText(service.getString(
                                R.string.backend_connected,
                                GuidanceClient.baseUrl()));
                    }
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    if (visible && root != null) {
                        TextView status = root.findViewById(
                                R.id.floatingAssistantConnection);
                        status.setText(service.getString(
                                R.string.backend_unavailable,
                                GuidanceClient.baseUrl()));
                    }
                });
            }
        });
    }

    private void submitQuestion() {
        if (root == null || !visible) {
            return;
        }
        if (screenContextPending) {
            setAnswer(R.string.floating_assistant_context_checking);
            return;
        }

        EditText input = root.findViewById(R.id.floatingAssistantQuestion);
        String question = input.getText().toString().trim();
        if (question.isEmpty()) {
            setAnswer(R.string.floating_assistant_question_empty);
            return;
        }
        if (question.length() > 500) {
            setAnswer(R.string.question_too_long);
            return;
        }
        if (QuestionInputValidator.containsLikelySensitiveValue(question)) {
            setAnswer(R.string.floating_assistant_question_sensitive);
            return;
        }

        if (taskListener != null && taskListener.onGoalSubmitted(question)) {
            input.setText("");
            return;
        }
        requestAnswer(question);
    }

    private void requestAnswer(String question) {
        if (QuestionInputValidator.containsLikelySensitiveValue(question)) {
    setQuestionLoading(false);
    setAnswer(R.string.floating_assistant_question_sensitive);
    return;
}
        QuestionRequestTracker.Request request = requestTracker.begin(question);
        if (request == null) {
            return;
        }

        setQuestionLoading(true);
        setAnswer(R.string.preparing_answer);
        GuidanceClient.askQuestion(
                request.question(),
                request.screenCategory(),
                request.visibleLabels(),
                request.actionableElements(),
                request.confidence(),
                request.evidenceStrength(),
                request.ambiguous(),
                request.evidenceCodes(),
                request.observedAt(),
                new GuidanceClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        mainHandler.post(() -> showAnswer(request, response));
                    }

                    @Override
                    public void onError(String message) {
                        mainHandler.post(() -> showError(request));
                    }
                });
    }

    private void showAnswer(
            QuestionRequestTracker.Request request,
            JSONObject response
    ) {
        if (!requestTracker.isCurrent(request) || root == null || !visible) {
            return;
        }

        requestTracker.finish(request);
        setQuestionLoading(false);
        String responseContext = response.optString(
                "screenCategory",
                request.screenCategory());
        if (!request.screenCategory().equals(responseContext)) {
            showErrorWithoutRequest();
            return;
        }

        String guidance = response.optString("guidance", "");
        if (guidance.isEmpty()) {
            showErrorWithoutRequest();
            return;
        }

        String disclaimer = response.optString("disclaimer", "");
        StringBuilder text = new StringBuilder(guidance);
        if (!disclaimer.isEmpty()) {
            text.append("\n\n").append(disclaimer);
        }
        ((TextView) root.findViewById(R.id.floatingAssistantAnswer))
                .setText(text.toString());
        root.findViewById(R.id.floatingAssistantRetry)
                .setVisibility(View.GONE);
    }

    private void showError(QuestionRequestTracker.Request request) {
        if (!requestTracker.isCurrent(request) || root == null || !visible) {
            return;
        }
        requestTracker.finish(request);
        setQuestionLoading(false);
        showErrorWithoutRequest();
    }

    private void showErrorWithoutRequest() {
        setAnswer(R.string.floating_assistant_question_error);
        root.findViewById(R.id.floatingAssistantRetry)
                .setVisibility(View.VISIBLE);
    }

    private void stopGuidance() {
        requestTracker.invalidate();
        setQuestionLoading(false);
        if (taskListener != null
                && taskState != null
                && taskState.goal() != null
                && taskState.status() != TaskState.Status.COMPLETED
                && taskState.status() != TaskState.Status.CANCELLED
                && taskState.status() != TaskState.Status.FAILED) {
            taskListener.onCancelTask();
        } else {
            setAnswer(R.string.floating_assistant_flow_stopped);
        }
        root.findViewById(R.id.floatingAssistantRetry)
                .setVisibility(View.GONE);
    }

    private void setQuestionLoading(boolean loading) {
        if (root == null) {
            return;
        }

        root.findViewById(R.id.floatingAssistantLoading)
                .setVisibility(loading ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.floatingAssistantSend)
                .setEnabled(!loading && !screenContextPending);
        root.findViewById(R.id.floatingAssistantRetry)
                .setEnabled(!loading && !screenContextPending);
        root.findViewById(R.id.floatingAssistantQuestion)
                .setEnabled(!loading && !screenContextPending);
    }

    void markScreenContextUncertain() {
        if (screenContextPending) {
            return;
        }
        screenContextPending = true;
        requestTracker.invalidateRequests();
        setQuestionLoading(false);
        renderCategory();
        setAnswer(R.string.floating_assistant_context_checking);
        if (root != null
                && taskState != null
                && taskState.goal() != null
                && taskState.status() != TaskState.Status.PAUSED) {
            ((TextView) root.findViewById(R.id.floatingAssistantAnswer))
                    .setText(R.string.floating_assistant_context_checking);
        }
        if (root != null) {
            root.findViewById(R.id.floatingAssistantRetry)
                    .setVisibility(View.GONE);
        }
    }

    private void setAnswer(int resourceId) {
        if (root != null) {
            ((TextView) root.findViewById(R.id.floatingAssistantAnswer))
                    .setText(resourceId);
        }
    }

    private int dp(int value) {
        return Math.round(value * service.getResources()
                .getDisplayMetrics().density);
    }
}
