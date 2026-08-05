package com.cooksync.app.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;

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
public class ForgotPasswordActivity extends AppCompatActivity {

    private ForgotPasswordViewModel viewModel;

    private View requestStage;
    private View resetStage;
    private EditText etEmail;
    private EditText etToken;
    private EditText etNewPassword;
    private EditText etRepeatPassword;
    private TextView tvEmailError;
    private TextView tvTokenError;
    private TextView tvNewPasswordError;
    private TextView tvRepeatPasswordError;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);

        bindViews();
        observeViewModel();
        setListeners();
    }

    private void bindViews() {
        requestStage = findViewById(R.id.request_stage);
        resetStage   = findViewById(R.id.reset_stage);

        etEmail          = findViewById(R.id.et_email);
        etToken          = findViewById(R.id.et_token);
        etNewPassword    = findViewById(R.id.et_new_password);
        etRepeatPassword = findViewById(R.id.et_repeat_password);

        tvEmailError          = findViewById(R.id.tv_email_error);
        tvTokenError          = findViewById(R.id.tv_token_error);
        tvNewPasswordError    = findViewById(R.id.tv_new_password_error);
        tvRepeatPasswordError = findViewById(R.id.tv_repeat_password_error);

        progress = findViewById(R.id.progress);
    }

    private void observeViewModel() {
        viewModel.getEmailError().observe(this,          e -> showFieldError(tvEmailError, e));
        viewModel.getTokenError().observe(this,          e -> showFieldError(tvTokenError, e));
        viewModel.getNewPasswordError().observe(this,    e -> showFieldError(tvNewPasswordError, e));
        viewModel.getRepeatPasswordError().observe(this, e -> showFieldError(tvRepeatPasswordError, e));

        viewModel.getForgotPasswordResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
            } else if (result instanceof ApiResult.Success) {
                progress.setVisibility(View.GONE);
                requestStage.setVisibility(View.GONE);
                resetStage.setVisibility(View.VISIBLE);
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getResetPasswordResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
            } else if (result instanceof ApiResult.Success) {
                progress.setVisibility(View.GONE);
                Toast.makeText(this, R.string.reset_password_success, Toast.LENGTH_LONG).show();
                finish();
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_send_reset_link).setOnClickListener(v ->
                viewModel.requestReset(etEmail.getText().toString())
        );
        findViewById(R.id.btn_reset_password).setOnClickListener(v ->
                viewModel.resetPassword(
                        etToken.getText().toString(),
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
