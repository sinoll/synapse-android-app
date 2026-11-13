package com.sinoll.synapse_app.ui.content;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.data.repository.ContentRepository;
import com.sinoll.synapse_app.data.session.SessionStore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TikTok-style short video composer. Users can either record a new clip with the
 * system camera or pick an existing video from the gallery, then publish it.
 */
public final class CreateVideoFragment extends Fragment {

    private static final int VISIBILITY_AUTHOR = 0;
    private static final int VISIBILITY_USERS = 1;
    private static final int VISIBILITY_PUBLIC = 2;
    private static final int[] VISIBILITY_VALUES = {
            VISIBILITY_AUTHOR, VISIBILITY_USERS, VISIBILITY_PUBLIC
    };

    private final ActivityResultLauncher<String> videoPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::onVideoSelected);
    private final ActivityResultLauncher<Intent> videoCapture = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onVideoCaptured);

    private CreateContentViewModel viewModel;
    private ExecutorService worker;
    private VideoView preview;
    private View emptyState;
    private ImageButton playToggle;
    private TextView changeButton;
    private MaterialButton submitButton;
    private EditText descriptionInput;
    private EditText titleInput;
    private MaterialAutoCompleteTextView visibilityInput;
    private String[] visibilityLabels;
    private ProgressBar progress;
    private TextView messageView;
    private Uri selectedVideo;
    private String selectedVideoName;
    private String selectedVideoMime;
    private long selectedVideoSize = -1;
    private File capturedFile;
    private Uri pendingCaptureUri;
    private File pendingCaptureFile;
    private int selectedVisibility = VISIBILITY_PUBLIC;
    private boolean resultHandled;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_video, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CreateContentViewModel.class);
        worker = Executors.newSingleThreadExecutor();
        preview = view.findViewById(R.id.video_preview);
        emptyState = view.findViewById(R.id.video_preview_empty);
        playToggle = view.findViewById(R.id.video_preview_toggle);
        changeButton = view.findViewById(R.id.video_preview_change);
        submitButton = view.findViewById(R.id.video_publish_submit);
        descriptionInput = view.findViewById(R.id.video_publish_description);
        titleInput = view.findViewById(R.id.video_publish_title_input);
        visibilityInput = view.findViewById(R.id.video_publish_visibility);
        progress = view.findViewById(R.id.video_publish_progress);
        messageView = view.findViewById(R.id.video_publish_message);

        setupVisibilityDropdown();
        view.findViewById(R.id.video_publish_close)
                .setOnClickListener(ignored -> Navigation.findNavController(requireView())
                        .popBackStack());
        submitButton.setOnClickListener(ignored -> publish());
        view.findViewById(R.id.video_publish_record)
                .setOnClickListener(ignored -> recordVideo());
        view.findViewById(R.id.video_publish_pick)
                .setOnClickListener(ignored -> videoPicker.launch("video/*"));
        changeButton.setOnClickListener(ignored -> videoPicker.launch("video/*"));
        playToggle.setOnClickListener(ignored -> togglePlayback());

        submitButton.setEnabled(false);
        preview.setOnCompletionListener(mediaPlayer -> updatePlayToggle(false));
        preview.setOnErrorListener((mediaPlayer, what, extra) -> {
            updatePlayToggle(false);
            showMessage(getString(R.string.video_publish_read_failed));
            return true;
        });

        viewModel.getPublishing().observe(getViewLifecycleOwner(), publishing -> {
            boolean busy = Boolean.TRUE.equals(publishing);
            submitButton.setEnabled(!busy && selectedVideo != null);
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        });
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (TextUtils.isEmpty(message)) {
                return;
            }
            progress.setVisibility(View.GONE);
            submitButton.setEnabled(selectedVideo != null);
            showMessage(message);
        });
        viewModel.getCreatedId().observe(getViewLifecycleOwner(), id -> {
            if (resultHandled || id == null) {
                return;
            }
            resultHandled = true;
            progress.setVisibility(View.GONE);
            submitButton.setEnabled(true);
            viewModel.clearCreated();
            deleteQuietly(capturedFile);
            capturedFile = null;
            Bundle result = new Bundle();
            result.putString(CreateContentFragment.ARG_CONTENT_TYPE,
                    ContentType.SHORT_VIDEO.getEndpoint());
            getParentFragmentManager().setFragmentResult(
                    CreateContentFragment.RESULT_CONTENT_CREATED, result);
            Toast.makeText(requireContext(), R.string.video_publish_success,
                    Toast.LENGTH_SHORT).show();
            Navigation.findNavController(requireView()).popBackStack();
        });
    }

    private void setupVisibilityDropdown() {
        visibilityLabels = new String[]{
                getString(R.string.create_content_visibility_author),
                getString(R.string.create_content_visibility_users),
                getString(R.string.create_content_visibility_public)
        };
        visibilityInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, visibilityLabels));
        visibilityInput.setOnItemClickListener((parent, view, position, id) ->
                selectedVisibility = VISIBILITY_VALUES[position]);
        selectedVisibility = VISIBILITY_PUBLIC;
        visibilityInput.setText(visibilityLabels[2], false);
    }

    private void recordVideo() {
        try {
            File directory = new File(requireContext().getCacheDir(), "video");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Cannot create the video cache directory.");
            }
            pendingCaptureFile = File.createTempFile("capture_", ".mp4", directory);
            pendingCaptureUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", pendingCaptureFile);
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (intent.resolveActivity(requireContext().getPackageManager()) == null) {
                clearPendingCapture();
                showMessage(getString(R.string.video_publish_camera_unavailable));
                return;
            }
            videoCapture.launch(intent);
        } catch (android.content.ActivityNotFoundException error) {
            clearPendingCapture();
            showMessage(getString(R.string.video_publish_camera_unavailable));
        } catch (Exception error) {
            clearPendingCapture();
            showMessage(getString(R.string.video_publish_file_failed));
        }
    }

    private void onVideoCaptured(@NonNull ActivityResult result) {
        File captured = pendingCaptureFile;
        Uri uri = pendingCaptureUri;
        pendingCaptureFile = null;
        pendingCaptureUri = null;
        if (result.getResultCode() != Activity.RESULT_OK) {
            deleteQuietly(captured);
            return;
        }
        Uri returned = result.getData() == null ? null : result.getData().getData();
        if ((captured == null || captured.length() <= 0) && returned != null) {
            // Some camera apps ignore EXTRA_OUTPUT and return the clip directly.
            deleteQuietly(captured);
            onVideoSelected(returned);
            return;
        }
        if (captured == null || uri == null || captured.length() <= 0) {
            deleteQuietly(captured);
            showMessage(getString(R.string.video_publish_capture_failed));
            return;
        }
        deleteQuietly(capturedFile);
        capturedFile = captured;
        setSelectedVideo(uri, captured.getName(), "video/mp4", captured.length());
    }

    private void onVideoSelected(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        ContentResolver resolver = requireContext().getApplicationContext()
                .getContentResolver();
        String mimeType = resolver.getType(uri);
        if (mimeType == null || !mimeType.startsWith("video/")) {
            mimeType = "video/mp4";
        }
        String name = queryDisplayName(resolver, uri);
        if (name == null || name.trim().isEmpty()) {
            name = "video" + videoExtensionFor(mimeType);
        } else if (!name.contains(".")) {
            name = name + videoExtensionFor(mimeType);
        }
        long size = queryFileSize(resolver, uri);
        if (size == 0) {
            showMessage(getString(R.string.video_publish_read_failed));
            return;
        }
        deleteQuietly(capturedFile);
        capturedFile = null;
        setSelectedVideo(uri, name, mimeType, size);
    }

    private void setSelectedVideo(Uri uri, String name, String mimeType, long size) {
        selectedVideo = uri;
        selectedVideoName = name;
        selectedVideoMime = mimeType;
        selectedVideoSize = size;
        emptyState.setVisibility(View.GONE);
        preview.setVisibility(View.VISIBLE);
        playToggle.setVisibility(View.VISIBLE);
        changeButton.setVisibility(View.VISIBLE);
        submitButton.setEnabled(true);
        showMessage("");
        preview.stopPlayback();
        preview.setVideoURI(uri);
        preview.setOnPreparedListener(mediaPlayer -> {
            mediaPlayer.setLooping(true);
            preview.seekTo(1);
            updatePlayToggle(false);
        });
    }

    private void togglePlayback() {
        if (selectedVideo == null) {
            return;
        }
        if (preview.isPlaying()) {
            preview.pause();
            updatePlayToggle(false);
        } else {
            preview.start();
            updatePlayToggle(true);
        }
    }

    private void updatePlayToggle(boolean playing) {
        playToggle.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        playToggle.setContentDescription(getString(playing
                ? R.string.video_publish_pause : R.string.video_publish_play));
    }

    private void publish() {
        String authorization = getAuthorization();
        if (authorization == null) {
            showMessage(getString(R.string.video_publish_login_expired));
            return;
        }
        if (selectedVideo == null) {
            showMessage(getString(R.string.video_publish_required));
            return;
        }
        showProgress(getString(R.string.video_publish_uploading));
        final ContentResolver resolver = requireContext().getApplicationContext()
                .getContentResolver();
        final Context appContext = requireContext().getApplicationContext();
        final Uri uri = selectedVideo;
        final String fileName = selectedVideoName;
        final String mimeType = selectedVideoMime;
        final long length = selectedVideoSize;
        final String title = titleInput.getText().toString();
        final String description = descriptionInput.getText().toString();
        final int visibility = selectedVisibility;
        final String readFailed = getString(R.string.video_publish_read_failed);
        worker.execute(() -> {
            long durationSeconds = 0;
            int videoType = 2;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(appContext, uri);
                durationSeconds = parseLong(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000;
                int width = (int) parseLong(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height = (int) parseLong(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                int rotation = (int) parseLong(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                if (rotation == 90 || rotation == 270) {
                    int swap = width;
                    width = height;
                    height = swap;
                }
                if (width > 0 && height > 0) {
                    videoType = height > width ? 2 : 1;
                }
            } catch (Exception error) {
                // Metadata is optional; fall back to the vertical feed default.
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                    // Nothing else to release.
                }
            }
            ContentRepository.UploadStream stream = new ContentRepository.UploadStream() {
                @Override
                public String getFileName() {
                    return fileName;
                }

                @Override
                public String getMimeType() {
                    return mimeType;
                }

                @Override
                public long getLength() {
                    return length;
                }

                @Override
                public InputStream open() throws IOException {
                    InputStream input = resolver.openInputStream(uri);
                    if (input == null) {
                        throw new IOException("Video stream is unavailable.");
                    }
                    return input;
                }
            };
            try {
                viewModel.publishVideo(title, description, stream, durationSeconds,
                        videoType, visibility, authorization);
            } catch (Exception error) {
                viewModel.fail(readFailed);
            }
        });
    }

    private void showProgress(String message) {
        progress.setVisibility(View.VISIBLE);
        submitButton.setEnabled(false);
        showMessage(message);
    }

    private void showMessage(String message) {
        messageView.setText(message);
        messageView.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
    }

    @Nullable
    private String getAuthorization() {
        try {
            return new SessionStore(requireContext()).getAuthorizationHeader();
        } catch (Exception error) {
            return null;
        }
    }

    private void clearPendingCapture() {
        deleteQuietly(pendingCaptureFile);
        pendingCaptureFile = null;
        pendingCaptureUri = null;
    }

    private static void deleteQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    @Nullable
    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception error) {
            // Fall back to a generated file name.
        }
        return null;
    }

    private static long queryFileSize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        } catch (Exception error) {
            // Unknown size falls back to a chunked upload.
        }
        return -1;
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static String videoExtensionFor(String mimeType) {
        if ("video/webm".equals(mimeType)) {
            return ".webm";
        }
        if ("video/3gpp".equals(mimeType)) {
            return ".3gp";
        }
        if ("video/quicktime".equals(mimeType)) {
            return ".mov";
        }
        if ("video/x-matroska".equals(mimeType)) {
            return ".mkv";
        }
        return ".mp4";
    }

    @Override
    public void onDestroyView() {
        if (preview != null) {
            preview.stopPlayback();
        }
        if (worker != null) {
            worker.shutdownNow();
        }
        if (!resultHandled) {
            deleteQuietly(capturedFile);
        }
        capturedFile = null;
        clearPendingCapture();
        super.onDestroyView();
    }
}
