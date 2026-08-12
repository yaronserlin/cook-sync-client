package com.cooksync.app.ui.auth;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.AvatarView;
import com.cooksync.app.ui.recipe.common.RecipeRowCardAdapter;
import com.cooksync.app.ui.recipe.detail.RecipeDetailActivity;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.user.UserResponse;

import java.util.List;

/**
 * Full-screen page displaying another user's public profile: avatar, name, city, bio, and — when
 * the viewed user's privacy preferences allow it — a "Recipes" section (their published recipes)
 * and a "Favorites" section (the recipes they've favorited), each row clickable through to
 * {@link RecipeDetailActivity}. Replaces the earlier {@code UserProfileDialogFragment} popup,
 * which had no room for these list sections.
 *
 * <p>Both sections are gated purely by what {@link UserProfileViewModel#loadProfile} reports for
 * this specific user ({@code showRecipesPublicly}/{@code showFavoritesPublicly}); the server
 * enforces the same gating independently, so this page renders correctly even if reached with a
 * stale cached name.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class UserProfileActivity extends BaseActivity {

    /** Intent extra: the ID of the user whose profile should be displayed. */
    public static final String EXTRA_USER_ID = "extra_user_id";
    /** Intent extra: display name to show immediately while the full profile loads, optional. */
    public static final String EXTRA_USER_NAME = "extra_user_name";

    private UserProfileViewModel viewModel;

    private AvatarView avatarView;
    private TextView tvFullName;
    private TextView tvCity;
    private TextView tvBio;
    private ProgressBar progressBar;

    private View sectionRecipes;
    private RecyclerView rvRecipes;
    private TextView tvRecipesEmpty;
    private RecipeRowCardAdapter recipesAdapter;

    private View sectionFavorites;
    private RecyclerView rvFavorites;
    private TextView tvFavoritesEmpty;
    private RecipeRowCardAdapter favoritesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(UserProfileViewModel.class);

        bindViews();
        setupLists();
        findViewById(R.id.btn_back).setOnClickListener(v -> Navigator.finish(this));

        String userId = getIntent().getStringExtra(EXTRA_USER_ID);
        String initialName = getIntent().getStringExtra(EXTRA_USER_NAME);
        if (initialName != null && !initialName.isBlank()) {
            tvFullName.setText(initialName);
            avatarView.setAvatar(null, initialName);
        }

        if (userId != null && !userId.isBlank()) {
            observeProfile();
            observeRecipes();
            observeFavorites();
            viewModel.loadProfile(userId);
        }
    }

    private void bindViews() {
        avatarView = findViewById(R.id.avatar_view);
        tvFullName = findViewById(R.id.tv_full_name);
        tvCity = findViewById(R.id.tv_city);
        tvBio = findViewById(R.id.tv_bio);
        progressBar = findViewById(R.id.progress_bar);

        sectionRecipes = findViewById(R.id.section_recipes);
        rvRecipes = findViewById(R.id.rv_recipes);
        tvRecipesEmpty = findViewById(R.id.tv_recipes_empty);

        sectionFavorites = findViewById(R.id.section_favorites);
        rvFavorites = findViewById(R.id.rv_favorites);
        tvFavoritesEmpty = findViewById(R.id.tv_favorites_empty);
    }

    /**
     * Wires both recipe lists to the shared {@link RecipeRowCardAdapter}, read-only (no trailing
     * action, no visibility badge — meaningless here since only the target's own public recipes
     * are ever shown), each row navigating to {@link RecipeDetailActivity} on tap.
     */
    private void setupLists() {
        recipesAdapter = new RecipeRowCardAdapter();
        recipesAdapter.setTrailingAction(RecipeRowCardAdapter.TrailingAction.NONE);
        recipesAdapter.setShowVisibilityBadge(false);
        recipesAdapter.setListener(new RecipeRowCardAdapter.Listener() {
            @Override
            public void onRecipeClick(RecipePreviewResponse recipe) {
                openRecipe(recipe);
            }

            @Override
            public void onTrailingActionClick(RecipePreviewResponse recipe, View anchor) {
                // No trailing action on this read-only page.
            }
        });
        rvRecipes.setAdapter(recipesAdapter);

        favoritesAdapter = new RecipeRowCardAdapter();
        favoritesAdapter.setTrailingAction(RecipeRowCardAdapter.TrailingAction.NONE);
        favoritesAdapter.setShowVisibilityBadge(false);
        favoritesAdapter.setListener(new RecipeRowCardAdapter.Listener() {
            @Override
            public void onRecipeClick(RecipePreviewResponse recipe) {
                openRecipe(recipe);
            }

            @Override
            public void onTrailingActionClick(RecipePreviewResponse recipe, View anchor) {
                // No trailing action on this read-only page.
            }
        });
        rvFavorites.setAdapter(favoritesAdapter);
    }

    private void openRecipe(RecipePreviewResponse recipe) {
        Intent intent = new Intent();
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id());
        Navigator.start(this, RecipeDetailActivity.class, intent);
    }

    private void observeProfile() {
        viewModel.getProfileResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progressBar.setVisibility(View.VISIBLE);
            } else if (result instanceof ApiResult.Success<UserResponse> success) {
                progressBar.setVisibility(View.GONE);
                renderUser(success.getData());
            } else if (result instanceof ApiResult.Error<?> error) {
                progressBar.setVisibility(View.GONE);
                showError(error.getMessage(), null);
            }
        });
    }

    private void renderUser(UserResponse user) {
        String fullName = (user.firstName() + " " + user.lastName()).trim();
        if (fullName.isEmpty()) fullName = getString(R.string.anonymous);

        tvFullName.setText(fullName);
        avatarView.setAvatar(user.avatarUrl(), fullName);

        if (user.city() != null && !user.city().isBlank()) {
            tvCity.setText(user.city());
            tvCity.setVisibility(View.VISIBLE);
        } else {
            tvCity.setVisibility(View.GONE);
        }

        if (user.bio() != null && !user.bio().isBlank()) {
            tvBio.setText(user.bio());
        } else {
            tvBio.setText(R.string.user_profile_no_bio);
        }

        sectionRecipes.setVisibility(user.showRecipesPublicly() ? View.VISIBLE : View.GONE);
        sectionFavorites.setVisibility(user.showFavoritesPublicly() ? View.VISIBLE : View.GONE);
    }

    private void observeRecipes() {
        viewModel.getRecipesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                List<RecipePreviewResponse> recipes = success.getData();
                recipesAdapter.setRecipes(recipes);
                tvRecipesEmpty.setVisibility(recipes.isEmpty() ? View.VISIBLE : View.GONE);
                rvRecipes.setVisibility(recipes.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void observeFavorites() {
        viewModel.getFavoritesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                List<RecipePreviewResponse> favorites = success.getData();
                favoritesAdapter.setRecipes(favorites);
                tvFavoritesEmpty.setVisibility(favorites.isEmpty() ? View.VISIBLE : View.GONE);
                rvFavorites.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
    }
}
