package com.cooksync.app.ui.auth;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.ViewModelFactory;

/**
 * Activity presenting the forgot-password flow: request a reset token by email, then use
 * that token to set a new password. Both steps live on this single screen; the reset stage
 * is revealed only after the request stage succeeds.
 *
 * <p>The View layer contains no validation or business logic; all of that lives in
 * {@link ForgotPasswordViewModel}.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public class ForgotPasswordActivity extends BaseActivity {

    private ForgotPasswordViewModel viewModel;

    private View requestStage;
    private View resetStage;
    private EditText etEmail;
    private EditText etCode;
    private EditText etNewPassword;
    private EditText etRepeatPassword;
    private TextView tvEmailError;
    private TextView tvCodeError;
    private TextView tvNewPasswordError;
    private TextView tvRepeatPasswordError;
    private com.google.android.material.button.MaterialButton btnResendCode;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(ForgotPasswordViewModel.class);

        bindViews();
        observeViewModel();
        setListeners();
    }

    private void bindViews() {
        requestStage = findViewById(R.id.request_stage);
        resetStage = findViewById(R.id.reset_stage);

        etEmail = findViewById(R.id.et_email);
        etCode = findViewById(R.id.et_code);
        etNewPassword = findViewById(R.id.et_new_password);
        etRepeatPassword = findViewById(R.id.et_repeat_password);

        tvEmailError = findViewById(R.id.tv_email_error);
        tvCodeError = findViewById(R.id.tv_code_error);
        tvNewPasswordError = findViewById(R.id.tv_new_password_error);
        tvRepeatPasswordError = findViewById(R.id.tv_repeat_password_error);
        btnResendCode = findViewById(R.id.btn_resend_code);

        progress = findViewById(R.id.progress);
    }

    private void observeViewModel() {
        viewModel.getEmailError().observe(this, e -> showFieldError(tvEmailError, e));
        viewModel.getCodeError().observe(this, e -> showFieldError(tvCodeError, e));
        viewModel.getNewPasswordError().observe(this, e -> showFieldError(tvNewPasswordError, e));
        viewModel.getRepeatPasswordError().observe(this, e -> showFieldError(tvRepeatPasswordError, e));

        viewModel.getResendCooldownSeconds().observe(this, seconds -> {
            if (seconds == null || seconds <= 0) {
                btnResendCode.setEnabled(true);
                btnResendCode.setText(R.string.action_resend_code);
            } else {
                btnResendCode.setEnabled(false);
                btnResendCode.setText(getString(R.string.action_resend_code_countdown, seconds));
            }
        });

        viewModel.getForgotPasswordResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
            } else if (result instanceof ApiResult.Success) {
                progress.setVisibility(View.GONE);
                requestStage.setVisibility(View.GONE);
                resetStage.setVisibility(View.VISIBLE);
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                showError(error.getMessage(), null);
            }
        });

        viewModel.getResendCodeResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                showSuccess(getString(R.string.resend_otp_success), null);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), null);
            }
        });

        viewModel.getResetPasswordResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
            } else if (result instanceof ApiResult.Success) {
                progress.setVisibility(View.GONE);
                showSuccess(getString(R.string.reset_password_success), null);
                finish();
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                showError(error.getMessage(), null);
            }
        });
    }

    private void setListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_send_reset_link).setOnClickListener(v ->
                viewModel.requestReset(etEmail.getText().toString())
        );
        btnResendCode.setOnClickListener(v -> viewModel.resendCode());
        findViewById(R.id.btn_reset_password).setOnClickListener(v ->
                viewModel.resetPassword(
                        etCode.getText().toString(),
                        etNewPassword.getText().toString(),
                        etRepeatPassword.getText().toString()
                )
        );
    }

    private void showFieldError(TextView tv, String error) {
        if (error == null) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setText(error);
            tv.setVisibility(View.VISIBLE);
        }
    }
}
