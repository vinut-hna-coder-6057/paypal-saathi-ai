package com.vinuthna.paypalsaathi.auth;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class OidcConfigurationTest {
    private static final String REDIRECT_URI =
            "com.vinuthna.paypalsaathi:/oauth2redirect";

    @Test
    public void acceptsConfiguredHttpsProviderAndRequiredScopes() {
        OidcConfiguration configuration = new OidcConfiguration(
                "https://identity.example.test/",
                "public-client",
                "saathi-api",
                null,
                "openid saathi:guidance",
                REDIRECT_URI);

        assertNull(configuration.validationError());
        assertEquals(
                "com.vinuthna.paypalsaathi:/oauth2logout",
                configuration.logoutRedirectUri());
    }

    @Test
    public void rejectsHttpIssuerAndMissingRequiredScope() {
        OidcConfiguration insecureIssuer = new OidcConfiguration(
                "http://identity.example.test/",
                "public-client",
                "saathi-api",
                null,
                "openid saathi:guidance",
                REDIRECT_URI);
        OidcConfiguration missingScope = new OidcConfiguration(
                "https://identity.example.test/",
                "public-client",
                "saathi-api",
                null,
                "openid profile",
                REDIRECT_URI);

        assertNotNull(insecureIssuer.validationError());
        assertNotNull(missingScope.validationError());
    }

    @Test
    public void rejectsInsecureTokenRevocationEndpoint() {
        OidcConfiguration configuration = new OidcConfiguration(
                "https://identity.example.test/",
                "public-client",
                "saathi-api",
                "http://identity.example.test/revoke",
                "openid saathi:guidance",
                REDIRECT_URI);

        assertNotNull(configuration.validationError());
    }
}
