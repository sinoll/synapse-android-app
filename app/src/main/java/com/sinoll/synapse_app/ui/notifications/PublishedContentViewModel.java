package com.sinoll.synapse_app.ui.notifications;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.api.ApiException;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.model.ContentPage;
import com.sinoll.synapse_app.data.model.ContentQuery;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.data.repository.ContentRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

public final class PublishedContentViewModel extends AndroidViewModel {

    private static final int PAGE_SIZE = 20;

    private final ContentRepository repository =
            new ContentRepository(ApiClient.forApplication());
    private final MutableLiveData<List<ContentItem>> items =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<ContentType, Integer> nextPages = new EnumMap<>(ContentType.class);
    private final List<Future<?>> requests = new ArrayList<>();
    private Integer userId;
    private String authorization;
    private volatile int generation;

    public PublishedContentViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<ContentItem>> getItems() {
        return items;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void loadFirstPage(int ownerId, String authorizationHeader) {
        if (ownerId <= 0 || authorizationHeader == null
                || authorizationHeader.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner and authorization are required.");
        }
        userId = ownerId;
        authorization = authorizationHeader;
        nextPages.clear();
        for (ContentType type : ContentType.values()) {
            nextPages.put(type, 1);
        }
        items.setValue(Collections.emptyList());
        requestPages(new EnumMap<>(nextPages), false);
    }

    public void refresh() {
        if (userId != null && authorization != null) {
            loadFirstPage(userId, authorization);
        }
    }

    public void loadNextPage() {
        if (userId == null || authorization == null
                || Boolean.TRUE.equals(loading.getValue()) || nextPages.isEmpty()) {
            return;
        }
        requestPages(new EnumMap<>(nextPages), true);
    }

    public void clear() {
        generation++;
        cancelRequests();
        userId = null;
        authorization = null;
        nextPages.clear();
        items.setValue(Collections.emptyList());
        errorMessage.setValue(null);
        loading.setValue(false);
    }

    private void requestPages(Map<ContentType, Integer> pages, boolean append) {
        int requestGeneration = ++generation;
        cancelRequests();
        errorMessage.setValue(null);
        loading.setValue(true);
        Batch batch = new Batch(requestGeneration, append, pages.size());
        for (Map.Entry<ContentType, Integer> entry : pages.entrySet()) {
            ContentType type = entry.getKey();
            ContentQuery query = ContentQuery.builder()
                    .page(entry.getValue())
                    .perPage(PAGE_SIZE)
                    .userId(userId)
                    .build();
            Future<?> request = repository.loadPage(type, query, authorization,
                    new ContentRepository.Callback<ContentPage>() {
                        @Override
                        public void onSuccess(ContentPage page) {
                            batch.complete(type, page, null);
                        }

                        @Override
                        public void onError(Exception error) {
                            batch.complete(type, null, error);
                        }
                    });
            requests.add(request);
        }
    }

    private void finishBatch(Batch batch) {
        if (batch.requestGeneration != generation) {
            return;
        }
        List<ContentItem> merged = new ArrayList<>();
        if (batch.append) {
            List<ContentItem> current = items.getValue();
            if (current != null) {
                merged.addAll(current);
            }
        }
        Set<String> seen = new HashSet<>();
        for (ContentItem item : merged) {
            seen.add(item.getType() + ":" + item.getId());
        }
        for (Map.Entry<ContentType, ContentPage> entry : batch.pages.entrySet()) {
            ContentPage page = entry.getValue();
            if (page.hasNextPage()) {
                nextPages.put(entry.getKey(), page.getPage() + 1);
            } else {
                nextPages.remove(entry.getKey());
            }
            for (ContentItem item : page.getItems()) {
                String key = item.getType() + ":" + item.getId();
                if (item.getStatus() == 1 && seen.add(key)) {
                    merged.add(item);
                }
            }
        }
        merged.sort(Comparator.comparing(ContentItem::getCreatedAt).reversed());
        items.setValue(Collections.unmodifiableList(merged));
        if (!batch.errors.isEmpty()) {
            String message = batch.errors.size() == batch.requestCount
                    ? userMessage(batch.errors.get(0))
                    : "部分内容加载失败，请下拉刷新重试。";
            errorMessage.setValue(message);
        }
        loading.setValue(false);
    }

    private void cancelRequests() {
        for (Future<?> request : requests) {
            request.cancel(true);
        }
        requests.clear();
    }

    private static String userMessage(Exception error) {
        if (error instanceof ApiException) {
            return error.getMessage();
        }
        if (error instanceof IOException) {
            return "无法连接服务器，请检查网络和 HTTPS 证书。";
        }
        return "加载内容失败，请稍后重试。";
    }

    @Override
    protected void onCleared() {
        generation++;
        cancelRequests();
        repository.close();
    }

    private final class Batch {
        private final int requestGeneration;
        private final boolean append;
        private final int requestCount;
        private final Map<ContentType, ContentPage> pages = new EnumMap<>(ContentType.class);
        private final List<Exception> errors = new ArrayList<>();
        private int pending;

        Batch(int requestGeneration, boolean append, int requestCount) {
            this.requestGeneration = requestGeneration;
            this.append = append;
            this.requestCount = requestCount;
            pending = requestCount;
        }

        void complete(ContentType type, ContentPage page, Exception error) {
            boolean finished;
            synchronized (this) {
                if (page != null) {
                    pages.put(type, page);
                }
                if (error != null) {
                    errors.add(error);
                }
                finished = --pending == 0;
            }
            if (finished) {
                mainHandler.post(() -> finishBatch(this));
            }
        }
    }
}
