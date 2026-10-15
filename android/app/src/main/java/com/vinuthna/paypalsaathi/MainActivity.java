package com.vinuthna.paypalsaathi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private TextView serviceStatus;
    private TextView backendStatus;
    private TextView currentCategory;
    private TextView guidanceTitle;
    private TextView guidanceSteps;
    private TextView eventLog;
    private TextView questionResult;
    private TextView emptyState;
    private EditText questionInput;
    private ProgressBar guidanceLoading;
    private ProgressBar questionLoading;
    private Button refreshButton;
    private Button askButton;
    private Button retryButton;

    private String lastCategory = ScreenCategories.UNKNOWN;
    private boolean receiverRegistered = false;
    private boolean guidanceRequestInFlight = false;
    private int guidanceRequestId;
    private final HealthRequestGate healthRequestGate = new HealthRequestGate();
    private final QuestionRequestTracker questionRequestTracker =
            new QuestionRequestTracker();

    private final ArrayDeque<String> recentEvents = new ArrayDeque<>();

    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ScreenMonitorContract.ACTION_SCREEN_CATEGORY
                    .equals(intent.getAction())) {
                return;
            }

            String category = intent.getStringExtra(
                    ScreenMonitorContract.EXTRA_CATEGORY);

            String eventType = intent.getStringExtra(
                    ScreenMonitorContract.EXTRA_EVENT_TYPE);
            ArrayList<String> visibleLabels = intent.getStringArrayListExtra(
                    ScreenMonitorContract.EXTRA_VISIBLE_LABELS);
            ArrayList<String> actionableElements = intent.getStringArrayListExtra(
                    ScreenMonitorContract.EXTRA_ACTIONABLE_ELEMENTS);
            int confidence = intent.getIntExtra(
                    ScreenMonitorContract.EXTRA_CONFIDENCE, 0);
            String evidenceStrength = intent.getStringExtra(
                    ScreenMonitorContract.EXTRA_EVIDENCE_STRENGTH);
            boolean ambiguous = intent.getBooleanExtra(
                    ScreenMonitorContract.EXTRA_AMBIGUOUS, true);
            ArrayList<String> evidenceCodes = intent.getStringArrayListExtra(
                    ScreenMonitorContract.EXTRA_EVIDENCE_CODES);
            long observedAt = intent.getLongExtra(
                    ScreenMonitorContract.EXTRA_OBSERVED_AT,
                    0L);
            boolean paypalActive = intent.getBooleanExtra(
                    ScreenMonitorContract.EXTRA_PAYPAL_ACTIVE, true);

            if (!paypalActive) {
                applyCategory(
                        ScreenCategories.UNKNOWN,
                        eventType,
                        false,
                        new ArrayList<>(),
                        new ArrayList<>(),
                        0,
                        "insufficient",
                        true,
                        new ArrayList<>(),
                        0L);
                return;
            }

            if (category == null || !ScreenCategories.isKnownCategory(category.trim())) {
                category = ScreenCategories.UNKNOWN;
            }

            if (eventType == null) {
                eventType = "UI update";
            }

            boolean categoryChanged = intent.getBooleanExtra(
                    ScreenMonitorContract.EXTRA_CATEGORY_CHANGED, true);
            applyCategory(
                    category.trim(),
                    eventType,
                    categoryChanged,
                    visibleLabels,
                    actionableElements,
                    confidence,
                    evidenceStrength,
                    ambiguous,
                    evidenceCodes,
                    observedAt);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceStatus = findViewById(R.id.serviceStatus);
        backendStatus = findViewById(R.id.backendStatus);
        currentCategory = findViewById(R.id.currentCategory);
        guidanceTitle = findViewById(R.id.guidanceTitle);
        guidanceSteps = findViewById(R.id.guidanceSteps);
        eventLog = findViewById(R.id.eventLog);
        questionInput = findViewById(R.id.questionInput);
        questionResult = findViewById(R.id.questionResult);
        emptyState = findViewById(R.id.emptyState);
        guidanceLoading = findViewById(R.id.guidanceLoading);
        questionLoading = findViewById(R.id.questionLoading);
        refreshButton = findViewById(R.id.refreshGuidanceButton);
        askButton = findViewById(R.id.askSaathiButton);
        retryButton = findViewById(R.id.retryGuidanceButton);

        Button settingsButton =
                findViewById(R.id.openAccessibilitySettingsButton);

        if (BuildConfig.DIAGNOSTIC_BUILD) {
            settingsButton.setVisibility(View.GONE);
        } else {
            settingsButton.setOnClickListener(view ->
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.accessibility_consent_title)
                            .setMessage(R.string.accessibility_consent_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(
                                    R.string.continue_to_accessibility_settings,
                                    (dialog, which) -> startActivity(new Intent(
                                            Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                            .show());
        }

        refreshButton.setOnClickListener(view ->
                loadGuidance(lastCategory, false));

        retryButton.setOnClickListener(view ->
                loadGuidance(lastCategory, false));

        askButton.setOnClickListener(view -> askSaathi());

        lastCategory = CategoryStore.lastCategory(this);
        questionRequestTracker.updateScreenCategory(lastCategory);
        String storedEvent = CategoryStore.lastEvent(this);

        if (CategoryStore.lastUpdatedAt(this) > 0L) {
            applyCategory(lastCategory, storedEvent, true);
        } else {
            currentCategory.setText(getString(
                    R.string.detected_category_waiting));
            emptyState.setVisibility(View.VISIBLE);
            renderEvents();
            loadGuidance(ScreenCategories.UNKNOWN, true);
        }

        registerEventReceiver();
        checkBackendHealth();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();

        String stored = CategoryStore.lastCategory(this);
        if (stored != null && !stored.equals(lastCategory)) {
            applyCategory(stored, CategoryStore.lastEvent(this), true);
        }
    }

    @Override
    protected void onDestroy() {
        healthRequestGate.close();
        guidanceRequestId++;
        questionRequestTracker.invalidate();
        unregisterEventReceiver();
        super.onDestroy();
    }

    private void registerEventReceiver() {
        if (receiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter(
                ScreenMonitorContract.ACTION_SCREEN_CATEGORY);

        ContextCompat.registerReceiver(
                this,
                eventReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void unregisterEventReceiver() {
        if (!receiverRegistered) {
            return;
        }

        try {
            unregisterReceiver(eventReceiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered.
        }

        receiverRegistered = false;
    }

    private void applyCategory(
            String category,
            String eventType,
            boolean fetchGuidance
    ) {
        applyCategory(
                category,
                eventType,
                fetchGuidance,
                new ArrayList<>(),
                new ArrayList<>(),
                0,
                "insufficient",
                true,
                new ArrayList<>(),
                0L);
    }

    private void applyCategory(
            String category,
            String eventType,
            boolean fetchGuidance,
            List<String> visibleLabels,
            List<String> actionableElements,
            int confidence,
            String evidenceStrength,
            boolean ambiguous,
            List<String> evidenceCodes,
            long observedAt
    ) {
        boolean categoryChanged = questionRequestTracker.updateScreenContext(
                category,
                visibleLabels,
                actionableElements,
                confidence,
                evidenceStrength,
                ambiguous,
                evidenceCodes,
                observedAt);
        lastCategory = questionRequestTracker.screenCategory();

        if (categoryChanged) {
            guidanceRequestId++;
            guidanceRequestInFlight = false;
            setGuidanceLoading(false);
            setQuestionLoading(false);
        }

        currentCategory.setText(getString(
                R.string.detected_category_fmt,
                ScreenCategories.displayLabel(category),
                category));

        emptyState.setVisibility(
                ScreenCategories.UNKNOWN.equals(category)
                        ? View.VISIBLE
                        : View.GONE);

        if (eventType != null && !eventType.trim().isEmpty()) {
            recentEvents.addFirst(eventType + " — " + category);
            while (recentEvents.size() > 6) {
                recentEvents.removeLast();
            }
        }

        renderEvents();

        if (fetchGuidance) {
            loadGuidance(lastCategory, false);
        }
    }

    private void loadGuidance(String category, boolean silentEmpty) {
        if (guidanceRequestInFlight) {
            return;
        }

        guidanceRequestInFlight = true;
        int requestId = ++guidanceRequestId;
        setGuidanceLoading(true);
        retryButton.setVisibility(View.GONE);

        if (!silentEmpty) {
            guidanceTitle.setText(R.string.loading_guidance);
            guidanceSteps.setText(R.string.contacting_server);
        }

        GuidanceClient.getGuidance(category, new GuidanceClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    if (requestId != guidanceRequestId
                            || !category.equals(lastCategory)) {
                        return;
                    }
                    guidanceRequestInFlight = false;
                    setGuidanceLoading(false);
                    showGuidance(response, false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (requestId != guidanceRequestId
                            || !category.equals(lastCategory)) {
                        return;
                    }
                    guidanceRequestInFlight = false;
                    setGuidanceLoading(false);
                    JSONObject fallback =
                            LocalGuidanceFallback.forCategory(category);
                    showGuidance(fallback, true);
                    guidanceTitle.setText(getString(
                            R.string.guidance_offline_title));
                    guidanceSteps.setText(getString(
                            R.string.guidance_offline_with_error,
                            GuidanceClient.formatSteps(fallback),
                            getString(R.string.guidance_error_detail, message)));
                    retryButton.setVisibility(View.VISIBLE);
                    updateBackendStatus(getString(
                            R.string.backend_unavailable,
                            GuidanceClient.baseUrl()));
                });
            }
        });
    }

    private void showGuidance(JSONObject response, boolean offline) {
        guidanceTitle.setText(response.optString(
                "title",
                getString(R.string.saathi_guidance)));
        guidanceSteps.setText(GuidanceClient.formatSteps(response));

        if (!offline) {
            updateBackendStatus(getString(
                    R.string.backend_connected,
                    GuidanceClient.baseUrl()));
            retryButton.setVisibility(View.GONE);
        }
    }

    private void askSaathi() {
        String question = questionInput.getText().toString().trim();

        if (question.isEmpty()) {
            questionResult.setText(R.string.type_question_first);
            return;
        }

        if (question.length() > 500) {
            questionResult.setText(R.string.question_too_long);
            return;
        }

        if (QuestionInputValidator.containsLikelySensitiveValue(question)) {
            Toast.makeText(
                    this,
                    R.string.do_not_share_secrets,
                    Toast.LENGTH_LONG).show();
            return;
        }

        QuestionRequestTracker.Request request =
                questionRequestTracker.begin(question);
        if (request == null) {
            return;
        }
        setQuestionLoading(true);
        questionResult.setText(R.string.preparing_answer);

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
                        runOnUiThread(() -> {
                            if (!questionRequestTracker.isCurrent(request)) {
                                return;
                            }
                            questionRequestTracker.finish(request);
                            setQuestionLoading(false);

                            String topic =
                                    response.optString("category", "general");
                            String context = response.optString(
                                    "screenCategory",
                                    request.screenCategory());
                            if (!request.screenCategory().equals(context)) {
                                questionResult.setText(
                                        R.string.floating_assistant_question_error);
                                return;
                            }
                            String guidance = response.optString(
                                    "guidance",
                                    getString(R.string.check_paypal_app));
                            String disclaimer =
                                    response.optString("disclaimer", "");

                            StringBuilder text = new StringBuilder();
                            text.append(guidance);

                            if (!disclaimer.isEmpty()) {
                                text.append("\n\n").append(disclaimer);
                            }

                            questionResult.setText(text.toString());
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (!questionRequestTracker.isCurrent(request)) {
                                return;
                            }
                            questionRequestTracker.finish(request);
                            setQuestionLoading(false);
                            questionResult.setText(getString(
                                    R.string.question_error, message));
                        });
                    }

                });
    }

    private void checkBackendHealth() {
        long requestId = healthRequestGate.beginRequest();
        if (requestId < 0L) {
            return;
        }
        backendStatus.setText(R.string.backend_checking);

        GuidanceClient.checkHealth(new GuidanceClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    if (!healthRequestGate.isCurrent(requestId)) {
                        return;
                    }
                    updateBackendStatus(getString(
                            R.string.backend_connected,
                            GuidanceClient.baseUrl()));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (!healthRequestGate.isCurrent(requestId)) {
                        return;
                    }
                    updateBackendStatus(getString(
                            R.string.backend_unavailable,
                            GuidanceClient.baseUrl()));
                });
            }
        });
    }

    private void updateBackendStatus(String message) {
        healthRequestGate.invalidate();
        backendStatus.setText(message);
    }

    private void setGuidanceLoading(boolean loading) {
        guidanceLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!loading);
        retryButton.setEnabled(!loading);
    }

    private void setQuestionLoading(boolean loading) {
        questionLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        askButton.setEnabled(!loading);
        questionInput.setEnabled(!loading);
    }

    private void updateServiceStatus() {
        if (BuildConfig.DIAGNOSTIC_BUILD) {
            serviceStatus.setText(R.string.diagnostic_service_unavailable);
            return;
        }

        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);

        boolean enabled = false;

        if (manager != null) {
            List<AccessibilityServiceInfo> services =
                    manager.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

            for (AccessibilityServiceInfo info : services) {
                if (info.getId() != null
                        && info.getId().contains("ScreenMonitorService")) {
                    enabled = true;
                    break;
                }
            }
        }

        serviceStatus.setText(enabled
                ? getString(R.string.accessibility_enabled)
                : getString(R.string.accessibility_disabled));
    }

    private void renderEvents() {
        if (eventLog == null) {
            return;
        }

        if (recentEvents.isEmpty()) {
            eventLog.setText(R.string.no_screen_events);
            return;
        }

        StringBuilder builder = new StringBuilder();

        for (String item : recentEvents) {
            builder.append("• ")
                    .append(item)
                    .append('\n');
        }

        eventLog.setText(builder.toString());
    }
}
