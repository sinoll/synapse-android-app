package com.sinoll.synapse_app.data.model;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class ContentQuery {

    private final int page;
    private final int perPage;
    private final String sort;
    private final String order;
    private final Integer userId;
    private final String search;
    private final String tag;

    private ContentQuery(Builder builder) {
        page = builder.page;
        perPage = builder.perPage;
        sort = builder.sort;
        order = builder.order;
        userId = builder.userId;
        search = builder.search;
        tag = builder.tag;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getPage() {
        return page;
    }

    public int getPerPage() {
        return perPage;
    }

    public String toQueryString() {
        StringBuilder query = new StringBuilder();
        add(query, "page", String.valueOf(page));
        add(query, "per_page", String.valueOf(perPage));
        add(query, "sort", sort);
        add(query, "order", order);
        if (userId != null) {
            add(query, "user_id", String.valueOf(userId));
        }
        if (search != null && !search.trim().isEmpty()) {
            add(query, "q", search.trim());
        }
        if (tag != null && !tag.trim().isEmpty()) {
            add(query, "tag", tag.trim());
        }
        return query.toString();
    }

    private static void add(StringBuilder query, String key, String value) {
        if (query.length() > 0) {
            query.append('&');
        }
        try {
            query.append(URLEncoder.encode(key, "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(value, "UTF-8"));
        } catch (UnsupportedEncodingException error) {
            throw new IllegalStateException("UTF-8 is required by Android.", error);
        }
    }

    public static final class Builder {

        private int page = 1;
        private int perPage = 20;
        private String sort = "created_at";
        private String order = "desc";
        private Integer userId;
        private String search;
        private String tag;

        public Builder page(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("page must be at least 1.");
            }
            page = value;
            return this;
        }

        public Builder perPage(int value) {
            if (value < 1 || value > 100) {
                throw new IllegalArgumentException("perPage must be between 1 and 100.");
            }
            perPage = value;
            return this;
        }

        public Builder sort(String value) {
            if (!"created_at".equals(value) && !"updated_at".equals(value)
                    && !"like_count".equals(value) && !"views".equals(value)) {
                throw new IllegalArgumentException("Unsupported content sort field.");
            }
            sort = value;
            return this;
        }

        public Builder order(String value) {
            if (!"asc".equals(value) && !"desc".equals(value)) {
                throw new IllegalArgumentException("order must be asc or desc.");
            }
            order = value;
            return this;
        }

        public Builder userId(Integer value) {
            if (value != null && value <= 0) {
                throw new IllegalArgumentException("userId must be positive.");
            }
            userId = value;
            return this;
        }

        public Builder search(String value) {
            search = value;
            return this;
        }

        public Builder tag(String value) {
            tag = value;
            return this;
        }

        public ContentQuery build() {
            return new ContentQuery(this);
        }
    }
}
