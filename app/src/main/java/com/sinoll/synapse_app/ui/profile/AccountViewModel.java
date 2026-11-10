package com.sinoll.synapse_app.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.api.ApiException;
import com.sinoll.synapse_app.data.repository.AccountRepository;
import com.sinoll.synapse_app.data.session.SessionStore;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Future;

public final class AccountViewModel extends AndroidViewModel {

    private final SessionStore sessionStore;
    private final AccountRepository repository;
    private final MutableLiveData<JSONObject> user = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private Future<?> activeRequest;
    private volatile int generation;

    public AccountViewModel(@NonNull Application application) {
        super(application);
        sessionStore = new SessionStore(application);
        repository = new AccountRepository(ApiClient.forApplication(), sessionStore);
    }

    public LiveData<JSONObject> getUser() {
        return user;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public void refreshSession() {
        int requestGeneration = beginRequest();
        activeRequest = repository.getCurrentUser(new AccountRepository.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                if (requestGeneration == generation) {
                    user.postValue(result);
                    message.postValue(null);
                    loading.postValue(false);
                }
            }

            @Override
            public void onError(Exception error) {
                if (requestGeneration != generation) {
                    return;
                }
                if (error instanceof ApiException
                        && ((ApiException) error).getHttpStatus() == 401) {
                    try {
                        sessionStore.clear();
                    } catch (Exception clearError) {
                        error.addSuppressed(clearError);
                    }
                    user.postValue(null);
                }
                message.postValue(userMessage(error));
                loading.postValue(false);
            }
        });
    }

    public void login(String username, String password) {
        int requestGeneration = beginRequest();
        activeRequest = repository.login(username, password,
                callback(requestGeneration, true));
    }

    public void requestRegistrationCode(String email) {
        int requestGeneration = beginRequest();
        activeRequest = repository.requestRegistrationCode(email,
                callback(requestGeneration, false));
    }

    public void register(String username, String nickname, String email, String code,
                         String password, String confirmPassword) {
        int requestGeneration = beginRequest();
        activeRequest = repository.register(username, nickname, email, code, password,
                confirmPassword, callback(requestGeneration, false));
    }

    public void requestPasswordResetCode(String email) {
        int requestGeneration = beginRequest();
        activeRequest = repository.requestPasswordResetCode(email,
                callback(requestGeneration, false));
    }

    public void resetPassword(String email, String code, String password,
                              String confirmPassword) {
        int requestGeneration = beginRequest();
        activeRequest = repository.resetPassword(email, code, password, confirmPassword,
                callback(requestGeneration, false));
    }

    public void logout() {
        int requestGeneration = beginRequest();
        activeRequest = repository.logout(new AccountRepository.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                if (requestGeneration == generation) {
                    user.postValue(null);
                    message.postValue("已退出登录。");
                    loading.postValue(false);
                }
            }

            @Override
            public void onError(Exception error) {
                if (requestGeneration == generation) {
                    user.postValue(null);
                    message.postValue("已清除本机登录状态；服务器退出通知失败。");
                    loading.postValue(false);
                }
            }
        });
    }

    public Integer getCurrentUserId() {
        JSONObject currentUser = user.getValue();
        if (currentUser == null) {
            return null;
        }
        int id = currentUser.optInt("id", 0);
        return id > 0 ? id : null;
    }

    public String getAuthorizationHeader() {
        try {
            return sessionStore.getAuthorizationHeader();
        } catch (Exception error) {
            message.postValue("无法读取本地登录状态，请重新登录。");
            return null;
        }
    }

    private int beginRequest() {
        int nextGeneration = ++generation;
        if (activeRequest != null) {
            activeRequest.cancel(true);
        }
        loading.setValue(true);
        message.setValue(null);
        return nextGeneration;
    }

    private AccountRepository.Callback<JSONObject> callback(int requestGeneration,
                                                             boolean isLogin) {
        return new AccountRepository.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                if (requestGeneration != generation) {
                    return;
                }
                if (isLogin) {
                    user.postValue(result);
                    message.postValue("登录成功。");
                } else {
                    message.postValue("操作成功，请按提示继续。");
                }
                loading.postValue(false);
            }

            @Override
            public void onError(Exception error) {
                if (requestGeneration == generation) {
                    message.postValue(userMessage(error));
                    loading.postValue(false);
                }
            }
        };
    }

    private static String userMessage(Exception error) {
        if (error instanceof ApiException) {
            return error.getMessage();
        }
        if (error instanceof IOException) {
            return "无法连接服务器，请检查网络和 HTTPS 证书。";
        }
        return "操作失败，请稍后重试。";
    }

    @Override
    protected void onCleared() {
        generation++;
        if (activeRequest != null) {
            activeRequest.cancel(true);
        }
        repository.close();
    }
}
