package com.vinuthna.paypalsaathi.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class EncryptedAuthStateStore {
    private static final String KEY_ALIAS =
            "com.vinuthna.paypalsaathi.oidc.authstate";
    private static final String STORE_NAME = "saathi_oidc_secure";
    private static final String STATE_KEY = "encrypted_auth_state";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SharedPreferences preferences;

    EncryptedAuthStateStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                STORE_NAME,
                Context.MODE_PRIVATE);
    }

    synchronized String read() throws GeneralSecurityException {
        String encoded = preferences.getString(STATE_KEY, null);
        if (encoded == null) {
            return null;
        }
        byte[] ciphertext;
        try {
            ciphertext = Base64.decode(encoded, Base64.NO_WRAP);
        } catch (IllegalArgumentException exception) {
            throw new GeneralSecurityException(
                    "Stored authentication state is invalid.",
                    exception);
        }
        if (ciphertext.length <= IV_LENGTH_BYTES) {
            throw new GeneralSecurityException("Stored authentication state is invalid.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                getKey(false),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8);
    }

    synchronized void write(String value) throws GeneralSecurityException {
        if (value == null || value.isEmpty()) {
            clear();
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey(true));
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(
                value.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
        buffer.put(iv);
        buffer.put(encrypted);
        boolean saved = preferences.edit()
                .putString(
                        STATE_KEY,
                        Base64.encodeToString(buffer.array(), Base64.NO_WRAP))
                .commit();
        if (!saved) {
            throw new GeneralSecurityException(
                    "Could not securely save authentication state.");
        }
    }

    synchronized void clear() {
        if (!preferences.edit().remove(STATE_KEY).commit()) {
            throw new IllegalStateException(
                    "Could not remove encrypted authentication state.");
        }
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        } catch (GeneralSecurityException | java.io.IOException exception) {
            throw new IllegalStateException(
                    "Could not clear secure authentication state.", exception);
        }
    }

    private static SecretKey getKey(boolean create)
            throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            }
            if (!create) {
                throw new GeneralSecurityException(
                        "Secure authentication key is unavailable.");
            }
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT
                            | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(
                            KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        } catch (java.io.IOException exception) {
            throw new GeneralSecurityException(
                    "Could not access secure authentication storage.",
                    exception);
        }
    }
}
