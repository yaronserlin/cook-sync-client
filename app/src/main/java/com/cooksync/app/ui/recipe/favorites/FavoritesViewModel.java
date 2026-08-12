package com.cooksync.app.ui.recipe.favorites;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.AbstractFilterableListViewModel;
import com.cooksync.app.util.PendingActionScheduler;
import com.cooksync.app.util.RecipeFilterUtils;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages data state for {@link FavoriteRecipesActivity}: the user's favorited recipes,
 * search/sort/difficulty/tag filtering (client-side, mirroring {@link com.cooksync.app.ui.recipe.myrecipes.MyRecipesViewModel}
 * since {@code GET /api/favorites} also returns the whole set unpaginated), an optional
 * "only the ones I annotated" filter, and removing a recipe from favorites with a client-side
 * undo window (see {@link #removeFavorite}).
 *
 * @author Yaron Serlin
 * @version 1.3
 * @since 04/08/2026
 */
public class FavoritesViewModel extends AbstractFilterableListViewModel {

    private final RecipeRepository repository;
    private final TagRepository tagRepository;

    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> displayedResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<TagResponse>>> tagsResult = new MutableLiveData<>();

    private final List<RecipePreviewResponse> allFavorites = new ArrayList<>();

    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private String currentQuery = null;
    private boolean onlyWithNotes = false;

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param repository the repository used for favorites calls
     * @param tagRepository the repository used to load the available tags
     */
    public FavoritesViewModel(RecipeRepository repository, TagRepository tagRepository) {
        this.repository = repository;
        this.tagRepository = tagRepository;
    }

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getDisplayedResult() { return displayedResult; }
    public LiveData<ApiResult<List<TagResponse>>> getTagsResult() { return tagsResult; }

    /** Total favorited recipes, ignoring the active search/filters. */
    public int getTotalCount() { return allFavorites.size(); }

    /** How many favorited recipes carry a private note, ignoring the active search/filters. */
    public long getWithNotesCount() {
        return allFavorites.stream().filter(RecipePreviewResponse::hasPersonalNote).count();
    }

    /** {@code true} once favorites have loaded and there's at least one. */
    public boolean hasAnyFavorites() { return !allFavorites.isEmpty(); }

    /** The active search text, or {@code null} if none is set. */
    public String getCurrentQuery() { return currentQuery; }

    public void loadTags() {
        tagRepository.getAllTags(tagsResult);
    }

    public void loadFavorites() {
        displayedResult.setValue(new ApiResult.Loading<>());
        MutableLiveData<ApiResult<List<RecipePreviewResponse>>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                allFavorites.clear();
                allFavorites.addAll(success.getData());
                publishFiltered();
            } else {
                displayedResult.setValue(apiResult);
            }
        });
        repository.getFavorites(result);
    }

    /**
     * Filters the already-loaded favorites by title/description match.
     *
     * @param query the search text, or blank/{@code null} to clear it
     */
    public void search(String query) {
        currentQuery = (query == null || query.isBlank()) ? null : query.trim();
        publishFiltered();
    }

    /**
     * Toggles the "only recipes I've annotated" filter.
     *
     * @param onlyWithNotes {@code true} to show only favorites with a private note
     */
    public void setOnlyWithNotes(boolean onlyWithNotes) {
        this.onlyWithNotes = onlyWithNotes;
        publishFiltered();
    }

    /**
     * Re-filters {@link #allFavorites} against the new filter state — this list is already
     * fully loaded client-side, so no new network call is needed.
     */
    @Override
    protected void onFiltersChanged() {
        publishFiltered();
    }

    /**
     * Removes a recipe from favorites and drops it from the local cache immediately, so the
     * list updates without waiting on a full reload. The server call itself is delayed by
     * {@link BaseRepository#UNDO_WINDOW_MS} rather than sent right away, so a tap on the toast's
     * "Undo" action (see {@link #undoRemoveFavorite}) can cancel it before it's ever sent — a
     * removal the user undoes in time never reaches the server at all.
     *
     * @param recipeId the recipe to unfavorite
     */
    public void removeFavorite(String recipeId) {
        allFavorites.removeIf(r -> r.id().equals(recipeId));
        publishFiltered();
        pendingActions.schedule(recipeId, BaseRepository.UNDO_WINDOW_MS,
                () -> repository.removeFavorite(recipeId, new MutableLiveData<>()));
    }

    /**
     * Cancels a still-pending removal and restores the recipe to the list. Does nothing if the
     * undo window already elapsed and the removal reached the server (the toast that offers
     * this action auto-dismisses at the same time, so that case isn't reachable in practice).
     *
     * @param recipe the recipe to restore, as it looked before being removed
     */
    public void undoRemoveFavorite(RecipePreviewResponse recipe) {
        if (!pendingActions.cancel(recipe.id())) return;
        allFavorites.add(recipe);
        publishFiltered();
    }

    /**
     * Flushes any still-pending removals immediately rather than dropping them, so navigating
     * away before the undo window elapses doesn't silently discard a removal the user never
     * undid.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        pendingActions.flushAll();
    }

    private void publishFiltered() {
        List<RecipePreviewResponse> displayed = new ArrayList<>(allFavorites);

        if (currentQuery != null) {
            String needle = currentQuery.toLowerCase(Locale.ROOT);
            displayed.removeIf(r -> {
                boolean titleMatch = r.title() != null && r.title().toLowerCase(Locale.ROOT).contains(needle);
                boolean descMatch = r.description() != null && r.description().toLowerCase(Locale.ROOT).contains(needle);
                return !titleMatch && !descMatch;
            });
        }
        if (onlyWithNotes) {
            displayed.removeIf(r -> !r.hasPersonalNote());
        }

        displayed = RecipeFilterUtils.applyFiltersAndSort(displayed, currentDifficulty, currentMinRating,
                currentMaxTotalTimeMinutes, selectedTags, currentSort);

        displayedResult.setValue(new ApiResult.Success<>(displayed));
    }
}
