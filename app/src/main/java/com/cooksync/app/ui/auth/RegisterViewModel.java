package com.cooksync.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.AuthRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.dtos.request.auth.RegisterRequestDTO;
import com.dtos.response.auth.AuthResponse;

import java.util.regex.Pattern;

/**
 * ViewModel for {@link RegisterActivity}. Validates all registration fields client-side
 * before submitting to {@link AuthRepository}, and exposes per-field error observables so
 * the Activity can highlight exactly which input is invalid without any business logic in
 * the View layer.
 *
 * <p>Password policy replicates the server-side {@code @Pattern} constraint on
 * {@code RegisterRequestDTO}: at least one uppercase letter, one lowercase letter, one digit,
 * and one special character.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class RegisterViewModel extends ViewModel {

    /**
     * Mirrors the server-side password pattern constraint on {@code RegisterRequestDTO}.
     * Requires at minimum: one uppercase, one lowercase, one digit, one special character,
     * and a total length of at least 6 characters.
     */
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{6,}$");

    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 50;

    private final AuthRepository authRepository;

    private final MutableLiveData<ApiResult<AuthResponse>> registerResult = new MutableLiveData<>();
    private final MutableLiveData<String> firstNameError  = new MutableLiveData<>();
    private final MutableLiveData<String> lastNameError   = new MutableLiveData<>();
    private final MutableLiveData<String> emailError      = new MutableLiveData<>();
    private final MutableLiveData<String> passwordError   = new MutableLiveData<>();
    private final MutableLiveData<String> repeatPassError = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with a concrete {@link AuthRepositoryImpl}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public RegisterViewModel() {
        this.authRepository = new AuthRepositoryImpl();
    }

    /**
     * Validates all registration fields. If every field passes, submits the registration
     * request through the repository. Field errors are posted to their respective
     * {@link LiveData} streams and set to {@code null} when the field is valid.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param firstName      raw text from the first-name field
     * @param lastName       raw text from the last-name field
     * @param email          raw text from the email field
     * @param password       raw text from the password field
     * @param repeatPassword raw text from the repeat-password field
     */
    public void register(String firstName, String lastName,
                         String email, String password, String repeatPassword) {
        boolean valid = true;

        // — First name —
        if (firstName == null || firstName.trim().length() < NAME_MIN
                || firstName.trim().length() > NAME_MAX) {
            firstNameError.setValue("First name must be between " + NAME_MIN + " and " + NAME_MAX + " characters");
            valid = false;
        } else {
            firstNameError.setValue(null);
        }

        // — Last name —
        if (lastName == null || lastName.trim().length() < NAME_MIN
                || lastName.trim().length() > NAME_MAX) {
            lastNameError.setValue("Last name must be between " + NAME_MIN + " and " + NAME_MAX + " characters");
            valid = false;
        } else {
            lastNameError.setValue(null);
        }

        // — Email —
        if (email == null || email.isBlank()) {
            emailError.setValue("Email cannot be blank");
            valid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            emailError.setValue("Please enter a valid email address");
            valid = false;
        } else {
            emailError.setValue(null);
        }

        // — Password —
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            passwordError.setValue(
                    "Password must be at least 6 characters and contain uppercase, lowercase, a digit, and a special character");
            valid = false;
        } else {
            passwordError.setValue(null);
        }

        // — Repeat password (only meaningful once password itself is valid) —
        if (password != null && !password.equals(repeatPassword)) {
            repeatPassError.setValue("Passwords do not match");
            valid = false;
        } else {
            repeatPassError.setValue(null);
        }

        if (!valid) {
            return;
        }

        authRepository.register(
                new RegisterRequestDTO(firstName.trim(), lastName.trim(), email.trim(), password),
                registerResult
        );
    }

    /** @return observable registration result */
    public LiveData<ApiResult<AuthResponse>> getRegisterResult() { return registerResult; }
    /** @return observable first-name validation error (null when valid) */
    public LiveData<String> getFirstNameError()  { return firstNameError;  }
    /** @return observable last-name validation error (null when valid) */
    public LiveData<String> getLastNameError()   { return lastNameError;   }
    /** @return observable email validation error (null when valid) */
    public LiveData<String> getEmailError()      { return emailError;      }
    /** @return observable password validation error (null when valid) */
    public LiveData<String> getPasswordError()   { return passwordError;   }
    /** @return observable repeat-password validation error (null when valid) */
    public LiveData<String> getRepeatPassError() { return repeatPassError; }
}
