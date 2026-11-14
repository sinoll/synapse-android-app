package com.sinoll.synapse_app.ui.notifications;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

import com.google.android.material.button.MaterialButton;
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
    private View registerButton;
    private View logoutButton;
    private TextView nameView;
    private TextView subtitleView;
    private TextView messageView;
    private TextView stateView;
    private SwipeRefreshLayout refreshLayout;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MaterialButton registrationCodeButton;
    private AlertDialog registrationDialog;
    private TextView registrationErrorView;
    private int registrationCodeCountdown;
    private boolean registrationCodeRequestPending;
    private final Runnable updateRegistrationCodeCountdown = new Runnable() {
        @Override
        public void run() {
            if (registrationCodeCountdown <= 0 || registrationCodeButton == null) {
                resetRegistrationCodeCountdown();
                return;
            }
            registrationCodeButton.setText(getString(
                    R.string.profile_request_code_countdown, registrationCodeCountdown));
            registrationCodeCountdown--;
            mainHandler.postDelayed(this, 1000);
        }
    };

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
        view.findViewById(R.id.profile_search).setOnClickListener(button ->
                Navigation.findNavController(button).navigate(R.id.action_global_search));

        loginButton = view.findViewById(R.id.profile_login);
        loginButton.setOnClickListener(ignored -> showLoginDialog());
        registerButton = view.findViewById(R.id.profile_register);
        registerButton.setOnClickListener(ignored -> showRegistrationDialog());
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
        accountViewModel.getRegistrationCompleted().observe(getViewLifecycleOwner(), completed -> {
            if (Boolean.TRUE.equals(completed) && registrationDialog != null) {
                registrationDialog.dismiss();
            }
        });
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
        registerButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
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
            if (registrationErrorView != null) {
                registrationErrorView.setVisibility(View.GONE);
            }
            return;
        }
        if (registrationCodeRequestPending) {
            registrationCodeRequestPending = false;
            if (!getString(R.string.profile_registration_code_sent).equals(message)) {
                resetRegistrationCodeCountdown();
            }
        }
        messageView.setText(message);
        messageView.setVisibility(View.VISIBLE);
        if (registrationDialog != null && registrationErrorView != null) {
            registrationErrorView.setText(message);
            registrationErrorView.setVisibility(View.VISIBLE);
        }
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

    private void showRegistrationDialog() {
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding, padding, 0);

        EditText username = field(R.string.profile_username_hint, InputType.TYPE_CLASS_TEXT);
        EditText nickname = field(R.string.profile_nickname_hint, InputType.TYPE_CLASS_TEXT);
        EditText email = field(R.string.profile_email_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText code = field(R.string.profile_code_hint,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
        EditText password = field(R.string.profile_password_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirmPassword = field(R.string.profile_confirm_password_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        form.addView(username);
        form.addView(nickname);
        form.addView(email);
        LinearLayout codeRow = new LinearLayout(requireContext());
        codeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        codeRow.addView(code, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton sendCode = new MaterialButton(requireContext());
        sendCode.setText(R.string.profile_request_code);
        LinearLayout.LayoutParams codeButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        codeButtonParams.setMarginStart((int) (8 * getResources().getDisplayMetrics().density));
        codeRow.addView(sendCode, codeButtonParams);
        form.addView(codeRow);
        form.addView(password);
        form.addView(confirmPassword);
        registrationErrorView = new TextView(requireContext());
        registrationErrorView.setTextColor(requireContext().getColor(R.color.brand_primary));
        registrationErrorView.setTextSize(13);
        registrationErrorView.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errorParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        form.addView(registrationErrorView, errorParams);

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.addView(form);
        registrationDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_register_title)
                .setView(scrollView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.profile_register, null)
                .create();
        registrationCodeButton = sendCode;
        sendCode.setOnClickListener(ignored -> {
            String emailValue = value(email);
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
                showMessage(getString(R.string.profile_registration_email_required));
                return;
            }
            registrationCodeRequestPending = true;
            sendCode.setEnabled(false);
            sendCode.setText(R.string.profile_sending_code);
            registrationCodeCountdown = 60;
            mainHandler.removeCallbacks(updateRegistrationCodeCountdown);
            mainHandler.post(updateRegistrationCodeCountdown);
            accountViewModel.requestRegistrationCode(emailValue);
        });
        registrationDialog.setOnShowListener(ignored -> registrationDialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String usernameValue = value(username);
            String nicknameValue = value(nickname);
            String emailValue = value(email);
            String codeValue = value(code);
            String passwordValue = value(password);
            String confirmPasswordValue = value(confirmPassword);
            if (usernameValue.isEmpty() || passwordValue.isEmpty()
                    || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
                showMessage(getString(R.string.form_invalid_registration));
                return;
            }
            if (codeValue.isEmpty()) {
                showMessage(getString(R.string.profile_code_required));
                return;
            }
            if (!passwordValue.equals(confirmPasswordValue)) {
                showMessage(getString(R.string.form_invalid_registration));
                return;
            }
            accountViewModel.register(usernameValue, nicknameValue, emailValue, codeValue,
                    passwordValue, confirmPasswordValue);
        }));
        registrationDialog.setOnDismissListener(ignored -> {
            mainHandler.removeCallbacks(updateRegistrationCodeCountdown);
            registrationCodeCountdown = 0;
            registrationCodeButton = null;
            registrationErrorView = null;
            registrationCodeRequestPending = false;
            registrationDialog = null;
        });
        registrationDialog.show();
    }

    private void resetRegistrationCodeCountdown() {
        mainHandler.removeCallbacks(updateRegistrationCodeCountdown);
        registrationCodeCountdown = 0;
        if (registrationCodeButton != null) {
            registrationCodeButton.setEnabled(true);
            registrationCodeButton.setText(R.string.profile_request_code);
        }
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

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacks(updateRegistrationCodeCountdown);
        if (registrationDialog != null) {
            registrationDialog.dismiss();
            registrationDialog = null;
        }
        registrationCodeButton = null;
        registrationErrorView = null;
        super.onDestroyView();
    }

    private void openDetail(ContentItem item) {
        Bundle arguments = new Bundle();
        arguments.putString(ContentDetailFragment.ARG_CONTENT_TYPE, item.getType());
        arguments.putInt(ContentDetailFragment.ARG_CONTENT_ID, item.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_profile_to_content_detail, arguments);
    }

}
