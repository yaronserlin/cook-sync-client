package com.cooksync.app.ui.recipe.favorites;
import com.cooksync.app.ui.recipe.common.*;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.FilterSheetLauncher;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.common.NoResultsStateHelper;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.recipe.detail.RecipeDetailActivity;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists every recipe the current user has favorited, with search, sort/difficulty/tag
 * filtering, and a filter for favorites that carry a private note. Tapping the (always-filled)
 * heart on a card removes that recipe from favorites and offers an "Undo" toast to reverse it
 * (see {@link FavoritesViewModel#removeFavorite}). Uses the same shared list layout and row
 * card as {@link MyRecipesActivity}, differing only in data source, chips, and trailing action.
 *
 * @author Yaron Serlin
 * @version 1.2
 * @since 04/08/2026
 */
public class FavoriteRecipesActivity extends RecipeListActivity {

    private FavoritesViewModel viewModel;
    private RecipeRowCardAdapter adapter;
    private List<String> loadedTagNames = new ArrayList<>();

    private TextView chipAll;
    private TextView chipNotesOnly;

    @IdRes
    @Override
    protected int getSelectedNavItemId() {
        return R.id.nav_favorites;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(FavoritesViewModel.class);

        initViews();
        setupObservers();

        showSkeleton(true);
        viewModel.loadFavorites();
        viewModel.loadTags();
    }

    private void initViews() {
        tvTitle.setText(R.string.favorites_title);
        ivEmptyIcon.setImageResource(R.drawable.ic_heart_filled);
        tvEmptyTitle.setText(R.string.favorites_empty_title);
        tvEmptySubtitle.setText(R.string.favorites_empty_subtitle);
        searchView.setQueryHint(getString(R.string.favorites_search_hint));
        tvSubtitle.setVisibility(View.VISIBLE);

        adapter = new RecipeRowCardAdapter();
        adapter.setTrailingAction(RecipeRowCardAdapter.TrailingAction.FAVORITE_TOGGLE);
        adapter.setShowVisibilityBadge(false);
        adapter.setListener(new RecipeRowCardAdapter.Listener() {
            @Override
            public void onRecipeClick(RecipePreviewResponse recipe) {
                Intent intent = new Intent();
                intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id());
                Navigator.start(FavoriteRecipesActivity.this, RecipeDetailActivity.class, intent);
            }

            @Override
            public void onTrailingActionClick(RecipePreviewResponse recipe, View anchor) {
                viewModel.removeFavorite(recipe.id());
                OrganicToast.showWithAction(FavoriteRecipesActivity.this, bottomNav, R.drawable.ic_heart_outline,
                        getString(R.string.favorites_removed), getString(R.string.action_undo), () -> viewModel.undoRemoveFavorite(recipe));
            }
        });
        rvList.setAdapter(adapter);

        setupSearchListener(viewModel::search);

        btnFilters.setOnClickListener(v ->
                FilterSheetLauncher.show(getSupportFragmentManager(), loadedTagNames, viewModel,
                        (sortBy, difficulty, tags, minRating, maxTotalTimeMinutes) -> {
                            viewModel.applyFilters(sortBy, difficulty, tags, minRating, maxTotalTimeMinutes);
                            updateFilterButton();
                        }));

        chipAll = addChip(getString(R.string.filter_all), true, () -> selectNotesFilter(false));
        chipNotesOnly = addChip(getString(R.string.favorites_with_notes_chip_format, 0L), false, () -> selectNotesFilter(true));

        setOnClearAllClickListener(() -> {
            viewModel.applyFilters("Newest", null, new ArrayList<>(), null, null);
            searchView.setQuery("", true);
            updateFilterButton();
        });
    }

    private void setupObservers() {
        viewModel.getDisplayedResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                showSkeleton(false);
                List<RecipePreviewResponse> recipes = success.getData();
                adapter.setRecipes(recipes);

                tvSubtitle.setText(getString(R.string.favorites_subtitle_format,
                        viewModel.getTotalCount(), viewModel.getWithNotesCount()));
                chipNotesOnly.setText(getString(R.string.favorites_with_notes_chip_format, viewModel.getWithNotesCount()));
                updateFilterButton();

                if (!recipes.isEmpty()) {
                    hideNoResultsState();
                    emptyState.setVisibility(View.GONE);
                    rvList.setVisibility(View.VISIBLE);
                } else if (!viewModel.hasAnyFavorites()) {
                    // Genuinely no favorites yet — the static "No favorites yet" empty state.
                    emptyState.setVisibility(View.VISIBLE);
                    rvList.setVisibility(View.GONE);
                } else {
                    // Favorites exist, but the active search/filters matched none of them.
                    emptyState.setVisibility(View.GONE);
                    showNoResultsState(buildRemovableConstraints());
                }
            } else if (result instanceof ApiResult.Error<List<RecipePreviewResponse>> error) {
                showSkeleton(false);
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getTagsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<TagResponse>> success) {
                loadedTagNames = success.getData().stream().map(TagResponse::name).collect(java.util.stream.Collectors.toList());
            }
        });
    }

    private void selectNotesFilter(boolean onlyWithNotes) {
        viewModel.setOnlyWithNotes(onlyWithNotes);
        styleChip(chipAll, !onlyWithNotes);
        styleChip(chipNotesOnly, onlyWithNotes);
    }

    /**
     * Updates the "Filters · N" button to reflect the currently active non-default filters
     * (difficulty, tags, minimum rating, and/or total time — sort is ignored since one sort
     * option is always selected). Reads the active filters directly from {@link #viewModel}.
     */
    private void updateFilterButton() {
        int count = (viewModel.getCurrentDifficulty() != null ? 1 : 0)
                + viewModel.getSelectedTags().size()
                + (viewModel.getCurrentMinRating() != null ? 1 : 0)
                + (viewModel.getCurrentMaxTotalTimeMinutes() != null ? 1 : 0);
        boolean active = count > 0;

        btnFilters.setText(getString(R.string.filters_count_format, count));
        btnFilters.setBackgroundTintList(ColorStateList.valueOf(
                active ? getColor(R.color.color_accent) : getColor(R.color.color_neutral_300)));
        btnFilters.setTextColor(active ? getColor(R.color.color_bg) : getColor(R.color.color_text));

        ColorStateList tint = ColorStateList.valueOf(active ? getColor(R.color.color_bg) : getColor(R.color.color_accent));
        btnFilters.setIconTint(tint);
    }

    /**
     * Builds the list of currently active search/filter constraints for the no-results state,
     * mirroring {@code SearchActivity}'s equivalent.
     */
    private List<NoResultsStateHelper.Constraint> buildRemovableConstraints() {
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
                updateFilterButton();
            }));
        }
        for (String tag : viewModel.getSelectedTags()) {
            constraints.add(new NoResultsStateHelper.Constraint(tag, () -> {
                viewModel.removeTag(tag);
                updateFilterButton();
            }));
        }
        Integer maxTotalTimeMinutes = viewModel.getCurrentMaxTotalTimeMinutes();
        if (maxTotalTimeMinutes != null) {
            constraints.add(new NoResultsStateHelper.Constraint(
                    getString(R.string.filters_applied_time_format, maxTotalTimeMinutes), () -> {
                        viewModel.removeMaxTotalTime();
                        updateFilterButton();
                    }));
        }
        Double minRating = viewModel.getCurrentMinRating();
        if (minRating != null) {
            constraints.add(new NoResultsStateHelper.Constraint(
                    getString(R.string.filters_applied_rating_format, minRating), () -> {
                        viewModel.removeMinRating();
                        updateFilterButton();
                    }));
        }
        return constraints;
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadFavorites();
    }
}
