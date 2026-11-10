package com.sinoll.synapse_app.ui.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentItem;

import java.util.ArrayList;
import java.util.List;

final class MyContentAdapter extends RecyclerView.Adapter<MyContentAdapter.ContentViewHolder> {

    private final OnContentClickListener clickListener;
    private final List<ContentItem> items = new ArrayList<>();

    MyContentAdapter(OnContentClickListener clickListener) {
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
                .inflate(R.layout.item_my_content, parent, false);
        return new ContentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContentViewHolder holder, int position) {
        ContentItem item = items.get(position);
        String title = item.getTitle().trim();
        holder.title.setText(title.isEmpty()
                ? holder.itemView.getContext().getString(R.string.content_untitled) : title);
        String createdAt = item.getCreatedAt();
        if (createdAt.length() >= 10) {
            createdAt = createdAt.substring(0, 10);
        }
        holder.subtitle.setText(holder.itemView.getContext().getString(
                typeLabel(item.getType()), createdAt));
        holder.itemView.setOnClickListener(ignored -> clickListener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int typeLabel(String type) {
        switch (type) {
            case "moment":
                return R.string.profile_content_moment;
            case "images":
                return R.string.profile_content_images;
            case "short_video":
                return R.string.profile_content_video;
            case "blog":
            default:
                return R.string.profile_content_blog;
        }
    }

    interface OnContentClickListener {
        void onClick(ContentItem item);
    }

    static final class ContentViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        ContentViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.my_content_title);
            subtitle = itemView.findViewById(R.id.my_content_subtitle);
        }
    }
}
