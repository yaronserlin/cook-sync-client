package com.cooksync.app;

import android.app.Application;

import com.cooksync.app.data.local.TokenStore;
import com.cooksync.app.util.SessionManager;

/**
 * Application entry point responsible for eagerly initializing process-wide singletons
 * that must exist before any {@code Activity} starts, namely the encrypted token storage
 * and the session state holder derived from it.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class CookSyncApplication extends Application {

    /**
     * Initializes {@link TokenStore} and {@link SessionManager} singletons.
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
    }
}
