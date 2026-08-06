package com.cooksync.app.ui.recipe;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.NoResultsStateHelper;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.detail.RecipeDetailActivity;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists every recipe (published or private) the current user has authored, with search,
 * sort/difficulty/tag filtering, a Public/Private chip filter, and per-recipe management
 * actions (toggle visibility, delete) via an overflow menu.
 *
 * @author Yaron Serlin
 * @version 1.2
 * @since 04/08/2026
 */
public class MyRecipesActivity extends RecipeListActivity {

    private MyRecipesViewModel viewModel;
    private RecipeRowCardAdapter adapter;
    private List<String> loadedTagNames = new ArrayList<>();

    private TextView chipAll;
    private TextView chipPublic;
    private TextView chipPrivate;

    @IdRes
    @Override
    protected int getSelectedNavItemId() {
        return R.id.nav_my_recipes;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MyRecipesViewModel.class);

        initViews();
        setupObservers();

        showSkeleton(true);
        viewModel.loadMyRecipes();
        viewModel.loadTags();
    }

    private void initViews() {
        ivEmptyIcon.setImageResource(R.drawable.ic_book);
        tvEmptyTitle.setText("No recipes yet");
        tvEmptySubtitle.setText("Recipes you publish will show up here.");
        searchView.setQueryHint("Search your recipes...");

        adapter = new RecipeRowCardAdapter();
        adapter.setTrailingAction(RecipeRowCardAdapter.TrailingAction.OPTIONS_MENU);
        adapter.setShowVisibilityBadge(true);
        adapter.setListener(new RecipeRowCardAdapter.Listener() {
            @Override
            public void onRecipeClick(RecipePreviewResponse recipe) {
                Intent intent = new Intent(MyRecipesActivity.this, RecipeDetailActivity.class);
                intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id());
                startActivity(intent);
            }

            @Override
            public void onTrailingActionClick(RecipePreviewResponse recipe, View anchor) {
                showOptionsMenu(recipe, anchor);
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
            dialog.setInitialState(viewModel.getCurrentSort(), viewModel.getCurrentDifficulty(), viewModel.getSelectedTags(),
                    viewModel.getCurrentMinRating(), viewModel.getCurrentMaxTotalTimeMinutes());
            dialog.setOnFiltersAppliedListener((sortBy, difficulty, tags, minRating, maxTotalTimeMinutes) -> {
                viewModel.applyFilters(sortBy, difficulty, tags, minRating, maxTotalTimeMinutes);
                updateFilterButton();
            });
            dialog.show(getSupportFragmentManager(), "filters");
        });

        chipAll = addChip("All", true, () -> selectVisibility("ALL"));
        chipPublic = addChip("Public", false, () -> selectVisibility("PUBLIC"));
        chipPrivate = addChip("Private", false, () -> selectVisibility("PRIVATE"));

        setOnClearAllClickListener(() -> {
            viewModel.applyFilters("Newest", null, new ArrayList<>(), null, null);
            searchView.setQuery("", true);
            updateFilterButton();
        });
    }

    private void setupObservers() {
        viewModel.getRecipesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                showSkeleton(false);
                List<RecipePreviewResponse> recipes = success.getData();
                adapter.setRecipes(recipes);

                tvTitle.setText(getString(R.string.my_recipes_title_format, viewModel.getPublishedCount()));

                if (!recipes.isEmpty()) {
                    hideNoResultsState();
                    emptyState.setVisibility(View.GONE);
                    rvList.setVisibility(View.VISIBLE);
                } else if (!viewModel.hasAnyRecipes()) {
                    // Genuinely no recipes yet — the static "No recipes yet" empty state.
                    emptyState.setVisibility(View.VISIBLE);
                    rvList.setVisibility(View.GONE);
                } else {
                    // Recipes exist, but the active search/filters matched none of them.
                    emptyState.setVisibility(View.GONE);
                    showNoResultsState(buildRemovableConstraints());
                }
            } else if (result instanceof ApiResult.Error<List<RecipePreviewResponse>> error) {
                showSkeleton(false);
                OrganicToast.showError(this, bottomNav, error.getMessage());
            }
        });

        viewModel.getTagsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<TagResponse>> success) {
                loadedTagNames = success.getData().stream().map(TagResponse::name).collect(java.util.stream.Collectors.toList());
            }
        });

        // Both results now only fire for a deferred call that reached the server and failed —
        // a success needs no signal since the list already reflects it optimistically, and the
        // "Recipe deleted"/"is now public/private" toast (with its Undo action) is shown
        // immediately from confirmDelete()/showOptionsMenu() instead of from here.
        viewModel.getDeleteResult().observe(this, result -> {
            if (result instanceof ApiResult.Error<Void> error) {
                OrganicToast.showError(this, bottomNav, error.getMessage());
            }
        });

        viewModel.getVisibilityResult().observe(this, result -> {
            if (result instanceof ApiResult.Error<RecipeResponse> error) {
                OrganicToast.showError(this, bottomNav, error.getMessage());
            }
        });
    }

    private void selectVisibility(String visibility) {
        viewModel.setVisibilityFilter(visibility);
        styleChip(chipAll, "ALL".equals(visibility));
        styleChip(chipPublic, "PUBLIC".equals(visibility));
        styleChip(chipPrivate, "PRIVATE".equals(visibility));
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

    private void showOptionsMenu(RecipePreviewResponse recipe, View anchor) {
        boolean isPublic = "PUBLIC".equalsIgnoreCase(recipe.visibility());

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_my_recipe_options, popup.getMenu());
        popup.getMenu().findItem(R.id.action_toggle_visibility)
                .setTitle(isPublic ? "Make private" : "Make public");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_toggle_visibility) {
                viewModel.toggleVisibility(recipe);
                String message = isPublic ? "Recipe is now private" : "Recipe is now public";
                OrganicToast.showWithAction(this, bottomNav, 0, message, "Undo",
                        () -> viewModel.undoToggleVisibility(recipe));
                return true;
            }
            if (id == R.id.action_delete_recipe) {
                confirmDelete(recipe);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(RecipePreviewResponse recipe) {
        OrganicConfirmDialog.show(this, "Delete this recipe?",
                "\"" + recipe.title() + "\" will be permanently deleted. This can't be undone.",
                "Delete", "Cancel", true, () -> {
                    viewModel.deleteRecipe(recipe);
                    OrganicToast.showWithAction(this, bottomNav, R.drawable.ic_delete, "Recipe deleted", "Undo",
                            () -> viewModel.undoDeleteRecipe(recipe));
                });
    }
}
