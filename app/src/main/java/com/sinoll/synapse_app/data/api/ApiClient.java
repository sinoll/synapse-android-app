package com.sinoll.synapse_app.data.api;

import org.json.JSONException;
import org.json.JSONObject;

import com.sinoll.synapse_app.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public final class ApiClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 30000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final String baseUrl;
    private final String configurationError;
    private final boolean allowsDevelopmentHttp;

    public ApiClient(String baseUrl) {
        this(baseUrl, false);
    }

    private ApiClient(String baseUrl, boolean allowsDevelopmentHttp) {
        this.allowsDevelopmentHttp = allowsDevelopmentHttp;
        this.baseUrl = normalizeBaseUrl(baseUrl, allowsDevelopmentHttp);
        this.configurationError = null;
    }

    private ApiClient(IllegalArgumentException error) {
        this.allowsDevelopmentHttp = false;
        this.baseUrl = null;
        this.configurationError = error.getMessage() == null
                ? "API 地址配置无效。" : error.getMessage();
    }

    public static ApiClient forApplication() {
        // Development builds talk to a plain HTTP test server. The network security
        // config still restricts cleartext traffic to the configured dev host.
        boolean allowsDevelopmentHttp = "development".equals(BuildConfig.FLAVOR);
        return createForApplication(BuildConfig.API_BASE_URL, allowsDevelopmentHttp);
    }

    static ApiClient createForApplication(String baseUrl, boolean allowsDevelopmentHttp) {
        try {
            return new ApiClient(baseUrl, allowsDevelopmentHttp);
        } catch (IllegalArgumentException error) {
            // A bad BuildConfig must not crash the app on startup or when a screen
            // is opened. Keep the error and surface it through the request API.
            return new ApiClient(error);
        }
    }

    public JSONObject get(String path, String query, String authorization)
            throws IOException, ApiException {
        return request("GET", path, query, null, authorization);
    }

    public JSONObject post(String path, JSONObject body, String authorization)
            throws IOException, ApiException {
        if (body == null) {
            throw new IllegalArgumentException("POST body must not be null.");
        }
        return request("POST", path, null, body, authorization);
    }

    private JSONObject request(String method, String path, String query, JSONObject body,
                               String authorization) throws IOException, ApiException {
        requireConfiguration();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(buildUrl(path, query));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] requestBody = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(requestBody.length);
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(requestBody);
                }
            }
            if (authorization != null && !authorization.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", authorization);
            }

            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            JSONObject responseBody = parseBody(readResponse(responseStream, status), status);
            return ApiEnvelope.requireData(status, responseBody);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public String resolveMediaUrl(String value) {
        if (configurationError != null || value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            URI base = new URI(baseUrl);
            URI media = new URI(value.trim());
            URI resolved;
            if (media.isAbsolute()
                    && allowsDevelopmentHttp
                    && "http".equalsIgnoreCase(base.getScheme())
                    && "http".equalsIgnoreCase(media.getScheme())) {
                if (media.getUserInfo() != null || media.getFragment() != null) {
                    return null;
                }
                String mediaPath = media.getRawPath();
                if (mediaPath == null || mediaPath.isEmpty()) {
                    mediaPath = "/";
                }
                String mediaQuery = media.getRawQuery() == null
                        ? "" : "?" + media.getRawQuery();
                resolved = new URI(base.getScheme() + "://" + base.getRawAuthority()
                        + mediaPath + mediaQuery);
            } else {
                resolved = media.isAbsolute() ? media : base.resolve(media);
            }
            boolean isHttps = "https".equalsIgnoreCase(resolved.getScheme());
            boolean isAllowedDevelopmentHttp = allowsDevelopmentHttp
                    && "http".equalsIgnoreCase(resolved.getScheme())
                    && "http".equalsIgnoreCase(base.getScheme())
                    && base.getRawAuthority().equals(resolved.getRawAuthority());
            if ((!isHttps && !isAllowedDevelopmentHttp)
                    || resolved.getHost() == null || resolved.getUserInfo() != null
                    || resolved.getFragment() != null) {
                return null;
            }
            return resolved.toASCIIString();
        } catch (URISyntaxException error) {
            return null;
        }
    }

    private String buildUrl(String path, String query) {
        if (path == null || path.trim().isEmpty() || path.startsWith("//")
                || path.contains("..") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("API path must be a relative endpoint path.");
        }
        String endpoint = path.startsWith("/") ? path.substring(1) : path;
        String url = baseUrl + endpoint;
        if (query != null && !query.isEmpty()) {
            url += "?" + query;
        }
        try {
            URI uri = new URI(url);
            URI baseUri = new URI(baseUrl);
            if (!baseUri.getScheme().equalsIgnoreCase(uri.getScheme())
                    || !baseUri.getRawAuthority().equals(uri.getRawAuthority())
                    || !uri.getRawPath().startsWith(baseUri.getRawPath())
                    || uri.getRawFragment() != null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("API 请求地址与配置的服务器不一致。");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("API 请求地址无效。", error);
        }
    }

    private void requireConfiguration() throws ApiException {
        if (configurationError != null) {
            throw new ApiException(configurationError, 0, 0,
                    java.util.Collections.<String, java.util.List<String>>emptyMap());
        }
    }

    private static String normalizeBaseUrl(String value, boolean allowDevelopmentHttp) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("API 地址未配置。");
        }
        try {
            URI uri = new URI(value.trim());
            boolean isHttps = "https".equalsIgnoreCase(uri.getScheme());
            boolean isAllowedDevelopmentHttp = allowDevelopmentHttp
                    && "http".equalsIgnoreCase(uri.getScheme());
            if ((!isHttps && !isAllowedDevelopmentHttp) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException(
                        "API 地址必须使用 HTTPS（development 环境可使用 HTTP）。");
            }
            String normalized = uri.toASCIIString();
            return normalized.endsWith("/") ? normalized : normalized + "/";
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("API 地址格式无效。", error);
        }
    }

    private static byte[] readResponse(InputStream input, int httpStatus)
            throws IOException, ApiException {
        if (input == null) {
            return new byte[0];
        }
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new ApiException("服务器响应过大。", httpStatus, 0,
                            java.util.Collections.<String, java.util.List<String>>emptyMap());
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static JSONObject parseBody(byte[] bytes, int httpStatus) throws ApiException {
        try {
            return new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } catch (JSONException error) {
            if (httpStatus < 200 || httpStatus >= 300) {
                return null;
            }
            throw new ApiException("服务器返回了无法识别的响应。", httpStatus, 0,
                    java.util.Collections.<String, java.util.List<String>>emptyMap());
        }
    }
}
