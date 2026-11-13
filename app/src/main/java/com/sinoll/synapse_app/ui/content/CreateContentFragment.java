package com.sinoll.synapse_app.ui.content;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.Category;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.data.repository.ContentRepository;
import com.sinoll.synapse_app.data.session.SessionStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CreateContentFragment extends Fragment {

    public static final String ARG_CONTENT_TYPE = "content_type";
    public static final String RESULT_CONTENT_CREATED = "content_created";

    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    private static final int MAX_IMAGES = 9;
    private static final int VISIBILITY_AUTHOR = 0;
    private static final int VISIBILITY_USERS = 1;
    private static final int VISIBILITY_PUBLIC = 2;
    private static final int[] VISIBILITY_VALUES = {
            VISIBILITY_AUTHOR, VISIBILITY_USERS, VISIBILITY_PUBLIC
    };

    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), this::onImagesSelected);
    private final ActivityResultLauncher<String> videoPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::onVideoSelected);
    private final List<Uri> selectedImages = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();

    private ExecutorService imageReader;
    private CreateContentViewModel viewModel;
    private ContentType contentType = ContentType.MOMENT;
    private TextView headingView;
    private TextView subheadingView;
    private EditText titleInput;
    private EditText summaryInput;
    private EditText bodyInput;
    private EditText captionInput;
    private EditText tagsInput;
    private TextInputLayout categoryLayout;
    private MaterialAutoCompleteTextView categoryInput;
    private MaterialAutoCompleteTextView visibilityInput;
    private String[] visibilityLabels;
    private int selectedCategoryId;
    private int selectedVisibility = VISIBILITY_USERS;
    private View imagesSection;
    private TextView imagesStatus;
    private View videoSection;
    private TextView videoStatus;
    private Uri selectedVideo;
    private String selectedVideoName;
    private String selectedVideoMime;
    private long selectedVideoSize = -1;
    private Button publishButton;
    private ProgressBar progress;
    private TextView messageView;
    private boolean resultHandled;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CreateContentViewModel.class);
        imageReader = Executors.newSingleThreadExecutor();
        headingView = view.findViewById(R.id.create_heading);
        subheadingView = view.findViewById(R.id.create_subheading);
        titleInput = view.findViewById(R.id.create_title);
        summaryInput = view.findViewById(R.id.create_summary);
        bodyInput = view.findViewById(R.id.create_body);
        captionInput = view.findViewById(R.id.create_caption);
        tagsInput = view.findViewById(R.id.create_tags);
        categoryLayout = view.findViewById(R.id.create_category_layout);
        categoryInput = view.findViewById(R.id.create_category);
        visibilityInput = view.findViewById(R.id.create_visibility);
        imagesSection = view.findViewById(R.id.create_images_section);
        imagesStatus = view.findViewById(R.id.create_images_status);
        videoSection = view.findViewById(R.id.create_video_section);
        videoStatus = view.findViewById(R.id.create_video_status);
        publishButton = view.findViewById(R.id.create_publish);
        progress = view.findViewById(R.id.create_progress);
        messageView = view.findViewById(R.id.create_message);

        Bundle arguments = getArguments();
        String typeName = arguments == null ? null : arguments.getString(ARG_CONTENT_TYPE);
        ContentType parsed = contentTypeFromName(typeName);
        contentType = parsed == null ? ContentType.MOMENT : parsed;
        bindType();
        setupVisibilityDropdown();
        if (contentType == ContentType.BLOG) {
            setupCategoryDropdown();
            viewModel.loadCategories();
        }
        publishButton.setOnClickListener(ignored -> publish());
        view.findViewById(R.id.create_select_images)
                .setOnClickListener(ignored -> imagePicker.launch("image/*"));
        view.findViewById(R.id.create_select_video)
                .setOnClickListener(ignored -> videoPicker.launch("video/*"));

        viewModel.getPublishing().observe(getViewLifecycleOwner(), publishing -> {
            boolean busy = Boolean.TRUE.equals(publishing);
            publishButton.setEnabled(!busy);
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        });
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (TextUtils.isEmpty(message)) {
                return;
            }
            progress.setVisibility(View.GONE);
            publishButton.setEnabled(true);
            showMessage(message);
        });
        viewModel.getCreatedId().observe(getViewLifecycleOwner(), id -> {
            if (resultHandled || id == null) {
                return;
            }
            resultHandled = true;
            progress.setVisibility(View.GONE);
            publishButton.setEnabled(true);
            viewModel.clearCreated();
            Bundle result = new Bundle();
            result.putString(ARG_CONTENT_TYPE, contentType.getEndpoint());
            getParentFragmentManager().setFragmentResult(RESULT_CONTENT_CREATED, result);
            Toast.makeText(requireContext(), R.string.create_content_success,
                    Toast.LENGTH_SHORT).show();
            Navigation.findNavController(requireView()).popBackStack();
        });
    }

    private void bindType() {
        int heading;
        int subheading;
        boolean blog = contentType == ContentType.BLOG;
        boolean gallery = contentType == ContentType.IMAGE_GALLERY;
        boolean video = contentType == ContentType.SHORT_VIDEO;
        if (blog) {
            heading = R.string.create_content_blog_title;
            subheading = R.string.create_content_blog_subtitle;
        } else if (gallery) {
            heading = R.string.create_content_gallery_title;
            subheading = R.string.create_content_gallery_subtitle;
        } else if (video) {
            heading = R.string.create_content_video_title;
            subheading = R.string.create_content_video_subtitle;
        } else {
            heading = R.string.create_content_moment_title;
            subheading = R.string.create_content_moment_subtitle;
        }
        headingView.setText(heading);
        subheadingView.setText(subheading);
        titleInput.setVisibility(blog || video ? View.VISIBLE : View.GONE);
        summaryInput.setVisibility(blog ? View.VISIBLE : View.GONE);
        bodyInput.setVisibility(gallery ? View.GONE : View.VISIBLE);
        bodyInput.setHint(video
                ? R.string.create_content_video_description_hint
                : R.string.create_content_body_hint);
        captionInput.setVisibility(gallery ? View.VISIBLE : View.GONE);
        tagsInput.setVisibility(blog ? View.VISIBLE : View.GONE);
        categoryLayout.setVisibility(blog ? View.VISIBLE : View.GONE);
        imagesSection.setVisibility(gallery ? View.VISIBLE : View.GONE);
        videoSection.setVisibility(video ? View.VISIBLE : View.GONE);
        selectedVisibility = blog || video ? VISIBILITY_PUBLIC : VISIBILITY_USERS;
    }

    private void setupVisibilityDropdown() {
        visibilityLabels = new String[]{
                getString(R.string.create_content_visibility_author),
                getString(R.string.create_content_visibility_users),
                getString(R.string.create_content_visibility_public)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, visibilityLabels);
        visibilityInput.setAdapter(adapter);
        visibilityInput.setOnItemClickListener((parent, view, position, id) ->
                selectedVisibility = VISIBILITY_VALUES[position]);
        setVisibilitySelection(selectedVisibility);
    }

    private void setVisibilitySelection(int visibility) {
        int index = 0;
        for (int i = 0; i < VISIBILITY_VALUES.length; i++) {
            if (VISIBILITY_VALUES[i] == visibility) {
                index = i;
                break;
            }
        }
        selectedVisibility = VISIBILITY_VALUES[index];
        visibilityInput.setText(visibilityLabels[index], false);
    }

    private void setupCategoryDropdown() {
        categoryInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, new ArrayList<>()));
        categoryInput.setOnItemClickListener((parent, view, position, id) ->
                selectedCategoryId = position <= 0 ? 0 : categories.get(position - 1).getId());
        viewModel.getCategories().observe(getViewLifecycleOwner(), this::applyCategories);
    }

    private void applyCategories(@Nullable List<Category> values) {
        categories.clear();
        if (values != null) {
            categories.addAll(values);
        }
        List<String> labels = new ArrayList<>(categories.size() + 1);
        labels.add(getString(R.string.create_content_category_none));
        for (Category category : categories) {
            labels.add(category.getName());
        }
        categoryInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));
        selectedCategoryId = 0;
        categoryInput.setText(labels.get(0), false);
    }

    private void publish() {
        String authorization = getAuthorization();
        if (authorization == null) {
            showMessage(getString(R.string.create_content_login_expired));
            return;
        }
        if (contentType == ContentType.SHORT_VIDEO) {
            publishVideo(authorization);
            return;
        }
        if (contentType == ContentType.IMAGE_GALLERY) {
            if (selectedImages.isEmpty()) {
                showMessage(getString(R.string.create_content_images_required));
                return;
            }
            showProgress(getString(R.string.create_content_uploading));
            ContentResolver resolver = requireContext().getApplicationContext()
                    .getContentResolver();
            List<Uri> uris = new ArrayList<>(selectedImages);
            String caption = captionInput.getText().toString();
            int visibility = selectedVisibility;
            String readFailed = getString(R.string.create_content_read_failed);
            imageReader.execute(() -> {
                try {
                    List<ContentRepository.UploadFile> files = new ArrayList<>();
                    for (Uri uri : uris) {
                        files.add(readUploadFile(resolver, uri));
                    }
                    viewModel.publishGallery(caption, files, visibility, authorization);
                } catch (Exception error) {
                    viewModel.fail(readFailed);
                }
            });
            return;
        }

        String body = bodyInput.getText().toString().trim();
        if (body.isEmpty()) {
            showMessage(getString(R.string.create_content_required));
            return;
        }
        showProgress(getString(R.string.create_content_publishing));
        String tags = contentType == ContentType.BLOG ? tagsInput.getText().toString() : "";
        viewModel.publishText(contentType, titleInput.getText().toString(),
                summaryInput.getText().toString(), body, tags,
                selectedCategoryId, selectedVisibility, authorization);
    }

    private void publishVideo(String authorization) {
        if (selectedVideo == null) {
            showMessage(getString(R.string.create_content_video_required));
            return;
        }
        showProgress(getString(R.string.create_content_uploading_video));
        final ContentResolver resolver = requireContext().getApplicationContext()
                .getContentResolver();
        final android.content.Context appContext =
                requireContext().getApplicationContext();
        final Uri uri = selectedVideo;
        final String fileName = selectedVideoName;
        final String mimeType = selectedVideoMime;
        final long length = selectedVideoSize;
        final String title = titleInput.getText().toString();
        final String description = bodyInput.getText().toString();
        final int visibility = selectedVisibility;
        final String readFailed = getString(R.string.create_content_read_video_failed);
        imageReader.execute(() -> {
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
        selectedVideo = uri;
        selectedVideoName = name;
        selectedVideoMime = mimeType;
        selectedVideoSize = queryFileSize(resolver, uri);
        videoStatus.setText(getString(R.string.create_content_video_selected, name));
    }

    private void onImagesSelected(@Nullable List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        selectedImages.clear();
        if (uris.size() > MAX_IMAGES) {
            selectedImages.addAll(uris.subList(0, MAX_IMAGES));
            Toast.makeText(requireContext(), R.string.create_content_too_many_images,
                    Toast.LENGTH_SHORT).show();
        } else {
            selectedImages.addAll(uris);
        }
        imagesStatus.setText(getString(R.string.create_content_images_selected,
                selectedImages.size()));
    }

    private void showProgress(String message) {
        progress.setVisibility(View.VISIBLE);
        publishButton.setEnabled(false);
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

    private static ContentRepository.UploadFile readUploadFile(ContentResolver resolver, Uri uri)
            throws IOException {
        String mimeType = resolver.getType(uri);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/jpeg";
        }
        String fileName = queryDisplayName(resolver, uri);
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "image" + extensionFor(mimeType);
        } else if (!fileName.contains(".")) {
            fileName = fileName + extensionFor(mimeType);
        }
        byte[] data = readBytes(resolver.openInputStream(uri));
        return new ContentRepository.UploadFile(fileName, mimeType, data);
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

    private static byte[] readBytes(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("Image stream is unavailable.");
        }
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_IMAGE_BYTES) {
                    throw new IOException("Image is too large.");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static String extensionFor(String mimeType) {
        if ("image/png".equals(mimeType)) {
            return ".png";
        }
        if ("image/webp".equals(mimeType)) {
            return ".webp";
        }
        if ("image/gif".equals(mimeType)) {
            return ".gif";
        }
        return ".jpg";
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

    @Nullable
    private static ContentType contentTypeFromName(String name) {
        for (ContentType type : ContentType.values()) {
            if (type.getEndpoint().equals(name)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        imageReader.shutdownNow();
        super.onDestroyView();
    }
}
