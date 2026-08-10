package com.cooksync.app.ui.auth;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.data.datasource.local.TokenStore;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.home.HomeActivity;

/**
 * Entry-point Activity presenting the Login screen.
 *
 * <h3>Skeleton / auto-login flow</h3>
 * <ol>
 *   <li>On creation the <em>skeleton</em> is shown immediately (warm Organic palette bones,
 *       pulsing shimmer via {@link BaseActivity#setupSkeleton}) while the Activity checks
 *       whether a previous session exists on device.</li>
 *   <li>If a stored access token exists, {@link LoginViewModel#validateExistingToken()} is
 *       called — a silent server round-trip. On success the user is routed to the main
 *       screen without ever seeing the login form.</li>
 *   <li>If no session is found, or if validation fails, the skeleton fades out and the real
 *       form slides in.</li>
 * </ol>
 *
 * <p>The View layer contains <em>zero</em> validation or business logic; all of that lives
 * in {@link LoginViewModel}.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class LoginActivity extends BaseActivity {

    private LoginViewModel viewModel;

    private View formContainer;
    private EditText etEmail;
    private EditText etPassword;
    private TextView tvEmailError;
    private TextView tvPasswordError;
    private ProgressBar progress;

    /**
     * Inflates the layout, starts the skeleton shimmer, and kicks off the auto-login
     * token check if a session is present on device.
     *
     * Complexity:
     * Time: O(n) — n is the number of skeleton bone views found by {@link BaseActivity}
     * Space: O(n)
     *
     * @param savedInstanceState saved instance state bundle (may be {@code null})
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(LoginViewModel.class);

        bindViews();
        setupSkeleton(R.id.skeleton_container);
        observeViewModel();
        setListeners();

        if (TokenStore.hasSession()) {
            // A session exists — silently validate it. The skeleton stays up until the
            // result arrives.
            viewModel.validateExistingToken();
        } else {
            // No session — skip straight to showing the form.
            transitionToForm();
        }
    }

    /**
     * Binds all view references from the inflated layout.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void bindViews() {
        formContainer = findViewById(R.id.form_container);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        tvEmailError = findViewById(R.id.tv_email_error);
        tvPasswordError = findViewById(R.id.tv_password_error);
        progress = findViewById(R.id.progress);
    }

    /**
     * Subscribes to all LiveData streams exposed by {@link LoginViewModel}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void observeViewModel() {
        // ── Silent token validation result (skeleton → navigate or form) ────────
        viewModel.getValidateResult().observe(this, result -> {
            if (!(result instanceof ApiResult.Loading)) {
                if (result instanceof ApiResult.Success) {
                    navigateToMain();
                } else {
                    // Validation failed or network error → show the login form
                    transitionToForm();
                }
            }
        });

        // ── Explicit login result ────────────────────────────────────────────────
        viewModel.getLoginResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
                setFormButtonsEnabled(false);
            } else if (result instanceof ApiResult.Success) {
                navigateToMain();
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                setFormButtonsEnabled(true);
                showFieldError(tvPasswordError, error.getMessage());
            }
        });

        // ── Per-field validation errors ──────────────────────────────────────────
        viewModel.getEmailError().observe(this, e -> showFieldError(tvEmailError, e));
        viewModel.getPasswordError().observe(this, e -> showFieldError(tvPasswordError, e));
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
                viewModel.login(etEmail.getText().toString(), etPassword.getText().toString())
        );
        findViewById(R.id.btn_create_account).setOnClickListener(v ->
                Navigator.start(this, RegisterActivity.class)
        );
        findViewById(R.id.tv_forgot_password).setOnClickListener(v ->
                Navigator.start(this, ForgotPasswordActivity.class)
        );
    }

    /**
     * Hides the skeleton, stops the shimmer animator, and reveals the real form.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void transitionToForm() {
        showSkeleton(false, formContainer);
    }

    /**
     * Enables or disables both form-submission buttons during an in-flight call.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param enabled {@code true} to re-enable, {@code false} to disable
     */
    private void setFormButtonsEnabled(boolean enabled) {
        findViewById(R.id.btn_sign_in).setEnabled(enabled);
        findViewById(R.id.btn_create_account).setEnabled(enabled);
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
        Intent extras = new Intent();
        extras.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Navigator.start(this, HomeActivity.class, extras);
        Navigator.finish(this);
    }
}
