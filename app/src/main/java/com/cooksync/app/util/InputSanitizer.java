package com.cooksync.app.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Defensive utility that strips or detects potentially dangerous content in raw user input
 * <em>before</em> it is validated or sent over the wire. This is a client-side,
 * defense-in-depth layer — the server enforces its own constraints independently; this class
 * exists to catch accidental or malicious payloads early and give the user a clear error
 * instead of a cryptic server rejection.
 *
 * <p>Checks performed:</p>
 * <ul>
 *   <li>Null byte injection ({@code \u0000})</li>
 *   <li>ASCII control-character injection (0x01 – 0x1F, excluding tab)</li>
 *   <li>HTML/script injection patterns ({@code <script}, {@code javascript:}, event handlers)</li>
 *   <li>SQL injection fingerprints ({@code '}, {@code --}, {@code ;}, UNION/SELECT keywords)</li>
 *   <li>LDAP special characters ({@code (}, {@code )}, {@code *}, {@code \\})</li>
 * </ul>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public final class InputSanitizer {

    // ─── Compiled patterns ──────────────────────────────────────────────────────

    /** Null-byte and ASCII control characters (except horizontal tab 0x09). */
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0A-\\x1F]");

    /** HTML tag openers and javascript: URI scheme (case-insensitive). */
    private static final Pattern HTML_INJECTION =
            Pattern.compile("<\\s*script|javascript\\s*:|on\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    /** SQL injection fingerprints likely in a form field context. */
    private static final Pattern SQL_INJECTION =
            Pattern.compile("('\\s*(--|\\/\\*|;|OR|AND|UNION|SELECT|INSERT|DROP|DELETE|UPDATE|EXEC)\\s*)",
                    Pattern.CASE_INSENSITIVE);

    /** LDAP special characters that have no place in a name/email/password field. */
    private static final Pattern LDAP_INJECTION =
            Pattern.compile("[()\\\\*\\x00]");

    /**
     * Characters permitted in a person's name: Unicode letters, spaces, hyphens,
     * apostrophes, and periods (for initials/titles like "St. Claire").
     */
    private static final Pattern VALID_NAME_CHARS =
            Pattern.compile("^[\\p{L}\\s'\\-.]+$");

    /**
     * Hard limit on any single field submitted by the client, regardless of server-side
     * max-length constraints, to prevent absurdly large payloads reaching the network.
     */
    private static final int MAX_FIELD_LENGTH = 500;

    private InputSanitizer() {
    }

    // ─── Public API ─────────────────────────────────────────────────────────────

    /**
     * Trims whitespace from the input and returns the result, or an empty string if
     * {@code null} is supplied.
     *
     * Complexity:
     * Time: O(n) where n is the length of the string
     * Space: O(n)
     *
     * @param raw the raw string from a UI field
     * @return trimmed string, never {@code null}
     */
    @NonNull
    public static String trim(@Nullable String raw) {
        return raw == null ? "" : raw.trim();
    }

    /**
     * Returns {@code true} if the (already-trimmed) input contains any character pattern
     * considered dangerous for the field types this app uses. Call this on the trimmed
     * value before running field-specific validation rules.
     *
     * Complexity:
     * Time: O(n) where n is the length of the string
     * Space: O(1)
     *
     * @param input trimmed field value (may be blank)
     * @return {@code true} if a dangerous pattern is detected
     */
    public static boolean containsDangerousContent(@Nullable String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        if (input.length() > MAX_FIELD_LENGTH) {
            return true; // unreasonably long — treat as suspicious
        }
        return CONTROL_CHARS.matcher(input).find()
                || HTML_INJECTION.matcher(input).find()
                || SQL_INJECTION.matcher(input).find()
                || LDAP_INJECTION.matcher(input).find();
    }

    /**
     * Returns {@code true} if the value contains only characters that are acceptable
     * in a person's given or family name (Unicode letters, spaces, hyphens, apostrophes,
     * and periods).
     *
     * <p>Rejects digits, emoji, control characters, and all punctuation not in the
     * allowed set — preventing names like {@code "'; DROP TABLE users; --"} from
     * being accepted at the form level.</p>
     *
     * Complexity:
     * Time: O(n) where n is the length of the string
     * Space: O(1)
     *
     * @param name the trimmed name value to check
     * @return {@code true} if the name contains only safe characters
     */
    public static boolean isValidNameCharset(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return VALID_NAME_CHARS.matcher(name).matches();
    }
}
