package com.sinoll.synapse_app.data.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Category {

    private final int id;
    private final String name;

    private Category(JSONObject json) throws JSONException {
        id = json.getInt("id");
        name = json.optString("name", "").trim();
    }

    public static Category fromJson(JSONObject json) throws JSONException {
        if (json == null) {
            throw new JSONException("Category data is required.");
        }
        return new Category(json);
    }

    public static List<Category> listFromJson(JSONObject data) throws JSONException {
        if (data == null) {
            throw new JSONException("Category list data is required.");
        }
        JSONArray items = data.optJSONArray("items");
        if (items == null) {
            throw new JSONException("Category response is missing items.");
        }
        List<Category> categories = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                throw new JSONException("Category item must be an object.");
            }
            categories.add(fromJson(item));
        }
        return Collections.unmodifiableList(categories);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name.isEmpty() ? String.valueOf(id) : name;
    }
}
