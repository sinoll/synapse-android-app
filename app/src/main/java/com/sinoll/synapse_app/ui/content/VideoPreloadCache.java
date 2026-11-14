package com.sinoll.synapse_app.ui.content;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.model.ContentItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class VideoPreloadCache {

    private static final String TAG = "VideoPreloadCache";
    private static final int PRELOAD_COUNT = VideoPagerAdapter.PRELOAD_COUNT;
    private static final int MAX_CACHE_FILES = 12;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Set<String> ACTIVE_DOWNLOADS =
            Collections.synchronizedSet(new HashSet<>());
    private static final ApiClient API_CLIENT = ApiClient.forApplication();

    private final File cacheDirectory;

    VideoPreloadCache(@NonNull Context context) {
        cacheDirectory = new File(context.getApplicationContext().getCacheDir(), "video-preload");
    }

    int preloadFollowing(List<ContentItem> items, int currentPosition) {
        List<String> urls = new ArrayList<>();
        for (ContentItem item : items) {
            urls.add(API_CLIENT.resolveMediaUrl(item.getVideoUrl()));
        }
        List<String> upcoming = selectUpcomingVideoUrls(urls, currentPosition, PRELOAD_COUNT);
        for (String url : upcoming) {
            prefetch(url);
        }
        return upcoming.size();
    }

    Uri getPlaybackUri(String resolvedUrl) {
        if (resolvedUrl == null) {
            return null;
        }
        File cachedFile = cacheFile(resolvedUrl);
        if (cachedFile.isFile() && cachedFile.length() > 0) {
            cachedFile.setLastModified(System.currentTimeMillis());
            return Uri.fromFile(cachedFile);
        }
        return Uri.parse(resolvedUrl);
    }

    static List<String> selectUpcomingVideoUrls(List<String> urls, int currentPosition,
                                                 int limit) {
        if (urls == null || limit <= 0) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (int i = Math.max(0, currentPosition + 1);
             i < urls.size() && selected.size() < limit; i++) {
            String url = urls.get(i);
            if (url != null && !url.trim().isEmpty()) {
                selected.add(url);
            }
        }
        return new ArrayList<>(selected);
    }

    private void prefetch(String url) {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            Log.w(TAG, "Unable to create the video preload cache directory.");
            return;
        }
        File target = cacheFile(url);
        if (target.isFile() && target.length() > 0) {
            target.setLastModified(System.currentTimeMillis());
            return;
        }
        String key = target.getAbsolutePath();
        if (!ACTIVE_DOWNLOADS.add(key)) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                download(url, target);
                ACTIVE_DOWNLOADS.remove(key);
                trimCache();
            } catch (IOException error) {
                Log.w(TAG, "Unable to cache a video.", error);
                target.delete();
            } finally {
                ACTIVE_DOWNLOADS.remove(key);
            }
        });
    }

    private void download(String url, File target) throws IOException {
        HttpURLConnection connection = null;
        File temporary = new File(target.getPath() + ".part");
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "video/*");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Video preload failed with HTTP " + status);
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (temporary.length() == 0 || !temporary.renameTo(target)) {
                throw new IOException("Video preload could not be committed to cache.");
            }
        } finally {
            temporary.delete();
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private File cacheFile(String url) {
        return new File(cacheDirectory, sha256(url) + extensionFor(url));
    }

    private static String extensionFor(String url) {
        String path = Uri.parse(url).getLastPathSegment();
        if (path == null) {
            return ".video";
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return ".video";
        }
        String extension = path.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.(mp4|webm|mov|ogv|ogg)") ? extension : ".video";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte valueByte : digest) {
                result.append(String.format(Locale.ROOT, "%02x", valueByte & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private void trimCache() {
        File[] files = cacheDirectory.listFiles((directory, name) ->
                !name.endsWith(".part") && !ACTIVE_DOWNLOADS.contains(
                        new File(directory, name).getAbsolutePath()));
        if (files == null || files.length <= MAX_CACHE_FILES) {
            return;
        }
        List<File> oldestFirst = new ArrayList<>();
        Collections.addAll(oldestFirst, files);
        oldestFirst.sort(Comparator.comparingLong(File::lastModified));
        int removeCount = oldestFirst.size() - MAX_CACHE_FILES;
        for (int i = 0; i < removeCount; i++) {
            oldestFirst.get(i).delete();
        }
    }
}
