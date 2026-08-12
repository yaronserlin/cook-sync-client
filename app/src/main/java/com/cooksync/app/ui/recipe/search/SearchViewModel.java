package com.cooksync.app.ui.recipe.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.FeedState;
import com.cooksync.app.ui.base.AbstractFilterableListViewModel;
import com.cooksync.app.util.RecipeFilterUtils;
import com.dtos.response.PagedResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Manages the data state for the dedicated {@link SearchActivity}: running a keyword search
 * against the public recipe catalog, applying the same sort/difficulty/tags/rating/time
 * filters as the Home feed (via the shared {@code FiltersBottomSheetDialogFragment}), and
 * surfacing tag suggestions that match the in-progress query. Both the keyword search and the
 * tag-browse mode are paginated server-side, so results are fetched and displayed incrementally
 * as the results list is scrolled, exactly like {@code HomeViewModel}'s browse feed.
 *
 * @author Yaron Serlin
 * @version 1.2
 * @since 05/08/2026
 */
public class SearchViewModel extends AbstractFilterableListViewModel {

    private static final int PAGE_SIZE = 10;

    private final RecipeRepository recipeRepository;
    private final TagRepository tagRepository;

    private final MutableLiveData<FeedState> feedState = new MutableLiveData<>(new FeedState.Idle());
    private final MutableLiveData<ApiResult<List<TagResponse>>> tagsResult = new MutableLiveData<>();

    /** The raw, unfiltered results accumulated so far for the active query/tag — filters are applied over this. */
    private final List<RecipePreviewResponse> rawResults = new ArrayList<>();
    private List<TagResponse> allTags = Collections.emptyList();

    private int currentPage = 0;
    private boolean isLastPage = false;

    private String currentQuery = null;
    /** The tag currently being browsed via {@link #searchByTag}, or {@code null} for a keyword search. */
    private String browseTagName = null;

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param recipeRepository the repository used for search/tag-browse calls
     * @param tagRepository the repository used to load the tag catalog
     */
    public SearchViewModel(RecipeRepository recipeRepository, TagRepository tagRepository) {
        this.recipeRepository = recipeRepository;
        this.tagRepository = tagRepository;
    }

    public LiveData<FeedState> getFeedState() { return feedState; }
    public LiveData<ApiResult<List<TagResponse>>> getTagsResult() { return tagsResult; }

    /** @return the most recently submitted (non-blank) search query, or {@code null} */
    public String getCurrentQuery() { return currentQuery; }

    /**
     * Runs a keyword search against the public recipe catalog, resetting pagination. Resets
     * nothing about the active filters — clearing/re-running a search keeps whatever filters
     * were already active.
     *
     * @param query the search text
     */
    public void search(String query) {
        if (query == null || query.isBlank()) {
            currentQuery = null;
            browseTagName = null;
            rawResults.clear();
            feedState.setValue(new FeedState.Success(applyFiltersAndSort(rawResults), false));
            return;
        }
        currentQuery = query;
        browseTagName = null;
        resetAndFetch();
    }

    /**
     * Applies a "Matching tags" suggestion as a tag filter — browsing every recipe carrying
     * that tag, the same way {@code HomeViewModel}'s single-tag chip does — rather than as a
     * keyword search for the tag's name. Replaces any previous query/tag selection, since the
     * suggestion is meant to replace what the user was typing, not add to it.
     *
     * @param tagName the tag to browse by
     */
    public void searchByTag(String tagName) {
        currentQuery = null;
        browseTagName = tagName;
        selectedTags.clear();
        selectedTags.add(tagName);
        resetAndFetch();
    }

    /**
     * Fetches the next page of results if there are more available and no request is currently
     * in flight, for whichever mode (keyword search or tag-browse) is currently active.
     */
    public void loadNextPage() {
        if (isLastPage || feedState.getValue() instanceof FeedState.Loading) {
            return;
        }
        currentPage++;
        fetchPage();
    }

    /**
     * Resets pagination and the accumulated results, then fetches the first page for whichever
     * mode (keyword search or tag-browse) is currently active.
     */
    private void resetAndFetch() {
        currentPage = 0;
        isLastPage = false;
        rawResults.clear();
        fetchPage();
    }

    /**
     * Fetches the page at {@link #currentPage} from the endpoint matching the current mode and
     * merges it into {@link #rawResults}.
     */
    private void fetchPage() {
        feedState.setValue(new FeedState.Loading(currentPage == 0));
        MutableLiveData<ApiResult<PagedResponse<RecipePreviewResponse>>> result = new MutableLiveData<>();

        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<PagedResponse<RecipePreviewResponse>> success) {
                PagedResponse<RecipePreviewResponse> page = success.getData();
                rawResults.addAll(page.content());
                isLastPage = page.last();
                feedState.postValue(new FeedState.Success(applyFiltersAndSort(rawResults), !isLastPage));
            } else if (apiResult instanceof ApiResult.Error<PagedResponse<RecipePreviewResponse>> error) {
                feedState.postValue(new FeedState.Error(error.getMessage()));
            }
        });

        if (browseTagName != null) {
            recipeRepository.getRecipesByTag(browseTagName, currentPage, PAGE_SIZE, result);
        } else {
            recipeRepository.searchRecipes(currentQuery, currentPage, PAGE_SIZE, result);
        }
    }

    /**
     * Loads the full tag catalog, used to surface "Matching tags" suggestions as the user types.
     */
    public void loadTags() {
        MutableLiveData<ApiResult<List<TagResponse>>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<List<TagResponse>> success) {
                allTags = success.getData();
            }
            tagsResult.setValue(apiResult);
        });
        tagRepository.getAllTags(result);
    }

    /**
     * Returns up to 3 known tags whose name contains the in-progress query text, for the
     * "Matching tags" suggestion row shown while the user is typing.
     *
     * @param query the in-progress (not yet submitted) query text
     * @return matching tag names, best-effort ordered by tag catalog order
     */
    public List<String> getMatchingTagSuggestions(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return allTags.stream()
                .map(TagResponse::name)
                .filter(name -> name != null && name.toLowerCase(Locale.ROOT).contains(needle))
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * Re-filters {@link #rawResults} against the new filter state without issuing a new network
     * call — the search/tag-browse results already fetched so far are filtered client-side.
     */
    @Override
    protected void onFiltersChanged() {
        publishFiltered();
    }

    /**
     * Clears every active filter (not the search query itself) and re-filters.
     */
    public void clearAllFilters() {
        currentDifficulty = null;
        currentMinRating = null;
        currentMaxTotalTimeMinutes = null;
        selectedTags.clear();
        publishFiltered();
    }

    /**
     * Republishes {@link #rawResults} through the active filters without issuing a new network
     * call, preserving whatever {@code hasMore} state the last fetch established.
     */
    private void publishFiltered() {
        feedState.setValue(new FeedState.Success(applyFiltersAndSort(rawResults), !isLastPage));
    }

    /**
     * Filters {@code source} by the active difficulty/tag/rating/time selection and sorts it
     * per the active sort choice, via {@link RecipeFilterUtils#applyFiltersAndSort}.
     *
     * @param source the raw, unfiltered search results
     * @return a filtered, sorted copy
     */
    private List<RecipePreviewResponse> applyFiltersAndSort(List<RecipePreviewResponse> source) {
        return RecipeFilterUtils.applyFiltersAndSort(source, currentDifficulty, currentMinRating,
                currentMaxTotalTimeMinutes, selectedTags, currentSort);
    }
}
