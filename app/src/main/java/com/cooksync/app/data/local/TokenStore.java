package com.cooksync.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Persists the JWT access/refresh token pair and a minimal cached user profile in
 * {@link EncryptedSharedPreferences}, backed by the Android Keystore. This is the single
 * on-device source of truth for authentication state: {@link com.cooksync.app.data.remote.AuthInterceptor},
 * {@link com.cooksync.app.data.remote.TokenAuthenticator} and {@link com.cooksync.app.util.SessionManager}
 * all read and write through this class rather than touching {@link SharedPreferences} directly.
 *
 * <p>Must be initialized once via {@link #init(Context)} before first use, which
 * {@link com.cooksync.app.CookSyncApplication} does on process start.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public final class TokenStore {

    private static final String PREFS_FILE_NAME = "cooksync_secure_prefs";

    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FIRST_NAME = "first_name";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_IS_ADMIN = "is_admin";
    private static final String KEY_AVATAR_URL = "avatar_url";

    private static volatile SharedPreferences preferences;

    private TokenStore() {
    }

    /**
     * Creates the encrypted preferences file, generating (or reusing) an AES256-GCM master
     * key in the Android Keystore. Safe to call more than once; subsequent calls are no-ops.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context application context used to resolve the Keystore-backed master key
     */
    public static void init(Context context) {
        if (preferences != null) {
            return;
        }
        synchronized (TokenStore.class) {
            if (preferences != null) {
                return;
            }
            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                preferences = EncryptedSharedPreferences.create(
                        context,
                        PREFS_FILE_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (GeneralSecurityException | IOException e) {
                throw new IllegalStateException("Failed to initialize encrypted token storage", e);
            }
        }
    }

    /**
     * Persists a fresh JWT access/refresh token pair, replacing any previous values.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param accessToken bearer token to attach to subsequent authenticated requests
     * @param refreshToken token used to obtain a new access token once it expires
     */
    public static void saveTokens(String accessToken, String refreshToken) {
        preferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    /**
     * Persists the minimal cached user profile fields carried on an {@code AuthResponse}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param userId unique identifier of the authenticated user
     * @param firstName authenticated user's first name
     * @param lastName authenticated user's last name
     * @param isAdmin whether the authenticated user has administrative privileges
     * @param avatarUrl URL of the authenticated user's avatar image, may be {@code null}
     */
    public static void saveUserProfile(String userId, String firstName, String lastName,
                                        boolean isAdmin, String avatarUrl) {
        preferences.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_FIRST_NAME, firstName)
                .putString(KEY_LAST_NAME, lastName)
                .putBoolean(KEY_IS_ADMIN, isAdmin)
                .putString(KEY_AVATAR_URL, avatarUrl)
                .apply();
    }

    /**
     * Returns the currently stored access token.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the bearer access token, or {@code null} if no session is stored
     */
    public static String getAccessToken() {
        return preferences.getString(KEY_ACCESS_TOKEN, null);
    }

    /**
     * Returns the currently stored refresh token.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the refresh token, or {@code null} if no session is stored
     */
    public static String getRefreshToken() {
        return preferences.getString(KEY_REFRESH_TOKEN, null);
    }

    /**
     * Returns the cached authenticated user id.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the user id, or {@code null} if no session is stored
     */
    public static String getUserId() {
        return preferences.getString(KEY_USER_ID, null);
    }

    /**
     * Returns the cached authenticated user's first name.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the first name, or {@code null} if no session is stored
     */
    public static String getFirstName() {
        return preferences.getString(KEY_FIRST_NAME, null);
    }

    /**
     * Returns the cached authenticated user's last name.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the last name, or {@code null} if no session is stored
     */
    public static String getLastName() {
        return preferences.getString(KEY_LAST_NAME, null);
    }

    /**
     * Returns whether the cached authenticated user has administrative privileges.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return {@code true} if the user is an admin, {@code false} otherwise or if unset
     */
    public static boolean isAdmin() {
        return preferences.getBoolean(KEY_IS_ADMIN, false);
    }

    /**
     * Returns the cached authenticated user's avatar URL.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the avatar URL, or {@code null} if unset
     */
    public static String getAvatarUrl() {
        return preferences.getString(KEY_AVATAR_URL, null);
    }

    /**
     * Returns whether a non-blank access token is currently stored.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return {@code true} if a session is stored, {@code false} otherwise
     */
    public static boolean hasSession() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }

    /**
     * Clears all stored tokens and cached profile fields, e.g. on logout or when a
     * refresh attempt fails because the refresh token itself is no longer valid.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public static void clear() {
        preferences.edit().clear().apply();
    }
}
