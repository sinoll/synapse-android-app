package com.sinoll.synapse_app.data.repository;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.api.ApiException;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.model.ContentPage;
import com.sinoll.synapse_app.data.model.ContentQuery;
import com.sinoll.synapse_app.data.model.ContentType;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ContentRepository implements AutoCloseable {

    private final ApiClient apiClient;
    private final ExecutorService executor;

    public ContentRepository(ApiClient apiClient) {
        if (apiClient == null) {
            throw new IllegalArgumentException("apiClient must not be null.");
        }
        this.apiClient = apiClient;
        this.executor = Executors.newFixedThreadPool(3);
    }

    public Future<?> loadPage(ContentType type, ContentQuery query, String authorization,
                              Callback<ContentPage> callback) {
        requireArguments(type, query, callback);
        return executor.submit(() -> {
            try {
                JSONObject data = apiClient.get(type.getEndpoint(), query.toQueryString(), authorization);
                callback.onSuccess(ContentPage.fromJson(data, query));
            } catch (ApiException | IOException | JSONException error) {
                callback.onError(error);
            }
        });
    }

    public Future<?> loadDetail(ContentType type, int id, String authorization,
                                Callback<ContentItem> callback) {
        if (type == null || id <= 0 || callback == null) {
            throw new IllegalArgumentException("type, positive id, and callback are required.");
        }
        return executor.submit(() -> {
            try {
                JSONObject data = apiClient.get(type.getEndpoint() + "/" + id, "", authorization);
                callback.onSuccess(ContentItem.fromJson(data));
            } catch (ApiException | IOException | JSONException error) {
                callback.onError(error);
            }
        });
    }

    private static void requireArguments(ContentType type, ContentQuery query,
                                         Callback<?> callback) {
        if (type == null || query == null || callback == null) {
            throw new IllegalArgumentException("type, query, and callback are required.");
        }
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
