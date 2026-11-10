package com.sinoll.synapse_app.ui.content;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sinoll.synapse_app.BuildConfig;
import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.api.ApiException;
import com.sinoll.synapse_app.data.model.ContentPage;
import com.sinoll.synapse_app.data.model.ContentQuery;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.data.repository.ContentRepository;

import java.io.IOException;
import java.util.concurrent.Future;

public final class ContentListViewModel extends ViewModel {

    private final ContentRepository repository =
            new ContentRepository(new ApiClient(BuildConfig.API_BASE_URL));
    private final MutableLiveData<ContentPage> page = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private ContentType contentType;
    private String search = "";
    private String tag = "";
    private String authorization;
    private Integer userId;
    private Future<?> activeRequest;
    private volatile int requestGeneration;

    public LiveData<ContentPage> getPage() {
        return page;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void setSession(String authorizationHeader, Integer ownerId) {
        authorization = authorizationHeader;
        userId = ownerId;
    }

    public void setContentType(ContentType value) {
        if (value == null) {
            throw new IllegalArgumentException("contentType must not be null.");
        }
        if (contentType != value) {
            contentType = value;
            search = "";
            tag = "";
            page.setValue(null);
            loadFirstPage("");
        } else if (page.getValue() == null) {
            loadFirstPage(search);
        }
    }

    public void loadFirstPage(String searchText) {
        loadFirstPage(searchText, "");
    }

    public void loadFirstPage(String searchText, String tagText) {
        search = searchText == null ? "" : searchText.trim();
        tag = tagText == null ? "" : tagText.trim();
        requestPage(1, false);
    }

    public void loadNextPage() {
        ContentPage current = page.getValue();
        if (current == null || !current.hasNextPage() || Boolean.TRUE.equals(loading.getValue())) {
            return;
        }
        requestPage(current.getPage() + 1, true);
    }

    private void requestPage(int pageNumber, boolean append) {
        if (contentType == null) {
            throw new IllegalStateException("Set a content type before loading content.");
        }
        final int generation = ++requestGeneration;
        if (activeRequest != null) {
            activeRequest.cancel(true);
        }
        ContentQuery query = ContentQuery.builder()
                .page(pageNumber)
                .perPage(20)
                .search(search)
                .tag(tag)
                .userId(userId)
                .build();

        errorMessage.setValue(null);
        loading.setValue(true);
        activeRequest = repository.loadPage(contentType, query, authorization,
                new ContentRepository.Callback<ContentPage>() {
                    @Override
                    public void onSuccess(ContentPage result) {
                        if (generation != requestGeneration) {
                            return;
                        }
                        ContentPage current = page.getValue();
                        page.postValue(append && current != null
                                ? ContentPage.append(current, result) : result);
                        loading.postValue(false);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (generation != requestGeneration) {
                            return;
                        }
                        errorMessage.postValue(errorMessageFor(error));
                        loading.postValue(false);
                    }
                });
    }

    private static String errorMessageFor(Exception error) {
        if (error instanceof ApiException) {
            return error.getMessage();
        }
        if (error instanceof IOException) {
            return "无法连接服务器，请检查网络连接和 HTTPS 证书配置。";
        }
        return "加载内容失败，请稍后重试。";
    }

    @Override
    protected void onCleared() {
        requestGeneration++;
        if (activeRequest != null) {
            activeRequest.cancel(true);
        }
        repository.close();
    }
}
