package com.sinoll.synapse_app.ui.content;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.data.session.SessionStore;

import java.util.Collections;

public final class ContentSearchFragment extends Fragment {

    private ContentSearchViewModel viewModel;
    private ContentListAdapter adapter;
    private EditText searchInput;
    private TextView stateView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_content_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        searchInput = view.findViewById(R.id.content_search_input);
        stateView = view.findViewById(R.id.content_search_state);
        RecyclerView results = view.findViewById(R.id.content_search_results);
        results.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ContentListAdapter(this::openDetail);
        results.setAdapter(adapter);

        view.findViewById(R.id.content_search_back).setOnClickListener(button ->
                Navigation.findNavController(button).popBackStack());
        view.findViewById(R.id.content_search_submit).setOnClickListener(ignored ->
                performSearch());
        searchInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        viewModel = new ViewModelProvider(this).get(ContentSearchViewModel.class);
        viewModel.getResults().observe(getViewLifecycleOwner(), items -> {
            adapter.submitItems(items == null ? Collections.emptyList() : items);
            renderState();
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), ignored -> renderState());
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && adapter.getItemCount() > 0) {
                Snackbar.make(view, errorMessage(error), Snackbar.LENGTH_LONG).show();
            }
            renderState();
        });
        renderState();
    }

    private void performSearch() {
        String authorization = null;
        try {
            authorization = new SessionStore(requireContext()).getAuthorizationHeader();
        } catch (Exception error) {
            // Public search remains available if the local session cannot be read.
        }
        InputMethodManager inputMethodManager = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        viewModel.search(searchInput.getText().toString(), authorization);
        renderState();
    }

    private void renderState() {
        if (adapter == null || stateView == null || searchInput == null || viewModel == null) {
            return;
        }
        if (adapter.getItemCount() > 0) {
            stateView.setVisibility(View.GONE);
        } else if (Boolean.TRUE.equals(viewModel.getLoading().getValue())) {
            showState(getString(R.string.content_search_loading));
        } else if (viewModel.getError().getValue() != null) {
            showState(errorMessage(viewModel.getError().getValue()));
        } else if (TextUtils.isEmpty(searchInput.getText())) {
            showState(getString(R.string.content_search_prompt));
        } else {
            showState(getString(R.string.content_search_empty));
        }
    }

    private void showState(String message) {
        stateView.setText(message);
        stateView.setVisibility(View.VISIBLE);
    }

    private String errorMessage(ContentSearchViewModel.SearchError error) {
        if (error.isPartial()) {
            return getString(R.string.content_search_partial_error);
        }
        return TextUtils.isEmpty(error.getMessage())
                ? getString(R.string.content_search_error) : error.getMessage();
    }

    private void openDetail(ContentItem item) {
        Bundle arguments = new Bundle();
        arguments.putString(ContentDetailFragment.ARG_CONTENT_TYPE, item.getType());
        arguments.putInt(ContentDetailFragment.ARG_CONTENT_ID, item.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_global_content_detail, arguments);
    }

    @Override
    public void onDestroyView() {
        adapter = null;
        searchInput = null;
        stateView = null;
        super.onDestroyView();
    }
}
