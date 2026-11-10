package com.sinoll.synapse_app.ui.content;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RemoteImageLoader {

    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ApiClient API_CLIENT = ApiClient.forApplication();

    void load(String url, ImageView imageView) {
        String resolved = API_CLIENT.resolveMediaUrl(url);
        imageView.setTag(R.id.remote_image_url, resolved);
        imageView.setImageResource(R.drawable.image_placeholder);
        if (resolved == null) {
            return;
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap;
            try {
                bitmap = download(resolved, imageView.getWidth(), imageView.getHeight());
            } catch (IOException error) {
                bitmap = null;
            }
            Bitmap result = bitmap;
            MAIN.post(() -> {
                if (resolved.equals(imageView.getTag(R.id.remote_image_url))) {
                    if (result != null) {
                        imageView.setImageBitmap(result);
                    } else {
                        imageView.setImageResource(R.drawable.image_placeholder);
                    }
                }
            });
        });
    }

    void clear(ImageView imageView) {
        imageView.setTag(R.id.remote_image_url, null);
        imageView.setImageResource(R.drawable.image_placeholder);
    }

    private static Bitmap download(String url, int width, int height) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "image/*");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Image request failed with HTTP " + status);
            }
            byte[] bytes = readLimited(connection.getInputStream());
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new IOException("Image response was not a supported image.");
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight,
                    Math.max(width, 360), Math.max(height, 200));
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null) {
                throw new IOException("Image could not be decoded.");
            }
            return bitmap;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int size = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                size += count;
                if (size > MAX_IMAGE_BYTES) {
                    throw new IOException("Image response exceeds the supported size.");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static int sampleSize(int width, int height, int requestedWidth, int requestedHeight) {
        int sample = 1;
        while (width / sample > requestedWidth * 2
                || height / sample > requestedHeight * 2) {
            sample *= 2;
        }
        return sample;
    }
}
