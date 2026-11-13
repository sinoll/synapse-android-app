package com.sinoll.synapse_app.ui.content;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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

    private static final long PLAYBACK_CONTROLS_TIMEOUT_MILLIS = 1800;
    private static final long VIDEO_INFO_TIMEOUT_MILLIS = 2200;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<ContentItem> items = new ArrayList<>();
    private VideoViewHolder activeHolder;
    private int activePosition = RecyclerView.NO_POSITION;
    private OnFullscreenListener fullscreenListener;
    private boolean fullscreenMode;

    public void setFullscreenListener(OnFullscreenListener listener) {
        fullscreenListener = listener;
    }

    public void setFullscreenMode(boolean fullscreen) {
        fullscreenMode = fullscreen;
        if (activeHolder != null) {
            activeHolder.setFullscreenMode(fullscreen);
        }
    }

    public void submitItems(List<ContentItem> values) {
        if (values == null) {
            return;
        }
        if (isPrefixOf(values)) {
            if (values.size() == items.size()) {
                return;
            }
            int first = items.size();
            items.addAll(values.subList(first, values.size()));
            notifyItemRangeInserted(first, items.size() - first);
            return;
        }
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    private boolean isPrefixOf(List<ContentItem> values) {
        if (values.size() < items.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            ContentItem existing = items.get(i);
            ContentItem incoming = values.get(i);
            if (existing.getId() != incoming.getId()
                    || !existing.getType().equals(incoming.getType())) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout root = new FrameLayout(parent.getContext());
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(parent.getContext().getColor(android.R.color.black));

        VideoView video = new VideoView(parent.getContext());
        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        root.addView(video, videoParams);

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
        ImageButton fullscreen = new ImageButton(parent.getContext());
        fullscreen.setImageResource(android.R.drawable.ic_menu_crop);
        fullscreen.setBackgroundResource(R.drawable.bg_video_action);
        fullscreen.setContentDescription(parent.getContext().getString(
                R.string.video_fullscreen_enter));
        FrameLayout.LayoutParams fullscreenParams = new FrameLayout.LayoutParams(
                dp(parent, 48), dp(parent, 48), Gravity.TOP | Gravity.END);
        fullscreenParams.setMargins(0, dp(parent, 20), dp(parent, 16), 0);
        root.addView(fullscreen, fullscreenParams);
        return new VideoViewHolder(root, video, info.layout, scrim, info.title,
                info.description, info.metadata, playPause, fullscreen, mainHandler,
                isFullscreen -> {
                    fullscreenMode = isFullscreen;
                    if (fullscreenListener != null) {
                        fullscreenListener.onFullscreenChanged(isFullscreen);
                    }
                });
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        boundHolders.values().remove(holder);
        boundHolders.put(position, holder);
        holder.bind(items.get(position), position);
        holder.setFullscreenMode(fullscreenMode);
        if (position == activePosition) {
            activeHolder = holder;
            holder.playIfReady();
        }
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

    public boolean isVertical(int position) {
        return position < 0 || position >= items.size()
                || items.get(position).isVerticalVideo();
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
        private final android.widget.LinearLayout infoPanel;
        private final View infoScrim;
        private final TextView title;
        private final TextView description;
        private final TextView metadata;
        private final ImageButton playPause;
        private final ImageButton fullscreen;
        private final Handler mainHandler;
        private final OnFullscreenListener fullscreenListener;
        private boolean prepared;
        private boolean verticalVideo = true;
        private boolean fullscreenMode;
        private int videoWidth;
        private int videoHeight;
        private final Runnable hidePlayButton;
        private final Runnable hideVideoInfo;
        private final View.OnLayoutChangeListener videoLayoutListener =
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (videoWidth > 0) {
                        layoutVideo();
                    }
                };

        VideoViewHolder(@NonNull View root, VideoView video,
                        android.widget.LinearLayout infoPanel, View infoScrim,
                        TextView title, TextView description, TextView metadata,
                        ImageButton playPause, ImageButton fullscreen, Handler mainHandler,
                        OnFullscreenListener fullscreenListener) {
            super(root);
            this.video = video;
            this.infoPanel = infoPanel;
            this.infoScrim = infoScrim;
            this.title = title;
            this.description = description;
            this.metadata = metadata;
            this.playPause = playPause;
            this.fullscreen = fullscreen;
            this.mainHandler = mainHandler;
            this.fullscreenListener = fullscreenListener;
            hidePlayButton = () -> {
                if (video.isPlaying()) {
                    playPause.animate().alpha(0f).setDuration(180)
                            .withEndAction(() -> playPause.setVisibility(View.GONE)).start();
                }
            };
            hideVideoInfo = () -> {
                if (video.isPlaying() && (verticalVideo || fullscreenMode)) {
                    setVideoInfoVisible(false);
                }
            };
            playPause.setOnClickListener(ignored -> toggle());
            fullscreen.setOnClickListener(ignored -> {
                fullscreenMode = !fullscreenMode;
                updateFullscreenButton();
                updateVideoInfoLayout();
                if (!verticalVideo && prepared) {
                    layoutVideo();
                }
                if (video.isPlaying()) {
                    scheduleHideVideoInfo();
                }
                this.fullscreenListener.onFullscreenChanged(fullscreenMode);
            });
            itemView.addOnLayoutChangeListener(videoLayoutListener);
            itemView.setOnClickListener(ignored -> toggle());
        }

        void bind(ContentItem item, int position) {
            mainHandler.removeCallbacks(hidePlayButton);
            mainHandler.removeCallbacks(hideVideoInfo);
            prepared = false;
            videoWidth = 0;
            videoHeight = 0;
            video.stopPlayback();
            playPause.setSelected(false);
            playPause.animate().cancel();
            playPause.setAlpha(1f);
            playPause.setImageResource(android.R.drawable.ic_media_play);
            playPause.setVisibility(View.VISIBLE);
            verticalVideo = item.isVerticalVideo();
            fullscreenMode = false;
            updateVideoInfoLayout();
            setVideoInfoVisible(true);
            fullscreen.setVisibility(verticalVideo ? View.GONE : View.VISIBLE);
            updateFullscreenButton();
            video.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
            String url = ApiClient.forApplication()
                    .resolveMediaUrl(item.getVideoUrl());
            title.setText(item.getTitle());
            String descriptionText = item.getText();
            if (descriptionText.isEmpty()) {
                descriptionText = item.getFields().optString("description", "");
            }
            if ("null".equalsIgnoreCase(descriptionText.trim())) {
                descriptionText = "";
            }
            description.setText(descriptionText);
            metadata.setText(item.getAuthorName() + "  ·  "
                    + item.getLikeCount() + " 赞");
            if (url == null) {
                description.setText(R.string.video_play_error);
                return;
            }
            video.setOnPreparedListener(mediaPlayer -> {
                prepared = true;
                videoWidth = mediaPlayer.getVideoWidth();
                videoHeight = mediaPlayer.getVideoHeight();
                mediaPlayer.setLooping(true);
                if (verticalVideo || !fullscreenMode) {
                    layoutVideo();
                }
                if (playPause.isSelected()) {
                    playIfReady();
                }
            });
            video.setOnErrorListener((mediaPlayer, what, extra) -> {
                mainHandler.removeCallbacks(hidePlayButton);
                description.setText(R.string.video_play_error);
                playPause.setAlpha(1f);
                playPause.setImageResource(android.R.drawable.ic_media_play);
                playPause.setVisibility(View.GONE);
                return true;
            });
            video.setOnCompletionListener(mediaPlayer -> playIfReady());
            video.setVideoURI(Uri.parse(url));
        }

        void playIfReady() {
            playPause.setSelected(true);
            playPause.setImageResource(android.R.drawable.ic_media_pause);
            if (prepared && !video.isPlaying()) {
                video.start();
                scheduleHidePlayButton();
                scheduleHideVideoInfo();
            } else if (video.isPlaying()) {
                scheduleHidePlayButton();
                scheduleHideVideoInfo();
            }
        }

        void pause() {
            mainHandler.removeCallbacks(hidePlayButton);
            mainHandler.removeCallbacks(hideVideoInfo);
            playPause.animate().cancel();
            playPause.setAlpha(1f);
            playPause.setVisibility(View.VISIBLE);
            setVideoInfoVisible(true);
            playPause.setSelected(false);
            playPause.setImageResource(android.R.drawable.ic_media_play);
            if (video.isPlaying()) {
                video.pause();
            }
        }

        void release() {
            mainHandler.removeCallbacks(hidePlayButton);
            mainHandler.removeCallbacks(hideVideoInfo);
            pause();
            video.stopPlayback();
            prepared = false;
        }

        private void toggle() {
            if (video.isPlaying()) {
                pause();
            } else {
                playPause.setVisibility(View.VISIBLE);
                playPause.setAlpha(1f);
                playIfReady();
            }
        }

        private void scheduleHidePlayButton() {
            mainHandler.removeCallbacks(hidePlayButton);
            playPause.setVisibility(View.VISIBLE);
            playPause.setAlpha(1f);
            mainHandler.postDelayed(hidePlayButton, PLAYBACK_CONTROLS_TIMEOUT_MILLIS);
        }

        private void scheduleHideVideoInfo() {
            mainHandler.removeCallbacks(hideVideoInfo);
            if ((verticalVideo || fullscreenMode) && video.isPlaying()) {
                long timeout = verticalVideo
                        ? PLAYBACK_CONTROLS_TIMEOUT_MILLIS : VIDEO_INFO_TIMEOUT_MILLIS;
                mainHandler.postDelayed(hideVideoInfo, timeout);
            }
        }

        private void updateVideoInfoLayout() {
            boolean compact = fullscreenMode && !verticalVideo;
            infoPanel.setPadding(dp((ViewGroup) itemView, compact ? 16 : 22),
                    dp((ViewGroup) itemView, compact ? 6 : 16),
                    dp((ViewGroup) itemView, compact ? 16 : 22),
                    dp((ViewGroup) itemView, compact ? 6 : 28));
            title.setTextSize(compact ? 16 : 21);
            title.setMaxLines(compact ? 1 : Integer.MAX_VALUE);
            description.setVisibility(compact ? View.GONE : View.VISIBLE);
            description.setMaxLines(compact ? 1 : 3);
            metadata.setTextSize(compact ? 12 : 13);
            android.widget.LinearLayout.LayoutParams metadataParams =
                    (android.widget.LinearLayout.LayoutParams) metadata.getLayoutParams();
            metadataParams.bottomMargin = compact ? 0 : dp((ViewGroup) itemView, 7);
            metadata.setLayoutParams(metadataParams);
            FrameLayout.LayoutParams scrimParams =
                    (FrameLayout.LayoutParams) infoScrim.getLayoutParams();
            scrimParams.height = compact
                    ? dp((ViewGroup) itemView, 72)
                    : itemView.getResources().getDimensionPixelSize(R.dimen.video_info_height);
            infoScrim.setLayoutParams(scrimParams);
        }

        private void setVideoInfoVisible(boolean visible) {
            mainHandler.removeCallbacks(hideVideoInfo);
            infoPanel.animate().cancel();
            infoScrim.animate().cancel();
            if (visible) {
                infoPanel.setVisibility(View.VISIBLE);
                infoScrim.setVisibility(View.VISIBLE);
                infoPanel.setAlpha(1f);
                infoScrim.setAlpha(1f);
            } else {
                infoPanel.animate().alpha(0f).setDuration(220)
                        .withEndAction(() -> infoPanel.setVisibility(View.GONE)).start();
                infoScrim.animate().alpha(0f).setDuration(220)
                        .withEndAction(() -> infoScrim.setVisibility(View.GONE)).start();
            }
        }

        private void updateFullscreenButton() {
            fullscreen.setContentDescription(itemView.getContext().getString(fullscreenMode
                    ? R.string.video_fullscreen_exit : R.string.video_fullscreen_enter));
            fullscreen.setImageResource(fullscreenMode
                    ? android.R.drawable.ic_menu_revert : android.R.drawable.ic_menu_crop);
        }

        void setFullscreenMode(boolean fullscreen) {
            fullscreenMode = fullscreen;
            updateFullscreenButton();
            updateVideoInfoLayout();
            setVideoInfoVisible(true);
            if (prepared) {
                layoutVideo();
            }
            scheduleHideVideoInfo();
        }

        private void layoutVideo() {
            ViewGroup parent = (ViewGroup) video.getParent();
            int availableWidth = parent.getWidth();
            int availableHeight = parent.getHeight();
            if (availableWidth <= 0 || availableHeight <= 0
                    || videoWidth <= 0 || videoHeight <= 0) {
                return;
            }
            float scale = verticalVideo
                    ? Math.max((float) availableWidth / videoWidth,
                            (float) availableHeight / videoHeight)
                    : Math.min((float) availableWidth / videoWidth,
                            (float) availableHeight / videoHeight);
            int width = Math.round(videoWidth * scale);
            int height = Math.round(videoHeight * scale);
            ViewGroup.LayoutParams current = video.getLayoutParams();
            if (current.width == width && current.height == height) {
                return;
            }
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    width, height, Gravity.CENTER);
            video.setLayoutParams(params);
        }
    }

    public interface OnFullscreenListener {
        void onFullscreenChanged(boolean fullscreen);
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
