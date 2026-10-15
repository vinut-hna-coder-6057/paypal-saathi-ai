package com.vinuthna.paypalsaathi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP client for the local Saathi guidance API.
 * Uses {@link BuildConfig#API_BASE_URL} (debug default: emulator host loopback).
 */
public final class GuidanceClient {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int QUESTION_READ_TIMEOUT_MS = 55_000;

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    public interface Callback {
        void onSuccess(JSONObject response);

        void onError(String message);
    }

    private GuidanceClient() {}

    public static String baseUrl() {
        return BuildConfig.API_BASE_URL;
    }

    public static void checkHealth(Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(baseUrl() + "/health");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);

                int status = connection.getResponseCode();
                String body = readBody(connection, status);

                if (status < 200 || status >= 300) {
                    callback.onError("Health check failed (HTTP " + status + ").");
                    return;
                }

                callback.onSuccess(new JSONObject(body));
            } catch (Exception exception) {
                callback.onError(safeMessage(exception));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public static void getGuidance(String category, Callback callback) {
        JSONObject body = new JSONObject();

        try {
            body.put("screenCategory", category);
        } catch (Exception exception) {
            callback.onError("Could not prepare the guidance request.");
            return;
        }

        post("/api/guidance", body, callback, READ_TIMEOUT_MS);
    }

    public static void askQuestion(String question, Callback callback) {
        askQuestion(question, "unknown", callback);
    }

    public static void askQuestion(
            String question,
            String screenCategory,
            Callback callback
    ) {
        askQuestion(question, screenCategory, null, 0L, callback);
    }

    public static void askQuestion(
            String question,
            String screenCategory,
            List<String> visibleLabels,
            long observedAt,
            Callback callback
    ) {
        askQuestion(
                question,
                screenCategory,
                visibleLabels,
                null,
                0,
                "insufficient",
                true,
                null,
                observedAt,
                callback);
    }

    public static void askQuestion(
            String question,
            String screenCategory,
            List<String> visibleLabels,
            List<String> actionableElements,
            int confidence,
            String evidenceStrength,
            boolean ambiguous,
            List<String> evidenceCodes,
            long observedAt,
            Callback callback
    ) {
        JSONObject body = new JSONObject();

        try {
            body.put("question", question);
            body.put("screenCategory", screenCategory);
            long contextAge = System.currentTimeMillis() - observedAt;
            if (observedAt > 0L && contextAge >= -5_000L
                    && contextAge <= 15_000L) {
                JSONObject screenContext = new JSONObject();
                screenContext.put("category", screenCategory);
                JSONArray safeLabels = new JSONArray();
                for (String label : ScreenContext.sanitizeNavigationLabels(
                        visibleLabels)) {
                    safeLabels.put(label);
                }
                screenContext.put("visibleLabels", safeLabels);
                JSONArray safeActions = new JSONArray();
                for (String action : ScreenContext.sanitizeActionDescriptors(
                        actionableElements)) {
                    safeActions.put(action);
                }
                screenContext.put("actionableElements", safeActions);
                screenContext.put("confidence", Math.max(0, Math.min(
                        confidence, 100)));
                screenContext.put(
                        "evidenceStrength",
                        "strong".equals(evidenceStrength)
                                || "moderate".equals(evidenceStrength)
                                ? evidenceStrength
                                : "insufficient");
                screenContext.put("ambiguous", ambiguous);
                JSONArray safeEvidenceCodes = new JSONArray();
                for (String code : ScreenContext.sanitizeEvidenceCodes(
                        evidenceCodes)) {
                    safeEvidenceCodes.put(code);
                }
                screenContext.put("evidenceCodes", safeEvidenceCodes);
                screenContext.put("source", "accessibility");
                screenContext.put("observedAt", observedAt);
                body.put("screenContext", screenContext);
            }
        } catch (Exception exception) {
            callback.onError("Could not prepare your question.");
            return;
        }

        post("/api/classify", body, callback, QUESTION_READ_TIMEOUT_MS);
    }

    private static void post(
            String path,
            JSONObject body,
            Callback callback,
            int readTimeoutMs
    ) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(baseUrl() + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(readTimeoutMs);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type", "application/json; charset=UTF-8");

                byte[] payload =
                        body.toString().getBytes(StandardCharsets.UTF_8);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }

                int status = connection.getResponseCode();
                String text = readBody(connection, status);

                if (status < 200 || status >= 300) {
                    String serverError = extractError(text);
                    callback.onError(
                            serverError != null
                                    ? serverError
                                    : "The server returned HTTP " + status + "."
                    );
                    return;
                }

                if (text == null || text.trim().isEmpty()) {
                    callback.onError("The server returned an empty response.");
                    return;
                }

                callback.onSuccess(new JSONObject(text));
            } catch (Exception exception) {
                callback.onError(safeMessage(exception));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static String readBody(HttpURLConnection connection, int status)
            throws Exception {
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (stream == null) {
            return "";
        }

        StringBuilder text = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
        }

        return text.toString();
    }

    private static String extractError(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject json = new JSONObject(body);
            String error = json.optString("error", "");
            return error.isEmpty() ? null : error;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.trim().isEmpty()) {
            return "Could not reach the guidance server.";
        }

        // Avoid leaking unusual internal details; keep short network-oriented text.
        if (message.contains("Cleartext")) {
            return "Cleartext HTTP is blocked. Use the debug network config "
                    + "or HTTPS for production.";
        }

        if (message.contains("Failed to connect")
                || message.contains("ECONNREFUSED")
                || message.contains("ConnectException")) {
            return "Could not connect to "
                    + baseUrl()
                    + ". Is the backend running?";
        }

        if (message.contains("timeout") || message.contains("Timeout")) {
            return "The guidance server timed out. Try again.";
        }

        if (message.contains("JSON") || message.contains("End of input")) {
            return "The server returned invalid JSON.";
        }

        return message.length() > 180 ? message.substring(0, 180) + "…" : message;
    }

    public static String formatSteps(JSONObject response) {
        JSONArray steps = response.optJSONArray("steps");
        StringBuilder result = new StringBuilder();

        if (steps != null && steps.length() > 0) {
            for (int i = 0; i < steps.length(); i++) {
                result.append(i + 1)
                        .append(". ")
                        .append(steps.optString(i))
                        .append("\n\n");
            }
        } else {
            String guidance = response.optString("guidance", "");
            if (guidance.isEmpty()) {
                result.append("No instructions were returned.");
            } else {
                result.append(guidance);
            }
        }

        String safetyNote = response.optString("safetyNote", "");
        if (!safetyNote.isEmpty()) {
            if (result.length() > 0) {
                result.append("\n\n");
            }
            result.append(safetyNote);
        }

        return result.toString().trim();
    }
}
