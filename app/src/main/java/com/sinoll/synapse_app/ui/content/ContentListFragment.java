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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.ui.profile.MyContentFragment;

import java.util.ArrayList;
import java.util.List;

public abstract class ContentListFragment extends Fragment {

    private ContentListViewModel viewModel;
    private ContentListAdapter adapter;
    private SwipeRefreshLayout refreshLayout;
    private TextView stateText;
    private EditText searchInput;
    private EditText tagInput;
    private RecyclerView recyclerView;
    private RecyclerView categoryRecycler;
    private ContentCategoryAdapter categoryAdapter;
    private PagerSnapHelper categorySnapHelper;
    private ContentType selectedCategoryType;

    protected abstract ContentType initialContentType();

    protected boolean showsTypeSelector() {
        return false;
    }

    protected boolean showsIntroHeader() {
        return true;
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
        if (!showsIntroHeader()) {
            view.findViewById(R.id.content_heading).setVisibility(View.GONE);
            view.findViewById(R.id.content_subheading).setVisibility(View.GONE);
            ConstraintLayout.LayoutParams categoryParams =
                    (ConstraintLayout.LayoutParams) view.findViewById(
                            R.id.content_category_cards).getLayoutParams();
            categoryParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            categoryParams.topToBottom = ConstraintLayout.LayoutParams.UNSET;
            categoryParams.topMargin = dp(12);
            view.findViewById(R.id.content_category_cards).setLayoutParams(categoryParams);
        }
        if (!showsTypeSelector()) {
            view.findViewById(R.id.content_category_cards).setVisibility(View.GONE);
        } else {
            setupCategories(view);
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

    private void setupCategories(View root) {
        categoryRecycler = root.findViewById(R.id.content_category_cards);
        List<ContentCategoryAdapter.Category> categories = new ArrayList<>();
        categories.add(new ContentCategoryAdapter.Category(ContentType.BLOG,
                getString(R.string.content_type_blog), "文"));
        categories.add(new ContentCategoryAdapter.Category(ContentType.MOMENT,
                getString(R.string.content_type_moment), "动"));
        categories.add(new ContentCategoryAdapter.Category(ContentType.IMAGE_GALLERY,
                getString(R.string.content_type_images), "集"));
        if (isOwnerContent()) {
            categories.add(new ContentCategoryAdapter.Category(ContentType.SHORT_VIDEO,
                    getString(R.string.title_dashboard), "影"));
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(
                requireContext(), RecyclerView.HORIZONTAL, false);
        categoryRecycler.setLayoutManager(layoutManager);
        categoryAdapter = new ContentCategoryAdapter(categories, position -> {
            updateSelectedCategory(position);
            categoryRecycler.smoothScrollToPosition(position);
        });
        categoryRecycler.setAdapter(categoryAdapter);
        categorySnapHelper = new PagerSnapHelper();
        categorySnapHelper.attachToRecyclerView(categoryRecycler);

        int sidePadding = Math.max(0,
                (getResources().getDisplayMetrics().widthPixels - dp(196)) / 2);
        categoryRecycler.setPadding(sidePadding, 0, sidePadding, 0);

        int initialPosition = 0;
        for (int position = 0; position < categories.size(); position++) {
            if (categories.get(position).type == initialContentType()) {
                initialPosition = position;
                break;
            }
        }
        selectedCategoryType = initialContentType();
        categoryAdapter.setSelectedPosition(initialPosition);
        categoryRecycler.scrollToPosition(initialPosition);
        categoryRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    return;
                }
                View snappedView = categorySnapHelper.findSnapView(layoutManager);
                if (snappedView != null) {
                    int position = layoutManager.getPosition(snappedView);
                    updateSelectedCategory(position);
                }
            }
        });
    }

    private void updateSelectedCategory(int position) {
        ContentCategoryAdapter.Category category = categoryAdapter.getCategory(position);
        categoryAdapter.setSelectedPosition(position);
        if (selectedCategoryType == category.type) {
            return;
        }
        selectedCategoryType = category.type;
        searchInput.setText("");
        tagInput.setText("");
        viewModel.setContentType(category.type);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
