package com.cooksync.app.util;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.local.TokenStore;
import com.dtos.response.auth.AuthResponse;

/**
 * Process-wide, observable holder of the current authentication state. Wraps
 * {@link TokenStore} with a reactive {@link LiveData} surface so that any screen
 * (e.g. a base activity watching for forced logout) can react immediately when the
 * session starts or ends, without polling shared preferences.
 *
 * <p>{@link com.cooksync.app.data.remote.TokenAuthenticator} calls {@link #forceLogout()}
 * when a refresh-token attempt fails, which is the single place a session is invalidated
 * outside of an explicit user-initiated logout.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public final class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    private final MutableLiveData<Boolean> loggedIn = new MutableLiveData<>(false);

    /** Whether the most recent transition to logged-out was a forced logout (expired/revoked
     *  refresh token) rather than the user tapping "Log out". Consumed once by
     *  {@link com.cooksync.app.CookSyncApplication} to decide whether to show the "session
     *  expired" toast. */
    private volatile boolean lastLogoutWasForced = false;

    private SessionManager() {
    }

    /**
     * Returns the process-wide singleton instance.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the shared {@code SessionManager} instance
     */
    public static SessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Synchronizes the observable login state with whatever session {@link TokenStore}
     * currently holds on disk. Called once on process start.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void restoreFromTokenStore() {
        loggedIn.postValue(TokenStore.hasSession());
    }

    /**
     * Persists a newly issued session and flips observers to the logged-in state.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param authResponse the auth payload returned by login, register, or token refresh
     */
    public void startSession(AuthResponse authResponse) {
        TokenStore.saveTokens(authResponse.token(), authResponse.refreshToken());
        TokenStore.saveUserProfile(
                authResponse.userId(),
                authResponse.firstName(),
                authResponse.lastName(),
                authResponse.isAdmin(),
                authResponse.avatarUrl()
        );
        loggedIn.postValue(true);
    }

    /**
     * Refreshes the cached user profile fields without touching the stored access/refresh
     * tokens. Used by {@code /api/auth/validate-token}, whose response carries {@code null}
     * token fields by design (it's a profile check, not a token-issuing call) — passing that
     * response to {@link #startSession} would overwrite a good token pair with {@code null}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param authResponse the auth payload returned by token validation
     */
    public void refreshCachedProfile(AuthResponse authResponse) {
        TokenStore.saveUserProfile(
                authResponse.userId(),
                authResponse.firstName(),
                authResponse.lastName(),
                authResponse.isAdmin(),
                authResponse.avatarUrl()
        );
    }

    /**
     * Clears the stored session as the result of an explicit, user-initiated logout.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void logout() {
        TokenStore.clear();
        lastLogoutWasForced = false;
        loggedIn.postValue(false);
    }

    /**
     * Clears the stored session because token refresh failed (refresh token expired or
     * revoked). Distinct method from {@link #logout()} only for call-site clarity —
     * behavior is currently identical.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void forceLogout() {
        TokenStore.clear();
        lastLogoutWasForced = true;
        loggedIn.postValue(false);
    }

    /**
     * Reads and resets whether the most recent logout was forced. Intended to be called
     * exactly once, by the process-wide logged-out observer, immediately after reacting to a
     * true → false transition.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return {@code true} if the logout was forced (expired/revoked session)
     */
    public boolean consumeWasForcedLogout() {
        boolean wasForced = lastLogoutWasForced;
        lastLogoutWasForced = false;
        return wasForced;
    }

    /**
     * Exposes the observable login state for UI components to react to session changes.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return live, observable login state
     */
    public LiveData<Boolean> isLoggedIn() {
        return loggedIn;
    }

    /**
     * Returns the cached first name of the currently authenticated user.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the first name, or {@code null} if no session is active
     */
    @Nullable
    public String getFirstName() {
        return TokenStore.getFirstName();
    }

    /**
     * Returns the cached last name of the currently authenticated user.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the last name, or {@code null} if no session is active
     */
    @Nullable
    public String getLastName() {
        return TokenStore.getLastName();
    }

    /**
     * Builds a two-letter initials string (e.g. "YS") from the cached first/last name, for
     * the avatar chip shown in the app bar. Falls back to a single "?" if no session is active.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the initials to render in the profile avatar
     */
    public String getInitials() {
        String first = getFirstName();
        String last = getLastName();
        StringBuilder initials = new StringBuilder();
        if (first != null && !first.isEmpty()) {
            initials.append(Character.toUpperCase(first.charAt(0)));
        }
        if (last != null && !last.isEmpty()) {
            initials.append(Character.toUpperCase(last.charAt(0)));
        }
        return initials.length() > 0 ? initials.toString() : "?";
    }

    /**
     * Returns the cached user ID of the currently authenticated user.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the user ID, or {@code null} if no session is active
     */
    @Nullable
    public String getUserId() {
        return TokenStore.getUserId();
    }

    /**
     * Returns the cached email of the currently authenticated user.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the email, or {@code null} if no session is active or it was never cached
     */
    @Nullable
    public String getEmail() {
        return TokenStore.getEmail();
    }

    /**
     * Returns the cached avatar URL of the currently authenticated user.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the avatar URL, or {@code null} if unset
     */
    @Nullable
    public String getAvatarUrl() {
        return TokenStore.getAvatarUrl();
    }

    /**
     * Caches the authenticated user's email address. {@code AuthResponse} never carries an
     * email field, so this is populated separately from whichever request payload already
     * had it (login, register, or an email-change request), rather than from the response.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param email the email address to cache
     */
    public void cacheEmail(String email) {
        TokenStore.saveEmail(email);
    }

    /**
     * Updates the cached first/last name after a successful profile edit.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param firstName the updated first name
     * @param lastName the updated last name
     */
    public void updateCachedProfile(String firstName, String lastName) {
        TokenStore.updateNames(firstName, lastName);
    }

    /**
     * Updates the cached avatar URL after a successful avatar edit.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param avatarUrl the updated avatar URL
     */
    public void updateCachedAvatar(String avatarUrl) {
        TokenStore.updateAvatarUrl(avatarUrl);
    }
}
