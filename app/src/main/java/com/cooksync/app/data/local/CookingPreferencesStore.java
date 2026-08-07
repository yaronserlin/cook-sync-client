package com.cooksync.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.cooksync.app.CookSyncApplication;

/**
 * Persists device-level cooking preferences (screen-awake and timer sound/vibration toggles)
 * in a plain {@link SharedPreferences} file, separate from {@link TokenStore}'s encrypted
 * session storage since these settings carry no sensitive data and belong to the device rather
 * than the account. Read by {@link com.cooksync.app.ui.recipe.cooking.CookingModeActivity} and
 * written by {@link com.cooksync.app.ui.settings.CookingPreferencesActivity}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public final class CookingPreferencesStore {

    private static final String PREFS_FILE_NAME = "cooksync_cooking_prefs";

    private static final String KEY_SCREEN_AWAKE = "screen_awake_enabled";
    private static final String KEY_TIMER_SOUND = "timer_sound_enabled";

    private CookingPreferencesStore() {
    }

    /**
     * Returns whether cooking mode should keep the screen awake.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return {@code true} if the screen should stay awake during cooking mode, defaults to
     *         {@code true} for a fresh install
     */
    public static boolean isScreenAwakeEnabled() {
        return prefs().getBoolean(KEY_SCREEN_AWAKE, true);
    }

    /**
     * Persists whether cooking mode should keep the screen awake.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param enabled {@code true} to keep the screen awake during cooking mode
     */
    public static void setScreenAwakeEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_SCREEN_AWAKE, enabled).apply();
    }

    /**
     * Returns whether a finished step timer should play a sound and vibrate.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return {@code true} if timers should sound and vibrate, defaults to {@code true} for a
     *         fresh install
     */
    public static boolean isTimerSoundEnabled() {
        return prefs().getBoolean(KEY_TIMER_SOUND, true);
    }

    /**
     * Persists whether a finished step timer should play a sound and vibrate.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param enabled {@code true} to play a sound and vibrate when a timer finishes
     */
    public static void setTimerSoundEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_TIMER_SOUND, enabled).apply();
    }

    private static SharedPreferences prefs() {
        return CookSyncApplication.getAppContext().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
    }
}
