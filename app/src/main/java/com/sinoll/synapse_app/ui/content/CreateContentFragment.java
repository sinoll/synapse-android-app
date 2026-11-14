package com.sinoll.synapse_app.ui.content;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.util.Base64;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final Map<String, Uri> blogImageSources = new LinkedHashMap<>();

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
    private View blogEditorSection;
    private WebView blogEditor;
    private View blogInsertImageButton;
    private boolean blogEditorReady;
    private View imagesSection;
    private TextView imagesStatus;
    private View insertImagesButton;
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
        blogEditorSection = view.findViewById(R.id.create_blog_editor_section);
        blogEditor = view.findViewById(R.id.create_blog_editor);
        blogInsertImageButton = view.findViewById(R.id.create_blog_insert_image);
        imagesSection = view.findViewById(R.id.create_images_section);
        imagesStatus = view.findViewById(R.id.create_images_status);
        insertImagesButton = view.findViewById(R.id.create_insert_images);
        videoSection = view.findViewById(R.id.create_video_section);
        videoStatus = view.findViewById(R.id.create_video_status);
        publishButton = view.findViewById(R.id.create_publish);
        progress = view.findViewById(R.id.create_progress);
        messageView = view.findViewById(R.id.create_message);

        Bundle arguments = getArguments();
        String typeName = arguments == null ? null : arguments.getString(ARG_CONTENT_TYPE);
        ContentType parsed = contentTypeFromName(typeName);
        contentType = parsed == null ? ContentType.MOMENT : parsed;
        blogImageSources.clear();
        blogEditorReady = false;
        bindType();
        setupVisibilityDropdown();
        if (contentType == ContentType.BLOG) {
            setupCategoryDropdown();
            setupBlogEditor(view);
            viewModel.loadCategories();
        }
        publishButton.setOnClickListener(ignored -> publish());
        view.findViewById(R.id.create_select_images)
                .setOnClickListener(ignored -> imagePicker.launch("image/*"));
        insertImagesButton.setOnClickListener(ignored -> imagePicker.launch("image/*"));
        blogInsertImageButton.setOnClickListener(ignored -> pickBlogImages());
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
        blogEditorSection.setVisibility(blog ? View.VISIBLE : View.GONE);
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
        bodyInput.setVisibility(gallery || blog ? View.GONE : View.VISIBLE);
        bodyInput.setHint(video
                ? R.string.create_content_video_description_hint
                : contentType == ContentType.MOMENT
                        ? R.string.create_content_moment_body_hint
                        : R.string.create_content_body_hint);
        captionInput.setVisibility(gallery ? View.VISIBLE : View.GONE);
        tagsInput.setVisibility(blog ? View.VISIBLE : View.GONE);
        categoryLayout.setVisibility(blog ? View.VISIBLE : View.GONE);
        insertImagesButton.setVisibility(contentType == ContentType.MOMENT
                ? View.VISIBLE : View.GONE);
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

    private void setupBlogEditor(View root) {
        WebSettings settings = blogEditor.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        blogEditor.setBackgroundColor(Color.TRANSPARENT);
        blogEditor.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (view == blogEditor) {
                    blogEditorReady = true;
                }
            }
        });
        blogEditor.loadDataWithBaseURL("https://editor.local/",
                blogEditorDocument(), "text/html", "UTF-8", null);

        root.findViewById(R.id.create_blog_heading)
                .setOnClickListener(ignored -> showBlogBlockMenu());
        root.findViewById(R.id.create_blog_bold)
                .setOnClickListener(ignored -> runBlogEditorCommand("bold", ""));
        root.findViewById(R.id.create_blog_italic)
                .setOnClickListener(ignored -> runBlogEditorCommand("italic", ""));
        root.findViewById(R.id.create_blog_color)
                .setOnClickListener(ignored -> showBlogColorMenu());
        root.findViewById(R.id.create_blog_size)
                .setOnClickListener(ignored -> showBlogSizeMenu());
    }

    private void showBlogBlockMenu() {
        withSavedBlogSelection(() -> {
            String[] labels = {"正文", "一级标题", "二级标题", "三级标题"};
            String[] blocks = {"p", "h1", "h2", "h3"};
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.create_blog_format_heading)
                    .setItems(labels, (dialog, position) ->
                            runBlogEditorCommand("formatBlock", blocks[position]))
                    .show();
        });
    }

    private void showBlogColorMenu() {
        withSavedBlogSelection(() -> {
            String[] labels = {"黑色", "红色", "橙色", "蓝色", "绿色"};
            String[] colors = {"#202124", "#D93025", "#E37400", "#1967D2", "#188038"};
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.create_blog_color_dialog)
                    .setItems(labels, (dialog, position) ->
                            runBlogEditorColor(colors[position]))
                    .show();
        });
    }

    private void showBlogSizeMenu() {
        withSavedBlogSelection(() -> {
            String[] labels = {"较小", "小", "正常", "大", "较大", "特大"};
            String[] sizes = {"1", "2", "3", "4", "5", "6"};
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.create_blog_size_dialog)
                    .setItems(labels, (dialog, position) ->
                            runBlogEditorCommand("fontSize", sizes[position]))
                    .show();
        });
    }

    private void withSavedBlogSelection(Runnable action) {
        if (!blogEditorReady) {
            showMessage(getString(R.string.create_blog_editor_loading));
            return;
        }
        blogEditor.evaluateJavascript("window.saveSelection()", ignored -> {
            if (isAdded()) {
                action.run();
            }
        });
    }

    private void runBlogEditorCommand(String command, String value) {
        if (!blogEditorReady) {
            showMessage(getString(R.string.create_blog_editor_loading));
            return;
        }
        blogEditor.evaluateJavascript("window.applyCommand('"
                + command + "','" + value + "')", null);
    }

    private void runBlogEditorColor(String color) {
        if (!blogEditorReady) {
            showMessage(getString(R.string.create_blog_editor_loading));
            return;
        }
        blogEditor.evaluateJavascript("window.applyColor('" + color + "')", null);
    }

    private void pickBlogImages() {
        withSavedBlogSelection(() -> imagePicker.launch("image/*"));
    }

    private static String blogEditorDocument() {
        return "<!doctype html><html><head><meta name='viewport' "
                + "content='width=device-width, initial-scale=1'>"
                + "<style>html,body{margin:0;padding:0;background:transparent;}"
                + "body{font-family:sans-serif;color:#292929;font-size:16px;}"
                + "#editor{box-sizing:border-box;min-height:280px;padding:14px;"
                + "outline:none;overflow-wrap:anywhere;line-height:1.6;}"
                + "#editor h1{font-size:28px}#editor h2{font-size:24px}"
                + "#editor h3{font-size:20px}"
                + "#editor img{display:block;max-width:100%;height:auto;margin:10px 0;}"
                + "</style></head><body><div id='editor' contenteditable='true'>"
                + "<p><br></p></div><script>(function(){"
                + "var editor=document.getElementById('editor'),savedRange=null;"
                + "window.saveSelection=function(){var s=window.getSelection();"
                + "if(s&&s.rangeCount&&(s.anchorNode===editor||editor.contains(s.anchorNode)))"
                + "savedRange=s.getRangeAt(0).cloneRange();};"
                + "function restoreSelection(){editor.focus();var s=window.getSelection();"
                + "if(savedRange){s.removeAllRanges();s.addRange(savedRange);}"
                + "else{var r=document.createRange();r.selectNodeContents(editor);"
                + "r.collapse(false);s.removeAllRanges();s.addRange(r);}}"
                + "document.addEventListener('selectionchange',window.saveSelection);"
                + "editor.addEventListener('keyup',window.saveSelection);"
                + "editor.addEventListener('mouseup',window.saveSelection);"
                + "editor.addEventListener('input',window.saveSelection);"
                + "window.applyCommand=function(c,v){restoreSelection();"
                + "document.execCommand('styleWithCSS',false,true);"
                + "document.execCommand(c,false,v);window.saveSelection();};"
                + "window.applyColor=function(c){restoreSelection();"
                + "document.execCommand('styleWithCSS',false,true);"
                + "document.execCommand('foreColor',false,c);window.saveSelection();};"
                + "window.insertImage=function(id,src){restoreSelection();"
                + "var s=window.getSelection(),r=s.getRangeAt(0),img=document.createElement('img');"
                + "img.setAttribute('data-image-id',id);img.src=src;"
                + "img.style.maxWidth='100%';img.style.height='auto';"
                + "img.style.display='block';img.style.margin='10px 0';"
                + "r.deleteContents();r.insertNode(img);"
                + "var br=document.createElement('br');img.parentNode.insertBefore(br,img.nextSibling);"
                + "r.setStartAfter(br);r.collapse(true);s.removeAllRanges();s.addRange(r);"
                + "window.saveSelection();};"
                + "window.serializeEditor=function(){var clone=editor.cloneNode(true),"
                + "images=clone.querySelectorAll('img'),ids=[],untracked=false;"
                + "for(var i=0;i<images.length;i++){var id=images[i].getAttribute('data-image-id');"
                + "if(!id){untracked=true;continue;}ids.push(id);"
                + "images[i].setAttribute('src','__SYNAPSE_IMAGE_'+(ids.length-1)+'__');"
                + "images[i].removeAttribute('data-image-id');}"
                + "return JSON.stringify({html:clone.innerHTML,text:editor.innerText||'',"
                + "imageIds:ids,untrackedImages:untracked});};})();</script></body></html>";
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

        if (contentType == ContentType.BLOG) {
            publishBlog(authorization);
            return;
        }

        if (contentType == ContentType.MOMENT) {
            publishMoment(authorization);
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

    private void publishBlog(String authorization) {
        if (!blogEditorReady) {
            showMessage(getString(R.string.create_blog_editor_loading));
            return;
        }
        showProgress(getString(R.string.create_content_publishing));
        String title = titleInput.getText().toString();
        String summary = summaryInput.getText().toString();
        String tags = tagsInput.getText().toString();
        int categoryId = selectedCategoryId;
        int visibility = selectedVisibility;
        blogEditor.evaluateJavascript("window.serializeEditor()", result -> {
            if (!isAdded() || blogEditor == null) {
                return;
            }
            try {
                String json = new JSONArray("[" + result + "]").getString(0);
                JSONObject payload = new JSONObject(json);
                if (payload.optBoolean("untrackedImages")) {
                    viewModel.fail(getString(R.string.create_blog_untracked_images));
                    return;
                }
                String content = payload.optString("html", "");
                JSONArray imageIds = payload.optJSONArray("imageIds");
                int imageCount = imageIds == null ? 0 : imageIds.length();
                String plainText = payload.optString("text", "")
                        .replace("\u200B", "").trim();
                if (content.trim().isEmpty()
                        || plainText.isEmpty() && imageCount == 0) {
                    viewModel.fail(getString(R.string.create_content_required));
                    return;
                }
                if (imageCount > 0) {
                    showMessage(getString(R.string.create_content_uploading));
                }

                List<Uri> imageUris = new ArrayList<>(imageCount);
                for (int i = 0; i < imageCount; i++) {
                    String imageId = imageIds.getString(i);
                    Uri uri = blogImageSources.get(imageId);
                    if (uri == null) {
                        viewModel.fail(getString(R.string.create_blog_editor_invalid));
                        return;
                    }
                    imageUris.add(uri);
                }
                ContentResolver resolver = requireContext().getApplicationContext()
                        .getContentResolver();
                String readFailed = getString(R.string.create_content_read_failed);
                imageReader.execute(() -> {
                    try {
                        List<ContentRepository.UploadFile> files =
                                new ArrayList<>(imageUris.size());
                        for (Uri uri : imageUris) {
                            files.add(readUploadFile(resolver, uri));
                        }
                        viewModel.publishBlog(title, summary, content, tags,
                                categoryId, files, visibility, authorization);
                    } catch (Exception error) {
                        viewModel.fail(readFailed);
                    }
                });
            } catch (Exception error) {
                viewModel.fail(getString(R.string.create_blog_editor_invalid));
            }
        });
    }

    private void publishMoment(String authorization) {
        SpannableStringBuilder content = new SpannableStringBuilder(bodyInput.getText());
        ImageSpan[] imageSpans = content.getSpans(0, content.length(), ImageSpan.class);
        Arrays.sort(imageSpans, Comparator.comparingInt(content::getSpanStart));
        if (content.toString().replace("\uFFFC", "").trim().isEmpty()
                && imageSpans.length == 0) {
            showMessage(getString(R.string.create_content_required));
            return;
        }
        String html = toMomentHtml(content, imageSpans);
        if (html.isEmpty()) {
            showMessage(getString(R.string.create_content_required));
            return;
        }

        ContentResolver resolver = requireContext().getApplicationContext()
                .getContentResolver();
        List<Uri> imageUris = new ArrayList<>(imageSpans.length);
        for (ImageSpan imageSpan : imageSpans) {
            String source = imageSpan.getSource();
            if (source == null || source.trim().isEmpty()) {
                showMessage(getString(R.string.create_content_read_failed));
                return;
            }
            imageUris.add(Uri.parse(source));
        }

        showProgress(imageUris.isEmpty()
                ? getString(R.string.create_content_publishing)
                : getString(R.string.create_content_uploading));
        int visibility = selectedVisibility;
        List<String> tags = CreateContentViewModel.extractHashtags(content.toString());
        String readFailed = getString(R.string.create_content_read_failed);
        imageReader.execute(() -> {
            try {
                List<ContentRepository.UploadFile> files = new ArrayList<>(imageUris.size());
                for (Uri uri : imageUris) {
                    files.add(readUploadFile(resolver, uri));
                }
                viewModel.publishMoment(html, files, tags, visibility, authorization);
            } catch (Exception error) {
                viewModel.fail(readFailed);
            }
        });
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
        if (contentType == ContentType.BLOG) {
            insertImagesIntoBlog(uris);
            return;
        }
        if (contentType == ContentType.MOMENT) {
            insertImagesIntoMoment(uris);
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

    private void insertImagesIntoBlog(List<Uri> uris) {
        int available = MAX_IMAGES - blogImageSources.size();
        if (available <= 0) {
            Toast.makeText(requireContext(), R.string.create_blog_editor_image_limit,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int count = Math.min(available, uris.size());
        if (count < uris.size()) {
            Toast.makeText(requireContext(), R.string.create_blog_editor_image_limit,
                    Toast.LENGTH_SHORT).show();
        }
        List<Uri> selected = new ArrayList<>(uris.subList(0, count));
        List<String> imageIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            imageIds.add(UUID.randomUUID().toString().replace("-", ""));
        }
        int targetWidth = blogEditor.getWidth() - (int) (28
                * getResources().getDisplayMetrics().density);
        if (targetWidth <= 0) {
            targetWidth = (int) (280 * getResources().getDisplayMetrics().density);
        }
        int imageWidth = targetWidth;
        ContentResolver resolver = requireContext().getApplicationContext()
                .getContentResolver();
        Activity activity = requireActivity();
        blogInsertImageButton.setEnabled(false);
        publishButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        showMessage(getString(R.string.create_content_preparing_images));
        imageReader.execute(() -> {
            List<String> previews = new ArrayList<>(selected.size());
            try {
                for (Uri uri : selected) {
                    previews.add(createBlogImagePreview(resolver, uri, imageWidth));
                }
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (isAdded() && blogEditor != null) {
                        finishBlogImagePreparation(
                                getString(R.string.create_content_read_failed));
                    }
                });
                return;
            }
            activity.runOnUiThread(() -> {
                if (!isAdded() || blogEditor == null) {
                    return;
                }
                for (int i = 0; i < selected.size(); i++) {
                    String imageId = imageIds.get(i);
                    blogImageSources.put(imageId, selected.get(i));
                    blogEditor.evaluateJavascript("window.insertImage('"
                            + imageId + "','" + previews.get(i) + "')", null);
                }
                finishBlogImagePreparation("");
            });
        });
    }

    private void finishBlogImagePreparation(String message) {
        progress.setVisibility(View.GONE);
        blogInsertImageButton.setEnabled(true);
        publishButton.setEnabled(!Boolean.TRUE.equals(viewModel.getPublishing().getValue()));
        showMessage(message);
    }

    private void insertImagesIntoMoment(List<Uri> uris) {
        SpannableStringBuilder currentContent =
                new SpannableStringBuilder(bodyInput.getText());
        ImageSpan[] existingSpans = currentContent.getSpans(
                0, currentContent.length(), ImageSpan.class);
        int available = MAX_IMAGES - existingSpans.length;
        if (available <= 0) {
            Toast.makeText(requireContext(), R.string.create_content_too_many_images,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int count = Math.min(available, uris.size());
        if (count < uris.size()) {
            Toast.makeText(requireContext(), R.string.create_content_too_many_images,
                    Toast.LENGTH_SHORT).show();
        }

        int selectionStart = Math.max(0, bodyInput.getSelectionStart());
        int selectionEnd = Math.max(selectionStart, bodyInput.getSelectionEnd());
        int targetWidth = bodyInput.getWidth() - bodyInput.getPaddingLeft()
                - bodyInput.getPaddingRight();
        if (targetWidth <= 0) {
            targetWidth = (int) (280 * getResources().getDisplayMetrics().density);
        }
        ContentResolver resolver = requireContext().getApplicationContext()
                .getContentResolver();
        List<Uri> selected = new ArrayList<>(uris.subList(0, count));
        int imageWidth = targetWidth;
        Activity activity = requireActivity();
        publishButton.setEnabled(false);
        insertImagesButton.setEnabled(false);
        bodyInput.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        showMessage(getString(R.string.create_content_preparing_images));
        imageReader.execute(() -> {
            List<ImageSpan> spans = new ArrayList<>(selected.size());
            try {
                for (Uri uri : selected) {
                    spans.add(createInlineImageSpan(resolver, uri, imageWidth));
                }
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (isAdded() && bodyInput != null) {
                        finishInlineImagePreparation(
                                getString(R.string.create_content_read_failed));
                    }
                });
                return;
            }
            activity.runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                if (bodyInput == null) {
                    return;
                }
                SpannableStringBuilder updated =
                        new SpannableStringBuilder(bodyInput.getText());
                int start = Math.min(selectionStart, updated.length());
                int end = Math.min(selectionEnd, updated.length());
                if (end > start) {
                    updated.delete(start, end);
                }
                int cursor = start;
                for (ImageSpan span : spans) {
                    if (cursor > 0 && updated.charAt(cursor - 1) != '\n') {
                        updated.insert(cursor++, "\n");
                    }
                    updated.insert(cursor, "\uFFFC\n");
                    updated.setSpan(span, cursor, cursor + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    cursor += 2;
                }
                bodyInput.setText(updated);
                bodyInput.setSelection(Math.min(cursor, updated.length()));
                finishInlineImagePreparation("");
            });
        });
    }

    private void finishInlineImagePreparation(String message) {
        progress.setVisibility(View.GONE);
        publishButton.setEnabled(!Boolean.TRUE.equals(viewModel.getPublishing().getValue()));
        insertImagesButton.setEnabled(true);
        bodyInput.setEnabled(true);
        showMessage(message);
    }

    private static ImageSpan createInlineImageSpan(ContentResolver resolver, Uri uri,
                                                   int maxWidth) throws IOException {
        Bitmap bitmap = decodeScaledImage(resolver, uri, maxWidth);
        BitmapDrawable drawable = new BitmapDrawable(
                android.content.res.Resources.getSystem(), bitmap);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        return new ImageSpan(drawable, uri.toString());
    }

    private static String createBlogImagePreview(ContentResolver resolver, Uri uri,
                                                 int maxWidth) throws IOException {
        Bitmap bitmap = decodeScaledImage(resolver, uri, maxWidth);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                throw new IOException("Image preview could not be encoded.");
            }
            return "data:image/jpeg;base64,"
                    + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        } finally {
            bitmap.recycle();
        }
    }

    private static Bitmap decodeScaledImage(ContentResolver resolver, Uri uri, int maxWidth)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Image stream is unavailable.");
            }
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Image dimensions are unavailable.");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / (options.inSampleSize * 2) >= maxWidth) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Image stream is unavailable.");
            }
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Image could not be decoded.");
        }
        if (bitmap.getWidth() > maxWidth) {
            int height = Math.max(1,
                    bitmap.getHeight() * maxWidth / bitmap.getWidth());
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, height, true);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            bitmap = scaled;
        }
        return bitmap;
    }

    private static String toMomentHtml(Spanned content, ImageSpan[] imageSpans) {
        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder();
        int imageIndex = 0;
        for (int position = 0; position < content.length(); position++) {
            if (imageIndex < imageSpans.length
                    && content.getSpanStart(imageSpans[imageIndex]) == position) {
                appendParagraph(html, text);
                html.append("<p><img src=\"__SYNAPSE_IMAGE_")
                        .append(imageIndex++)
                        .append("__\" alt=\"\"></p>");
                position = Math.max(position,
                        content.getSpanEnd(imageSpans[imageIndex - 1]) - 1);
                if (position + 1 < content.length()
                        && content.charAt(position + 1) == '\n') {
                    position++;
                }
                continue;
            }
            char character = content.charAt(position);
            if (character == '\uFFFC') {
                continue;
            }
            if (character == '\n') {
                text.append("<br>");
            } else {
                text.append(escapeHtml(String.valueOf(character)));
            }
        }
        appendParagraph(html, text);
        return html.toString();
    }

    private static void appendParagraph(StringBuilder html, StringBuilder text) {
        if (text.length() > 0) {
            html.append("<p>").append(text).append("</p>");
            text.setLength(0);
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
        blogEditorReady = false;
        blogImageSources.clear();
        if (blogEditor != null) {
            blogEditor.stopLoading();
            blogEditor.setWebViewClient(null);
            blogEditor.loadUrl("about:blank");
            blogEditor.destroy();
            blogEditor = null;
        }
        super.onDestroyView();
    }
}
