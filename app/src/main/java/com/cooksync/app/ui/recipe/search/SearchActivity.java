package com.cooksync.app.ui.recipe.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.BaseActivity;
import com.cooksync.app.ui.common.FilterSheetLauncher;
import com.cooksync.app.ui.common.Navigator;
import com.cooksync.app.ui.common.NoResultsStateHelper;
import com.cooksync.app.ui.common.ViewModelFactory;
import com.cooksync.app.ui.home.TagChipAdapter;
import com.cooksync.app.ui.recipe.detail.RecipeDetailActivity;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dedicated recipe search screen, reached by tapping the search field on {@link
 * com.cooksync.app.ui.home.HomeActivity}. Runs a keyword search against the public recipe
 * catalog, surfaces matching tag suggestions while typing, supports the same sort/difficulty/
 * tags/rating/time filters as Home via the shared {@link com.cooksync.app.ui.recipe.FiltersBottomSheetDialogFragment},
 * and displays results in the design's compact row format.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public class SearchActivity extends BaseActivity {

    /** How long to wait after the last keystroke before running a live search. */
    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private static final String EXTRA_SORT = "extra_sort";
    private static final String EXTRA_DIFFICULTY = "extra_difficulty";
    private static final String EXTRA_TAGS = "extra_tags";
    private static final String EXTRA_MIN_RATING = "extra_min_rating";
    private static final String EXTRA_MAX_TOTAL_TIME_MINUTES = "extra_max_total_time_minutes";

    /**
     * Builds an {@link Intent} to this screen carrying {@code filters}' current sort/
     * difficulty/tags/rating/time, so a screen the viewer already filtered (e.g. Home) hands
     * that state off instead of Search silently resetting to its own defaults.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context the calling screen's context
     * @param filters the filter state to carry over, typically the calling screen's ViewModel
     * @return an intent to {@link SearchActivity}, pre-seeded with {@code filters}
     */
    public static Intent newIntentWithFilters(Context context, FilterSheetLauncher.FilterState filters) {
        Intent intent = new Intent(context, SearchActivity.class);
        intent.putExtra(EXTRA_SORT, filters.getCurrentSort());
        intent.putExtra(EXTRA_DIFFICULTY, filters.getCurrentDifficulty());
        intent.putStringArrayListExtra(EXTRA_TAGS, new ArrayList<>(filters.getSelectedTags()));
        if (filters.getCurrentMinRating() != null) {
            intent.putExtra(EXTRA_MIN_RATING, filters.getCurrentMinRating());
        }
        if (filters.getCurrentMaxTotalTimeMinutes() != null) {
            intent.putExtra(EXTRA_MAX_TOTAL_TIME_MINUTES, filters.getCurrentMaxTotalTimeMinutes());
        }
        return intent;
    }

    private SearchViewModel viewModel;
    private SearchResultAdapter recipeAdapter;
    private TagChipAdapter matchingTagsAdapter;
    private List<String> loadedTagNames = new ArrayList<>();

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private SearchView searchView;
    private RecyclerView rvResults;
    private View matchingTagsSection;
    private TextView tvResultsSummary;
    private TextView tvEmptyState;
    private View noResultsState;
    private com.google.android.material.chip.ChipGroup cgRemovableConstraints;
    private View btnClearAll;
    private TextView tvFiltersBadge;
    private View progress;
    private boolean hasSearched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(SearchViewModel.class);

        initViews();
        setupAdapters();
        setupObservers();
        seedFiltersFromIntent();

        viewModel.loadTags();
        searchView.setIconified(false);
        searchView.requestFocus();
    }

    /**
     * Applies whatever filter state {@link #newIntentWithFilters} attached to the launching
     * intent, if any, so filters chosen on the calling screen are already active by the time
     * the viewer runs their first search here. A no-op if this screen was reached without a
     * seeded intent (e.g. tapped in some future entry point that doesn't have prior filters).
     */
    private void seedFiltersFromIntent() {
        String sort = getIntent().getStringExtra(EXTRA_SORT);
        if (sort == null) {
            return;
        }
        String difficulty = getIntent().getStringExtra(EXTRA_DIFFICULTY);
        List<String> tags = getIntent().getStringArrayListExtra(EXTRA_TAGS);
        Double minRating = getIntent().hasExtra(EXTRA_MIN_RATING)
                ? getIntent().getDoubleExtra(EXTRA_MIN_RATING, 0) : null;
        Integer maxTotalTimeMinutes = getIntent().hasExtra(EXTRA_MAX_TOTAL_TIME_MINUTES)
                ? getIntent().getIntExtra(EXTRA_MAX_TOTAL_TIME_MINUTES, 0) : null;

        viewModel.applyFilters(sort, difficulty, tags, minRating, maxTotalTimeMinutes);
        updateFiltersBadge();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                cancelPendingSearch();
                runSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                cancelPendingSearch();
                if (newText.isEmpty()) {
                    matchingTagsSection.setVisibility(View.GONE);
                    runSearch(null);
                } else {
                    updateMatchingTags(newText);
                    // Live search-as-you-type, debounced so every keystroke doesn't fire a
                    // network call — only the pause after the user stops typing does.
                    pendingSearch = () -> runSearch(newText);
                    searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
                }
                return false;
            }
        });

        findViewById(R.id.btn_filters).setOnClickListener(v ->
                FilterSheetLauncher.show(getSupportFragmentManager(), loadedTagNames, viewModel,
                        (sortBy, difficulty, tags, minRating, maxTotalTimeMinutes) -> {
                            viewModel.applyFilters(sortBy, difficulty, tags, minRating, maxTotalTimeMinutes);
                            updateFiltersBadge();
                        }));

        matchingTagsSection = findViewById(R.id.matching_tags_section);
        rvResults = findViewById(R.id.rv_results);
        tvResultsSummary = findViewById(R.id.tv_results_summary);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        noResultsState = findViewById(R.id.no_results_state);
        cgRemovableConstraints = noResultsState.findViewById(R.id.cg_removable_constraints);
        btnClearAll = noResultsState.findViewById(R.id.btn_clear_all);
        tvFiltersBadge = findViewById(R.id.tv_filters_badge);
        progress = findViewById(R.id.progress);

        btnClearAll.setOnClickListener(v -> {
            viewModel.clearAllFilters();
            updateFiltersBadge();
            searchView.setQuery("", true);
        });
    }

    /**
     * Cancels a not-yet-fired debounced search, if one is pending.
     */
    private void cancelPendingSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    /**
     * Runs (or clears) a search and updates {@link #hasSearched} plus the "Matching tags" row.
     *
     * @param query the query text, or {@code null}/blank to clear the search
     */
    private void runSearch(String query) {
        hasSearched = query != null && !query.isBlank();
        if (!hasSearched) matchingTagsSection.setVisibility(View.GONE);
        viewModel.search(query);
    }

    /**
     * Updates the small numeric badge on the filters icon to reflect how many filter
     * dimensions (difficulty, tags, rating, time) are currently active.
     */
    private void updateFiltersBadge() {
        int count = (viewModel.getCurrentDifficulty() != null ? 1 : 0)
                + viewModel.getSelectedTags().size()
                + (viewModel.getCurrentMinRating() != null ? 1 : 0)
                + (viewModel.getCurrentMaxTotalTimeMinutes() != null ? 1 : 0);
        if (count > 0) {
            tvFiltersBadge.setText(String.valueOf(count));
            tvFiltersBadge.setVisibility(View.VISIBLE);
        } else {
            tvFiltersBadge.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPendingSearch();
    }

    private void setupAdapters() {
        recipeAdapter = new SearchResultAdapter();
        recipeAdapter.setOnRecipeClickListener(recipeId -> {
            Intent intent = new Intent();
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId);
            Navigator.start(SearchActivity.this, RecipeDetailActivity.class, intent);
        });
        rvResults.setAdapter(recipeAdapter);
        rvResults.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));

        RecyclerView rvMatchingTags = findViewById(R.id.rv_matching_tags);
        matchingTagsAdapter = new TagChipAdapter(false);
        // Tapping a matching-tag suggestion applies it as a tag filter (like picking it in the
        // Filters sheet), rather than dropping the tag name into the search box as if it were a
        // keyword — the tag is a filter, not search text.
        matchingTagsAdapter.setOnTagClickListener(tagName -> {
            cancelPendingSearch();
            matchingTagsSection.setVisibility(View.GONE);
            searchView.setQuery("", false);
            hasSearched = true;
            viewModel.searchByTag(tagName);
            updateFiltersBadge();
        });
        rvMatchingTags.setAdapter(matchingTagsAdapter);
    }

    private void setupObservers() {
        viewModel.getSearchResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
                tvEmptyState.setVisibility(View.GONE);
                noResultsState.setVisibility(View.GONE);
            } else if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                progress.setVisibility(View.GONE);
                List<RecipePreviewResponse> recipes = success.getData();
                recipeAdapter.setRecipes(recipes);
                updateSummaryAndEmptyState(recipes);
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                showError(error.getMessage(), null);
            }
        });

        viewModel.getTagsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<TagResponse>> success) {
                loadedTagNames = success.getData().stream().map(TagResponse::name).collect(Collectors.toList());
            }
        });
    }

    /**
     * Refreshes the "Matching tags" suggestion row for the in-progress query text.
     *
     * @param query the text currently typed into the search field
     */
    private void updateMatchingTags(String query) {
        List<String> matches = viewModel.getMatchingTagSuggestions(query);
        if (matches.isEmpty()) {
            matchingTagsSection.setVisibility(View.GONE);
            return;
        }
        matchingTagsAdapter.setTags(matches.stream()
                .map(name -> new TagResponse(name, name, null, null))
                .collect(Collectors.toList()));
        matchingTagsSection.setVisibility(View.VISIBLE);
    }

    /**
     * Shows the results summary (query/filters/sort feedback) and toggles between the
     * RecyclerView, the initial search prompt, and the no-results state — the latter always
     * shown for a zero-result search, whether it's the query or the active filters causing it.
     *
     * @param recipes the current (filtered) result set
     */
    private void updateSummaryAndEmptyState(List<RecipePreviewResponse> recipes) {
        boolean hasResults = !recipes.isEmpty();
        rvResults.setVisibility(hasResults ? View.VISIBLE : View.GONE);

        if (!hasSearched) {
            tvResultsSummary.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText(R.string.search_empty_prompt);
            noResultsState.setVisibility(View.GONE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);

        if (hasResults) {
            tvResultsSummary.setVisibility(View.VISIBLE);
            tvResultsSummary.setText(buildResultsSummary(recipes.size()));
            noResultsState.setVisibility(View.GONE);
            return;
        }

        // Zero results: always show the same dedicated no-results state (never a bare shrug,
        // and never a different layout for "query caused it" vs. "filters caused it") — every
        // active constraint (query and/or filters) is offered as its own removable chip.
        tvResultsSummary.setVisibility(View.GONE);
        noResultsState.setVisibility(View.VISIBLE);
        populateRemovableConstraints();
    }

    /**
     * Builds the list of currently active constraints (the search query, difficulty, each
     * selected tag, minimum rating, total time) and hands it to {@link NoResultsStateHelper} to
     * render as removable chips.
     */
    private void populateRemovableConstraints() {
        List<NoResultsStateHelper.Constraint> constraints = new ArrayList<>();

        String query = viewModel.getCurrentQuery();
        if (query != null) {
            constraints.add(new NoResultsStateHelper.Constraint(
                    "\"" + query + "\"", () -> searchView.setQuery("", true)));
        }
        String difficulty = viewModel.getCurrentDifficulty();
        if (difficulty != null) {
            constraints.add(new NoResultsStateHelper.Constraint(difficulty, () -> {
                viewModel.removeDifficulty();
                updateFiltersBadge();
            }));
        }
        for (String tag : viewModel.getSelectedTags()) {
            constraints.add(new NoResultsStateHelper.Constraint(tag, () -> {
                viewModel.removeTag(tag);
                updateFiltersBadge();
            }));
        }
        Integer maxTotalTimeMinutes = viewModel.getCurrentMaxTotalTimeMinutes();
        if (maxTotalTimeMinutes != null) {
            constraints.add(new NoResultsStateHelper.Constraint(
                    getString(R.string.filters_applied_time_format, maxTotalTimeMinutes), () -> {
                        viewModel.removeMaxTotalTime();
                        updateFiltersBadge();
                    }));
        }
        Double minRating = viewModel.getCurrentMinRating();
        if (minRating != null) {
            constraints.add(new NoResultsStateHelper.Constraint(
                    getString(R.string.filters_applied_rating_format, minRating), () -> {
                        viewModel.removeMinRating();
                        updateFiltersBadge();
                    }));
        }

        NoResultsStateHelper.populate(getLayoutInflater(), cgRemovableConstraints, btnClearAll, constraints);
    }

    /**
     * Builds the "N recipes [for "query"] · sorted by X [· filters]" summary line, naming
     * every dimension currently shaping the result set so the user always knows what's active.
     *
     * @param count how many recipes are currently displayed
     * @return the summary text to show above the results
     */
    private String buildResultsSummary(int count) {
        String countPhrase = count == 1 ? getString(R.string.count_1_recipe) : getString(R.string.count_n_recipes, count);
        String query = viewModel.getCurrentQuery();

        List<String> activeFilters = new ArrayList<>();
        if (viewModel.getCurrentDifficulty() != null) activeFilters.add(viewModel.getCurrentDifficulty());
        activeFilters.addAll(viewModel.getSelectedTags());
        if (viewModel.getCurrentMaxTotalTimeMinutes() != null) activeFilters.add(getString(R.string.filters_applied_time_format, viewModel.getCurrentMaxTotalTimeMinutes()));
        if (viewModel.getCurrentMinRating() != null) activeFilters.add(getString(R.string.filters_applied_rating_format, viewModel.getCurrentMinRating()));

        if (!activeFilters.isEmpty()) {
            String base = query != null
                    ? getString(R.string.search_results_summary_with_query_format, countPhrase, query, viewModel.getCurrentSort())
                    : getString(R.string.search_results_summary_format, countPhrase, viewModel.getCurrentSort());
            return base + " · " + String.join(", ", activeFilters);
        }
        return query != null
                ? getString(R.string.search_results_summary_with_query_format, countPhrase, query, viewModel.getCurrentSort())
                : getString(R.string.search_results_summary_format, countPhrase, viewModel.getCurrentSort());
    }
}
