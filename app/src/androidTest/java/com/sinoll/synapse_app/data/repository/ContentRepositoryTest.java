package com.sinoll.synapse_app.data.repository;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ContentRepositoryTest {

    @Test
    public void readsVideoPathFromUpdatedUploadResponse() throws Exception {
        JSONObject response = new JSONObject()
                .put("kind", "video")
                .put("video_path", "/uploads/video/clip.mp4")
                .put("thumbnail_path", JSONObject.NULL)
                .put("duration_seconds", 18)
                .put("video_type", 2);

        assertEquals("/uploads/video/clip.mp4",
                ContentRepository.uploadedVideoPath(response));
        assertEquals("", ContentRepository.uploadedThumbnailPath(response));
    }

    @Test
    public void readsOptionalThumbnailPathWhenProvided() throws Exception {
        JSONObject response = new JSONObject()
                .put("thumbnail_path", "/uploads/thumb/video/clip.jpg");

        assertEquals("/uploads/thumb/video/clip.jpg",
                ContentRepository.uploadedThumbnailPath(response));
    }

    @Test
    public void readsImagePathFromDocumentedUploadResponse() throws Exception {
        JSONObject response = new JSONObject()
                .put("kind", "images")
                .put("imgs", new JSONArray().put(new JSONObject()
                        .put("path", "/uploads/images/photo.jpg")
                        .put("original_name", "photo.jpg")))
                .put("thumbnail_path", "/uploads/thumb/images/photo.jpg");

        assertEquals("/uploads/images/photo.jpg",
                ContentRepository.uploadedImagePath(response));
    }

    @Test
    public void createsShortVideoRequestWithDocumentedThumbnailField() throws Exception {
        JSONObject body = ContentRepository.buildVideoRequestBody(
                "/uploads/video/clip.mp4",
                "/uploads/thumb/video/clip.jpg",
                "周末散步",
                "记录周末风景",
                18,
                2,
                2);

        assertEquals("/uploads/video/clip.mp4", body.getString("video_path"));
        assertEquals("/uploads/thumb/video/clip.jpg", body.getString("thumbnail_path"));
        assertFalse(body.has("cover_path"));
        assertEquals(18, body.getInt("duration_seconds"));
        assertEquals(2, body.getInt("video_type"));
        assertEquals(1, body.getInt("status"));
        assertEquals(2, body.getInt("visibility"));
        assertTrue(body.has("title"));
        assertTrue(body.has("description"));
    }
}
