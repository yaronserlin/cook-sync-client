package com.cooksync.app.ui.recipe.myrecipes;
import com.cooksync.app.ui.recipe.common.*;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.common.FilterSheetLauncher;
import com.cooksync.app.util.PendingActionScheduler;
import com.cooksync.app.util.RecipeFilterUtils;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Manages data state for {@link MyRecipesActivity}: the current user's own recipes, search/
 * sort/filter over that list (client-side, since {@code GET /api/recipes/mine} returns the
 * whole set unpaginated), and the outcome of management actions (delete, visibility toggle)
 * triggered from the list.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 04/08/2026
 */
public class MyRecipesViewModel extends BaseViewModel implements FilterSheetLauncher.FilterState {

    /**
     * How long a delete/visibility change waits before actually reaching the server, giving the
     * "Undo" toast action a window to cancel it. Matches {@code OrganicToast}'s auto-dismiss
     * duration, since the undo action stops being reachable once the toast itself is gone.
     */
    private static final long UNDO_WINDOW_MS = 3200;

    private final RecipeRepository repository;
    private final TagRepository tagRepository;

    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> recipesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<TagResponse>>> tagsResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> deleteResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<RecipeResponse>> visibilityResult = new MutableLiveData<>();

    private final List<RecipePreviewResponse> allRecipes = new ArrayList<>();
    /** Thread-safe since filter changes and background repository callbacks can both touch it. */
    private final Set<String> selectedTags = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private String currentQuery = null;
    /** One of "ALL", "PUBLIC", "PRIVATE". */
    private String visibilityFilter = "ALL";
    private String currentSort = "Newest";
    private String currentDifficulty = null;
    private Double currentMinRating = null;
    private Integer currentMaxTotalTimeMinutes = null;

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param repository the repository used for recipe management calls
     * @param tagRepository the repository used to load the available tags
     */
    public MyRecipesViewModel(RecipeRepository repository, TagRepository tagRepository) {
        this.repository = repository;
        this.tagRepository = tagRepository;
        com.cooksync.app.data.service.RecipePublishManager.getInstance().getRecipePublishedEvent().observeForever(event -> {
            if (event != null && event.getContentIfNotHandled() != null) {
                loadMyRecipes();
            }
        });
    }

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getRecipesResult() { return recipesResult; }
    public LiveData<ApiResult<List<TagResponse>>> getTagsResult() { return tagsResult; }

    public void fetchRecipeDetail(String recipeId, MutableLiveData<ApiResult<RecipeResponse>> resultTarget) {
        repository.getRecipeDetail(recipeId, resultTarget);
    }

    /**
     * Fires only when a deferred delete actually reaches the server and fails (see
     * {@link #deleteRecipe}) — a successful delete needs no signal here since the list already
     * reflects it optimistically, and an undone delete never reaches the server at all.
     */
    public LiveData<ApiResult<Void>> getDeleteResult() { return deleteResult; }

    /**
     * Fires only when a deferred visibility change actually reaches the server and fails (see
     * {@link #toggleVisibility}), for the same reason as {@link #getDeleteResult()}.
     */
    public LiveData<ApiResult<RecipeResponse>> getVisibilityResult() { return visibilityResult; }

    public String getCurrentSort() { return currentSort; }
    public String getCurrentDifficulty() { return currentDifficulty; }
    public Double getCurrentMinRating() { return currentMinRating; }
    public Integer getCurrentMaxTotalTimeMinutes() { return currentMaxTotalTimeMinutes; }
    public String getVisibilityFilter() { return visibilityFilter; }
    public Set<String> getSelectedTags() { return Collections.unmodifiableSet(selectedTags); }

    /**
     * How many of the user's recipes (across the whole library, ignoring the active search/
     * filter) are public — used for the screen's "My recipes · N published" title, which
     * should reflect the real total, not just whatever the current filter happens to show.
     */
    public long getPublishedCount() {
        return allRecipes.stream().filter(r -> "PUBLIC".equalsIgnoreCase(r.visibility())).count();
    }

    /** {@code true} once the user's recipe library has loaded and contains at least one recipe. */
    public boolean hasAnyRecipes() { return !allRecipes.isEmpty(); }

