package com.cooksync.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the app's "act now, send later" undo pattern: a user action's visible effect happens
 * immediately, but the network call it triggers is deferred by a fixed window so a matching
 * {@link #cancel} (wired to a toast's "Undo" action) can suppress it before it's ever sent.
 * Used by the recipe/review/favorites ViewModels for removing a favorite, deleting or reporting
 * a review, toggling recipe visibility, and deleting a recipe — one instance per ViewModel.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 06/08/2026
 */
public final class PendingActionScheduler {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> pending = new HashMap<>();

    /**
     * Schedules {@code action} to run after {@code delayMs}, replacing any action already
     * pending under the same key.
     *
     * @param key identifies the pending action (e.g. a recipe or review id)
     * @param delayMs how long to wait before running {@code action}
     * @param action the deferred work, typically a network call
     */
    public void schedule(String key, long delayMs, Runnable action) {
        cancel(key);
        Runnable wrapped = () -> {
            pending.remove(key);
            action.run();
        };
        pending.put(key, wrapped);
        handler.postDelayed(wrapped, delayMs);
    }

    /**
     * Cancels a pending action before it runs.
     *
     * @param key the key it was scheduled under
     * @return {@code true} if an action was actually pending and got cancelled
     */
    public boolean cancel(String key) {
        Runnable action = pending.remove(key);
        if (action == null) {
            return false;
        }
        handler.removeCallbacks(action);
        return true;
    }

    /**
     * Runs every still-pending action immediately rather than dropping it, so clearing the
     * owning ViewModel before a window elapses doesn't silently discard the action.
     */
    public void flushAll() {
        List<Runnable> actions = new ArrayList<>(pending.values());
        pending.clear();
        for (Runnable action : actions) {
            handler.removeCallbacks(action);
            action.run();
        }
    }
}
