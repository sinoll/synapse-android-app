package com.sinoll.synapse_app.ui.content;

import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.api.ApiClient;
import com.sinoll.synapse_app.data.model.ContentItem;

import java.util.ArrayList;
import java.util.List;

public final class VideoPagerAdapter extends RecyclerView.Adapter<VideoPagerAdapter.VideoViewHolder> {

    private final List<ContentItem> items = new ArrayList<>();
    private VideoViewHolder activeHolder;
    private int activePosition = RecyclerView.NO_POSITION;

    public void submitItems(List<ContentItem> values) {
        if (values.size() < items.size()) {
            items.clear();
            notifyDataSetChanged();
        }
        if (values.size() == items.size()) {
            return;
        }
        int first = items.size();
        items.addAll(values.subList(first, values.size()));
        notifyItemRangeInserted(first, items.size() - first);
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout root = new FrameLayout(parent.getContext());
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(parent.getContext().getColor(android.R.color.black));

        VideoView video = new VideoView(parent.getContext());
        root.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View scrim = new View(parent.getContext());
        scrim.setBackgroundResource(R.drawable.video_info_scrim);
        FrameLayout.LayoutParams scrimParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, parent.getResources().getDimensionPixelSize(
                R.dimen.video_info_height), Gravity.BOTTOM);
        root.addView(scrim, scrimParams);

        LinearLayoutInfo info = new LinearLayoutInfo(parent);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(info.layout, infoParams);

        ImageButton playPause = new ImageButton(parent.getContext());
        playPause.setImageResource(android.R.drawable.ic_media_play);
        playPause.setBackgroundResource(R.drawable.bg_video_action);
        playPause.setContentDescription(parent.getContext().getString(R.string.video_toggle));
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                dp(parent, 56), dp(parent, 56), Gravity.CENTER);
        root.addView(playPause, buttonParams);
        return new VideoViewHolder(root, video, info.title, info.description, info.metadata,
                playPause);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        boundHolders.values().remove(holder);
        boundHolders.put(position, holder);
        holder.bind(items.get(position), position);
    }

    @Override
    public void onViewRecycled(@NonNull VideoViewHolder holder) {
        holder.release();
        boundHolders.values().remove(holder);
        if (activeHolder == holder) {
            activeHolder = null;
            activePosition = RecyclerView.NO_POSITION;
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void activate(int position) {
        if (position == activePosition && activeHolder != null) {
            activeHolder.playIfReady();
            return;
        }
        if (activeHolder != null) {
            activeHolder.pause();
        }
        activePosition = position;
        if (position >= 0 && position < items.size()) {
            // The pager attaches the selected holder through bind callbacks.
            activeHolder = findBoundHolder(position);
            if (activeHolder != null) {
                activeHolder.playIfReady();
            }
        }
    }

    public void pauseActive() {
        if (activeHolder != null) {
            activeHolder.pause();
        }
    }

    private VideoViewHolder findBoundHolder(int position) {
        return boundHolders.get(position);
    }

    private final java.util.Map<Integer, VideoViewHolder> boundHolders =
            new java.util.HashMap<>();

    private static int dp(ViewGroup parent, int value) {
        return (int) (value * parent.getResources().getDisplayMetrics().density + 0.5f);
    }

    static final class VideoViewHolder extends RecyclerView.ViewHolder {

        private final VideoView video;
        private final TextView title;
        private final TextView description;
        private final TextView metadata;
        private final ImageButton playPause;
        private boolean prepared;

        VideoViewHolder(@NonNull View root, VideoView video, TextView title,
                        TextView description, TextView metadata, ImageButton playPause) {
            super(root);
            this.video = video;
            this.title = title;
            this.description = description;
            this.metadata = metadata;
            this.playPause = playPause;
            playPause.setOnClickListener(ignored -> toggle());
        }

        void bind(ContentItem item, int position) {
            prepared = false;
            video.stopPlayback();
            String url = ApiClient.forApplication()
                    .resolveMediaUrl(item.getVideoUrl());
            title.setText(item.getTitle());
            description.setText(item.getText().isEmpty()
                    ? item.getFields().optString("description", "") : item.getText());
            metadata.setText(item.getAuthorName() + "  ·  "
                    + item.getLikeCount() + " 赞");
            if (url == null || !url.startsWith("https://")) {
                description.setText(R.string.video_play_error);
                return;
            }
            video.setVideoURI(Uri.parse(url));
            video.setOnPreparedListener(mediaPlayer -> {
                prepared = true;
                mediaPlayer.setLooping(true);
                if (playPause.isSelected()) {
                    playIfReady();
                }
            });
            video.setOnErrorListener((mediaPlayer, what, extra) -> {
                description.setText(R.string.video_play_error);
                playPause.setVisibility(View.GONE);
                return true;
            });
            video.setOnCompletionListener(mediaPlayer -> playIfReady());
            playPause.setSelected(false);
            playPause.setVisibility(View.VISIBLE);
        }

        void playIfReady() {
            playPause.setSelected(true);
            playPause.setImageResource(android.R.drawable.ic_media_pause);
            if (prepared && !video.isPlaying()) {
                video.start();
            }
        }

        void pause() {
            playPause.setSelected(false);
            playPause.setImageResource(android.R.drawable.ic_media_play);
            if (video.isPlaying()) {
                video.pause();
            }
        }

        void release() {
            pause();
            video.stopPlayback();
            prepared = false;
        }

        private void toggle() {
            if (video.isPlaying()) {
                pause();
            } else {
                playIfReady();
            }
        }
    }

    private static final class LinearLayoutInfo {
        final android.widget.LinearLayout layout;
        final TextView title;
        final TextView description;
        final TextView metadata;

        LinearLayoutInfo(ViewGroup parent) {
            layout = new android.widget.LinearLayout(parent.getContext());
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(dp(parent, 22), dp(parent, 16), dp(parent, 22), dp(parent, 28));
            title = text(parent, 21, true);
            description = text(parent, 15, false);
            description.setMaxLines(3);
            metadata = text(parent, 13, false);
            layout.addView(title);
            layout.addView(description);
            layout.addView(metadata);
        }

        private static TextView text(ViewGroup parent, int size, boolean bold) {
            TextView view = new TextView(parent.getContext());
            view.setTextColor(parent.getContext().getColor(android.R.color.white));
            view.setTextSize(size);
            if (bold) {
                view.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            android.widget.LinearLayout.LayoutParams params =
                    new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(parent, 7);
            view.setLayoutParams(params);
            return view;
        }
    }
}
