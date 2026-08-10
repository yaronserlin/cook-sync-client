package com.cooksync.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cooksync.app.data.datasource.local.TokenStore;
import com.cooksync.app.ui.auth.LoginActivity;
import com.cooksync.app.ui.auth.RegisterActivity;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.common.OrganicToast;
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
 * <p>Also exposes the process-wide {@link Context} via {@link #getAppContext()} so classes
 * outside the UI layer (e.g. {@code *RepositoryImpl}) can resolve string resources without
 * needing a {@code Context} threaded through every call.</p>
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 04/08/2026
 */
public class CookSyncApplication extends Application {

    private static Context appContext;

    private WeakReference<Activity> currentActivity = new WeakReference<>(null);

    /** True once the process has actually seen a logged-in session, so a fresh install's
     *  initial {@code false} state (no session yet) is never mistaken for a forced logout. */
    private boolean sessionWasActive = false;

    /**
     * Returns the process-wide application {@link Context}, available once {@link #onCreate()}
     * has run.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the application context
     */
    public static Context getAppContext() {
        return appContext;
    }

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
        appContext = getApplicationContext();
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
        boolean wasForced = SessionManager.getInstance().consumeWasForcedLogout();
        redirectToLogin(wasForced);
    }

    /**
     * Sends the user back to {@link LoginActivity} with a cleared back stack, unless they're
     * already on the login/register flow (nothing to redirect away from there). Also the
     * single place a logout-triggered navigation happens, whether the logout was forced
     * (expired/revoked session) or explicitly requested by the user (e.g. from Profile) —
     * only the toast message differs between the two.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param wasForced whether this logout was involuntary (expired/revoked session)
     */
    private void redirectToLogin(boolean wasForced) {
        Activity top = currentActivity.get();
        if (top instanceof LoginActivity || top instanceof RegisterActivity) {
            return;
        }
        if (top != null && wasForced) {
            OrganicToast.show(top, null, getString(R.string.error_session_expired));
        }
        Intent extras = new Intent();
        extras.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Navigator.start(this, LoginActivity.class, extras);
    }

    /**
     * Tracks the current foreground activity so {@link #redirectToLogin} knows where the
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
