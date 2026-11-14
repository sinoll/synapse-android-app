package com.sinoll.synapse_app.data.repository;

import android.util.Log;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.api.ApiException;
import com.sinoll.synapse_app.data.model.Category;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.model.ContentPage;
import com.sinoll.synapse_app.data.model.ContentQuery;
import com.sinoll.synapse_app.data.model.ContentType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ContentRepository implements AutoCloseable {

    private static final String TAG = "ContentRepository";

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
            } catch (Exception error) {
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
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public Future<?> loadCategories(Callback<List<Category>> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback is required.");
        }
        return executor.submit(() -> {
            try {
                callback.onSuccess(Category.listFromJson(apiClient.get("cate", "", null)));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public Future<?> create(ContentType type, JSONObject body, String authorization,
                            Callback<JSONObject> callback) {
        if (type == null || body == null || callback == null) {
            throw new IllegalArgumentException("type, body, and callback are required.");
        }
        return executor.submit(() -> {
            try {
                callback.onSuccess(apiClient.post(type.getEndpoint(), body, authorization));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public Future<?> createBlog(JSONObject body, List<UploadFile> inlineImages,
                                String authorization, Callback<JSONObject> callback) {
        if (body == null || callback == null) {
            throw new IllegalArgumentException("body and callback are required.");
        }
        return executor.submit(() -> {
            try {
                String content = body.optString("content", "");
                List<String> imagePaths = new ArrayList<>();
                if (inlineImages != null) {
                    for (UploadFile file : inlineImages) {
                        JSONObject upload = apiClient.upload("blog", file.getFileName(),
                                file.getMimeType(), file.getData(), authorization);
                        String path = uploadedImagePath(upload);
                        if (path.isEmpty()) {
                            throw new IllegalStateException("图片上传响应缺少路径。");
                        }
                        imagePaths.add(path);
                    }
                }
                body.put("content", replaceImagePlaceholders(content, imagePaths));
                callback.onSuccess(apiClient.post("blog", body, authorization));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public Future<?> createMoment(String content, List<UploadFile> inlineImages,
                                  List<String> tags, int visibility, String authorization,
                                  Callback<JSONObject> callback) {
        if (content == null || callback == null) {
            throw new IllegalArgumentException("content and callback are required.");
        }
        return executor.submit(() -> {
            try {
                String publishedContent = content;
                List<String> imagePaths = new ArrayList<>();
                if (inlineImages != null) {
                    for (UploadFile file : inlineImages) {
                        JSONObject upload = apiClient.upload("moment", file.getFileName(),
                                file.getMimeType(), file.getData(), authorization);
                        String path = uploadedImagePath(upload);
                        if (path.isEmpty()) {
                            throw new IllegalStateException("图片上传响应缺少路径。");
                        }
                        imagePaths.add(path);
                    }
                }
                publishedContent = replaceImagePlaceholders(publishedContent, imagePaths);
                JSONObject body = new JSONObject();
                body.put("content", publishedContent);
                if (tags != null && !tags.isEmpty()) {
                    JSONArray tagArray = new JSONArray();
                    for (String tag : tags) {
                        if (tag != null && !tag.trim().isEmpty()) {
                            tagArray.put(tag.trim());
                        }
                    }
                    if (tagArray.length() > 0) {
                        body.put("tags", tagArray);
                    }
                }
                body.put("status", 1);
                body.put("visibility", visibility);
                callback.onSuccess(apiClient.post("moment", body, authorization));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public Future<?> createGallery(String caption, List<UploadFile> files, int visibility,
                                   String authorization, Callback<JSONObject> callback) {
        if (files == null || files.isEmpty() || callback == null) {
            throw new IllegalArgumentException("At least one image and a callback are required.");
        }
        return executor.submit(() -> {
            try {
                JSONArray paths = new JSONArray();
                for (UploadFile file : files) {
                    JSONObject upload = apiClient.upload("images", file.getFileName(),
                            file.getMimeType(), file.getData(), authorization);
                    String path = uploadedImagePath(upload);
                    if (path.isEmpty()) {
                        throw new IllegalStateException("图片上传响应缺少路径。");
                    }
                    paths.put(path);
                }
                JSONObject body = new JSONObject();
                body.put("caption", caption == null ? "" : caption.trim());
                body.put("file_paths", paths);
                body.put("status", 1);
                body.put("visibility", visibility);
                callback.onSuccess(apiClient.post("images", body, authorization));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public Future<?> uploadVideo(UploadStream video, String authorization,
                                 Callback<JSONObject> callback) {
        if (video == null || callback == null) {
            throw new IllegalArgumentException("video and callback are required.");
        }
        return executor.submit(() -> {
            try (InputStream input = video.open()) {
                JSONObject upload = apiClient.upload("video", video.getFileName(),
                        video.getMimeType(), input, video.getLength(), authorization);
                String videoPath = uploadedVideoPath(upload);
                if (videoPath.trim().isEmpty()) {
                    throw new IllegalStateException("视频上传响应缺少路径。");
                }
                callback.onSuccess(upload);
            } catch (Exception error) {
                if (error instanceof ApiException) {
                    ApiException apiError = (ApiException) error;
                    Log.e(TAG, "Failed while uploading video (HTTP "
                            + apiError.getHttpStatus() + ", API " + apiError.getApiCode()
                            + ", fields " + apiError.getFieldErrors() + ")", error);
                } else {
                    Log.e(TAG, "Failed while uploading video", error);
                }
                callback.onError(error);
            }
        });
    }

    public Future<?> createVideo(String videoPath, String coverPath, String title,
                                 String description, long durationSeconds, int videoType,
                                 int visibility, String authorization,
                                 Callback<JSONObject> callback) {
        if (videoPath == null || videoPath.trim().isEmpty() || callback == null) {
            throw new IllegalArgumentException("video path and callback are required.");
        }
        return executor.submit(() -> {
            try {
                JSONObject body = buildVideoRequestBody(videoPath, coverPath, title,
                        description, durationSeconds, videoType, visibility);
                callback.onSuccess(apiClient.post("short_video", body, authorization));
            } catch (Exception error) {
                if (error instanceof ApiException) {
                    ApiException apiError = (ApiException) error;
                    Log.e(TAG, "Failed while creating short video (HTTP "
                            + apiError.getHttpStatus() + ", API " + apiError.getApiCode()
                            + ", fields " + apiError.getFieldErrors() + ")", error);
                } else {
                    Log.e(TAG, "Failed while creating short video", error);
                }
                callback.onError(error);
            }
        });
    }

    public Future<?> createVideo(String title, String description, UploadStream video,
                                 long durationSeconds, int videoType, int visibility,
                                 String authorization, Callback<JSONObject> callback) {
        if (video == null || callback == null) {
            throw new IllegalArgumentException("video and callback are required.");
        }
        return uploadVideo(video, authorization, new Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject upload) {
                String videoPath = uploadedVideoPath(upload);
                String coverPath = uploadedThumbnailPath(upload);
                createVideo(videoPath, coverPath, title, description, durationSeconds,
                        videoType, visibility, authorization, callback);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    static JSONObject buildVideoRequestBody(String videoPath, String thumbnailPath,
                                            String title, String description,
                                            long durationSeconds, int videoType, int visibility)
            throws org.json.JSONException {
        JSONObject body = new JSONObject();
        body.put("video_path", videoPath);
        String normalizedTitle = trimToNull(title);
        if (normalizedTitle != null) {
            body.put("title", normalizedTitle);
        }
        String normalizedDescription = trimToNull(description);
        if (normalizedDescription != null) {
            body.put("description", normalizedDescription);
        }
        String normalizedThumbnailPath = trimToNull(thumbnailPath);
        if (normalizedThumbnailPath != null) {
            body.put("thumbnail_path", normalizedThumbnailPath);
        }
        if (durationSeconds > 0) {
            body.put("duration_seconds", durationSeconds);
        }
        body.put("video_type", videoType == 1 ? 1 : 2);
        body.put("status", 1);
        body.put("visibility", visibility);
        return body;
    }

    static String uploadedVideoPath(JSONObject upload) {
        String path = upload.optString("video_path", "");
        if (path.trim().isEmpty()) {
            path = upload.optString("path", "");
        }
        if (path.trim().isEmpty()) {
            path = upload.optString("file_path", "");
        }
        return path;
    }

    static String uploadedImagePath(JSONObject upload) {
        if (upload == null) {
            return "";
        }
        JSONArray images = upload.optJSONArray("imgs");
        if (images != null && images.length() > 0) {
            JSONObject image = images.optJSONObject(0);
            if (image != null) {
                String path = image.optString("path", "");
                if (!path.trim().isEmpty()) {
                    return path;
                }
            }
        }
        String path = upload.optString("path", "");
        return path.trim().isEmpty() ? upload.optString("file_path", "") : path;
    }

    static String uploadedThumbnailPath(JSONObject upload) {
        if (upload == null || !upload.has("thumbnail_path")
                || upload.isNull("thumbnail_path")) {
            return "";
        }
        return upload.optString("thumbnail_path", "");
    }

    static String replaceImagePlaceholders(String content, List<String> imagePaths) {
        String result = content;
        for (int i = 0; i < imagePaths.size(); i++) {
            result = result.replace("__SYNAPSE_IMAGE_" + i + "__", imagePaths.get(i));
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
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

    public interface UploadStream {

        String getFileName();

        String getMimeType();

        /**
         * Total byte count, or {@code -1} when it cannot be determined.
         */
        long getLength();

        InputStream open() throws IOException;
    }

    public static final class UploadFile {

        private final String fileName;
        private final String mimeType;
        private final byte[] data;

        public UploadFile(String fileName, String mimeType, byte[] data) {
            if (fileName == null || fileName.trim().isEmpty()
                    || mimeType == null || mimeType.trim().isEmpty()
                    || data == null || data.length == 0) {
                throw new IllegalArgumentException(
                        "Upload file name, mime type, and data are required.");
            }
            this.fileName = fileName.trim();
            this.mimeType = mimeType.trim();
            this.data = data;
        }

        public String getFileName() {
            return fileName;
        }

        public String getMimeType() {
            return mimeType;
        }

        public byte[] getData() {
            return data;
        }
    }

    public interface Callback<T> {
        void onSuccess(T value);

        void onError(Exception error);
    }
}
