package com.sinoll.synapse_app.ui.content;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.api.ApiException;
import com.sinoll.synapse_app.data.model.Category;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.data.repository.ContentRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CreateContentViewModel extends ViewModel {

    private static final Pattern HASHTAG_PATTERN =
            Pattern.compile("(?<!#)#([^#]+)#(?!#)");
    private static final int MAX_TAGS = 20;

    private final ContentRepository repository =
            new ContentRepository(ApiClient.forApplication());
    private final MutableLiveData<Boolean> publishing = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> createdId = new MutableLiveData<>();
    private final MutableLiveData<JSONObject> uploadedVideo = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();

    private Future<?> activeRequest;

    public LiveData<Boolean> getPublishing() {
        return publishing;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Integer> getCreatedId() {
        return createdId;
    }

    public LiveData<JSONObject> getUploadedVideo() {
        return uploadedVideo;
    }

    public void clearCreated() {
        createdId.setValue(null);
    }

    public void clearUploadedVideo() {
        uploadedVideo.setValue(null);
    }

    public LiveData<List<Category>> getCategories() {
        return categories;
    }

    public void loadCategories() {
        repository.loadCategories(new ContentRepository.Callback<List<Category>>() {
            @Override
            public void onSuccess(List<Category> value) {
                categories.postValue(value);
            }

            @Override
            public void onError(Exception error) {
                // A missing category list must not block publishing; the user can
                // still publish without selecting a category.
                categories.postValue(java.util.Collections.emptyList());
            }
        });
    }

    public void publishText(ContentType type, String title, String summary, String plainContent,
                            String tags, int cateId, int visibility, String authorization) {
        if (type != ContentType.BLOG && type != ContentType.MOMENT) {
            fail("不支持的内容类型。");
            return;
        }
        if (!isValidVisibility(visibility)) {
            fail("请选择有效的可见范围。");
            return;
        }
        String content = toHtml(plainContent);
        if (content.isEmpty()) {
            fail("请填写正文内容。");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("content", content);
            String normalizedTitle = trimToNull(title);
            if (normalizedTitle != null) {
                body.put("title", normalizedTitle);
            }
            String normalizedSummary = trimToNull(summary);
            if (type == ContentType.BLOG && normalizedSummary != null) {
                body.put("summary", normalizedSummary);
            }
            if (type == ContentType.BLOG && cateId > 0) {
                body.put("cate_id", cateId);
            }
            JSONArray tagArray = parseTags(tags);
            if (tagArray.length() > 0) {
                body.put("tags", tagArray);
            }
            body.put("status", 1);
            body.put("visibility", visibility);
        } catch (JSONException error) {
            fail("无法构建发布内容。");
            return;
        }
        submit(repository.create(type, body, authorization, callback()));
    }

    public void publishGallery(String caption, List<ContentRepository.UploadFile> files,
                               int visibility, String authorization) {
        if (files == null || files.isEmpty()) {
            fail("请至少选择一张图片。");
            return;
        }
        if (!isValidVisibility(visibility)) {
            fail("请选择有效的可见范围。");
            return;
        }
        submit(repository.createGallery(caption, files, visibility, authorization, callback()));
    }

    public void publishBlog(String title, String summary, String content, String tags,
                            int cateId, List<ContentRepository.UploadFile> inlineImages,
                            int visibility, String authorization) {
        if (content == null || content.trim().isEmpty()) {
            fail("请填写正文内容。");
            return;
        }
        if (!isValidVisibility(visibility)) {
            fail("请选择有效的可见范围。");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("content", content);
            String normalizedTitle = trimToNull(title);
            if (normalizedTitle != null) {
                body.put("title", normalizedTitle);
            }
            String normalizedSummary = trimToNull(summary);
            if (normalizedSummary != null) {
                body.put("summary", normalizedSummary);
            }
            if (cateId > 0) {
                body.put("cate_id", cateId);
            }
            JSONArray tagArray = parseTags(tags);
            if (tagArray.length() > 0) {
                body.put("tags", tagArray);
            }
            body.put("status", 1);
            body.put("visibility", visibility);
        } catch (JSONException error) {
            fail("无法构建发布内容。");
            return;
        }
        submit(repository.createBlog(body, inlineImages, authorization, callback()));
    }

    public void publishMoment(String content, List<ContentRepository.UploadFile> inlineImages,
                              List<String> tags, int visibility, String authorization) {
        if (content == null || content.trim().isEmpty()) {
            fail("请填写正文内容。");
            return;
        }
        if (!isValidVisibility(visibility)) {
            fail("请选择有效的可见范围。");
            return;
        }
        submit(repository.createMoment(content, inlineImages, tags, visibility,
                authorization, callback()));
    }

    public void uploadVideo(ContentRepository.UploadStream video, String authorization) {
        if (video == null) {
            fail("请选择要发布的视频。");
            return;
        }
        uploadedVideo.postValue(null);
        errorMessage.postValue(null);
        publishing.postValue(true);
        activeRequest = repository.uploadVideo(video, authorization,
                new ContentRepository.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject value) {
                        publishing.postValue(false);
                        uploadedVideo.postValue(value);
                    }

                    @Override
                    public void onError(Exception error) {
                        publishing.postValue(false);
                        errorMessage.postValue(errorMessageFor(error));
                    }
                });
    }

    public void publishUploadedVideo(String videoPath, String coverPath,
                                     String title, String description, long durationSeconds,
                                     int videoType, int visibility, String authorization) {
        if (videoPath == null || videoPath.trim().isEmpty()) {
            fail("请先上传视频。");
            return;
        }
        if (!isValidVisibility(visibility)) {
            fail("请选择有效的可见范围。");
            return;
        }
        submit(repository.createVideo(videoPath, coverPath, title, description,
                durationSeconds, videoType, visibility, authorization, callback()));
    }

    public void publishVideo(String title, String description,
                             ContentRepository.UploadStream video, long durationSeconds,
                             int videoType, int visibility, String authorization) {
        if (video == null) {
            fail("请选择要发布的视频。");
            return;
        }
        if (!isValidVisibility(visibility)) {
            fail("请选择有效的可见范围。");
            return;
        }
        submit(repository.createVideo(title, description, video, durationSeconds,
                videoType, visibility, authorization, callback()));
    }

    private static boolean isValidVisibility(int visibility) {
        return visibility >= 0 && visibility <= 2;
    }

    public void fail(String message) {
        publishing.postValue(false);
        errorMessage.postValue(message == null || message.trim().isEmpty()
                ? "发布失败，请稍后重试。" : message);
    }

    private void submit(Future<?> request) {
        if (activeRequest != null) {
            activeRequest.cancel(true);
        }
        errorMessage.postValue(null);
        publishing.postValue(true);
        activeRequest = request;
    }

    private ContentRepository.Callback<JSONObject> callback() {
        return new ContentRepository.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                publishing.postValue(false);
                createdId.postValue(data == null ? 0 : data.optInt("id", 0));
            }

            @Override
            public void onError(Exception error) {
                publishing.postValue(false);
                errorMessage.postValue(errorMessageFor(error));
            }
        };
    }

    private static String errorMessageFor(Exception error) {
        if (error instanceof ApiException) {
            ApiException apiError = (ApiException) error;
            StringBuilder message = new StringBuilder(error.getMessage() == null
                    ? "" : error.getMessage().trim());
            for (Map.Entry<String, List<String>> fieldError
                    : apiError.getFieldErrors().entrySet()) {
                for (String detail : fieldError.getValue()) {
                    if (message.length() > 0) {
                        message.append('\n');
                    }
                    message.append(fieldError.getKey()).append(": ").append(detail);
                }
            }
            if (message.length() > 0) {
                return message.toString();
            }
        }
        if (error instanceof IOException) {
            return "无法连接服务器，请检查网络连接后重试。";
        }
        if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
            return "发布失败：" + error.getMessage().trim();
        }
        return "发布失败，请稍后重试。";
    }

    private static String toHtml(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        String[] lines = normalized.split("\n", -1);
        StringBuilder html = new StringBuilder();
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (html.length() > 0) {
                html.append('\n');
            }
            html.append("<p>").append(escapeHtml(line)).append("</p>");
        }
        return html.toString();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static JSONArray parseTags(String value) {
        JSONArray tags = new JSONArray();
        if (value == null) {
            return tags;
        }
        for (String tag : value.split("[,，\\s]+")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty() && tags.length() < 20) {
                tags.put(trimmed);
            }
        }
        return tags;
    }

    static List<String> extractHashtags(String content) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (content == null) {
            return new ArrayList<>();
        }
        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        while (matcher.find() && tags.size() < MAX_TAGS) {
            String tag = matcher.group(1).trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return new ArrayList<>(tags);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @Override
    protected void onCleared() {
        if (activeRequest != null) {
            activeRequest.cancel(true);
        }
        repository.close();
    }
}
