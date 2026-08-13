package com.cooksync.app.ui.auth;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.util.InputSanitizer;
import com.cooksync.app.util.InputValidator;
import com.dtos.request.auth.ResendRegistrationOtpRequestDTO;
import com.dtos.request.auth.VerifyRegistrationOtpRequestDTO;
import com.dtos.response.auth.AuthResponse;
import com.dtos.response.auth.PendingRegistrationResponse;

/**
 * ViewModel for {@link VerifyOtpActivity}. Validates the submitted OTP code through
 * {@link InputValidator} before delegating to {@link AuthRepository}, and drives a
 * client-side resend cooldown so the resend button is disabled for
 * {@value #RESEND_COOLDOWN_SECONDS} seconds after the screen opens or a code is resent.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 13/08/2026
 */
public class VerifyOtpViewModel extends BaseViewModel {

    /** Seconds the resend button stays disabled after the OTP screen opens or a resend fires. */
    private static final int RESEND_COOLDOWN_SECONDS = 30;

    private final AuthRepository authRepository;

    private String email;

    private final MutableLiveData<ApiResult<AuthResponse>> verifyResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<PendingRegistrationResponse>> resendResult = new MutableLiveData<>();
    private final MutableLiveData<String> otpCodeError = new MutableLiveData<>();
    private final MutableLiveData<Integer> resendCooldownSeconds = new MutableLiveData<>(0);

    private CountDownTimer resendCountDownTimer;

    /**
     * Constructs the ViewModel with the given {@link AuthRepository}, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param authRepository the repository used for the OTP verify/resend calls
     */
    public VerifyOtpViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    /**
     * One-time initialization with the email the OTP was sent to, and starts the initial resend
     * cooldown. Safe to call on every {@code onCreate}: a no-op after the first call, so a
     * config-change-driven ViewModel restore does not restart the cooldown from scratch.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param email the email address the pending registration and OTP code belong to
     */
    public void init(String email) {
        if (this.email != null) {
            return;
        }
        this.email = email;
        startResendCooldown();
    }

    /**
     * Sanitises and validates the submitted OTP code. If valid, submits it through the
     * repository to complete registration.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param rawCode raw text from the OTP code {@code EditText}
     */
    public void verify(String rawCode) {
        String code = InputSanitizer.trim(rawCode);
        InputValidator.ValidationResult result = InputValidator.validateOtpCode(code);
        otpCodeError.setValue(result.errorMessage);
        if (!result.isValid) {
            return;
        }
        authRepository.verifyRegistrationOtp(new VerifyRegistrationOtpRequestDTO(email, code), verifyResult);
    }

    /**
     * Requests a fresh OTP code, restarting the resend cooldown on success. No-op while the
     * cooldown from a previous send is still running.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void resend() {
        Integer cooldown = resendCooldownSeconds.getValue();
        if (cooldown != null && cooldown > 0) {
            return;
        }
        observeOnce(resendResult, result -> {
            if (result instanceof ApiResult.Success) {
                startResendCooldown();
            }
        });
        authRepository.resendRegistrationOtp(new ResendRegistrationOtpRequestDTO(email), resendResult);
    }

    /**
     * (Re)starts the resend cooldown countdown from {@value #RESEND_COOLDOWN_SECONDS} seconds.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void startResendCooldown() {
        cancelResendCountDown();
        resendCooldownSeconds.setValue(RESEND_COOLDOWN_SECONDS);
        resendCountDownTimer = new CountDownTimer(RESEND_COOLDOWN_SECONDS * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                resendCooldownSeconds.setValue((int) Math.ceil(millisUntilFinished / 1000.0));
            }

            @Override
            public void onFinish() {
                resendCooldownSeconds.setValue(0);
            }
        }.start();
    }

    private void cancelResendCountDown() {
        if (resendCountDownTimer != null) {
            resendCountDownTimer.cancel();
            resendCountDownTimer = null;
        }
    }

    /** @return observable OTP verification result (Loading → Success/Error) */
    public LiveData<ApiResult<AuthResponse>> getVerifyResult() { return verifyResult; }
    /** @return observable resend-OTP result (Loading → Success/Error) */
    public LiveData<ApiResult<PendingRegistrationResponse>> getResendResult() { return resendResult; }
    /** @return observable OTP code field error, {@code null} when valid */
    public LiveData<String> getOtpCodeError() { return otpCodeError; }
    /** @return observable seconds remaining before resend is allowed again, 0 when allowed */
    public LiveData<Integer> getResendCooldownSeconds() { return resendCooldownSeconds; }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelResendCountDown();
    }
}
