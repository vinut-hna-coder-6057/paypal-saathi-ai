package com.vinuthna.paypalsaathi.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.HardwareBuffer;
import android.graphics.Rect;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Build;
import android.text.InputType;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.vinuthna.paypalsaathi.CategoryStore;
import com.vinuthna.paypalsaathi.R;
import com.vinuthna.paypalsaathi.ScreenContext;
import com.vinuthna.paypalsaathi.ScreenMonitorContract;
import com.vinuthna.paypalsaathi.tasks.SafeActionPolicy;
import com.vinuthna.paypalsaathi.detection.ScreenCategories;
import com.vinuthna.paypalsaathi.detection.ScreenClassification;
import com.vinuthna.paypalsaathi.detection.ObservedUiElement;
import com.vinuthna.paypalsaathi.detection.ScreenDataSanitizer;
import com.vinuthna.paypalsaathi.detection.ScreenElement;
import com.vinuthna.paypalsaathi.detection.ScreenObservationBuilder;
import com.vinuthna.paypalsaathi.detection.ScreenState;
import com.vinuthna.paypalsaathi.detection.ScreenTransitionDetector;
import com.vinuthna.paypalsaathi.detection.OnDeviceScreenTextRecognizer;
import com.vinuthna.paypalsaathi.detection.VisualFallbackPolicy;
import com.vinuthna.paypalsaathi.tasks.GoalUnderstandingEngine;
import com.vinuthna.paypalsaathi.tasks.TaskGoal;
import com.vinuthna.paypalsaathi.tasks.TaskOrchestrator;
import com.vinuthna.paypalsaathi.tasks.TaskRecoveryManager;
import com.vinuthna.paypalsaathi.tasks.TaskState;
import com.vinuthna.paypalsaathi.tasks.TaskStateRepository;
import com.vinuthna.paypalsaathi.tasks.TaskStep;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Accessibility-based screen monitor for the official PayPal app.
 *
 * Privacy rules:
 * - Only processes the configured official PayPal package.
 * - Skips password fields and their descendants.
 * - Keeps sanitized element metadata transient in memory only.
 * - Never persists, logs, or uploads raw screen text.
 * - Broadcasts a category and allow-listed navigation labels, not screen text.
 * - Opens only an allow-listed navigation control after an explicit user tap.
 * - Never enters financial data or submits/authorizes payments.
 */
public class ScreenMonitorService extends AccessibilityService {
    private static final String TAG = "SaathiScreenMonitor";
    private static final String PAYPAL_PACKAGE =
            "com.paypal.android.p2pmobile";
    private static final int MAX_DEPTH = 18;
    private static final int MAX_NODES = 120;
    private static final int MAX_NAVIGATION_HINTS = 6;
    private static final long OBSERVATION_DEBOUNCE_MS = 180L;
    // Bound both missing-root recovery and weak-evidence stabilization equally.
    private static final long CATEGORY_UNCERTAINTY_GRACE_MS = 1_000L;
    private static final long VISUAL_FALLBACK_COOLDOWN_MS = 10_000L;

    private final CategoryEventGate categoryEventGate = new CategoryEventGate();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final RootUnavailableCategoryPolicy rootUnavailablePolicy =
            new RootUnavailableCategoryPolicy(CATEGORY_UNCERTAINTY_GRACE_MS);
    private final GoalUnderstandingEngine goalUnderstanding =
            new GoalUnderstandingEngine();
    private final TaskOrchestrator taskOrchestrator = new TaskOrchestrator();
    private final TaskRecoveryManager taskRecoveryManager =
            new TaskRecoveryManager();
    private final SafeActionPolicy safeActionPolicy = new SafeActionPolicy();
    private final VisualFallbackPolicy visualFallbackPolicy =
            new VisualFallbackPolicy();

    private FloatingAssistantOverlay overlay;
    private TaskStateRepository taskStateRepository;
    private HandlerThread observationThread;
    private Handler observationHandler;
    private Runnable pendingObservation;
    private int pendingObservationEventType;
    private boolean observationInFlight;
    private boolean observationRequested;
    private long lifecycleGeneration;
    private boolean paypalActive;
    private String currentCategory = ScreenCategories.UNKNOWN;
    private ScreenState lastScreenState;
    private ScreenTransitionDetector.Result lastTransition;
    private Runnable pendingUncertainCategory;
    private String lastExecutedActionKey;
    private long lastVisualAttemptElapsed;
    private OnDeviceScreenTextRecognizer visualTextRecognizer;

    @Override
    public void onCreate() {
        super.onCreate();
        com.vinuthna.paypalsaathi.GuidanceClient.initialize(
                getApplicationContext());
        SharedPreferences taskPreferences = getSharedPreferences(
                "saathi_task_state",
                MODE_PRIVATE);
        taskStateRepository = new TaskStateRepository(
                new TaskStateRepository.Storage() {
                    @Override
                    public String read() {
                        return taskPreferences.getString("active_task", null);
                    }

                    @Override
                    public void write(String value) {
                        taskPreferences.edit()
                                .putString("active_task", value)
                                .apply();
                    }

                    @Override
                    public void clear() {
                        taskPreferences.edit().remove("active_task").apply();
                    }
                });
        try {
            TaskState restoredTask = taskStateRepository.restore(
                    System.currentTimeMillis());
            if (restoredTask != null) {
                taskOrchestrator.restore(
                        restoredTask, System.currentTimeMillis());
                persistAndPublishTask();
            } else if (taskStateRepository.lastRestoreStatus()
                    == TaskStateRepository.RestoreStatus.EXPIRED) {
                taskOrchestrator.showMessage(taskRecoveryManager.recover(
                        TaskRecoveryManager.Problem.TASK_EXPIRED).message());
            } else if (taskStateRepository.lastRestoreStatus()
                    == TaskStateRepository.RestoreStatus.INVALID) {
                taskOrchestrator.showMessage(taskRecoveryManager.recover(
                        TaskRecoveryManager.Problem.RESTORATION_FAILURE).message());
            }
        } catch (IllegalStateException | SecurityException exception) {
            Log.e(TAG, "Could not restore task state ("
                    + exception.getClass().getSimpleName() + ").");
            taskOrchestrator.showMessage(taskRecoveryManager.recover(
                    TaskRecoveryManager.Problem.STORAGE_FAILURE).message());
        }
        observationThread = new HandlerThread("SaathiScreenObservation");
        observationThread.start();
        observationHandler = new Handler(observationThread.getLooper());
    }

