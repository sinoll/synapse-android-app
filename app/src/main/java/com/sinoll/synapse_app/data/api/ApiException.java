package com.sinoll.synapse_app.data.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiException extends Exception {

    private final int httpStatus;
    private final int apiCode;
    private final Map<String, List<String>> fieldErrors;

    ApiException(String message, int httpStatus, int apiCode,
                 Map<String, List<String>> fieldErrors) {
        super(message);
        this.httpStatus = httpStatus;
        this.apiCode = apiCode;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getApiCode() {
        return apiCode;
    }

    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }

    static ApiException fromResponse(int httpStatus, JSONObject body) {
        int apiCode = body == null ? 0 : body.optInt("code", 0);
        String message = body == null ? null : body.optString("message", null);
        if (message == null || message.trim().isEmpty()) {
            message = defaultMessage(httpStatus);
        }

        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        JSONObject data = body == null ? null : body.optJSONObject("data");
        JSONObject errors = data == null ? null : data.optJSONObject("errors");
        if (errors != null) {
            JSONArray keys = errors.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.optString(i);
                    Object value = errors.opt(key);
                    List<String> messages = new ArrayList<>();
                    if (value instanceof JSONArray) {
                        JSONArray values = (JSONArray) value;
                        for (int j = 0; j < values.length(); j++) {
                            String error = values.optString(j, "");
                            if (!error.isEmpty()) {
                                messages.add(error);
                            }
                        }
                    } else if (value != null && value != JSONObject.NULL) {
                        messages.add(String.valueOf(value));
                    }
                    if (!messages.isEmpty()) {
                        fieldErrors.put(key, Collections.unmodifiableList(messages));
                    }
                }
            }
        }

        return new ApiException(message, httpStatus, apiCode,
                Collections.unmodifiableMap(fieldErrors));
    }

    static String defaultMessage(int httpStatus) {
        switch (httpStatus) {
            case 400:
                return "请求格式有误，请检查后重试。";
            case 401:
                return "登录状态已失效，请重新登录。";
            case 403:
                return "当前账户无权执行此操作。";
            case 404:
                return "请求的内容不存在或不可见。";
            case 422:
                return "提交的信息不符合要求。";
            case 429:
                return "操作过于频繁，请稍后重试。";
            default:
                return httpStatus >= 500
                        ? "服务暂时不可用，请稍后重试。"
                        : "网络请求失败，请稍后重试。";
        }
    }
}
