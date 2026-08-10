package com.cooksync.app.ui.auth;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.util.InputSanitizer;
import com.cooksync.app.util.InputValidator;
import com.dtos.request.auth.ForgotPasswordRequestDTO;
import com.dtos.request.auth.ResetPasswordRequestDTO;

/**
 * ViewModel for {@link ForgotPasswordActivity}. Drives both stages of the forgot-password
 * flow: requesting a reset token by email, then consuming that token to set a new password.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public class ForgotPasswordViewModel extends BaseViewModel {

    private final AuthRepository authRepository;

    private final MutableLiveData<ApiResult<Void>> forgotPasswordResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> resetPasswordResult = new MutableLiveData<>();
    private final MutableLiveData<String> emailError = new MutableLiveData<>();
    private final MutableLiveData<String> tokenError = new MutableLiveData<>();
    private final MutableLiveData<String> newPasswordError = new MutableLiveData<>();
    private final MutableLiveData<String> repeatPasswordError = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with the given {@link AuthRepository}, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param authRepository the repository used for the forgot/reset password calls
     */
    public ForgotPasswordViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    /**
     * Validates and submits the forgot-password email request.
     *
     * Complexity:
     * Time: O(n) where n is the length of the email string
     * Space: O(1)
     *
     * @param rawEmail raw text from the email {@code EditText}
     */
    public void requestReset(String rawEmail) {
        String email = InputSanitizer.trim(rawEmail);
        InputValidator.ValidationResult emResult = InputValidator.validateEmail(email);
        emailError.setValue(emResult.errorMessage);
        if (!emResult.isValid) {
            return;
        }
        authRepository.forgotPassword(new ForgotPasswordRequestDTO(email), forgotPasswordResult);
    }

    /**
     * Validates and submits the new password using the reset token.
     *
     * Complexity:
     * Time: O(n) where n is the combined length of the password fields
     * Space: O(1)
     *
     * @param rawToken           raw text from the reset-token {@code EditText}
     * @param rawNewPassword     raw text from the new-password {@code EditText}
     * @param rawRepeatPassword  raw text from the repeat-password {@code EditText}
     */
    public void resetPassword(String rawToken, String rawNewPassword, String rawRepeatPassword) {
        String token = InputSanitizer.trim(rawToken);
        String newPassword = rawNewPassword == null ? "" : rawNewPassword;
        String repeatPassword = rawRepeatPassword == null ? "" : rawRepeatPassword;

        InputValidator.ValidationResult tokenResult = token.isEmpty()
                ? InputValidator.ValidationResult.invalid("Reset token cannot be blank")
                : InputValidator.ValidationResult.valid();
        InputValidator.ValidationResult pwResult = InputValidator.validateNewPassword(newPassword);
        InputValidator.ValidationResult rpResult = InputValidator.validatePasswordsMatch(newPassword, repeatPassword);

        tokenError.setValue(tokenResult.errorMessage);
        newPasswordError.setValue(pwResult.errorMessage);
        repeatPasswordError.setValue(rpResult.errorMessage);

        if (!tokenResult.isValid || !pwResult.isValid || !rpResult.isValid) {
            return;
        }

        authRepository.resetPassword(new ResetPasswordRequestDTO(token, newPassword), resetPasswordResult);
    }

    /** @return observable forgot-password request result (Loading → Success/Error) */
    public LiveData<ApiResult<Void>> getForgotPasswordResult() { return forgotPasswordResult; }
    /** @return observable reset-password result (Loading → Success/Error) */
    public LiveData<ApiResult<Void>> getResetPasswordResult() { return resetPasswordResult; }
    /** @return observable email error, {@code null} when valid */
    public LiveData<String> getEmailError() { return emailError; }
    /** @return observable reset-token error, {@code null} when valid */
    public LiveData<String> getTokenError() { return tokenError; }
    /** @return observable new-password policy error, {@code null} when valid */
    public LiveData<String> getNewPasswordError() { return newPasswordError; }
    /** @return observable repeat-password mismatch error, {@code null} when valid */
    public LiveData<String> getRepeatPasswordError() { return repeatPasswordError; }
}
