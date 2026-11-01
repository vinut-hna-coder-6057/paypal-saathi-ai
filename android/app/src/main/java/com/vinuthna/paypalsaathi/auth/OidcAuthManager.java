package com.vinuthna.paypalsaathi.auth;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.vinuthna.paypalsaathi.MainActivity;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.CodeVerifierUtil;
import net.openid.appauth.EndSessionRequest;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;

import org.json.JSONException;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OidcAuthManager implements AutoCloseable {
    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    public interface AccessTokenCallback {
        void onResult(String accessToken, String error);
    }

    private static final String TAG = "SaathiOidc";
    public static final int AUTHORIZATION_REQUEST_CODE = 7801;
    private static final int LOGOUT_REQUEST_CODE = 7802;
    private static final int LOGOUT_CANCEL_REQUEST_CODE = 7803;
    private static final ExecutorService REVOCATION_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private final Context appContext;
    private final OidcConfiguration configuration;
    private final EncryptedAuthStateStore stateStore;
    private final AuthorizationService authorizationService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<AccessTokenCallback> tokenCallbacks = new ArrayList<>();
    private AuthState authState = new AuthState();
    private boolean tokenOperationInFlight;
    private long tokenOperationId;
    private String storageError;

    public OidcAuthManager(Context context) {
        appContext = context.getApplicationContext();
        configuration = OidcConfiguration.fromBuildConfig();
        stateStore = new EncryptedAuthStateStore(appContext);
        authorizationService = new AuthorizationService(appContext);
        try {
            String serialized = stateStore.read();
            if (serialized != null) {
                authState = AuthState.jsonDeserialize(serialized);
            }
        } catch (GeneralSecurityException | JSONException exception) {
            storageError = "Saved sign-in could not be opened securely. "
                    + "Please sign in again.";
            Log.e(TAG, "Could not read encrypted sign-in state ("
                    + exception.getClass().getSimpleName() + ").");
        }
    }

    public boolean isProviderConfigured() {
        return configuration.isConfigured();
    }

    public String configurationError() {
        return configuration.validationError();
    }

    public boolean isSignedIn() {
        return authState.isAuthorized()
                || authState.getRefreshToken() != null;
    }

    public void authorize(Activity activity, ResultCallback callback) {
        String error = configuration.validationError();
        if (error != null) {
            callback.onResult(false, error);
            return;
        }
        AuthorizationServiceConfiguration.fetchFromIssuer(
                Uri.parse(configuration.issuer()),
                (serviceConfiguration, exception) -> {
                    if (exception != null || serviceConfiguration == null) {
                        Log.w(TAG, "OIDC discovery failed.");
                        callback.onResult(
                                false,
                                "Could not reach the configured sign-in provider.");
                        return;
                    }
                    String codeVerifier =
                            CodeVerifierUtil.generateRandomCodeVerifier();
                    AuthorizationRequest request =
                            new AuthorizationRequest.Builder(
                                    serviceConfiguration,
                                    configuration.clientId(),
                                    ResponseTypeValues.CODE,
                                    Uri.parse(configuration.redirectUri()))
                                    .setScope(configuration.scope())
                                    .setCodeVerifier(
                                    codeVerifier,
                                    CodeVerifierUtil
                                            .deriveCodeVerifierChallenge(
                                                    codeVerifier),
                                    AuthorizationRequest
                                            .CODE_CHALLENGE_METHOD_S256)
                                    .setAdditionalParameters(
                                            Collections.singletonMap(
                                                    "audience",
                                                    configuration.audience()))
                                    .build();
                    Intent authorizationIntent =
                            authorizationService.getAuthorizationRequestIntent(
                                    request);
                    activity.startActivityForResult(
                            authorizationIntent,
                            AUTHORIZATION_REQUEST_CODE);
                    callback.onResult(
                            true,
                            "Continue sign-in in your browser.");
                });
    }

    public void handleAuthorizationResult(
            int resultCode,
            Intent data,
            ResultCallback callback
    ) {
        if (resultCode != Activity.RESULT_OK) {
            callback.onResult(false, "Sign-in was cancelled.");
            return;
        }
        if (data == null) {
            callback.onResult(false, "Sign-in was cancelled or not completed.");
            return;
        }
        AuthorizationResponse response =
                AuthorizationResponse.fromIntent(data);
        AuthorizationException exception =
                AuthorizationException.fromIntent(data);
        if (response == null) {
            callback.onResult(false, exception == null
                    ? "The sign-in response was not valid."
                    : "The sign-in provider rejected the request.");
            return;
        }

        authState.update(response, exception);
        try {
            persistState();
        } catch (GeneralSecurityException storageException) {
            Log.e(TAG, "Could not persist authorization response ("
                    + storageException.getClass().getSimpleName() + ").");
            callback.onResult(
                    false,
                    "Secure sign-in storage is unavailable. Try again later.");
            return;
        }
        authorizationService.performTokenRequest(
                response.createTokenExchangeRequest(),
                (tokenResponse, tokenException) -> {
                    authState.update(tokenResponse, tokenException);
                    if (tokenException != null || tokenResponse == null) {
                        Log.w(TAG, "OIDC token exchange failed.");
                        callback.onResult(
                                false,
                                "Sign-in could not be completed. Please try again.");
                        return;
                    }
                    try {
                        persistState();
                    } catch (GeneralSecurityException storageException) {
                        Log.e(TAG, "Could not persist tokens ("
                                + storageException.getClass().getSimpleName()
                                + ").");
                        callback.onResult(
                                false,
                                "Secure sign-in storage is unavailable.");
                        return;
                    }
                    callback.onResult(true, "Signed in to Saathi.");
                });
    }

    public void withFreshAccessToken(AccessTokenCallback callback) {
        mainHandler.post(() -> {
            tokenCallbacks.add(callback);
            if (tokenOperationInFlight) {
                return;
            }
            if (!configuration.isConfigured()) {
                drainTokenCallbacks(null, configuration.validationError());
                return;
            }
            if (storageError != null) {
                drainTokenCallbacks(null, storageError);
                return;
            }
            if (authState.getRefreshToken() == null
                    && !authState.isAuthorized()) {
                drainTokenCallbacks(
                        null,
                        "Sign in to Saathi to use the configured remote API.");
                return;
            }
            tokenOperationInFlight = true;
            long operationId = ++tokenOperationId;
            authState.performActionWithFreshTokens(
                    authorizationService,
                    (accessToken, idToken, exception) -> {
                        if (operationId != tokenOperationId) {
                            return;
                        }
                        if (exception != null || accessToken == null) {
                            Log.w(TAG, "OIDC token refresh failed.");
                            clearAuthorizationState();
                            drainTokenCallbacks(
                                    null,
                                    "Your Saathi sign-in expired. Sign in again.");
                            return;
                        }
                        try {
                            persistState();
                            drainTokenCallbacks(accessToken, null);
                        } catch (GeneralSecurityException storageException) {
                            Log.e(TAG, "Could not persist refreshed tokens ("
                                    + storageException.getClass()
                                            .getSimpleName() + ").");
                            drainTokenCallbacks(
                                    null,
                                    "Secure sign-in storage is unavailable.");
                        }
                    });
        });
    }

    public void logout(Activity activity, ResultCallback callback) {
        AuthState oldState = authState;
        AuthorizationServiceConfiguration serviceConfiguration =
                oldState.getAuthorizationServiceConfiguration();
        String idToken = oldState.getIdToken();
        String refreshToken = oldState.getRefreshToken();
        authState = new AuthState();
        storageError = null;
        tokenOperationId++;
        drainTokenCallbacks(null, "You signed out. Sign in again to continue.");
        try {
            stateStore.clear();
        } catch (IllegalStateException exception) {
            Log.e(TAG, "Could not clear secure sign-in state ("
                    + exception.getClass().getSimpleName() + ").");
            callback.onResult(
                    false,
                    "Could not fully clear secure sign-in storage.");
            return;
        }

        if (serviceConfiguration == null
                || serviceConfiguration.endSessionEndpoint == null
                || idToken == null) {
            revokeRefreshToken(refreshToken, callback);
            return;
        }
        EndSessionRequest request = new EndSessionRequest.Builder(
                serviceConfiguration)
                .setIdTokenHint(idToken)
                .setPostLogoutRedirectUri(
                        Uri.parse(configuration.logoutRedirectUri()))
                .build();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent completed = PendingIntent.getActivity(
                activity,
                LOGOUT_REQUEST_CODE,
                new Intent(activity, MainActivity.class),
                flags);
        PendingIntent canceled = PendingIntent.getActivity(
                activity,
                LOGOUT_CANCEL_REQUEST_CODE,
                new Intent(activity, MainActivity.class),
                flags);
        authorizationService.performEndSessionRequest(
                request,
                completed,
                canceled);
        revokeRefreshToken(refreshToken, callback);
    }

    private void revokeRefreshToken(
            String refreshToken,
            ResultCallback callback
    ) {
        String endpoint = configuration.revocationEndpoint();
        if (refreshToken == null) {
            callback.onResult(
                    true,
                    "Signed out on this device. No refresh token was available to revoke.");
            return;
        }
        if (endpoint == null) {
            callback.onResult(
                    true,
                    "Signed out on this device. Provider token revocation is not configured.");
            return;
        }
        REVOCATION_EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            boolean revoked = false;
            try {
                connection = (HttpURLConnection) new URL(endpoint)
                        .openConnection();
                connection.setRequestMethod("POST");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(5_000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8");
                String body = "client_id="
                        + URLEncoder.encode(
                                configuration.clientId(),
                                StandardCharsets.UTF_8.name())
                        + "&token="
                        + URLEncoder.encode(
                                refreshToken,
                                StandardCharsets.UTF_8.name())
                        + "&token_type_hint=refresh_token";
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                revoked = status >= 200 && status < 300;
            } catch (IOException exception) {
                Log.w(TAG, "OIDC token revocation request failed ("
                        + exception.getClass().getSimpleName() + ").");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            boolean result = revoked;
            mainHandler.post(() -> callback.onResult(
                    result,
                    result
                            ? "Signed out on this device and revoked the refresh token."
                            : "Signed out on this device, but provider token revocation failed."));
        });
    }

    public void invalidateAuthorization() {
        mainHandler.post(() -> {
            clearAuthorizationState();
            drainTokenCallbacks(
                    null,
                    "Your Saathi sign-in was rejected. Sign in again.");
        });
    }

    private void clearAuthorizationState() {
        authState = new AuthState();
        tokenOperationId++;
        try {
            stateStore.clear();
            storageError = null;
        } catch (IllegalStateException exception) {
            storageError = "Saved sign-in could not be cleared securely.";
            Log.e(TAG, "Could not clear rejected sign-in state ("
                    + exception.getClass().getSimpleName() + ").");
        }
    }

    private void persistState() throws GeneralSecurityException {
        stateStore.write(authState.jsonSerializeString());
        storageError = null;
    }

    private void drainTokenCallbacks(String accessToken, String error) {
        tokenOperationInFlight = false;
        List<AccessTokenCallback> callbacks =
                new ArrayList<>(tokenCallbacks);
        tokenCallbacks.clear();
        for (AccessTokenCallback callback : callbacks) {
            callback.onResult(accessToken, error);
        }
    }

    @Override
    public void close() {
        authorizationService.dispose();
    }
}
