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
import android.util.Log;
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

import org.json.JSONObject;

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

    private static final String TAG = "CreateVideoFragment";

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
    private View uploadStep;
    private View detailsStep;
    private View selectActions;
    private TextView heading;
    private ImageButton playToggle;
    private TextView changeButton;
    private MaterialButton nextButton;
    private MaterialButton cancelButton;
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
    private int uploadedVideoType = 2;
    private long uploadedDurationSeconds;
    private String uploadedVideoPath;
    private String uploadedCoverPath;
    private boolean uploadingVideo;
    private boolean uploadComplete;
    private boolean previewReady;
    private boolean detailsVisible;
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
        uploadStep = view.findViewById(R.id.video_publish_upload_step);
        detailsStep = view.findViewById(R.id.video_publish_details_step);
        selectActions = view.findViewById(R.id.video_publish_select_actions);
        heading = view.findViewById(R.id.video_publish_heading);
        playToggle = view.findViewById(R.id.video_preview_toggle);
        changeButton = view.findViewById(R.id.video_preview_change);
        nextButton = view.findViewById(R.id.video_publish_next);
        cancelButton = view.findViewById(R.id.video_publish_cancel);
        submitButton = view.findViewById(R.id.video_publish_submit);
        descriptionInput = view.findViewById(R.id.video_publish_description);
        titleInput = view.findViewById(R.id.video_publish_title_input);
        visibilityInput = view.findViewById(R.id.video_publish_visibility);
        progress = view.findViewById(R.id.video_publish_progress);
        messageView = view.findViewById(R.id.video_publish_message);

        setupVisibilityDropdown();
        view.findViewById(R.id.video_publish_close)
                .setOnClickListener(ignored -> cancelPublishing());
        cancelButton.setOnClickListener(ignored -> cancelPublishing());
        nextButton.setOnClickListener(ignored -> showDetailsStep());
        submitButton.setOnClickListener(ignored -> publish());
        view.findViewById(R.id.video_publish_record)
                .setOnClickListener(ignored -> recordVideo());
        view.findViewById(R.id.video_publish_pick)
                .setOnClickListener(ignored -> videoPicker.launch("video/*"));
        changeButton.setOnClickListener(ignored -> videoPicker.launch("video/*"));
        playToggle.setOnClickListener(ignored -> togglePlayback());

        preview.setOnCompletionListener(mediaPlayer -> updatePlayToggle(false));
        preview.setOnErrorListener((mediaPlayer, what, extra) -> {
            updatePlayToggle(false);
            showMessage(getString(R.string.video_publish_read_failed));
            return true;
        });
        preview.setOnPreparedListener(mediaPlayer -> {
            mediaPlayer.setLooping(true);
            preview.seekTo(1);
            previewReady = true;
            updatePlayToggle(false);
            updateWorkflowUi();
        });

        viewModel.getPublishing().observe(getViewLifecycleOwner(), publishing -> {
            updateWorkflowUi();
        });
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (TextUtils.isEmpty(message)) {
                return;
            }
            if (uploadingVideo) {
                uploadingVideo = false;
                uploadComplete = false;
                previewReady = false;
            }
            showMessage(message);
            updateWorkflowUi();
        });
        viewModel.getUploadedVideo().observe(getViewLifecycleOwner(), upload -> {
            if (upload == null) {
                return;
            }
            uploadedVideoPath = upload.optString("path",
                    upload.optString("file_path", ""));
            uploadedCoverPath = upload.optString("thumbnail_path", "");
            uploadingVideo = false;
            uploadComplete = !uploadedVideoPath.trim().isEmpty();
            if (!uploadComplete) {
                showMessage(getString(R.string.video_publish_upload_failed));
                updateWorkflowUi();
                return;
            }
            showMessage("");
            preview.stopPlayback();
            emptyState.setVisibility(View.GONE);
            preview.setVisibility(View.VISIBLE);
            playToggle.setVisibility(View.VISIBLE);
            previewReady = false;
            preview.setVideoURI(selectedVideo);
            updateWorkflowUi();
        });
        viewModel.getCreatedId().observe(getViewLifecycleOwner(), id -> {
            if (resultHandled || id == null) {
                return;
            }
            resultHandled = true;
            progress.setVisibility(View.GONE);
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
        updateWorkflowUi();
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
        prepareAndUploadVideo(uri, captured.getName(), "video/mp4", captured.length());
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
        prepareAndUploadVideo(uri, name, mimeType, size);
    }

    private void prepareAndUploadVideo(Uri uri, String name, String mimeType, long size) {
        String authorization = getAuthorization();
        if (authorization == null) {
            showMessage(getString(R.string.video_publish_login_expired));
            return;
        }
        selectedVideo = uri;
        selectedVideoName = name;
        selectedVideoMime = mimeType;
        selectedVideoSize = size;
        preview.stopPlayback();
        preview.setVisibility(View.GONE);
        playToggle.setVisibility(View.GONE);
        changeButton.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        uploadingVideo = true;
        uploadComplete = false;
        previewReady = false;
        uploadedVideoPath = null;
        uploadedCoverPath = null;
        viewModel.clearUploadedVideo();
        showMessage(getString(R.string.video_publish_uploading));
        updateWorkflowUi();

        final ContentResolver resolver = requireContext().getApplicationContext()
                .getContentResolver();
        final Context appContext = requireContext().getApplicationContext();
        worker.execute(() -> {
            readVideoMetadata(appContext, uri);
            ContentRepository.UploadStream stream = new ContentRepository.UploadStream() {
                @Override
                public String getFileName() {
                    return name;
                }

                @Override
                public String getMimeType() {
                    return mimeType;
                }

                @Override
                public long getLength() {
                    return size;
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
            viewModel.uploadVideo(stream, authorization);
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

    private void readVideoMetadata(Context context, Uri uri) {
        uploadedDurationSeconds = 0;
        uploadedVideoType = 2;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            uploadedDurationSeconds = parseLong(retriever.extractMetadata(
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
                uploadedVideoType = height > width ? 2 : 1;
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not read optional video metadata; using vertical defaults",
                    error);
        } finally {
            try {
                retriever.release();
            } catch (Exception error) {
                Log.w(TAG, "Could not release video metadata retriever", error);
            }
        }
    }

    private void showDetailsStep() {
        if (!uploadComplete || !previewReady) {
            return;
        }
        detailsVisible = true;
        if (preview.isPlaying()) {
            preview.pause();
            updatePlayToggle(false);
        }
        showMessage("");
        updateWorkflowUi();
    }

    private void cancelPublishing() {
        Navigation.findNavController(requireView()).popBackStack();
    }

    private void updateWorkflowUi() {
        if (uploadStep == null || !isAdded()) {
            return;
        }
        boolean busy = uploadingVideo
                || Boolean.TRUE.equals(viewModel.getPublishing().getValue());
        uploadStep.setVisibility(detailsVisible ? View.GONE : View.VISIBLE);
        detailsStep.setVisibility(detailsVisible ? View.VISIBLE : View.GONE);
        heading.setText(detailsVisible
                ? R.string.video_publish_step_details : R.string.video_publish_step_upload);
        selectActions.setVisibility(!detailsVisible && !uploadComplete && !uploadingVideo
                ? View.VISIBLE : View.GONE);
        boolean canContinue = !detailsVisible && uploadComplete && previewReady && !busy;
        nextButton.setVisibility(canContinue ? View.VISIBLE : View.GONE);
        cancelButton.setVisibility(canContinue || detailsVisible ? View.VISIBLE : View.GONE);
        submitButton.setVisibility(detailsVisible ? View.VISIBLE : View.GONE);
        nextButton.setEnabled(canContinue);
        cancelButton.setEnabled(!busy);
        submitButton.setEnabled(detailsVisible && !busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void publish() {
        String authorization = getAuthorization();
        if (authorization == null) {
            showMessage(getString(R.string.video_publish_login_expired));
            return;
        }
        if (!uploadComplete || uploadedVideoPath == null) {
            showMessage(getString(R.string.video_publish_required));
            return;
        }
        showProgress(getString(R.string.video_publish_publishing));
        viewModel.publishUploadedVideo(uploadedVideoPath, uploadedCoverPath,
                titleInput.getText().toString(), descriptionInput.getText().toString(),
                uploadedDurationSeconds, uploadedVideoType, selectedVisibility, authorization);
    }

    private void showProgress(String message) {
        showMessage(message);
        updateWorkflowUi();
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
