package com.sinoll.synapse_app.ui.notifications;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.data.model.ContentItem;
import com.sinoll.synapse_app.ui.content.ContentDetailFragment;
import com.sinoll.synapse_app.ui.content.RemoteImageLoader;
import com.sinoll.synapse_app.ui.profile.AccountViewModel;

import org.json.JSONObject;

public final class NotificationsFragment extends Fragment {

    private AccountViewModel accountViewModel;
    private PublishedContentViewModel publishedContentViewModel;
    private MyContentAdapter adapter;
    private RemoteImageLoader avatarLoader;
    private android.widget.ImageView avatarView;
    private View loginButton;
    private View logoutButton;
    private TextView nameView;
    private TextView subtitleView;
    private TextView messageView;
    private TextView stateView;
    private SwipeRefreshLayout refreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        publishedContentViewModel =
                new ViewModelProvider(this).get(PublishedContentViewModel.class);

        avatarView = view.findViewById(R.id.profile_avatar);
        avatarLoader = new RemoteImageLoader();
        nameView = view.findViewById(R.id.profile_name);
        subtitleView = view.findViewById(R.id.profile_subtitle);
        messageView = view.findViewById(R.id.profile_message);
        stateView = view.findViewById(R.id.profile_content_state);
        refreshLayout = view.findViewById(R.id.profile_content_refresh);

        loginButton = view.findViewById(R.id.profile_login);
        loginButton.setOnClickListener(ignored -> showLoginDialog());
        logoutButton = view.findViewById(R.id.profile_logout);
        logoutButton.setOnClickListener(ignored -> accountViewModel.logout());

        RecyclerView contentList = view.findViewById(R.id.profile_content_list);
        contentList.setLayoutManager(new LinearLayoutManager(requireContext()));
        contentList.addItemDecoration(new DividerItemDecoration(
                requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new MyContentAdapter(this::openDetail);
        contentList.setAdapter(adapter);
        contentList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (dy > 0 && layoutManager instanceof LinearLayoutManager
                        && ((LinearLayoutManager) layoutManager)
                        .findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    publishedContentViewModel.loadNextPage();
                }
            }
        });
        refreshLayout.setOnRefreshListener(() -> {
            if (accountViewModel.getUser().getValue() == null) {
                refreshLayout.setRefreshing(false);
            } else {
                publishedContentViewModel.refresh();
            }
        });

        accountViewModel.getUser().observe(getViewLifecycleOwner(), this::showUser);
        accountViewModel.getMessage().observe(getViewLifecycleOwner(), this::showMessage);
        publishedContentViewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            adapter.submitItems(items);
            renderContentState();
        });
        publishedContentViewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            refreshLayout.setRefreshing(Boolean.TRUE.equals(loading) && adapter.getItemCount() > 0);
            renderContentState();
        });
        publishedContentViewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.trim().isEmpty()) {
                return;
            }
            if (adapter.getItemCount() == 0) {
                stateView.setText(message);
                stateView.setVisibility(View.VISIBLE);
            } else {
                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
            }
        });
        accountViewModel.refreshSession();
    }

    private void showUser(@Nullable JSONObject user) {
        JSONObject account = user == null ? null : user.optJSONObject("user");
        if (account == null) {
            account = user;
        }
        int userId = account == null ? 0
                : account.optInt("id", account.optInt("user_id", 0));
        String authorization = accountViewModel.getAuthorizationHeader();
        boolean loggedIn = account != null && userId > 0 && authorization != null;
        loginButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        logoutButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        if (!loggedIn) {
            nameView.setText(R.string.profile_welcome);
            subtitleView.setText(R.string.profile_subtitle);
            avatarLoader.load("", avatarView, R.drawable.ic_avatar_default);
            publishedContentViewModel.clear();
            renderContentState();
            return;
        }

        String name = account.optString("nickname",
                account.optString("username", account.optString("email", "")));
        nameView.setText(name.isEmpty() ? getString(R.string.profile_welcome) : name);
        String email = account.optString("email", "");
        subtitleView.setText(email.isEmpty()
                ? account.optString("username", "") : email);
        avatarLoader.load(account.optString("avatar_url", ""), avatarView,
                R.drawable.ic_avatar_default);

        publishedContentViewModel.loadFirstPage(userId, authorization);
    }

    private void showMessage(@Nullable String message) {
        if (message == null || message.trim().isEmpty()) {
            messageView.setVisibility(View.GONE);
            return;
        }
        messageView.setText(message);
        messageView.setVisibility(View.VISIBLE);
    }

    private void renderContentState() {
        if (adapter == null || stateView == null || publishedContentViewModel == null) {
            return;
        }
        if (accountViewModel.getUser().getValue() == null) {
            stateView.setText(R.string.profile_login_for_content);
            stateView.setVisibility(View.VISIBLE);
        } else if (Boolean.TRUE.equals(publishedContentViewModel.getLoading().getValue())
                && adapter.getItemCount() == 0) {
            stateView.setText(R.string.content_loading);
            stateView.setVisibility(View.VISIBLE);
        } else if (adapter.getItemCount() == 0
                && (publishedContentViewModel.getErrorMessage().getValue() == null
                || publishedContentViewModel.getErrorMessage().getValue().trim().isEmpty())) {
            stateView.setText(R.string.profile_content_empty);
            stateView.setVisibility(View.VISIBLE);
        } else if (adapter.getItemCount() > 0) {
            stateView.setVisibility(View.GONE);
        }
    }

    private void showLoginDialog() {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, 0);
        EditText username = field(R.string.profile_username_hint, InputType.TYPE_CLASS_TEXT);
        EditText password = field(R.string.profile_password_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(username);
        form.addView(password);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_login_title)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.profile_login, (dialog, which) -> {
                    if (value(username).isEmpty() || value(password).isEmpty()) {
                        showMessage(getString(R.string.form_required));
                        return;
                    }
                    accountViewModel.login(value(username), value(password));
                })
                .show();
    }

    private EditText field(int hint, int inputType) {
        EditText editText = new EditText(requireContext());
        editText.setSingleLine(true);
        editText.setHint(hint);
        editText.setInputType(inputType);
        return editText;
    }

    private static String value(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void openDetail(ContentItem item) {
        Bundle arguments = new Bundle();
        arguments.putString(ContentDetailFragment.ARG_CONTENT_TYPE, item.getType());
        arguments.putInt(ContentDetailFragment.ARG_CONTENT_ID, item.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_profile_to_content_detail, arguments);
    }

}
