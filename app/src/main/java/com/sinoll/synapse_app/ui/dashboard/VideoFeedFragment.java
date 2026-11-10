package com.sinoll.synapse_app.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentPage;
import com.sinoll.synapse_app.data.session.SessionStore;
import com.sinoll.synapse_app.data.model.ContentType;
import com.sinoll.synapse_app.ui.content.ContentListViewModel;
import com.sinoll.synapse_app.ui.content.VideoPagerAdapter;

public final class VideoFeedFragment extends Fragment {

    private ContentListViewModel viewModel;
    private VideoPagerAdapter adapter;
    private ViewPager2 pager;
    private TextView state;
    private String errorMessage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pager = view.findViewById(R.id.video_pager);
        state = view.findViewById(R.id.video_feed_state);
        viewModel = new ViewModelProvider(this).get(ContentListViewModel.class);
        adapter = new VideoPagerAdapter();
        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                adapter.activate(position);
                if (position >= adapter.getItemCount() - 2) {
                    viewModel.loadNextPage();
                }
            }
        });

        viewModel.getPage().observe(getViewLifecycleOwner(), page -> {
            if (page != null) {
                adapter.submitItems(page.getItems());
            }
            renderState(page);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(),
                loading -> renderState(viewModel.getPage().getValue()));
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            errorMessage = message;
            renderState(viewModel.getPage().getValue());
        });

        String token = null;
        try {
            token = new SessionStore(requireContext()).getAuthorizationHeader();
        } catch (Exception error) {
            showMessage(getString(R.string.video_play_error));
        }
        viewModel.setSession(token, null);
        viewModel.setContentType(ContentType.SHORT_VIDEO);
    }

    private void renderState(@Nullable ContentPage page) {
        if (adapter.getItemCount() > 0) {
            state.setVisibility(View.GONE);
        } else if (Boolean.TRUE.equals(viewModel.getLoading().getValue())) {
            showMessage(getString(R.string.content_loading));
        } else if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            showMessage(errorMessage);
        } else if (page != null && page.getItems().isEmpty()) {
            showMessage(getString(R.string.video_empty));
        }
    }

    private void showMessage(String message) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (adapter != null) {
            adapter.pauseActive();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.activate(pager.getCurrentItem());
        }
    }
}
