package com.sinoll.synapse_app.data.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ContentItem {

    private final int id;
    private final String type;
    private final String title;
    private final int status;
    private final int visibility;
    private final int likeCount;
    private final int views;
    private final boolean liked;
    private final String authorName;
    private final String coverUrl;
    private final String videoUrl;
    private final boolean verticalVideo;
    private final String createdAt;
    private final String summary;
    private final String text;
    private final List<String> tags;
    private final JSONObject fields;

    private ContentItem(JSONObject json) throws JSONException {
        id = json.getInt("id");
        type = json.optString("type", "");
        title = json.optString("title", "");
        status = json.optInt("status", 1);
        visibility = json.optInt("visibility", 2);
        likeCount = json.optInt("like_count", 0);
        views = json.optInt("views", 0);
        liked = json.optBoolean("liked", false);

        JSONObject author = json.optJSONObject("author");
        authorName = author == null ? "" : author.optString("nickname",
                author.optString("username", ""));
        coverUrl = resolveCoverUrl(json, type);
        videoUrl = firstNonEmpty(json.optString("video_url", ""),
                json.optString("url", ""));
        verticalVideo = json.has("is_vertical")
                ? json.optBoolean("is_vertical", true)
                : json.optInt("video_type", 2) != 1;
        createdAt = json.optString("created_at", "");
        summary = json.optString("summary", "");
        text = json.optString("text", "");
        tags = parseTags(json.optJSONArray("tags"));
        fields = new JSONObject(json.toString());
    }

    public static ContentItem fromJson(JSONObject json) throws JSONException {
        if (json == null) {
            throw new JSONException("Content item must be an object.");
        }
        return new ContentItem(json);
    }

    public int getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public int getStatus() {
        return status;
    }

    public int getVisibility() {
        return visibility;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getViews() {
        return views;
    }

    public boolean isLiked() {
        return liked;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public boolean isVerticalVideo() {
        return verticalVideo;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getSummary() {
        return summary;
    }

    public String getText() {
        return text;
    }

    public List<String> getTags() {
        return tags;
    }

    public JSONObject getFields() {
        try {
            return new JSONObject(fields.toString());
        } catch (JSONException error) {
            throw new IllegalStateException("Content fields could not be copied.", error);
        }
    }

    private static List<String> parseTags(JSONArray values) {
        if (values == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(values.length());
        for (int i = 0; i < values.length(); i++) {
            Object value = values.opt(i);
            String tag;
            if (value instanceof JSONObject) {
                JSONObject object = (JSONObject) value;
                tag = object.optString("name", object.optString("slug", ""));
            } else {
                tag = value == null ? "" : String.valueOf(value);
            }
            if (!tag.isEmpty()) {
                result.add(tag);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()
                    && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private static String resolveCoverUrl(JSONObject json, String type) {
        String galleryImage = firstGalleryImage(json.optJSONArray("images"));
        if ("short_video".equals(type)) {
            return firstNonEmpty(json.optString("cover_url", ""),
                    json.optString("cover_path", ""), galleryImage);
        }

        String thumbnail = firstNonDefaultCover(
                json.optString("thumb_url", ""), json.optString("thumb", ""));
        if (!thumbnail.isEmpty()) {
            return thumbnail;
        }
        thumbnail = firstNonDefaultCover(galleryImage);
        if (!thumbnail.isEmpty()) {
            return thumbnail;
        }
        return firstNonDefaultCover(json.optString("cover_url", ""));
    }

    private static String firstNonDefaultCover(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()
                    && !"null".equalsIgnoreCase(value.trim())
                    && !isDefaultCover(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean isDefaultCover(String value) {
        return value.toLowerCase(Locale.ROOT).contains("/images/default-cover.png");
    }

    private static String firstGalleryImage(JSONArray images) {
        if (images == null || images.length() == 0) {
            return "";
        }
        JSONObject image = images.optJSONObject(0);
        if (image == null) {
            return "";
        }
        return firstNonEmpty(image.optString("thumbnail_url", ""),
                image.optString("file_url", ""));
    }
}
