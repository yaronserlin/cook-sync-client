package com.cooksync.app.ui.recipe;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.detail.RecipeDetailActivity;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.tags.TagResponse;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
                updateFilterButton(difficulty, tags);
            });
            dialog.show(getSupportFragmentManager(), "filters");
        });

        chipAll = addChip("All", true, () -> selectVisibility("ALL"));
        chipPublic = addChip("Public", false, () -> selectVisibility("PUBLIC"));
        chipPrivate = addChip("Private", false, () -> selectVisibility("PRIVATE"));
    }

    private void setupObservers() {
        viewModel.getRecipesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                showSkeleton(false);
                List<RecipePreviewResponse> recipes = success.getData();
                adapter.setRecipes(recipes);

                tvTitle.setText(getString(R.string.my_recipes_title_format, viewModel.getPublishedCount()));

                emptyState.setVisibility(recipes.isEmpty() ? View.VISIBLE : View.GONE);
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

        viewModel.getDeleteResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<Void>) {
                Toast.makeText(this, "Recipe deleted", Toast.LENGTH_SHORT).show();
                viewModel.loadMyRecipes();
            } else if (result instanceof ApiResult.Error<Void> error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getVisibilityResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<RecipeResponse> success) {
                boolean isPublic = "PUBLIC".equalsIgnoreCase(success.getData().visibility());
                Toast.makeText(this, isPublic ? "Recipe is now public" : "Recipe is now private", Toast.LENGTH_SHORT).show();
                viewModel.loadMyRecipes();
            } else if (result instanceof ApiResult.Error<RecipeResponse> error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
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

    private void showOptionsMenu(RecipePreviewResponse recipe, View anchor) {
        boolean isPublic = "PUBLIC".equalsIgnoreCase(recipe.visibility());

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_my_recipe_options, popup.getMenu());
        popup.getMenu().findItem(R.id.action_toggle_visibility)
                .setTitle(isPublic ? "Make private" : "Make public");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_toggle_visibility) {
                viewModel.toggleVisibility(recipe.id(), isPublic);
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
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this recipe?")
                .setMessage("\"" + recipe.title() + "\" will be permanently deleted. This can't be undone.")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteRecipe(recipe.id()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
