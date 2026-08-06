package com.cooksync.app.ui.recipe;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.RecipeRepositoryImpl;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.data.repository.TagRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.util.PendingActionScheduler;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

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
public class MyRecipesViewModel extends ViewModel {

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
    private final Set<String> selectedTags = new LinkedHashSet<>();

    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private String currentQuery = null;
    /** One of "ALL", "PUBLIC", "PRIVATE". */
    private String visibilityFilter = "ALL";
    private String currentSort = "Newest";
    private String currentDifficulty = null;
    private Double currentMinRating = null;
    private Integer currentMaxTotalTimeMinutes = null;

    public MyRecipesViewModel() {
        this.repository = new RecipeRepositoryImpl();
        this.tagRepository = new TagRepositoryImpl();
    }

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getRecipesResult() {
        return recipesResult;
    }

    public LiveData<ApiResult<List<TagResponse>>> getTagsResult() {
        return tagsResult;
    }

    /**
     * Fires only when a deferred delete actually reaches the server and fails (see
     * {@link #deleteRecipe}) — a successful delete needs no signal here since the list already
     * reflects it optimistically, and an undone delete never reaches the server at all.
     */
    public LiveData<ApiResult<Void>> getDeleteResult() {
        return deleteResult;
    }

    /**
     * Fires only when a deferred visibility change actually reaches the server and fails (see
     * {@link #toggleVisibility}), for the same reason as {@link #getDeleteResult()}.
     */
    public LiveData<ApiResult<RecipeResponse>> getVisibilityResult() {
        return visibilityResult;
    }

    public String getCurrentSort() {
        return currentSort;
    }

    public String getCurrentDifficulty() {
        return currentDifficulty;
    }

    public Double getCurrentMinRating() {
        return currentMinRating;
    }

    public Integer getCurrentMaxTotalTimeMinutes() {
        return currentMaxTotalTimeMinutes;
    }

    public String getVisibilityFilterValue() {
        return visibilityFilter;
    }

    public Set<String> getSelectedTags() {
        return Collections.unmodifiableSet(selectedTags);
    }

    /**
     * How many of the user's recipes (across the whole library, ignoring the active search/
     * filter) are public — used for the screen's "My recipes · N published" title, which
     * should reflect the real total, not just whatever the current filter happens to show.
     */
    public long getPublishedCount() {
        return allRecipes.stream().filter(r -> "PUBLIC".equalsIgnoreCase(r.visibility())).count();
    }

    /** {@code true} once the user's recipe library has loaded and contains at least one recipe. */
    public boolean hasAnyRecipes() {
        return !allRecipes.isEmpty();
    }

    /** The active search text, or {@code null} if none is set. */
    public String getCurrentQuery() {
        return currentQuery;
    }

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
        allRecipes.removeIf(r -> r.id().equals(recipeId));
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
        if (!pendingActions.cancel(recipe.id())) {
            return;
        }
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
        if (!pendingActions.cancel(recipe.id())) {
            return;
        }
        replaceRecipe(recipe.id(), recipe);
        publishFiltered();
    }

    /** Swaps the list entry with the given id for {@code replacement}, in place. */
    private void replaceRecipe(String recipeId, RecipePreviewResponse replacement) {
        for (int i = 0; i < allRecipes.size(); i++) {
            if (allRecipes.get(i).id().equals(recipeId)) {
                allRecipes.set(i, replacement);
                return;
            }
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
        if (currentDifficulty != null) {
            displayed.removeIf(r -> r.difficulty() == null || !r.difficulty().equalsIgnoreCase(currentDifficulty));
        }
        if (currentMinRating != null) {
            displayed.removeIf(r -> r.averageRating() == null || r.averageRating() < currentMinRating);
        }
        if (currentMaxTotalTimeMinutes != null) {
            displayed.removeIf(r -> (r.prepTimeMinutes() + r.cookTimeMinutes()) > currentMaxTotalTimeMinutes);
        }
        if (!selectedTags.isEmpty()) {
            displayed.removeIf(r -> r.tags() == null || !selectedTags.stream().allMatch(selected ->
                    r.tags().stream().anyMatch(tag -> tag.name() != null && tag.name().equalsIgnoreCase(selected))));
        }

        Comparator<RecipePreviewResponse> comparator = switch (currentSort == null ? "" : currentSort) {
            case "Top Rated" -> Comparator.comparing(
                    (RecipePreviewResponse r) -> r.averageRating() == null ? 0.0 : r.averageRating(),
                    Comparator.reverseOrder());
            case "Shortest Time" -> Comparator.comparingInt(
                    r -> r.prepTimeMinutes() + r.cookTimeMinutes());
            default -> Comparator.comparing(
                    (RecipePreviewResponse r) -> r.createdAt() == null ? "" : r.createdAt(),
                    Comparator.reverseOrder());
        };
        displayed.sort(comparator);

        recipesResult.setValue(new ApiResult.Success<>(displayed));
    }

    /**
     * Attaches a self-removing observer to a one-shot repository call: skips the initial
     * {@link ApiResult.Loading} emission, invokes {@code onSettled} for the terminal
     * Success/Error value, then detaches itself.
     *
     * @param <T> the payload type carried by the result
     * @param liveData the one-shot result stream to observe
     * @param onSettled callback invoked with the first non-Loading value
     */
    private <T> void observeOnce(MutableLiveData<ApiResult<T>> liveData, Consumer<ApiResult<T>> onSettled) {
        liveData.observeForever(new Observer<>() {
            @Override
            public void onChanged(ApiResult<T> value) {
                if (value instanceof ApiResult.Loading) {
                    return;
                }
                liveData.removeObserver(this);
                onSettled.accept(value);
            }
        });
    }
}
