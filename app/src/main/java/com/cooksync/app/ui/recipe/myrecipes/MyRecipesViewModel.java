package com.cooksync.app.ui.recipe.myrecipes;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.data.service.RecipePublishManager;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.base.AbstractFilterableListViewModel;
import com.cooksync.app.util.PendingActionScheduler;
import com.cooksync.app.util.RecipeFilterUtils;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Manages data state for {@link MyRecipesActivity}: the current user's own recipes, search/
 * sort/filter over that list (client-side, since {@code GET /api/recipes/mine} returns the
 * whole set unpaginated), and the outcome of management actions (delete, visibility toggle)
 * triggered from the list.
 *
 * @author Yaron Serlin
 * @version 1.2
 * @since 04/08/2026
 */
public class MyRecipesViewModel extends AbstractFilterableListViewModel {

    private final RecipeRepository repository;
    private final TagRepository tagRepository;

    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> recipesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<TagResponse>>> tagsResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> deleteResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<RecipeResponse>> visibilityResult = new MutableLiveData<>();

    private final List<RecipePreviewResponse> allRecipes = new ArrayList<>();

    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private String currentQuery = null;
    /** One of "ALL", "PUBLIC", "PRIVATE". */
    private String visibilityFilter = "ALL";

    /**
     * Kept as a field so it can be detached in {@link #onCleared()} — {@link RecipePublishManager}
     * is a process-wide singleton, so an observer registered via {@code observeForever} and never
     * removed would keep this ViewModel (and everything it references) alive indefinitely.
     */
    private final Observer<Event<RecipeResponse>> recipePublishedObserver = event -> {
        if (event != null && event.getContentIfNotHandled() != null) {
            loadMyRecipes();
        }
    };

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
        RecipePublishManager.getInstance().getRecipePublishedEvent().observeForever(recipePublishedObserver);
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

    public String getVisibilityFilter() { return visibilityFilter; }

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
     * Re-filters {@link #allRecipes} against the new filter state — this list is already fully
     * loaded client-side, so no new network call is needed.
     */
    @Override
    protected void onFiltersChanged() {
        publishFiltered();
    }

    /**
     * Deletes a recipe: waits for the server call to succeed before removing it from the list,
     * then publishes the outcome via {@link #getDeleteResult()}. Unlike {@link #toggleVisibility},
     * this is not optimistic — the recipe stays visible until the server confirms the delete.
     *
     * @param recipe the recipe to delete, as currently shown
     */
    public void deleteRecipe(RecipePreviewResponse recipe) {
        String recipeId = recipe.id();
        MutableLiveData<ApiResult<Void>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<Void>) {
                allRecipes.removeIf(r -> Objects.equals(r.id(), recipeId));
                publishFiltered();
            }
            deleteResult.postValue(apiResult);
        });
        repository.deleteRecipe(recipeId, result);
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

        pendingActions.schedule(recipeId, BaseRepository.UNDO_WINDOW_MS, () -> {
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
        RecipePublishManager.getInstance().getRecipePublishedEvent().removeObserver(recipePublishedObserver);
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
