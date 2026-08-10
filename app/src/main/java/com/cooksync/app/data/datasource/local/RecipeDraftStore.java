package com.cooksync.app.data.datasource.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.cooksync.app.CookSyncApplication;
import com.cooksync.app.data.model.recipe.RecipeDraft;
import com.google.gson.Gson;

/**
 * Persists a single in-progress {@link RecipeDraft} as a serialized JSON blob in a plain
 * {@link SharedPreferences} file, separate from {@link TokenStore}'s encrypted session storage
 * since a recipe draft carries no sensitive data — the same local-only, non-network pattern
 * {@link CookingPreferencesStore} already uses. Only one draft is ever pinned at a time (per the
 * design's singular "Resumable draft" card on My Recipes), so this store has no list/ID
 * management: saving always replaces whatever draft was there before.
 *
 * <p>Used directly by {@link com.cooksync.app.ui.recipe.wizard.AddRecipeViewModel} rather than
 * wrapped in a network-style {@code *Repository} interface, matching how {@link TokenStore} is
 * used elsewhere in the app.</p>
 *
 * <p>Known accepted tradeoff: the draft is device-local only (not synced across devices or
 * reinstalls), and any cover/inline images already uploaded to Cloudinary while drafting are
 * not cleaned up if the draft is discarded.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public final class RecipeDraftStore {

    private static final String PREFS_FILE_NAME = "cooksync_recipe_draft_prefs";
    private static final String KEY_DRAFT_JSON = "draft_json";

    private static final Gson GSON = new Gson();

    private RecipeDraftStore() {
    }

    /**
     * Persists {@code draft}, replacing whatever draft was previously stored.
     *
     * Complexity:
     * Time: O(n) where n is the serialized draft size
     * Space: O(n)
     *
     * @param draft the draft to persist
     */
    public static void save(RecipeDraft draft) {
        prefs().edit().putString(KEY_DRAFT_JSON, GSON.toJson(draft)).apply();
    }

    /**
     * Loads the currently stored draft, if any.
     *
     * Complexity:
     * Time: O(n) where n is the serialized draft size
     * Space: O(n)
     *
     * @return the stored draft, or {@code null} if none is stored
     */
    public static RecipeDraft load() {
        String json = prefs().getString(KEY_DRAFT_JSON, null);
        if (json == null) {
            return null;
        }
        return GSON.fromJson(json, RecipeDraft.class);
    }

    /**
     * Returns whether a draft is currently stored.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return {@code true} if a draft exists
     */
    public static boolean hasDraft() {
        return prefs().contains(KEY_DRAFT_JSON);
    }

    /**
     * Clears the stored draft, e.g. after a successful publish or an explicit discard.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public static void clear() {
        prefs().edit().remove(KEY_DRAFT_JSON).apply();
    }

    private static SharedPreferences prefs() {
        return CookSyncApplication.getAppContext().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
    }
}
