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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.data.session.SessionStore;
import com.sinoll.synapse_app.ui.profile.MyContentFragment;

public abstract class ContentListFragment extends Fragment {

    private ContentListViewModel viewModel;
    private ContentListAdapter adapter;
    private SwipeRefreshLayout refreshLayout;
    private TextView stateText;
    private EditText searchInput;
    private RecyclerView recyclerView;

    protected abstract ContentType initialContentType();

    protected int contentHeadingResource() {
        return R.string.content_heading;
    }
    protected int contentSubheadingResource() {
        return R.string.content_subheading;
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
        recyclerView = view.findViewById(R.id.content_list);
        TextView heading = view.findViewById(R.id.content_heading);
        TextView subheading = view.findViewById(R.id.content_subheading);
        heading.setText(contentHeadingResource());
        subheading.setText(contentSubheadingResource());
        if (!showsIntroHeader()) {
            heading.setVisibility(View.GONE);
            subheading.setVisibility(View.GONE);
        }
        Button searchButton = view.findViewById(R.id.content_search_button);
        adapter = new ContentListAdapter(this::openDetail);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        refreshLayout.setOnRefreshListener(this::searchContent);
        searchButton.setOnClickListener(ignored ->
                searchContent());
        View createButton = view.findViewById(R.id.content_create);
        if (isOwnerContent()) {
            createButton.setVisibility(View.GONE);
        } else {
            createButton.setOnClickListener(ignored -> openCreate());
        }
        searchInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchContent();
                return true;
            }
            return false;
        });
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
        getParentFragmentManager().setFragmentResultListener(
                CreateContentFragment.RESULT_CONTENT_CREATED, getViewLifecycleOwner(),
                (key, result) -> viewModel.loadFirstPage(
                        searchInput.getText().toString()));
        try {
            String authorization = new SessionStore(
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
        viewModel.loadFirstPage(searchInput.getText().toString());
    }

    private void openCreate() {
        String authorization = null;
        try {
            authorization = new SessionStore(requireContext()).getAuthorizationHeader();
        } catch (Exception error) {
            // A broken local session is treated as logged out.
        }
        if (authorization == null) {
            Toast.makeText(requireContext(), R.string.content_create_login_required,
                    Toast.LENGTH_SHORT).show();
            Navigation.findNavController(requireView()).navigate(R.id.navigation_profile);
            return;
        }
        Bundle arguments = new Bundle();
        arguments.putString(CreateContentFragment.ARG_CONTENT_TYPE,
                initialContentType().getEndpoint());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_global_create_content, arguments);
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
                : R.id.action_global_content_detail;
        Navigation.findNavController(requireView()).navigate(action, arguments);
    }

}
