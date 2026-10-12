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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GuidanceClient {
    // Android emulator -> host computer.
    // For a physical phone, replace this with your computer's LAN address.
    private static final String BASE_URL = "http://10.0.2.2:3001";

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String message);
    }

    private GuidanceClient() {}

    public static void getGuidance(String category, Callback callback) {
        JSONObject body = new JSONObject();

        try {
            body.put("screenCategory", category);
        } catch (Exception exception) {
            callback.onError("Could not prepare the guidance request.");
            return;
        }

        post("/api/guidance", body, callback);
    }

    public static void askQuestion(String question, Callback callback) {
        JSONObject body = new JSONObject();

        try {
            body.put("question", question);
        } catch (Exception exception) {
            callback.onError("Could not prepare your question.");
            return;
        }

        post("/api/classify", body, callback);
    }

    private static void post(
            String path,
            JSONObject body,
            Callback callback
    ) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(BASE_URL + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type", "application/json; charset=UTF-8");

                byte[] payload =
                        body.toString().getBytes(StandardCharsets.UTF_8);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }

                int status = connection.getResponseCode();

                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                StringBuilder text = new StringBuilder();

                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    stream, StandardCharsets.UTF_8))) {
                        String line;

                        while ((line = reader.readLine()) != null) {
                            text.append(line);
                        }
                    }
                }

                if (status < 200 || status >= 300) {
                    throw new Exception(
                            "The server returned HTTP " + status + ".");
                }

                JSONObject response = new JSONObject(text.toString());

                callback.onSuccess(response);

            } catch (Exception exception) {
                String message = exception.getMessage();

                callback.onError(
                        message == null
                                ? "Could not reach the guidance server."
                                : message
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public static String formatSteps(JSONObject response) {
        JSONArray steps = response.optJSONArray("steps");

        if (steps == null || steps.length() == 0) {
            String guidance = response.optString("guidance", "");

            return guidance.isEmpty()
                    ? "No instructions were returned."
                    : guidance;
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < steps.length(); i++) {
            result.append(i + 1)
                    .append(". ")
                    .append(steps.optString(i))
                    .append("\n\n");
        }

        return result.toString().trim();
    }
}
