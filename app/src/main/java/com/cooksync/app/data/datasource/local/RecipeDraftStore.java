package com.cooksync.app.data.datasource.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.cooksync.app.CookSyncApplication;
import com.cooksync.app.data.model.recipe.RecipeDraft;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Persists every in-progress {@link RecipeDraft} as a single serialized JSON array in a plain
 * {@link SharedPreferences} file, separate from {@link TokenStore}'s encrypted session storage
 * since a recipe draft carries no sensitive data — the same local-only, non-network pattern
 * {@link CookingPreferencesStore} already uses. Any number of drafts can be pinned at once, each
 * identified by its own {@link RecipeDraft#draftId}; {@link #save(RecipeDraft)} upserts by that
 * id so saving one draft never disturbs another.
 *
 * <p>Used directly by {@link com.cooksync.app.ui.recipe.wizard.AddRecipeViewModel} rather than
 * wrapped in a network-style {@code *Repository} interface, matching how {@link TokenStore} is
 * used elsewhere in the app.</p>
 *
 * <p>Known accepted tradeoff: drafts are device-local only (not synced across devices or
 * reinstalls), and any cover/inline images already uploaded to Cloudinary while drafting are
 * not cleaned up if a draft is discarded.</p>
 *
 * @author Yaron Serlin
 * @version 2.0
 * @since 08/08/2026
 */
public final class RecipeDraftStore {

    private static final String PREFS_FILE_NAME = "cooksync_recipe_draft_prefs";
    private static final String KEY_DRAFTS_JSON = "drafts_json";

    private static final Gson GSON = new Gson();

    private RecipeDraftStore() {
    }

    /**
     * Persists {@code draft}, replacing any previously stored draft with the same
     * {@link RecipeDraft#draftId} or appending it as a new entry otherwise.
     *
     * Complexity:
     * Time: O(n) where n is the number of stored drafts
     * Space: O(n)
     *
     * @param draft the draft to persist
     */
    public static void save(RecipeDraft draft) {
        List<RecipeDraft> drafts = loadAll();
        boolean replaced = false;
        for (int i = 0; i < drafts.size(); i++) {
            if (Objects.equals(drafts.get(i).draftId, draft.draftId)) {
                drafts.set(i, draft);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            drafts.add(draft);
        }
        persist(drafts);
    }

    /**
     * Loads every currently stored draft.
     *
     * Complexity:
     * Time: O(n) where n is the serialized size of all stored drafts
     * Space: O(n)
     *
     * @return the stored drafts, most-recently-added first is not guaranteed — callers that
     *         care about order should sort by {@link RecipeDraft#savedAtMillis}
     */
    public static List<RecipeDraft> loadAll() {
        String json = prefs().getString(KEY_DRAFTS_JSON, null);
        if (json == null) {
            return new ArrayList<>();
        }
        RecipeDraft[] stored = GSON.fromJson(json, RecipeDraft[].class);
        return stored != null ? new ArrayList<>(Arrays.asList(stored)) : new ArrayList<>();
    }

    /**
     * Loads a single stored draft by id.
     *
     * Complexity:
     * Time: O(n) where n is the number of stored drafts
     * Space: O(n)
     *
     * @param draftId the draft's client-generated id
     * @return the stored draft, or {@code null} if none matches
     */
    public static RecipeDraft load(String draftId) {
        for (RecipeDraft draft : loadAll()) {
            if (Objects.equals(draft.draftId, draftId)) {
                return draft;
            }
        }
        return null;
    }

    /**
     * Returns whether at least one draft is currently stored.
     *
     * Complexity:
     * Time: O(n) where n is the number of stored drafts
     * Space: O(1)
     *
     * @return {@code true} if any draft exists
     */
    public static boolean hasDraft() {
        return !loadAll().isEmpty();
    }

    /**
     * Removes a single stored draft, e.g. after a successful publish or an explicit discard.
     * Other stored drafts are left untouched.
     *
     * Complexity:
     * Time: O(n) where n is the number of stored drafts
     * Space: O(n)
     *
     * @param draftId the draft's client-generated id
     */
    public static void remove(String draftId) {
        List<RecipeDraft> drafts = loadAll();
        drafts.removeIf(draft -> Objects.equals(draft.draftId, draftId));
        persist(drafts);
    }

    private static void persist(List<RecipeDraft> drafts) {
        prefs().edit().putString(KEY_DRAFTS_JSON, GSON.toJson(drafts)).apply();
    }

    private static SharedPreferences prefs() {
        return CookSyncApplication.getAppContext().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
    }
}
