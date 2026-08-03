package com.cooksync.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.AuthRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.util.InputSanitizer;
import com.cooksync.app.util.InputValidator;
import com.dtos.request.auth.LoginRequestDTO;
import com.dtos.response.auth.AuthResponse;

/**
 * ViewModel for {@link LoginActivity}. Holds observable state across configuration changes,
 * enforces client-side field validation via {@link InputValidator} (which in turn runs
 * {@link InputSanitizer} security checks), and delegates authenticated network calls to
 * {@link AuthRepository}.
 *
 * <p>A submission rate-limit of {@value #SUBMIT_COOLDOWN_MS} ms prevents rapid-fire
 * button presses from flooding the server with duplicate login requests.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class LoginViewModel extends ViewModel {

    /** Minimum milliseconds between successive login attempts. */
    private static final long SUBMIT_COOLDOWN_MS = 1500;

    private final AuthRepository authRepository;

    private final MutableLiveData<ApiResult<AuthResponse>> loginResult       = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<AuthResponse>> validateResult    = new MutableLiveData<>();
    private final MutableLiveData<String>                   emailError        = new MutableLiveData<>();
    private final MutableLiveData<String>                   passwordError     = new MutableLiveData<>();

    private long lastSubmitTimestamp = 0L;

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

    // ─── Actions ────────────────────────────────────────────────────────────────

    /**
     * Validates both fields (security check + domain rules) and, if all pass, triggers
     * the login call through the repository. Enforces a per-ViewModel rate limit to prevent
     * double-tap submission.
     *
     * Complexity:
     * Time: O(n) where n is the combined length of both field values
     * Space: O(1)
     *
     * @param rawEmail    raw text from the email {@code EditText}
     * @param rawPassword raw text from the password {@code EditText}
     */
    public void login(String rawEmail, String rawPassword) {
        long now = System.currentTimeMillis();
        if (now - lastSubmitTimestamp < SUBMIT_COOLDOWN_MS) {
            return; // rate-limit: silently ignore rapid re-taps
        }
        lastSubmitTimestamp = now;

        // ── Sanitised values (trimmed) ──────────────────────────────────────────
        String email    = InputSanitizer.trim(rawEmail);
        String password = InputSanitizer.trim(rawPassword);

        // ── Validate ────────────────────────────────────────────────────────────
        InputValidator.ValidationResult emailRes    = InputValidator.validateEmail(email);
        InputValidator.ValidationResult passwordRes = InputValidator.validateLoginPassword(password);

        emailError.setValue(emailRes.errorMessage);
        passwordError.setValue(passwordRes.errorMessage);

        if (!emailRes.isValid || !passwordRes.isValid) {
            return;
        }

        authRepository.login(new LoginRequestDTO(email, password), loginResult);
    }

    /**
     * Verifies the stored access token with the server. Used by {@link LoginActivity}
     * on startup to silently auto-login a user whose previous session is still valid.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void validateExistingToken() {
        authRepository.validateToken(validateResult);
    }

    // ─── Observable state ────────────────────────────────────────────────────────

    /** @return observable login result (Loading → Success/Error) */
    public LiveData<ApiResult<AuthResponse>> getLoginResult()    { return loginResult; }

    /**
     * @return observable token-validation result, used to decide whether to skip the
     *         login screen on startup
     */
    public LiveData<ApiResult<AuthResponse>> getValidateResult()  { return validateResult; }

    /** @return observable email field error, {@code null} when the field is valid */
    public LiveData<String> getEmailError()    { return emailError; }

    /** @return observable password field error, {@code null} when the field is valid */
    public LiveData<String> getPasswordError() { return passwordError; }
}
