package com.sinoll.synapse_app.ui.notifications;

import android.app.AlertDialog;
import android.os.CountDownTimer;
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

import com.google.android.material.snackbar.Snackbar;
import com.sinoll.synapse_app.R;
import com.sinoll.synapse_app.ui.profile.AccountViewModel;
import com.sinoll.synapse_app.ui.profile.MyContentFragment;

import org.json.JSONObject;

public final class NotificationsFragment extends Fragment {

    private AccountViewModel accountViewModel;
    private TextView nameView;
    private TextView subtitleView;
    private TextView messageView;
    private View loginButton;
    private View registerButton;
    private View passwordButton;
    private View myContentButton;
    private View logoutButton;
    private CountDownTimer codeCountdown;

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
        nameView = view.findViewById(R.id.profile_name);
        subtitleView = view.findViewById(R.id.profile_subtitle);
        messageView = view.findViewById(R.id.profile_message);
        loginButton = view.findViewById(R.id.profile_login);
        registerButton = view.findViewById(R.id.profile_register);
        passwordButton = view.findViewById(R.id.profile_password);
        myContentButton = view.findViewById(R.id.profile_my_content);
        logoutButton = view.findViewById(R.id.profile_logout);

        loginButton.setOnClickListener(ignored -> showLoginDialog());
        registerButton.setOnClickListener(ignored -> showRegisterDialog());
        passwordButton.setOnClickListener(ignored -> showPasswordDialog());
        myContentButton.setOnClickListener(ignored -> openMyContent());
        logoutButton.setOnClickListener(ignored -> accountViewModel.logout());

        accountViewModel.getUser().observe(getViewLifecycleOwner(), this::showUser);
        accountViewModel.getMessage().observe(getViewLifecycleOwner(), this::showMessage);
        accountViewModel.refreshSession();
    }

    private void showUser(@Nullable JSONObject user) {
        boolean loggedIn = user != null;
        loginButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        registerButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        passwordButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        myContentButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        logoutButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        if (loggedIn) {
            String name = user.optString("nickname",
                    user.optString("username", user.optString("email", "")));
            nameView.setText(name.isEmpty() ? getString(R.string.profile_welcome) : name);
            subtitleView.setText(user.optString("email", ""));
        } else {
            nameView.setText(R.string.profile_welcome);
            subtitleView.setText(R.string.profile_subtitle);
        }
    }

    private void showMessage(@Nullable String message) {
        if (message == null || message.trim().isEmpty()) {
            messageView.setVisibility(View.GONE);
            return;
        }
        messageView.setText(message);
        messageView.setVisibility(View.VISIBLE);
    }

    private void showLoginDialog() {
        LinearLayout form = form();
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
                    if (isEmpty(username) || isEmpty(password)) {
                        showMessage(getString(R.string.form_required));
                        return;
                    }
                    accountViewModel.login(value(username), value(password));
                })
                .show();
    }

    private void showRegisterDialog() {
        LinearLayout form = form();
        EditText username = field(R.string.profile_username_hint, InputType.TYPE_CLASS_TEXT);
        EditText nickname = field(R.string.profile_nickname_hint, InputType.TYPE_CLASS_TEXT);
        EditText email = field(R.string.profile_email_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText code = field(R.string.profile_code_hint, InputType.TYPE_CLASS_NUMBER);
        EditText password = field(R.string.profile_password_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirm = field(R.string.profile_confirm_password_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(username);
        form.addView(nickname);
        form.addView(email);
        form.addView(code);
        form.addView(password);
        form.addView(confirm);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_register_title)
                .setMessage(R.string.profile_registration_hint)
                .setView(form)
                .setNeutralButton(R.string.profile_request_code, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.profile_register, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button -> {
                if (!validEmail(email)) {
                    showMessage(getString(R.string.form_email_required));
                    return;
                }
                accountViewModel.requestRegistrationCode(value(email));
                startCodeCooldown(dialog);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                if (isEmpty(username) || !validEmail(email) || isEmpty(code)
                        || isEmpty(password) || !value(password).equals(value(confirm))) {
                    showMessage(getString(R.string.form_invalid_registration));
                    return;
                }
                dialog.dismiss();
                accountViewModel.register(value(username), value(nickname), value(email),
                        value(code), value(password), value(confirm));
            });
        });
        dialog.show();
    }

    private void showPasswordDialog() {
        LinearLayout form = form();
        EditText email = field(R.string.profile_email_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText code = field(R.string.profile_code_hint, InputType.TYPE_CLASS_NUMBER);
        EditText password = field(R.string.profile_password_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirm = field(R.string.profile_confirm_password_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(email);
        form.addView(code);
        form.addView(password);
        form.addView(confirm);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_password_title)
                .setMessage(R.string.profile_password_reset_hint)
                .setView(form)
                .setNeutralButton(R.string.profile_request_code, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.profile_change_password, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button -> {
                if (!validEmail(email)) {
                    showMessage(getString(R.string.form_email_required));
                    return;
                }
                accountViewModel.requestPasswordResetCode(value(email));
                startCodeCooldown(dialog);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                if (!validEmail(email) || isEmpty(code) || isEmpty(password)
                        || !value(password).equals(value(confirm))) {
                    showMessage(getString(R.string.form_invalid_password));
                    return;
                }
                dialog.dismiss();
                accountViewModel.resetPassword(value(email), value(code),
                        value(password), value(confirm));
            });
        });
        dialog.show();
    }

    private void openMyContent() {
        Integer userId = accountViewModel.getCurrentUserId();
        if (userId == null) {
            showMessage(getString(R.string.profile_login_required_action));
            return;
        }
        Bundle arguments = new Bundle();
        arguments.putInt(MyContentFragment.ARG_USER_ID, userId);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_profile_to_my_content, arguments);
    }

    private LinearLayout form() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, 0);
        return layout;
    }

    private EditText field(int hint, int inputType) {
        EditText editText = new EditText(requireContext());
        editText.setSingleLine(true);
        editText.setHint(hint);
        editText.setInputType(inputType);
        return editText;
    }

    private void startCodeCooldown(AlertDialog dialog) {
        if (codeCountdown != null) {
            codeCountdown.cancel();
        }
        android.widget.Button button = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        button.setEnabled(false);
        codeCountdown = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                button.setText(getString(R.string.profile_request_code_countdown,
                        (int) Math.ceil(millisUntilFinished / 1000.0)));
            }

            @Override
            public void onFinish() {
                button.setText(R.string.profile_request_code);
                button.setEnabled(true);
                codeCountdown = null;
            }
        }.start();
    }

    @Override
    public void onDestroyView() {
        if (codeCountdown != null) {
            codeCountdown.cancel();
            codeCountdown = null;
        }
        super.onDestroyView();
    }

    private boolean validEmail(EditText field) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(value(field)).matches();
    }

    private static boolean isEmpty(EditText field) {
        return value(field).trim().isEmpty();
    }

    private static String value(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}
