package com.sinoll.synapse_app.data.repository;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.session.SessionStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class AccountRepository implements AutoCloseable {

    private final ApiClient apiClient;
    private final SessionStore sessionStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AccountRepository(ApiClient apiClient, SessionStore sessionStore) {
        if (apiClient == null || sessionStore == null) {
            throw new IllegalArgumentException("API client and session store are required.");
        }
        this.apiClient = apiClient;
        this.sessionStore = sessionStore;
    }

    public Future<?> getCurrentUser(Callback<JSONObject> callback) {
        return executor.submit(() -> {
            try {
                String authorization = sessionStore.getAuthorizationHeader();
                if (authorization == null) {
                    callback.onSuccess(null);
                    return;
                }
                callback.onSuccess(apiClient.get("auth/me", "", authorization));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public Future<?> login(String username, String password, Callback<JSONObject> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Could not create login request.", error);
        }
        return executor.submit(() -> {
            try {
                JSONObject data = apiClient.post("auth/login", body, null);
                String token = data.optString("token", "");
                String tokenType = data.optString("token_type", "");
                if (token.isEmpty() || tokenType.isEmpty()) {
                    throw new IllegalStateException("登录响应缺少令牌信息。");
                }
                sessionStore.saveToken(token, tokenType);
                JSONObject user = data.optJSONObject("user");
                callback.onSuccess(user == null ? data : user);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public Future<?> requestRegistrationCode(String email, Callback<JSONObject> callback) {
        return post("auth/register/code", emailRequest(email), null, callback);
    }

    public Future<?> register(String username, String nickname, String email, String code,
                              String password, String confirmPassword,
                              Callback<JSONObject> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("email", email);
            body.put("code", code);
            body.put("password", password);
            body.put("confirmPassword", confirmPassword);
            if (nickname != null && !nickname.trim().isEmpty()) {
                body.put("nickname", nickname.trim());
            }
        } catch (JSONException error) {
            throw new IllegalArgumentException("Could not create registration request.", error);
        }
        return post("auth/register", body, null, callback);
    }

    public Future<?> requestPasswordResetCode(String email, Callback<JSONObject> callback) {
        return post("auth/forgot-password", emailRequest(email), null, callback);
    }

    public Future<?> resetPassword(String email, String code, String password,
                                   String confirmPassword, Callback<JSONObject> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("code", code);
            body.put("password", password);
            body.put("confirmPassword", confirmPassword);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Could not create password reset request.", error);
        }
        return post("auth/reset-password", body, null, callback);
    }

    public Future<?> logout(Callback<JSONObject> callback) {
        return executor.submit(() -> {
            Exception requestError = null;
            try {
                String authorization = sessionStore.getAuthorizationHeader();
                if (authorization != null) {
                    apiClient.post("auth/logout", new JSONObject(), authorization);
                }
            } catch (Exception error) {
                requestError = error;
            }
            try {
                sessionStore.clear();
            } catch (Exception clearError) {
                callback.onError(clearError);
                return;
            }
            if (requestError != null) {
                callback.onError(requestError);
            } else {
                callback.onSuccess(new JSONObject());
            }
        });
    }

    private Future<?> post(String path, JSONObject body, String authorization,
                           Callback<JSONObject> callback) {
        return executor.submit(() -> {
            try {
                callback.onSuccess(apiClient.post(path, body, authorization));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    private static JSONObject emailRequest(String email) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Could not create email request.", error);
        }
        return body;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    public interface Callback<T> {
        void onSuccess(T value);

        void onError(Exception error);
    }
}
