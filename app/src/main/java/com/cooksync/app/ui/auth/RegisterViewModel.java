package com.cooksync.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.AuthRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.util.InputSanitizer;
import com.cooksync.app.util.InputValidator;
import com.dtos.request.auth.RegisterRequestDTO;
import com.dtos.response.auth.AuthResponse;

/**
 * ViewModel for {@link RegisterActivity}. Validates all five registration fields through
 * {@link InputValidator} (which embeds {@link InputSanitizer} security checks for each
 * field) before delegating to {@link AuthRepository}. Exposes per-field error {@link LiveData}
 * streams so the Activity highlights exactly the invalid input.
 *
 * <p>A submission rate-limit of {@value #SUBMIT_COOLDOWN_MS} ms prevents rapid-fire
 * button presses from triggering duplicate account creation.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class RegisterViewModel extends ViewModel {

    /** Minimum milliseconds between successive registration attempts. */
    private static final long SUBMIT_COOLDOWN_MS = 2000;

    private final AuthRepository authRepository;

    private final MutableLiveData<ApiResult<AuthResponse>> registerResult  = new MutableLiveData<>();
    private final MutableLiveData<String>                   firstNameError  = new MutableLiveData<>();
    private final MutableLiveData<String>                   lastNameError   = new MutableLiveData<>();
    private final MutableLiveData<String>                   emailError      = new MutableLiveData<>();
    private final MutableLiveData<String>                   passwordError   = new MutableLiveData<>();
    private final MutableLiveData<String>                   repeatPassError = new MutableLiveData<>();

    private long lastSubmitTimestamp = 0L;

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

    // ─── Actions ────────────────────────────────────────────────────────────────

    /**
     * Sanitises and validates all five registration fields. If every field passes,
     * the registration call is submitted through the repository. All field errors are
     * posted to their respective {@link LiveData} streams simultaneously so the user
     * sees every problem at once rather than one at a time.
     *
     * <p>Rate-limited to one attempt per {@value #SUBMIT_COOLDOWN_MS} ms.</p>
     *
     * Complexity:
     * Time: O(n) where n is the combined length of all field values
     * Space: O(1)
     *
     * @param rawFirstName      raw text from the first-name {@code EditText}
     * @param rawLastName       raw text from the last-name {@code EditText}
     * @param rawEmail          raw text from the email {@code EditText}
     * @param rawPassword       raw text from the password {@code EditText}
     * @param rawRepeatPassword raw text from the repeat-password {@code EditText}
     */
    public void register(String rawFirstName, String rawLastName,
                         String rawEmail, String rawPassword, String rawRepeatPassword) {
        long now = System.currentTimeMillis();
        if (now - lastSubmitTimestamp < SUBMIT_COOLDOWN_MS) {
            return;
        }
        lastSubmitTimestamp = now;

        // ── Sanitise (trim) ─────────────────────────────────────────────────────
        String firstName = InputSanitizer.trim(rawFirstName);
        String lastName  = InputSanitizer.trim(rawLastName);
        String email     = InputSanitizer.trim(rawEmail);
        // Passwords are NOT trimmed — trailing spaces may be intentional and trimming
        // would silently change the password the user typed.
        String password       = rawPassword  == null ? "" : rawPassword;
        String repeatPassword = rawRepeatPassword == null ? "" : rawRepeatPassword;

        // ── Validate all fields simultaneously ──────────────────────────────────
        InputValidator.ValidationResult fnResult  = InputValidator.validateName(firstName, "First name");
        InputValidator.ValidationResult lnResult  = InputValidator.validateName(lastName,  "Last name");
        InputValidator.ValidationResult emResult  = InputValidator.validateEmail(email);
        InputValidator.ValidationResult pwResult  = InputValidator.validateNewPassword(password);
        InputValidator.ValidationResult rpResult  = InputValidator.validatePasswordsMatch(password, repeatPassword);

        firstNameError.setValue(fnResult.errorMessage);
        lastNameError.setValue(lnResult.errorMessage);
        emailError.setValue(emResult.errorMessage);
        passwordError.setValue(pwResult.errorMessage);
        repeatPassError.setValue(rpResult.errorMessage);

        if (!fnResult.isValid || !lnResult.isValid || !emResult.isValid
                || !pwResult.isValid || !rpResult.isValid) {
            return;
        }

        authRepository.register(
                new RegisterRequestDTO(firstName, lastName, email, password),
                registerResult
        );
    }

    // ─── Observable state ────────────────────────────────────────────────────────

    /** @return observable registration result (Loading → Success/Error) */
    public LiveData<ApiResult<AuthResponse>> getRegisterResult() { return registerResult; }
    /** @return observable first-name error, {@code null} when valid */
    public LiveData<String> getFirstNameError()  { return firstNameError;  }
    /** @return observable last-name error, {@code null} when valid */
    public LiveData<String> getLastNameError()   { return lastNameError;   }
    /** @return observable email error, {@code null} when valid */
    public LiveData<String> getEmailError()      { return emailError;      }
    /** @return observable password policy error, {@code null} when valid */
    public LiveData<String> getPasswordError()   { return passwordError;   }
    /** @return observable repeat-password mismatch error, {@code null} when valid */
    public LiveData<String> getRepeatPassError() { return repeatPassError; }
}
