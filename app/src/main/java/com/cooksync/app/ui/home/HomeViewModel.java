package com.cooksync.app.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.RecipeRepositoryImpl;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.data.repository.TagRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.domain.FeedState;
import com.cooksync.app.util.PendingActionScheduler;
import com.dtos.response.PagedResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages the data state for the {@link HomeActivity}, including paginated recipe
 * feed loading and tag/difficulty/rating/time filtering. Keyword search lives on the
 * dedicated {@link com.cooksync.app.ui.recipe.SearchActivity} instead.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class HomeViewModel extends ViewModel {

    private static final int PAGE_SIZE = 10;

    /**
     * How long an "add to favorites" waits before actually reaching the server, giving the
     * "Undo" toast action a window to cancel it. Matches {@code OrganicToast}'s auto-dismiss
     * duration, since the undo action stops being reachable once the toast itself is gone.
     */
    private static final long UNDO_WINDOW_MS = 3200;

    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private final RecipeRepository recipeRepository;
    private final TagRepository tagRepository;

    private final MutableLiveData<FeedState> feedState = new MutableLiveData<>(new FeedState.Idle());
    private final MutableLiveData<ApiResult<List<TagResponse>>> tagsResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> favoritesResult = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> errorEvent = new MutableLiveData<>();

    private final List<RecipePreviewResponse> currentRecipes = new ArrayList<>();
    private final Set<String> selectedTags = new LinkedHashSet<>();
    private int currentPage = 0;
    private boolean isLastPage = false;
    private String currentSort = "Newest";
    private String currentDifficulty = null;
    private Double currentMinRating = null;
    private Integer currentMaxTotalTimeMinutes = null;

    public HomeViewModel() {
        this.recipeRepository = new RecipeRepositoryImpl();
        this.tagRepository = new TagRepositoryImpl();
    }

    public LiveData<FeedState> getFeedState() {
        return feedState;
    }

    public LiveData<ApiResult<List<TagResponse>>> getTagsResult() {
        return tagsResult;
    }

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getFavoritesResult() {
        return favoritesResult;
    }

    /**
     * One-off error notifications (e.g. a failed favorite toggle) meant to be shown once
     * (a Toast/Snackbar) rather than re-delivered on every observer re-attachment.
     *
     * @return the error event stream
     */
    public LiveData<Event<String>> getErrorEvent() {
        return errorEvent;
    }

    /**
     * Resets pagination, then reloads the feed from the first page. The active
     * sort/difficulty/tag selections are left untouched.
     */
    public void loadInitialFeed() {
        refresh();
    }

    /**
     * Fetches the next page of recipes if there are more available and no request
     * is currently in flight. A no-op whenever the current view (search results, or a
     * single-tag filter) was fetched in full rather than paginated.
     */
    public void loadNextPage() {
        if (isLastPage || feedState.getValue() instanceof FeedState.Loading) {
            return;
        }
        currentPage++;
        fetchNextPage();
    }

    /**
     * Toggles a single tag's membership in {@link #selectedTags} (multi-select) and refreshes
     * the feed. Unlike the old single-tag model, this never clears sort/difficulty or the
     * other selected tags.
     *
     * @param tagName the tag to toggle on/off
     */
    public void toggleTag(String tagName) {
        if (tagName == null) {
            return;
        }
        if (!selectedTags.remove(tagName)) {
            selectedTags.add(tagName);
        }
        refresh();
    }

    /**
     * Clears every selected tag (the Home tag row's "All" chip) and refreshes the feed.
     */
    public void clearTags() {
        if (selectedTags.isEmpty()) {
            return;
        }
        selectedTags.clear();
        refresh();
    }

    /**
     * An unmodifiable snapshot of the currently selected tag names, for the tag-row highlight
     * and the active-filters summary.
     */
    public Set<String> getSelectedTags() {
        return Collections.unmodifiableSet(selectedTags);
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

    /**
     * Applies the sort/difficulty/tags chosen in the filters sheet and reloads the feed from
     * the first page so the new criteria take effect immediately.
     *
     * <p>The server's {@code /public/paged}, {@code /public/search} and
     * {@code /public/tag/{tag}} endpoints don't accept sort, difficulty, or multi-tag
     * parameters, so filtering and sorting happen client-side, over whatever recipes have been
     * loaded so far (see {@link #applyFiltersAndSort}). Server-side support for these would let
     * filtering cover the full catalog immediately instead of only the pages loaded via
     * scrolling.</p>
     *
     * @param sortBy one of "Newest", "Top Rated", "Shortest Time"
     * @param difficulty one of "Easy", "Medium", "Hard", or {@code null} for no filter
     * @param tags the selected tag names (possibly empty, never {@code null})
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
        refresh();
    }

    public void loadTags() {
        tagRepository.getAllTags(tagsResult);
    }

    public void loadFavorites() {
        recipeRepository.getFavorites(favoritesResult);
    }

    /**
     * Resets pagination and re-runs whichever fetch matches the current mode: a single-tag
     * full fetch (the one case the server can answer without pagination gaps), or the general
     * paginated browse feed. Multi-tag selections (2+) fall back to the paginated feed with
     * client-side filtering, since there is no multi-tag server endpoint.
     */
    private void refresh() {
        currentPage = 0;
        isLastPage = false;
        currentRecipes.clear();

        if (selectedTags.size() == 1) {
            runSingleTagFetch(selectedTags.iterator().next());
        } else {
            fetchNextPage();
        }
    }

    private void runSingleTagFetch(String tagName) {
        feedState.setValue(new FeedState.Loading(true));
        MutableLiveData<ApiResult<List<RecipePreviewResponse>>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                currentRecipes.addAll(success.getData());
                // The tag-filter endpoint is not paginated server-side; it returns every match at once.
                isLastPage = true;
                feedState.postValue(new FeedState.Success(applyFiltersAndSort(currentRecipes), false));
            } else if (apiResult instanceof ApiResult.Error<List<RecipePreviewResponse>> error) {
                feedState.postValue(new FeedState.Error(error.getMessage()));
            }
        });
        recipeRepository.getRecipesByTag(tagName, result);
    }

    private void fetchNextPage() {
        feedState.setValue(new FeedState.Loading(currentPage == 0));
        MutableLiveData<ApiResult<PagedResponse<RecipePreviewResponse>>> result = new MutableLiveData<>();

        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<PagedResponse<RecipePreviewResponse>> success) {
                PagedResponse<RecipePreviewResponse> page = success.getData();
                currentRecipes.addAll(page.content());
                isLastPage = page.last();
                feedState.postValue(new FeedState.Success(applyFiltersAndSort(currentRecipes), !isLastPage));
            } else if (apiResult instanceof ApiResult.Error<PagedResponse<RecipePreviewResponse>> error) {
                feedState.postValue(new FeedState.Error(error.getMessage()));
            }
        });

        recipeRepository.getPublicFeed(currentPage, PAGE_SIZE, result);
    }

    /**
     * Filters {@code source} by the active difficulty/tag selection and sorts it per the
     * active sort choice, returning a new list — {@code source} itself (the raw accumulated
     * page cache) is left untouched so further pagination keeps working against the full set.
     * A recipe must carry <em>every</em> selected tag (AND, not OR) to remain in the result.
     *
     * @param source the raw, unfiltered recipes accumulated so far
     * @return a filtered, sorted copy ready to display
     */
    private List<RecipePreviewResponse> applyFiltersAndSort(List<RecipePreviewResponse> source) {
        List<RecipePreviewResponse> displayed = new ArrayList<>(source);

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

        return displayed;
    }

    /**
     * Optimistically toggles a recipe's favorite state in {@link #favoritesResult}. Removing a
     * favorite is sent immediately, same as before; adding one is deferred by
     * {@link #UNDO_WINDOW_MS} instead, so a tap on the toast's "Undo" action (see
     * {@link #undoAddFavorite}) can cancel it before it's ever sent. If a remove is requested
     * while its matching add is still pending, the pending add is simply cancelled rather than
     * sending a remove for something the server never received. If a server call that does go
     * out fails, the optimistic change is rolled back and {@link #errorEvent} is emitted so the
     * UI can inform the user — the heart icon never claims a state the server didn't confirm.
     *
     * @param recipeId the id of the recipe to favorite/unfavorite
     */
    public void toggleFavorite(String recipeId) {
        List<RecipePreviewResponse> previous =
                favoritesResult.getValue() instanceof ApiResult.Success<List<RecipePreviewResponse>> success
                        ? new ArrayList<>(success.getData())
                        : new ArrayList<>();

        boolean isFavorite = previous.stream().anyMatch(r -> r.id().equals(recipeId));

        if (isFavorite) {
            List<RecipePreviewResponse> withoutRecipe = new ArrayList<>(previous);
            withoutRecipe.removeIf(r -> r.id().equals(recipeId));
            favoritesResult.setValue(new ApiResult.Success<>(withoutRecipe));

            if (pendingActions.cancel(recipeId)) {
                return;
            }
            MutableLiveData<ApiResult<Void>> writeResult = new MutableLiveData<>();
            observeOnce(writeResult, result -> {
                if (result instanceof ApiResult.Error<Void> error) {
                    favoritesResult.setValue(new ApiResult.Success<>(previous));
                    errorEvent.setValue(new Event<>(error.getMessage()));
                }
            });
            recipeRepository.removeFavorite(recipeId, writeResult);
        } else {
            List<RecipePreviewResponse> withRecipe = new ArrayList<>(previous);
            currentRecipes.stream()
                    .filter(r -> r.id().equals(recipeId))
                    .findFirst()
                    .ifPresent(withRecipe::add);
            favoritesResult.setValue(new ApiResult.Success<>(withRecipe));

            pendingActions.schedule(recipeId, UNDO_WINDOW_MS, () -> {
                MutableLiveData<ApiResult<Void>> writeResult = new MutableLiveData<>();
                observeOnce(writeResult, result -> {
                    if (result instanceof ApiResult.Error<Void> error) {
                        favoritesResult.setValue(new ApiResult.Success<>(previous));
                        errorEvent.setValue(new Event<>(error.getMessage()));
                    }
                });
                recipeRepository.addFavorite(recipeId, writeResult);
            });
        }
    }

    /**
     * Cancels a still-pending "add to favorites" and restores the pre-add state. Does nothing
     * if the undo window already elapsed and the add reached the server.
     *
     * @param recipeId the id of the recipe whose favorite-add should be undone
     */
    public void undoAddFavorite(String recipeId) {
        if (!pendingActions.cancel(recipeId)) {
            return;
        }
        List<RecipePreviewResponse> current =
                favoritesResult.getValue() instanceof ApiResult.Success<List<RecipePreviewResponse>> success
                        ? new ArrayList<>(success.getData())
                        : new ArrayList<>();
        current.removeIf(r -> r.id().equals(recipeId));
        favoritesResult.setValue(new ApiResult.Success<>(current));
    }

    /**
     * Flushes any still-pending favorite adds immediately rather than dropping them, so
     * navigating away before the undo window elapses doesn't silently discard an add the user
     * never undid.
     */
    @Override
    protected void onCleared() {
        pendingActions.flushAll();
    }

    /**
     * Attaches a self-removing observer to a one-shot repository call: skips the initial
     * {@link ApiResult.Loading} emission, invokes {@code onSettled} for the terminal
     * Success/Error value, then detaches itself so the short-lived {@code liveData} instance
     * and this lambda can be garbage collected. Centralizes the observe-once pattern used by
     * every fire-and-forget repository call in this ViewModel.
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
