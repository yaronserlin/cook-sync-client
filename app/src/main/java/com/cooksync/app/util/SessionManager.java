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
     * Clears the stored session as the result of an explicit, user-initiated logout.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void logout() {
        TokenStore.clear();
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
        loggedIn.postValue(false);
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
}
