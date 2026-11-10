package com.sinoll.synapse_app.data.model;

public enum ContentType {
    BLOG("blog"),
    MOMENT("moment"),
    IMAGE_GALLERY("images"),
    SHORT_VIDEO("short_video");

    private final String endpoint;

    ContentType(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
