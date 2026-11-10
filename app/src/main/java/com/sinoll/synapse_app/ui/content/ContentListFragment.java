package com.sinoll.synapse_app.ui.content;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.card.MaterialCardView;

import com.google.android.material.snackbar.Snackbar;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.ui.profile.MyContentFragment;

public abstract class ContentListFragment extends Fragment {

    private ContentListViewModel viewModel;
    private ContentListAdapter adapter;
    private SwipeRefreshLayout refreshLayout;
    private TextView stateText;
    private EditText searchInput;
    private EditText tagInput;
    private RecyclerView recyclerView;
    private final java.util.Map<Integer, MaterialCardView> categoryCards =
            new java.util.LinkedHashMap<>();

    protected abstract ContentType initialContentType();

    protected boolean showsTypeSelector() {
        return false;
    }

    protected boolean isOwnerContent() {
        return false;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_content_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ContentListViewModel.class);
        refreshLayout = view.findViewById(R.id.content_refresh);
        stateText = view.findViewById(R.id.content_state);
        searchInput = view.findViewById(R.id.content_search);
        tagInput = view.findViewById(R.id.content_tag);
        recyclerView = view.findViewById(R.id.content_list);
        Button searchButton = view.findViewById(R.id.content_search_button);
        adapter = new ContentListAdapter(this::openDetail);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        refreshLayout.setOnRefreshListener(this::searchContent);
        searchButton.setOnClickListener(ignored ->
                searchContent());
        searchInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchContent();
                return true;
            }
            return false;
        });
        if (!showsTypeSelector()) {
            view.findViewById(R.id.content_category_cards).setVisibility(View.GONE);
        } else {
            addCategory(view, R.id.content_category_blog, R.id.content_category_blog_icon,
                    ContentType.BLOG, "文");
            addCategory(view, R.id.content_category_moment, R.id.content_category_moment_icon,
                    ContentType.MOMENT, "动");
            addCategory(view, R.id.content_category_images, R.id.content_category_images_icon,
                    ContentType.IMAGE_GALLERY, "集");
            View videoCard = view.findViewById(R.id.content_category_video);
            if (isOwnerContent()) {
                addCategory(view, R.id.content_category_video, R.id.content_category_video_icon,
                        ContentType.SHORT_VIDEO, "影");
            } else {
                videoCard.setVisibility(View.GONE);
            }
            selectCategory(initialContentType());
        }
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView list, int dx, int dy) {
                super.onScrolled(list, dx, dy);
                if (dy <= 0 || !(list.getLayoutManager() instanceof LinearLayoutManager)) {
                    return;
                }
                LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
                if (adapter.getItemCount() > 0
                        && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    viewModel.loadNextPage();
                }
            }
        });

        observeViewModel(view);
        try {
            String authorization = new com.sinoll.synapse_app.data.session.SessionStore(
                    requireContext()).getAuthorizationHeader();
            Integer ownerId = isOwnerContent() && getArguments() != null
                    ? getArguments().getInt(MyContentFragment.ARG_USER_ID, 0) : null;
            viewModel.setSession(authorization, ownerId != null && ownerId > 0 ? ownerId : null);
        } catch (Exception error) {
            if (isOwnerContent()) {
                showState(getString(R.string.profile_login_required));
                return;
            }
        }
        viewModel.setContentType(initialContentType());
    }

    private void searchContent() {
        viewModel.loadFirstPage(searchInput.getText().toString(), tagInput.getText().toString());
    }

    private void observeViewModel(View root) {
        viewModel.getPage().observe(getViewLifecycleOwner(), page -> {
            if (page == null) {
                adapter.submitItems(java.util.Collections.emptyList());
                return;
            }
            adapter.submitItems(page.getItems());
            if (page.getItems().isEmpty()) {
                showState(getString(R.string.content_empty));
            } else {
                stateText.setVisibility(View.GONE);
            }
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            boolean loading = Boolean.TRUE.equals(isLoading);
            refreshLayout.setRefreshing(loading && adapter.getItemCount() > 0);
            if (loading && adapter.getItemCount() == 0) {
                showState(getString(R.string.content_loading));
            }
        });
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (TextUtils.isEmpty(message)) {
                return;
            }
            if (adapter.getItemCount() == 0) {
                showState(message);
            } else {
                Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void showState(String message) {
        stateText.setText(message);
        stateText.setVisibility(View.VISIBLE);
    }

    private void openDetail(ContentItem item) {
        Bundle arguments = new Bundle();
        arguments.putString(ContentDetailFragment.ARG_CONTENT_TYPE, item.getType());
        arguments.putInt(ContentDetailFragment.ARG_CONTENT_ID, item.getId());
        int action = isOwnerContent()
                ? R.id.action_my_content_to_content_detail
                : R.id.action_home_to_content_detail;
        Navigation.findNavController(requireView()).navigate(action, arguments);
    }

    private void addCategory(View root, int cardId, int iconId, ContentType type, String icon) {
        MaterialCardView card = root.findViewById(cardId);
        categoryCards.put(cardId, card);
        TextView iconView = root.findViewById(iconId);
        iconView.setText(icon);
        card.setOnClickListener(ignored -> {
            selectCategory(type);
            searchInput.setText("");
            tagInput.setText("");
            viewModel.setContentType(type);
        });
    }

    private void selectCategory(ContentType type) {
        int selectedId = type == ContentType.BLOG ? R.id.content_category_blog
                : type == ContentType.MOMENT ? R.id.content_category_moment
                : type == ContentType.IMAGE_GALLERY ? R.id.content_category_images
                : R.id.content_category_video;
        for (java.util.Map.Entry<Integer, MaterialCardView> entry : categoryCards.entrySet()) {
            boolean selected = entry.getKey() == selectedId;
            MaterialCardView card = entry.getValue();
            card.setStrokeWidth(selected ? dp(2) : dp(1));
            card.setStrokeColor(requireContext().getColor(selected
                    ? R.color.brand_primary : R.color.card_stroke));
            card.setCardBackgroundColor(requireContext().getColor(selected
                    ? R.color.card_selected : R.color.white));
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
