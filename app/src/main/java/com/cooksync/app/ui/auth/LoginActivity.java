package com.cooksync.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.dtos.response.auth.AuthResponse;

/**
 * Entry-point Activity presenting the Login screen. Observes {@link LoginViewModel} for
 * field validation errors and network results, and navigates to the main application flow
 * upon a successful login.
 *
 * <p>The View layer contains <em>zero</em> validation or business logic — it only binds to
 * LiveData streams and delegates user actions to the ViewModel.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class LoginActivity extends AppCompatActivity {

    private LoginViewModel viewModel;

    private EditText etEmail;
    private EditText etPassword;
    private TextView tvEmailError;
    private TextView tvPasswordError;
    private ProgressBar progress;

    /**
     * Inflates the login layout, initialises the ViewModel, and sets up all observers and
     * click listeners.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param savedInstanceState saved instance state bundle (may be {@code null})
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        bindViews();
        observeViewModel();
        setListeners();
    }

    /**
     * Binds all view references from the inflated layout.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void bindViews() {
        etEmail        = findViewById(R.id.et_email);
        etPassword     = findViewById(R.id.et_password);
        tvEmailError   = findViewById(R.id.tv_email_error);
        tvPasswordError = findViewById(R.id.tv_password_error);
        progress       = findViewById(R.id.progress);
    }

    /**
     * Subscribes to all LiveData streams exposed by {@link LoginViewModel}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void observeViewModel() {
        viewModel.getEmailError().observe(this, error -> showFieldError(tvEmailError, error));
        viewModel.getPasswordError().observe(this, error -> showFieldError(tvPasswordError, error));

        viewModel.getLoginResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
                findViewById(R.id.btn_sign_in).setEnabled(false);
                findViewById(R.id.btn_create_account).setEnabled(false);
            } else if (result instanceof ApiResult.Success) {
                navigateToMain();
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                findViewById(R.id.btn_sign_in).setEnabled(true);
                findViewById(R.id.btn_create_account).setEnabled(true);
                tvPasswordError.setText(error.getMessage());
                tvPasswordError.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Attaches click listeners to interactive elements.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void setListeners() {
        findViewById(R.id.btn_sign_in).setOnClickListener(v ->
                viewModel.login(
                        etEmail.getText().toString(),
                        etPassword.getText().toString()
                )
        );

        findViewById(R.id.btn_create_account).setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    /**
     * Displays or hides a field-level validation error message.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param tv    the error {@link TextView} attached to a specific field
     * @param error the error message to display, or {@code null} to hide the view
     */
    private void showFieldError(TextView tv, String error) {
        if (error == null) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setText(error);
            tv.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Navigates to the application's main screen and removes the login screen from
     * the back stack so the user cannot navigate back to it while logged in.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void navigateToMain() {
        // TODO (Module 2): replace with MainActivity once it exists.
        finish();
    }
}
