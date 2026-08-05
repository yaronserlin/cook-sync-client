package com.cooksync.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.FeedState;
import com.cooksync.app.ui.common.SkeletonHelper;
import com.cooksync.app.ui.recipe.FiltersBottomSheetDialogFragment;
import com.cooksync.app.ui.recipe.MyRecipesActivity;
import com.cooksync.app.util.SessionManager;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main entry point of the app after login. Displays a paginated feed of recipes and
 * tag-based filtering. Tapping the search field navigates to the dedicated
 * {@link com.cooksync.app.ui.recipe.SearchActivity} rather than filtering in place.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class HomeActivity extends AppCompatActivity {

    private HomeViewModel viewModel;
    private RecipeCardAdapter recipeAdapter;
    private TagChipAdapter tagAdapter;
    private SkeletonHelper skeletonHelper;

    private View skeletonView;
    private RecyclerView rvFeed;
    private List<String> loadedTagNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        
        initViews();
        setupAdapters();
        setupObservers();

        viewModel.loadInitialFeed();
        viewModel.loadTags();
        viewModel.loadFavorites();

        findViewById(R.id.btn_filters).setOnClickListener(v -> {
                FiltersBottomSheetDialogFragment dialog = new FiltersBottomSheetDialogFragment();
                dialog.setAvailableTags(loadedTagNames);
                dialog.setInitialState(viewModel.getCurrentSort(), viewModel.getCurrentDifficulty(), viewModel.getSelectedTags(),
                        viewModel.getCurrentMinRating(), viewModel.getCurrentMaxTotalTimeMinutes());
                dialog.setOnFiltersAppliedListener((sortBy, difficulty, tags, minRating, maxTotalTimeMinutes) -> {
                    viewModel.applyFilters(sortBy, difficulty, tags, minRating, maxTotalTimeMinutes);
                    tagAdapter.setSelectedTags(viewModel.getSelectedTags());
                });
                dialog.show(getSupportFragmentManager(), "filters");
        });
    }

    private void initViews() {
        rvFeed = findViewById(R.id.rv_feed);
        skeletonView = findViewById(R.id.skeleton_view);

        skeletonHelper = new SkeletonHelper();
        skeletonHelper.attachAll(findViewById(R.id.skeleton_view));

        android.widget.TextView avatar = findViewById(R.id.tv_profile_avatar);
        avatar.setText(SessionManager.getInstance().getInitials());
        avatar.setOnClickListener(v ->
                startActivity(new Intent(this, com.cooksync.app.ui.profile.ProfileActivity.class)));

        findViewById(R.id.search_bar_tap_target).setOnClickListener(v ->
                startActivity(new Intent(this, com.cooksync.app.ui.recipe.SearchActivity.class)));

        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            }
            if (id == R.id.nav_my_recipes) {
                startActivity(new Intent(this, MyRecipesActivity.class));
                return true;
            }
            if (id == R.id.nav_favorites) {
                startActivity(new Intent(this, com.cooksync.app.ui.recipe.FavoriteRecipesActivity.class));
                return true;
            }
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, com.cooksync.app.ui.profile.ProfileActivity.class));
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
                Intent intent = new Intent(HomeActivity.this, com.cooksync.app.ui.detail.RecipeDetailActivity.class);
                intent.putExtra(com.cooksync.app.ui.detail.RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId);
                startActivity(intent);
            }

            @Override
            public void onFavoriteClick(String recipeId) {
                viewModel.toggleFavorite(recipeId);
            }
        });
        rvFeed.setAdapter(recipeAdapter);

        // Infinite scroll
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
                    showSkeleton(true);
                }
            } else if (state instanceof FeedState.Success success) {
                showSkeleton(false);
                recipeAdapter.setRecipes(success.getRecipes());
                updateFilterButton();
            } else if (state instanceof FeedState.Error error) {
                showSkeleton(false);
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getTagsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<java.util.List<TagResponse>> success) {
                tagAdapter.setTags(success.getData());
                loadedTagNames = success.getData().stream().map(TagResponse::name).collect(Collectors.toList());
            }
        });

        viewModel.getFavoritesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<java.util.List<com.dtos.response.recipe.RecipePreviewResponse>> success) {
                recipeAdapter.setFavorites(success.getData());
            }
        });

        viewModel.getErrorEvent().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Updates the "Filters · N" button to reflect the currently active non-default filters
     * (difficulty, tags, minimum rating, and/or total time — sort is ignored since one sort
     * option is always selected). Reads the active filters directly from {@link #viewModel}.
     *
     * <p>Uses {@code setBackgroundResource} rather than {@code setBackgroundColor} so the
     * button's rounded-pill shape (defined by the drawable, not by MaterialButton's own shape
     * appearance once a raw background is set) is preserved instead of being replaced by a
     * flat {@code ColorDrawable} rectangle.</p>
     */
    private void updateFilterButton() {
        int count = (viewModel.getCurrentDifficulty() != null ? 1 : 0)
                + viewModel.getSelectedTags().size()
                + (viewModel.getCurrentMinRating() != null ? 1 : 0)
                + (viewModel.getCurrentMaxTotalTimeMinutes() != null ? 1 : 0);
        boolean active = count > 0;

        com.google.android.material.button.MaterialButton btn = findViewById(R.id.btn_filters);
        btn.setText(getString(R.string.filters_count_format, count));
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                active ? getColor(R.color.color_accent) : getColor(R.color.color_neutral_300)));
        btn.setTextColor(active ? getColor(R.color.color_bg) : getColor(R.color.color_text));

        android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(
                active ? getColor(R.color.color_bg) : getColor(R.color.color_accent));
        btn.setIconTint(tint);
    }

    private void showSkeleton(boolean show) {
        if (show) {
            skeletonView.setVisibility(View.VISIBLE);
            rvFeed.setVisibility(View.GONE);
            skeletonHelper.start();
        } else {
            skeletonHelper.stop();
            skeletonView.setVisibility(View.GONE);
            rvFeed.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        skeletonHelper.release();
    }
}
