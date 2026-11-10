package com.sinoll.synapse_app.ui.content;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentItem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ContentListAdapter extends RecyclerView.Adapter<ContentListAdapter.ContentViewHolder> {

    private final OnContentClickListener clickListener;
    private final RemoteImageLoader imageLoader = new RemoteImageLoader();
    private final List<ContentItem> items = new ArrayList<>();

    ContentListAdapter(OnContentClickListener clickListener) {
        this.clickListener = clickListener;
    }

    void submitItems(List<ContentItem> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_content, parent, false);
        return new ContentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContentViewHolder holder, int position) {
        ContentItem item = items.get(position);
        if (item.getCoverUrl().isEmpty()) {
            holder.cover.setVisibility(View.GONE);
            imageLoader.clear(holder.cover);
        } else {
            holder.cover.setVisibility(View.VISIBLE);
            imageLoader.load(item.getCoverUrl(), holder.cover);
        }
        holder.title.setText(item.getTitle().isEmpty()
                ? holder.itemView.getContext().getString(R.string.content_untitled)
                : item.getTitle());
        JSONObject fields = item.getFields();
        String summary = firstNonEmpty(item.getSummary(), item.getText(),
                fields.optString("caption", ""), fields.optString("description", ""));
        holder.summary.setText(summary);
        holder.summary.setVisibility(summary.isEmpty() ? View.GONE : View.VISIBLE);

        StringBuilder metadata = new StringBuilder();
        if (!item.getAuthorName().isEmpty()) {
            metadata.append(item.getAuthorName());
        }
        if (!item.getCreatedAt().isEmpty()) {
            appendSeparator(metadata);
            metadata.append(item.getCreatedAt());
        }
        appendSeparator(metadata);
        metadata.append(holder.itemView.getContext().getString(
                R.string.content_engagement, item.getViews(), item.getLikeCount()));
        if (!item.getTags().isEmpty()) {
            metadata.append(" · #").append(item.getTags().get(0)).append('#');
            if (item.getTags().size() > 1) {
                metadata.append(" +").append(item.getTags().size() - 1);
            }
        }
        holder.metadata.setText(metadata);
        holder.itemView.setOnClickListener(view -> clickListener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static void appendSeparator(StringBuilder text) {
        if (text.length() > 0) {
            text.append(" · ");
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    interface OnContentClickListener {
        void onClick(ContentItem item);
    }

    static final class ContentViewHolder extends RecyclerView.ViewHolder {

        final TextView title;
        final TextView summary;
        final TextView metadata;
        final android.widget.ImageView cover;

        ContentViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.content_cover);
            title = itemView.findViewById(R.id.content_title);
            summary = itemView.findViewById(R.id.content_summary);
            metadata = itemView.findViewById(R.id.content_metadata);
        }
    }
}
