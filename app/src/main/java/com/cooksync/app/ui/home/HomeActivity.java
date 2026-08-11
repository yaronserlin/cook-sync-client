package com.cooksync.app.ui.home;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.FeedState;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.common.AvatarView;
import com.cooksync.app.ui.common.FilterSheetLauncher;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.recipe.detail.RecipeDetailActivity;
import com.cooksync.app.ui.recipe.favorites.FavoriteRecipesActivity;
import com.cooksync.app.ui.recipe.myrecipes.MyRecipesActivity;
import com.cooksync.app.ui.recipe.search.SearchActivity;
import com.cooksync.app.ui.settings.AccountDetailsActivity;
import com.cooksync.app.ui.settings.SettingsActivity;
import com.cooksync.app.util.SessionManager;
import com.dtos.response.tags.TagResponse;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main entry point of the app after login. Displays a paginated feed of recipes and
 * tag-based filtering. Tapping the search field navigates to the dedicated
 * {@link com.cooksync.app.ui.recipe.search.SearchActivity} rather than filtering in place.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class HomeActivity extends BaseActivity {

    private HomeViewModel viewModel;
    private RecipeCardAdapter recipeAdapter;
    private TagChipAdapter tagAdapter;

    private RecyclerView rvFeed;
    private View noResultsState;
    private ChipGroup cgRemovableConstraints;
    private View btnClearAll;
    private List<String> loadedTagNames = new ArrayList<>();
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(HomeViewModel.class);

        initViews();
        setupAdapters();
        setupObservers();

        viewModel.loadTags();

        findViewById(R.id.btn_filters).setOnClickListener(v ->
                FilterSheetLauncher.show(getSupportFragmentManager(), loadedTagNames, viewModel,
                        (sortBy, difficulty, tags, minRating, maxTotalTimeMinutes) -> {
                            viewModel.applyFilters(sortBy, difficulty, tags, minRating, maxTotalTimeMinutes);
                            tagAdapter.setSelectedTags(viewModel.getSelectedTags());
                        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AvatarView avatarView = findViewById(R.id.avatar_view_profile);
        if (avatarView != null) {
            avatarView.setAvatar(SessionManager.getInstance().getAvatarUrl(), SessionManager.getInstance().getDisplayName());
        }
        viewModel.loadInitialFeed();
        viewModel.loadFavorites();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    private View emptyStateView;

    private void initViews() {
        rvFeed = findViewById(R.id.rv_feed);
        noResultsState = findViewById(R.id.no_results_state);
        emptyStateView = findViewById(R.id.empty_state);
        cgRemovableConstraints = noResultsState.findViewById(R.id.cg_removable_constraints);
        btnClearAll = noResultsState.findViewById(R.id.btn_clear_all);
        btnClearAll.setOnClickListener(v -> {
            viewModel.applyFilters("Newest", null, Collections.emptyList(), null, null);
            tagAdapter.setSelectedTags(viewModel.getSelectedTags());
        });

        setupSkeleton(R.id.skeleton_view);

        AvatarView avatarView = findViewById(R.id.avatar_view_profile);
        avatarView.setAvatar(SessionManager.getInstance().getAvatarUrl(), SessionManager.getInstance().getDisplayName());
        avatarView.setOnClickListener(v -> Navigator.start(this, AccountDetailsActivity.class));

        findViewById(R.id.search_bar_tap_target).setOnClickListener(v ->
                Navigator.start(this, SearchActivity.newIntentWithFilters(this, viewModel)));

        findViewById(R.id.fab_add_recipe).setOnClickListener(v ->
                Navigator.start(this, com.cooksync.app.ui.recipe.wizard.AddRecipeWizardActivity.class));

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_my_recipes) {
                Navigator.start(this, MyRecipesActivity.class);
                return true;
            }
            if (id == R.id.nav_favorites) {
                Navigator.start(this, FavoriteRecipesActivity.class);
                return true;
            }
            if (id == R.id.nav_settings) {
                Navigator.start(this, SettingsActivity.class);
                return true;
            }
            return false;
        });
    }

    private void setupAdapters() {
        recipeAdapter = new RecipeCardAdapter();
        recipeAdapter.setOnRecipeClickListener(new RecipeCardAdapter.OnRecipeClickListener() {
            @Override
            public void onRecipeClick(String recipeId) {
                Intent intent = new Intent();
                intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId);
                Navigator.start(HomeActivity.this, RecipeDetailActivity.class, intent);
            }

            @Override
            public void onFavoriteClick(String recipeId, boolean wasFavorite) {
                viewModel.toggleFavorite(recipeId);
                if (!wasFavorite) {
                    showSuccess(getString(R.string.favorites_added), bottomNav);
                } else {
                    OrganicToast.showWithAction(HomeActivity.this, bottomNav, R.drawable.ic_heart_outline,
                            getString(R.string.favorites_removed), getString(R.string.action_undo), () -> viewModel.undoRemoveFavorite(recipeId));
                }
            }
        });
        rvFeed.setAdapter(recipeAdapter);

        rvFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(1)) {
                    viewModel.loadNextPage();
                }
            }
        });

        RecyclerView rvTags = findViewById(R.id.rv_tags);
        tagAdapter = new TagChipAdapter();
        tagAdapter.setOnTagClickListener(tagName -> {
            if (tagName == null) {
                viewModel.clearTags();
            } else {
                viewModel.toggleTag(tagName);
            }
            tagAdapter.setSelectedTags(viewModel.getSelectedTags());
        });
        rvTags.setAdapter(tagAdapter);
    }

    private void setupObservers() {
        viewModel.getFeedState().observe(this, state -> {
            if (state instanceof FeedState.Loading loading) {
                if (loading.isInitial()) {
                    showSkeleton(true, rvFeed);
                }
            } else if (state instanceof FeedState.Success success) {
                showSkeleton(false, rvFeed);
                recipeAdapter.setRecipes(success.getRecipes());
                updateFilterButton();
                updateNoResultsState(success.getRecipes().isEmpty());
            } else if (state instanceof FeedState.Error error) {
                showSkeleton(false, rvFeed);
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getTagsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<TagResponse>> success) {
                tagAdapter.setTags(success.getData());
                loadedTagNames = success.getData().stream().map(TagResponse::name).collect(Collectors.toList());
            }
        });

        viewModel.getFavoritesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<com.dtos.response.recipe.RecipePreviewResponse>> success) {
                recipeAdapter.setFavorites(success.getData());
            }
        });

        viewModel.getErrorEvent().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                showError(message, bottomNav);
            }
        });
    }

    /**
     * Updates the "Filters · N" button to reflect the currently active non-default filters
     * (difficulty, tags, minimum rating, and/or total time — sort is ignored since one sort
     * option is always selected), and the summary line naming exactly which ones. Reads the
     * active filters directly from {@link #viewModel}.
     *
     * <p>Uses {@code setBackgroundTintList} rather than swapping the button's background
     * drawable, since a MaterialButton whose background was overwritten with a raw drawable
     * stops going through Material's own shape/tint redraw pipeline and can fail to repaint
     * on some devices.</p>
     */
    private void updateFilterButton() {
        String difficulty = viewModel.getCurrentDifficulty();
        java.util.Set<String> tags = viewModel.getSelectedTags();
        Double minRating = viewModel.getCurrentMinRating();
        Integer maxTotalTimeMinutes = viewModel.getCurrentMaxTotalTimeMinutes();

        int count = (difficulty != null ? 1 : 0) + tags.size()
                + (minRating != null ? 1 : 0) + (maxTotalTimeMinutes != null ? 1 : 0);
        boolean active = count > 0;

        com.google.android.material.button.MaterialButton btn = findViewById(R.id.btn_filters);
        btn.setText(getString(R.string.filters_count_format, count));
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                active ? getColor(R.color.color_accent) : getColor(R.color.color_neutral_300)));
        btn.setTextColor(active ? getColor(R.color.color_bg) : getColor(R.color.color_text));

        android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(
                active ? getColor(R.color.color_bg) : getColor(R.color.color_accent));
        btn.setIconTint(tint);

        TextView summary = findViewById(R.id.tv_active_filters_summary);
        if (!active) {
            summary.setVisibility(View.GONE);
            return;
        }
        List<String> parts = new ArrayList<>();
        if (difficulty != null) parts.add(difficulty);
        parts.addAll(tags);
        if (maxTotalTimeMinutes != null) {
            parts.add(getString(R.string.filters_applied_time_format, maxTotalTimeMinutes));
        }
        if (minRating != null) {
            parts.add(getString(R.string.filters_applied_rating_format, minRating));
        }
        summary.setText(getString(R.string.filters_applied_summary_format, String.join(" · ", parts)));
        summary.setVisibility(View.VISIBLE);
    }

    @Override
    protected void showSkeleton(boolean show, View contentView) {
        super.showSkeleton(show, contentView);
        View searchTarget = findViewById(R.id.search_bar_tap_target);
        View filterButton = findViewById(R.id.btn_filters);
        View tagsList = findViewById(R.id.rv_tags);
        if (searchTarget != null) searchTarget.setVisibility(show ? View.GONE : View.VISIBLE);
        if (filterButton != null) filterButton.setVisibility(show ? View.GONE : View.VISIBLE);
        if (tagsList != null) tagsList.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * Shows the no-results state (with one removable chip per active filter) instead of the
     * feed when the currently active filters match no recipes. A no-op when nothing is
     * filtered, since a genuinely empty feed isn't this screen's concern.
     *
     * @param feedIsEmpty whether the just-published feed page is empty
     */
    private void updateNoResultsState(boolean feedIsEmpty) {
        String difficulty = viewModel.getCurrentDifficulty();
        java.util.Set<String> tags = viewModel.getSelectedTags();
        Double minRating = viewModel.getCurrentMinRating();
        Integer maxTotalTimeMinutes = viewModel.getCurrentMaxTotalTimeMinutes();
        boolean hasActiveFilters = difficulty != null || !tags.isEmpty()
                || minRating != null || maxTotalTimeMinutes != null;

        if (!feedIsEmpty) {
            noResultsState.setVisibility(View.GONE);
            if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
            rvFeed.setVisibility(View.VISIBLE);
            return;
        }

        if (!hasActiveFilters) {
            noResultsState.setVisibility(View.GONE);
            rvFeed.setVisibility(View.GONE);
            if (emptyStateView != null) emptyStateView.setVisibility(View.VISIBLE);
            return;
        }

        if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
        rvFeed.setVisibility(View.GONE);
        List<com.cooksync.app.ui.common.NoResultsStateHelper.Constraint> constraints = new ArrayList<>();
        if (difficulty != null) {
            constraints.add(new com.cooksync.app.ui.common.NoResultsStateHelper.Constraint(difficulty, () -> {
                viewModel.applyFilters(viewModel.getCurrentSort(), null, tags, minRating, maxTotalTimeMinutes);
                tagAdapter.setSelectedTags(viewModel.getSelectedTags());
            }));
        }
        for (String tag : tags) {
            constraints.add(new com.cooksync.app.ui.common.NoResultsStateHelper.Constraint(tag, () -> {
                java.util.Set<String> remaining = new java.util.LinkedHashSet<>(tags);
                remaining.remove(tag);
                viewModel.applyFilters(viewModel.getCurrentSort(), difficulty, remaining, minRating, maxTotalTimeMinutes);
                tagAdapter.setSelectedTags(viewModel.getSelectedTags());
            }));
        }
        if (maxTotalTimeMinutes != null) {
            constraints.add(new com.cooksync.app.ui.common.NoResultsStateHelper.Constraint(
                    getString(R.string.filters_applied_time_format, maxTotalTimeMinutes), () -> {
                        viewModel.applyFilters(viewModel.getCurrentSort(), difficulty, tags, minRating, null);
                        tagAdapter.setSelectedTags(viewModel.getSelectedTags());
                    }));
        }
        if (minRating != null) {
            constraints.add(new com.cooksync.app.ui.common.NoResultsStateHelper.Constraint(
                    getString(R.string.filters_applied_rating_format, minRating), () -> {
                        viewModel.applyFilters(viewModel.getCurrentSort(), difficulty, tags, null, maxTotalTimeMinutes);
                        tagAdapter.setSelectedTags(viewModel.getSelectedTags());
                    }));
        }
        com.cooksync.app.ui.common.NoResultsStateHelper.populate(getLayoutInflater(), cgRemovableConstraints, btnClearAll, constraints);
        noResultsState.setVisibility(View.VISIBLE);
    }
}
