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

/**
 * Activity presenting the "Create account" registration screen. Delegates all field
 * validation and the network call to {@link RegisterViewModel}; the View layer contains
 * no business logic whatsoever.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class RegisterActivity extends AppCompatActivity {

    private RegisterViewModel viewModel;

    private EditText etFirstName;
    private EditText etLastName;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etRepeatPassword;

    private TextView tvFirstNameError;
    private TextView tvLastNameError;
    private TextView tvEmailError;
    private TextView tvPasswordError;
    private TextView tvRepeatPasswordError;

    private ProgressBar progress;

    /**
     * Inflates the registration layout, initialises the ViewModel, and attaches all
     * observers and click listeners.
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
        setContentView(R.layout.activity_register);

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);

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
        etFirstName       = findViewById(R.id.et_first_name);
        etLastName        = findViewById(R.id.et_last_name);
        etEmail           = findViewById(R.id.et_email);
        etPassword        = findViewById(R.id.et_password);
        etRepeatPassword  = findViewById(R.id.et_repeat_password);

        tvFirstNameError      = findViewById(R.id.tv_first_name_error);
        tvLastNameError       = findViewById(R.id.tv_last_name_error);
        tvEmailError          = findViewById(R.id.tv_email_error);
        tvPasswordError       = findViewById(R.id.tv_password_error);
        tvRepeatPasswordError = findViewById(R.id.tv_repeat_password_error);

        progress = findViewById(R.id.progress);
    }

    /**
     * Subscribes to all LiveData streams exposed by {@link RegisterViewModel}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void observeViewModel() {
        viewModel.getFirstNameError().observe(this,  e -> showFieldError(tvFirstNameError, e));
        viewModel.getLastNameError().observe(this,   e -> showFieldError(tvLastNameError, e));
        viewModel.getEmailError().observe(this,      e -> showFieldError(tvEmailError, e));
        viewModel.getPasswordError().observe(this,   e -> showFieldError(tvPasswordError, e));
        viewModel.getRepeatPassError().observe(this, e -> showFieldError(tvRepeatPasswordError, e));

        viewModel.getRegisterResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
                setButtonsEnabled(false);
            } else if (result instanceof ApiResult.Success) {
                navigateToMain();
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                setButtonsEnabled(true);
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
        findViewById(R.id.btn_create_account).setOnClickListener(v ->
                viewModel.register(
                        etFirstName.getText().toString(),
                        etLastName.getText().toString(),
                        etEmail.getText().toString(),
                        etPassword.getText().toString(),
                        etRepeatPassword.getText().toString()
                )
        );

        findViewById(R.id.btn_have_account).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    /**
     * Enables or disables both form-submission buttons simultaneously to prevent
     * double-tap during an in-flight network call.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param enabled {@code true} to re-enable, {@code false} to disable
     */
    private void setButtonsEnabled(boolean enabled) {
        findViewById(R.id.btn_create_account).setEnabled(enabled);
        findViewById(R.id.btn_have_account).setEnabled(enabled);
    }

    /**
     * Displays or hides a field-level validation error.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param tv    the error {@link TextView} for a specific field
     * @param error the error string, or {@code null} to hide
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
     * Navigates to the application main screen after successful registration. Clears
     * the back stack so the user cannot navigate back to the auth flow.
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
