package com.cooksync.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Backs the app's "act now, send later" undo pattern: a user action's visible effect happens
 * immediately, but the network call it triggers is deferred by a fixed window so a matching
 * {@link #cancel} (wired to a toast's "Undo" action) can suppress it before it's ever sent.
 * Used by the recipe/review/favorites/admin ViewModels for removing a favorite, deleting or
 * reporting a review, toggling recipe visibility, deleting a recipe, and admin moderation
 * actions — one instance per ViewModel.
 *
 * <p>The delay itself runs on a background {@link ScheduledExecutorService}, kept
 * framework-free so this class can be constructed in a plain JVM unit test. But every
 * caller's deferred action ends up calling {@code LiveData} APIs (typically via
 * {@link BaseViewModel#observeOnce}, which calls {@code observeForever}) that assert
 * they're being invoked from the Android main thread — so once the delay elapses, the
 * action itself is posted back to the main thread via {@link Handler} before it runs, not
 * executed directly on the executor's background thread. {@link #flushAll()} is the
 * exception: it runs each action synchronously on its caller's thread, since it's only
 * ever invoked from {@code onCleared()}, which Android already guarantees runs on the main
 * thread — matching this class's original, purely {@code Handler}-based behavior.</p>
 *
 * @author Yaron Serlin
 * @version 2.1
 * @since 06/08/2026
 */
public final class PendingActionScheduler {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, PendingEntry> pending = new ConcurrentHashMap<>();

    private record PendingEntry(Runnable action, ScheduledFuture<?> future) {
    }

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
        Runnable fireOnMainThread = () -> {
            pending.remove(key);
            new Handler(Looper.getMainLooper()).post(action);
        };
        ScheduledFuture<?> future = executor.schedule(fireOnMainThread, delayMs, TimeUnit.MILLISECONDS);
        pending.put(key, new PendingEntry(action, future));
    }

    /**
     * Cancels a pending action before it runs.
     *
     * @param key the key it was scheduled under
     * @return {@code true} if an action was actually pending and got cancelled
     */
    public boolean cancel(String key) {
        PendingEntry entry = pending.remove(key);
        if (entry == null) {
            return false;
        }
        entry.future().cancel(false);
        return true;
    }

    /**
     * Runs every still-pending action immediately rather than dropping it, so clearing the
     * owning ViewModel before a window elapses doesn't silently discard the action.
     */
    public void flushAll() {
        List<PendingEntry> entries = new ArrayList<>(pending.values());
        pending.clear();
        for (PendingEntry entry : entries) {
            entry.future().cancel(false);
            entry.action().run();
        }
    }
}
