package com.sinoll.synapse_app.ui.content;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.api.ApiException;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.data.repository.ContentRepository;

import java.io.IOException;
import java.util.concurrent.Future;

public final class ContentDetailViewModel extends ViewModel {

    private final ContentRepository repository =
            new ContentRepository(ApiClient.forApplication());
    private final MutableLiveData<ContentItem> item = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private Future<?> activeRequest;
    private volatile int requestGeneration;
    private String loadedKey;

    public LiveData<ContentItem> getItem() {
        return item;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void load(ContentType type, int id) {
        if (type == null || id <= 0) {
            throw new IllegalArgumentException("A content type and positive id are required.");
        }
        String key = type.getEndpoint() + ":" + id;
        if (key.equals(loadedKey)) {
            return;
        }
        loadedKey = key;
        final int generation = ++requestGeneration;
        if (activeRequest != null) {
            activeRequest.cancel(true);
        }
        loading.setValue(true);
        errorMessage.setValue(null);
        activeRequest = repository.loadDetail(type, id, null,
                new ContentRepository.Callback<ContentItem>() {
                    @Override
                    public void onSuccess(ContentItem result) {
                        if (generation != requestGeneration) {
                            return;
                        }
                        item.postValue(result);
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
