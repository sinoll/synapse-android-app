package com.sinoll.synapse_app.data.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContentPage {

    private final boolean authenticated;
    private final int page;
    private final int perPage;
    private final int total;
    private final int totalPages;
    private final String sort;
    private final String order;
    private final List<ContentItem> items;

    private ContentPage(JSONObject data, ContentQuery query) throws JSONException {
        authenticated = data.optBoolean("authenticated", false);
        page = data.optInt("page", query.getPage());
        perPage = data.optInt("per_page", query.getPerPage());
        total = data.optInt("total", 0);
        totalPages = data.optInt("total_pages", 0);
        sort = data.optString("sort", "created_at");
        order = data.optString("order", "desc");

        JSONArray jsonItems = data.optJSONArray("items");
        if (jsonItems == null) {
            throw new JSONException("Content response is missing items.");
        }
        List<ContentItem> parsedItems = new ArrayList<>(jsonItems.length());
        for (int i = 0; i < jsonItems.length(); i++) {
            JSONObject item = jsonItems.optJSONObject(i);
            if (item == null) {
                throw new JSONException("Content item must be an object.");
            }
            parsedItems.add(ContentItem.fromJson(item));
        }
        items = Collections.unmodifiableList(parsedItems);
    }

    private ContentPage(ContentPage source, List<ContentItem> values) {
        authenticated = source.authenticated;
        page = source.page;
        perPage = source.perPage;
        total = source.total;
        totalPages = source.totalPages;
        sort = source.sort;
        order = source.order;
        items = Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static ContentPage fromJson(JSONObject data, ContentQuery query) throws JSONException {
        if (data == null || query == null) {
            throw new JSONException("Content page data and query are required.");
        }
        return new ContentPage(data, query);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public int getPage() {
        return page;
    }

    public int getPerPage() {
        return perPage;
    }

    public int getTotal() {
        return total;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public String getSort() {
        return sort;
    }

    public String getOrder() {
        return order;
    }

    public List<ContentItem> getItems() {
        return items;
    }

    public boolean hasNextPage() {
        return page < totalPages;
    }

    public static ContentPage append(ContentPage previous, ContentPage next) {
        if (previous == null || next == null) {
            throw new IllegalArgumentException("Both content pages are required.");
        }
        List<ContentItem> combined = new ArrayList<>(previous.items);
        combined.addAll(next.items);
        return new ContentPage(next, combined);
    }
}
