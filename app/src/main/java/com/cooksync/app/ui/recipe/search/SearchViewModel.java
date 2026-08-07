package com.cooksync.app.ui.recipe.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.BaseViewModel;
import com.cooksync.app.ui.common.FilterSheetLauncher;
import com.cooksync.app.util.RecipeFilterUtils;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the data state for the dedicated {@link SearchActivity}: running a keyword search
 * against the public recipe catalog, applying the same sort/difficulty/tags/rating/time
 * filters as the Home feed (via the shared {@code FiltersBottomSheetDialogFragment}), and
 * surfacing tag suggestions that match the in-progress query.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public class SearchViewModel extends BaseViewModel implements FilterSheetLauncher.FilterState {

    private final RecipeRepository recipeRepository;
    private final TagRepository tagRepository;

    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> searchResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<TagResponse>>> tagsResult = new MutableLiveData<>();

    /** The raw, unfiltered results of the last network search — filters are applied over this. */
    private List<RecipePreviewResponse> rawResults = new ArrayList<>();
    private List<TagResponse> allTags = Collections.emptyList();

    private String currentQuery = null;
    private String currentSort = "Newest";
    private String currentDifficulty = null;
    private Double currentMinRating = null;
    private Integer currentMaxTotalTimeMinutes = null;
    private final Set<String> selectedTags = new LinkedHashSet<>();

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.common.ViewModelFactory}.
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

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getSearchResult() { return searchResult; }
    public LiveData<ApiResult<List<TagResponse>>> getTagsResult() { return tagsResult; }
    public String getCurrentSort() { return currentSort; }
    public String getCurrentDifficulty() { return currentDifficulty; }
    public Double getCurrentMinRating() { return currentMinRating; }
    public Integer getCurrentMaxTotalTimeMinutes() { return currentMaxTotalTimeMinutes; }
    public Set<String> getSelectedTags() { return Collections.unmodifiableSet(selectedTags); }

    /** @return the most recently submitted (non-blank) search query, or {@code null} */
    public String getCurrentQuery() { return currentQuery; }

    /**
     * Runs a keyword search against the public recipe catalog. Resets nothing about the active
     * filters — clearing/re-running a search keeps whatever filters were already active.
     *
     * @param query the search text
     */
    public void search(String query) {
        if (query == null || query.isBlank()) {
            currentQuery = null;
            rawResults = new ArrayList<>();
            searchResult.setValue(new ApiResult.Success<>(rawResults));
            return;
        }
        currentQuery = query;
        searchResult.setValue(new ApiResult.Loading<>());
        MutableLiveData<ApiResult<List<RecipePreviewResponse>>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                rawResults = success.getData();
                searchResult.setValue(new ApiResult.Success<>(applyFiltersAndSort(rawResults)));
            } else {
                searchResult.setValue(apiResult);
            }
        });
        recipeRepository.searchRecipes(query, result);
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
        selectedTags.clear();
        selectedTags.add(tagName);
        searchResult.setValue(new ApiResult.Loading<>());
        MutableLiveData<ApiResult<List<RecipePreviewResponse>>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                rawResults = success.getData();
                searchResult.setValue(new ApiResult.Success<>(applyFiltersAndSort(rawResults)));
            } else {
                searchResult.setValue(apiResult);
            }
        });
        recipeRepository.getRecipesByTag(tagName, result);
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
     * Applies the sort/difficulty/tags/rating/time chosen in the shared filters sheet, re-
     * filtering over the last search's raw results rather than issuing a new network call.
     *
     * @param sortBy one of "Newest", "Top Rated", "Shortest Time"
     * @param difficulty one of "Easy", "Medium", "Hard", or {@code null}
     * @param tags the selected tag names, possibly empty
     * @param minRating minimum average rating threshold, or {@code null}
     * @param maxTotalTimeMinutes maximum prep+cook time in minutes, or {@code null}
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
        searchResult.setValue(new ApiResult.Success<>(applyFiltersAndSort(rawResults)));
    }

    /**
     * Drops the active difficulty filter alone and re-filters, leaving every other constraint
     * (query, tags, rating, time) untouched — lets the no-results state offer per-constraint
     * removal instead of only an all-or-nothing reset.
     */
    public void removeDifficulty() {
        currentDifficulty = null;
        searchResult.setValue(new ApiResult.Success<>(applyFiltersAndSort(rawResults)));
    }

    /**
     * Drops a single selected tag alone and re-filters.
     *
     * @param tagName the tag to remove from {@link #selectedTags}
     */
    public void removeTag(String tagName) {
        if (selectedTags.remove(tagName)) {
            searchResult.setValue(new ApiResult.Success<>(applyFiltersAndSort(rawResults)));
        }
    }

    /**
     * Drops the active minimum-rating filter alone and re-filters.
     */
    public void removeMinRating() {
        currentMinRating = null;
        searchResult.setValue(new ApiResult.Success<>(applyFiltersAndSort(rawResults)));
    }

    /**
     * Drops the active total-time filter alone and re-filters.
     */
    public void removeMaxTotalTime() {
        currentMaxTotalTimeMinutes = null;
        searchResult.setValue(new ApiResult.Success<>(applyFiltersAndSort(rawResults)));
    }

    /**
     * Clears every active filter (not the search query itself) and re-filters.
     */
    public void clearAllFilters() {
        currentDifficulty = null;
        currentMinRating = null;
        currentMaxTotalTimeMinutes = null;
        selectedTags.clear();
        searchResult.setValue(new ApiResult.Success<>(applyFiltersAndSort(rawResults)));
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
