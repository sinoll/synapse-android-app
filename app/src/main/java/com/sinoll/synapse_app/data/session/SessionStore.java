package com.sinoll.synapse_app.data.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SessionStore {

    private static final String PREFERENCES = "synapse_session";
    private static final String TOKEN_KEY = "encrypted_token";
    private static final String TOKEN_TYPE_KEY = "token_type";
    private static final String IV_KEY = "token_iv";
    private static final String KEYSTORE_ALIAS = "synapse_api_session_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final SharedPreferences preferences;

    public SessionStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public synchronized void saveToken(String token, String tokenType) throws Exception {
        if (token == null || token.trim().isEmpty() || token.matches(".*[\\s\\p{Cntrl}].*")) {
            throw new IllegalArgumentException("Token must not be empty or contain whitespace.");
        }
        if (tokenType == null || !tokenType.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("Token type is invalid.");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        boolean saved = preferences.edit()
                .putString(TOKEN_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(IV_KEY, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(TOKEN_TYPE_KEY, tokenType)
                .commit();
        if (!saved) {
            throw new IllegalStateException("Could not save encrypted session.");
        }
    }

    public synchronized String getToken() throws Exception {
        String encodedToken = preferences.getString(TOKEN_KEY, null);
        String encodedIv = preferences.getString(IV_KEY, null);
        if (encodedToken == null && encodedIv == null) {
            return null;
        }
        if (encodedToken == null || encodedIv == null) {
            clear();
            throw new IllegalStateException("Stored session data is incomplete.");
        }
        byte[] encrypted = Base64.decode(encodedToken, Base64.NO_WRAP);
        byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    public synchronized String getAuthorizationHeader() throws Exception {
        String token = getToken();
        if (token == null) {
            return null;
        }
        String tokenType = preferences.getString(TOKEN_TYPE_KEY, null);
        if (tokenType == null || !tokenType.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            clear();
            throw new IllegalStateException("Stored session has no valid token type.");
        }
        return tokenType + " " + token;
    }

    public synchronized void clear() {
        if (!preferences.edit().clear().commit()) {
            throw new IllegalStateException("Could not clear local session.");
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existingKey = keyStore.getKey(KEYSTORE_ALIAS, null);
        if (existingKey instanceof SecretKey) {
            return (SecretKey) existingKey;
        }
        if (existingKey != null) {
            throw new IllegalStateException("Stored session key has an invalid type.");
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        SecretKey key = generator.generateKey();
        if (key == null) {
            throw new IllegalStateException("Android Keystore did not create a session key.");
        }
        return key;
    }
}