    private final FloatingAssistantOverlay.TaskInteractionListener
            taskInteractionListener =
            new FloatingAssistantOverlay.TaskInteractionListener() {
                @Override
                public boolean onGoalSubmitted(String request) {
                    long now = System.currentTimeMillis();
                    GoalUnderstandingEngine.Result result =
                            taskOrchestrator.state().status()
                                    == TaskState.Status.NEEDS_CLARIFICATION
                                    ? goalUnderstanding.resolveClarification(request, now)
                                    : goalUnderstanding.understand(request, now);
                    if (!result.recognized()) {
                        if (taskOrchestrator.state().status()
                                == TaskState.Status.NEEDS_CLARIFICATION) {
                            persistAndPublishTask();
                            return true;
                        }
                        return false;
                    }

                    boolean started;
                    if (taskOrchestrator.state().status()
                            == TaskState.Status.NEEDS_CLARIFICATION
                            && !result.goal().clarificationRequired()) {
                        started = taskOrchestrator.resolveClarification(
                                result.goal(), now);
                    } else {
                        started = taskOrchestrator.start(
                                result.goal(),
                                result.clarification(),
                                now);
                    }
                    if (!started) {
                        taskOrchestrator.showMessage(
                                "A task is already in progress. Cancel it before starting another.");
                        persistAndPublishTask();
                        return true;
                    }
                    persistAndPublishTask();
                    if (lastScreenState != null) {
                        taskOrchestrator.observe(
                                lastScreenState,
                                lastTransition,
                                now);
                        persistAndPublishTask();
                    } else {
                        scheduleObservation(
                                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
                    }
                    return true;
                }

                @Override
                public void onPauseTask() {
                    taskOrchestrator.pause(System.currentTimeMillis());
                    persistAndPublishTask();
                }

                @Override
                public void onResumeTask() {
                    taskOrchestrator.resume(System.currentTimeMillis());
                    persistAndPublishTask();
                    if (paypalActive) {
                        scheduleObservation(
                                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
                    }
                }

                @Override
                public void onCancelTask() {
                    taskOrchestrator.cancel(System.currentTimeMillis());
                    persistAndPublishTask();
                }

                @Override
                public void onConfirmTaskComplete() {
                    taskOrchestrator.confirmGuidanceComplete(
                            System.currentTimeMillis());
                    persistAndPublishTask();
                }

                @Override
                public void onExecuteSafeAction() {
                    executeSafeAction();
                }

                @Override
                public void onRepeatTask() {
                    taskOrchestrator.repeatInstruction();
                    persistAndPublishTask();
                }
            };

    private void persistAndPublishTask() {
        TaskState taskState = taskOrchestrator.state();
        if (taskStateRepository != null) {
            try {
                taskStateRepository.save(taskState);
            } catch (IllegalStateException | SecurityException exception) {
                Log.e(TAG, "Could not persist task state ("
                        + exception.getClass().getSimpleName() + ").");
                taskOrchestrator.showMessage(taskRecoveryManager.recover(
                        TaskRecoveryManager.Problem.STORAGE_FAILURE).message());
            }
        }
        if (overlay != null) {
            overlay.updateTask(
                    taskState,
                    taskOrchestrator.currentStep(),
                    taskOrchestrator.currentMessage());
        }
    }

    private void executeSafeAction() {
        long now = System.currentTimeMillis();
        TaskState task = taskOrchestrator.state();
        TaskStep step = taskOrchestrator.currentStep();
        SafeActionPolicy.Decision decision = safeActionPolicy.evaluate(
                foregroundApplicationPackage(),
                task,
                step,
                lastScreenState,
                now,
                lastExecutedActionKey);
        if (!decision.allowed() || !paypalActive || observationHandler == null) {
            rejectSafeAction(
                    "That navigation is no longer verified. Please use PayPal directly.");
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null
                || root.getPackageName() == null
                || !PAYPAL_PACKAGE.contentEquals(root.getPackageName())) {
            if (root != null) {
                root.recycle();
            }
            rejectSafeAction(
                    "PayPal is not the active screen. Please check it before continuing.");
            return;
        }

        final ScreenState observedScreen = lastScreenState;
        final TaskState observedTask = task;
        final TaskStep observedStep = step;
        final String priorExecutionKey = lastExecutedActionKey;
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(
                WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }

        boolean posted = observationHandler.post(() -> {
            AccessibilityNodeInfo actionNode = null;
            boolean retainedForMainThread = false;
            try {
                List<ObservedUiElement> observations = new ArrayList<>();
                collectObservation(
                        root,
                        observations,
                        new int[] {0},
                        metrics.widthPixels,
                        metrics.heightPixels,
                        0);
                ScreenState liveScreen = ScreenObservationBuilder.build(
                        observations,
                        PAYPAL_PACKAGE,
                        System.currentTimeMillis());
                if (!observedScreen.screenIdentifier().equals(
                        liveScreen.screenIdentifier())) {
                    mainHandler.post(() -> rejectSafeAction(
                            "The PayPal screen changed. I will check it again before continuing."));
                    return;
                }
                actionNode = findSafeActionNode(
                        root,
                        observedStep.recommendedAction(),
                        liveScreen.category(),
                        metrics.widthPixels,
                        metrics.heightPixels,
                        new int[] {0},
                        0);
                if (actionNode == null) {
                    mainHandler.post(() -> rejectSafeAction(
                            "I can no longer find that enabled navigation control. Please use PayPal directly."));
                    return;
                }
                AccessibilityNodeInfo selectedNode = actionNode;
                retainedForMainThread = true;
                mainHandler.post(() -> performVerifiedSafeAction(
                        root,
                        selectedNode,
                        liveScreen,
                        observedTask,
                        observedStep,
                        priorExecutionKey));
            } catch (IllegalStateException exception) {
                Log.w(TAG, "Safe navigation observation became unavailable ("
                        + exception.getClass().getSimpleName() + ").");
                mainHandler.post(() -> rejectSafeAction(
                        "That navigation is no longer available. Please use PayPal directly."));
            } finally {
                if (!retainedForMainThread) {
                    if (actionNode != null && actionNode != root) {
                        actionNode.recycle();
                    }
                    root.recycle();
                }
            }
        });
        if (!posted) {
            root.recycle();
            rejectSafeAction(
                    "Screen monitoring is unavailable. Please use PayPal directly.");
        }
    }

    private void performVerifiedSafeAction(
            AccessibilityNodeInfo root,
            AccessibilityNodeInfo actionNode,
            ScreenState liveScreen,
            TaskState observedTask,
            TaskStep observedStep,
            String priorExecutionKey
    ) {
        try {
            TaskState currentTask = taskOrchestrator.state();
            TaskStep currentStep = taskOrchestrator.currentStep();
            SafeActionPolicy.Decision decision = safeActionPolicy.evaluate(
                    foregroundApplicationPackage(),
                    currentTask,
                    currentStep,
                    liveScreen,
                    System.currentTimeMillis(),
                    lastExecutedActionKey);
            boolean taskUnchanged = currentTask.goal() != null
                    && observedTask.goal() != null
                    && currentTask.goal().taskId().equals(
                            observedTask.goal().taskId())
                    && currentStep != null
                    && observedStep.stepId().equals(currentStep.stepId());
            if (!paypalActive
                    || !taskUnchanged
                    || lastScreenState == null
                    || !liveScreen.screenIdentifier().equals(
                            lastScreenState.screenIdentifier())
                    || !decision.allowed()
                    || !java.util.Objects.equals(
                            priorExecutionKey,
                            lastExecutedActionKey)) {
                rejectSafeAction(
                        "That navigation is no longer verified. Please use PayPal directly.");
                return;
            }

            lastExecutedActionKey = decision.executionKey();
            boolean executed = actionNode.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK);
            if (executed) {
                taskOrchestrator.showMessage(
                        "I opened the navigation control you selected. I will check what screen appears.");
                scheduleObservation(
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            } else {
                taskOrchestrator.showMessage(
                        "PayPal did not accept that navigation action. I will not retry it automatically.");
            }
            persistAndPublishTask();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Safe navigation became unavailable ("
                    + exception.getClass().getSimpleName() + ").");
            rejectSafeAction(
                    "That navigation is no longer available. Please use PayPal directly.");
        } finally {
            if (actionNode != null && actionNode != root) {
                actionNode.recycle();
            }
            root.recycle();
        }
    }

    private void rejectSafeAction(String message) {
        taskOrchestrator.showMessage(message);
        persistAndPublishTask();
    }

    private AccessibilityNodeInfo findSafeActionNode(
            AccessibilityNodeInfo node,
            String expectedDescriptor,
            String screenCategory,
            int screenWidth,
            int screenHeight,
            int[] visited,
            int depth
    ) {
        if (node == null
                || depth > MAX_DEPTH
                || visited[0] >= MAX_NODES
                || node.getPackageName() == null
                || !PAYPAL_PACKAGE.contentEquals(node.getPackageName())) {
            return null;
        }
        visited[0]++;

        CharSequence rawDescription = node.getContentDescription();
        CharSequence rawHint = node.isEditable() ? node.getHintText() : null;
        String className = node.getClassName() == null
                ? ""
                : node.getClassName().toString();
        boolean sensitive = ScreenDataSanitizer.isSensitiveField(
                node.isPassword() || isSensitiveInputType(node.getInputType()),
                node.getViewIdResourceName(),
                className,
                rawHint == null ? null : rawHint.toString(),
                rawDescription == null ? null : rawDescription.toString());

        if (!sensitive && !node.isEditable() && node.isVisibleToUser()
                && node.isEnabled()
                && node.isClickable()
                && isNavigationControlRole(className)
                && hasClickAction(node)) {
            CharSequence rawText = node.getText();
            String label = ScreenContext.safeNavigationLabel(rawText);
            if (label == null) {
                label = ScreenContext.safeNavigationLabel(rawDescription);
            }
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            String descriptor = ScreenContext.safeActionDescriptor(
                    label,
                    true,
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    screenWidth,
                    screenHeight);
            if (expectedDescriptor.equals(descriptor)
                    && SafeActionPolicy.isActionAllowed(label, screenCategory)) {
                return node;
            }
        }

        if (node.isEditable() || sensitive) {
            return null;
        }
        for (int i = 0; i < node.getChildCount() && visited[0] < MAX_NODES; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo found = findSafeActionNode(
                    child,
                    expectedDescriptor,
                    screenCategory,
                    screenWidth,
                    screenHeight,
                    visited,
                    depth + 1);
            if (found != null) {
                if (found != child) {
                    child.recycle();
                }
                return found;
            }
            child.recycle();
        }
        return null;
    }

    private boolean hasClickAction(AccessibilityNodeInfo node) {
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            if (action.getId() == AccessibilityNodeInfo.ACTION_CLICK) {
                return true;
            }
        }
        return false;
    }

    private boolean isNavigationControlRole(String className) {
        String normalized = className.toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith("button")
                || normalized.endsWith("tab")
                || normalized.endsWith("tabview");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName = event.getPackageName().toString();
        int eventType = event.getEventType();

        if (!isWindowTransitionEvent(eventType)
                && !PAYPAL_PACKAGE.equals(packageName)) {
            return;
        }

        String foregroundPackage = foregroundApplicationPackage();
        OverlayForegroundPolicy.Decision decision =
                OverlayForegroundPolicy.decide(
                        packageName,
                        foregroundPackage,
                        PAYPAL_PACKAGE,
                        getPackageName());
        if (decision == OverlayForegroundPolicy.Decision.IGNORE) {
            return;
        }
        if (decision == OverlayForegroundPolicy.Decision.HIDE) {
            Log.i(TAG, "PayPal is no longer foreground; hiding assistant.");
            leavePayPal();
            return;
        }

        enterPayPal();

        if (!PAYPAL_PACKAGE.equals(packageName)) {
            return;
        }

        scheduleObservation(eventType);
    }

    private void scheduleObservation(int eventType) {
        pendingObservationEventType = eventType;
        if (pendingObservation != null) {
            mainHandler.removeCallbacks(pendingObservation);
        }
        Runnable observation = () -> {
            pendingObservation = null;
            startObservation(pendingObservationEventType);
        };
        pendingObservation = observation;
        mainHandler.postDelayed(observation, OBSERVATION_DEBOUNCE_MS);
    }

    private void startObservation(int eventType) {
        if (!paypalActive || observationHandler == null) {
            return;
        }
        if (observationInFlight) {
            observationRequested = true;
            return;
        }
        observationInFlight = true;
        observationRequested = false;

        DisplayMetrics screenMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(
                WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(screenMetrics);
        } else {
            screenMetrics.setTo(getResources().getDisplayMetrics());
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean rootPackageMatches = root != null
                && root.getPackageName() != null
                && PAYPAL_PACKAGE.contentEquals(root.getPackageName());
        if (root == null || !rootPackageMatches) {
            if (root != null) {
                root.recycle();
            }
            handleUnavailableRoot(eventType, root != null, rootPackageMatches);
            finishObservation();
            return;
        }

        long observedAt = System.currentTimeMillis();
        long generation = lifecycleGeneration;
        ScreenState previousScreenState = lastScreenState;
        int screenWidth = screenMetrics.widthPixels;
        int screenHeight = screenMetrics.heightPixels;
        boolean posted = observationHandler.post(() -> {
            ScreenState screenState;
            List<ObservedUiElement> observations = new ArrayList<>();
            try {
                collectObservation(
                        root,
                        observations,
                        new int[] {0},
                        screenWidth,
                        screenHeight,
                        0);
                screenState = ScreenObservationBuilder.build(
                        observations,
                        PAYPAL_PACKAGE,
                        observedAt);
            } catch (IllegalStateException exception) {
                Log.w(TAG, "Accessibility observation became unavailable ("
                        + exception.getClass().getSimpleName() + ").");
                screenState = ScreenObservationBuilder.unavailable(observedAt);
            } finally {
                root.recycle();
            }

            ScreenState result = screenState;
            mainHandler.post(() -> {
                boolean newerObservationPending = pendingObservation != null
                        || observationRequested;
                if (generation == lifecycleGeneration
                        && paypalActive
                        && !newerObservationPending) {
                    if (shouldAttemptVisualFallback(result)) {
                        requestVisualFallback(
                                result,
                                previousScreenState,
                                eventType,
                                generation);
                        return;
                    }
                    handleScreenObservation(
                            result,
                            previousScreenState,
                            eventType,
                            observedAt);
                }
                finishObservation();
            });
        });
        if (!posted) {
            root.recycle();
            Log.e(TAG, "Could not queue accessibility screen observation.");
            finishObservation();
        }
    }

    private void handleUnavailableRoot(
            int eventType,
            boolean rootAvailable,
            boolean rootPackageMatches
    ) {
        long elapsedRealtime = SystemClock.elapsedRealtime();
        if (rootUnavailablePolicy.shouldPreserve(
                currentCategory,
                rootAvailable,
                rootPackageMatches,
                elapsedRealtime)) {
            long observedAt = System.currentTimeMillis();
            lastTransition = ScreenTransitionDetector.detect(
                    lastScreenState,
                    ScreenObservationBuilder.unavailable(observedAt),
                    observedAt);
            if (overlay != null) {
                overlay.markScreenContextUncertain();
            }
            scheduleUncertainCategoryExpiry(eventType);
            return;
        }
        resetCategoryUncertainty();
        long observedAt = System.currentTimeMillis();
        ScreenState unavailable = ScreenObservationBuilder.unavailable(observedAt);
        lastTransition = ScreenTransitionDetector.detect(
                lastScreenState,
                unavailable,
                observedAt);
        lastScreenState = unavailable;
        taskOrchestrator.observe(unavailable, lastTransition, observedAt);
        persistAndPublishTask();
        publishClassification(
                ScreenClassification.unknown(
                        false,
                        "insufficient_accessibility"),
                new ArrayList<>(),
                eventTypeLabel(eventType, lastTransition),
                observedAt);
    }

    private void handleScreenObservation(
            ScreenState screenState,
            ScreenState previousScreenState,
            int eventType,
            long observedAt
    ) {
        if (!screenState.observationAvailable()) {
            lastTransition = ScreenTransitionDetector.detect(
                    previousScreenState,
                    screenState,
                    observedAt);
            taskOrchestrator.observe(screenState, lastTransition, observedAt);
            persistAndPublishTask();
            if (overlay != null) {
                overlay.markScreenContextUncertain();
            }
            scheduleUncertainCategoryExpiry(eventType);
            return;
        }
        handleAvailableScreenObservation(
                screenState,
                previousScreenState,
                eventType,
                observedAt);
    }

    private boolean shouldAttemptVisualFallback(ScreenState screenState) {
                long elapsed = SystemClock.elapsedRealtime();
                if (elapsed - lastVisualAttemptElapsed
                        < VISUAL_FALLBACK_COOLDOWN_MS) {
                    return false;
                }
                boolean consented = getSharedPreferences(
                        "saathi_privacy",
                        MODE_PRIVATE).getBoolean("visual_ocr_consent", false);
                return visualFallbackPolicy.shouldCapture(
                        consented,
                        Build.VERSION.SDK_INT,
                        paypalActive,
                        screenState);
            }

            @android.annotation.TargetApi(Build.VERSION_CODES.R)
            private void requestVisualFallback(
                    ScreenState accessibilityState,
                    ScreenState previousScreenState,
                    int eventType,
                    long generation
            ) {
                lastVisualAttemptElapsed = SystemClock.elapsedRealtime();
                Executor screenshotExecutor = command -> {
                    if (!mainHandler.post(command)) {
                        Log.w(TAG, "Could not queue local screenshot callback.");
                        finishObservation();
                    }
                };
                try {
                    takeScreenshot(
                            Display.DEFAULT_DISPLAY,
                            screenshotExecutor,
                            new TakeScreenshotCallback() {
                                @Override
                                public void onSuccess(ScreenshotResult result) {
                                    processVisualFallback(
                                            result,
                                            accessibilityState,
                                            previousScreenState,
                                            eventType,
                                            generation);
                                }

                                @Override
                                public void onFailure(int errorCode) {
                                    Log.i(TAG, "Local screenshot unavailable (code="
                                            + errorCode + ").");
                                    finishScreenObservation(
                                            accessibilityState,
                                            previousScreenState,
                                            eventType,
                                            accessibilityState.observedAt(),
                                            generation);
                                }
                            });
                } catch (IllegalStateException | SecurityException exception) {
                    Log.i(TAG, "Local screenshot request unavailable ("
                            + exception.getClass().getSimpleName() + ").");
                    finishScreenObservation(
                            accessibilityState,
                            previousScreenState,
                            eventType,
                            accessibilityState.observedAt(),
                            generation);
                }
            }

            @android.annotation.TargetApi(Build.VERSION_CODES.R)
            private void processVisualFallback(
                    ScreenshotResult result,
                    ScreenState accessibilityState,
                    ScreenState previousScreenState,
                    int eventType,
                    long generation
            ) {
                HardwareBuffer hardwareBuffer = result == null
                        ? null : result.getHardwareBuffer();
                Bitmap hardwareBitmap = null;
                Bitmap bitmap = null;
                try {
                    if (hardwareBuffer != null) {
                        hardwareBitmap = Bitmap.wrapHardwareBuffer(
                                hardwareBuffer,
                                result.getColorSpace());
                        if (hardwareBitmap != null) {
                            bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, true);
                        }
                    }
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    Log.i(TAG, "Local screenshot image unavailable ("
                            + exception.getClass().getSimpleName() + ").");
                } finally {
                    if (hardwareBitmap != null) {
                        hardwareBitmap.recycle();
                    }
                    if (hardwareBuffer != null) {
                        hardwareBuffer.close();
                    }
                }
                if (bitmap == null) {
                    finishScreenObservation(
                            accessibilityState,
                            previousScreenState,
                            eventType,
                            accessibilityState.observedAt(),
                            generation);
                    return;
                }

                if (visualTextRecognizer == null) {
                    visualTextRecognizer = new OnDeviceScreenTextRecognizer();
                }
                Bitmap capturedBitmap = bitmap;
                try {
                    visualTextRecognizer.recognize(
                            capturedBitmap,
                            command -> {
                                if (observationHandler == null
                                        || !observationHandler.post(command)) {
                                    releaseScreenshotBitmap(capturedBitmap);
                                    mainHandler.post(() -> finishObservation());
                                }
                            },
                            new OnDeviceScreenTextRecognizer.Callback() {
                                @Override
                                public void onSuccess(String safeScreenHints) {
                                    releaseScreenshotBitmap(capturedBitmap);
                                    completeVisualFallback(
                                            accessibilityState,
                                            previousScreenState,
                                            eventType,
                                            generation,
                                            safeScreenHints);
                                }

                                @Override
                                public void onFailure() {
                                    releaseScreenshotBitmap(capturedBitmap);
                                    Log.i(TAG, "On-device text recognition unavailable.");
                                    mainHandler.post(() -> finishScreenObservation(
                                            accessibilityState,
                                            previousScreenState,
                                            eventType,
                                            accessibilityState.observedAt(),
                                            generation));
                                }
                            });
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    releaseScreenshotBitmap(capturedBitmap);
                    Log.w(TAG, "On-device text recognition could not start ("
                            + exception.getClass().getSimpleName() + ").");
                    finishScreenObservation(
                            accessibilityState,
                            previousScreenState,
                            eventType,
                            accessibilityState.observedAt(),
                            generation);
                }
            }

            private void completeVisualFallback(
                    ScreenState accessibilityState,
                    ScreenState previousScreenState,
                    int eventType,
                    long generation,
                    String safeScreenHints
            ) {
                mainHandler.post(() -> {
                    if (generation != lifecycleGeneration || !paypalActive) {
                        finishObservation();
                        return;
                    }
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root == null || root.getPackageName() == null
                            || !PAYPAL_PACKAGE.contentEquals(root.getPackageName())) {
                        if (root != null) {
                            root.recycle();
                        }
                        finishScreenObservation(
                                accessibilityState,
                                previousScreenState,
                                eventType,
                                accessibilityState.observedAt(),
                                generation);
                        return;
                    }
                    DisplayMetrics metrics = new DisplayMetrics();
                    WindowManager windowManager = (WindowManager) getSystemService(
                            WINDOW_SERVICE);
                    if (windowManager != null) {
                        windowManager.getDefaultDisplay().getRealMetrics(metrics);
                    } else {
                        metrics.setTo(getResources().getDisplayMetrics());
                    }
                    int screenWidth = metrics.widthPixels;
                    int screenHeight = metrics.heightPixels;
                    long observedAt = System.currentTimeMillis();
                    boolean posted = observationHandler != null
                            && observationHandler.post(() -> {
                                ScreenState freshAccessibilityState;
                                ScreenState combinedState;
                                List<ObservedUiElement> observations =
                                        new ArrayList<>();
                                try {
                                    collectObservation(
                                            root,
                                            observations,
                                            new int[] {0},
                                            screenWidth,
                                            screenHeight,
                                            0);
                                    freshAccessibilityState =
                                            ScreenObservationBuilder.build(
                                                    observations,
                                                    PAYPAL_PACKAGE,
                                                    observedAt);
                                    combinedState =
                                            accessibilityState.screenIdentifier()
                                                    .equals(freshAccessibilityState
                                                            .screenIdentifier())
                                            ? ScreenObservationBuilder.build(
                                                    observations,
                                                    PAYPAL_PACKAGE,
                                                    observedAt,
                                                    safeScreenHints)
                                            : freshAccessibilityState;
                                } catch (IllegalStateException exception) {
                                    Log.w(TAG, "Fresh screen check after OCR failed ("
                                            + exception.getClass().getSimpleName()
                                            + ").");
                                    combinedState =
                                            ScreenObservationBuilder.unavailable(
                                                    observedAt);
                                } finally {
                                    root.recycle();
                                }
                                ScreenState result = combinedState;
                                mainHandler.post(() -> finishScreenObservation(
                                        result,
                                        previousScreenState,
                                        eventType,
                                        observedAt,
                                        generation));
                            });
                    if (!posted) {
                        root.recycle();
                        finishScreenObservation(
                                accessibilityState,
                                previousScreenState,
                                eventType,
                                accessibilityState.observedAt(),
                                generation);
                    }
                });
            }

