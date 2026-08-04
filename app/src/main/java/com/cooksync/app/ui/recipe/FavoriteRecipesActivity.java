package com.cooksync.app.ui.recipe;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.detail.RecipeDetailActivity;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists every recipe the current user has favorited, with search, sort/difficulty/tag
 * filtering, and a filter for favorites that carry a private note. Tapping the (always-filled)
 * heart on a card removes that recipe from favorites. Uses the same shared list layout and row
 * card as {@link MyRecipesActivity}, differing only in data source, chips, and trailing action.
 *
 * @author Yaron Serlin
 * @version 1.1
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

        viewModel = new ViewModelProvider(this).get(FavoritesViewModel.class);

        initViews();
        setupObservers();

        showSkeleton(true);
        viewModel.loadFavorites();
        viewModel.loadTags();
    }

    private void initViews() {
        tvTitle.setText("Favorites");
        ivEmptyIcon.setImageResource(R.drawable.ic_heart_filled);
        tvEmptyTitle.setText("No favorites yet");
        tvEmptySubtitle.setText("Tap the heart on any recipe. Your favorites keep private notes that nobody else can see.");
        searchView.setQueryHint("Search your favorites...");
        tvSubtitle.setVisibility(View.VISIBLE);

        adapter = new RecipeRowCardAdapter();
        adapter.setTrailingAction(RecipeRowCardAdapter.TrailingAction.FAVORITE_TOGGLE);
        adapter.setShowVisibilityBadge(false);
        adapter.setListener(new RecipeRowCardAdapter.Listener() {
            @Override
            public void onRecipeClick(RecipePreviewResponse recipe) {
                Intent intent = new Intent(FavoriteRecipesActivity.this, RecipeDetailActivity.class);
                intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id());
                startActivity(intent);
            }

            @Override
            public void onTrailingActionClick(RecipePreviewResponse recipe, View anchor) {
                viewModel.removeFavorite(recipe.id());
            }
        });
        rvList.setAdapter(adapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.search(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                viewModel.search(newText);
                return true;
            }
        });

        btnFilters.setOnClickListener(v -> {
            FiltersBottomSheetDialogFragment dialog = new FiltersBottomSheetDialogFragment();
            dialog.setAvailableTags(loadedTagNames);
            dialog.setInitialState(viewModel.getCurrentSort(), viewModel.getCurrentDifficulty(), viewModel.getSelectedTags());
            dialog.setOnFiltersAppliedListener((sortBy, difficulty, tags) -> {
                viewModel.applyFilters(sortBy, difficulty, tags);
                updateFilterButton(difficulty, tags);
            });
            dialog.show(getSupportFragmentManager(), "filters");
        });

        chipAll = addChip("All", true, () -> selectNotesFilter(false));
        chipNotesOnly = addChip("With notes 0", false, () -> selectNotesFilter(true));
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

                boolean hasAnyFavorites = viewModel.getTotalCount() > 0;
                emptyState.setVisibility(!hasAnyFavorites ? View.VISIBLE : View.GONE);
                rvList.setVisibility(recipes.isEmpty() ? View.GONE : View.VISIBLE);
            } else if (result instanceof ApiResult.Error<List<RecipePreviewResponse>> error) {
                showSkeleton(false);
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
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
     * (difficulty and/or however many tags are selected — sort isn't counted).
     */
    private void updateFilterButton(String difficulty, java.util.Collection<String> tags) {
        int count = (difficulty != null ? 1 : 0) + (tags == null ? 0 : tags.size());
        boolean active = count > 0;

        btnFilters.setText(getString(R.string.filters_count_format, count));
        btnFilters.setBackgroundResource(active ? R.drawable.bg_filters_active : R.drawable.bg_tag_neutral);
        btnFilters.setTextColor(active ? getColor(R.color.color_bg) : getColor(R.color.color_text));

        ColorStateList tint = ColorStateList.valueOf(active ? getColor(R.color.color_bg) : getColor(R.color.color_accent));
        btnFilters.setIconTint(tint);
    }
}
