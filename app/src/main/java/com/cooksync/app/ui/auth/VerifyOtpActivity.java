package com.cooksync.app.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.home.HomeActivity;

/**
 * Activity presenting the registration email-OTP verification screen: the second step of the
 * registration flow, reached from {@link RegisterActivity} after the form is submitted. The
 * account is only created, and a session only started, once the code entered here is confirmed
 * by the server — if the user abandons this screen or exhausts their attempts, nothing was ever
 * persisted server-side beyond the pending registration's own expiry.
 *
 * <p>All validation and network calls are delegated to {@link VerifyOtpViewModel}; the View
 * layer contains no business logic.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 13/08/2026
 */
public class VerifyOtpActivity extends BaseActivity {

    /** Intent extra carrying the email address the OTP code was sent to. */
    public static final String EXTRA_EMAIL = "extra_email";

    private VerifyOtpViewModel viewModel;

    private TextView tvSubtitle;
    private EditText etOtpCode;
    private TextView tvOtpCodeError;
    private com.google.android.material.button.MaterialButton btnVerify;
    private com.google.android.material.button.MaterialButton btnResend;
    private ProgressBar progress;

    private String email;

    /**
     * Builds an intent to launch {@link VerifyOtpActivity} for the given pending-registration
     * email address.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context calling context
     * @param email   the email address the OTP code was sent to
     * @return an intent ready to start {@link VerifyOtpActivity}
     */
    @NonNull
    public static Intent newIntent(@NonNull Context context, @NonNull String email) {
        Intent intent = new Intent(context, VerifyOtpActivity.class);
        intent.putExtra(EXTRA_EMAIL, email);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otp);

        email = getIntent().getStringExtra(EXTRA_EMAIL);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(VerifyOtpViewModel.class);
        viewModel.init(email);

        bindViews();
        tvSubtitle.setText(getString(R.string.verify_otp_subtitle, email));
        observeViewModel();
        setListeners();
    }

    private void bindViews() {
        tvSubtitle = findViewById(R.id.tv_subtitle);
        etOtpCode = findViewById(R.id.et_otp_code);
        tvOtpCodeError = findViewById(R.id.tv_otp_code_error);
        btnVerify = findViewById(R.id.btn_verify);
        btnResend = findViewById(R.id.btn_resend);
        progress = findViewById(R.id.progress);
    }

    private void observeViewModel() {
        viewModel.getOtpCodeError().observe(this, e -> showFieldError(tvOtpCodeError, e));

        viewModel.getResendCooldownSeconds().observe(this, seconds -> {
            if (seconds == null || seconds <= 0) {
                btnResend.setEnabled(true);
                btnResend.setText(R.string.action_resend_code);
            } else {
                btnResend.setEnabled(false);
                btnResend.setText(getString(R.string.action_resend_code_countdown, seconds));
            }
        });

        viewModel.getVerifyResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
                setFormEnabled(false);
            } else if (result instanceof ApiResult.Success) {
                navigateToMain();
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                setFormEnabled(true);
                showFieldError(tvOtpCodeError, error.getMessage());
            }
        });

        viewModel.getResendResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                setFormEnabled(false);
            } else if (result instanceof ApiResult.Success) {
                setFormEnabled(true);
                showSuccess(getString(R.string.resend_otp_success), null);
            } else if (result instanceof ApiResult.Error<?> error) {
                setFormEnabled(true);
                showError(error.getMessage(), null);
            }
        });
    }

    private void setListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnVerify.setOnClickListener(v -> viewModel.verify(etOtpCode.getText().toString()));
        btnResend.setOnClickListener(v -> viewModel.resend());
    }

    private void setFormEnabled(boolean enabled) {
        btnVerify.setEnabled(enabled);
        etOtpCode.setEnabled(enabled);
    }

    private void showFieldError(TextView tv, String error) {
        if (error == null) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setText(error);
            tv.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Navigates to the main screen after successful OTP verification, exactly as
     * {@code RegisterActivity} used to do directly on successful registration.
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
