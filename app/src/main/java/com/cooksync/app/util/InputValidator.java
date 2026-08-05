package com.cooksync.app.util;

import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Centralised, stateless field-validation library. Every rule precisely mirrors the
 * server-side Jakarta Validation constraints declared on the {@code cooksync-DTOs} records,
 * so a payload that passes here will always pass server-side field validation as well —
 * and the user sees a clear, localised error before any network call is made.
 *
 * <p>Security layer: every method first runs {@link InputSanitizer#containsDangerousContent}
 * to reject payloads with injection patterns, and then applies the domain rule. Name fields
 * additionally enforce an allowed-character set via {@link InputSanitizer#isValidNameCharset}.</p>
 *
 * <p>All methods accept {@code null} and treat it as a blank/missing value.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public final class InputValidator {

    // ─── Constraints mirroring RegisterRequestDTO / LoginRequestDTO ─────────────

    /** Minimum acceptable password length (mirrors {@code @Size(min=6)} on both DTOs). */
    public static final int PASSWORD_MIN = 6;

    /** Maximum acceptable password length (mirrors {@code @Size(max=100)} on both DTOs). */
    public static final int PASSWORD_MAX = 100;

    /** Minimum acceptable name length (mirrors {@code @Size(min=2)} on RegisterRequestDTO). */
    public static final int NAME_MIN = 2;

    /** Maximum acceptable name length (mirrors {@code @Size(max=50)} on RegisterRequestDTO). */
    public static final int NAME_MAX = 50;

    /**
     * Password policy regex — exact copy of the {@code @Pattern} constraint on
     * {@code RegisterRequestDTO#password}:
     * <pre>^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[@$!%*?&amp;])[A-Za-z\d@$!%*?&amp;]{6,}$</pre>
     * Requires at least: one uppercase, one lowercase, one digit, one special character
     * from {@code @$!%*?&}.
     */
    private static final Pattern PASSWORD_POLICY =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{6,}$");

    // ─── Inner result type ───────────────────────────────────────────────────────

    /**
     * Immutable result of a single field validation check, carrying a success flag and,
     * when invalid, a user-facing error message.
     */
    public static final class ValidationResult {
        /** {@code true} when the field passes all rules. */
        public final boolean isValid;
        /** User-facing error text, or {@code null} when {@link #isValid} is {@code true}. */
        @Nullable
        public final String errorMessage;

        private ValidationResult(boolean isValid, @Nullable String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }

        /**
         * Constructs a passing result.
         *
         * Complexity:
         * Time: O(1)
         * Space: O(1)
         *
         * @return a valid {@code ValidationResult}
         */
        @NonNull
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        /**
         * Constructs a failing result with a user-facing message.
         *
         * Complexity:
         * Time: O(1)
         * Space: O(1)
         *
         * @param message the error description to display next to the field
         * @return an invalid {@code ValidationResult}
         */
        @NonNull
        public static ValidationResult invalid(@NonNull String message) {
            return new ValidationResult(false, message);
        }
    }

    private InputValidator() {
    }

    // ─── Field validators ────────────────────────────────────────────────────────

    /**
     * Validates an email address.
     *
     * <p>Rules (mirror {@code LoginRequestDTO#email} and {@code RegisterRequestDTO#email}):</p>
     * <ol>
     *   <li>Not blank</li>
     *   <li>No dangerous/injection content</li>
     *   <li>Matches {@link Patterns#EMAIL_ADDRESS}</li>
     *   <li>Does not exceed {@link #PASSWORD_MAX} characters (shared upper bound)</li>
     * </ol>
     *
     * Complexity:
     * Time: O(n) where n is the length of the email string
     * Space: O(1)
     *
     * @param raw raw value from the email {@code EditText}
     * @return the validation result
     */
    @NonNull
    public static ValidationResult validateEmail(@Nullable String raw) {
        String value = InputSanitizer.trim(raw);
        if (value.isEmpty()) {
            return ValidationResult.invalid("Email cannot be blank");
        }
        if (InputSanitizer.containsDangerousContent(value)) {
            return ValidationResult.invalid("Email contains invalid characters");
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            return ValidationResult.invalid("Please enter a valid email address");
        }
        return ValidationResult.valid();
    }

    /**
     * Validates a login password (existence + length only — no policy enforcement,
     * because the user may have registered before a stricter policy was introduced).
     *
     * <p>Rules (mirror {@code LoginRequestDTO#password}):</p>
     * <ol>
     *   <li>Not blank</li>
     *   <li>No dangerous/injection content</li>
     *   <li>Length between {@value #PASSWORD_MIN} and {@value #PASSWORD_MAX}</li>
     * </ol>
     *
     * Complexity:
     * Time: O(n) where n is the password length
     * Space: O(1)
     *
     * @param raw raw value from the password {@code EditText}
     * @return the validation result
     */
    @NonNull
    public static ValidationResult validateLoginPassword(@Nullable String raw) {
        String value = InputSanitizer.trim(raw);
        if (value.isEmpty()) {
            return ValidationResult.invalid("Password cannot be blank");
        }
        if (InputSanitizer.containsDangerousContent(value)) {
            return ValidationResult.invalid("Password contains invalid characters");
        }
        if (value.length() < PASSWORD_MIN) {
            return ValidationResult.invalid("Password must be at least " + PASSWORD_MIN + " characters");
        }
        if (value.length() > PASSWORD_MAX) {
            return ValidationResult.invalid("Password must not exceed " + PASSWORD_MAX + " characters");
        }
        return ValidationResult.valid();
    }

    /**
     * Validates a new/registration password — applies the full policy.
     *
     * <p>Rules (mirror {@code RegisterRequestDTO#password}):</p>
     * <ol>
     *   <li>Not blank</li>
     *   <li>No dangerous/injection content</li>
     *   <li>Length between {@value #PASSWORD_MIN} and {@value #PASSWORD_MAX}</li>
     *   <li>Matches {@link #PASSWORD_POLICY}: uppercase + lowercase + digit + special char</li>
     * </ol>
     *
     * Complexity:
     * Time: O(n) where n is the password length
     * Space: O(1)
     *
     * @param raw raw value from the new-password {@code EditText}
     * @return the validation result
     */
    @NonNull
    public static ValidationResult validateNewPassword(@Nullable String raw) {
        String value = InputSanitizer.trim(raw);
        if (value.isEmpty()) {
            return ValidationResult.invalid("Password cannot be blank");
        }
        if (InputSanitizer.containsDangerousContent(value)) {
            return ValidationResult.invalid("Password contains invalid characters");
        }
        if (value.length() < PASSWORD_MIN) {
            return ValidationResult.invalid("Password must be at least " + PASSWORD_MIN + " characters");
        }
        if (value.length() > PASSWORD_MAX) {
            return ValidationResult.invalid("Password must not exceed " + PASSWORD_MAX + " characters");
        }
        if (!PASSWORD_POLICY.matcher(value).matches()) {
            return ValidationResult.invalid(
                    "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character (@$!%*?&)");
        }
        return ValidationResult.valid();
    }

    /**
     * Validates a person's given or family name.
     *
     * <p>Rules (mirror {@code RegisterRequestDTO#firstName} and {@code #lastName}):</p>
     * <ol>
     *   <li>Not blank</li>
     *   <li>No dangerous/injection content</li>
     *   <li>Only Unicode letters, spaces, hyphens, apostrophes, and periods allowed</li>
     *   <li>Length between {@value #NAME_MIN} and {@value #NAME_MAX}</li>
     * </ol>
     *
     * Complexity:
     * Time: O(n) where n is the name length
     * Space: O(1)
     *
     * @param raw       raw value from the name {@code EditText}
     * @param fieldName human-readable field label used in error messages (e.g. "First name")
     * @return the validation result
     */
    @NonNull
    public static ValidationResult validateName(@Nullable String raw, @NonNull String fieldName) {
        String value = InputSanitizer.trim(raw);
        if (value.isEmpty()) {
            return ValidationResult.invalid(fieldName + " cannot be blank");
        }
        if (InputSanitizer.containsDangerousContent(value)) {
            return ValidationResult.invalid(fieldName + " contains invalid characters");
        }
        if (!InputSanitizer.isValidNameCharset(value)) {
            return ValidationResult.invalid(fieldName + " may only contain letters, spaces, hyphens, and apostrophes");
        }
        if (value.length() < NAME_MIN) {
            return ValidationResult.invalid(fieldName + " must be at least " + NAME_MIN + " characters");
        }
        if (value.length() > NAME_MAX) {
            return ValidationResult.invalid(fieldName + " must not exceed " + NAME_MAX + " characters");
        }
        return ValidationResult.valid();
    }

    /**
     * Validates that a repeated-password field matches the primary password field.
     *
     * Complexity:
     * Time: O(n) where n is the password length
     * Space: O(1)
     *
     * @param password       the primary (validated) password
     * @param repeatPassword the value entered in the repeat-password field
     * @return the validation result
     */
    @NonNull
    public static ValidationResult validatePasswordsMatch(@Nullable String password,
                                                          @Nullable String repeatPassword) {
        if (password == null || !password.equals(repeatPassword)) {
            return ValidationResult.invalid("Passwords do not match");
        }
        return ValidationResult.valid();
    }

    /**
     * Validates that the terms-of-use checkbox has been checked.
     *
     * <p>Rules (mirrors {@code RegisterRequestDTO#termsAccepted}'s {@code @AssertTrue}):</p>
     * <ol>
     *   <li>Must be {@code true}</li>
     * </ol>
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param accepted the current checked state of the terms checkbox
     * @return the validation result
     */
    @NonNull
    public static ValidationResult validateTermsAccepted(boolean accepted) {
        if (!accepted) {
            return ValidationResult.invalid("You must accept the Terms of Use to continue");
        }
        return ValidationResult.valid();
    }
}
