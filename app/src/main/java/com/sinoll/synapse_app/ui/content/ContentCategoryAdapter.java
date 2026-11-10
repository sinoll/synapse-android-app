package com.sinoll.synapse_app.ui.content;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentType;

import java.util.List;

final class ContentCategoryAdapter
        extends RecyclerView.Adapter<ContentCategoryAdapter.CategoryViewHolder> {

    private final List<Category> categories;
    private final OnCategoryClickListener clickListener;
    private int selectedPosition;

    ContentCategoryAdapter(List<Category> categories, OnCategoryClickListener clickListener) {
        this.categories = categories;
        this.clickListener = clickListener;
    }

    void setSelectedPosition(int position) {
        if (position < 0 || position >= categories.size() || selectedPosition == position) {
            return;
        }
        int previousPosition = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(previousPosition);
        notifyItemChanged(selectedPosition);
    }

    Category getCategory(int position) {
        return categories.get(position);
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_content_category, parent, false);
        RecyclerView.LayoutParams params =
                (RecyclerView.LayoutParams) view.getLayoutParams();
        params.width = dp(parent, 184);
        view.setLayoutParams(params);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.icon.setText(category.icon);
        holder.label.setText(category.label);
        boolean selected = position == selectedPosition;
        holder.card.setStrokeWidth(dp(holder.itemView, selected ? 2 : 1));
        holder.card.setStrokeColor(holder.itemView.getContext().getColor(selected
                ? R.color.brand_primary : R.color.card_stroke));
        holder.card.setCardBackgroundColor(holder.itemView.getContext().getColor(selected
                ? R.color.card_selected : R.color.white));
        holder.itemView.setOnClickListener(ignored -> clickListener.onCategoryClicked(position));
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }

    static final class Category {
        final ContentType type;
        final String label;
        final String icon;

        Category(ContentType type, String label, String icon) {
            this.type = type;
            this.label = label;
            this.icon = icon;
        }
    }

    interface OnCategoryClickListener {
        void onCategoryClicked(int position);
    }

    static final class CategoryViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView icon;
        final TextView label;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            icon = itemView.findViewById(R.id.content_category_icon);
            label = itemView.findViewById(R.id.content_category_label);
        }
    }
}
