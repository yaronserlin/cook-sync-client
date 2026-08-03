package com.cooksync.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.AuthRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.dtos.request.auth.LoginRequestDTO;
import com.dtos.response.auth.AuthResponse;

/**
 * ViewModel for {@link LoginActivity}. Holds observable state across configuration changes
 * and enforces all client-side field validation before delegating to {@link AuthRepository}.
 *
 * <p>Validation logic replicates the server-side constraints from {@code LoginRequestDTO}
 * without depending on {@code jakarta.validation} (which is unavailable on Android).</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class LoginViewModel extends ViewModel {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AuthRepository authRepository;

    private final MutableLiveData<ApiResult<AuthResponse>> loginResult = new MutableLiveData<>();
    private final MutableLiveData<String> emailError = new MutableLiveData<>();
    private final MutableLiveData<String> passwordError = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with a concrete {@link AuthRepositoryImpl}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public LoginViewModel() {
        this.authRepository = new AuthRepositoryImpl();
    }

    /**
     * Validates fields and, if all pass, posts a {@link ApiResult.Loading} and initiates
     * the login call via the repository.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param email    raw text from the email field
     * @param password raw text from the password field
     */
    public void login(String email, String password) {
        boolean valid = true;

        if (email == null || email.isBlank()) {
            emailError.setValue("Email cannot be blank");
            valid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            emailError.setValue("Please enter a valid email address");
            valid = false;
        } else {
            emailError.setValue(null);
        }

        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            passwordError.setValue("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            valid = false;
        } else {
            passwordError.setValue(null);
        }

        if (!valid) {
            return;
        }

        authRepository.login(new LoginRequestDTO(email.trim(), password), loginResult);
    }

    /**
     * Returns the observable login result, emitting {@link ApiResult.Loading},
     * {@link ApiResult.Success}, or {@link ApiResult.Error}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return observable login result
     */
    public LiveData<ApiResult<AuthResponse>> getLoginResult() {
        return loginResult;
    }

    /**
     * Returns the observable email field validation error, or {@code null} when valid.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return observable email error message
     */
    public LiveData<String> getEmailError() {
        return emailError;
    }

    /**
     * Returns the observable password field validation error, or {@code null} when valid.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return observable password error message
     */
    public LiveData<String> getPasswordError() {
        return passwordError;
    }
}