            private void finishScreenObservation(
                    ScreenState screenState,
                    ScreenState previousScreenState,
                    int eventType,
                    long observedAt,
                    long generation
            ) {
                if (generation == lifecycleGeneration && paypalActive) {
                    boolean newerObservationPending = pendingObservation != null
                            || observationRequested;
                    if (!newerObservationPending) {
                        handleScreenObservation(
                                screenState,
                                previousScreenState,
                                eventType,
                                observedAt);
                    }
                }
                finishObservation();
            }

            private static void releaseScreenshotBitmap(Bitmap bitmap) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.eraseColor(Color.TRANSPARENT);
                    bitmap.recycle();
                }
    }

    private void handleAvailableScreenObservation(
            ScreenState screenState,
            ScreenState previousScreenState,
            int eventType,
            long observedAt
    ) {
        lastTransition = ScreenTransitionDetector.detect(
                previousScreenState,
                screenState,
                observedAt);
        lastScreenState = screenState;
        ScreenClassification classification = new ScreenClassification(
                screenState.category(),
                screenState.confidence(),
                screenState.evidenceStrength(),
                screenState.ambiguous(),
                screenState.evidenceCodes(),
                screenState.safeActionDescriptors());
        List<String> safeNavigationHints = navigationHints(screenState);

        if (ScreenCategories.UNKNOWN.equals(classification.category())
                && rootUnavailablePolicy.shouldPreserveUncertainCategory(
                        currentCategory,
                        SystemClock.elapsedRealtime())) {
            if (overlay != null) {
                overlay.markScreenContextUncertain();
            }
            scheduleUncertainCategoryExpiry(eventType);
            return;
        }
        resetCategoryUncertainty();
        taskOrchestrator.observe(screenState, lastTransition, observedAt);
        persistAndPublishTask();

        publishClassification(
                classification,
                safeNavigationHints,
                eventTypeLabel(eventType, lastTransition),
                observedAt);
    }

    private void finishObservation() {
        observationInFlight = false;
        if (observationRequested && paypalActive) {
            observationRequested = false;
            startObservation(pendingObservationEventType);
        }
    }

    private void publishClassification(
            ScreenClassification classification,
            List<String> safeNavigationHints,
            String eventLabel,
            long observedAt
    ) {
        String category = classification.category();
        List<String> safeActionableElements =
                classification.actionableElements();
        boolean categoryChanged = !category.equals(currentCategory);
        currentCategory = category;
        if (overlay != null) {
            overlay.updateScreenContext(
                    category,
                    safeNavigationHints,
                    safeActionableElements,
                    classification.confidence(),
                    classification.evidenceStrength(),
                    classification.ambiguous(),
                    classification.evidenceCodes(),
                    observedAt);
        }

        if (!categoryEventGate.shouldEmit(
                category,
                safeNavigationHints,
                safeActionableElements,
                SystemClock.elapsedRealtime())) {
            return;
        }

        CategoryStore.save(
                this,
                category,
                eventLabel,
                classification.confidence(),
                classification.evidenceStrength(),
                classification.ambiguous());

        Intent intent = new Intent(ScreenMonitorContract.ACTION_SCREEN_CATEGORY);
        intent.setPackage(getPackageName());
        intent.putExtra(ScreenMonitorContract.EXTRA_CATEGORY, category);
        intent.putExtra(ScreenMonitorContract.EXTRA_EVENT_TYPE, eventLabel);
        intent.putExtra(ScreenMonitorContract.EXTRA_PACKAGE, "PayPal app");
        intent.putExtra(
                ScreenMonitorContract.EXTRA_CATEGORY_CHANGED,
                categoryChanged);
        intent.putExtra(ScreenMonitorContract.EXTRA_PAYPAL_ACTIVE, true);
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_VISIBLE_LABELS,
                new ArrayList<>(safeNavigationHints));
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_ACTIONABLE_ELEMENTS,
                new ArrayList<>(safeActionableElements));
        intent.putExtra(
                ScreenMonitorContract.EXTRA_CONFIDENCE,
                classification.confidence());
        intent.putExtra(
                ScreenMonitorContract.EXTRA_EVIDENCE_STRENGTH,
                classification.evidenceStrength());
        intent.putExtra(
                ScreenMonitorContract.EXTRA_AMBIGUOUS,
                classification.ambiguous());
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_EVIDENCE_CODES,
                new ArrayList<>(classification.evidenceCodes()));
        intent.putExtra(ScreenMonitorContract.EXTRA_OBSERVED_AT, observedAt);
        sendBroadcast(intent);
    }

    private void scheduleUncertainCategoryExpiry(int eventType) {
        if (pendingUncertainCategory != null) {
            return;
        }

        long remainingMs = rootUnavailablePolicy.remainingMs(
                SystemClock.elapsedRealtime());
        Runnable expiry = new Runnable() {
            @Override
            public void run() {
                if (pendingUncertainCategory != this) {
                    return;
                }
                pendingUncertainCategory = null;
                if (!paypalActive
                        || !ScreenCategories.isKnownCategory(currentCategory)
                        || ScreenCategories.UNKNOWN.equals(currentCategory)) {
                    return;
                }
                if (rootUnavailablePolicy.shouldPreserve(
                        currentCategory,
                        false,
                        false,
                        SystemClock.elapsedRealtime())) {
                    scheduleUncertainCategoryExpiry(eventType);
                    return;
                }

                long observedAt = System.currentTimeMillis();
                ScreenState unavailable =
                        ScreenObservationBuilder.unavailable(observedAt);
                lastTransition = ScreenTransitionDetector.detect(
                        lastScreenState,
                        unavailable,
                        observedAt);
                lastScreenState = unavailable;
                taskOrchestrator.observe(
                        unavailable,
                        lastTransition,
                        observedAt);
                persistAndPublishTask();
                publishClassification(
                        ScreenClassification.unknown(
                                false,
                                "insufficient_accessibility"),
                        new ArrayList<>(),
                        eventTypeLabel(eventType, lastTransition),
                        observedAt);
            }
        };
        pendingUncertainCategory = expiry;
        mainHandler.postDelayed(expiry, remainingMs);
    }

    private void resetCategoryUncertainty() {
        if (pendingUncertainCategory != null) {
            mainHandler.removeCallbacks(pendingUncertainCategory);
            pendingUncertainCategory = null;
        }
        rootUnavailablePolicy.reset();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility service connected.");
        overlay = new FloatingAssistantOverlay(this, taskInteractionListener);
        persistAndPublishTask();
        boolean paypalIsForeground = PAYPAL_PACKAGE.equals(
                foregroundApplicationPackage());
        Log.i(TAG, paypalIsForeground
                ? "PayPal is foreground at service connection."
                : "PayPal is not foreground at service connection.");
        if (paypalIsForeground) {
            enterPayPal();
        }
    }

    private void collectObservation(
            AccessibilityNodeInfo node,
            List<ObservedUiElement> observations,
            int[] visitedNodes,
            int screenWidth,
            int screenHeight,
            int depth
    ) {
        if (node == null
                || depth > MAX_DEPTH
                || visitedNodes[0] >= MAX_NODES
                || !node.isVisibleToUser()) {
            return;
        }
        visitedNodes[0]++;

        CharSequence rawHint = node.isEditable() ? node.getHintText() : null;
        CharSequence rawDescription = node.getContentDescription();
        String className = node.getClassName() == null
                ? ""
                : node.getClassName().toString();
        String resourceId = node.getViewIdResourceName();
        boolean sensitive = ScreenDataSanitizer.isSensitiveField(
                node.isPassword() || isSensitiveInputType(node.getInputType()),
                resourceId,
                className,
                rawHint == null ? null : rawHint.toString(),
                rawDescription == null ? null : rawDescription.toString());

        if (node.isPassword()) {
            return;
        }

        boolean editable = node.isEditable();
        CharSequence rawText = !editable && !sensitive ? node.getText() : null;
        String safeText = rawText != null
                ? ScreenDataSanitizer.sanitizeLabel(rawText.toString())
                : null;
        String safeDescription = !sensitive && rawDescription != null
                ? ScreenDataSanitizer.sanitizeLabel(rawDescription.toString())
                : null;
        String safeHint = editable && !sensitive && rawHint != null
                ? ScreenDataSanitizer.sanitizeLabel(rawHint.toString())
                : null;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        observations.add(new ObservedUiElement(
                safeText,
                safeDescription,
                safeHint,
                className,
                node.isClickable(),
                node.isEnabled(),
                node.isFocused(),
                editable,
                sensitive,
                fieldType(node.getInputType(), sensitive),
                supportedActions(node),
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                screenWidth,
                screenHeight));

        if (editable && sensitive) {
            return;
        }

        for (int i = 0;
             i < node.getChildCount()
                     && visitedNodes[0] < MAX_NODES;
             i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child != null) {
                try {
                    collectObservation(
                            child,
                            observations,
                            visitedNodes,
                            screenWidth,
                            screenHeight,
                            depth + 1);
                } finally {
                    child.recycle();
                }
            }
        }
    }

    private List<String> navigationHints(ScreenState state) {
        List<String> labels = new ArrayList<>();
        for (ScreenElement element : state.elements()) {
            String label = ScreenContext.safeNavigationLabel(element.label());
            if (label != null
                    && labels.size() < MAX_NAVIGATION_HINTS
                    && !labels.contains(label)) {
                labels.add(label);
            }
        }
        return labels;
    }

    private List<String> supportedActions(AccessibilityNodeInfo node) {
        List<String> actions = new ArrayList<>();
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            int actionId = action.getId();
            if (actionId == AccessibilityNodeInfo.ACTION_CLICK) {
                actions.add("tap");
            } else if (actionId == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                    || actionId == AccessibilityNodeInfo.ACTION_FOCUS) {
                actions.add("focus");
            } else if (actionId == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                actions.add("scroll_forward");
            } else if (actionId == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                actions.add("scroll_backward");
            }
        }
        return actions;
    }

    private boolean isSensitiveInputType(int inputType) {
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return (inputClass == InputType.TYPE_CLASS_TEXT
                    && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                        || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
                || (inputClass == InputType.TYPE_CLASS_NUMBER
                    && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private String fieldType(int inputType, boolean sensitive) {
        if (sensitive) {
            return "sensitive";
        }
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            return "number";
        }
        if (inputClass == InputType.TYPE_CLASS_PHONE) {
            return "phone";
        }
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            int variation = inputType & InputType.TYPE_MASK_VARIATION;
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
                return "email";
            }
            return "text";
        }
        return "unknown";
    }

    private String eventTypeLabel(
            int type,
            ScreenTransitionDetector.Result transition
    ) {
        if (transition != null) {
            switch (transition.type()) {
                case SCREEN_CHANGED:
                    return "Screen changed";
                case SCREEN_UNCHANGED:
                    return "Screen unchanged";
                case SCREEN_PARTIALLY_UPDATED:
                    return "Screen updated";
                case LOADING_STARTED:
                    return "Loading started";
                case LOADING_FINISHED:
                    return "Loading finished";
                case OBSERVATION_UNAVAILABLE:
                    return "Screen unavailable";
                case STALE_OBSERVATION:
                    return "Stale screen observation";
                case UNKNOWN_TRANSITION:
                    return "Screen uncertain";
                default:
                    break;
            }
        }
        switch (type) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                return "Window changed";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                return "Content changed";
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                return "View focused";
            default:
                return "UI event";
        }
    }

    private boolean isWindowTransitionEvent(int type) {
        return type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
    }

    private String foregroundApplicationPackage() {
        List<AccessibilityWindowInfo> windows = getWindows();
        int topLayer = Integer.MIN_VALUE;
        String topApplicationPackage = null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                    && window.getLayer() > topLayer) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) {
                    try {
                        CharSequence packageName = root.getPackageName();
                        topApplicationPackage =
                                packageName == null ? null : packageName.toString();
                        topLayer = window.getLayer();
                    } finally {
                        root.recycle();
                    }
                }
            }
            window.recycle();
        }
        return topApplicationPackage;
    }

    private void enterPayPal() {
        if (!paypalActive) {
            paypalActive = true;
            lifecycleGeneration++;
            currentCategory = ScreenCategories.UNKNOWN;
            lastScreenState = null;
            lastTransition = null;
            categoryEventGate.reset();
            resetCategoryUncertainty();
            CategoryStore.clear(this);
            Log.i(TAG, "Official PayPal package became active.");
        }

        if (overlay != null) {
            overlay.show();
            persistAndPublishTask();
        }
    }

    private void leavePayPal() {
        if (!paypalActive) {
            return;
        }

        paypalActive = false;
        lifecycleGeneration++;
        if (pendingObservation != null) {
            mainHandler.removeCallbacks(pendingObservation);
            pendingObservation = null;
        }
        observationRequested = false;
        Log.i(TAG, "Clearing PayPal screen context.");
        taskOrchestrator.pause(System.currentTimeMillis());
        persistAndPublishTask();
        currentCategory = ScreenCategories.UNKNOWN;
        lastScreenState = null;
        lastTransition = null;
        categoryEventGate.reset();
        resetCategoryUncertainty();
        CategoryStore.clear(this);
        if (overlay != null) {
            overlay.hide();
        }

        Intent intent = new Intent(ScreenMonitorContract.ACTION_SCREEN_CATEGORY);
        intent.setPackage(getPackageName());
        intent.putExtra(
                ScreenMonitorContract.EXTRA_CATEGORY,
                ScreenCategories.UNKNOWN);
        intent.putExtra(
                ScreenMonitorContract.EXTRA_EVENT_TYPE,
                getString(R.string.floating_assistant_no_paypal_context));
        intent.putExtra(
                ScreenMonitorContract.EXTRA_CATEGORY_CHANGED,
                true);
        intent.putExtra(ScreenMonitorContract.EXTRA_PAYPAL_ACTIVE, false);
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_VISIBLE_LABELS,
                new ArrayList<>());
        intent.putStringArrayListExtra(
                ScreenMonitorContract.EXTRA_ACTIONABLE_ELEMENTS,
                new ArrayList<>());
        intent.putExtra(ScreenMonitorContract.EXTRA_OBSERVED_AT, 0L);
        sendBroadcast(intent);
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.");
        leavePayPal();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Accessibility service destroyed.");
        leavePayPal();
        if (visualTextRecognizer != null) {
            visualTextRecognizer.close();
            visualTextRecognizer = null;
        }
        lifecycleGeneration++;
        if (pendingObservation != null) {
            mainHandler.removeCallbacks(pendingObservation);
            pendingObservation = null;
        }
        if (observationThread != null) {
            observationThread.quitSafely();
            observationThread = null;
            observationHandler = null;
        }
        if (overlay != null) {
            overlay.hide();
        }
        super.onDestroy();
    }
}
