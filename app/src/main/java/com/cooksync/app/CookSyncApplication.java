package com.cooksync.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cooksync.app.data.local.TokenStore;
import com.cooksync.app.ui.auth.LoginActivity;
import com.cooksync.app.ui.auth.RegisterActivity;
import com.cooksync.app.util.SessionManager;

import java.lang.ref.WeakReference;

/**
 * Application entry point responsible for eagerly initializing process-wide singletons
 * that must exist before any {@code Activity} starts, namely the encrypted token storage
 * and the session state holder derived from it. Also owns the single place that reacts to a
 * forced logout (expired/invalid refresh token): {@link SessionManager#forceLogout()} only
 * clears local state, so without an observer here, a session invalidated mid-use would leave
 * every open screen silently failing its API calls instead of returning the user to
 * {@link LoginActivity}.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 04/08/2026
 */
public class CookSyncApplication extends Application {

    private WeakReference<Activity> currentActivity = new WeakReference<>(null);

    /** True once the process has actually seen a logged-in session, so a fresh install's
     *  initial {@code false} state (no session yet) is never mistaken for a forced logout. */
    private boolean sessionWasActive = false;

    /**
     * Initializes {@link TokenStore} and {@link SessionManager} singletons, and wires up the
     * forced-logout redirect.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    @Override
    public void onCreate() {
        super.onCreate();
        TokenStore.init(this);
        SessionManager.getInstance().restoreFromTokenStore();

        registerActivityLifecycleCallbacks(new ActivityTrackingCallbacks());
        SessionManager.getInstance().isLoggedIn().observeForever(this::onSessionStateChanged);
    }

    /**
     * Reacts to every change in the app-wide login state, redirecting to {@link LoginActivity}
     * only on the true → false transition of an already-active session (i.e. a forced logout),
     * never on the app's initial "no session yet" state.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param loggedIn the session's current logged-in state
     */
    private void onSessionStateChanged(Boolean loggedIn) {
        boolean isLoggedIn = Boolean.TRUE.equals(loggedIn);
        if (isLoggedIn) {
            sessionWasActive = true;
            return;
        }
        if (!sessionWasActive) {
            return;
        }
        sessionWasActive = false;
        redirectToLogin();
    }

    /**
     * Sends the user back to {@link LoginActivity} with a cleared back stack, unless they're
     * already on the login/register flow (nothing to redirect away from there).
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void redirectToLogin() {
        Activity top = currentActivity.get();
        if (top instanceof LoginActivity || top instanceof RegisterActivity) {
            return;
        }
        if (top != null) {
            Toast.makeText(top, "Your session expired — please sign in again.", Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    /**
     * Tracks the current foreground activity so {@link #redirectToLogin()} knows where the
     * user currently is, without every screen needing to opt in individually.
     */
    private class ActivityTrackingCallbacks implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            currentActivity = new WeakReference<>(activity);
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }
}
