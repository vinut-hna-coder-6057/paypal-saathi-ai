package com.vinuthna.paypalsaathi.auth;

import android.net.Uri;

import com.vinuthna.paypalsaathi.BuildConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class OidcConfiguration {
    private static final String REQUIRED_SCOPE = "saathi:guidance";

    private final String issuer;
    private final String clientId;
    private final String audience;
    private final String revocationEndpoint;
    private final String scope;
    private final String redirectUri;

    public OidcConfiguration(
            String issuer,
            String clientId,
            String audience,
            String revocationEndpoint,
            String scope,
            String redirectUri
    ) {
        this.issuer = safe(issuer);
        this.clientId = safe(clientId);
        this.audience = safe(audience);
        this.revocationEndpoint = safe(revocationEndpoint);
        this.scope = safe(scope);
        this.redirectUri = safe(redirectUri);
    }

    public static OidcConfiguration fromBuildConfig() {
        return new OidcConfiguration(
                BuildConfig.OIDC_ISSUER,
                BuildConfig.OIDC_CLIENT_ID,
                BuildConfig.OIDC_AUDIENCE,
                BuildConfig.OIDC_REVOCATION_ENDPOINT,
                BuildConfig.OIDC_SCOPE,
                BuildConfig.OIDC_REDIRECT_URI);
    }

    public String validationError() {
        if (issuer == null || clientId == null || audience == null
                || scope == null || redirectUri == null) {
            return "OIDC is not configured. Set the issuer, public client ID, "
                    + "API audience, scope, and redirect URI.";
        }
        try {
            URI issuerUri = new URI(issuer);
            if (!"https".equalsIgnoreCase(issuerUri.getScheme())
                    || issuerUri.getHost() == null
                    || issuerUri.getUserInfo() != null
                    || issuerUri.getFragment() != null
                    || issuerUri.getQuery() != null) {
                return "The OIDC issuer must be a trusted HTTPS URL.";
            }
            URI callbackUri = new URI(redirectUri);
            if (callbackUri.getScheme() == null
                    || "http".equalsIgnoreCase(callbackUri.getScheme())
                    || callbackUri.getFragment() != null
                    || callbackUri.getUserInfo() != null) {
                return "The OIDC redirect URI is invalid.";
            }
            if (revocationEndpoint != null) {
                URI revocationUri = new URI(revocationEndpoint);
                if (!"https".equalsIgnoreCase(revocationUri.getScheme())
                        || revocationUri.getHost() == null
                        || revocationUri.getUserInfo() != null
                        || revocationUri.getFragment() != null) {
                    return "The OIDC revocation endpoint must be HTTPS.";
                }
            }
        } catch (URISyntaxException exception) {
            return "The OIDC issuer or redirect URI is invalid.";
        }
        Set<String> scopes = new HashSet<>(
                Arrays.asList(scope.trim().split("\\s+")));
        if (!scopes.contains("openid") || !scopes.contains(REQUIRED_SCOPE)) {
            return "OIDC scope must include openid and saathi:guidance.";
        }
        return null;
    }

    public boolean isConfigured() {
        return validationError() == null;
    }

    public String issuer() { return issuer; }
    public String clientId() { return clientId; }
    public String audience() { return audience; }
    public String revocationEndpoint() { return revocationEndpoint; }
    public String scope() { return scope; }
    public String redirectUri() { return redirectUri; }

    public String logoutRedirectUri() {
        URI redirect = URI.create(redirectUri);
        try {
            return new URI(
                    redirect.getScheme(),
                    redirect.getUserInfo(),
                    redirect.getHost(),
                    redirect.getPort(),
                    "/oauth2logout",
                    null,
                    null).toString();
        } catch (URISyntaxException exception) {
            return Uri.parse(redirectUri).buildUpon()
                    .path("/oauth2logout")
                    .build()
                    .toString();
        }
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty()
                ? null : value.trim();
    }
}
