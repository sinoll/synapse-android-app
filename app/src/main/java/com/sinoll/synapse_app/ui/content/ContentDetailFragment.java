package com.sinoll.synapse_app.ui.content;

import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.model.ContentType;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ContentDetailFragment extends Fragment {

    public static final String ARG_CONTENT_TYPE = "content_type";
    public static final String ARG_CONTENT_ID = "content_id";

    private ContentDetailViewModel viewModel;
    private TextView titleView;
    private TextView metadataView;
    private TextView bodyView;
    private TextView stateView;
    private LinearLayout galleryView;
    private final RemoteImageLoader imageLoader = new RemoteImageLoader();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_content_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        titleView = view.findViewById(R.id.content_detail_title);
        metadataView = view.findViewById(R.id.content_detail_metadata);
        bodyView = view.findViewById(R.id.content_detail_body);
        stateView = view.findViewById(R.id.content_detail_state);
        galleryView = view.findViewById(R.id.content_detail_gallery);

        viewModel = new ViewModelProvider(this).get(ContentDetailViewModel.class);
        viewModel.getItem().observe(getViewLifecycleOwner(), this::showItem);
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (Boolean.TRUE.equals(loading) && viewModel.getItem().getValue() == null) {
                showState(getString(R.string.content_loading));
            }
        });
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (!TextUtils.isEmpty(message)) {
                showState(message);
                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
            }
        });

        Bundle arguments = getArguments();
        String typeName = arguments == null ? null : arguments.getString(ARG_CONTENT_TYPE);
        int id = arguments == null ? 0 : arguments.getInt(ARG_CONTENT_ID);
        ContentType type = contentTypeFromName(typeName);
        if (type == null || id <= 0) {
            showState(getString(R.string.content_invalid_reference));
            return;
        }
        viewModel.load(type, id);
    }

    private void showItem(ContentItem item) {
        stateView.setVisibility(View.GONE);
        titleView.setText(item.getTitle().isEmpty()
                ? getString(R.string.content_untitled) : item.getTitle());
        StringBuilder metadata = new StringBuilder(item.getAuthorName());
        if (!item.getCreatedAt().isEmpty()) {
            if (metadata.length() > 0) {
                metadata.append(" · ");
            }
            metadata.append(item.getCreatedAt());
        }
        metadataView.setText(metadata);

        JSONObject fields = item.getFields();
        String body = firstNonEmpty(fields.optString("content", ""),
                fields.optString("description", ""),
                fields.optString("caption", ""),
                fields.optString("summary", ""),
                fields.optString("text", ""));
        if (body.isEmpty()) {
            body = galleryDescription(fields.optJSONArray("images"));
        }
        bodyView.setText(Html.fromHtml(body, Html.FROM_HTML_MODE_COMPACT));
        bodyView.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
        showGallery(fields.optJSONArray("images"), item.getCoverUrl());
    }

    private void showGallery(@Nullable JSONArray images, String fallbackCover) {
        galleryView.removeAllViews();
        if (images == null || images.length() == 0) {
            addGalleryImage(fallbackCover, getString(R.string.content_cover_description));
            return;
        }
        int count = Math.min(images.length(), 20);
        for (int i = 0; i < count; i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null) {
                continue;
            }
            String url = firstNonEmpty(image.optString("file_url", ""),
                    image.optString("image_url", ""),
                    image.optString("url", ""),
                    image.optString("thumbnail_url", ""));
            String description = firstNonEmpty(image.optString("title", ""),
                    image.optString("caption", ""), getString(R.string.content_cover_description));
            addGalleryImage(url, description);
        }
    }

    private void addGalleryImage(String url, String description) {
        if (url == null || url.isEmpty()) {
            return;
        }
        ImageView image = new ImageView(requireContext());
        int height = (int) (240 * getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density + 0.5f);
        image.setLayoutParams(params);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(description);
        galleryView.addView(image);
        imageLoader.load(url, image);
    }

    private void showState(String message) {
        stateView.setText(message);
        stateView.setVisibility(View.VISIBLE);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String galleryDescription(@Nullable JSONArray images) {
        if (images == null || images.length() == 0) {
            return "";
        }
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null) {
                continue;
            }
            String title = image.optString("title", "");
            if (!title.isEmpty()) {
                if (description.length() > 0) {
                    description.append('\n');
                }
                description.append(title);
            }
        }
        return description.toString();
    }

    @Nullable
    private static ContentType contentTypeFromName(String name) {
        for (ContentType type : ContentType.values()) {
            if (type.getEndpoint().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
