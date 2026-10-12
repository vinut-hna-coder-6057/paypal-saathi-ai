package com.vinuthna.paypalsaathi;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityServiceInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.List;

public class MainActivity extends Activity {
    private TextView serviceStatus;
    private TextView currentCategory;
    private TextView guidanceTitle;
    private TextView guidanceSteps;
    private TextView eventLog;
    private TextView questionResult;
    private EditText questionInput;

    private String lastCategory = "unknown";
    private boolean receiverRegistered = false;

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

            if (category == null || category.trim().isEmpty()) {
                category = "unknown";
            }

            if (eventType == null) {
                eventType = "UI update";
            }

            lastCategory = category.trim();

            currentCategory.setText("Detected category: " + lastCategory);

            recentEvents.addFirst(eventType + " — " + lastCategory);

            while (recentEvents.size() > 6) {
                recentEvents.removeLast();
            }

            renderEvents();
            loadGuidance(lastCategory);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceStatus = findViewById(R.id.serviceStatus);
        currentCategory = findViewById(R.id.currentCategory);
        guidanceTitle = findViewById(R.id.guidanceTitle);
        guidanceSteps = findViewById(R.id.guidanceSteps);
        eventLog = findViewById(R.id.eventLog);
        questionInput = findViewById(R.id.questionInput);
        questionResult = findViewById(R.id.questionResult);

        Button settingsButton =
                findViewById(R.id.openAccessibilitySettingsButton);

        Button refreshButton =
                findViewById(R.id.refreshGuidanceButton);

        Button askButton =
                findViewById(R.id.askSaathiButton);

        settingsButton.setOnClickListener(view ->
                startActivity(new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        refreshButton.setOnClickListener(view ->
                loadGuidance(lastCategory));

        askButton.setOnClickListener(view -> askSaathi());

        currentCategory.setText("Detected category: waiting for screen event");
        renderEvents();
        loadGuidance("unknown");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();

        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(
                    ScreenMonitorContract.ACTION_SCREEN_CATEGORY);

            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                        eventReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(eventReceiver, filter);
            }

            receiverRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        if (receiverRegistered) {
            unregisterReceiver(eventReceiver);
            receiverRegistered = false;
        }

        super.onPause();
    }

    private void loadGuidance(String category) {
        guidanceTitle.setText("Loading guidance...");
        guidanceSteps.setText("Contacting the local development server.");

        GuidanceClient.getGuidance(category, new GuidanceClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    guidanceTitle.setText(
                            response.optString(
                                    "title", "Saathi guidance"));

                    guidanceSteps.setText(
                            GuidanceClient.formatSteps(response));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    guidanceTitle.setText("Guidance unavailable");
                    guidanceSteps.setText(
                            "Check that your backend is running on port 3001.\n\n"
                                    + "Details: " + message);
                });
            }
        });
    }

    private void askSaathi() {
        String question = questionInput.getText().toString().trim();

        if (question.isEmpty()) {
            questionResult.setText("Type a question first.");
            return;
        }

        if (question.length() > 500) {
            questionResult.setText(
                    "Please keep your question to 500 characters or fewer.");
            return;
        }

        questionResult.setText("Preparing an answer...");

        GuidanceClient.askQuestion(
                question,
                new GuidanceClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        runOnUiThread(() -> {
                            String category =
                                    response.optString("category", "general");

                            String guidance =
                                    response.optString(
                                            "guidance",
                                            "Please check the official PayPal app.");

                            questionResult.setText(
                                    "Topic: " + category + "\n\n" + guidance);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() ->
                                questionResult.setText(
                                        "Could not get an answer.\n" + message));
                    }
                });
    }

    private void updateServiceStatus() {
        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(
                        ACCESSIBILITY_SERVICE);

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
                ? "Accessibility service: enabled"
                : "Accessibility service: not enabled");
    }

    private void renderEvents() {
        if (eventLog == null) {
            return;
        }

        if (recentEvents.isEmpty()) {
            eventLog.setText("No screen events detected yet.");
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
