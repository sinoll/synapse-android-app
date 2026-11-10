package com.sinoll.synapse_app.data.api;

import org.json.JSONObject;

final class ApiEnvelope {

    private ApiEnvelope() {
    }

    static JSONObject requireData(int httpStatus, JSONObject body) throws ApiException {
        if (httpStatus < 200 || httpStatus >= 300) {
            throw ApiException.fromResponse(httpStatus, body);
        }
        if (body == null) {
            throw ApiException.fromResponse(httpStatus, null);
        }

        int apiCode = body.optInt("code", 0);
        if (apiCode != 200) {
            throw ApiException.fromResponse(httpStatus, body);
        }

        JSONObject data = body.optJSONObject("data");
        if (data == null) {
            throw ApiException.fromResponse(httpStatus, null);
        }
        return data;
    }
}