    /** The active search text, or {@code null} if none is set. */
    public String getCurrentQuery() { return currentQuery; }

    public void loadTags() {
        tagRepository.getAllTags(tagsResult);
    }

    /**
     * Fetches the user's recipes from the server, replacing whatever was cached before.
     */
    public void loadMyRecipes() {
        recipesResult.setValue(new ApiResult.Loading<>());
        MutableLiveData<ApiResult<List<RecipePreviewResponse>>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                allRecipes.clear();
                allRecipes.addAll(success.getData());
                publishFiltered();
            } else {
                recipesResult.setValue(apiResult);
            }
        });
        repository.getMyRecipes(result);
    }

    /**
     * Filters the already-loaded recipes by title/description match. Unlike the home feed,
     * this never hits the network — the full "mine" list is already local.
     *
     * @param query the search text, or blank/{@code null} to clear it
     */
    public void search(String query) {
        currentQuery = (query == null || query.isBlank()) ? null : query.trim();
        publishFiltered();
    }

    /**
     * Switches the Public/Private/All chip filter.
     *
     * @param visibility one of "ALL", "PUBLIC", "PRIVATE"
     */
    public void setVisibilityFilter(String visibility) {
        this.visibilityFilter = visibility;
        publishFiltered();
    }

    /**
     * Applies the sort/difficulty/tags chosen in the shared filters sheet.
     *
     * @param sortBy one of "Newest", "Top Rated", "Shortest Time"
     * @param difficulty one of "Easy", "Medium", "Hard", or {@code null}
     * @param tags the selected tag names, possibly empty
     * @param minRating minimum average rating threshold, or {@code null} for no filter
     * @param maxTotalTimeMinutes maximum prep+cook time in minutes, or {@code null} for no filter
     */
    public void applyFilters(String sortBy, String difficulty, Collection<String> tags,
                              Double minRating, Integer maxTotalTimeMinutes) {
        this.currentSort = sortBy;
        this.currentDifficulty = difficulty;
        this.currentMinRating = minRating;
        this.currentMaxTotalTimeMinutes = maxTotalTimeMinutes;
        this.selectedTags.clear();
        if (tags != null) {
            this.selectedTags.addAll(tags);
        }
        publishFiltered();
    }

    /** Drops the active difficulty filter alone, leaving query/tags/rating/time untouched. */
    public void removeDifficulty() {
        currentDifficulty = null;
        publishFiltered();
    }

    /** Drops a single selected tag alone. */
    public void removeTag(String tagName) {
        if (selectedTags.remove(tagName)) {
            publishFiltered();
        }
    }

    /** Drops the active minimum-rating filter alone. */
    public void removeMinRating() {
        currentMinRating = null;
        publishFiltered();
    }

    /** Drops the active total-time filter alone. */
    public void removeMaxTotalTime() {
        currentMaxTotalTimeMinutes = null;
        publishFiltered();
    }

    /**
     * Removes a recipe from the list immediately, so the UI updates without waiting on a
     * network round trip. The actual delete is delayed by {@link #UNDO_WINDOW_MS} rather than
     * sent right away, so a tap on the toast's "Undo" action (see {@link #undoDeleteRecipe})
     * can cancel it before it's ever sent — a delete the user undoes in time never reaches the
     * server at all. If the deferred call does reach the server and fails, the recipe is
     * restored and the failure is published via {@link #getDeleteResult()}.
     *
     * @param recipe the recipe to delete, as currently shown
     */
    public void deleteRecipe(RecipePreviewResponse recipe) {
        String recipeId = recipe.id();
        allRecipes.removeIf(r -> Objects.equals(r.id(), recipeId));
        publishFiltered();

        pendingActions.schedule(recipeId, UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<Void>> result = new MutableLiveData<>();
            observeOnce(result, apiResult -> {
                if (apiResult instanceof ApiResult.Error<Void>) {
                    allRecipes.add(recipe);
                    publishFiltered();
                }
                deleteResult.setValue(apiResult);
            });
            repository.deleteRecipe(recipeId, result);
        });
    }

    /**
     * Cancels a still-pending delete and restores the recipe to the list. Does nothing if the
     * undo window already elapsed and the delete reached the server.
     *
     * @param recipe the recipe to restore, as it looked before being deleted
     */
    public void undoDeleteRecipe(RecipePreviewResponse recipe) {
        if (!pendingActions.cancel(recipe.id())) return;
        allRecipes.add(recipe);
        publishFiltered();
    }

    /**
     * Flips a recipe's visibility in the list immediately, deferring the actual server call the
     * same way {@link #deleteRecipe} defers a delete — see {@link #undoToggleVisibility}.
     *
     * @param recipe the recipe to toggle, as currently shown
     */
    public void toggleVisibility(RecipePreviewResponse recipe) {
        String recipeId = recipe.id();
        String newVisibility = "PUBLIC".equalsIgnoreCase(recipe.visibility()) ? "PRIVATE" : "PUBLIC";
        replaceRecipe(recipeId, withVisibility(recipe, newVisibility));
        publishFiltered();

        pendingActions.schedule(recipeId, UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<RecipeResponse>> result = new MutableLiveData<>();
            observeOnce(result, apiResult -> {
                if (apiResult instanceof ApiResult.Error<RecipeResponse>) {
                    replaceRecipe(recipeId, recipe);
                    publishFiltered();
                }
                visibilityResult.setValue(apiResult);
            });
            repository.updateRecipeVisibility(recipeId, newVisibility, result);
        });
    }

    /**
     * Cancels a still-pending visibility change and restores the recipe's original visibility.
     * Does nothing if the undo window already elapsed and the change reached the server.
     *
     * @param recipe the recipe to restore, as it looked before being toggled
     */
    public void undoToggleVisibility(RecipePreviewResponse recipe) {
        if (!pendingActions.cancel(recipe.id())) return;
        replaceRecipe(recipe.id(), recipe);
        publishFiltered();
    }

    /** Swaps the list entry with the given id for {@code replacement}, in place. */
    private void replaceRecipe(String recipeId, RecipePreviewResponse replacement) {
        int i = 0;
        while (i < allRecipes.size()) {
            if (Objects.equals(allRecipes.get(i).id(), recipeId)) {
                allRecipes.set(i, replacement);
                return;
            }
            i++;
        }
    }

    /** Copies {@code recipe} with only its {@code visibility} changed. */
    private static RecipePreviewResponse withVisibility(RecipePreviewResponse recipe, String visibility) {
        return new RecipePreviewResponse(recipe.id(), recipe.authorName(), recipe.title(), recipe.description(),
                recipe.difficulty(), visibility, recipe.prepTimeMinutes(), recipe.cookTimeMinutes(),
                recipe.reviewCount(), recipe.averageRating(), recipe.createdAt(), recipe.tags(),
                recipe.primaryImageUrl(), recipe.hasPersonalNote(), recipe.personalNoteText());
    }

    /**
     * Flushes any still-pending deletes/visibility changes immediately rather than dropping
     * them, so navigating away before the undo window elapses doesn't silently discard an
     * action the user never undid.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        pendingActions.flushAll();
    }

    /**
     * Filters and sorts {@link #allRecipes} per the active search/visibility/difficulty/tag/
     * sort state, publishing the result — {@link #allRecipes} itself is left untouched.
     */
    private void publishFiltered() {
        List<RecipePreviewResponse> displayed = new ArrayList<>(allRecipes);

        if (currentQuery != null) {
            String needle = currentQuery.toLowerCase(Locale.ROOT);
            displayed.removeIf(r -> {
                boolean titleMatch = r.title() != null && r.title().toLowerCase(Locale.ROOT).contains(needle);
                boolean descMatch = r.description() != null && r.description().toLowerCase(Locale.ROOT).contains(needle);
                return !titleMatch && !descMatch;
            });
        }
        if (!"ALL".equals(visibilityFilter)) {
            displayed.removeIf(r -> r.visibility() == null || !r.visibility().equalsIgnoreCase(visibilityFilter));
        }

        displayed = RecipeFilterUtils.applyFiltersAndSort(displayed, currentDifficulty, currentMinRating,
                currentMaxTotalTimeMinutes, selectedTags, currentSort);

        recipesResult.setValue(new ApiResult.Success<>(displayed));
    }
}
