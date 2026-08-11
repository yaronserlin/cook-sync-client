package com.cooksync.app.ui.auth;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.home.HomeActivity;
import com.cooksync.app.ui.settings.LegalLinkSpanner;
import com.google.android.material.checkbox.MaterialCheckBox;

/**
 * Activity presenting the "Create account" registration screen.
 *
 * <h3>Skeleton behaviour</h3>
 * The skeleton is displayed for a brief {@value #SKELETON_DELAY_MS} ms on every entry to
 * give the screen a polished, deliberate loading feel consistent with the rest of the app.
 * This also covers the case where fonts or drawables are still being fetched from disk on
 * the first run.
 *
 * <p>All field validation and the network call are delegated to {@link RegisterViewModel};
 * the View layer contains no business logic.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class RegisterActivity extends BaseActivity {

    /**
     * Duration in milliseconds the skeleton is shown before the form is revealed.
     * Gives the impression of purposeful loading even on fast devices.
     */
    private static final long SKELETON_DELAY_MS = 400L;

    private RegisterViewModel viewModel;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private View formContainer;
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
    private TextView tvTermsError;
    private MaterialCheckBox cbTerms;
    private MaterialCheckBox cbMarketing;
    private ProgressBar progress;

    /**
     * Inflates the registration layout, starts the skeleton shimmer, and schedules a
     * delayed transition to the real form.
     *
     * Complexity:
     * Time: O(n) — n is the number of skeleton bone views
     * Space: O(n)
     *
     * @param savedInstanceState saved instance state bundle (may be {@code null})
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(RegisterViewModel.class);

        bindViews();
        setupSkeleton(R.id.skeleton_container);
        setupTermsLink();
        observeViewModel();
        setListeners();

        // Reveal the form after a brief skeleton moment.
        uiHandler.postDelayed(this::transitionToForm, SKELETON_DELAY_MS);
    }

    /**
     * Cancels the pending skeleton-transition runnable so it never fires after the
     * Activity has left the foreground.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    @Override
    protected void onStop() {
        super.onStop();
        uiHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Binds all view references.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void bindViews() {
        formContainer = findViewById(R.id.form_container);

        etFirstName = findViewById(R.id.et_first_name);
        etLastName = findViewById(R.id.et_last_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etRepeatPassword = findViewById(R.id.et_repeat_password);

        tvFirstNameError = findViewById(R.id.tv_first_name_error);
        tvLastNameError = findViewById(R.id.tv_last_name_error);
        tvEmailError = findViewById(R.id.tv_email_error);
        tvPasswordError = findViewById(R.id.tv_password_error);
        tvRepeatPasswordError = findViewById(R.id.tv_repeat_password_error);
        tvTermsError = findViewById(R.id.tv_terms_error);

        cbTerms = findViewById(R.id.cb_terms);
        cbMarketing = findViewById(R.id.cb_marketing);

        progress = findViewById(R.id.progress);
    }

    /**
     * Turns the "Terms of Use" and "Privacy Policy" mentions inside {@link #cbTerms}'s label into
     * tappable links, so the user can actually read what they're agreeing to before checking the
     * box. See {@link LegalLinkSpanner} for the shared span/click behavior.
     *
     * Complexity:
     * Time: O(n) — n is the length of the label text
     * Space: O(n)
     */
    private void setupTermsLink() {
        LegalLinkSpanner.apply(cbTerms, this, R.string.label_terms_accepted);
    }

    /**
     * Subscribes to all LiveData streams from {@link RegisterViewModel}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void observeViewModel() {
        viewModel.getFirstNameError().observe(this, e -> showFieldError(tvFirstNameError, e));
        viewModel.getLastNameError().observe(this, e -> showFieldError(tvLastNameError, e));
        viewModel.getEmailError().observe(this, e -> showFieldError(tvEmailError, e));
        viewModel.getPasswordError().observe(this, e -> showFieldError(tvPasswordError, e));
        viewModel.getRepeatPassError().observe(this, e -> showFieldError(tvRepeatPasswordError, e));
        viewModel.getTermsError().observe(this, e -> showFieldError(tvTermsError, e));

        viewModel.getRegisterResult().observe(this, result -> {
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
    }

    /**
     * Attaches click listeners.
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
                        etRepeatPassword.getText().toString(),
                        cbTerms.isChecked(),
                        cbMarketing.isChecked()
                )
        );
        findViewById(R.id.btn_have_account).setOnClickListener(v -> {
            Navigator.start(this, LoginActivity.class);
            Navigator.finish(this);
        });
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    /**
     * Hides the skeleton, stops the shimmer animator, and reveals the registration form.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void transitionToForm() {
        showSkeleton(false, formContainer);
    }

    /**
     * Enables or disables both submission buttons simultaneously to prevent double-tap.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param enabled {@code true} to re-enable, {@code false} to disable
     */
    private void setFormButtonsEnabled(boolean enabled) {
        findViewById(R.id.btn_create_account).setEnabled(enabled);
        findViewById(R.id.btn_have_account).setEnabled(enabled);
    }

    /**
     * Displays or hides a per-field validation error.
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
     * Navigates to the main screen after a successful registration.
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
