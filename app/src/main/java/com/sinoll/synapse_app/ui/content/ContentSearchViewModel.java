package com.sinoll.synapse_app.ui.content;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.api.ApiException;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.model.ContentPage;
import com.sinoll.synapse_app.data.model.ContentQuery;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.data.repository.ContentRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

public final class ContentSearchViewModel extends ViewModel {

    private static final ContentType[] SEARCHABLE_TYPES = {
            ContentType.MOMENT,
            ContentType.IMAGE_GALLERY,
            ContentType.BLOG,
            ContentType.SHORT_VIDEO
    };

    private final ContentRepository repository =
            new ContentRepository(ApiClient.forApplication());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<List<ContentItem>> results =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<SearchError> error = new MutableLiveData<>();
    private final List<Future<?>> activeRequests = new ArrayList<>();

    private int requestGeneration;
    private int pendingRequests;
    private int failedRequests;
    private String firstErrorMessage;
    private List<ContentItem> collectedResults = new ArrayList<>();

    public LiveData<List<ContentItem>> getResults() {
        return results;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<SearchError> getError() {
        return error;
    }

    public void search(String value, String authorization) {
        String queryText = value == null ? "" : value.trim();
        final int generation;
        synchronized (this) {
            generation = ++requestGeneration;
            cancelActiveRequests();
            collectedResults = new ArrayList<>();
            pendingRequests = 0;
            failedRequests = 0;
            firstErrorMessage = null;
        }
        results.setValue(Collections.emptyList());
        error.setValue(null);
        if (queryText.isEmpty()) {
            loading.setValue(false);
            return;
        }

        loading.setValue(true);
        ContentQuery query = ContentQuery.builder()
                .perPage(20)
                .search(queryText)
                .build();
        synchronized (this) {
            if (generation != requestGeneration) {
                return;
            }
            pendingRequests = SEARCHABLE_TYPES.length;
            for (ContentType type : SEARCHABLE_TYPES) {
                activeRequests.add(repository.loadPage(type, query, authorization,
                        new ContentRepository.Callback<ContentPage>() {
                            @Override
                            public void onSuccess(ContentPage page) {
                                finishRequest(generation, page, null);
                            }

                            @Override
                            public void onError(Exception error) {
                                finishRequest(generation, null, error);
                            }
                        }));
            }
        }
    }

    private synchronized void finishRequest(int generation, ContentPage page,
                                            Exception error) {
        if (generation != requestGeneration) {
            return;
        }
        if (page != null) {
            collectedResults.addAll(page.getItems());
        } else {
            failedRequests++;
            if (firstErrorMessage == null && error instanceof ApiException) {
                firstErrorMessage = error.getMessage();
            }
        }
        pendingRequests--;
        if (pendingRequests > 0) {
            return;
        }

        List<ContentItem> sortedResults = new ArrayList<>(collectedResults);
        Collections.sort(sortedResults, (first, second) ->
                second.getCreatedAt().compareTo(first.getCreatedAt()));
        SearchError searchError = null;
        if (failedRequests == SEARCHABLE_TYPES.length) {
            searchError = new SearchError(false, firstErrorMessage);
        } else if (failedRequests > 0) {
            searchError = new SearchError(true, null);
        }
        activeRequests.clear();
        SearchError finalError = searchError;
        mainHandler.post(() -> {
            synchronized (ContentSearchViewModel.this) {
                if (generation != requestGeneration) {
                    return;
                }
            }
            results.setValue(sortedResults);
            loading.setValue(false);
            ContentSearchViewModel.this.error.setValue(finalError);
        });
    }

    public static final class SearchError {

        private final boolean partial;
        private final String message;

        private SearchError(boolean partial, String message) {
            this.partial = partial;
            this.message = message;
        }

        public boolean isPartial() {
            return partial;
        }

        @Nullable
        public String getMessage() {
            return message;
        }
    }

    private void cancelActiveRequests() {
        for (Future<?> request : activeRequests) {
            request.cancel(true);
        }
        activeRequests.clear();
    }

    @Override
    protected void onCleared() {
        synchronized (this) {
            requestGeneration++;
            cancelActiveRequests();
        }
        repository.close();
    }
}
